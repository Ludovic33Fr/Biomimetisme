# Test pour forcer un blocage avec des requetes tres rapides
# Usage: .\test-force-blocking.ps1

Write-Host "Test force de blocage" -ForegroundColor Magenta
Write-Host "====================" -ForegroundColor Magenta
Write-Host ""

$testIp = "192.168.1.950"
Write-Host "Test avec IP: $testIp" -ForegroundColor Cyan
Write-Host "Configuration: Seuil RPS=10, Fenetre=10s" -ForegroundColor Gray
Write-Host ""

# Envoyer 20 requetes en 1 seconde (20 RPS > 10 RPS)
Write-Host "Envoi de 20 requetes en 1 seconde (20 RPS)..." -ForegroundColor Yellow

$jobs = @()
for ($i = 1; $i -le 20; $i++) {
    $job = Start-Job -ScriptBlock {
        param($index, $ip)
        try {
            $response = Invoke-WebRequest -Uri "http://localhost:8080/api/data" -Headers @{"X-Fake-IP"=$ip} -UseBasicParsing
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
    } -ArgumentList $i, $testIp
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
        Write-Host "." -NoNewline -ForegroundColor Green
    } elseif ($result.StatusCode -eq 429) {
        $blockedCount++
        $json = $result.Content | ConvertFrom-Json
        Write-Host "`nBLOQUE! $($json.message)" -ForegroundColor Red
        Write-Host "TTL: $($json.ttlMs)ms" -ForegroundColor Red
    } else {
        Write-Host "E" -NoNewline -ForegroundColor Red
    }
}

Write-Host "`nResume: $successCount OK, $blockedCount bloques" -ForegroundColor Cyan
Write-Host ""

# Test de diversite de chemins
Write-Host "Test de diversite de chemins (IP: 192.168.1.951)..." -ForegroundColor Yellow

$paths = @("/api/data", "/api/noisy/1", "/api/noisy/2", "/api/noisy/3", "/api/noisy/4", "/api/noisy/5")
$blocked2 = $false

foreach ($path in $paths) {
    try {
        $response = Invoke-WebRequest -Uri "http://localhost:8080$path" -Headers @{"X-Fake-IP"="192.168.1.951"} -UseBasicParsing
        
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
    
    Start-Sleep -Milliseconds 100
}

if (-not $blocked2) {
    Write-Host "  Aucun blocage par diversite" -ForegroundColor Yellow
}
Write-Host ""

# Verifier l'etat final
Write-Host "Etat final des IPs:" -ForegroundColor Yellow
try {
    $response = Invoke-WebRequest -Uri "http://localhost:8080/api/state" -UseBasicParsing
    $state = $response.Content | ConvertFrom-Json
    $blockedIps = $state.items | Where-Object { $_.tripped -eq $true }
    
    if ($blockedIps.Count -gt 0) {
        Write-Host "IPs bloquees:" -ForegroundColor Red
        $blockedIps | ForEach-Object {
            Write-Host "  - $($_.ip): RPS=$($_.rps), TTL=$($_.ttlMs)ms" -ForegroundColor Red
            Write-Host "    Raison: $($_.reason)" -ForegroundColor Red
        }
    } else {
        Write-Host "Aucune IP bloquee" -ForegroundColor Green
    }
} catch {
    Write-Host "Erreur: $($_.Exception.Message)" -ForegroundColor Red
}

Write-Host ""
Write-Host "Ouvrez http://localhost:8080 pour verifier l'historique des blocages" -ForegroundColor Cyan
Write-Host "Chaque blocage doit apparaître UNE SEULE FOIS dans l'historique." -ForegroundColor Green
