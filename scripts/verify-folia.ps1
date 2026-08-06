$ErrorActionPreference = 'Stop'

$root = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
Set-Location -LiteralPath $root

$failures = [System.Collections.Generic.List[string]]::new()

function Assert-NoMatches {
    param(
        [string]$Name,
        [string[]]$Arguments
    )

    $output = & rg @Arguments 2>&1
    $code = $LASTEXITCODE
    if ($code -eq 0) {
        $failures.Add("$Name`n$($output -join "`n")")
    }
    elseif ($code -gt 1) {
        $failures.Add("$Name scan failed with exit code $code`n$($output -join "`n")")
    }
}

Assert-NoMatches 'Direct legacy scheduler calls found' @(
    '-n', '-g', '*.java',
    'Bukkit\.getScheduler\(|scheduleSyncDelayedTask|scheduleAsyncRepeatingTask',
    'Core', 'API', 'tooltip-packetevents', 'tooltip-protocollib', 'compat-mythicmobs'
)
Assert-NoMatches 'NightCore business scheduler calls found' @(
    '-n', '-g', '*.java',
    'plugin\.runTask\(|addAsyncTask\(|addTask\(',
    'Core/src/main/java'
)
Assert-NoMatches 'Synchronous teleport calls found' @(
    '-n', '-g', '*.java',
    '\.teleport\s*\(',
    'Core/src/main/java'
)
Assert-NoMatches 'Unavailable Folia event listeners found' @(
    '-n', '-g', '*.java',
    'PlayerRespawnEvent|PlayerTeleportEvent|PlayerChangedWorldEvent|WorldLoadEvent|WorldUnloadEvent',
    'Core', 'API'
)
Assert-NoMatches 'Blocking Future wait found' @(
    '-n', '-g', '*.java',
    'Future<.*>.*\.get\(|\.join\s*\(\s*\)',
    'Core/src/main/java'
)

$commentDiff = & git diff '4bd372e5a73c5055d935d93c55ba7717393dd0e1' -- '*.java' 2>&1
if ($LASTEXITCODE -ne 0) {
    $failures.Add("Java comment diff failed`n$($commentDiff -join "`n")")
}
else {
    $addedComments = $commentDiff | Select-String '^\+.*(//|/\*)'
    if ($addedComments) {
        $failures.Add("New Java source comments found`n$($addedComments -join "`n")")
    }
}

$descriptors = @(
    'Core/src/main/resources/plugin.yml',
    'Core/src/main/resources/paper-plugin.yml'
)
foreach ($descriptor in $descriptors) {
    $content = Get-Content -Raw -Encoding UTF8 -LiteralPath $descriptor
    $foliaCount = [regex]::Matches($content, '(?m)^folia-supported:\s*true\s*$').Count
    $authorCount = [regex]::Matches($content, 'cloudfl4re').Count
    if ($foliaCount -ne 1) {
        $failures.Add("$descriptor must contain folia-supported: true exactly once")
    }
    if ($authorCount -ne 1) {
        $failures.Add("$descriptor must contain cloudfl4re exactly once")
    }
}

$schedulerPath = 'Core/src/main/java/su/nightexpress/excellentenchants/scheduler/SchedulerUtil.java'
$scheduler = Get-Content -Raw -Encoding UTF8 -LiteralPath $schedulerPath
$schedulerMethods = @(
    'isFolia',
    'isOwned',
    'runAtEntity',
    'runAtEntityDelayed',
    'runAtEntityTimer',
    'runAtRegion',
    'runAtRegionDelayed',
    'runAtRegionTimer',
    'runGlobal',
    'runGlobalDelayed',
    'runAsync',
    'runAtSender',
    'teleport',
    'shutdown'
)
foreach ($method in $schedulerMethods) {
    if ($scheduler -notmatch "(?m)^\s*public\s+[^\r\n]+\s+$method\s*\(") {
        $failures.Add("SchedulerUtil is missing public method: $method")
    }
}

if ($failures.Count -gt 0) {
    $failures | ForEach-Object { Write-Error $_ }
    exit 1
}

Write-Output 'Folia static verification passed'
