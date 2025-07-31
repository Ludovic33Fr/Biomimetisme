# Déploiement Docker - EP Catalog

## Prérequis

- Docker Desktop installé et démarré
- Docker Compose disponible

## Déploiement rapide

### Méthode 1 : Script automatique (Recommandé)

```bash
# Construction et démarrage automatique
.\docker-build.bat
```

### Méthode 2 : Commandes manuelles

```bash
# 1. Construire le JAR
.\build-jar.bat

# 2. Construire l'image Docker
docker build -t ep-catalog:latest .

# 3. Démarrer avec Docker Compose
docker-compose up -d
```

## Commandes utiles

### Gestion de l'application

```bash
# Démarrer l'application
docker-compose up -d

# Voir les logs
docker-compose logs -f

# Arrêter l'application
docker-compose down

# Redémarrer l'application
docker-compose restart
```

### Gestion des images

```bash
# Lister les images
docker images

# Supprimer l'image
docker rmi ep-catalog:latest

# Nettoyer les images non utilisées
docker image prune
```

### Scripts fournis

- `docker-build.bat` : Construction et démarrage automatique
- `docker-stop.bat` : Arrêt et nettoyage optionnel
- `build-jar.bat` : Construction du JAR exécutable

## Accès à l'application

Une fois démarrée, l'application est accessible sur :

- **URL principale** : http://localhost:8080
- **API produits** : http://localhost:8080/api/products
- **Produit spécifique** : http://localhost:8080/api/products/1

## Configuration

### Variables d'environnement

Vous pouvez modifier le fichier `docker-compose.yml` pour changer :

- **Port** : Modifier `"8080:8080"` pour changer le port externe
- **Variables Spring** : Ajouter des variables dans la section `environment`

### Exemple de personnalisation

```yaml
services:
  ep-catalog:
    build: .
    ports:
      - "9090:8080"  # Port externe 9090
    environment:
      - SPRING_PROFILES_ACTIVE=docker
      - SERVER_PORT=8080
```

## Dépannage

### Vérifier que l'application fonctionne

```bash
# Vérifier les conteneurs en cours
docker ps

# Voir les logs
docker-compose logs ep-catalog

# Tester l'API
curl http://localhost:8080/api/products
```

### Problèmes courants

1. **Port déjà utilisé** : Changez le port dans `docker-compose.yml`
2. **Erreur de build** : Vérifiez que le JAR a été construit avec `.\build-jar.bat`
3. **Application ne démarre pas** : Vérifiez les logs avec `docker-compose logs -f`

## Production

Pour un déploiement en production :

1. Utilisez des variables d'environnement pour la configuration
2. Configurez un reverse proxy (nginx, traefik)
3. Utilisez un registre Docker pour stocker l'image
4. Configurez la surveillance et les logs

### Exemple de déploiement avec registre

```bash
# Tag pour le registre
docker tag ep-catalog:latest your-registry.com/ep-catalog:latest

# Push vers le registre
docker push your-registry.com/ep-catalog:latest
``` 