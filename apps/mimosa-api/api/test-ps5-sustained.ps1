# Test soutenu pour declencher le blocage sur les reservations PS5
# Usage: .\test-ps5-sustained.ps1

Write-Host "Test soutenu de reservation PlayStation 5" -ForegroundColor Magenta
Write-Host "=======================================" -ForegroundColor Magenta
Write-Host ""

# Test 1: RPS soutenu pendant 6 secondes
Write-Host "1. Test RPS soutenu (IP: sustained@example.com):" -ForegroundColor Yellow
Write-Host "   Envoi de 4 requetes par seconde pendant 6 secondes (4 RPS > 3 RPS)..." -ForegroundColor Gray

$sustainedData = @{
    email = "sustained@example.com"
    name = "Sustained User"
    phone = "06 11 11 11 11"
    quantity = 1
} | ConvertTo-Json

$startTime = Get-Date
$requestCount = 0
$blockedCount = 0

while ((Get-Date) - $startTime -lt [TimeSpan]::FromSeconds(6)) {
    try {
        $response = Invoke-WebRequest -Uri "http://localhost:8080/api/reserve-ps5" -Method POST -Body $sustainedData -ContentType "application/json" -Headers @{"X-Fake-IP"="sustained@example.com"} -UseBasicParsing
        $requestCount++
        
        if ($response.StatusCode -eq 200) {
            Write-Host "." -NoNewline -ForegroundColor Green
        } elseif ($response.StatusCode -eq 429) {
            $blockedCount++
            $json = $response.Content | ConvertFrom-Json
            Write-Host "`nBLOQUE! $($json.message)" -ForegroundColor Red
            Write-Host "TTL: $($json.ttlMs)ms" -ForegroundColor Red
            break
        }
    } catch {
        Write-Host "E" -NoNewline -ForegroundColor Red
    }
    
    Start-Sleep -Milliseconds 250  # 4 requetes par seconde
}

Write-Host "`nResume: $requestCount requetes envoyees, $blockedCount bloques" -ForegroundColor Cyan
Write-Host ""

# Test 2: Diversite soutenue
Write-Host "2. Test diversite soutenue (IP: diverse@example.com):" -ForegroundColor Yellow
Write-Host "   Envoi de requetes vers differents endpoints..." -ForegroundColor Gray

$paths = @("/api/reserve-ps5", "/api/data", "/api/noisy/1", "/api/noisy/2")
$blocked2 = $false

foreach ($path in $paths) {
    try {
        if ($path -eq "/api/reserve-ps5") {
            $response = Invoke-WebRequest -Uri "http://localhost:8080$path" -Method POST -Body $sustainedData -ContentType "application/json" -Headers @{"X-Fake-IP"="diverse@example.com"} -UseBasicParsing
        } else {
            $response = Invoke-WebRequest -Uri "http://localhost:8080$path" -Headers @{"X-Fake-IP"="diverse@example.com"} -UseBasicParsing
        }
        
        if ($response.StatusCode -eq 200) {
            Write-Host "   $path`: OK" -ForegroundColor Green
        } elseif ($response.StatusCode -eq 429) {
            $blocked2 = $true
            $json = $response.Content | ConvertFrom-Json
            Write-Host "   $path`: BLOQUE! $($json.message)" -ForegroundColor Red
            Write-Host "   TTL: $($json.ttlMs)ms" -ForegroundColor Red
            break
        }
    } catch {
        Write-Host "   $path`: Erreur" -ForegroundColor Red
    }
    
    Start-Sleep -Milliseconds 300
}

if (-not $blocked2) {
    Write-Host "   Aucun blocage par diversite" -ForegroundColor Yellow
}
Write-Host ""

# Test 3: Verification de l'etat
Write-Host "3. Etat des IPs apres les tests:" -ForegroundColor Yellow
try {
    $response = Invoke-WebRequest -Uri "http://localhost:8080/api/state" -UseBasicParsing
    $state = $response.Content | ConvertFrom-Json
    $blockedIps = $state.items | Where-Object { $_.tripped -eq $true }
    
    if ($blockedIps.Count -gt 0) {
        Write-Host "   IPs bloquees:" -ForegroundColor Red
        $blockedIps | ForEach-Object {
            Write-Host "     - $($_.ip): RPS=$($_.rps), TTL=$($_.ttlMs)ms" -ForegroundColor Red
            Write-Host "       Raison: $($_.reason)" -ForegroundColor Red
        }
    } else {
        Write-Host "   Aucune IP bloquee" -ForegroundColor Green
        Write-Host "   Le systeme semble tres resistant aux blocages" -ForegroundColor Yellow
    }
} catch {
    Write-Host "   Erreur: $($_.Exception.Message)" -ForegroundColor Red
}
Write-Host ""

# Test 4: Test manuel de la page web
Write-Host "4. Test de la page web:" -ForegroundColor Cyan
Write-Host "   Ouvrez http://localhost:8080/ps5 dans votre navigateur" -ForegroundColor Gray
Write-Host ""
Write-Host "   La page devrait afficher:" -ForegroundColor Green
Write-Host "   - Un formulaire de reservation PlayStation 5" -ForegroundColor Gray
Write-Host "   - Un design moderne avec les couleurs PlayStation" -ForegroundColor Gray
Write-Host "   - Un indicateur de connexion WebSocket" -ForegroundColor Gray
Write-Host "   - Des messages de statut en temps reel" -ForegroundColor Gray
Write-Host ""
Write-Host "   Pour tester le blocage:" -ForegroundColor Yellow
Write-Host "   1. Remplissez le formulaire" -ForegroundColor Gray
Write-Host "   2. Cliquez sur 'Reserver' plusieurs fois rapidement" -ForegroundColor Gray
Write-Host "   3. Observez les messages d'erreur" -ForegroundColor Gray
Write-Host "   4. Regardez le dashboard principal pour voir les blocages" -ForegroundColor Gray

Write-Host ""
Write-Host "Test soutenu termine!" -ForegroundColor Green
