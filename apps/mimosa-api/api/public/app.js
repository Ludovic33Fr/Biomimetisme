// Mimosa API Dashboard - Client JavaScript
// Ce fichier contient la logique côté client pour le dashboard

console.log('🌿 Mimosa API Dashboard - app.js chargé');

// Variables globales
let socket = null;
let isConnected = false;
let metrics = {
    rps: 0,
    diversity: 0,
    tripped: false,
    ttl: 0
};

// Initialisation de la connexion Socket.IO
function initWebSocket() {
    console.log('🔌 Tentative de connexion Socket.IO...');
    
    socket = io();
    
    socket.on('connect', function() {
        console.log('✅ Socket.IO connecté');
        isConnected = true;
        updateConnectionStatus(true);
    });
    
    socket.on('disconnect', function() {
        console.log('🔌 Socket.IO déconnecté');
        isConnected = false;
        updateConnectionStatus(false);
    });
    
    socket.on('connect_error', function(error) {
        console.error('❌ Erreur connexion Socket.IO:', error);
    });
    
    // Gestion des messages Socket.IO
    socket.on('metrics', function(data) {
        console.log('📊 Métriques reçues:', data);
        handleWebSocketMessage({ type: 'metrics', payload: data });
    });
    
    socket.on('status', function(data) {
        handleWebSocketMessage({ type: 'status', payload: data });
    });
    
    socket.on('trip', function(data) {
        console.log('🚨 Événement de blocage reçu:', data);
        handleWebSocketMessage({ type: 'blocked', payload: data });
    });
    
    socket.on('recover', function(data) {
        console.log('✅ Événement de récupération reçu:', data);
        handleWebSocketMessage({ type: 'recovered', payload: data });
    });
    
    socket.on('ip-data', function(data) {
        console.log('🌿 Données IP reçues:', data);
        updateIpMonitoring(data);
    });
    
    socket.on('ips-cleared', function(data) {
        console.log('🗑️ Données IP effacées:', data);
        clearIpMonitoringDisplay();
    });
}

// Gestion des messages WebSocket
function handleWebSocketMessage(data) {
    console.log('📨 Message WebSocket reçu:', data);
    
    switch (data.type) {
        case 'metrics':
            updateMetrics(data.payload);
            break;
        case 'status':
            updateStatus(data.payload);
            break;
        case 'blocked':
            addBlockedEntry(data.payload);
            break;
        case 'recovered':
            addRecoveredEntry(data.payload);
            break;
        default:
            console.log('📨 Message non géré:', data);
    }
}

// Mise à jour des métriques
function updateMetrics(newMetrics) {
    metrics = { ...metrics, ...newMetrics };
    
    // Mise à jour de l'affichage
    const rpsElement = document.getElementById('th-rps');
    const divElement = document.getElementById('th-div');
    const tripElement = document.getElementById('th-trip');
    
    if (rpsElement) rpsElement.textContent = metrics.rps || 0;
    if (divElement) divElement.textContent = metrics.diversity || 0;
    if (tripElement) {
        tripElement.textContent = metrics.tripped ? 'OUI' : 'NON';
        tripElement.className = metrics.tripped ? 'status-indicator tripped' : 'status-indicator open';
    }
    
    console.log('📊 Métriques mises à jour:', metrics);
}

// Mise à jour du statut
function updateStatus(status) {
    console.log('🔄 Statut mis à jour:', status);
    // Ici on peut ajouter d'autres mises à jour d'interface
}

// Ajout d'une entrée de blocage
function addBlockedEntry(entry) {
    console.log('🚫 Nouvelle entrée de blocage:', entry);
    
    const historyContainer = document.getElementById('blocking-log');
    if (!historyContainer) return;
    
    // Supprimer le message "Aucun blocage récent" s'il existe
    const noBlockings = historyContainer.querySelector('.no-blockings');
    if (noBlockings) {
        noBlockings.remove();
    }
    
    const entryElement = document.createElement('div');
    entryElement.className = 'blocked-entry';
    entryElement.innerHTML = `
        <div class="blocked-ip">${entry.ip}</div>
        <div class="blocked-time">${new Date().toLocaleTimeString()}</div>
        <div class="blocked-reason">${entry.reason || 'Dépassement de seuil'}</div>
    `;
    
    // Ajouter en haut de la liste
    historyContainer.insertBefore(entryElement, historyContainer.firstChild);
    
    // Limiter à 10 entrées
    while (historyContainer.children.length > 10) {
        historyContainer.removeChild(historyContainer.lastChild);
    }
}

// Ajout d'une entrée de récupération
function addRecoveredEntry(entry) {
    console.log('✅ Nouvelle entrée de récupération:', entry);
    
    const historyContainer = document.getElementById('blocking-log');
    if (!historyContainer) return;
    
    // Supprimer le message "Aucun blocage récent" s'il existe
    const noBlockings = historyContainer.querySelector('.no-blockings');
    if (noBlockings) {
        noBlockings.remove();
    }
    
    const entryElement = document.createElement('div');
    entryElement.className = 'blocked-entry recovered';
    entryElement.innerHTML = `
        <div class="blocked-ip">${entry.ip}</div>
        <div class="blocked-time">${new Date().toLocaleTimeString()}</div>
        <div class="blocked-reason">✅ Récupéré</div>
    `;
    
    // Ajouter en haut de la liste
    historyContainer.insertBefore(entryElement, historyContainer.firstChild);
    
    // Limiter à 10 entrées
    while (historyContainer.children.length > 10) {
        historyContainer.removeChild(historyContainer.lastChild);
    }
}

// Mise à jour du monitoring par IP
function updateIpMonitoring(ipData) {
    const container = document.getElementById('ip-monitoring');
    if (!container) return;
    
    // Supprimer le message "Aucune activité récente" s'il existe
    const noIps = container.querySelector('.no-ips');
    if (noIps) {
        noIps.remove();
    }
    
    // Vider le conteneur
    container.innerHTML = '';
    
    if (ipData.length === 0) {
        container.innerHTML = '<div class="no-ips">Aucune activité récente</div>';
        return;
    }
    
    // Créer les entrées pour chaque IP
    ipData.forEach(ip => {
        const entryElement = document.createElement('div');
        entryElement.className = 'ip-entry';
        entryElement.innerHTML = `
            <div class="leaf-container">
                <div class="leaf ${getLeafClass(ip)}">🍃</div>
            </div>
            <div class="ip-info">
                <div class="ip-address">${ip.ip}</div>
                <div class="ip-rps">RPS: ${ip.rps.toFixed(2)}</div>
                <div class="ip-status">
                    <span class="status-indicator ${ip.tripped ? 'tripped' : 'open'}"></span>
                    ${ip.tripped ? `Bloqué (${Math.round(ip.ttlMs/1000)}s)` : 'Normal'}
                </div>
            </div>
        `;
        
        container.appendChild(entryElement);
    });
}

// Déterminer la classe CSS de la feuille selon l'état de l'IP
function getLeafClass(ip) {
    if (ip.tripped) {
        return 'blocked';
    } else if (ip.rps > 5) {
        return 'danger';
    } else if (ip.rps > 2) {
        return 'warning';
    } else {
        return 'normal';
    }
}

// Mise à jour du statut de connexion
function updateConnectionStatus(connected) {
    const statusElement = document.getElementById('connection-status');
    if (statusElement) {
        statusElement.textContent = connected ? 'Connecté' : 'Déconnecté';
        statusElement.className = connected ? 'status-indicator open' : 'status-indicator tripped';
    }
}

// Fonction de rafraîchissement du dashboard
function refreshDashboard() {
    console.log('🔄 Rafraîchissement du dashboard...');
    
    if (isConnected && socket) {
        // Demander les métriques actuelles
        socket.emit('get_metrics');
    } else {
        console.log('⚠️ Socket.IO non connecté, reconnexion...');
        initWebSocket();
    }
    
    // Rafraîchir la page après 1 seconde si toujours pas connecté
    setTimeout(() => {
        if (!isConnected) {
            console.log('🔄 Rechargement de la page...');
            window.location.reload();
        }
    }, 1000);
}

// Test de requêtes normales
function testNormalRequests() {
    console.log('🧪 Test de requêtes normales...');
    
    const testUrls = [
        '/',
        '/api/state',
        '/ps5'
    ];
    
    testUrls.forEach((url, index) => {
        setTimeout(() => {
            fetch(url)
                .then(response => {
                    console.log(`✅ ${url}: ${response.status}`);
                })
                .catch(error => {
                    console.log(`❌ ${url}: ${error.message}`);
                });
        }, index * 100); // Espacer les requêtes de 100ms
    });
}

// Test de requêtes bruyantes (attaque)
function testNoisyRequests() {
    console.log('🧪 Test de requêtes bruyantes (simulation d\'attaque)...');
    
    const fakeIps = [
        '192.168.1.100',
        '192.168.1.101',
        '192.168.1.102',
        '192.168.1.103',
        '192.168.1.104'
    ];
    
    const testPaths = [
        '/',
        '/api/state',
        '/ps5',
        '/api/reserve-ps5',
        '/test1',
        '/test2',
        '/test3',
        '/test4',
        '/test5'
    ];
    
    // Envoyer 50 requêtes rapidement avec différentes IPs et chemins
    for (let i = 0; i < 50; i++) {
        setTimeout(() => {
            const ip = fakeIps[Math.floor(Math.random() * fakeIps.length)];
            const path = testPaths[Math.floor(Math.random() * testPaths.length)];
            
            fetch(path, {
                headers: {
                    'X-Fake-IP': ip
                }
            })
            .then(response => {
                console.log(`🎭 ${ip} -> ${path}: ${response.status}`);
            })
            .catch(error => {
                console.log(`❌ ${ip} -> ${path}: ${error.message}`);
            });
        }, i * 20); // 20ms entre chaque requête
    }
}

// Fonction pour effacer l'affichage du monitoring IP
function clearIpMonitoringDisplay() {
    console.log('🗑️ Effacement de l\'affichage du monitoring IP');
    
    const container = document.getElementById('ip-monitoring');
    if (!container) return;
    
    // Réinitialiser l'affichage
    container.innerHTML = '<div class="no-ips">Aucune activité récente</div>';
    
    // Afficher un message temporaire de confirmation
    const confirmation = document.createElement('div');
    confirmation.style.cssText = `
        position: fixed;
        top: 20px;
        right: 20px;
        background: var(--ok);
        color: white;
        padding: 12px 20px;
        border-radius: 8px;
        box-shadow: 0 4px 12px rgba(0,0,0,0.3);
        z-index: 1000;
        font-size: 14px;
        font-weight: 600;
    `;
    confirmation.textContent = '✅ Données IP effacées';
    
    document.body.appendChild(confirmation);
    
    // Supprimer le message après 3 secondes
    setTimeout(() => {
        if (confirmation.parentNode) {
            confirmation.parentNode.removeChild(confirmation);
        }
    }, 3000);
}

// Fonction pour effacer les données IP (appelée par le bouton)
function clearIpMonitoring() {
    console.log('🗑️ Demande d\'effacement des données IP');
    
    // Confirmation avant effacement
    if (!confirm('Êtes-vous sûr de vouloir effacer toutes les données IP ? Cette action est irréversible.')) {
        return;
    }
    
    // Appel à l'API pour effacer les données
    fetch('/api/clear-ips', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json'
        }
    })
    .then(response => response.json())
    .then(data => {
        console.log('✅ Réponse API:', data);
        // L'affichage sera mis à jour via l'événement WebSocket 'ips-cleared'
    })
    .catch(error => {
        console.error('❌ Erreur lors de l\'effacement:', error);
        
        // Afficher un message d'erreur
        const errorMsg = document.createElement('div');
        errorMsg.style.cssText = `
            position: fixed;
            top: 20px;
            right: 20px;
            background: var(--bad);
            color: white;
            padding: 12px 20px;
            border-radius: 8px;
            box-shadow: 0 4px 12px rgba(0,0,0,0.3);
            z-index: 1000;
            font-size: 14px;
            font-weight: 600;
        `;
        errorMsg.textContent = '❌ Erreur lors de l\'effacement';
        
        document.body.appendChild(errorMsg);
        
        setTimeout(() => {
            if (errorMsg.parentNode) {
                errorMsg.parentNode.removeChild(errorMsg);
            }
        }, 3000);
    });
}

// Fonction de debug
function debugTest() {
    console.log('🔧 === TEST DE DEBUG COMPLET ===');
    
    console.log('1️⃣ État de la connexion:');
    console.log('   - WebSocket:', socket ? 'Initialisé' : 'Non initialisé');
    console.log('   - Connecté:', isConnected);
    console.log('   - URL:', socket ? socket.url : 'N/A');
    
    console.log('2️⃣ Métriques actuelles:');
    console.log('   - RPS:', metrics.rps);
    console.log('   - Diversité:', metrics.diversity);
    console.log('   - Tripped:', metrics.tripped);
    console.log('   - TTL:', metrics.ttl);
    
    console.log('3️⃣ Éléments DOM:');
    const elements = ['th-rps', 'th-div', 'th-trip', 'connection-status', 'blocked-history'];
    elements.forEach(id => {
        const el = document.getElementById(id);
        console.log(`   - ${id}:`, el ? 'Trouvé' : 'Non trouvé');
    });
    
    console.log('4️⃣ Test de requête API:');
    fetch('/api/state')
        .then(response => response.json())
        .then(data => {
            console.log('   - API State:', data);
        })
        .catch(error => {
            console.log('   - Erreur API:', error.message);
        });
    
    console.log('🔧 === FIN TEST DE DEBUG ===');
}

// Initialisation au chargement de la page
document.addEventListener('DOMContentLoaded', function() {
    console.log('🌿 Mimosa API Dashboard - Initialisation...');
    
    // Initialiser la connexion WebSocket
    initWebSocket();
    
    // Mettre à jour le statut initial
    updateConnectionStatus(false);
    
    // Rafraîchir le dashboard toutes les 5 secondes
    setInterval(refreshDashboard, 5000);
    
    console.log('✅ Dashboard initialisé');
});

// Exposer les fonctions globalement
window.debugTest = debugTest;
window.refreshDashboard = refreshDashboard;
window.testNormalRequests = testNormalRequests;
window.testNoisyRequests = testNoisyRequests;
window.clearIpMonitoring = clearIpMonitoring;
