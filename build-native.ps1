param(
    [string]$ImageName = "jjq",
    [switch]$SkipTests,
    [string[]]$NativeArgs
)

$ErrorActionPreference = 'Stop'

function Write-Info($msg) { Write-Host "[jjq-native] $msg" }

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

# Step 3: Build native image
$nativeImage = "native-image.cmd"
$outDir = Join-Path $PSScriptRoot 'target'
if (!(Test-Path $outDir)) { New-Item -ItemType Directory -Path $outDir | Out-Null }
$arguments = @("-H:Name=$ImageName", "-H:Path=$outDir", "--no-fallback", "--install-exit-handlers", "-jar", $jar.FullName)
if ($NativeArgs) { $arguments += $NativeArgs }

Write-Info "Invoking: $nativeImage $($arguments -join ' ')"
& $nativeImage @arguments
if ($LASTEXITCODE -ne 0) { throw "native-image failed with exit code $LASTEXITCODE" }

Write-Info "Native image built successfully: $outDir\$ImageName.exe (Windows) or $outDir/$ImageName (Unix)"
