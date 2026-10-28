param(
    [Parameter(Mandatory = $true)]
    [ValidatePattern('^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$')]
    [string]$RunId,
    [ValidateSet('prepare', 'server', 'client', 'both')]
    [string]$Mode = 'prepare',
    [switch]$SkipPrepare
)

$ErrorActionPreference = 'Stop'
$projectDirectory = Split-Path -Parent $PSScriptRoot
$evidenceDirectory = Join-Path $projectDirectory "build/final-runtime/$RunId"
$jar = Join-Path $projectDirectory 'build/hardware/libs/OpenComputer-Reloaded-0.1.0-SNAPSHOT.jar'
if (!$env:JAVA_HOME) { $env:JAVA_HOME = 'C:\Program Files\Microsoft\jdk-21.0.7.6-hotspot' }
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"

function Quote-NativeArgument([string]$Value) {
    if ($Value.Length -gt 0 -and $Value -notmatch '[\s"]') { return $Value }
    $encoded = [regex]::Replace($Value, '(\\*)"', '$1$1\"')
    $encoded = [regex]::Replace($encoded, '(\\+)$', '$1$1')
    return '"' + $encoded + '"'
}

function Save-Evidence([string]$Actor, [string]$Status, [string[]]$Lines) {
    @("status=$Status", "scope=production-$Actor-startup", "jar=$jar", "jar_sha256=$expectedHash") + $Lines |
        Set-Content -LiteralPath (Join-Path $evidenceDirectory "$Actor-results.txt") -Encoding UTF8
}

function Read-NativeLog([string]$Path, [datetime]$Started) {
    if (!(Test-Path -LiteralPath $Path -PathType Leaf)) { return '' }
    if ((Get-Item -LiteralPath $Path).LastWriteTimeUtc -lt $Started.ToUniversalTime().AddSeconds(-2)) { return '' }
    $stream = [IO.File]::Open($Path, [IO.FileMode]::Open, [IO.FileAccess]::Read,
        ([IO.FileShare]::ReadWrite -bor [IO.FileShare]::Delete))
    $reader = [IO.StreamReader]::new($stream)
    try { return $reader.ReadToEnd() } finally { $reader.Dispose() }
}

function Assert-ProductionMods([string]$Text) {
    $table = [regex]::Match($Text, '(?ms)^\s*Mod List:\s*\r?\n\s*Name Version \(Mod Id\)\s*\r?\n(?<rows>.*?)(?=^\[|\z)')
    if (!$table.Success) { throw 'The actual NeoForge mod list has not been recorded' }
    $ids = @([regex]::Matches($table.Groups['rows'].Value, '(?m)^\s*[^\r\n]+\((?<id>[a-z0-9_.-]+)\)\s*$') | ForEach-Object { $_.Groups['id'].Value })
    if ((($ids | Sort-Object) -join ',') -ne 'minecraft,neoforge,opencomputers') { throw "Unexpected actual runtime mods: $($ids -join ',')" }
    if (!$Text.Contains([IO.Path]::GetFileName($jar))) { throw 'The actual log does not identify the selected runtime JAR' }
    if ($Text -match 'API_FIXTURE|DEVICE_FIXTURE|ocfixture\.') { throw 'Fixture activity appeared in the production log' }
    return $ids
}

Push-Location $projectDirectory
try {
    if (!$SkipPrepare) {
        $gradleArguments = @('--offline', '-p', 'tools/api-compat-addon',
            '-I', (Join-Path $projectDirectory 'build/launch-support/local-repositories.init.gradle'),
            "-PocModJar=$jar", "-PocFinalRuntimeRunId=$RunId", 'writeFinalRuntimeLaunchers', '--console=plain')
        & .\gradlew.bat @gradleArguments
        if ($LASTEXITCODE -ne 0) { throw 'Final runtime launcher preparation failed' }
    }
    $manifest = Get-Content -LiteralPath (Join-Path $evidenceDirectory 'runtime-manifest.json') -Raw | ConvertFrom-Json
    $expectedHash = (Get-FileHash -LiteralPath $jar -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($manifest.jarSha256 -ne $expectedHash) { throw 'The main runtime JAR changed after launcher preparation; select a new run ID' }
    if ($Mode -eq 'prepare') { Write-Output "Prepared clean production launchers: $evidenceDirectory"; exit 0 }

    if ($Mode -in @('server', 'both')) {
        $launch = Get-Content -LiteralPath (Join-Path $evidenceDirectory 'server-launch.json') -Raw | ConvertFrom-Json
        $nativeLog = Join-Path $launch.workingDirectory 'logs/latest.log'
        if (Test-Path -LiteralPath $nativeLog) { throw 'The final dedicated-server run already has a log; choose a fresh run ID' }
        $info = New-Object System.Diagnostics.ProcessStartInfo
        $info.FileName = $launch.executable
        $info.WorkingDirectory = $launch.workingDirectory
        $info.Arguments = (@($launch.arguments | ForEach-Object { Quote-NativeArgument $_ }) -join ' ')
        $info.UseShellExecute = $false; $info.CreateNoWindow = $true
        $info.RedirectStandardInput = $true; $info.RedirectStandardOutput = $true; $info.RedirectStandardError = $true
        foreach ($entry in $launch.environment.PSObject.Properties) { $info.EnvironmentVariables[$entry.Name] = [string]$entry.Value }
        $server = New-Object System.Diagnostics.Process
        $server.StartInfo = $info
        [void]$server.Start()
        $stdout = $server.StandardOutput.ReadToEndAsync(); $stderr = $server.StandardError.ReadToEndAsync()
        Write-Output "Owned final server PID=$($server.Id)"
        $started = $server.StartTime; $deadline = [datetime]::UtcNow.AddMinutes(5); $ready = $false
        while (!$server.HasExited -and [datetime]::UtcNow -lt $deadline) {
            $text = Read-NativeLog $nativeLog $started
            if ($text -match 'Done \([0-9.,]+s\)!') { $ready = $true; break }
            Start-Sleep -Milliseconds 250
        }
        if (!$ready) {
            Save-Evidence 'server' 'FAIL' @("pid=$($server.Id)", "alive=$(!$server.HasExited)", 'reason=normal server did not reach Done; no process was killed')
            throw "Final server startup did not complete; inspect $nativeLog (owned PID $($server.Id))"
        }
        $modError = $null
        try { $mods = Assert-ProductionMods $text } catch { $modError = $_ }
        $server.StandardInput.WriteLine('stop'); $server.StandardInput.Flush()
        if (!$server.WaitForExit(30000)) {
            Save-Evidence 'server' 'FAIL' @("pid=$($server.Id)", 'alive=true', 'reason=normal console stop is pending; no process was killed')
            throw "Final server did not finish its normal stop (owned PID $($server.Id))"
        }
        $stdout.GetAwaiter().GetResult() | Set-Content -LiteralPath (Join-Path $evidenceDirectory 'server-stdout.log') -Encoding UTF8
        $stderr.GetAwaiter().GetResult() | Set-Content -LiteralPath (Join-Path $evidenceDirectory 'server-stderr.log') -Encoding UTF8
        Copy-Item -LiteralPath $nativeLog -Destination (Join-Path $evidenceDirectory 'server-latest.log')
        $text = [IO.File]::ReadAllText($nativeLog)
        if ($server.ExitCode -ne 0 -or $text -notmatch 'All dimensions are saved') { throw 'The final server did not exit normally after saving all dimensions' }
        if ($modError) {
            Save-Evidence 'server' 'FAIL' @("pid=$($server.Id)", 'exit_code=0', "reason=$modError")
            throw $modError
        }
        if ((Get-FileHash -LiteralPath $jar -Algorithm SHA256).Hash.ToLowerInvariant() -ne $expectedHash) { throw 'The runtime JAR changed during server acceptance' }
        Save-Evidence 'server' 'PASS' @("pid=$($server.Id)", 'exit_code=0', 'normal_console_stop=true', 'all_dimensions_saved=true', "actual_mods=$($mods -join ',')")
        Write-Output 'Final dedicated server loaded the packaged main JAR and stopped normally.'
    }

    if ($Mode -in @('client', 'both')) {
        $launch = Get-Content -LiteralPath (Join-Path $evidenceDirectory 'client-launch.json') -Raw | ConvertFrom-Json
        $expectedDirectory = [IO.Path]::GetFullPath((Join-Path $projectDirectory 'run'))
        if ([IO.Path]::GetFullPath($launch.workingDirectory) -ne $expectedDirectory) { throw 'The final client must use the existing regular run directory' }
        if (!(Test-Path -LiteralPath (Join-Path $expectedDirectory 'saves/OC-Showcase-20261028/level.dat') -PathType Leaf)) { throw 'The existing showcase save is missing' }
        foreach ($entry in $launch.environment.PSObject.Properties) { [Environment]::SetEnvironmentVariable($entry.Name, [string]$entry.Value, 'Process') }
        $arguments = @($launch.arguments | ForEach-Object { Quote-NativeArgument $_ }) -join ' '
        $client = Start-Process -FilePath $launch.executable -ArgumentList $arguments -WorkingDirectory $launch.workingDirectory -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $evidenceDirectory 'client-stdout.log') -RedirectStandardError (Join-Path $evidenceDirectory 'client-stderr.log')
        Write-Output "Owned final client PID=$($client.Id); normal quick play opens OC-Showcase-20261028"
        $nativeLog = Join-Path $expectedDirectory 'logs/latest.log'; $started = $client.StartTime
        $deadline = [datetime]::UtcNow.AddMinutes(5); $ready = $false
        while (!$client.HasExited -and [datetime]::UtcNow -lt $deadline) {
            $text = Read-NativeLog $nativeLog $started
            if ($text -match 'Dev\[local:[^\]]+\] logged in with entity id') { $ready = $true; break }
            Start-Sleep -Milliseconds 250
        }
        if (!$ready) {
            Save-Evidence 'client' 'FAIL' @("pid=$($client.Id)", "alive=$(!$client.HasExited)", 'reason=native quick play did not join the existing base; client left untouched')
            throw "Final client did not join its save; inspect $nativeLog (owned PID $($client.Id))"
        }
        $settleDeadline = [datetime]::UtcNow.AddSeconds(10)
        while (!$client.HasExited -and [datetime]::UtcNow -lt $settleDeadline) { Start-Sleep -Milliseconds 250 }
        if ($client.HasExited) { throw 'The final client exited after joining the base' }
        $text = Read-NativeLog $nativeLog $started
        $mods = Assert-ProductionMods $text
        [IO.File]::WriteAllText((Join-Path $evidenceDirectory 'client-latest.log'), $text)
        if ((Get-FileHash -LiteralPath $jar -Algorithm SHA256).Hash.ToLowerInvariant() -ne $expectedHash) { throw 'The runtime JAR changed during client acceptance' }
        Save-Evidence 'client' 'PASS' @("pid=$($client.Id)", 'alive=true', 'world=OC-Showcase-20261028', 'native_quick_play=true', "actual_mods=$($mods -join ',')", 'client_left_interactive=true')
        Write-Output 'Final normal client joined the existing base and remains interactive.'
    }
} catch {
    if ($server -and !$server.HasExited) {
        $server.StandardInput.WriteLine('stop'); $server.StandardInput.Flush()
        [void]$server.WaitForExit(30000)
    }
    throw
} finally { Pop-Location }
