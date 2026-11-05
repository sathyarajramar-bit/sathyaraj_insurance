# Deploys the built jars on this PC. Called by the Deploy stage in the Jenkinsfile.
#
#   1. Check that every jar was built (nothing is stopped if one is missing)
#   2. Stop the old services on their ports (java processes only)
#   3. Copy the new jars to C:\Deploy\insurance\apps
#   4. Start the services in order and wait until each one is healthy
#
# Optional settings live OUTSIDE git in C:\Deploy\insurance\deploy.env, as KEY=VALUE lines
# (MYSQL_PASSWORD, JWT_SECRET, ADMIN_PASSWORD...). Without it the defaults in the config are used.
#
# Manual run:  powershell -ExecutionPolicy Bypass -File scripts\deploy-local.ps1
param(
    [string]$DeployDir = 'C:\Deploy\insurance',
    [string]$Version = '1.0.0-SNAPSHOT',
    [int]$HealthTimeoutSeconds = 180
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$java = 'C:\Users\sathyaraj.ramar\.jdks\jdk-21.0.12.1+1\bin\java.exe'
$apps = Join-Path $DeployDir 'apps'
$logs = Join-Path $DeployDir 'logs'

# Eureka and config-server don't expose the liveness probe, so they use the plain health URL.
# The rest use liveness because /actuator/health reports DOWN when the optional Redis is absent.
$liveness = '/actuator/health/liveness'
$platform = @(
    @{ Name = 'eureka-server'; Port = 8761; Health = '/actuator/health' },
    @{ Name = 'config-server'; Port = 8888; Health = '/actuator/health' }
)
$gateway = @(
    @{ Name = 'api-gateway'; Port = 8080; Health = $liveness }
)
$business = @(
    @{ Name = 'auth-service';         Port = 8081; Health = $liveness },
    @{ Name = 'customer-service';     Port = 8082; Health = $liveness },
    @{ Name = 'product-service';      Port = 8083; Health = $liveness },
    @{ Name = 'quote-service';        Port = 8084; Health = $liveness },
    @{ Name = 'proposal-service';     Port = 8085; Health = $liveness },
    @{ Name = 'payment-service';      Port = 8086; Health = $liveness },
    @{ Name = 'policy-service';       Port = 8087; Health = $liveness },
    @{ Name = 'notification-service'; Port = 8088; Health = $liveness },
    @{ Name = 'document-service';     Port = 8089; Health = $liveness },
    @{ Name = 'claims-service';       Port = 8090; Health = $liveness }
)
$all = $platform + $gateway + $business

function Get-BuiltJar($svc) {
    Join-Path $root "$($svc.Name)\target\$($svc.Name)-$Version.jar"
}

function Stop-Service-OnPort($svc) {
    $listeners = @(Get-NetTCPConnection -LocalPort $svc.Port -State Listen -ErrorAction SilentlyContinue)
    foreach ($procId in ($listeners | Select-Object -ExpandProperty OwningProcess -Unique)) {
        $proc = Get-Process -Id $procId -ErrorAction SilentlyContinue
        if (-not $proc) { continue }
        # Never kill something that isn't one of our JVMs just because it holds the port.
        if ($proc.ProcessName -notmatch '^javaw?$') {
            throw "Port $($svc.Port) is used by '$($proc.ProcessName)' (PID $procId), not java. Free it and redeploy."
        }
        Write-Host "  stopping $($svc.Name) (PID $procId, port $($svc.Port))"
        Stop-Process -Id $procId -Force
        $proc.WaitForExit(30000) | Out-Null
    }
}

function Start-Service-Jar($svc) {
    $jar = Join-Path $apps "$($svc.Name).jar"
    $env:SERVER_PORT = "$($svc.Port)"
    # Without these two, Jenkins kills everything the build started when the build ends.
    $env:BUILD_ID = 'dontKillMe'
    $env:JENKINS_NODE_COOKIE = 'dontKillMe'
    # WorkingDirectory = DeployDir, so relative paths like ./data/documents survive each deploy.
    $proc = Start-Process -FilePath $java -ArgumentList @('-jar', "`"$jar`"") `
        -WorkingDirectory $DeployDir -WindowStyle Hidden -PassThru `
        -RedirectStandardOutput (Join-Path $logs "$($svc.Name).log") `
        -RedirectStandardError (Join-Path $logs "$($svc.Name).err.log")
    $svc.Process = $proc
    Write-Host "  started $($svc.Name) (PID $($proc.Id), port $($svc.Port))"
}

function Wait-Healthy($svc) {
    $url = "http://localhost:$($svc.Port)$($svc.Health)"
    $deadline = (Get-Date).AddSeconds($HealthTimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        if ($svc.Process.HasExited) { Fail-Service $svc "process exited with code $($svc.Process.ExitCode)" }
        try {
            $response = Invoke-WebRequest $url -UseBasicParsing -TimeoutSec 3
            if ($response.StatusCode -eq 200) { Write-Host "  $($svc.Name) is healthy" -ForegroundColor Green; return }
        } catch { }
        Start-Sleep -Seconds 3
    }
    Fail-Service $svc "not healthy after $HealthTimeoutSeconds seconds ($url)"
}

function Fail-Service($svc, $reason) {
    Write-Host "`nDEPLOY FAILED: $($svc.Name) - $reason" -ForegroundColor Red
    foreach ($file in @("$($svc.Name).err.log", "$($svc.Name).log")) {
        $path = Join-Path $logs $file
        if ((Test-Path $path) -and (Get-Item $path).Length -gt 0) {
            Write-Host "--- last lines of $path"
            Get-Content $path -Tail 30 | ForEach-Object { Write-Host $_ }
        }
    }
    exit 1
}

Write-Host "`n== 1. Checking jars ==" -ForegroundColor Cyan
$missing = @($all | Where-Object { -not (Test-Path (Get-BuiltJar $_)) } | ForEach-Object { Get-BuiltJar $_ })
if ($missing.Count -gt 0) {
    Write-Host "Jars not built, nothing was stopped:`n  $($missing -join "`n  ")" -ForegroundColor Red
    exit 1
}
Write-Host "  all $($all.Count) jars found"

New-Item -ItemType Directory -Force -Path $apps, $logs | Out-Null
$envFile = Join-Path $DeployDir 'deploy.env'
if (Test-Path $envFile) {
    foreach ($line in Get-Content $envFile) {
        if ($line -match '^\s*([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.*?)\s*$') {
            Set-Item -Path "Env:$($Matches[1])" -Value $Matches[2]
        }
    }
    Write-Host "  loaded settings from $envFile"
} else {
    Write-Host "  no $envFile, using config defaults"
}

Write-Host "`n== 2. Stopping old services ==" -ForegroundColor Cyan
# Reverse order: business services first, eureka last.
$reversed = @($all)
[array]::Reverse($reversed)
foreach ($svc in $reversed) { Stop-Service-OnPort $svc }

Write-Host "`n== 3. Copying jars to $apps ==" -ForegroundColor Cyan
foreach ($svc in $all) {
    $target = Join-Path $apps "$($svc.Name).jar"
    # Keep the last version, so a bad deploy can be rolled back by hand.
    if (Test-Path $target) { Copy-Item $target (Join-Path $apps "$($svc.Name).previous.jar") -Force }
    Copy-Item (Get-BuiltJar $svc) $target -Force
}
Write-Host "  done"

Write-Host "`n== 4. Starting services ==" -ForegroundColor Cyan
foreach ($svc in $platform) { Start-Service-Jar $svc }
foreach ($svc in $platform) { Wait-Healthy $svc }

foreach ($svc in $gateway) { Start-Service-Jar $svc; Wait-Healthy $svc }

foreach ($svc in $business) { Start-Service-Jar $svc }
foreach ($svc in $business) { Wait-Healthy $svc }

Write-Host "`nDeploy finished. Gateway http://localhost:8080  Eureka http://localhost:8761  Logs $logs" -ForegroundColor Green
exit 0
