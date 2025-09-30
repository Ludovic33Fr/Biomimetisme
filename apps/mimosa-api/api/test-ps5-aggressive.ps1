# Test agressif pour declencher le blocage sur les reservations PS5
# Usage: .\test-ps5-aggressive.ps1

Write-Host "Test agressif de reservation PlayStation 5" -ForegroundColor Magenta
Write-Host "=========================================" -ForegroundColor Magenta
Write-Host ""

Write-Host "Configuration du limiteur de reservation:" -ForegroundColor Cyan
Write-Host "  - Seuil RPS: 3" -ForegroundColor Gray
Write-Host "  - Diversite chemins: 2" -ForegroundColor Gray
Write-Host "  - Duree blocage: 60s" -ForegroundColor Gray
Write-Host "  - Fenetre: 5s" -ForegroundColor Gray
Write-Host ""

# Test 1: Tentatives tres rapides pour declencher le RPS
Write-Host "1. Test RPS eleve (IP: spammer1@example.com):" -ForegroundColor Yellow
Write-Host "   Envoi de 5 requetes en 1 seconde (5 RPS > 3 RPS)..." -ForegroundColor Gray

$spammerData = @{
    email = "spammer1@example.com"
    name = "Spam User 1"
    phone = "06 99 99 99 99"
    quantity = 1
} | ConvertTo-Json

$jobs = @()
for ($i = 1; $i -le 5; $i++) {
    $job = Start-Job -ScriptBlock {
        param($index, $data)
        try {
            $response = Invoke-WebRequest -Uri "http://localhost:8080/api/reserve-ps5" -Method POST -Body $data -ContentType "application/json" -Headers @{"X-Fake-IP"="spammer1@example.com"} -UseBasicParsing
            return @{
                Index = $index
                StatusCode = $response.StatusCode
                Content = $response.Content
            }
        } catch {
            return @{
                Index = $index
                StatusCode = 0
                Content = $_.Exception.Message
            }
        }
    } -ArgumentList $i, $spammerData
    $jobs += $job
}

# Attendre que tous les jobs se terminent
$results = $jobs | Wait-Job | Receive-Job
$jobs | Remove-Job

# Analyser les resultats
$successCount = 0
$blockedCount = 0
foreach ($result in $results) {
    if ($result.StatusCode -eq 200) {
        $successCount++
        Write-Host "   Requete $($result.Index): OK" -ForegroundColor Green
    } elseif ($result.StatusCode -eq 429) {
        $blockedCount++
        $json = $result.Content | ConvertFrom-Json
        Write-Host "   Requete $($result.Index): BLOQUE! $($json.message)" -ForegroundColor Red
        Write-Host "   TTL: $($json.ttlMs)ms" -ForegroundColor Red
    } else {
        Write-Host "   Requete $($result.Index): Erreur $($result.StatusCode)" -ForegroundColor Red
    }
}

Write-Host "   Resume: $successCount OK, $blockedCount bloques" -ForegroundColor Cyan
Write-Host ""

# Test 2: Diversite de chemins (simulation d'attaques multiples)
Write-Host "2. Test diversite de chemins (IP: spammer2@example.com):" -ForegroundColor Yellow
Write-Host "   Envoi de requetes vers differents endpoints..." -ForegroundColor Gray

$paths = @("/api/reserve-ps5", "/api/data", "/api/noisy/1")
$blocked2 = $false

foreach ($path in $paths) {
    try {
        if ($path -eq "/api/reserve-ps5") {
            $response = Invoke-WebRequest -Uri "http://localhost:8080$path" -Method POST -Body $spammerData -ContentType "application/json" -Headers @{"X-Fake-IP"="spammer2@example.com"} -UseBasicParsing
        } else {
            $response = Invoke-WebRequest -Uri "http://localhost:8080$path" -Headers @{"X-Fake-IP"="spammer2@example.com"} -UseBasicParsing
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
    
    Start-Sleep -Milliseconds 100
}

if (-not $blocked2) {
    Write-Host "   Aucun blocage par diversite" -ForegroundColor Yellow
}
Write-Host ""

# Test 3: Verification de l'etat final
Write-Host "3. Etat final des IPs:" -ForegroundColor Yellow
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

# Test 4: Instructions pour la page web
Write-Host "4. Instructions pour tester la page web:" -ForegroundColor Cyan
Write-Host "   Ouvrez http://localhost:8080/ps5 dans votre navigateur" -ForegroundColor Gray
Write-Host ""
Write-Host "   Pour declencher un blocage manuellement:" -ForegroundColor Yellow
Write-Host "   1. Remplissez le formulaire avec un email" -ForegroundColor Gray
Write-Host "   2. Cliquez sur 'Reserver PlayStation 5' 4 fois rapidement" -ForegroundColor Gray
Write-Host "   3. Observez le message de blocage" -ForegroundColor Gray
Write-Host "   4. Changez l'email et recommencez" -ForegroundColor Gray
Write-Host "   5. Regardez le dashboard principal pour voir les blocages" -ForegroundColor Gray

Write-Host ""
Write-Host "Test agressif termine!" -ForegroundColor Green
