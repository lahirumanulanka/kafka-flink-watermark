# Produces CSV lines from dataset files into Kafka topics via the Kafka container
param(
  [string]$FacebookPath = "datasets\dataset1\Facebook-datasets.csv",
  [string]$TwitterPath  = "datasets\dataset2\Twitter- datasets.csv",
  [string]$FacebookTopic = "social_facebook",
  [string]$TwitterTopic  = "social_twitter",
  [string]$ContainerName = "kafka",
  [string]$Bootstrap = "kafka:9092",
  [ValidateSet('both','facebook','twitter')][string]$Which = 'both'
)

function Send-FileToTopic {
  param(
    [Parameter(Mandatory=$true)][string]$HostPath,
    [Parameter(Mandatory=$true)][string]$Topic
  )
  if (-not (Test-Path -LiteralPath $HostPath)) {
    throw "File not found: $HostPath"
  }

  $base = [System.IO.Path]::GetFileName($HostPath)
  $containerDir = "/tmp/datasets"
  $containerPath = "$containerDir/$base"

  Write-Host "Copying '$HostPath' -> $ContainerName:$containerPath" -ForegroundColor Cyan
  & docker exec $ContainerName bash -lc "mkdir -p '$containerDir'" | Out-Null
  if ($LASTEXITCODE -ne 0) { throw "Failed to ensure directory $containerDir in container." }

  # Use / to work cross-platform for docker cp
  $hostCopyPath = $HostPath -replace "\\","/"
  & docker cp "$hostCopyPath" "$ContainerName`:$containerPath"
  if ($LASTEXITCODE -ne 0) { throw "docker cp failed for $HostPath" }

  Write-Host "Producing lines from $base to topic '$Topic'..." -ForegroundColor Green
  # Use bash -lc so input redirection happens in container
  & docker exec $ContainerName bash -lc "kafka-console-producer --bootstrap-server $Bootstrap --topic '$Topic' < '$containerPath'"
  if ($LASTEXITCODE -ne 0) { throw "Failed to produce $base to $Topic" }
}

switch ($Which) {
  'facebook' { Send-FileToTopic -HostPath $FacebookPath -Topic $FacebookTopic }
  'twitter'  { Send-FileToTopic -HostPath $TwitterPath  -Topic $TwitterTopic }
  default    {
    Send-FileToTopic -HostPath $FacebookPath -Topic $FacebookTopic
    Send-FileToTopic -HostPath $TwitterPath  -Topic $TwitterTopic
  }
}

Write-Host "Done producing." -ForegroundColor Cyan
