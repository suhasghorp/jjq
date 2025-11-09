param(
    [string[]]$TestIds = @("210910_AiDc3", "230101_BiDc1_1", "230515_AiDcJ_2")
)

$ErrorActionPreference = 'Continue'

Write-Host "Fetching WebPageTest data..." -ForegroundColor Cyan

# Create directories if they don't exist
$dirs = @("test-data\small", "test-data\medium", "test-data\large")
foreach ($dir in $dirs) {
    if (-not (Test-Path $dir)) {
        New-Item -ItemType Directory -Path $dir -Force | Out-Null
    }
}

# Function to fetch and categorize WPT results
function Fetch-WPTResult($testId) {
    $url = "https://www.webpagetest.org/jsonResult.php?test=$testId"
    $tempPath = "test-data\temp-$testId.json"

    Write-Host "  Fetching $testId..." -NoNewline

    try {
        $response = Invoke-WebRequest -Uri $url -TimeoutSec 30 -UseBasicParsing

        if ($response.StatusCode -eq 200) {
            $content = $response.Content
            $content | Out-File -FilePath $tempPath -Encoding UTF8

            $size = (Get-Item $tempPath).Length
            $sizeKB = [math]::Round($size / 1KB, 2)

            # Categorize by size
            if ($size -lt 10KB) {
                $targetDir = "test-data\small"
            } elseif ($size -lt 300KB) {
                $targetDir = "test-data\medium"
            } else {
                $targetDir = "test-data\large"
            }

            $targetPath = "$targetDir\$testId.json"
            Move-Item $tempPath $targetPath -Force

            Write-Host " OK ($sizeKB KB -> $targetDir)" -ForegroundColor Green
            return $true
        }
    } catch {
        Write-Host " FAILED: $($_.Exception.Message)" -ForegroundColor Yellow
        if (Test-Path $tempPath) {
            Remove-Item $tempPath -Force
        }
        return $false
    }
}

# Fetch test IDs
$successCount = 0
foreach ($testId in $TestIds) {
    if (Fetch-WPTResult $testId) {
        $successCount++
    }
}

Write-Host "`nFetched $successCount / $($TestIds.Count) test results successfully" -ForegroundColor Cyan

# Summary
Write-Host "`nTest data summary:" -ForegroundColor Cyan
foreach ($dir in $dirs) {
    if (Test-Path $dir) {
        $count = (Get-ChildItem $dir -Filter *.json -ErrorAction SilentlyContinue).Count
        if ($count -gt 0) {
            $totalSize = (Get-ChildItem $dir -Filter *.json | Measure-Object -Property Length -Sum).Sum
            $totalSizeKB = [math]::Round($totalSize / 1KB, 2)
            Write-Host "  $dir : $count files ($totalSizeKB KB total)"
        }
    }
}
