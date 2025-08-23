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

## 🔐 Stéganographie (Classe Steg)

Le projet inclut également une classe `Steg` pour la stéganographie, permettant de cacher des messages secrets dans des images.

### Principe de fonctionnement

La stéganographie consiste à cacher des informations dans les bits de poids faible des composantes RGB d'une image. Cette technique est invisible à l'œil nu car elle modifie seulement le bit le moins significatif de chaque canal de couleur.

### Structure des données cachées

```
[Signature "STEG1" (5 bytes)] + [Longueur du message (4 bytes)] + [Message (N bytes)]
```

- **Signature** : "STEG1" pour identifier les images contenant des messages cachés
- **Longueur** : Nombre de bytes du message (entier 32 bits)
- **Message** : Contenu UTF-8 à cacher

### Capacité de stockage

- **Capacité** ≈ largeur × hauteur × 3 bits
- **Exemple** : Image 1000×1000 pixels = 3 000 000 bits = 375 000 bytes

### Utilisation

#### Encoder un message

```bash
java Steg encode <image_entree.png> <image_sortie.png> <message...>
```

**Exemple :**
```bash
java Steg encode photo.png secret.png "Message secret à cacher"
```

#### Décoder un message

```bash
java Steg decode <image.png>
```

**Exemple :**
```bash
java Steg decode secret.png
```

### Algorithme d'encodage

1. **Préparation** : Création du payload avec signature, longueur et message
2. **Vérification** : Contrôle de la capacité de l'image
3. **Insertion** : Remplacement du bit de poids faible de chaque composante RGB
4. **Sauvegarde** : Écriture de l'image modifiée en PNG

### Algorithme de décodage

1. **Extraction** : Lecture des bits de poids faible de chaque composante RGB
2. **Validation** : Vérification de la signature "STEG1"
3. **Reconstruction** : Assemblage des bytes pour former le message
4. **Retour** : Affichage du message extrait

### Caractéristiques techniques

- **Format d'image** : PNG recommandé (sans perte)
- **Encodage** : UTF-8 pour le message
- **Sécurité** : Modifications invisibles à l'œil nu
- **Robustesse** : Signature pour identifier les images encodées
- **Performance** : Traitement pixel par pixel en O(n²)

### Cas d'usage

- **Communication secrète** : Cacher des messages dans des images partagées
- **Marquage numérique** : Identifier des images avec des métadonnées cachées
- **Protection de propriété** : Filigrane invisible dans des images
- **Recherche et développement** : Tests de sécurité et cryptographie

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