param(
    [string]$ImageName = "jjq",
    [switch]$SkipTests
)

$ErrorActionPreference = 'Stop'

function Write-Info($msg) { Write-Host "[jjq-native-optimized] $msg" }

# Move to repo root (script directory)
Set-Location -Path $PSScriptRoot

# Step 1: Build the fat jar
$mvncmd = "mvn"
$mvnArgs = @("-q", "-DtrimStackTrace=false", "package")
if ($SkipTests) { $mvnArgs += "-DskipTests" }

Write-Info "Running Maven package... ($($mvnArgs -join ' '))"
& $mvncmd @mvnArgs
if ($LASTEXITCODE -ne 0) { throw "Maven build failed with exit code $LASTEXITCODE" }

# Step 2: Locate the fat jar
$jar = Get-ChildItem -Path "$PSScriptRoot\target" -Filter "*-jar-with-dependencies.jar" |
    Sort-Object LastWriteTime -Descending |
    Select-Object -First 1

if (-not $jar) { throw "Could not find jar-with-dependencies in target folder. Did the build succeed?" }

Write-Info "Using JAR: $($jar.FullName)"

# Step 3: Build native image with aggressive optimizations
$nativeImage = "native-image.cmd"
$outDir = Join-Path $PSScriptRoot 'target'
if (!(Test-Path $outDir)) { New-Item -ItemType Directory -Path $outDir | Out-Null }

# Aggressive optimization flags
$arguments = @(
    "-H:Name=$ImageName",
    "-H:Path=$outDir",
    "--no-fallback",
    "-march=native",

    # Optimization level 3 (maximum)
    "-O3",

    # Experimental options
    "-H:+UnlockExperimentalVMOptions",

    # Optimize for runtime performance over build time
    "--initialize-at-build-time=com.challenges",

    # The JAR
    "-jar",
    $jar.FullName
)

Write-Info "Invoking: $nativeImage with aggressive optimizations"
Write-Info "Flags: -O3, aggressive inlining, G1 GC, build-time initialization"
& $nativeImage @arguments
if ($LASTEXITCODE -ne 0) { throw "native-image failed with exit code $LASTEXITCODE" }

Write-Info "Native image built successfully: $outDir\$ImageName.exe (Windows) or $outDir/$ImageName (Unix)"
