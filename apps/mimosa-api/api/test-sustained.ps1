# Script pour tester le limiteur avec des requetes soutenues
# Usage: .\test-sustained.ps1

Write-Host "Test soutenu du limiteur Mimosa" -ForegroundColor Red
Write-Host "=================================" -ForegroundColor Red
Write-Host ""

# Test 1: RPS soutenu pendant 15 secondes
Write-Host "1. Test RPS soutenu (IP: 192.168.1.400):" -ForegroundColor Yellow
Write-Host "   Envoi de 12 requetes par seconde pendant 15 secondes..." -ForegroundColor Gray

$startTime = Get-Date
$requestCount = 0
$blockedCount = 0

while ((Get-Date) - $startTime -lt [TimeSpan]::FromSeconds(15)) {
    try {
        $response = Invoke-WebRequest -Uri "http://localhost:8080/api/data" -Headers @{"X-Fake-IP"="192.168.1.400"} -UseBasicParsing
        $requestCount++
        
        if ($response.StatusCode -eq 200) {
            Write-Host "." -NoNewline -ForegroundColor Green
        } elseif ($response.StatusCode -eq 429) {
            $blockedCount++
            $json = $response.Content | ConvertFrom-Json
            Write-Host "`n   BLOQUE! $($json.message)" -ForegroundColor Red
            Write-Host "   TTL: $($json.ttlMs)ms" -ForegroundColor Red
            break
        }
    } catch {
        Write-Host "E" -NoNewline -ForegroundColor Red
    }
    
    Start-Sleep -Milliseconds 80  # ~12 requetes par seconde
}

Write-Host "`n   Resume: $requestCount requetes envoyees, $blockedCount bloques" -ForegroundColor Cyan
Write-Host ""

# Test 2: Diversite de chemins soutenue
Write-Host "2. Test diversite de chemins soutenue (IP: 192.168.1.401):" -ForegroundColor Yellow
Write-Host "   Envoi de 6 chemins differents par seconde pendant 10 secondes..." -ForegroundColor Gray

$startTime2 = Get-Date
$requestCount2 = 0
$blockedCount2 = 0
$pathIndex = 1

while ((Get-Date) - $startTime2 -lt [TimeSpan]::FromSeconds(10)) {
    try {
        $path = "/api/noisy/$pathIndex"
        $response = Invoke-WebRequest -Uri "http://localhost:8080$path" -Headers @{"X-Fake-IP"="192.168.1.401"} -UseBasicParsing
        $requestCount2++
        
        if ($response.StatusCode -eq 200) {
            Write-Host "." -NoNewline -ForegroundColor Green
        } elseif ($response.StatusCode -eq 429) {
            $blockedCount2++
            $json = $response.Content | ConvertFrom-Json
            Write-Host "`n   BLOQUE! $($json.message)" -ForegroundColor Red
            Write-Host "   TTL: $($json.ttlMs)ms" -ForegroundColor Red
            break
        }
    } catch {
        Write-Host "E" -NoNewline -ForegroundColor Red
    }
    
    $pathIndex = ($pathIndex % 6) + 1  # Cycle entre 1 et 6
    Start-Sleep -Milliseconds 150  # ~6 requetes par seconde
}

Write-Host "`n   Resume: $requestCount2 requetes envoyees, $blockedCount2 bloques" -ForegroundColor Cyan
Write-Host ""

# Test 3: Etat final
Write-Host "3. Etat final des IPs:" -ForegroundColor Yellow
try {
    $response = Invoke-WebRequest -Uri "http://localhost:8080/api/state" -UseBasicParsing
    $state = $response.Content | ConvertFrom-Json
    $blockedIps = $state.items | Where-Object { $_.tripped -eq $true }
    $normalIps = $state.items | Where-Object { $_.tripped -eq $false }
    
    if ($blockedIps.Count -gt 0) {
        Write-Host "   IPs bloquees:" -ForegroundColor Red
        $blockedIps | ForEach-Object {
            Write-Host "     - $($_.ip): RPS=$($_.rps), TTL=$($_.ttlMs)ms, Raison=$($_.reason)" -ForegroundColor Red
        }
    } else {
        Write-Host "   Aucune IP bloquee" -ForegroundColor Green
    }
    
    Write-Host "   IPs normales:" -ForegroundColor Cyan
    $normalIps | ForEach-Object {
        Write-Host "     - $($_.ip): RPS=$($_.rps)" -ForegroundColor Cyan
    }
} catch {
    Write-Host "   Erreur: $($_.Exception.Message)" -ForegroundColor Red
}

Write-Host ""
Write-Host "Test soutenu termine!" -ForegroundColor Green
