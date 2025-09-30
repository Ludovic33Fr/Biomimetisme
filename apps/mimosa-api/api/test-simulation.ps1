# Script de test PowerShell pour simuler des IPs différentes via le header X-Fake-IP
# Usage: .\test-simulation.ps1

Write-Host "🧪 Test de simulation d'IP avec X-Fake-IP header" -ForegroundColor Cyan
Write-Host "========================================================" -ForegroundColor Cyan
Write-Host ""

# Test 1: Requête normale (sans header)
Write-Host "1️⃣ Test IP normale (sans header X-Fake-IP):" -ForegroundColor Yellow
try {
    $response = Invoke-WebRequest -Uri "http://localhost:8080/api/data" -UseBasicParsing
    Write-Host "   Status: $($response.StatusCode)" -ForegroundColor Green
    Write-Host "   Response: $($response.Content)" -ForegroundColor Green
} catch {
    Write-Host "   Erreur: $($_.Exception.Message)" -ForegroundColor Red
}
Write-Host ""

# Test 2: IP simulée
Write-Host "2️⃣ Test IP simulée '192.168.1.100':" -ForegroundColor Yellow
try {
    $response = Invoke-WebRequest -Uri "http://localhost:8080/api/data" -Headers @{"X-Fake-IP"="192.168.1.100"} -UseBasicParsing
    Write-Host "   Status: $($response.StatusCode)" -ForegroundColor Green
    Write-Host "   Response: $($response.Content)" -ForegroundColor Green
} catch {
    Write-Host "   Erreur: $($_.Exception.Message)" -ForegroundColor Red
}
Write-Host ""

# Test 3: IP simulée différente
Write-Host "3️⃣ Test IP simulée '10.0.0.1':" -ForegroundColor Yellow
try {
    $response = Invoke-WebRequest -Uri "http://localhost:8080/api/data" -Headers @{"X-Fake-IP"="10.0.0.1"} -UseBasicParsing
    Write-Host "   Status: $($response.StatusCode)" -ForegroundColor Green
    Write-Host "   Response: $($response.Content)" -ForegroundColor Green
} catch {
    Write-Host "   Erreur: $($_.Exception.Message)" -ForegroundColor Red
}
Write-Host ""

# Test 4: Déclenchement du limiteur avec IP simulée
Write-Host "4️⃣ Test déclenchement limiteur avec IP simulée '10.0.0.99':" -ForegroundColor Yellow
Write-Host "   Envoi de 15 requêtes rapides..." -ForegroundColor Gray

for ($i = 1; $i -le 15; $i++) {
    try {
        $response = Invoke-WebRequest -Uri "http://localhost:8080/api/data" -Headers @{"X-Fake-IP"="10.0.0.99"} -UseBasicParsing
        if ($response.StatusCode -eq 200) {
            Write-Host "   Requête $i`: OK" -ForegroundColor Green
        } elseif ($response.StatusCode -eq 429) {
            $json = $response.Content | ConvertFrom-Json
            Write-Host "   Requête $i`: BLOQUÉ! $($json.message)" -ForegroundColor Red
            break
        } else {
            Write-Host "   Requête $i`: Erreur $($response.StatusCode)" -ForegroundColor Red
        }
    } catch {
        Write-Host "   Requête $i`: Erreur $($_.Exception.Message)" -ForegroundColor Red
    }
    
    Start-Sleep -Milliseconds 100
}
Write-Host ""

# Test 5: Diversité de chemins
Write-Host "5️⃣ Test diversité de chemins avec IP '10.0.0.88':" -ForegroundColor Yellow
$paths = @("/api/data", "/api/noisy/1", "/api/noisy/2", "/api/noisy/3", "/api/noisy/4", "/api/noisy/5")

foreach ($path in $paths) {
    try {
        $response = Invoke-WebRequest -Uri "http://localhost:8080$path" -Headers @{"X-Fake-IP"="10.0.0.88"} -UseBasicParsing
        if ($response.StatusCode -eq 200) {
            Write-Host "   $path`: OK" -ForegroundColor Green
        } elseif ($response.StatusCode -eq 429) {
            $json = $response.Content | ConvertFrom-Json
            Write-Host "   $path`: BLOQUÉ! $($json.message)" -ForegroundColor Red
            break
        } else {
            Write-Host "   $path`: Erreur $($response.StatusCode)" -ForegroundColor Red
        }
    } catch {
        Write-Host "   $path`: Erreur $($_.Exception.Message)" -ForegroundColor Red
    }
    
    Start-Sleep -Milliseconds 200
}
Write-Host ""

# Test 6: État des IPs
Write-Host "6️⃣ État actuel des IPs (dashboard):" -ForegroundColor Yellow
try {
    $response = Invoke-WebRequest -Uri "http://localhost:8080/api/state" -UseBasicParsing
    $state = $response.Content | ConvertFrom-Json
    $state.items | ForEach-Object {
        Write-Host "   IP: $($_.ip), RPS: $($_.rps), Tripped: $($_.tripped), TTL: $($_.ttlMs)ms, Reason: $($_.reason)" -ForegroundColor Cyan
    }
} catch {
    Write-Host "   Erreur lors de la récupération de l'état: $($_.Exception.Message)" -ForegroundColor Red
}
Write-Host ""

Write-Host "✅ Tests terminés!" -ForegroundColor Green
Write-Host ""
Write-Host "💡 Conseils:" -ForegroundColor Cyan
Write-Host "   - Vérifiez les logs du serveur pour voir les IPs simulées" -ForegroundColor Gray
Write-Host "   - Le dashboard à http://localhost:8080 montre l'état des IPs" -ForegroundColor Gray
Write-Host "   - Les IPs simulées sont traitées indépendamment des vraies IPs" -ForegroundColor Gray
Write-Host "   - Utilisez 'X-Fake-IP: <ip>' dans vos requêtes pour simuler" -ForegroundColor Gray
