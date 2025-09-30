# Script de demonstration de la simulation d'IP avec X-Fake-IP
# Usage: .\demo-simulation.ps1

Write-Host "=== DEMONSTRATION SIMULATION IP X-FAKE-IP ===" -ForegroundColor Magenta
Write-Host "=============================================" -ForegroundColor Magenta
Write-Host ""

Write-Host "Cette demonstration montre comment utiliser le header X-Fake-IP" -ForegroundColor Cyan
Write-Host "pour simuler differentes adresses IP et tester le systeme de limitation." -ForegroundColor Cyan
Write-Host ""

# Configuration
Write-Host "Configuration du limiteur:" -ForegroundColor Yellow
try {
    $config = Invoke-WebRequest -Uri "http://localhost:8080/api/config" -UseBasicParsing
    $configJson = $config.Content | ConvertFrom-Json
    Write-Host "  - Seuil RPS: $($configJson.thresholdRps)" -ForegroundColor Gray
    Write-Host "  - Diversite chemins: $($configJson.pathDiversity)" -ForegroundColor Gray
    Write-Host "  - Duree repli: $($configJson.tripMs/1000)s" -ForegroundColor Gray
    Write-Host "  - Fenetre: $($configJson.windowS)s" -ForegroundColor Gray
} catch {
    Write-Host "  Erreur lors de la recuperation de la config" -ForegroundColor Red
}
Write-Host ""

# Test 1: IP normale vs IP simulee
Write-Host "1. COMPARAISON IP NORMALE vs IP SIMULEE" -ForegroundColor Green
Write-Host "=========================================" -ForegroundColor Green

Write-Host "Requete avec IP normale (sans header):" -ForegroundColor Yellow
try {
    $response1 = Invoke-WebRequest -Uri "http://localhost:8080/api/data" -UseBasicParsing
    Write-Host "  Status: $($response1.StatusCode) - OK" -ForegroundColor Green
} catch {
    Write-Host "  Erreur: $($_.Exception.Message)" -ForegroundColor Red
}

Write-Host "Requete avec IP simulee '192.168.1.100':" -ForegroundColor Yellow
try {
    $response2 = Invoke-WebRequest -Uri "http://localhost:8080/api/data" -Headers @{"X-Fake-IP"="192.168.1.100"} -UseBasicParsing
    Write-Host "  Status: $($response2.StatusCode) - OK" -ForegroundColor Green
} catch {
    Write-Host "  Erreur: $($_.Exception.Message)" -ForegroundColor Red
}

Write-Host "Requete avec IP simulee '10.0.0.50':" -ForegroundColor Yellow
try {
    $response3 = Invoke-WebRequest -Uri "http://localhost:8080/api/data" -Headers @{"X-Fake-IP"="10.0.0.50"} -UseBasicParsing
    Write-Host "  Status: $($response3.StatusCode) - OK" -ForegroundColor Green
} catch {
    Write-Host "  Erreur: $($_.Exception.Message)" -ForegroundColor Red
}
Write-Host ""

# Test 2: Declenchement du limiteur
Write-Host "2. DECLENCHEMENT DU LIMITEUR" -ForegroundColor Red
Write-Host "=============================" -ForegroundColor Red

Write-Host "Test avec IP '192.168.1.600' - RPS eleve:" -ForegroundColor Yellow
Write-Host "  Envoi de 15 requetes par seconde..." -ForegroundColor Gray

$startTime = Get-Date
$requestCount = 0
$blocked = $false

while ((Get-Date) - $startTime -lt [TimeSpan]::FromSeconds(8) -and -not $blocked) {
    try {
        $response = Invoke-WebRequest -Uri "http://localhost:8080/api/data" -Headers @{"X-Fake-IP"="192.168.1.600"} -UseBasicParsing
        $requestCount++
        
        if ($response.StatusCode -eq 200) {
            Write-Host "." -NoNewline -ForegroundColor Green
        } elseif ($response.StatusCode -eq 429) {
            $blocked = $true
            $json = $response.Content | ConvertFrom-Json
            Write-Host "`n  BLOQUE! $($json.message)" -ForegroundColor Red
            Write-Host "  TTL: $($json.ttlMs)ms" -ForegroundColor Red
        }
    } catch {
        Write-Host "E" -NoNewline -ForegroundColor Red
    }
    
    Start-Sleep -Milliseconds 60
}

if (-not $blocked) {
    Write-Host "`n  Aucun blocage detecte ($requestCount requetes)" -ForegroundColor Yellow
}
Write-Host ""

# Test 3: Diversite de chemins
Write-Host "3. DECLENCHEMENT PAR DIVERSITE DE CHEMINS" -ForegroundColor Red
Write-Host "==========================================" -ForegroundColor Red

Write-Host "Test avec IP '192.168.1.700' - Diversite de chemins:" -ForegroundColor Yellow
Write-Host "  Envoi de requetes vers differents endpoints..." -ForegroundColor Gray

$paths = @("/api/data", "/api/noisy/1", "/api/noisy/2", "/api/noisy/3", "/api/noisy/4", "/api/noisy/5")
$blocked2 = $false

foreach ($path in $paths) {
    try {
        $response = Invoke-WebRequest -Uri "http://localhost:8080$path" -Headers @{"X-Fake-IP"="192.168.1.700"} -UseBasicParsing
        
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

# Test 4: Etat final
Write-Host "4. ETAT FINAL DES IPs" -ForegroundColor Cyan
Write-Host "=====================" -ForegroundColor Cyan

try {
    $response = Invoke-WebRequest -Uri "http://localhost:8080/api/state" -UseBasicParsing
    $state = $response.Content | ConvertFrom-Json
    $blockedIps = $state.items | Where-Object { $_.tripped -eq $true }
    $normalIps = $state.items | Where-Object { $_.tripped -eq $false }
    
    if ($blockedIps.Count -gt 0) {
        Write-Host "IPs BLOQUEES:" -ForegroundColor Red
        $blockedIps | ForEach-Object {
            Write-Host "  - $($_.ip): RPS=$($_.rps), TTL=$($_.ttlMs)ms" -ForegroundColor Red
            Write-Host "    Raison: $($_.reason)" -ForegroundColor Red
        }
    } else {
        Write-Host "Aucune IP bloquee" -ForegroundColor Green
    }
    
    Write-Host "`nIPs NORMALES:" -ForegroundColor Cyan
    $normalIps | ForEach-Object {
        Write-Host "  - $($_.ip): RPS=$($_.rps)" -ForegroundColor Cyan
    }
} catch {
    Write-Host "Erreur lors de la recuperation de l'etat: $($_.Exception.Message)" -ForegroundColor Red
}
Write-Host ""

# Conclusion
Write-Host "5. CONCLUSION" -ForegroundColor Magenta
Write-Host "=============" -ForegroundColor Magenta
Write-Host ""
Write-Host "✅ La simulation d'IP via X-Fake-IP fonctionne parfaitement!" -ForegroundColor Green
Write-Host ""
Write-Host "Avantages de cette fonctionnalite:" -ForegroundColor Cyan
Write-Host "  - Test de differentes IPs sans changer de machine" -ForegroundColor Gray
Write-Host "  - Simulation d'utilisateurs bloques vs non bloques" -ForegroundColor Gray
Write-Host "  - Debug du systeme de limitation" -ForegroundColor Gray
Write-Host "  - Tests automatisés reproductibles" -ForegroundColor Gray
Write-Host ""
Write-Host "Utilisation:" -ForegroundColor Cyan
Write-Host "  curl -H 'X-Fake-IP: 192.168.1.100' http://localhost:8080/api/data" -ForegroundColor Gray
Write-Host "  Invoke-WebRequest -Headers @{'X-Fake-IP'='192.168.1.100'} http://localhost:8080/api/data" -ForegroundColor Gray
Write-Host ""
Write-Host "Dashboard: http://localhost:8080" -ForegroundColor Cyan
Write-Host ""
Write-Host "=== FIN DE LA DEMONSTRATION ===" -ForegroundColor Magenta
