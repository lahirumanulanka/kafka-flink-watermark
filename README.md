---
noteId: "56184750c44311f0a848b968847fae3e"
tags: []

---

# Watermarks Kafka + Flink Project Structure

```
watermarks-kafka-flink/
│
├── docker/
│   ├── kafka/
│   ├── zookeeper/
│   ├── flink/
│   └── config/
│
├── datasets/
│   ├── dataset1/
│   └── dataset2/
│
├── flink-jobs/
│   ├── job1-tweets-topic1/
│   └── job2-tweets-topic2/
│
├── scripts/
│   ├── producer/
│   ├── consumer/
│   └── utils/
│
├── kafka-topics/
│   └── topic-configs/
│
├── docs/
│   ├── diagrams/
│   ├── explanation/
│   └── screenshots/
│
├── experiments/
│   ├── watermark-strategies/
│   ├── accuracy-results/
│   └── performance-metrics/
│
├── results/
│   ├── topic1-results/
│   └── topic2-results/
│
├── logs/
│   ├── kafka/
│   ├── flink/
│   └── app/
│
└── tests/
    ├── integration/
    └── performance/
```

Each leaf folder contains a `.gitkeep` so the directory is versioned even when empty.
