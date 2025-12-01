package com.example;

import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;

public class FacebookStreamVerifier {
    public static void main(String[] args) throws Exception {
        final String bootstrap = System.getProperty("bootstrap", "kafka:9092");
        final String topic = System.getProperty("topic", "social_facebook");
        final String groupId = System.getProperty("group", "facebook-verifier");

        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        KafkaSource<String> source = KafkaSource.<String>builder()
                .setBootstrapServers(bootstrap)
                .setTopics(topic)
                .setGroupId(groupId)
                .setStartingOffsets(OffsetsInitializer.earliest())
                .setValueOnlyDeserializer(new SimpleStringSchema())
                .build();

        env.fromSource(source, WatermarkStrategy.noWatermarks(), "facebook-source")
                .name("facebook-source")
                .uid("facebook-source")
                .print()
                .name("print-facebook")
                .uid("print-facebook");

        env.execute("FacebookStreamVerifier");
    }
}
