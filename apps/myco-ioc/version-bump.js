#!/usr/bin/env node

/**
 * Bump la version Myco-IOC dans les package.json des 3 services.
 * La version est lue depuis controller/package.json (source de vérité).
 * Usage : node version-bump.js [patch|minor|major]
 */

const fs = require('fs');
const path = require('path');

const SERVICES = ['controller', 'node', 'traffic'];
const PACKAGE_FILES = SERVICES.map(s => path.join(__dirname, s, 'package.json'));

function readPackage(file) {
    return JSON.parse(fs.readFileSync(file, 'utf8'));
}

function writePackage(file, pkg) {
    fs.writeFileSync(file, JSON.stringify(pkg, null, 2) + '\n');
}

function getCurrentVersion() {
    return readPackage(PACKAGE_FILES[0]).version;
}

function incrementVersion(currentVersion, type = 'patch') {
    const [major, minor, patch] = currentVersion.split('.').map(Number);
    switch (type) {
        case 'major': return `${major + 1}.0.0`;
        case 'minor': return `${major}.${minor + 1}.0`;
        case 'patch':
        default:      return `${major}.${minor}.${patch + 1}`;
    }
}

function updateAll(newVersion) {
    for (const file of PACKAGE_FILES) {
        const pkg = readPackage(file);
        pkg.version = newVersion;
        writePackage(file, pkg);
        console.log(`  ✔ ${path.relative(__dirname, file)} → ${newVersion}`);
    }
}

function main() {
    const type = process.argv[2] || 'patch';
    const currentVersion = getCurrentVersion();
    const newVersion = incrementVersion(currentVersion, type);

    console.log(`🔄 Bump ${type} : ${currentVersion} → ${newVersion}`);
    updateAll(newVersion);

    console.log('\n📋 Prochaines étapes :');
    console.log('  1. docker compose down');
    console.log('  2. docker compose up -d --build');
    console.log('  3. Vérifier la version sur http://localhost:3000');
}

if (require.main === module) main();

module.exports = { getCurrentVersion, incrementVersion, updateAll };
