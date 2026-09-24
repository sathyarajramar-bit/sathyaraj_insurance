@echo off
REM Stops the local Kafka broker started by kafka-start.cmd. Kafka's own kafka-server-stop.bat needs wmic, which
REM recent Windows versions no longer ship, so find the broker JVM (main class kafka.Kafka) with PowerShell instead.
powershell -NoProfile -Command "$p = Get-CimInstance Win32_Process -Filter \"Name='java.exe'\" | Where-Object { $_.CommandLine -like '*kafka.Kafka*' }; if ($p) { $p | ForEach-Object { Write-Output ('Stopping Kafka broker pid ' + $_.ProcessId); Stop-Process -Id $_.ProcessId -Force } } else { Write-Output 'No Kafka broker running' }"
