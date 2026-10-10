# Loot Luck live rendering profiling

Offscreen CPU benchmarks do not measure Java2D's live Direct3D uploads, scaling, synchronization or presentation. Compare the running client's Java timings with its own GPU engine counters and, when available, a native GPU trace.

## Development client

Start from the repository root:

```powershell
.\gradlew.bat runProfile
```

This uses the same plugin as `run`, with development-only JFR observers and improved JVM stack metadata. It records background/foreground paint durations, dimensions, DPI scale, foreground cache presence, animation registrations/visibility, repaint requests and whole Swing paint passes. Normal `run` does not install observers or change its repaint manager. `runProfile` does not inject game input or change game/FPS settings.

Login using RuneLite's [Using Jagex Accounts](https://github.com/runelite/runelite/wiki/Using-Jagex-Accounts) instructions.

## Controlled captures

The user switches views/effects manually. Keep the game location, camera, window size, FPS cap, visible items and other plugins fixed. Wait a few seconds after each change, then collect one phase:

```powershell
.\tools\Capture-LootLuckProfile.ps1 -ClientProcessId <client PID> -Phase none-grid
```

Capture, in order: `none-grid`, `aurora-grid`, `aurora-list`, `aurora-grid-after-list`, `none-grid-after-list`. Capture `panel-closed` if usage stays high after turning the effect off. Use the Java client PID, not the Gradle daemon. The script checks the JVM's working directory before attaching.

Each 20-second capture writes a JFR, per-process GPU-engine CSV, NVIDIA clocks/power-state CSV when available, and a summary under ignored `build/profiles/`. CPU percentage is expressed as a percentage of one logical core; GPU summaries use the busiest 3D engine per sample. Raw engine samples are preserved. NVIDIA samples describe the entire GPU, not just the client. JVM sampling remains useful on the regular client, but the `lootluck.*` events require `runProfile`.

Summarize a recording using a PowerShell 7 terminal with the JDK on PATH:

```powershell
.\tools\Summarize-LootLuckProfile.ps1 -Recording build/profiles/<capture>/client.jfr
```

This writes `render-summary.json` alongside the recording, including stage timing percentiles, geometry/DPI, foreground cache presence, visible/registered animation counts and sampled Java2D stack frames. Stack-frame sample counts overlap; they are not independent CPU percentages. `-JdkBin` can select the JDK's `bin` directory explicitly.

Animation clock events also report the number and summed area of batched viewport damage regions. The original requested-pixels field remains the sum of visible button sizes, for comparison with older recordings. Repaint-request events and Swing pass `effectRequests` count direct button requests; batched viewport requests are represented by the clock's damage-region fields instead.

Swing-buffer events record the shared volatile buffer's dimensions and public acceleration capability before validation. Paint geometry records the actual surface used during drawing. Together they help identify oversized or unaccelerated buffering without accessing JDK internals.

For native GPU tracing, run the same command in an **Administrator PowerShell** with `-NativeGpu`. It uses a separate WPR instance, a lighter GPU profile, and file mode. Only that instance is stopped; the script does not cancel somebody else's trace. If starting native tracing fails, capture again without `-NativeGpu`.

Windows Performance Analyzer or GPUView from the Windows Performance Toolkit can inspect `gpu.etl` for GPU queue execution, resource uploads and presentation work. Built-in `wpr.exe` can record traces even when those viewers are absent. Native tracing requires Windows administrator privileges; a sandbox-approved command does not supply those privileges.

## Interpretation

- High background duration with low foreground duration: investigate effect drawing and background-buffer scaling.
- High foreground duration or frequent missing foregrounds: investigate text/icon repainting, cache invalidation and DPI scaling.
- Many repaint requests/visible buttons or unexpectedly large requested regions: investigate animation registration, clipping and repaint coalescing. Request area is a sum of requests, not the actual GPU paint area.
- Long Swing passes but short measured button paints: investigate ancestors, other Swing components and presentation.
- High GPU usage with short Java paints: inspect the native GPU queues; asynchronous GPU work need not appear as long Java method durations.
- High usage after returning to grid: compare registrations, paint counts, DPI, GPU clocks and foreground cache state before and after list view.

Instrumentation and native tracing add some overhead. Compare phases using the same instrumentation, and confirm the eventual fix again in normal `run`. The files stay local and out of Git; review recordings before sharing them externally.

Primary references: [Oracle Java2D tracing](https://www.oracle.com/java/technologies/java2d.html), [Oracle JFR event configuration](https://docs.oracle.com/en/java/javase/24/jfapi/configuration.html), [Microsoft DirectX profiling](https://learn.microsoft.com/en-us/windows/win32/direct2d/profiling-directx-applications).
