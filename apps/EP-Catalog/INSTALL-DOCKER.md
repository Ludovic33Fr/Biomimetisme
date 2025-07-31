# Installation de Docker pour Windows

## Prérequis système

- Windows 10/11 Pro, Enterprise ou Education (64-bit)
- WSL 2 (Windows Subsystem for Linux 2)
- Virtualisation activée dans le BIOS

## Étapes d'installation

### 1. Vérifier la virtualisation

Ouvrez le Gestionnaire des tâches (Ctrl+Shift+Esc) et vérifiez que la virtualisation est activée dans l'onglet Performance > CPU.

### 2. Installer WSL 2

Ouvrez PowerShell en tant qu'administrateur et exécutez :

```powershell
wsl --install
```

Redémarrez votre ordinateur.

### 3. Installer Docker Desktop

1. Téléchargez Docker Desktop depuis : https://www.docker.com/products/docker-desktop/
2. Exécutez l'installateur
3. Suivez les instructions d'installation
4. Redémarrez votre ordinateur

### 4. Vérifier l'installation

Ouvrez PowerShell et exécutez :

```powershell
docker --version
docker-compose --version
```

### 5. Démarrer Docker Desktop

1. Lancez Docker Desktop depuis le menu Démarrer
2. Attendez que Docker démarre (icône dans la barre des tâches)
3. Vérifiez que Docker fonctionne :

```powershell
docker run hello-world
```

## Configuration recommandée

### Activer l'intégration WSL 2

1. Ouvrez Docker Desktop
2. Allez dans Settings > General
3. Cochez "Use WSL 2 based engine"
4. Appliquez et redémarrez

### Optimiser les ressources

Dans Docker Desktop > Settings > Resources :
- **Memory** : 4 GB minimum (recommandé 8 GB)
- **CPU** : 2 cores minimum
- **Disk image size** : 64 GB minimum

## Dépannage

### Problème : "Docker Desktop is starting..."

1. Vérifiez que WSL 2 est installé : `wsl --list --verbose`
2. Redémarrez Docker Desktop
3. Vérifiez les logs dans Docker Desktop > Troubleshoot

### Problème : "WSL 2 installation is incomplete"

```powershell
# Mettre à jour le kernel Linux
wsl --update
```

### Problème : Virtualisation désactivée

1. Redémarrez en mode BIOS/UEFI
2. Activez la virtualisation (Intel VT-x / AMD-V)
3. Sauvegardez et redémarrez

## Vérification finale

Une fois Docker installé, testez avec :

```powershell
# Vérifier Docker
docker --version

# Vérifier Docker Compose
docker-compose --version

# Tester avec une image simple
docker run hello-world
```

## Utilisation avec EP Catalog

Après l'installation, vous pourrez utiliser :

```powershell
# Construction et démarrage
.\docker-build.bat

# Arrêt
.\docker-stop.bat
```

## Ressources utiles

- [Documentation officielle Docker](https://docs.docker.com/desktop/windows/)
- [Guide WSL 2](https://docs.microsoft.com/en-us/windows/wsl/install)
- [Dépannage Docker Desktop](https://docs.docker.com/desktop/troubleshoot/) 