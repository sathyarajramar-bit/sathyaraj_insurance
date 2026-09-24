# Called by kafka-start.cmd. Kafka's bin\windows\kafka-run-class.bat adds every jar in libs\ to the classpath as a separate
# quoted entry (~130 of them); with an install path like C:\Softwares\kafka\kafka_2.13-4.3.1 the expanded java command
# exceeds the 8191 characters cmd.exe allows and every tool fails with "The syntax of the command is incorrect."
# Replace the loop with one wildcard entry (java -cp supports "dir\*"). Idempotent; keeps a .orig copy.
param([Parameter(Mandatory = $true)][string]$KafkaHome)
$bat = Join-Path $KafkaHome 'bin\windows\kafka-run-class.bat'
$text = [System.IO.File]::ReadAllText($bat)
if ($text -like '*Patched by insurance-ecommerce*') { exit 0 }
$loop = 'for %%i in ("%BASE_DIR%\libs\*") do (' + "`r`n`tcall :concat `"%%i`"`r`n)"
if (-not $text.Contains($loop)) { $loop = $loop -replace "`r`n", "`n" }
if (-not $text.Contains($loop)) { Write-Warning "kafka-run-class.bat: libs loop not found, not patched"; exit 0 }
$patched = 'rem Patched by insurance-ecommerce scripts\kafka-start.cmd: one wildcard entry instead of ~130 quoted jars, otherwise' + "`n" +
           'rem the expanded command line exceeds the 8191 characters cmd.exe allows ("The syntax of the command is incorrect.").' + "`n" +
           'call :concat "%BASE_DIR%\libs\*"'
Copy-Item $bat "$bat.orig" -Force
[System.IO.File]::WriteAllText($bat, $text.Replace($loop, $patched))
Write-Output "Patched $bat (original kept as kafka-run-class.bat.orig)"
