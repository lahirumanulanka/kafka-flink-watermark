package com.example;

import org.apache.flink.api.common.eventtime.SerializableTimestampAssigner;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.api.common.typeinfo.Types;
import org.apache.flink.connector.kafka.sink.KafkaRecordSerializationSchema;
import org.apache.flink.connector.kafka.sink.KafkaSink;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.windowing.ProcessAllWindowFunction;
import org.apache.flink.streaming.api.windowing.assigners.TumblingEventTimeWindows;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.apache.flink.streaming.api.windowing.windows.TimeWindow;
import org.apache.flink.util.Collector;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.regex.Pattern;

public class FacebookHashtagCounter {
    private static Pattern buildPattern(String hashtag) {
        String escaped = Pattern.quote(hashtag);
        String regex = "(?i)(^|[^A-Za-z0-9_])#?" + escaped + "([^A-Za-z0-9_]|$)";
        return Pattern.compile(regex);
    }

    public static void main(String[] args) throws Exception {
        final String bootstrap = System.getProperty("bootstrap", "kafka:9092");
        final String inputTopic = System.getProperty("input.topic", "social_facebook");
        final String outputTopic = System.getProperty("output.topic", "facebook-hashtag-counts");
        final String groupId = System.getProperty("group", "facebook-pt-counter");
        final int windowSeconds = Integer.getInteger("window.seconds", 15);
        final String hashtag = System.getProperty("hashtag", "5g");

        final Pattern pattern = buildPattern(hashtag);

        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        WatermarkStrategy<String> wm = WatermarkStrategy
            .<String>forBoundedOutOfOrderness(java.time.Duration.ofSeconds(5))
            .withTimestampAssigner((line, ts) -> extractFacebookTimestampMillis(line))
            .withIdleness(java.time.Duration.ofSeconds(10));

        KafkaSource<String> source = KafkaSource.<String>builder()
                .setBootstrapServers(bootstrap)
                .setTopics(inputTopic)
                .setGroupId(groupId)
                .setStartingOffsets(OffsetsInitializer.earliest())
            .setValueOnlyDeserializer(new SimpleStringSchema())
                .build();

        // Apply watermark strategy at the source to enable partition-aware watermarks
        DataStream<String> events = env.fromSource(source, wm, "facebook-source");

        DataStream<String> windowCounts = events
                .filter(line -> line != null && pattern.matcher(line).find())
                .map(v -> 1L).returns(Types.LONG)
                .windowAll(TumblingEventTimeWindows.of(Time.seconds(windowSeconds)))
                .process(new ProcessAllWindowFunction<Long, String, TimeWindow>() {
                    @Override
                    public void process(Context ctx, Iterable<Long> elements, Collector<String> out) {
                        long count = 0L;
                        java.util.Iterator<Long> it = elements.iterator();
                        while (it.hasNext()) { it.next(); count++; }
                        TimeWindow w = ctx.window();
                        String json = String.format(Locale.ROOT,
                                "{\"hashtag\":\"%s\",\"windowStart\":%d,\"windowEnd\":%d,\"count\":%d}",
                                hashtag,
                                w.getStart(),
                                w.getEnd(),
                                count);
                        out.collect(json);
                    }
                });

        KafkaSink<String> sink = KafkaSink.<String>builder()
                .setBootstrapServers(bootstrap)
                .setRecordSerializer(
                        KafkaRecordSerializationSchema.builder()
                                .setTopic(outputTopic)
                                .setValueSerializationSchema(new SimpleStringSchema())
                                .build()
                ).build();

        windowCounts.sinkTo(sink).name("kafka-sink-facebook").uid("kafka-sink-facebook");

        env.execute("FacebookHashtagCounter");
    }

    // Extract event timestamp from Facebook CSV line; fallback to processing time
    static long extractFacebookTimestampMillis(String line) {
        if (line == null) return System.currentTimeMillis();
        // The sample CSV includes a date_created field like 2024-01-12T13:41:08.000Z (quoted)
        String[] parts = line.split(",");
        for (String p : parts) {
            String trimmed = p.trim().replace("\"", "");
            try {
                OffsetDateTime odt = OffsetDateTime.parse(trimmed, DateTimeFormatter.ISO_OFFSET_DATE_TIME);
                return odt.toInstant().toEpochMilli();
            } catch (DateTimeParseException ignored) { /* continue */ }
        }
        return System.currentTimeMillis();
    }
}
