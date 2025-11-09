param(
    [switch]$FunctionalOnly,
    [switch]$PerformanceOnly,
    [switch]$SkipNative,
    [string]$OutputFile = "benchmark-results.md"
)

$ErrorActionPreference = 'Continue'

Write-Host "=" * 70 -ForegroundColor Cyan
Write-Host "JQ vs JJQ Benchmark Suite" -ForegroundColor Cyan
Write-Host "=" * 70 -ForegroundColor Cyan

# Ensure jq is available
if (-not (Get-Command jq -ErrorAction SilentlyContinue)) {
    throw "jq is not installed or not in PATH"
}

# Find jjq JAR
$jjqJar = Get-Item "target\jjq-*-jar-with-dependencies.jar" -ErrorAction SilentlyContinue
if (-not $jjqJar) {
    throw "jjq JAR not found. Run 'mvn package' first."
}
$jjqJar = $jjqJar.FullName

# Check native image
$jjqNative = "target\jjq.exe"
$hasNative = Test-Path $jjqNative

if (-not $hasNative -and -not $SkipNative) {
    Write-Warning "Native image not found at $jjqNative. Use -SkipNative to suppress this warning."
}

# Initialize results
$results = @{
    Timestamp = Get-Date -Format "yyyy-MM-dd HH:mm:ss"
    Environment = @{
        JQ_Version = (jq --version 2>&1 | Out-String).Trim()
        JJQ_Version = "1.0-SNAPSHOT"
        Java_Version = (java -version 2>&1 | Out-String).Trim().Split("`n")[0]
        OS = [System.Environment]::OSVersion.VersionString
        Has_Native = $hasNative
    }
    Functional = @()
    Performance = @()
}

Write-Host "`nEnvironment:" -ForegroundColor Yellow
Write-Host "  jq:         $($results.Environment.JQ_Version)"
Write-Host "  jjq JAR:    $jjqJar"
Write-Host "  jjq Native: $(if ($hasNative) { $jjqNative } else { 'Not available' })"
Write-Host "  Java:       $($results.Environment.Java_Version)"

# Helper function for semantic JSON comparison
function Compare-JsonSemantically {
    param(
        [string]$Json1,
        [string]$Json2
    )

    # Handle empty or error outputs
    if ([string]::IsNullOrWhiteSpace($Json1) -or [string]::IsNullOrWhiteSpace($Json2)) {
        return $Json1.Trim() -eq $Json2.Trim()
    }

    # Check if output contains errors
    if ($Json1 -match "error|Error|ERROR" -or $Json2 -match "error|Error|ERROR") {
        # For errors, just check if both errored
        $bothError = ($Json1 -match "error|Error|ERROR") -and ($Json2 -match "error|Error|ERROR")
        return $bothError
    }

    try {
        # Try to parse as JSON and compare
        $obj1 = $Json1 | ConvertFrom-Json -ErrorAction Stop
        $obj2 = $Json2 | ConvertFrom-Json -ErrorAction Stop

        # Use jq to normalize both outputs with sorted keys
        $temp1 = [System.IO.Path]::GetTempFileName()
        $temp2 = [System.IO.Path]::GetTempFileName()

        $Json1 | Out-File -FilePath $temp1 -Encoding UTF8 -NoNewline
        $Json2 | Out-File -FilePath $temp2 -Encoding UTF8 -NoNewline

        $normalized1 = jq -S -c "." $temp1 2>&1 | Out-String
        $normalized2 = jq -S -c "." $temp2 2>&1 | Out-String

        Remove-Item $temp1, $temp2 -ErrorAction SilentlyContinue

        return $normalized1.Trim() -eq $normalized2.Trim()
    } catch {
        # If parsing fails, fall back to string comparison
        return $Json1.Trim() -eq $Json2.Trim()
    }
}

# ===== FUNCTIONAL TESTS =====
if (-not $PerformanceOnly) {
    Write-Host "`n" + ("=" * 70) -ForegroundColor Cyan
    Write-Host "FUNCTIONAL CORRECTNESS TESTS" -ForegroundColor Cyan
    Write-Host ("=" * 70) -ForegroundColor Cyan

    $queries = @(
        @{ Query = "."; Description = "Identity (semantic)"; KnownDiff = $false },
        @{ Query = ".data"; Description = "Top-level field (semantic)"; KnownDiff = $false },
        @{ Query = ".data | .testId"; Description = "Piped field access"; KnownDiff = $false },
        @{ Query = ".data | .median | .firstView | .SpeedIndex"; Description = "Deep nested access"; KnownDiff = $false },
        @{ Query = ".data | .runs"; Description = "Object field (semantic)"; KnownDiff = $false },
        @{ Query = ".data | .median"; Description = "Nested object access"; KnownDiff = $false },
        @{ Query = ".data | .median | .firstView"; Description = "Deep object access"; KnownDiff = $false },
        @{ Query = ".data | .runs | .1"; Description = ".N syntax quirk (jq:0.1, jjq:field '1')"; KnownDiff = $true }
    )

    $testFile = "test-data\medium\wpt-result.json"
    if (-not (Test-Path $testFile)) {
        $testFile = "test-data\small\210910_AiDc3.json"
    }

    Write-Host "`nTest file: $testFile`n"

    $passCount = 0
    $failCount = 0
    $knownDiffCount = 0

    foreach ($test in $queries) {
        $query = $test.Query
        $desc = $test.Description
        $isKnownDiff = $test.KnownDiff

        Write-Host "[$($passCount + $failCount + $knownDiffCount + 1)/$($queries.Count)] Testing: $desc" -NoNewline

        try {
            $jqResult = jq -c $query $testFile 2>&1 | Out-String
            $jjqResult = java -jar $jjqJar -c $query $testFile 2>&1 | Out-String

            $jqClean = $jqResult.Trim()
            $jjqClean = $jjqResult.Trim()

            # Use semantic comparison instead of string comparison
            $match = Compare-JsonSemantically $jqClean $jjqClean

            $results.Functional += @{
                Query = $query
                Description = $desc
                Match = $match
                KnownDiff = $isKnownDiff
                JQ_Output = $jqClean
                JJQ_Output = $jjqClean
            }

            if ($match) {
                Write-Host " - PASS" -ForegroundColor Green
                $passCount++
            } elseif ($isKnownDiff) {
                Write-Host " - KNOWN DIFF" -ForegroundColor Yellow
                Write-Host "    jq:  $($jqClean.Substring(0, [Math]::Min(80, $jqClean.Length)))" -ForegroundColor Gray
                Write-Host "    jjq: $($jjqClean.Substring(0, [Math]::Min(80, $jjqClean.Length)))" -ForegroundColor Gray
                $knownDiffCount++
            } else {
                Write-Host " - FAIL" -ForegroundColor Red
                Write-Host "    jq:  $($jqClean.Substring(0, [Math]::Min(80, $jqClean.Length)))" -ForegroundColor Gray
                Write-Host "    jjq: $($jjqClean.Substring(0, [Math]::Min(80, $jjqClean.Length)))" -ForegroundColor Gray
                $failCount++
            }
        } catch {
            Write-Host " - ERROR: $($_.Exception.Message)" -ForegroundColor Red
            $failCount++
        }
    }

    Write-Host "`nFunctional Summary: $passCount PASS, $failCount FAIL, $knownDiffCount KNOWN DIFF" -ForegroundColor $(if ($failCount -eq 0) { "Green" } else { "Yellow" })
}

# ===== PERFORMANCE TESTS =====
if (-not $FunctionalOnly) {
    Write-Host "`n" + ("=" * 70) -ForegroundColor Cyan
    Write-Host "PERFORMANCE BENCHMARKS" -ForegroundColor Cyan
    Write-Host ("=" * 70) -ForegroundColor Cyan

    # Test configurations
    $perfTests = @(
        @{
            Name = "Identity Query"
            Query = "."
            File = "test-data\small\210910_AiDc3.json"
            Iterations = 100
        },
        @{
            Name = "Simple Field Access"
            Query = ".data | .testId"
            File = "test-data\medium\wpt-result.json"
            Iterations = 100
        },
        @{
            Name = "Deep Nested Access"
            Query = ".data | .median | .firstView | .SpeedIndex"
            File = "test-data\medium\wpt-result.json"
            Iterations = 100
        },
        @{
            Name = "Object Field Access"
            Query = ".data | .runs | .1"
            File = "test-data\medium\wpt-result.json"
            Iterations = 50
        },
        @{
            Name = "Complex Pipeline"
            Query = ".data | .runs | .1 | .firstView | .SpeedIndex"
            File = "test-data\medium\wpt-result.json"
            Iterations = 50
        }
    )

    foreach ($test in $perfTests) {
        Write-Host "`nBenchmark: $($test.Name)" -ForegroundColor Yellow
        Write-Host "  Query: $($test.Query)"
        Write-Host "  File:  $($test.File)"
        Write-Host "  Iterations: $($test.Iterations)"

        if (-not (Test-Path $test.File)) {
            Write-Warning "  Test file not found, skipping..."
            continue
        }

        # Warmup
        Write-Host "  Warming up..." -NoNewline
        1..5 | ForEach-Object {
            jq $test.Query $test.File | Out-Null
            java -jar $jjqJar $test.Query $test.File | Out-Null
            if ($hasNative) {
                & $jjqNative $test.Query $test.File | Out-Null
            }
        }
        Write-Host " done" -ForegroundColor Green

        # Benchmark jq
        Write-Host "  jq:         " -NoNewline
        $jqTime = (Measure-Command {
            1..$test.Iterations | ForEach-Object {
                jq $test.Query $test.File | Out-Null
            }
        }).TotalMilliseconds / $test.Iterations
        Write-Host "$([math]::Round($jqTime, 2)) ms" -ForegroundColor Cyan

        # Benchmark jjq JAR
        Write-Host "  jjq (JAR):  " -NoNewline
        $jjqJarTime = (Measure-Command {
            1..$test.Iterations | ForEach-Object {
                java -jar $jjqJar $test.Query $test.File | Out-Null
            }
        }).TotalMilliseconds / $test.Iterations
        $jarSpeedup = $jqTime / $jjqJarTime
        Write-Host "$([math]::Round($jjqJarTime, 2)) ms ($(if ($jarSpeedup -lt 1) { [math]::Round(1/$jarSpeedup, 2).ToString() + 'x slower' } else { [math]::Round($jarSpeedup, 2).ToString() + 'x faster' }))" -ForegroundColor Cyan

        # Benchmark jjq native (if available)
        $jjqNativeTime = $null
        $nativeSpeedup = $null
        if ($hasNative -and -not $SkipNative) {
            Write-Host "  jjq (Native):" -NoNewline
            $jjqNativeTime = (Measure-Command {
                1..$test.Iterations | ForEach-Object {
                    & $jjqNative $test.Query $test.File | Out-Null
                }
            }).TotalMilliseconds / $test.Iterations
            $nativeSpeedup = $jqTime / $jjqNativeTime
            Write-Host "$([math]::Round($jjqNativeTime, 2)) ms ($(if ($nativeSpeedup -lt 1) { [math]::Round(1/$nativeSpeedup, 2).ToString() + 'x slower' } else { [math]::Round($nativeSpeedup, 2).ToString() + 'x faster' }))" -ForegroundColor Cyan
        }

        $results.Performance += @{
            Name = $test.Name
            Query = $test.Query
            Iterations = $test.Iterations
            JQ_Time_ms = [math]::Round($jqTime, 2)
            JJQ_JAR_Time_ms = [math]::Round($jjqJarTime, 2)
            JJQ_Native_Time_ms = if ($jjqNativeTime) { [math]::Round($jjqNativeTime, 2) } else { "N/A" }
            JAR_vs_JQ = if ($jarSpeedup -lt 1) { "$([math]::Round(1/$jarSpeedup, 2))x slower" } else { "$([math]::Round($jarSpeedup, 2))x faster" }
            Native_vs_JQ = if ($nativeSpeedup) {
                if ($nativeSpeedup -lt 1) { "$([math]::Round(1/$nativeSpeedup, 2))x slower" } else { "$([math]::Round($nativeSpeedup, 2))x faster" }
            } else { "N/A" }
        }
    }

    # Batch processing test
    Write-Host "`nBatch Processing Test (Query Caching Benefit)" -ForegroundColor Yellow
    $batchQuery = ".data | .testId"
    $batchFiles = Get-ChildItem "test-data\batch\*.json" -ErrorAction SilentlyContinue

    if ($batchFiles -and $batchFiles.Count -gt 0) {
        Write-Host "  Processing $($batchFiles.Count) files with query: $batchQuery"

        Write-Host "  jq:         " -NoNewline
        $jqBatchTime = (Measure-Command {
            $batchFiles | ForEach-Object {
                jq $batchQuery $_.FullName | Out-Null
            }
        }).TotalMilliseconds
        Write-Host "$([math]::Round($jqBatchTime, 2)) ms total" -ForegroundColor Cyan

        if ($hasNative) {
            Write-Host "  jjq (Native):" -NoNewline
            $jjqBatchTime = (Measure-Command {
                $batchFiles | ForEach-Object {
                    & $jjqNative $batchQuery $_.FullName | Out-Null
                }
            }).TotalMilliseconds
            $batchSpeedup = $jqBatchTime / $jjqBatchTime
            Write-Host "$([math]::Round($jjqBatchTime, 2)) ms total ($(if ($batchSpeedup -lt 1) { [math]::Round(1/$batchSpeedup, 2).ToString() + 'x slower' } else { [math]::Round($batchSpeedup, 2).ToString() + 'x faster' }))" -ForegroundColor Cyan

            $results.Performance += @{
                Name = "Batch Processing (20 files)"
                Query = $batchQuery
                Iterations = $batchFiles.Count
                JQ_Time_ms = [math]::Round($jqBatchTime, 2)
                JJQ_JAR_Time_ms = "N/A"
                JJQ_Native_Time_ms = [math]::Round($jjqBatchTime, 2)
                JAR_vs_JQ = "N/A"
                Native_vs_JQ = if ($batchSpeedup -lt 1) { "$([math]::Round(1/$batchSpeedup, 2))x slower" } else { "$([math]::Round($batchSpeedup, 2))x faster" }
            }
        }
    }
}

# ===== GENERATE REPORT =====
Write-Host "`n" + ("=" * 70) -ForegroundColor Cyan
Write-Host "GENERATING REPORT" -ForegroundColor Cyan
Write-Host ("=" * 70) -ForegroundColor Cyan

$report = @"
# JQ vs JJQ Performance Comparison Report

**Generated:** $($results.Timestamp)

## Environment

- **jq Version:** $($results.Environment.JQ_Version)
- **jjq Version:** $($results.Environment.JJQ_Version)
- **Java Version:** $($results.Environment.Java_Version)
- **OS:** $($results.Environment.OS)
- **Native Image:** $(if ($results.Environment.Has_Native) { "Available" } else { "Not Built" })

---

"@

if ($results.Functional.Count -gt 0) {
    $report += @"
## Functional Correctness Results

| # | Test | Query | Result |
|---|------|-------|--------|

"@

    $num = 1
    foreach ($test in $results.Functional) {
        $status = if ($test.Match) { "✅ PASS" } else { "❌ FAIL" }
        $report += "| $num | $($test.Description) | ``$($test.Query)`` | $status |`n"
        $num++
    }

    $passCount = ($results.Functional | Where-Object { $_.Match }).Count
    $totalCount = $results.Functional.Count
    $passRate = [math]::Round(($passCount / $totalCount) * 100, 1)
    $report += "`n**Summary:** $passCount / $totalCount tests passed ($passRate%)`n`n---`n`n"
}

if ($results.Performance.Count -gt 0) {
    $report += @"
## Performance Results

| Test | jq (ms) | jjq JAR (ms) | jjq Native (ms) | JAR vs jq | Native vs jq |
|------|---------|--------------|-----------------|-----------|--------------|

"@

    foreach ($test in $results.Performance) {
        $report += "| $($test.Name) | $($test.JQ_Time_ms) | $($test.JJQ_JAR_Time_ms) | $($test.JJQ_Native_Time_ms) | $($test.JAR_vs_JQ) | $($test.Native_vs_JQ) |`n"
    }

    $report += "`n---`n`n"
}

$report += @"
## Detailed Query Information

"@

foreach ($test in $results.Performance) {
    if ($test.Query) {
        $report += "`n### $($test.Name)`n"
        $report += "- **Query:** ``$($test.Query)```n"
        $report += "- **Iterations:** $($test.Iterations)`n"
    }
}

$report += @"

---

## Notes

- All times are averaged over multiple iterations
- Warmup rounds were performed before measurements
- Tests use WebPageTest.org JSON data (sample and synthetic)
- Baseline measurements (before optimizations)

"@

$report | Out-File $OutputFile -Encoding UTF8
Write-Host "`nReport saved to: $OutputFile" -ForegroundColor Green

# Display summary
Write-Host "`n" + ("=" * 70) -ForegroundColor Cyan
Write-Host "SUMMARY" -ForegroundColor Cyan
Write-Host ("=" * 70) -ForegroundColor Cyan

if ($results.Functional.Count -gt 0) {
    $passCount = ($results.Functional | Where-Object { $_.Match }).Count
    Write-Host "Functional: $passCount / $($results.Functional.Count) PASS" -ForegroundColor $(if ($passCount -eq $results.Functional.Count) { "Green" } else { "Yellow" })
}

if ($results.Performance.Count -gt 0) {
    Write-Host "`nPerformance:" -ForegroundColor Yellow
    $results.Performance | ForEach-Object {
        if ($_.Name -notlike "*Batch*") {
            Write-Host "  $($_.Name): jq=$($_.JQ_Time_ms)ms, native=$($_.JJQ_Native_Time_ms)ms"
        } else {
            Write-Host "  $($_.Name): jq=$($_.JQ_Time_ms)ms, native=$($_.JJQ_Native_Time_ms)ms (total)"
        }
    }
}

Write-Host "`n" + ("=" * 70) -ForegroundColor Cyan
