param(
    [Parameter(Mandatory=$true)][string]$Recording,
    [string]$JdkBin=(Split-Path -Parent (Get-Command java).Source)
)
$ErrorActionPreference='Stop'
$jfr=Join-Path $JdkBin 'jfr.exe'
$recordingPath=(Resolve-Path -LiteralPath $Recording).Path
$json=& $jfr print --json --events 'lootluck.Paint,lootluck.AnimationClock,lootluck.SwingPaintPass,jdk.ExecutionSample,jdk.NativeMethodSample' $recordingPath
if ($LASTEXITCODE -ne 0) { throw 'Could not read the flight recording.' }
$events=($json -join "`n" | ConvertFrom-Json -Depth 100).recording.events
function Percentile($values,[double]$fraction) {
    $sorted=@($values | Sort-Object)
    if ($sorted.Count -eq 0) { return $null }
    $sorted[[math]::Max(0,[math]::Ceiling($sorted.Count*$fraction)-1)]
}
function Milliseconds($duration) { [System.Xml.XmlConvert]::ToTimeSpan($duration).TotalMilliseconds }
$paints=@($events | Where-Object type -eq 'lootluck.Paint')
$paintSummary=@($paints | Group-Object { $_.values.stage+' '+$_.values.effect } | ForEach-Object {
    $durations=@($_.Group | ForEach-Object { Milliseconds $_.values.duration })
    $measure=$durations | Measure-Object -Sum -Average -Maximum
    [pscustomobject]@{Stage=$_.Name;Count=$durations.Count;TotalMs=$measure.Sum;MeanMs=$measure.Average;P95Ms=(Percentile $durations .95);P99Ms=(Percentile $durations .99);MaxMs=$measure.Maximum}
})
$foregrounds=@($paints | Where-Object { $_.values.stage -eq 'foreground' })
$foregroundPresent=@($foregrounds | Where-Object { $_.values.foregroundPresent }).Count
$clockValues=@($events | Where-Object type -eq 'lootluck.AnimationClock' | ForEach-Object { $_.values })
$passes=@($events | Where-Object type -eq 'lootluck.SwingPaintPass' | ForEach-Object { Milliseconds $_.values.duration })
$samples=@($events | Where-Object { $_.type -in 'jdk.ExecutionSample','jdk.NativeMethodSample' -and $_.values.state -eq 'STATE_RUNNABLE' -and $_.values.sampledThread.javaName -match '^AWT-EventQueue|^D3D' })
$javaStacks=@($samples | ForEach-Object { foreach ($frame in $_.values.stackTrace.frames) { $frame.method.type.name+'.'+$frame.method.name } } | Group-Object | Sort-Object Count -Descending | Select-Object -First 30 Count,Name)
$summary=[ordered]@{
    Recording=$recordingPath;HasRenderProbes=($paints.Count -gt 0);Paint=$paintSummary
    PaintGeometry=@($paints | Group-Object { "$($_.values.width)x$($_.values.height) scale=$($_.values.scaleX),$($_.values.scaleY) $($_.values.configuration)" } | Sort-Object Count -Descending | Select-Object Count,Name)
    ForegroundPaints=$foregrounds.Count;ForegroundCachePresent=$foregroundPresent
    MeanRegisteredButtons=($clockValues | Measure-Object registered -Average).Average
    MeanVisibleButtons=($clockValues | Measure-Object visible -Average).Average
    MeanAnimationRequestedPixels=($clockValues | Measure-Object requestedPixels -Average).Average
    SwingPassCount=$passes.Count;SwingPassP95Ms=(Percentile $passes .95);SwingPassP99Ms=(Percentile $passes .99)
    RunnableSwingD3DSamples=$samples.Count;SwingD3DStackFrames=$javaStacks
}
$destination=Join-Path (Split-Path -Parent $recordingPath) 'render-summary.json'
$summary | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $destination -Encoding utf8
$summary | ConvertTo-Json -Depth 8
