# Gestion des données de test

## Structure des données

Les données de test sont stockées dans le fichier `src/main/resources/data/products.json`.

### Format d'un produit

```json
{
  "id": "1",
  "name": "Nom du produit",
  "description": "Description détaillée",
  "price": 99.99,
  "category": "Catégorie",
  "brand": "Marque",
  "color": "Couleur",
  "size": "Taille"
}
```

## Ajout de nouveaux produits

### Méthode 1 : Script Python (Recommandé)

1. Naviguez vers le dossier `scripts/` :
   ```bash
   cd scripts
   ```

2. Exécutez le script interactif :
   ```bash
   python add-product.py add
   ```

3. Suivez les instructions pour saisir les informations du produit.

### Méthode 2 : Édition manuelle

1. Ouvrez le fichier `src/main/resources/data/products.json`
2. Ajoutez un nouvel objet produit à la fin du tableau
3. Assurez-vous que l'ID est unique
4. Sauvegardez le fichier

### Méthode 3 : Lister les produits existants

```bash
cd scripts
python add-product.py list
```

## Redémarrage de l'application

Après avoir modifié le fichier JSON, redémarrez l'application pour que les changements soient pris en compte :

```bash
.\run.bat
```

## API Endpoints

- `GET /api/products` - Liste tous les produits
- `GET /api/products/{id}` - Récupère un produit par son ID

## Exemple d'ajout de produit

```bash
cd scripts
python add-product.py add

# Suivez les instructions :
# Nom du produit: Nouveau Produit
# Description: Description du nouveau produit
# Prix: 45.99
# Catégorie: Accessoires
# Marque: MaMarque
# Couleur: Rouge
# Taille: M
```

## Validation des données

Le script vérifie automatiquement :
- L'unicité des IDs
- La validité du prix (nombre)
- La présence de tous les champs requis

## Sauvegarde

Le fichier JSON est automatiquement sauvegardé avec un formatage propre (indentation de 2 espaces). 