# Starts all movie-booking services with demo-friendly small heaps.
# Usage: powershell -File scripts/start-all.ps1
$ErrorActionPreference = "Continue"
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root
New-Item -ItemType Directory -Force -Path logs | Out-Null

$services = @(
    @{ Name = "discovery"; Port = 8762; Jar = "lin-spring-movie-booking-discovery/target/lin-spring-movie-booking-discovery-0.0.1-SNAPSHOT.jar"; Env = @() },
    @{ Name = "gateway";   Port = 8180; Jar = "lin-spring-movie-booking-gateway/target/lin-spring-movie-booking-gateway-0.0.1-SNAPSHOT.jar";       Env = @() },
    @{ Name = "movie";     Port = 8191; Jar = "lin-spring-movie-booking-movie-service/target/lin-spring-movie-booking-movie-service-0.0.1-SNAPSHOT.jar"; Env = @() },
    @{ Name = "booking";   Port = 8192; Jar = "lin-spring-movie-booking-booking-service/target/lin-spring-movie-booking-booking-service-0.0.1-SNAPSHOT.jar"; Env = @("HOLD_TTL_SECONDS=5") },
    @{ Name = "payment";   Port = 8193; Jar = "lin-spring-movie-booking-payment-service/target/lin-spring-movie-booking-payment-service-0.0.1-SNAPSHOT.jar"; Env = @() }
)

foreach ($svc in $services) {
    $running = $false
    try { Invoke-WebRequest "http://localhost:$($svc.Port)/actuator/health" -UseBasicParsing -TimeoutSec 2 *> $null; $running = $true } catch {}
    if ($running) { "$($svc.Name): already up on $($svc.Port)"; continue }

    $envVars = @{}
    foreach ($e in $svc.Env) { $k, $v = $e -split "=", 2; $envVars[$k] = $v }

    # Per-service env via cmd wrapper (Start-Process cannot set env directly pre-PS7.4)
    $cmd = "set HOLD_TTL_SECONDS=5&& java -Xmx192m -jar `"$($svc.Jar)`""
    if ($svc.Name -ne "booking") { $cmd = "java -Xmx192m -jar `"$($svc.Jar)`"" }

    Start-Process -FilePath "cmd.exe" -ArgumentList "/c", $cmd `
        -WorkingDirectory $root -WindowStyle Hidden `
        -RedirectStandardOutput "logs\$($svc.Name).log" -RedirectStandardError "logs\$($svc.Name).err.log"

    $ok = $false
    foreach ($i in 1..30) {
        Start-Sleep -Seconds 2
        try {
            $resp = Invoke-WebRequest "http://localhost:$($svc.Port)/actuator/health" -UseBasicParsing -TimeoutSec 2
            if ($resp.StatusCode -eq 200) { $ok = $true; break }
        } catch {}
    }
    if ($ok) { "$($svc.Name): UP on $($svc.Port) ($($i*2)s)" }
    else { "$($svc.Name): FAILED to become healthy — see logs\$($svc.Name).log" }
}
"--- all-started ---"
