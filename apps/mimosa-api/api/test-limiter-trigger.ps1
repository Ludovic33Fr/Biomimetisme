# Script pour declencher le limiteur avec des IPs simulees
# Usage: .\test-limiter-trigger.ps1

Write-Host "Test de declenchement du limiteur Mimosa" -ForegroundColor Red
Write-Host "=============================================" -ForegroundColor Red
Write-Host ""

# Configuration
$thresholdRps = 10
$pathDiversity = 5
$tripMs = 30000

Write-Host "Configuration actuelle:" -ForegroundColor Cyan
Write-Host "  - Seuil RPS: $thresholdRps" -ForegroundColor Gray
Write-Host "  - Diversite chemins: $pathDiversity" -ForegroundColor Gray
Write-Host "  - Duree repli: $($tripMs/1000)s" -ForegroundColor Gray
Write-Host ""

# Test 1: Declenchement par RPS eleve
Write-Host "1. Test declenchement par RPS eleve (IP: 192.168.1.200):" -ForegroundColor Yellow
Write-Host "   Envoi de $($thresholdRps + 5) requetes rapides..." -ForegroundColor Gray

for ($i = 1; $i -le ($thresholdRps + 5); $i++) {
    try {
        $response = Invoke-WebRequest -Uri "http://localhost:8080/api/data" -Headers @{"X-Fake-IP"="192.168.1.200"} -UseBasicParsing
        if ($response.StatusCode -eq 200) {
            Write-Host "   Requete $i`: OK" -ForegroundColor Green
        } elseif ($response.StatusCode -eq 429) {
            $json = $response.Content | ConvertFrom-Json
            Write-Host "   Requete $i`: BLOQUE! $($json.message)" -ForegroundColor Red
            Write-Host "   TTL: $($json.ttlMs)ms" -ForegroundColor Red
            break
        }
    } catch {
        Write-Host "   Requete $i`: Erreur $($_.Exception.Message)" -ForegroundColor Red
    }
    
    Start-Sleep -Milliseconds 50
}
Write-Host ""

# Test 2: Declenchement par diversite de chemins
Write-Host "2. Test declenchement par diversite de chemins (IP: 192.168.1.201):" -ForegroundColor Yellow
Write-Host "   Envoi de requetes vers $($pathDiversity + 2) chemins differents..." -ForegroundColor Gray

$paths = @()
for ($i = 1; $i -le ($pathDiversity + 2); $i++) {
    $paths += "/api/noisy/$i"
}

foreach ($path in $paths) {
    try {
        $response = Invoke-WebRequest -Uri "http://localhost:8080$path" -Headers @{"X-Fake-IP"="192.168.1.201"} -UseBasicParsing
        if ($response.StatusCode -eq 200) {
            Write-Host "   $path`: OK" -ForegroundColor Green
        } elseif ($response.StatusCode -eq 429) {
            $json = $response.Content | ConvertFrom-Json
            Write-Host "   $path`: BLOQUE! $($json.message)" -ForegroundColor Red
            Write-Host "   TTL: $($json.ttlMs)ms" -ForegroundColor Red
            break
        }
    } catch {
        Write-Host "   $path`: Erreur $($_.Exception.Message)" -ForegroundColor Red
    }
    
    Start-Sleep -Milliseconds 100
}
Write-Host ""

# Test 3: Verification de l'etat des IPs bloquees
Write-Host "3. Etat des IPs apres les tests:" -ForegroundColor Yellow
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
    Write-Host "   Erreur lors de la recuperation de l'etat: $($_.Exception.Message)" -ForegroundColor Red
}
Write-Host ""

Write-Host "Tests de declenchement termines!" -ForegroundColor Green
Write-Host ""
Write-Host "Pour voir le dashboard en temps reel:" -ForegroundColor Cyan
Write-Host "   Ouvrez http://localhost:8080 dans votre navigateur" -ForegroundColor Gray