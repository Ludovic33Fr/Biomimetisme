# Script pour tester le limiteur avec des requetes extremes
# Usage: .\test-extreme.ps1

Write-Host "Test extreme du limiteur Mimosa" -ForegroundColor Red
Write-Host "=================================" -ForegroundColor Red
Write-Host ""

# Test 1: RPS tres eleve pendant 12 secondes
Write-Host "1. Test RPS extreme (IP: 192.168.1.500):" -ForegroundColor Yellow
Write-Host "   Envoi de 15 requetes par seconde pendant 12 secondes..." -ForegroundColor Gray

$startTime = Get-Date
$requestCount = 0
$blockedCount = 0

while ((Get-Date) - $startTime -lt [TimeSpan]::FromSeconds(12)) {
    try {
        $response = Invoke-WebRequest -Uri "http://localhost:8080/api/data" -Headers @{"X-Fake-IP"="192.168.1.500"} -UseBasicParsing
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
    
    Start-Sleep -Milliseconds 60  # ~15 requetes par seconde
}

Write-Host "`n   Resume: $requestCount requetes envoyees, $blockedCount bloques" -ForegroundColor Cyan
Write-Host ""

# Test 2: Diversite de chemins extreme
Write-Host "2. Test diversite de chemins extreme (IP: 192.168.1.501):" -ForegroundColor Yellow
Write-Host "   Envoi de 8 chemins differents par seconde pendant 8 secondes..." -ForegroundColor Gray

$startTime2 = Get-Date
$requestCount2 = 0
$blockedCount2 = 0
$pathIndex = 1

while ((Get-Date) - $startTime2 -lt [TimeSpan]::FromSeconds(8)) {
    try {
        $path = "/api/noisy/$pathIndex"
        $response = Invoke-WebRequest -Uri "http://localhost:8080$path" -Headers @{"X-Fake-IP"="192.168.1.501"} -UseBasicParsing
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
    
    $pathIndex = ($pathIndex % 8) + 1  # Cycle entre 1 et 8
    Start-Sleep -Milliseconds 120  # ~8 requetes par seconde
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
Write-Host "Test extreme termine!" -ForegroundColor Green
