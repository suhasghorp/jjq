# Performance Testing Plan: jq vs jjq with WebPageTest.org Data

## Overview

Compare the performance and correctness of real jq (v1.8.1) against jjq using real-world JSON data from WebPageTest.org API.

**Goals:**
1. Verify functional correctness (jjq produces same output as jq)
2. Measure performance differences across various query types
3. Identify performance bottlenecks and optimization opportunities
4. Document current performance baseline before optimizations

---

## Test Environment

### Tools
- **jq**: v1.8.1 (system installed)
- **jjq**: Current implementation (JAR + native image)
- **WebPageTest.org API**: Public API for real-world JSON data
- **Measurement tools**:
  - PowerShell `Measure-Command`
  - `hyperfine` (optional, for statistical analysis)
  - Custom benchmark harness

### Test Data Sources

**1. Small JSON (~0.5-5 KB)**
- Sample fixture: `src/test/resources/webpagetest/sample-result.json`
- Use case: Quick queries, minimal parsing overhead

**2. Medium JSON (~50-200 KB)**
- Live WebPageTest API result: Single test with full metrics
- URL: `https://www.webpagetest.org/jsonResult.php?test={testId}`
- Example test IDs: `210910_AiDc3`, recent test results

**3. Large JSON (~500KB-2MB)**
- WebPageTest API with multiple runs/locations
- Bulk test results or pagespeed data
- Use case: Stress testing, real-world performance

**4. Streaming Data**
- Multiple small JSON files processed sequentially
- Simulates log processing, batch operations
- Use case: Measure query caching benefits

---

## Test Query Categories

### Category 1: Simple Field Access
**Purpose:** Measure basic parsing + field lookup overhead

```bash
# Identity (baseline)
.

# Single field
.data

# Nested field (2 levels)
.data.testId

# Deep nested field (4+ levels)
.data.median.firstView.SpeedIndex
```

**Expected Behavior:**
- jq: Highly optimized, minimal overhead
- jjq: Should be comparable, possibly slower on first parse

### Category 2: Array Operations
**Purpose:** Measure iteration and collection performance

```bash
# Array iteration
.data.runs | .[]

# Array indexing
.data.runs | .[0]

# Array slicing
.data.runs | .[0:3]

# Array mapping
.data.runs | .[] | .firstView.SpeedIndex
```

**Expected Behavior:**
- jq: Optimized streaming, lazy evaluation
- jjq: May be slower on array operations, depends on Eclipse Collections efficiency

### Category 3: Filtering and Selection
**Purpose:** Measure conditional evaluation performance

```bash
# Select with condition
.data.runs | .[] | select(.firstView.SpeedIndex > 1000)

# Complex selection
.data.runs | .[] | .firstView | select(.TTFB < 500) | .SpeedIndex
```

**Expected Behavior:**
- jq: Mature implementation, efficient filtering
- jjq: Depends on Select implementation quality

### Category 4: Complex Pipe Chains
**Purpose:** Measure composition and query execution overhead

```bash
# Multi-stage pipeline
.data | .runs | .[] | .firstView | select(.SpeedIndex > 1000) | .SpeedIndex

# Deeply nested pipes
.data | .median | .firstView | .requests | .[] | select(.responseCode == 200) | .url
```

**Expected Behavior:**
- jq: Optimized for streaming, minimal intermediate allocations
- jjq: May show overhead in stream composition

### Category 5: Repetitive Queries (Caching Test)
**Purpose:** Measure benefit of query caching (when implemented)

```bash
# Run same query 1000 times on different files
for file in test-data/*.json; do
    jq ".data.median.firstView.SpeedIndex" $file
done
```

**Expected Behavior:**
- jq: Parses query every time
- jjq (with cache): Should significantly outperform after warmup
- jjq (without cache): Similar to jq

---

## Performance Metrics

### Primary Metrics

**1. Execution Time**
- Cold start (first query)
- Warm execution (repeated queries)
- Time breakdown:
  - JSON parsing time
  - Query parsing time
  - Query execution time
  - Output formatting time

**2. Memory Usage**
- Peak memory consumption
- Allocation rate
- GC pressure (for JAR version)

**3. Throughput**
- Queries per second (simple query)
- MB/sec processed (large JSON)
- Files processed per second (batch mode)

### Secondary Metrics

**4. Binary Size**
- jq binary size
- jjq JAR size
- jjq native image size

**5. Startup Time**
- Cold start latency
- Time to first output

---

## Test Methodology

### Phase 1: Functional Correctness Testing

**Objective:** Ensure jjq produces identical output to jq

**Approach:**
1. Define 50+ test queries across all categories
2. Run each query against test data with both jq and jjq
3. Compare outputs (normalized JSON, ignore whitespace)
4. Report any discrepancies

**Script Structure:**
```powershell
# functional-test.ps1
$queries = @(
    ".",
    ".data",
    ".data | .testId",
    # ... 50+ queries
)

$testFile = "test-data/wpt-result.json"
$failures = @()

foreach ($query in $queries) {
    $jqOutput = jq $query $testFile 2>&1
    $jjqOutput = java -jar target/jjq.jar $query $testFile 2>&1

    if ($jqOutput -ne $jjqOutput) {
        $failures += "Query: $query"
        $failures += "jq:  $jqOutput"
        $failures += "jjq: $jjqOutput"
    }
}

# Report results
```

**Success Criteria:**
- 100% of queries produce identical output
- OR: Document known differences with justification

### Phase 2: Micro-Benchmarks (Single Query Performance)

**Objective:** Measure performance on isolated operations

**Test Cases:**

**Test 2.1: Parse + Execute Simple Query**
```powershell
# Measure-Command with 100 iterations
$query = ".data.median.firstView.SpeedIndex"
$testFile = "test-data/medium.json"

# jq benchmark
$jqTime = (Measure-Command {
    1..100 | ForEach-Object { jq $query $testFile | Out-Null }
}).TotalMilliseconds / 100

# jjq JAR benchmark
$jjqJarTime = (Measure-Command {
    1..100 | ForEach-Object { java -jar target/jjq.jar $query $testFile | Out-Null }
}).TotalMilliseconds / 100

# jjq native benchmark
$jjqNativeTime = (Measure-Command {
    1..100 | ForEach-Object { target/jjq.exe $query $testFile | Out-Null }
}).TotalMilliseconds / 100
```

**Test 2.2: Query Parsing Overhead**
```powershell
# Use hyperfine for statistical analysis
hyperfine --warmup 10 --runs 100 `
    "jq '.data.median.firstView.SpeedIndex' test-data/medium.json" `
    "target/jjq.exe '.data.median.firstView.SpeedIndex' test-data/medium.json"
```

**Test 2.3: Large JSON Processing**
```powershell
# 500KB+ JSON file
hyperfine --warmup 3 --runs 50 `
    "jq '.data.runs | .[] | .firstView.SpeedIndex' test-data/large.json" `
    "target/jjq.exe '.data.runs | .[] | .firstView.SpeedIndex' test-data/large.json"
```

**Metrics to Capture:**
- Mean execution time
- Standard deviation
- Min/Max times
- P50, P95, P99 latencies

### Phase 3: Macro-Benchmarks (Real-World Scenarios)

**Objective:** Measure performance on realistic workloads

**Test 3.1: Batch Processing (Query Caching Benefit)**
```powershell
# Download 100 WebPageTest results
1..100 | ForEach-Object {
    Invoke-WebRequest "https://www.webpagetest.org/jsonResult.php?test=$testId" `
        -OutFile "test-data/batch/result-$_.json"
}

# Benchmark batch processing with same query
$query = ".data.median.firstView.SpeedIndex"

# jq (no caching)
$jqBatchTime = (Measure-Command {
    Get-ChildItem test-data/batch/*.json | ForEach-Object {
        jq $query $_.FullName | Out-Null
    }
}).TotalSeconds

# jjq native (potential caching benefit)
$jjqBatchTime = (Measure-Command {
    Get-ChildItem test-data/batch/*.json | ForEach-Object {
        target/jjq.exe $query $_.FullName | Out-Null
    }
}).TotalSeconds
```

**Test 3.2: Pipeline Processing**
```bash
# Simulate streaming log processing
cat test-data/batch/*.json | jq '.data.testId'
cat test-data/batch/*.json | target/jjq.exe '.data.testId'
```

**Test 3.3: Complex Query on Large Dataset**
```powershell
# Multi-stage filtering and mapping
$complexQuery = '.data.runs | .[] | .firstView | select(.SpeedIndex < 2000) | {url: .URL, speed: .SpeedIndex}'

# Compare performance
hyperfine --warmup 3 `
    "jq '$complexQuery' test-data/large.json" `
    "target/jjq.exe '$complexQuery' test-data/large.json"
```

### Phase 4: Memory Profiling

**Objective:** Measure memory efficiency

**Approach:**

**For jq:**
```bash
# Use /usr/bin/time (Linux/WSL) or Performance Monitor (Windows)
/usr/bin/time -v jq '.data.runs | .[]' test-data/large.json
```

**For jjq (JAR):**
```bash
# Java Flight Recorder
java -XX:StartFlightRecording=filename=jjq-profile.jfr \
     -jar target/jjq.jar '.data.runs | .[]' test-data/large.json

# Analyze with JDK Mission Control
```

**For jjq (Native):**
```powershell
# Windows Performance Monitor
# Track: Working Set, Private Bytes, Page Faults
```

**Metrics:**
- Peak memory usage
- Resident set size
- Allocation rate (JAR only)
- GC time (JAR only)

---

## Test Data Preparation

### Step 1: Fetch WebPageTest Data

**Script: `fetch-wpt-data.ps1`**
```powershell
param(
    [int]$SmallCount = 5,
    [int]$MediumCount = 10,
    [int]$LargeCount = 3
)

$ErrorActionPreference = 'Stop'

# Create test data directories
New-Item -ItemType Directory -Force -Path "test-data/small" | Out-Null
New-Item -ItemType Directory -Force -Path "test-data/medium" | Out-Null
New-Item -ItemType Directory -Force -Path "test-data/large" | Out-Null
New-Item -ItemType Directory -Force -Path "test-data/batch" | Out-Null

# Fetch sample test IDs (you can customize these)
$testIds = @(
    "210910_AiDc3",
    "230515_BiDcJ_ABC",
    # Add more test IDs
)

# Function to fetch and categorize by size
function Fetch-WPTResult($testId, $outputPath) {
    $url = "https://www.webpagetest.org/jsonResult.php?test=$testId"
    try {
        Invoke-WebRequest -Uri $url -OutFile $outputPath -TimeoutSec 30
        $size = (Get-Item $outputPath).Length
        Write-Host "Downloaded $testId - Size: $($size / 1KB) KB"
        return $size
    } catch {
        Write-Warning "Failed to fetch $testId : $_"
        return 0
    }
}

# Download and categorize
foreach ($testId in $testIds) {
    $tempPath = "test-data/temp-$testId.json"
    $size = Fetch-WPTResult $testId $tempPath

    if ($size -gt 0) {
        # Categorize by size
        if ($size -lt 10KB) {
            Move-Item $tempPath "test-data/small/$testId.json" -Force
        } elseif ($size -lt 300KB) {
            Move-Item $tempPath "test-data/medium/$testId.json" -Force
        } else {
            Move-Item $tempPath "test-data/large/$testId.json" -Force
        }
    }
}

Write-Host "Test data preparation complete!"
```

### Step 2: Create Synthetic Test Data (Optional)

For controlled testing, generate JSON files of specific sizes:

```powershell
# generate-test-data.ps1
# Create JSON with N WebPageTest-like entries

param([int]$Runs = 10)

$template = Get-Content "src/test/resources/webpagetest/sample-result.json" | ConvertFrom-Json

# Duplicate runs to increase size
$template.data.runs = @{}
1..$Runs | ForEach-Object {
    $template.data.runs."$_" = $template.data.runs."1"
}

$template | ConvertTo-Json -Depth 20 | Out-File "test-data/synthetic-${Runs}runs.json"
```

---

## Benchmark Execution Script

### Main Benchmark Runner: `run-benchmarks.ps1`

```powershell
param(
    [switch]$FunctionalOnly,
    [switch]$PerformanceOnly,
    [switch]$SkipNative,
    [string]$OutputFile = "benchmark-results.md"
)

$ErrorActionPreference = 'Stop'

# Ensure jq is available
if (-not (Get-Command jq -ErrorAction SilentlyContinue)) {
    throw "jq is not installed or not in PATH"
}

# Ensure jjq is built
if (-not (Test-Path "target/jjq-*-jar-with-dependencies.jar")) {
    Write-Host "Building jjq JAR..."
    mvn clean package -DskipTests
}

$jjqJar = (Get-Item "target/jjq-*-jar-with-dependencies.jar").FullName

if (-not $SkipNative -and -not (Test-Path "target/jjq.exe")) {
    Write-Host "Building jjq native image..."
    .\build-native.ps1 -SkipTests
}

# Initialize results
$results = @{
    Timestamp = Get-Date -Format "yyyy-MM-dd HH:mm:ss"
    Environment = @{
        JQ_Version = (jq --version)
        JJQ_Version = "1.0-SNAPSHOT"
        Java_Version = (java -version 2>&1 | Select-Object -First 1)
        OS = "$([System.Environment]::OSVersion.VersionString)"
    }
    Functional = @()
    Performance = @()
}

# ===== FUNCTIONAL TESTS =====
if (-not $PerformanceOnly) {
    Write-Host "`n===== Running Functional Tests ====="

    $queries = @(
        ".",
        ".data",
        ".data.testId",
        ".data.median.firstView.SpeedIndex",
        ".data.runs | .[0]",
        ".data.runs | .[] | .firstView.SpeedIndex"
    )

    $testFile = "test-data/medium/210910_AiDc3.json"
    if (-not (Test-Path $testFile)) {
        $testFile = "src/test/resources/webpagetest/sample-result.json"
    }

    foreach ($query in $queries) {
        Write-Host "  Testing: $query"

        $jqResult = jq -c $query $testFile 2>&1 | Out-String
        $jjqResult = java -jar $jjqJar -c $query $testFile 2>&1 | Out-String

        $match = ($jqResult.Trim() -eq $jjqResult.Trim())

        $results.Functional += @{
            Query = $query
            Match = $match
            JQ_Output = $jqResult.Trim()
            JJQ_Output = $jjqResult.Trim()
        }

        if (-not $match) {
            Write-Warning "MISMATCH: $query"
        }
    }
}

# ===== PERFORMANCE TESTS =====
if (-not $FunctionalOnly) {
    Write-Host "`n===== Running Performance Tests ====="

    # Test configurations
    $perfTests = @(
        @{
            Name = "Simple Field Access"
            Query = ".data.testId"
            File = "test-data/medium/210910_AiDc3.json"
            Iterations = 100
        },
        @{
            Name = "Deep Nested Access"
            Query = ".data.median.firstView.SpeedIndex"
            File = "test-data/medium/210910_AiDc3.json"
            Iterations = 100
        },
        @{
            Name = "Array Iteration"
            Query = ".data.runs | .[]"
            File = "test-data/medium/210910_AiDc3.json"
            Iterations = 50
        }
    )

    foreach ($test in $perfTests) {
        Write-Host "  Benchmarking: $($test.Name)"

        if (-not (Test-Path $test.File)) {
            $test.File = "src/test/resources/webpagetest/sample-result.json"
        }

        # Benchmark jq
        $jqTime = (Measure-Command {
            1..$test.Iterations | ForEach-Object {
                jq $test.Query $test.File | Out-Null
            }
        }).TotalMilliseconds / $test.Iterations

        # Benchmark jjq JAR
        $jjqJarTime = (Measure-Command {
            1..$test.Iterations | ForEach-Object {
                java -jar $jjqJar $test.Query $test.File | Out-Null
            }
        }).TotalMilliseconds / $test.Iterations

        # Benchmark jjq native (if available)
        $jjqNativeTime = $null
        if (-not $SkipNative -and (Test-Path "target/jjq.exe")) {
            $jjqNativeTime = (Measure-Command {
                1..$test.Iterations | ForEach-Object {
                    & target/jjq.exe $test.Query $test.File | Out-Null
                }
            }).TotalMilliseconds / $test.Iterations
        }

        $results.Performance += @{
            Name = $test.Name
            Query = $test.Query
            Iterations = $test.Iterations
            JQ_Time_ms = [math]::Round($jqTime, 2)
            JJQ_JAR_Time_ms = [math]::Round($jjqJarTime, 2)
            JJQ_Native_Time_ms = if ($jjqNativeTime) { [math]::Round($jjqNativeTime, 2) } else { "N/A" }
            Speedup_Native_vs_JQ = if ($jjqNativeTime) { [math]::Round($jqTime / $jjqNativeTime, 2) } else { "N/A" }
        }
    }
}

# ===== GENERATE REPORT =====
Write-Host "`n===== Generating Report ====="

$report = @"
# JQ vs JJQ Performance Comparison Report

**Generated:** $($results.Timestamp)

## Environment

- **jq Version:** $($results.Environment.JQ_Version)
- **jjq Version:** $($results.Environment.JJQ_Version)
- **Java Version:** $($results.Environment.Java_Version)
- **OS:** $($results.Environment.OS)

---

## Functional Correctness Results

"@

if ($results.Functional.Count -gt 0) {
    $report += "`n| Query | Match | Notes |`n"
    $report += "|-------|-------|-------|`n"

    foreach ($test in $results.Functional) {
        $status = if ($test.Match) { "✅ PASS" } else { "❌ FAIL" }
        $notes = if (-not $test.Match) { "See details below" } else { "" }
        $report += "| ``$($test.Query)`` | $status | $notes |`n"
    }

    $passCount = ($results.Functional | Where-Object { $_.Match }).Count
    $totalCount = $results.Functional.Count
    $report += "`n**Summary:** $passCount / $totalCount tests passed`n"
}

$report += @"

---

## Performance Results

"@

if ($results.Performance.Count -gt 0) {
    $report += "`n| Test | jq (ms) | jjq JAR (ms) | jjq Native (ms) | Native Speedup |`n"
    $report += "|------|---------|--------------|-----------------|----------------|`n"

    foreach ($test in $results.Performance) {
        $speedup = if ($test.Speedup_Native_vs_JQ -ne "N/A") {
            "$($test.Speedup_Native_vs_JQ)x"
        } else {
            "N/A"
        }
        $report += "| $($test.Name) | $($test.JQ_Time_ms) | $($test.JJQ_JAR_Time_ms) | $($test.JJQ_Native_Time_ms) | $speedup |`n"
    }
}

$report += @"

---

## Query Details

"@

foreach ($test in $results.Performance) {
    $report += "`n### $($test.Name)`n"
    $report += "**Query:** ``$($test.Query)```n"
    $report += "**Iterations:** $($test.Iterations)`n`n"
}

$report | Out-File $OutputFile -Encoding UTF8
Write-Host "Report saved to $OutputFile"

# Display summary
Write-Host "`n===== SUMMARY ====="
if ($results.Functional.Count -gt 0) {
    $passCount = ($results.Functional | Where-Object { $_.Match }).Count
    Write-Host "Functional: $passCount / $($results.Functional.Count) PASS"
}
if ($results.Performance.Count -gt 0) {
    Write-Host "`nPerformance (average time per query):"
    $results.Performance | ForEach-Object {
        Write-Host "  $($_.Name): jq=$($_.JQ_Time_ms)ms, jjq-native=$($_.JJQ_Native_Time_ms)ms"
    }
}
```

---

## Expected Outcomes

### Baseline Expectations (Before Optimization)

**Functional:**
- ✅ 100% correctness for supported queries
- ❌ Some queries may not be supported (document limitations)

**Performance:**
- jq (C implementation): **Fastest** (baseline 1.0x)
- jjq JAR: **Slowest** (3-10x slower, JVM startup overhead)
- jjq Native: **Medium** (1.5-3x slower than jq)

**Bottlenecks Expected:**
- Query parsing (no caching)
- BigDecimal operations
- StringBuilder allocations
- Stream overhead

### Post-Optimization Targets

After implementing optimizations from `optim.md`:

**Performance Targets:**
- jjq Native: **0.8-1.2x** jq speed (near parity)
- jjq JAR: **2-4x** slower (improved but still JVM overhead)
- Batch processing: **Faster than jq** (query caching advantage)

---

## Success Criteria

**Phase 1 (Functional):**
- ✅ 100% pass rate on supported features
- ✅ Clear documentation of unsupported features

**Phase 2 (Performance - Baseline):**
- ✅ Establish baseline metrics for all test categories
- ✅ Identify top 3 performance bottlenecks

**Phase 3 (Performance - Optimized):**
- ✅ Native image within 2x of jq for simple queries
- ✅ Batch processing faster than jq (with caching)
- ✅ Memory usage within 3x of jq

---

## Timeline

**Week 1:**
- Create and document test plan ✅
- Fetch WebPageTest data
- Implement functional tests
- Run baseline benchmarks

**Week 2:**
- Implement optimizations (from optim.md)
- Re-run benchmarks
- Document improvements

**Week 3:**
- Final optimization tuning
- Comprehensive performance report
- Publish results

---

## Deliverables

1. **Test Data Package**
   - Small/Medium/Large JSON files from WebPageTest
   - Organized in test-data/ directory

2. **Benchmark Scripts**
   - `fetch-wpt-data.ps1` - Data acquisition
   - `run-benchmarks.ps1` - Automated testing
   - `analyze-results.ps1` - Results analysis

3. **Reports**
   - `functional-comparison.md` - Correctness report
   - `performance-baseline.md` - Pre-optimization metrics
   - `performance-final.md` - Post-optimization metrics

4. **Documentation**
   - Known limitations vs jq
   - Performance characteristics
   - Optimization recommendations

---

## Next Steps

1. ✅ Review and approve test plan
2. ⏳ Build jjq JAR and native image
3. ⏳ Fetch WebPageTest data
4. ⏳ Run functional tests
5. ⏳ Run baseline performance benchmarks
6. ⏳ Implement optimizations
7. ⏳ Re-benchmark and compare
8. ⏳ Document findings
