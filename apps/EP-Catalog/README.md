# EP Catalog - API de Gestion de Produits

Application Spring Boot REST API pour la gestion d'un catalogue de produits avec support Docker.

## 🚀 Démarrage rapide

### Prérequis
- Java 17
- Maven (inclus avec le wrapper)
- Docker Desktop (pour le déploiement Docker)

### Développement local

```bash
# Compiler l'application
.\compile.bat

# Démarrer l'application
.\run.bat
```

### Déploiement Docker

```bash
# Vérifier l'environnement Docker
.\check-docker.bat

# Construire et démarrer avec Docker
.\docker-build.bat

# Arrêter l'application Docker
.\docker-stop.bat
```

## 📋 API Endpoints

- `GET /api/products` - Liste tous les produits
- `GET /api/products/{id}` - Récupère un produit par son ID

### Exemples d'utilisation

```bash
# Lister tous les produits
curl http://localhost:8080/api/products

# Récupérer un produit spécifique
curl http://localhost:8080/api/products/1
```

## 📁 Structure du projet

```
EP-Catalog/
├── src/main/java/com/bioinfo/EP_Catalog/
│   ├── controller/          # Contrôleurs REST
│   ├── model/              # Modèles de données
│   └── service/            # Services métier
├── src/main/resources/
│   └── data/               # Données de test JSON
├── scripts/                # Scripts de gestion des données
├── Dockerfile              # Configuration Docker
├── docker-compose.yml      # Orchestration Docker
└── *.bat                   # Scripts Windows
```

## 🛠️ Gestion des données

### Ajouter un nouveau produit

```bash
cd scripts
python add-product.py add
```

### Lister les produits existants

```bash
cd scripts
python add-product.py list
```

### Édition manuelle

Modifiez directement le fichier `src/main/resources/data/products.json`

## 🐳 Docker

### Installation

Suivez le guide `INSTALL-DOCKER.md` pour installer Docker Desktop.

### Utilisation

```bash
# Vérifier l'environnement
.\check-docker.bat

# Déployer
.\docker-build.bat

# Gérer l'application
docker-compose up -d          # Démarrer
docker-compose down           # Arrêter
docker-compose logs -f        # Voir les logs
```

## 📚 Documentation

- `README-DATA.md` - Gestion des données de test
- `README-DOCKER.md` - Déploiement Docker détaillé
- `INSTALL-DOCKER.md` - Guide d'installation Docker

## 🔧 Configuration

### Variables d'environnement

- `SPRING_PROFILES_ACTIVE` - Profil Spring (défaut: docker)
- `SERVER_PORT` - Port de l'application (défaut: 8080)

### Ports

- **Développement** : http://localhost:8080
- **Docker** : http://localhost:8080 (configurable dans docker-compose.yml)

## 🚨 Dépannage

### Problèmes courants

1. **Erreur Java 17** : Vérifiez que Java 17 est installé
2. **Port déjà utilisé** : Changez le port dans `docker-compose.yml`
3. **Docker non disponible** : Suivez `INSTALL-DOCKER.md`

### Logs

```bash
# Développement
.\run.bat

# Docker
docker-compose logs -f
```

## 📦 Build

### JAR exécutable

```bash
.\build-jar.bat
```

Le JAR sera créé dans `target/EP-Catalog-0.0.1-SNAPSHOT.jar`

### Image Docker

```bash
docker build -t ep-catalog:latest .
```

## 🤝 Contribution

1. Fork le projet
2. Créez une branche feature
3. Committez vos changements
4. Poussez vers la branche
5. Ouvrez une Pull Request

## 📄 Licence

Ce projet est sous licence MIT. 