# Script pour declencher agressivement le limiteur
# Usage: .\test-aggressive.ps1

Write-Host "Test agressif du limiteur Mimosa" -ForegroundColor Red
Write-Host "=================================" -ForegroundColor Red
Write-Host ""

# Test 1: Envoi tres rapide de requetes
Write-Host "1. Test RPS tres eleve (IP: 192.168.1.300):" -ForegroundColor Yellow
Write-Host "   Envoi de 20 requetes en 1 seconde..." -ForegroundColor Gray

$jobs = @()
for ($i = 1; $i -le 20; $i++) {
    $job = Start-Job -ScriptBlock {
        param($index)
        try {
            $response = Invoke-WebRequest -Uri "http://localhost:8080/api/data" -Headers @{"X-Fake-IP"="192.168.1.300"} -UseBasicParsing
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
    } -ArgumentList $i
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
    } else {
        Write-Host "   Requete $($result.Index): Erreur $($result.StatusCode)" -ForegroundColor Red
    }
}

Write-Host "   Resume: $successCount OK, $blockedCount bloques" -ForegroundColor Cyan
Write-Host ""

# Test 2: Diversite de chemins avec envoi rapide
Write-Host "2. Test diversite de chemins rapide (IP: 192.168.1.301):" -ForegroundColor Yellow
Write-Host "   Envoi de 10 chemins differents rapidement..." -ForegroundColor Gray

$jobs2 = @()
for ($i = 1; $i -le 10; $i++) {
    $job = Start-Job -ScriptBlock {
        param($index)
        try {
            $response = Invoke-WebRequest -Uri "http://localhost:8080/api/noisy/$index" -Headers @{"X-Fake-IP"="192.168.1.301"} -UseBasicParsing
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
    } -ArgumentList $i
    $jobs2 += $job
}

# Attendre que tous les jobs se terminent
$results2 = $jobs2 | Wait-Job | Receive-Job
$jobs2 | Remove-Job

# Analyser les resultats
$successCount2 = 0
$blockedCount2 = 0
foreach ($result in $results2) {
    if ($result.StatusCode -eq 200) {
        $successCount2++
        Write-Host "   /api/noisy/$($result.Index): OK" -ForegroundColor Green
    } elseif ($result.StatusCode -eq 429) {
        $blockedCount2++
        $json = $result.Content | ConvertFrom-Json
        Write-Host "   /api/noisy/$($result.Index): BLOQUE! $($json.message)" -ForegroundColor Red
    } else {
        Write-Host "   /api/noisy/$($result.Index): Erreur $($result.StatusCode)" -ForegroundColor Red
    }
}

Write-Host "   Resume: $successCount2 OK, $blockedCount2 bloques" -ForegroundColor Cyan
Write-Host ""

# Test 3: Etat final
Write-Host "3. Etat final des IPs:" -ForegroundColor Yellow
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
    Write-Host "   Erreur: $($_.Exception.Message)" -ForegroundColor Red
}

Write-Host ""
Write-Host "Test agressif termine!" -ForegroundColor Green
