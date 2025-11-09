param(
    [string]$ImageName = "jjq",
    [switch]$SkipTests
)

$ErrorActionPreference = 'Stop'

function Write-Info($msg) { Write-Host "[jjq-pgo] $msg" -ForegroundColor Cyan }
function Write-Success($msg) { Write-Host "[jjq-pgo] $msg" -ForegroundColor Green }

# Move to repo root
Set-Location -Path $PSScriptRoot

# Step 1: Build the JAR
$mvncmd = "mvn"
$mvnArgs = @("-q", "-DtrimStackTrace=false", "package")
if ($SkipTests) { $mvnArgs += "-DskipTests" }

Write-Info "Step 1/5: Building JAR..."
& $mvncmd @mvnArgs
if ($LASTEXITCODE -ne 0) { throw "Maven build failed" }

$jar = Get-ChildItem -Path "$PSScriptRoot\target" -Filter "*-jar-with-dependencies.jar" |
    Sort-Object LastWriteTime -Descending | Select-Object -First 1
if (-not $jar) { throw "JAR not found" }
Write-Success "JAR built: $($jar.Name)"

# Step 2: Build INSTRUMENTED native image for profiling
Write-Info "Step 2/5: Building instrumented native image for profiling..."
$outDir = Join-Path $PSScriptRoot 'target'
$nativeImage = "native-image.cmd"

$instrumentedArgs = @(
    "-H:Name=jjq_instrumented",
    "-H:Path=$outDir",
    "--no-fallback",
    "-march=native",
    "-O3",
    "-H:+UnlockExperimentalVMOptions",
    "--initialize-at-build-time=com.challenges",
    "--pgo-instrument",
    "-jar",
    $jar.FullName
)

Write-Info "Building instrumented image (this may take a while)..."
& $nativeImage @instrumentedArgs
if ($LASTEXITCODE -ne 0) { throw "Instrumented build failed" }
Write-Success "Instrumented image built"

# Step 3: Run representative workload to collect profile
Write-Info "Step 3/5: Collecting runtime profile data..."
$instrumentedExe = Join-Path $outDir "jjq_instrumented.exe"
$profileDir = Join-Path $outDir "pgo-profiles"
$env:GRAAL_PGO_DATA = $profileDir

# Create profile directory
if (Test-Path $profileDir) { Remove-Item $profileDir -Recurse -Force }
New-Item -ItemType Directory -Path $profileDir | Out-Null

# Run representative queries on test data
$testFile = "test-data\small\210910_AiDc3.json"
if (!(Test-Path $testFile)) {
    $testFile = "test-data\medium\wpt-result.json"
}

$queries = @(
    ".",
    ".data",
    ".data | .testId",
    ".data | .median | .firstView | .SpeedIndex",
    ".data | .runs",
    ".data | .median"
)

Write-Info "Running $($queries.Count) representative queries..."
foreach ($query in $queries) {
    Write-Info "  Query: $query"
    & $instrumentedExe "-c" $query $testFile | Out-Null
    if ($LASTEXITCODE -ne 0) { Write-Warning "Query failed, continuing..." }
}

# Run array iteration and map operations
$arrayJson = '{"items":[1,2,3,4,5]}'
$arrayTempFile = Join-Path $env:TEMP "jjq-pgo-test.json"
$arrayJson | Out-File -FilePath $arrayTempFile -Encoding UTF8 -NoNewline

$arrayQueries = @(".items", ".items[]", ".items | map(. * 2)")
foreach ($query in $arrayQueries) {
    Write-Info "  Query: $query"
    $result = & $instrumentedExe "-c" $query $arrayTempFile 2>&1
}
Remove-Item $arrayTempFile -ErrorAction SilentlyContinue

Write-Success "Profile data collected in: $profileDir"

# Step 4: Build OPTIMIZED native image using profile data
Write-Info "Step 4/5: Building PGO-optimized native image..."

$optimizedArgs = @(
    "-H:Name=$ImageName",
    "-H:Path=$outDir",
    "--no-fallback",
    "-march=native",
    "-O3",
    "-H:+UnlockExperimentalVMOptions",
    "--initialize-at-build-time=com.challenges",
    "--pgo=$profileDir",
    "-jar",
    $jar.FullName
)

Write-Info "Building final optimized image with PGO data..."
& $nativeImage @optimizedArgs
if ($LASTEXITCODE -ne 0) { throw "PGO-optimized build failed" }

# Step 5: Cleanup
Write-Info "Step 5/5: Cleaning up..."
if (Test-Path $instrumentedExe) { Remove-Item $instrumentedExe -Force }
if (Test-Path $profileDir) { Remove-Item $profileDir -Recurse -Force }

Write-Success "PGO-optimized native image built: $outDir\$ImageName.exe"
Write-Info "Expected performance improvement: 1.3-1.7x over non-PGO build"
