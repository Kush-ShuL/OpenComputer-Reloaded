param(
    [Parameter(Mandatory = $true)]
    [ValidateSet('menus', 'effects', 'activity', 'models', 'text')]
    [string]$Focus,
    [Parameter(Mandatory = $true)]
    [ValidatePattern('^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$')]
    [string]$RunId,
    [string]$MenuFocus = 'all',
    [ValidateSet('all', 'world')]
    [string]$TextFocus = 'all',
    [ValidateSet('all', 'labels')]
    [string]$EffectsFocus = 'all',
    [ValidateSet('all', 'supplement')]
    [string]$ModelFocus = 'all'
)

$ErrorActionPreference = 'Stop'
$projectDirectory = Split-Path -Parent $PSScriptRoot
if (!$env:JAVA_HOME) { $env:JAVA_HOME = 'C:\Program Files\Microsoft\jdk-21.0.7.6-hotspot' }
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
$arguments = @('--offline', '-p', 'tools/api-compat-addon',
    '-I', (Join-Path $projectDirectory 'build/launch-support/local-repositories.init.gradle'),
    '-PocApiCoordinate=com.github.Kush-ShuL:OpenComputer-Reloaded:api-8.0.0-alpha.1',
    "-PocModJar=$projectDirectory/build/hardware/libs/OpenComputer-Reloaded-0.1.0-SNAPSHOT.jar",
    "-PlegacyApiJar=$projectDirectory/build/api-compat-addon/legacy-sdk/opencomputers-api-two-arg.jar",
    "-PocJeiJar=$projectDirectory/build/jei-integration-deps/maven/mezz/jei/jei-1.21.1-neoforge/19.21.0.247/jei-1.21.1-neoforge-19.21.0.247.jar",
    "-PocJadeJar=$projectDirectory/build/libraries/Jade-1.21.1-NeoForge-15.10.6.jar",
    "-PocIntegrationClientRunId=$RunId", "-PocIntegrationClientFocus=$Focus", "-PocMenuFocus=$MenuFocus", "-PocTextFocus=$TextFocus", "-PocEffectsFocus=$EffectsFocus", "-PocModelFocus=$ModelFocus",
    'runIntegrationClient', '--console=plain')
Push-Location $projectDirectory
try {
    & .\gradlew.bat @arguments
    exit $LASTEXITCODE
} finally { Pop-Location }
