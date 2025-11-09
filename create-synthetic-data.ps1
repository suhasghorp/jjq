$ErrorActionPreference = 'Stop'

Write-Host "Creating synthetic test data..." -ForegroundColor Cyan

# Read the sample file
$samplePath = "test-data\small\210910_AiDc3.json"
if (-not (Test-Path $samplePath)) {
    Write-Error "Sample file not found: $samplePath"
    exit 1
}

$sample = Get-Content $samplePath -Raw | ConvertFrom-Json

# Create medium-sized JSON (duplicate runs)
Write-Host "  Creating medium.json..." -NoNewline
$medium = $sample.PSObject.Copy()
$medium.data.runs = @{}

# Create 10 runs
1..10 | ForEach-Object {
    $medium.data.runs | Add-Member -NotePropertyName "$_" -NotePropertyValue $sample.data.runs."1" -Force
}

$mediumPath = "test-data\medium\synthetic-10runs.json"
$medium | ConvertTo-Json -Depth 20 | Out-File $mediumPath -Encoding UTF8
$mediumSize = [math]::Round((Get-Item $mediumPath).Length / 1KB, 2)
Write-Host " OK ($mediumSize KB)" -ForegroundColor Green

# Create large JSON (many runs)
Write-Host "  Creating large.json..." -NoNewline
$large = $sample.PSObject.Copy()
$large.data.runs = @{}

# Create 50 runs
1..50 | ForEach-Object {
    $large.data.runs | Add-Member -NotePropertyName "$_" -NotePropertyValue $sample.data.runs."1" -Force
}

$largePath = "test-data\large\synthetic-50runs.json"
$large | ConvertTo-Json -Depth 20 | Out-File $largePath -Encoding UTF8
$largeSize = [math]::Round((Get-Item $largePath).Length / 1KB, 2)
Write-Host " OK ($largeSize KB)" -ForegroundColor Green

# Create batch test data (multiple small files)
Write-Host "  Creating batch files..." -NoNewline
$batchDir = "test-data\batch"
if (-not (Test-Path $batchDir)) {
    New-Item -ItemType Directory -Path $batchDir -Force | Out-Null
}

1..20 | ForEach-Object {
    $batchData = $sample.PSObject.Copy()
    # Modify testId to make each unique
    $batchData.data.testId = "batch_test_$_"
    $batchData | ConvertTo-Json -Depth 20 | Out-File "$batchDir\batch-$_.json" -Encoding UTF8
}
Write-Host " OK (20 files)" -ForegroundColor Green

Write-Host "`nSynthetic test data created successfully!" -ForegroundColor Green
Write-Host "`nSummary:"
Write-Host "  Small:  1 file   (0.58 KB) - test-data\small\210910_AiDc3.json"
Write-Host "  Medium: 1 file   ($mediumSize KB) - test-data\medium\synthetic-10runs.json"
Write-Host "  Large:  1 file   ($largeSize KB) - test-data\large\synthetic-50runs.json"
Write-Host "  Batch:  20 files - test-data\batch\batch-*.json"
