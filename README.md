---
noteId: "56184750c44311f0a848b968847fae3e"
tags: []

---

# Kafka + Flink Watermarks (Student Guide)

This repo shows how to stream data into Kafka and process it with Apache Flink. We focus on watermarks (event‑time) and compare with processing‑time windows. You can run everything locally using Docker.

## What you will do

- Start Kafka, Zookeeper and Flink using `docker compose`.
- Create two topics: `facebook-datasets` and `twitter-datasets`.
- Produce CSV rows into each topic.
- Run two Flink streaming jobs to read, filter by hashtag, and count messages every 15 seconds.
- Try event‑time with watermarks and see how results change.

## Prerequisites

- Windows with PowerShell (commands below are for PowerShell).
- Docker Desktop installed and running.

## 1) Start the stack

From the project root:

```powershell
docker compose up -d
```

Flink UI will be at `http://localhost:8081`.

### Verify containers (quick checks)

Run these from the repo root in PowerShell:

```powershell
# 1) Check services are Up
docker compose ps

# 2) Tail recent logs for core services
docker compose logs --tail=100 zookeeper kafka jobmanager taskmanager

# 3) Probe Flink dashboard
Start-Process http://localhost:8081

# 4) Kafka smoke test (list topics from inside the broker)
docker exec kafka kafka-topics --bootstrap-server localhost:9092 --list

# (Optional) Create a test topic and produce/consume a message
docker exec kafka kafka-topics --bootstrap-server localhost:9092 --create --topic smoke-test --partitions 1 --replication-factor 1
docker exec -i kafka bash -lc "printf 'hello\n' | kafka-console-producer --broker-list localhost:9092 --topic smoke-test"
docker exec kafka kafka-console-consumer --bootstrap-server localhost:9092 --topic smoke-test --from-beginning --max-messages 1
```

Notes:

- Use `localhost:29092` from host applications; use `kafka:9092` from other containers.
- If any service is not Up, run `docker compose up -d` and recheck with `docker compose ps`.

## 2) Create topics

```powershell
# Inside the kafka container we’ll use kafka-topics
docker exec kafka bash -lc "kafka-topics --bootstrap-server kafka:9092 --create --topic facebook-datasets --partitions 1 --replication-factor 1"
docker exec kafka bash -lc "kafka-topics --bootstrap-server kafka:9092 --create --topic twitter-datasets  --partitions 1 --replication-factor 1"
```

To check:

```powershell
docker exec kafka bash -lc "kafka-topics --bootstrap-server kafka:9092 --list"
```

## 3) Produce the CSVs

This streams each line in the CSV as a Kafka message. If your CSVs have a header, you can skip it.

```powershell
# From repo root
Get-Content .\datasets\dataset1\Facebook-datasets.csv | docker exec -i kafka bash -lc "kafka-console-producer --broker-list kafka:9092 --topic facebook-datasets"
Get-Content '.\datasets\dataset2\Twitter- datasets.csv' | docker exec -i kafka bash -lc "kafka-console-producer --broker-list kafka:9092 --topic twitter-datasets"
```

Optional: skip header row

```powershell
(Get-Content .\datasets\dataset1\Facebook-datasets.csv | Select-Object -Skip 1) | docker exec -i kafka bash -lc "kafka-console-producer --broker-list kafka:9092 --topic facebook-datasets"
```

## 4) Build the Flink jobs (Java)

We already provided a Maven project in `flink-apps`. Build with local Maven or via Docker:

```powershell
cd .\flink-apps
mvn -q -DskipTests package
```

The jar will be at `flink-apps\target\flink-apps-1.0-SNAPSHOT.jar`.

If you don’t have Maven:

```powershell
cd .\flink-apps
$pwdPath = (Get-Location).Path
docker run --rm -v "${pwdPath}:/workspace" -w /workspace maven:3.9-eclipse-temurin-17 mvn -q -DskipTests package
```

## 5) Submit the jobs

Copy the jar into the JobManager and run the two verifiers (they read from the two topics and print lines to taskmanager logs):

```powershell
# From repo root
$jar = "flink-apps/target/flink-apps-1.0-SNAPSHOT.jar"
docker cp $jar jobmanager:/opt/flink/usrlib/flink-apps.jar

# Run the Facebook/Twitter verifiers
docker exec jobmanager bash -lc "flink run -d -c com.example.FacebookStreamVerifier /opt/flink/usrlib/flink-apps.jar"
docker exec jobmanager bash -lc "flink run -d -c com.example.TwitterStreamVerifier  /opt/flink/usrlib/flink-apps.jar"
```

Check running jobs:

```powershell
docker exec jobmanager bash -lc "flink list"
```

Optionally set different bootstrap or topics when submitting:

```powershell
docker exec jobmanager bash -lc "flink run -d -Dbootstrap=localhost:29092 -Dtopic=social_facebook -Dgroup=fb-ui com.example.FacebookStreamVerifier /opt/flink/usrlib/flink-apps.jar"
```

## 6) Event‑time counters (watermarks)

Use Flink’s WatermarkStrategy (bounded out‑of‑orderness) to count hashtag matches every 15 seconds based on the event’s timestamp.

Create output topics (once):

```powershell
docker exec kafka bash -lc "kafka-topics --bootstrap-server kafka:9092 --create --topic twitter-hashtag-et-counts  --partitions 1 --replication-factor 1 || true"
docker exec kafka bash -lc "kafka-topics --bootstrap-server kafka:9092 --create --topic facebook-hashtag-et-counts --partitions 1 --replication-factor 1 || true"
```

Submit the event‑time jobs (set your tag without `#`):

```powershell
# Twitter (event-time)
docker exec jobmanager bash -lc "flink run -d -c com.example.TwitterEventTimeHashtagCounter  -Dhashtag=Ligue1 -Dwindow.seconds=15 -Dlateness.millis=5000 /opt/flink/usrlib/flink-apps.jar"

# Facebook (event-time)
docker exec jobmanager bash -lc "flink run -d -c com.example.FacebookEventTimeHashtagCounter -Dhashtag=5g     -Dwindow.seconds=15 -Dlateness.millis=5000 /opt/flink/usrlib/flink-apps.jar"
```

Consume results:

```powershell
docker exec kafka bash -lc "kafka-console-consumer --bootstrap-server kafka:9092 --topic twitter-hashtag-et-counts  --from-beginning"
docker exec kafka bash -lc "kafka-console-consumer --bootstrap-server kafka:9092 --topic facebook-hashtag-et-counts --from-beginning"
```

## 7) Try higher parallelism

- Increase Kafka partitions to 2:

```powershell
docker exec kafka bash -lc "kafka-topics --bootstrap-server kafka:9092 --alter --topic twitter-datasets           --partitions 2"
docker exec kafka bash -lc "kafka-topics --bootstrap-server kafka:9092 --alter --topic facebook-datasets           --partitions 2"
docker exec kafka bash -lc "kafka-topics --bootstrap-server kafka:9092 --alter --topic twitter-hashtag-et-counts   --partitions 2"
docker exec kafka bash -lc "kafka-topics --bootstrap-server kafka:9092 --alter --topic facebook-hashtag-et-counts  --partitions 2"
```

- Rebuild and redeploy the event‑time jobs (they set parallelism=2 and use `withIdleness(...)` so idle partitions don’t block watermarks).
- Re‑ingest data and observe changes in latency and throughput.

## Notes

- If you don’t see counts, make sure you have recent lines containing your hashtag; otherwise the windows will be empty.
- Watermarks are event‑time; they wait for late data up to the slack you set (`lateness.millis`).
- For exact counts on restarts, enable checkpointing and use Kafka transactional sinks.

## Clean up

```powershell
docker compose down
```

## Scripts (automation)

If you prefer one-liners to automate topic creation and producing the CSVs, use the scripts in `scripts/`.

PowerShell (Windows):

```powershell
# Create two topics (defaults: social_facebook, social_twitter)
./scripts/create_topics.ps1

# Produce both CSVs into the matching topics
./scripts/produce_from_csv.ps1

# Options
./scripts/create_topics.ps1 -FacebookTopic social_facebook -TwitterTopic social_twitter -Partitions 1 -ReplicationFactor 1
./scripts/produce_from_csv.ps1  -Which both   # or 'facebook' or 'twitter'
```

Bash (WSL/macOS/Linux):

```bash
# Create two topics
bash scripts/create_topics.sh

# Produce both CSVs
bash scripts/produce_from_csv.sh

# Options via env vars
FACEBOOK_TOPIC=social_facebook TWITTER_TOPIC=social_twitter bash scripts/create_topics.sh
WHICH=facebook bash scripts/produce_from_csv.sh
```
