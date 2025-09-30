#!/bin/bash

# Script de test avec curl pour démontrer l'utilisation du header X-Fake-IP
# Usage: ./test-curl.sh

echo "🧪 Test de simulation d'IP avec curl et header X-Fake-IP"
echo "========================================================"
echo ""

# Vérifier que le serveur est en cours d'exécution
echo "1️⃣ Vérification du serveur..."
if ! curl -s http://localhost:8080/api/config > /dev/null; then
    echo "❌ Serveur non accessible sur http://localhost:8080"
    echo "   Démarrez d'abord le serveur avec: npm start"
    exit 1
fi
echo "✅ Serveur accessible"
echo ""

# Test 1: Requête normale (sans header)
echo "2️⃣ Test IP normale (sans header X-Fake-IP):"
curl -s -w "Status: %{http_code}\n" http://localhost:8080/api/data
echo ""

# Test 2: IP simulée
echo "3️⃣ Test IP simulée '192.168.1.100':"
curl -s -w "Status: %{http_code}\n" -H "X-Fake-IP: 192.168.1.100" http://localhost:8080/api/data
echo ""

# Test 3: IP simulée différente
echo "4️⃣ Test IP simulée '10.0.0.1':"
curl -s -w "Status: %{http_code}\n" -H "X-Fake-IP: 10.0.0.1" http://localhost:8080/api/data
echo ""

# Test 4: Déclenchement du limiteur avec IP simulée
echo "5️⃣ Test déclenchement limiteur avec IP simulée '10.0.0.99':"
echo "   Envoi de 15 requêtes rapides..."

for i in {1..15}; do
    echo -n "   Requête $i: "
    response=$(curl -s -w "%{http_code}" -H "X-Fake-IP: 10.0.0.99" http://localhost:8080/api/data)
    status=${response: -3}
    body=${response%???}
    
    if [ "$status" = "200" ]; then
        echo "OK"
    elif [ "$status" = "429" ]; then
        echo "BLOQUÉ! $(echo $body | jq -r '.message // "Rate limited"')"
        break
    else
        echo "Erreur $status"
    fi
    
    sleep 0.1
done
echo ""

# Test 5: Diversité de chemins
echo "6️⃣ Test diversité de chemins avec IP '10.0.0.88':"
paths=("/api/data" "/api/noisy/1" "/api/noisy/2" "/api/noisy/3" "/api/noisy/4" "/api/noisy/5")

for path in "${paths[@]}"; do
    echo -n "   $path: "
    response=$(curl -s -w "%{http_code}" -H "X-Fake-IP: 10.0.0.88" "http://localhost:8080$path")
    status=${response: -3}
    body=${response%???}
    
    if [ "$status" = "200" ]; then
        echo "OK"
    elif [ "$status" = "429" ]; then
        echo "BLOQUÉ! $(echo $body | jq -r '.message // "Rate limited"')"
        break
    else
        echo "Erreur $status"
    fi
    
    sleep 0.2
done
echo ""

# Test 6: État des IPs
echo "7️⃣ État actuel des IPs (dashboard):"
curl -s http://localhost:8080/api/state | jq '.items[] | {ip: .ip, rps: .rps, tripped: .tripped, ttlMs: .ttlMs, reason: .reason}'
echo ""

echo "✅ Tests terminés!"
echo ""
echo "💡 Conseils:"
echo "   - Vérifiez les logs du serveur pour voir les IPs simulées"
echo "   - Le dashboard à http://localhost:8080 montre l'état des IPs"
echo "   - Les IPs simulées sont traitées indépendamment des vraies IPs"
echo "   - Utilisez 'X-Fake-IP: <ip>' dans vos requêtes pour simuler"
