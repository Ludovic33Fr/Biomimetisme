#!/usr/bin/env node

/**
 * Script de test pour simuler des IPs différentes via le header X-Fake-IP
 * Usage: node test-simulation.js
 */

import http from 'http';

const BASE_URL = 'http://localhost:8080';

// Configuration du limiteur (doit correspondre à celle du serveur)
const THRESHOLD_RPS = 10;
const PATH_DIVERSITY = 5;

function makeRequest(path, fakeIp = null) {
    return new Promise((resolve, reject) => {
        const options = {
            hostname: 'localhost',
            port: 8080,
            path: path,
            method: 'GET',
            headers: {}
        };

        if (fakeIp) {
            options.headers['X-Fake-IP'] = fakeIp;
        }

        const req = http.request(options, (res) => {
            let data = '';
            res.on('data', (chunk) => data += chunk);
            res.on('end', () => {
                try {
                    const json = JSON.parse(data);
                    resolve({ status: res.statusCode, data: json });
                } catch (e) {
                    resolve({ status: res.statusCode, data: data });
                }
            });
        });

        req.on('error', reject);
        req.end();
    });
}

async function testSimulation() {
    console.log('🧪 Test de simulation d\'IP avec X-Fake-IP header\n');

    // Test 1: IP normale (sans header)
    console.log('1️⃣ Test IP normale (sans header X-Fake-IP):');
    try {
        const result1 = await makeRequest('/api/data');
        console.log(`   Status: ${result1.status}`);
        console.log(`   Response: ${JSON.stringify(result1.data)}\n`);
    } catch (error) {
        console.log(`   Erreur: ${error.message}\n`);
    }

    // Test 2: IP simulée "192.168.1.100"
    console.log('2️⃣ Test IP simulée "192.168.1.100":');
    try {
        const result2 = await makeRequest('/api/data', '192.168.1.100');
        console.log(`   Status: ${result2.status}`);
        console.log(`   Response: ${JSON.stringify(result2.data)}\n`);
    } catch (error) {
        console.log(`   Erreur: ${error.message}\n`);
    }

    // Test 3: IP simulée "10.0.0.1" - déclenchement du limiteur
    console.log('3️⃣ Test IP simulée "10.0.0.1" - déclenchement du limiteur:');
    console.log('   Envoi de plusieurs requêtes rapides...');
    
    for (let i = 0; i < 15; i++) {
        try {
            const result = await makeRequest('/api/data', '10.0.0.1');
            console.log(`   Requête ${i + 1}: Status ${result.status} - ${result.data.status || 'OK'}`);
            
            if (result.status === 429) {
                console.log(`   🚨 IP 10.0.0.1 bloquée! TTL: ${result.data.ttlMs}ms`);
                break;
            }
        } catch (error) {
            console.log(`   Erreur requête ${i + 1}: ${error.message}`);
        }
        
        // Petite pause pour éviter de surcharger
        await new Promise(resolve => setTimeout(resolve, 100));
    }

    console.log('\n4️⃣ Test diversité de chemins avec IP "10.0.0.2":');
    console.log('   Envoi de requêtes vers différents endpoints...');
    
    const paths = ['/api/data', '/api/noisy/1', '/api/noisy/2', '/api/noisy/3', '/api/noisy/4', '/api/noisy/5'];
    
    for (let i = 0; i < paths.length; i++) {
        try {
            const result = await makeRequest(paths[i], '10.0.0.2');
            console.log(`   ${paths[i]}: Status ${result.status} - ${result.data.status || 'OK'}`);
            
            if (result.status === 429) {
                console.log(`   🚨 IP 10.0.0.2 bloquée par diversité! TTL: ${result.data.ttlMs}ms`);
                break;
            }
        } catch (error) {
            console.log(`   Erreur ${paths[i]}: ${error.message}`);
        }
        
        await new Promise(resolve => setTimeout(resolve, 200));
    }

    console.log('\n✅ Tests terminés!');
    console.log('\n💡 Conseils:');
    console.log('   - Vérifiez les logs du serveur pour voir les IPs simulées');
    console.log('   - Le dashboard à http://localhost:8080 montre l\'état des IPs');
    console.log('   - Les IPs simulées sont traitées indépendamment des vraies IPs');
}

// Vérifier que le serveur est en cours d'exécution
makeRequest('/api/config').then(() => {
    testSimulation();
}).catch((error) => {
    console.error('❌ Impossible de se connecter au serveur Mimosa API');
    console.error('   Assurez-vous que le serveur est démarré sur http://localhost:8080');
    console.error('   Erreur:', error.message);
    process.exit(1);
});
