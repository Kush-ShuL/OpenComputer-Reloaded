param(
    [Parameter(Mandatory = $true)]
    [ValidatePattern('^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$')]
    [string]$RunId,
    [ValidateRange(1024, 65535)]
    [int]$Port = 25585
)

$ErrorActionPreference = 'Stop'
$projectDirectory = Split-Path -Parent $PSScriptRoot
$runDirectory = Join-Path $projectDirectory "build\api-compat-addon\multiplayer\$RunId"
if (!$env:JAVA_HOME) { $env:JAVA_HOME = 'C:\Program Files\Microsoft\jdk-21.0.7.6-hotspot' }
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
Push-Location $projectDirectory
try {
    $arguments = @('--offline', '-p', 'tools/api-compat-addon',
        '-I', (Join-Path $projectDirectory 'build/launch-support/local-repositories.init.gradle'),
        '-PocApiCoordinate=com.github.Kush-ShuL:OpenComputer-Reloaded:api-8.0.0-alpha.1',
        "-PocModJar=$projectDirectory/build/hardware/libs/OpenComputer-Reloaded-0.1.0-SNAPSHOT.jar",
        "-PlegacyApiJar=$projectDirectory/build/api-compat-addon/legacy-sdk/opencomputers-api-two-arg.jar",
        "-PocMultiplayerRunId=$RunId", "-PocMultiplayerPort=$Port", 'writeMultiplayerLaunchers', '--console=plain')
    & .\gradlew.bat @arguments
    if ($LASTEXITCODE -ne 0) { throw "Multiplayer preparation failed: $LASTEXITCODE" }
    # A real world-local program catalog must exist before LevelEvent.Load/login;
    # its normal production DiskSync then populates both independent clients.
    $lootDirectory = Join-Path $runDirectory 'server\ocfixture-tcp-world\opencomputers\loot'
    $programDirectory = Join-Path $lootDirectory 'tcp-program'
    New-Item -ItemType Directory -Force -Path $programDirectory | Out-Null
    [System.IO.File]::WriteAllText((Join-Path $lootDirectory 'loot.properties'), "tcp-program=TCP fixture program:1`n", [System.Text.UTF8Encoding]::new($false))
    [System.IO.File]::WriteAllText((Join-Path $programDirectory 'init.lua'), "return 'actual TCP world program'`n", [System.Text.UTF8Encoding]::new($false))
    $startActor = {
        param([string]$actor)
        $directory = Join-Path $runDirectory $actor
        $process = Start-Process -FilePath 'powershell.exe' -ArgumentList '-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', 'launch.ps1' -WorkingDirectory $directory -WindowStyle Hidden -PassThru `
            -RedirectStandardOutput (Join-Path $directory 'console.log') -RedirectStandardError (Join-Path $directory 'console-error.log')
        Write-Host "TCP_ACTOR $actor pid=$($process.Id) directory=$directory"
        return $process
    }
    $serverProcess = & $startActor 'server'
    $deadline = [DateTime]::UtcNow.AddMinutes(2)
    while (!(Test-Path (Join-Path $runDirectory 'server/server-ready.txt'))) {
        $serverProcess.Refresh()
        if ($serverProcess.HasExited) { throw 'The owned server exited before readiness; inspect server/console.log.' }
        if ([DateTime]::UtcNow -gt $deadline) { throw "Owned server still running as PID $($serverProcess.Id); inspect its console before taking further action." }
        Start-Sleep -Seconds 1
    }
    & $startActor 'OcTcpOne' | Out-Null
    & $startActor 'OcTcpTwo' | Out-Null
} finally { Pop-Location }
