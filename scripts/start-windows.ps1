# Starts every module in its OWN PowerShell console window (H2 profile, no MySQL/Redis/Docker).
# Each window is titled with the service name and stays open so you can watch its log live.
# Usage:  powershell -ExecutionPolicy Bypass -File scripts\start-windows.ps1
# Requires the jars to be built:  .\mvnw -q -DskipTests package
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$java = 'C:\Users\sathyaraj.ramar\.jdks\jdk-21.0.12.1+1\bin\java.exe'

# Shared env every window inherits (same values as scripts/run-local.sh)
$env:JWT_SECRET      = if ($env:JWT_SECRET)      { $env:JWT_SECRET }      else { 'local-dev-secret-please-change-me-0123456789' }
$env:ADMIN_PASSWORD  = if ($env:ADMIN_PASSWORD)  { $env:ADMIN_PASSWORD }  else { 'Admin12345' }
$env:KAFKA_ENABLED   = if ($env:KAFKA_ENABLED)   { $env:KAFKA_ENABLED }   else { 'false' }

function Start-Module {
  param([string]$Name, [int]$Port, [string]$Profile)
  $jar = Join-Path $root "$Name\target\$Name-1.0.0-SNAPSHOT.jar"
  if (-not (Test-Path $jar)) { Write-Host "  SKIP $Name (jar not built: $jar)" -ForegroundColor Yellow; return }
  $prof = if ($Profile) { "`$env:SPRING_PROFILES_ACTIVE='$Profile'; " } else { '' }
  $inner = "`$host.ui.RawUI.WindowTitle='$Name ($Port)'; " +
           "`$env:SERVER_PORT='$Port'; $prof" +
           "`$env:JWT_SECRET='$($env:JWT_SECRET)'; `$env:ADMIN_PASSWORD='$($env:ADMIN_PASSWORD)'; `$env:KAFKA_ENABLED='$($env:KAFKA_ENABLED)'; " +
           "Set-Location '$root'; " +
           "Write-Host 'Starting $Name on port $Port' -ForegroundColor Cyan; " +
           "& '$java' -jar '$jar'"
  Start-Process -FilePath 'powershell.exe' -ArgumentList '-NoExit','-Command',$inner | Out-Null
  Write-Host "  launched $Name -> http://localhost:$Port"
}

function Wait-Healthy {
  param([string]$Name, [int]$Port)
  for ($i = 0; $i -lt 90; $i++) {
    try { Invoke-WebRequest "http://localhost:$Port/actuator/health" -UseBasicParsing -TimeoutSec 2 | Out-Null; Write-Host "  $Name is up" -ForegroundColor Green; return } catch { Start-Sleep 2 }
  }
  Write-Host "  $Name not healthy yet (its window shows why)" -ForegroundColor Yellow
}

Write-Host "`n== platform ==" -ForegroundColor Magenta
Start-Module 'eureka-server' 8761
Start-Module 'config-server' 8888
Wait-Healthy  'eureka-server' 8761
Wait-Healthy  'config-server' 8888

Write-Host "`n== gateway ==" -ForegroundColor Magenta
Start-Module 'api-gateway' 8080
Wait-Healthy  'api-gateway' 8080

Write-Host "`n== business services (H2) ==" -ForegroundColor Magenta
Start-Module 'auth-service'         8081 'h2'
Start-Module 'customer-service'     8082 'h2'
Start-Module 'product-service'      8083 'h2'
Start-Module 'quote-service'        8084 'h2'
Start-Module 'proposal-service'     8085 'h2'
Start-Module 'payment-service'      8086 'h2'
Start-Module 'policy-service'       8087 'h2'
Start-Module 'notification-service' 8088 'h2'
Start-Module 'document-service'     8089 'h2'
Start-Module 'claims-service'       8090 'h2'

Write-Host "`nAll windows launched. Gateway http://localhost:8080  Eureka http://localhost:8761" -ForegroundColor Green
Write-Host "Admin: admin@insurance.local / $($env:ADMIN_PASSWORD)" -ForegroundColor Green
Write-Host "Close a service's window to stop that one module." -ForegroundColor Green
