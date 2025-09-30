# Script pour tester l'historique des blocages sans duplication
# Usage: .\test-blocking-history.ps1

Write-Host "Test de l'historique des blocages" -ForegroundColor Magenta
Write-Host "=================================" -ForegroundColor Magenta
Write-Host ""

Write-Host "Ce test va declencher des blocages et verifier qu'ils n'apparaissent qu'une fois dans l'historique" -ForegroundColor Cyan
Write-Host ""

# Test 1: Blocage par RPS eleve
Write-Host "1. Test blocage par RPS eleve (IP: 192.168.1.800):" -ForegroundColor Yellow
Write-Host "   Envoi de 15 requetes par seconde..." -ForegroundColor Gray

$startTime = Get-Date
$requestCount = 0
$blocked = $false

while ((Get-Date) - $startTime -lt [TimeSpan]::FromSeconds(8) -and -not $blocked) {
    try {
        $response = Invoke-WebRequest -Uri "http://localhost:8080/api/data" -Headers @{"X-Fake-IP"="192.168.1.800"} -UseBasicParsing
        $requestCount++
        
        if ($response.StatusCode -eq 200) {
            Write-Host "." -NoNewline -ForegroundColor Green
        } elseif ($response.StatusCode -eq 429) {
            $blocked = $true
            $json = $response.Content | ConvertFrom-Json
            Write-Host "`n   BLOQUE! $($json.message)" -ForegroundColor Red
            Write-Host "   TTL: $($json.ttlMs)ms" -ForegroundColor Red
        }
    } catch {
        Write-Host "E" -NoNewline -ForegroundColor Red
    }
    
    Start-Sleep -Milliseconds 60
}

if (-not $blocked) {
    Write-Host "`n   Aucun blocage detecte ($requestCount requetes)" -ForegroundColor Yellow
}
Write-Host ""

# Test 2: Blocage par diversite de chemins
Write-Host "2. Test blocage par diversite de chemins (IP: 192.168.1.801):" -ForegroundColor Yellow
Write-Host "   Envoi de requetes vers differents endpoints..." -ForegroundColor Gray

$paths = @("/api/data", "/api/noisy/1", "/api/noisy/2", "/api/noisy/3", "/api/noisy/4", "/api/noisy/5")
$blocked2 = $false

foreach ($path in $paths) {
    try {
        $response = Invoke-WebRequest -Uri "http://localhost:8080$path" -Headers @{"X-Fake-IP"="192.168.1.801"} -UseBasicParsing
        
        if ($response.StatusCode -eq 200) {
            Write-Host "  $path`: OK" -ForegroundColor Green
        } elseif ($response.StatusCode -eq 429) {
            $blocked2 = $true
            $json = $response.Content | ConvertFrom-Json
            Write-Host "  $path`: BLOQUE! $($json.message)" -ForegroundColor Red
            Write-Host "  TTL: $($json.ttlMs)ms" -ForegroundColor Red
            break
        }
    } catch {
        Write-Host "  $path`: Erreur" -ForegroundColor Red
    }
    
    Start-Sleep -Milliseconds 200
}

if (-not $blocked2) {
    Write-Host "  Aucun blocage par diversite detecte" -ForegroundColor Yellow
}
Write-Host ""

# Test 3: Verification de l'etat des IPs
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
    }
} catch {
    Write-Host "   Erreur: $($_.Exception.Message)" -ForegroundColor Red
}
Write-Host ""

# Test 4: Instructions pour verifier l'historique
Write-Host "4. Verification de l'historique des blocages:" -ForegroundColor Cyan
Write-Host "   Ouvrez le dashboard dans votre navigateur:" -ForegroundColor Gray
Write-Host "   http://localhost:8080" -ForegroundColor Gray
Write-Host ""
Write-Host "   Dans la section 'Historique des Blocages' en bas de page:" -ForegroundColor Gray
Write-Host "   - Chaque IP bloquee doit apparaître UNE SEULE FOIS" -ForegroundColor Green
Write-Host "   - Les entrées doivent être triées par ordre chronologique (plus récent en haut)" -ForegroundColor Green
Write-Host "   - Chaque entrée doit afficher: IP, raison, heure, TTL" -ForegroundColor Green
Write-Host ""

Write-Host "Test de l'historique des blocages termine!" -ForegroundColor Green
Write-Host ""
Write-Host "Si vous voyez des doublons dans l'historique, le probleme n'est pas encore corrige." -ForegroundColor Yellow
Write-Host "Si chaque blocage n'apparait qu'une fois, la correction fonctionne!" -ForegroundColor Green
