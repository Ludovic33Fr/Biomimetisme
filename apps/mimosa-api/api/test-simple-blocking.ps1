# Test simple pour declencher un blocage et verifier l'historique
# Usage: .\test-simple-blocking.ps1

Write-Host "Test simple de blocage" -ForegroundColor Magenta
Write-Host "=====================" -ForegroundColor Magenta
Write-Host ""

# Test avec une IP fraiche
$testIp = "192.168.1.900"
Write-Host "Test avec IP: $testIp" -ForegroundColor Cyan
Write-Host ""

# Envoyer beaucoup de requetes rapidement
Write-Host "Envoi de 25 requetes rapides..." -ForegroundColor Yellow

for ($i = 1; $i -le 25; $i++) {
    try {
        $response = Invoke-WebRequest -Uri "http://localhost:8080/api/data" -Headers @{"X-Fake-IP"=$testIp} -UseBasicParsing
        
        if ($response.StatusCode -eq 200) {
            Write-Host "." -NoNewline -ForegroundColor Green
        } elseif ($response.StatusCode -eq 429) {
            $json = $response.Content | ConvertFrom-Json
            Write-Host "`nBLOQUE! $($json.message)" -ForegroundColor Red
            Write-Host "TTL: $($json.ttlMs)ms" -ForegroundColor Red
            break
        }
    } catch {
        Write-Host "E" -NoNewline -ForegroundColor Red
    }
    
    Start-Sleep -Milliseconds 50
}

Write-Host "`n"

# Verifier l'etat
Write-Host "Verification de l'etat:" -ForegroundColor Yellow
try {
    $response = Invoke-WebRequest -Uri "http://localhost:8080/api/state" -UseBasicParsing
    $state = $response.Content | ConvertFrom-Json
    $blockedIps = $state.items | Where-Object { $_.tripped -eq $true }
    
    if ($blockedIps.Count -gt 0) {
        Write-Host "IPs bloquees:" -ForegroundColor Red
        $blockedIps | ForEach-Object {
            Write-Host "  - $($_.ip): $($_.reason)" -ForegroundColor Red
        }
    } else {
        Write-Host "Aucune IP bloquee" -ForegroundColor Green
    }
} catch {
    Write-Host "Erreur: $($_.Exception.Message)" -ForegroundColor Red
}

Write-Host ""
Write-Host "Maintenant, ouvrez http://localhost:8080 dans votre navigateur" -ForegroundColor Cyan
Write-Host "et verifiez la section 'Historique des Blocages' en bas de page." -ForegroundColor Cyan
Write-Host "Chaque blocage doit apparaître UNE SEULE FOIS." -ForegroundColor Green
