param(
    [string]$Paperclip = 'E:\down\luminolpaperclip\luminol-26.2-paperclip.jar',
    [string]$PluginJar = 'target\ExcellentEnchants-5.4.3.jar',
    [int]$StartupTimeoutSeconds = 180
)

$ErrorActionPreference = 'Stop'

$root = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$utf8 = [System.Text.UTF8Encoding]::new($false)

function Resolve-ProjectPath {
    param([string]$Path)

    if ([System.IO.Path]::IsPathRooted($Path)) {
        return [System.IO.Path]::GetFullPath($Path)
    }
    return [System.IO.Path]::GetFullPath((Join-Path $root $Path))
}

function Read-SharedUtf8 {
    param([string]$Path)

    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        return ''
    }

    $stream = [System.IO.FileStream]::new($Path, [System.IO.FileMode]::Open, [System.IO.FileAccess]::Read, [System.IO.FileShare]::ReadWrite)
    try {
        $reader = [System.IO.StreamReader]::new($stream, [System.Text.Encoding]::UTF8, $true, 4096, $true)
        try {
            return $reader.ReadToEnd()
        }
        finally {
            $reader.Dispose()
        }
    }
    finally {
        $stream.Dispose()
    }
}

function Install-MavenCentralFile {
    param([string]$RelativePath)

    $destination = Join-Path (Join-Path $serverDir 'libraries') ($RelativePath -replace '/', '\')
    if (Test-Path -LiteralPath $destination -PathType Leaf) {
        return
    }

    $parent = Split-Path -Parent $destination
    New-Item -ItemType Directory -Path $parent -Force | Out-Null
    $url = "https://repo1.maven.org/maven2/$RelativePath"
    Invoke-WebRequest -UseBasicParsing -Proxy 'http://localhost:7897' -Uri $url -OutFile $destination
}

$paperclipPath = Resolve-ProjectPath $Paperclip
$pluginJarPath = Resolve-ProjectPath $PluginJar
if (-not (Test-Path -LiteralPath $paperclipPath -PathType Leaf)) {
    throw "Luminol paperclip not found: $paperclipPath"
}
if (-not (Test-Path -LiteralPath $pluginJarPath -PathType Leaf)) {
    throw "ExcellentEnchants JAR not found: $pluginJarPath"
}

$tempRoot = [System.IO.Path]::GetFullPath($env:TEMP).TrimEnd('\') + '\'
$serverDir = [System.IO.Path]::GetFullPath((Join-Path $env:TEMP 'excellentenchants-luminol-smoke'))
if (-not ($serverDir + '\').StartsWith($tempRoot, [System.StringComparison]::OrdinalIgnoreCase)) {
    throw "Refusing to prepare smoke server outside temp: $serverDir"
}
if (Test-Path -LiteralPath $serverDir) {
    Remove-Item -Recurse -Force -LiteralPath $serverDir
}

$pluginsDir = Join-Path $serverDir 'plugins'
$configDir = Join-Path $pluginsDir 'ExcellentEnchants'
New-Item -ItemType Directory -Path $configDir -Force | Out-Null

$paperclipCache = Join-Path (Split-Path -Parent $paperclipPath) '.cache'
if (Test-Path -LiteralPath $paperclipCache -PathType Container) {
    Get-ChildItem -LiteralPath $paperclipCache -Force | ForEach-Object {
        Copy-Item -LiteralPath $_.FullName -Destination $serverDir -Recurse -Force
    }
}

$serverJar = Join-Path $serverDir 'server.jar'
$installedPlugin = Join-Path $pluginsDir 'ExcellentEnchants-5.4.3.jar'
$nightCoreJar = Join-Path $pluginsDir 'nightcore-2.16.4.jar'
Copy-Item -LiteralPath $paperclipPath -Destination $serverJar
Copy-Item -LiteralPath $pluginJarPath -Destination $installedPlugin

$nightCoreUrl = 'https://hangar.papermc.io/api/v1/projects/NightExpress/nightcore/versions/2.16.4/PAPER/download'
Invoke-WebRequest -UseBasicParsing -Proxy 'http://localhost:7897' -Uri $nightCoreUrl -OutFile $nightCoreJar
if ((Get-Item -LiteralPath $nightCoreJar).Length -lt 1400000) {
    throw "Downloaded NightCore JAR is unexpectedly small: $nightCoreJar"
}

Add-Type -AssemblyName System.IO.Compression.FileSystem
$nightCoreZip = [System.IO.Compression.ZipFile]::OpenRead($nightCoreJar)
try {
    if ($null -eq $nightCoreZip.GetEntry('su/nightexpress/nightcore/util/LowerCase.class')) {
        throw 'Downloaded NightCore JAR is not the complete Paper release'
    }
}
finally {
    $nightCoreZip.Dispose()
}

foreach ($relativePath in @(
    'com/zaxxer/HikariCP/6.3.2/HikariCP-6.3.2.jar',
    'com/zaxxer/HikariCP/6.3.2/HikariCP-6.3.2.pom',
    'it/unimi/dsi/fastutil-core/8.5.16/fastutil-core-8.5.16.jar',
    'it/unimi/dsi/fastutil-core/8.5.16/fastutil-core-8.5.16.pom',
    'org/slf4j/slf4j-api/2.0.17/slf4j-api-2.0.17.jar',
    'org/slf4j/slf4j-api/2.0.17/slf4j-api-2.0.17.pom',
    'org/slf4j/slf4j-parent/2.0.17/slf4j-parent-2.0.17.pom',
    'org/slf4j/slf4j-bom/2.0.17/slf4j-bom-2.0.17.pom'
)) {
    Install-MavenCentralFile $relativePath
}

$portProbe = [System.Net.Sockets.TcpListener]::new([System.Net.IPAddress]::Loopback, 0)
$portProbe.Start()
try {
    $serverPort = ([System.Net.IPEndPoint]$portProbe.LocalEndpoint).Port
}
finally {
    $portProbe.Stop()
}

[System.IO.File]::WriteAllText((Join-Path $serverDir 'eula.txt'), "eula=true`n", $utf8)
[System.IO.File]::WriteAllText((Join-Path $serverDir 'server.properties'), @"
server-ip=127.0.0.1
server-port=$serverPort
online-mode=false
enable-rcon=false
enable-query=false
level-name=world
view-distance=2
simulation-distance=2
spawn-protection=0
max-players=1
motd=ExcellentEnchants Luminol smoke
"@, $utf8)
[System.IO.File]::WriteAllText((Join-Path $configDir 'config.yml'), @"
Modules:
  EnchantTooltip: false
"@, $utf8)

$javaCommand = Get-Command java -ErrorAction Stop
$java = $javaCommand.Source
$savedErrorActionPreference = $ErrorActionPreference
$ErrorActionPreference = 'Continue'
$javaVersion = (& $java -version 2>&1) -join "`n"
$javaVersionExitCode = $LASTEXITCODE
$ErrorActionPreference = $savedErrorActionPreference
if ($javaVersionExitCode -ne 0) {
    throw "Java version check failed with code $javaVersionExitCode`n$javaVersion"
}
if ($javaVersion -notmatch 'version "25(?:\.|\")') {
    throw "Java 25 is required for the smoke test. Found:`n$javaVersion"
}

$stdoutPath = Join-Path $serverDir 'console.log'
$stderrPath = Join-Path $serverDir 'console-error.log'
$latestLogPath = Join-Path $serverDir 'logs\latest.log'
$resultDir = Join-Path $root 'target'
$resultLog = Join-Path $resultDir 'smoke-luminol.log'
New-Item -ItemType Directory -Path $resultDir -Force | Out-Null

$startInfo = [System.Diagnostics.ProcessStartInfo]::new()
$startInfo.FileName = $java
$startInfo.Arguments = '-Xms512M -Xmx2G -Dhttp.proxyHost=localhost -Dhttp.proxyPort=7897 -Dhttps.proxyHost=localhost -Dhttps.proxyPort=7897 -jar server.jar --nogui'
$startInfo.WorkingDirectory = $serverDir
$startInfo.UseShellExecute = $false
$startInfo.CreateNoWindow = $true
$startInfo.RedirectStandardInput = $true
$startInfo.RedirectStandardOutput = $true
$startInfo.RedirectStandardError = $true
$startInfo.StandardOutputEncoding = $utf8
$startInfo.StandardErrorEncoding = $utf8

$process = [System.Diagnostics.Process]::new()
$process.StartInfo = $startInfo
$stdoutFile = $null
$stderrFile = $null
$stdoutCopy = $null
$stderrCopy = $null
$ready = $false
$normalStopSent = $false
$exitCode = $null
$started = $false

try {
    if (-not $process.Start()) {
        throw 'Luminol process did not start'
    }
    $started = $true

    $stdoutFile = [System.IO.FileStream]::new($stdoutPath, [System.IO.FileMode]::Create, [System.IO.FileAccess]::Write, [System.IO.FileShare]::ReadWrite)
    $stderrFile = [System.IO.FileStream]::new($stderrPath, [System.IO.FileMode]::Create, [System.IO.FileAccess]::Write, [System.IO.FileShare]::ReadWrite)
    $stdoutCopy = $process.StandardOutput.BaseStream.CopyToAsync($stdoutFile)
    $stderrCopy = $process.StandardError.BaseStream.CopyToAsync($stderrFile)

    $deadline = [DateTime]::UtcNow.AddSeconds($StartupTimeoutSeconds)
    while ([DateTime]::UtcNow -lt $deadline) {
        if ($process.HasExited) {
            break
        }
        $console = (Read-SharedUtf8 $stdoutPath) + "`n" + (Read-SharedUtf8 $latestLogPath)
        if ($console -match '(?m)Done \([0-9.]+s\)!') {
            $ready = $true
            break
        }
        Start-Sleep -Milliseconds 500
    }

    if (-not $ready) {
        if ($process.HasExited) {
            throw "Luminol exited before startup completed with code $($process.ExitCode)"
        }
        throw "Luminol did not finish startup within $StartupTimeoutSeconds seconds"
    }

    foreach ($command in @('plugins', 'version ExcellentEnchants', 'eenchants', 'eenchants reload')) {
        $process.StandardInput.WriteLine($command)
        $process.StandardInput.Flush()
        Start-Sleep -Seconds 2
    }

    $process.StandardInput.WriteLine('stop')
    $process.StandardInput.Flush()
    $normalStopSent = $true
    if (-not $process.WaitForExit(60000)) {
        throw 'Luminol did not stop within 60 seconds'
    }
    $exitCode = $process.ExitCode
}
finally {
    if ($started -and -not $process.HasExited) {
        try {
            if (-not $normalStopSent) {
                $process.StandardInput.WriteLine('stop')
                $process.StandardInput.Flush()
            }
            if (-not $process.WaitForExit(20000)) {
                $process.Kill()
                $process.WaitForExit(10000) | Out-Null
            }
        }
        catch {
            if (-not $process.HasExited) {
                $process.Kill()
            }
        }
    }

    if ($stdoutCopy -ne $null) { $stdoutCopy.Wait(10000) | Out-Null }
    if ($stderrCopy -ne $null) { $stderrCopy.Wait(10000) | Out-Null }
    if ($stdoutFile -ne $null) { $stdoutFile.Dispose() }
    if ($stderrFile -ne $null) { $stderrFile.Dispose() }

    $stdout = Read-SharedUtf8 $stdoutPath
    $stderr = Read-SharedUtf8 $stderrPath
    [System.IO.File]::WriteAllText($resultLog, $stdout + "`n" + $stderr, $utf8)
    $process.Dispose()
}

if ($exitCode -ne 0) {
    throw "Luminol exited with code $exitCode"
}

$result = [System.IO.File]::ReadAllText($resultLog, $utf8)
foreach ($requiredPattern in @(
    '(?m)Done \([0-9.]+s\)!',
    '(?i)nightcore',
    'ExcellentEnchants',
    '5\.4\.3',
    'Reload is disabled on Luminol\. Restart the server to reload ExcellentEnchants safely\.'
)) {
    if ($result -notmatch $requiredPattern) {
        throw "Smoke log is missing required pattern: $requiredPattern"
    }
}

$fatalPattern = 'SEVERE|Thread.*check|not owned by current region|cross-region|IllegalStateException|ConcurrentModificationException|NoClassDefFoundError|NoSuchMethodError|Could not load.*ExcellentEnchants|Error occurred while (enabling|disabling) ExcellentEnchants|Failed to run bootstrapper.*ExcellentEnchants|Plugin attempted to register task while disabled'
if ($result -match $fatalPattern) {
    throw "Smoke log contains a fatal compatibility pattern: $($Matches[0])"
}

Write-Output "Luminol smoke verification passed on port $serverPort"
Write-Output "Smoke log: $resultLog"
