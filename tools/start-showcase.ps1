param([switch]$Run, [switch]$ResumeBuild, [switch]$RepairPrint)

$ErrorActionPreference = 'Stop'
$projectDirectory = Split-Path -Parent $PSScriptRoot
$launchDirectory = Join-Path $projectDirectory 'build/launch-support'
if (!(Test-Path -LiteralPath $launchDirectory -PathType Container)) {
    New-Item -ItemType Directory -Path $launchDirectory | Out-Null
}
if (!$Run) {
    $helperArguments = @('-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', ('"' + $PSCommandPath + '"'), '-Run')
    if ($ResumeBuild) { $helperArguments += '-ResumeBuild' }
    if ($RepairPrint) { $helperArguments += '-RepairPrint' }
    $helper = Start-Process -FilePath 'powershell.exe' -ArgumentList $helperArguments -WorkingDirectory $projectDirectory -WindowStyle Hidden -PassThru
    Write-Output "Showcase client helper started: PID $($helper.Id)"
    Write-Output "Save: $projectDirectory\run\saves\OC-Showcase-20261028"
    Write-Output "Log: $launchDirectory\windows-showcase-client.log"
    exit 0
}

if (!$env:JAVA_HOME) { $env:JAVA_HOME = 'C:\Program Files\Microsoft\jdk-21.0.7.6-hotspot' }
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
$arguments = @('--offline', '-p', 'tools/api-compat-addon',
    '-I', (Join-Path $launchDirectory 'local-repositories.init.gradle'),
    '-PocApiCoordinate=com.github.Kush-ShuL:OpenComputer-Reloaded:api-8.0.0-alpha.1',
    "-PocModJar=$projectDirectory/build/hardware/libs/OpenComputer-Reloaded-0.1.0-SNAPSHOT.jar",
    "-PlegacyApiJar=$projectDirectory/build/api-compat-addon/legacy-sdk/opencomputers-api-two-arg.jar",
    "-PocJeiJar=$projectDirectory/build/jei-integration-deps/maven/mezz/jei/jei-1.21.1-neoforge/19.21.0.247/jei-1.21.1-neoforge-19.21.0.247.jar",
    "-PocJadeJar=$projectDirectory/build/libraries/Jade-1.21.1-NeoForge-15.10.6.jar",
    'runShowcaseClient', '--console=plain')
if ($ResumeBuild) { $arguments += '-PocShowcaseResumeBuild=true' }
if ($RepairPrint) { $arguments += '-PocShowcaseRepairPrint=true' }
$command = (@((Join-Path $projectDirectory 'gradlew.bat')) + $arguments | ForEach-Object { '"' + $_ + '"' }) -join ' '
$command += ' > "' + (Join-Path $launchDirectory 'windows-showcase-client.log') + '" 2>&1'
Push-Location $projectDirectory
try {
    & $env:ComSpec /d /s /c ('"' + $command + '"')
    exit $LASTEXITCODE
} finally { Pop-Location }
