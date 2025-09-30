# Test de demonstration de l'historique des blocages
# Ce script simule des blocages pour tester l'affichage

Write-Host "Demonstration de l'historique des blocages" -ForegroundColor Magenta
Write-Host "=========================================" -ForegroundColor Magenta
Write-Host ""

Write-Host "Ce test va simuler des blocages pour verifier l'affichage de l'historique" -ForegroundColor Cyan
Write-Host ""

# Test 1: Simulation de blocage par RPS
Write-Host "1. Simulation blocage par RPS eleve (IP: 192.168.1.999):" -ForegroundColor Yellow

# Envoyer beaucoup de requetes tres rapidement
for ($i = 1; $i -le 30; $i++) {
    try {
        $response = Invoke-WebRequest -Uri "http://localhost:8080/api/data" -Headers @{"X-Fake-IP"="192.168.1.999"} -UseBasicParsing
        
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
    
    # Pas de sleep pour aller tres vite
}

Write-Host "`n"

# Test 2: Simulation de blocage par diversite
Write-Host "2. Simulation blocage par diversite (IP: 192.168.1.998):" -ForegroundColor Yellow

$paths = @("/api/data", "/api/noisy/1", "/api/noisy/2", "/api/noisy/3", "/api/noisy/4", "/api/noisy/5", "/api/noisy/6")
foreach ($path in $paths) {
    try {
        $response = Invoke-WebRequest -Uri "http://localhost:8080$path" -Headers @{"X-Fake-IP"="192.168.1.998"} -UseBasicParsing
        
        if ($response.StatusCode -eq 200) {
            Write-Host "  $path`: OK" -ForegroundColor Green
        } elseif ($response.StatusCode -eq 429) {
            $json = $response.Content | ConvertFrom-Json
            Write-Host "  $path`: BLOQUE! $($json.message)" -ForegroundColor Red
            Write-Host "  TTL: $($json.ttlMs)ms" -ForegroundColor Red
            break
        }
    } catch {
        Write-Host "  $path`: Erreur" -ForegroundColor Red
    }
    
    Start-Sleep -Milliseconds 50
}

Write-Host ""

# Test 3: Verification de l'etat
Write-Host "3. Verification de l'etat des IPs:" -ForegroundColor Yellow
try {
    $response = Invoke-WebRequest -Uri "http://localhost:8080/api/state" -UseBasicParsing
    $state = $response.Content | ConvertFrom-Json
    $blockedIps = $state.items | Where-Object { $_.tripped -eq $true }
    
    if ($blockedIps.Count -gt 0) {
        Write-Host "IPs bloquees detectees:" -ForegroundColor Red
        $blockedIps | ForEach-Object {
            Write-Host "  - $($_.ip): RPS=$($_.rps), TTL=$($_.ttlMs)ms" -ForegroundColor Red
            Write-Host "    Raison: $($_.reason)" -ForegroundColor Red
        }
    } else {
        Write-Host "Aucune IP bloquee detectee" -ForegroundColor Yellow
        Write-Host "Le systeme semble tres resistant aux blocages" -ForegroundColor Gray
    }
} catch {
    Write-Host "Erreur lors de la verification: $($_.Exception.Message)" -ForegroundColor Red
}

Write-Host ""
Write-Host "4. Instructions pour verifier l'historique:" -ForegroundColor Cyan
Write-Host "   Ouvrez http://localhost:8080 dans votre navigateur" -ForegroundColor Gray
Write-Host "   Allez en bas de la page dans la section 'Historique des Blocages'" -ForegroundColor Gray
Write-Host ""
Write-Host "   Si des blocages ont ete detectes:" -ForegroundColor Green
Write-Host "   - Chaque IP doit apparaître UNE SEULE FOIS" -ForegroundColor Green
Write-Host "   - Les entrées doivent être triées par ordre chronologique" -ForegroundColor Green
Write-Host "   - Chaque entrée doit afficher: IP, raison, heure, TTL" -ForegroundColor Green
Write-Host ""
Write-Host "   Si aucun blocage n'a ete detecte:" -ForegroundColor Yellow
Write-Host "   - L'historique doit afficher 'Aucun blocage récent'" -ForegroundColor Yellow
Write-Host "   - Vous pouvez tester manuellement avec les boutons de test" -ForegroundColor Yellow

Write-Host ""
Write-Host "Demonstration terminee!" -ForegroundColor Green
