param(
    [Parameter(Mandatory=$true)][int]$ClientProcessId,
    [Parameter(Mandatory=$true)][ValidatePattern('^[a-z0-9-]+$')][string]$Phase,
    [ValidateRange(5,60)][int]$Seconds=20,
    [switch]$NativeGpu
)

$ErrorActionPreference='Stop'
$repoRoot=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$client=Get-Process -Id $ClientProcessId
$processInfo=Get-CimInstance Win32_Process -Filter "ProcessId=$ClientProcessId"
if ($processInfo.CommandLine -notmatch 'com\.lootluck\.(LootLuckLauncher|ui\.LootLuckProfileLauncher)(?:\s|$)') { throw 'Select the Loot Luck Java client PID, not the Gradle daemon or another Java process.' }
$renderHooksEnabled=$processInfo.CommandLine -match 'com\.lootluck\.ui\.LootLuckProfileLauncher(?:\s|$)'
$javaDirectory=Split-Path -Parent $client.Path
$jcmd=Join-Path $javaDirectory 'jcmd.exe'
if (!(Test-Path -LiteralPath $jcmd)) { throw 'The selected client needs a JDK with jcmd.exe.' }
$properties=& $jcmd $ClientProcessId VM.system_properties
if ($LASTEXITCODE -ne 0) { throw 'Could not attach to the selected JVM.' }
$workingDirectory=($properties | Where-Object { $_ -like 'user.dir=*' }) -replace '^user.dir=',''
$workingDirectory=$workingDirectory.Replace('\:',':').Replace('\\','\')
if ([IO.Path]::GetFullPath($workingDirectory) -ne $repoRoot) { throw 'Launch the development client from this repository before capturing.' }
if ($NativeGpu) {
    $principal=[Security.Principal.WindowsPrincipal]::new([Security.Principal.WindowsIdentity]::GetCurrent())
    if (!$principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) { throw 'Native GPU tracing requires an Administrator PowerShell. JFR and GPU counters work without -NativeGpu.' }
}
$captureName=[DateTime]::UtcNow.ToString('yyyyMMdd-HHmmss')+'-'+$Phase
$relativeDirectory='build/profiles/'+$captureName
$directory=Join-Path $repoRoot $relativeDirectory
New-Item -ItemType Directory -Path $directory -Force | Out-Null
$recordingName='lootluck-'+$Phase
$nativeStarted=$false
$jfrStarted=$false
$rows=[Collections.Generic.List[object]]::new()
$nvidiaRows=[Collections.Generic.List[object]]::new()
$nvidia=Get-Command nvidia-smi -ErrorAction SilentlyContinue
$started=[DateTimeOffset]::UtcNow
$cpuBefore=$client.TotalProcessorTime.TotalSeconds
try {
    if ($NativeGpu) {
        & wpr -start GPU.Light -filemode -recordtempto $directory -instancename LootLuck
        if ($LASTEXITCODE -ne 0) { throw 'Windows GPU tracing could not start. No existing tracing session was cancelled.' }
        $nativeStarted=$true
    }
    & $jcmd $ClientProcessId JFR.start "name=$recordingName" settings=profile "duration=$($Seconds+30)s" "filename=$relativeDirectory/client.jfr"
    if ($LASTEXITCODE -ne 0) { throw 'JFR could not start.' }
    $jfrStarted=$true
    $client.Refresh();$cpuBefore=$client.TotalProcessorTime.TotalSeconds;$started=[DateTimeOffset]::UtcNow
    Get-Counter '\GPU Engine(*)\Utilization Percentage' -SampleInterval 1 -MaxSamples $Seconds | ForEach-Object {
        $sample=$_
        foreach ($counter in $sample.CounterSamples) {
            if ($counter.InstanceName -match "^pid_${ClientProcessId}_") {
                $rows.Add([pscustomobject]@{Utc=$sample.Timestamp.ToUniversalTime().ToString('o');Instance=$counter.InstanceName;Engine=($counter.InstanceName -split 'engtype_')[-1];Percent=$counter.CookedValue})
            }
        }
        if ($nvidia) {
            $nvidiaData=& $nvidia.Source --query-gpu=index,utilization.gpu,clocks.current.graphics,clocks.current.memory,pstate,power.draw --format=csv,noheader,nounits
            if ($LASTEXITCODE -eq 0) { foreach ($line in $nvidiaData) { $nvidiaRows.Add([pscustomobject]@{Utc=[DateTimeOffset]::UtcNow.ToString('o');Data=$line}) } }
        }
    }
}
finally {
    $client.Refresh();$cpuAfter=$client.TotalProcessorTime.TotalSeconds;$elapsed=([DateTimeOffset]::UtcNow-$started).TotalSeconds
    if ($jfrStarted) { & $jcmd $ClientProcessId JFR.stop "name=$recordingName" }
    if ($nativeStarted) {
        & wpr -stop (Join-Path $directory 'gpu.etl') -instancename LootLuck
        if ($LASTEXITCODE -ne 0) { Write-Warning 'The native trace could not be saved; check wpr -status -instancename LootLuck.' }
    }
    $rows | Export-Csv -LiteralPath (Join-Path $directory 'gpu-engines.csv') -NoTypeInformation
    $nvidiaRows | Export-Csv -LiteralPath (Join-Path $directory 'gpu-clocks.csv') -NoTypeInformation
    $busiest3D=@($rows | Where-Object Engine -eq '3D' | Group-Object Utc | ForEach-Object { ($_.Group | Measure-Object Percent -Maximum).Maximum })
    $gpu=$busiest3D | Measure-Object -Average -Minimum -Maximum
    $summary=[ordered]@{
        Phase=$Phase;ClientProcessId=$ClientProcessId;StartedUtc=$started.ToString('o');ElapsedSeconds=$elapsed
        Gpu3DBusiestEnginePercentMean=$gpu.Average;Gpu3DBusiestEnginePercentMin=$gpu.Minimum;Gpu3DBusiestEnginePercentMax=$gpu.Maximum
        CpuPercentOfOneCore=100*($cpuAfter-$cpuBefore)/$elapsed
        Samples=$busiest3D.Count;RenderHooksEnabled=$renderHooksEnabled;NativeTraceSaved=(Test-Path -LiteralPath (Join-Path $directory 'gpu.etl'))
    }
    $summary | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $directory 'summary.json') -Encoding utf8
    $summary | ConvertTo-Json
    "Capture: $directory"
}
