#!/usr/bin/env node

/**
 * Script pour incrémenter automatiquement la version de Myco-IOC
 * Usage: node version-bump.js [patch|minor|major]
 */

const fs = require('fs');
const path = require('path');

const VERSION_FILE = path.join(__dirname, 'controller', 'src', 'index.ts');
const README_FILE = path.join(__dirname, 'README.md');

function getCurrentVersion() {
    const content = fs.readFileSync(VERSION_FILE, 'utf8');
    const match = content.match(/const SYSTEM_VERSION = "([^"]+)"/);
    return match ? match[1] : '1.0.0';
}

function updateVersion(newVersion) {
    // Mettre à jour le fichier controller
    let content = fs.readFileSync(VERSION_FILE, 'utf8');
    content = content.replace(
        /const SYSTEM_VERSION = "[^"]+"/,
        `const SYSTEM_VERSION = "${newVersion}"`
    );
    fs.writeFileSync(VERSION_FILE, content);
    
    // Mettre à jour le README si nécessaire
    let readmeContent = fs.readFileSync(README_FILE, 'utf8');
    if (readmeContent.includes('Version:')) {
        readmeContent = readmeContent.replace(
            /Version: [^\n]+/,
            `Version: ${newVersion}`
        );
        fs.writeFileSync(README_FILE, readmeContent);
    }
    
    console.log(`✅ Version mise à jour: ${newVersion}`);
    console.log(`📝 Fichiers modifiés: ${VERSION_FILE}`);
}

function incrementVersion(currentVersion, type = 'patch') {
    const [major, minor, patch] = currentVersion.split('.').map(Number);
    
    switch (type) {
        case 'major':
            return `${major + 1}.0.0`;
        case 'minor':
            return `${major}.${minor + 1}.0`;
        case 'patch':
        default:
            return `${major}.${minor}.${patch + 1}`;
    }
}

function main() {
    const type = process.argv[2] || 'patch';
    const currentVersion = getCurrentVersion();
    const newVersion = incrementVersion(currentVersion, type);
    
    console.log(`🔄 Incrémentation ${type}: ${currentVersion} → ${newVersion}`);
    updateVersion(newVersion);
    
    console.log('\n📋 Prochaines étapes:');
    console.log('1. docker compose down');
    console.log('2. docker compose up -d --build');
    console.log('3. Vérifier la version sur http://localhost:3000');
}

if (require.main === module) {
    main();
}

module.exports = { getCurrentVersion, updateVersion, incrementVersion };
