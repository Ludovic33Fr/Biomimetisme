# Test de la nouvelle configuration avec 10 RPS par défaut et 0.5 RPS pour /api/reserve-ps5

Write-Host "🧪 Test de la nouvelle configuration Mimosa" -ForegroundColor Green
Write-Host ""

# Test 1: Configuration par défaut (10 RPS)
Write-Host "📊 Test 1: Route par défaut (/api/data) - 10 RPS" -ForegroundColor Yellow
Write-Host "Envoi de 12 requêtes rapides (devrait déclencher le rate limiting à 10 RPS)..."

$successCount = 0
$blockedCount = 0

for ($i = 1; $i -le 12; $i++) {
    try {
        $response = Invoke-RestMethod -Uri "http://localhost:8080/api/data" -Method GET -TimeoutSec 5
        $successCount++
        Write-Host "✅ Requête $i : Succès" -ForegroundColor Green
    }
    catch {
        if ($_.Exception.Response.StatusCode -eq 429) {
            $blockedCount++
            Write-Host "🚫 Requête $i : Bloquée (Rate Limited)" -ForegroundColor Red
        } else {
            Write-Host "❌ Requête $i : Erreur - $($_.Exception.Message)" -ForegroundColor Red
        }
    }
    Start-Sleep -Milliseconds 50
}

Write-Host ""
Write-Host "📈 Résultats route par défaut:"
Write-Host "  - Succès: $successCount"
Write-Host "  - Bloquées: $blockedCount"
Write-Host ""

# Attendre que le rate limiting se remette
Write-Host "⏳ Attente de 35 secondes pour que le rate limiting se remette..." -ForegroundColor Yellow
Start-Sleep -Seconds 35

# Test 2: Route de réservation PS5 (0.5 RPS)
Write-Host "🎮 Test 2: Route de réservation PS5 (/api/reserve-ps5) - 0.5 RPS" -ForegroundColor Yellow
Write-Host "Envoi de 3 requêtes avec 2 secondes d'intervalle (devrait déclencher le rate limiting à 0.5 RPS)..."

$successCount2 = 0
$blockedCount2 = 0

for ($i = 1; $i -le 3; $i++) {
    try {
        $body = @{
            email = "test$i@example.com"
            name = "Test User $i"
            phone = "0123456789"
            quantity = "1"
        } | ConvertTo-Json

        $response = Invoke-RestMethod -Uri "http://localhost:8080/api/reserve-ps5" -Method POST -Body $body -ContentType "application/json" -TimeoutSec 5
        $successCount2++
        Write-Host "✅ Réservation $i : Succès - Référence: $($response.reference)" -ForegroundColor Green
    }
    catch {
        if ($_.Exception.Response.StatusCode -eq 429) {
            $blockedCount2++
            Write-Host "🚫 Réservation $i : Bloquée (Rate Limited)" -ForegroundColor Red
        } else {
            Write-Host "❌ Réservation $i : Erreur - $($_.Exception.Message)" -ForegroundColor Red
        }
    }
    
    if ($i -lt 3) {
        Start-Sleep -Seconds 2
    }
}

Write-Host ""
Write-Host "📈 Résultats route de réservation:"
Write-Host "  - Succès: $successCount2"
Write-Host "  - Bloquées: $blockedCount2"
Write-Host ""

# Test 3: Vérifier la configuration
Write-Host "⚙️ Test 3: Vérification de la configuration" -ForegroundColor Yellow
try {
    $config = Invoke-RestMethod -Uri "http://localhost:8080/api/config" -Method GET
    Write-Host "Configuration par défaut:"
    Write-Host "  - RPS: $($config.default.thresholdRps)"
    Write-Host "  - Fenêtre: $($config.default.windowS)s"
    Write-Host "  - Diversité: $($config.default.pathDiversity)"
    Write-Host "  - Blocage: $($config.default.tripMs)ms"
    Write-Host ""
    Write-Host "Configurations spécifiques:"
    foreach ($route in $config.routes) {
        Write-Host "  - Route: $($route.path)"
        Write-Host "    - RPS: $($route.config.thresholdRps)"
        Write-Host "    - Fenêtre: $($route.config.windowS)s"
        Write-Host "    - Diversité: $($route.config.pathDiversity)"
        Write-Host "    - Blocage: $($route.config.tripMs)ms"
    }
} catch {
    Write-Host "❌ Erreur lors de la récupération de la configuration: $($_.Exception.Message)" -ForegroundColor Red
}

Write-Host ""
Write-Host "🎉 Tests terminés!" -ForegroundColor Green
