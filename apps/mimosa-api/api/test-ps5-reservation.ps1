# Script de test pour la page de reservation PlayStation 5
# Usage: .\test-ps5-reservation.ps1

Write-Host "Test de reservation PlayStation 5 avec Mimosa" -ForegroundColor Magenta
Write-Host "=============================================" -ForegroundColor Magenta
Write-Host ""

Write-Host "Ce test va simuler des tentatives de reservation pour declencher le blocage" -ForegroundColor Cyan
Write-Host ""

# Test 1: Reservation normale
Write-Host "1. Test reservation normale (IP: user1@example.com):" -ForegroundColor Yellow

$reservationData = @{
    email = "user1@example.com"
    name = "Jean Dupont"
    phone = "06 12 34 56 78"
    quantity = 1
} | ConvertTo-Json

try {
    $response = Invoke-WebRequest -Uri "http://localhost:8080/api/reserve-ps5" -Method POST -Body $reservationData -ContentType "application/json" -Headers @{"X-Fake-IP"="user1@example.com"} -UseBasicParsing
    $result = $response.Content | ConvertFrom-Json
    Write-Host "   Status: $($response.StatusCode)" -ForegroundColor Green
    Write-Host "   Message: $($result.message)" -ForegroundColor Green
    if ($result.reference) {
        Write-Host "   Reference: $($result.reference)" -ForegroundColor Cyan
    }
} catch {
    Write-Host "   Erreur: $($_.Exception.Message)" -ForegroundColor Red
}
Write-Host ""

# Test 2: Tentatives multiples pour declencher le blocage
Write-Host "2. Test tentatives multiples pour declencher le blocage (IP: spammer@example.com):" -ForegroundColor Yellow
Write-Host "   Envoi de 5 tentatives rapides..." -ForegroundColor Gray

$spammerData = @{
    email = "spammer@example.com"
    name = "Spam User"
    phone = "06 99 99 99 99"
    quantity = 1
} | ConvertTo-Json

for ($i = 1; $i -le 5; $i++) {
    try {
        $response = Invoke-WebRequest -Uri "http://localhost:8080/api/reserve-ps5" -Method POST -Body $spammerData -ContentType "application/json" -Headers @{"X-Fake-IP"="spammer@example.com"} -UseBasicParsing
        $result = $response.Content | ConvertFrom-Json
        
        if ($response.StatusCode -eq 200) {
            Write-Host "   Tentative $i`: OK - $($result.message)" -ForegroundColor Green
        } elseif ($response.StatusCode -eq 429) {
            Write-Host "   Tentative $i`: BLOQUE! $($result.message)" -ForegroundColor Red
            Write-Host "   TTL: $($result.ttlMs)ms" -ForegroundColor Red
            break
        }
    } catch {
        Write-Host "   Tentative $i`: Erreur - $($_.Exception.Message)" -ForegroundColor Red
    }
    
    Start-Sleep -Milliseconds 200
}
Write-Host ""

# Test 3: Test avec differentes IPs
Write-Host "3. Test avec differentes IPs (simulation d'utilisateurs multiples):" -ForegroundColor Yellow

$users = @(
    @{email="user2@example.com"; name="Marie Martin"; phone="06 22 33 44 55"},
    @{email="user3@example.com"; name="Pierre Durand"; phone="06 33 44 55 66"},
    @{email="user4@example.com"; name="Sophie Bernard"; phone="06 44 55 66 77"}
)

foreach ($user in $users) {
    $userData = @{
        email = $user.email
        name = $user.name
        phone = $user.phone
        quantity = 1
    } | ConvertTo-Json
    
    try {
        $response = Invoke-WebRequest -Uri "http://localhost:8080/api/reserve-ps5" -Method POST -Body $userData -ContentType "application/json" -Headers @{"X-Fake-IP"=$user.email} -UseBasicParsing
        $result = $response.Content | ConvertFrom-Json
        
        if ($response.StatusCode -eq 200) {
            Write-Host "   $($user.email): OK - $($result.message)" -ForegroundColor Green
        } elseif ($response.StatusCode -eq 429) {
            Write-Host "   $($user.email): BLOQUE! $($result.message)" -ForegroundColor Red
        }
    } catch {
        Write-Host "   $($user.email): Erreur - $($_.Exception.Message)" -ForegroundColor Red
    }
    
    Start-Sleep -Milliseconds 500
}
Write-Host ""

# Test 4: Verification de l'etat des IPs
Write-Host "4. Verification de l'etat des IPs:" -ForegroundColor Yellow
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

# Test 5: Instructions pour tester la page web
Write-Host "5. Instructions pour tester la page web:" -ForegroundColor Cyan
Write-Host "   Ouvrez http://localhost:8080/ps5 dans votre navigateur" -ForegroundColor Gray
Write-Host ""
Write-Host "   Fonctionnalites a tester:" -ForegroundColor Green
Write-Host "   - Formulaire de reservation fonctionnel" -ForegroundColor Gray
Write-Host "   - Blocage automatique apres 3 tentatives" -ForegroundColor Gray
Write-Host "   - Messages d'erreur en temps reel" -ForegroundColor Gray
Write-Host "   - Indicateur de limitation de taux" -ForegroundColor Gray
Write-Host "   - Connexion WebSocket pour les notifications" -ForegroundColor Gray
Write-Host ""
Write-Host "   Pour declencher un blocage:" -ForegroundColor Yellow
Write-Host "   - Remplissez le formulaire et soumettez 4 fois rapidement" -ForegroundColor Gray
Write-Host "   - Changez l'email et recommencez" -ForegroundColor Gray
Write-Host "   - Observez le blocage dans le dashboard principal" -ForegroundColor Gray

Write-Host ""
Write-Host "Test de reservation PlayStation 5 termine!" -ForegroundColor Green
Write-Host ""
Write-Host "Configuration du limiteur de reservation:" -ForegroundColor Cyan
Write-Host "   - Seuil RPS: 3 (au lieu de 10)" -ForegroundColor Gray
Write-Host "   - Diversite chemins: 2 (au lieu de 5)" -ForegroundColor Gray
Write-Host "   - Duree blocage: 60s (au lieu de 30s)" -ForegroundColor Gray
Write-Host "   - Fenetre: 5s (au lieu de 10s)" -ForegroundColor Gray
