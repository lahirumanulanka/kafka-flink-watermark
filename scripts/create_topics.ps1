# Creates two Kafka topics with 1 partition each using the Kafka container
param(
  [string]$FacebookTopic = "social_facebook",
  [string]$TwitterTopic = "social_twitter",
  [int]$Partitions = 1,
  [int]$ReplicationFactor = 1,
  [string]$ContainerName = "kafka",
  [string]$Bootstrap = "kafka:9092"
)

function New-TopicIfMissing {
  param(
    [string]$Topic
  )
  Write-Host "Checking topic '$Topic'..." -ForegroundColor Cyan
  $list = (& docker exec $ContainerName kafka-topics --bootstrap-server $Bootstrap --list) 2>$null
  if ($LASTEXITCODE -ne 0) {
    throw "Failed to list topics. Ensure container '$ContainerName' is running."
  }

  $topics = @()
  if ($list) { $topics = $list -split "`n" | ForEach-Object { $_.Trim() } | Where-Object { $_ -ne "" } }
  if ($topics -contains $Topic) {
    Write-Host "Topic '$Topic' already exists. Skipping create." -ForegroundColor Yellow
    return
  }

  Write-Host "Creating topic '$Topic' (partitions=$Partitions, rf=$ReplicationFactor)..." -ForegroundColor Green
  & docker exec $ContainerName kafka-topics --bootstrap-server $Bootstrap --create --topic $Topic --partitions $Partitions --replication-factor $ReplicationFactor | Write-Host
  if ($LASTEXITCODE -ne 0) {
    throw "Failed to create topic '$Topic'"
  }
}

New-TopicIfMissing -Topic $FacebookTopic
New-TopicIfMissing -Topic $TwitterTopic

Write-Host "Done. Current topics:" -ForegroundColor Cyan
& docker exec $ContainerName kafka-topics --bootstrap-server $Bootstrap --list | Write-Host
