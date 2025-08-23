# Biomimétisme et cybersécurité

### Compilation de la classe Steg

```bash
# Compiler la classe Steg
javac Steg.java

# Ou avec le wrapper Maven (si dans le projet EP-Catalog)
.\mvnw.cmd compile
```

## 🔐 Utilisation de la stéganographie

### Prérequis

- Java 8+ installé
- Images au format PNG (recommandé pour éviter la perte de données)

### Exemples pratiques

#### 1. Cacher un message dans une image

```bash
# Encoder un message secret
java Steg encode photo_originale.png photo_secrete.png "Ceci est un message secret!"

# Vérifier que l'image a été créée
ls -la photo_secrete.png
```

#### 2. Extraire un message caché

```bash
# Décoder le message
java Steg decode photo_secrete.png
# Sortie: Ceci est un message secret!
```

#### 3. Vérifier la capacité d'une image

```bash
# Pour une image 1920x1080
# Capacité = 1920 × 1080 × 3 bits = 6 220 800 bits = 777 600 bytes
# Soit environ 777 KB de données cachables
```

### Bonnes pratiques

- **Utilisez des images PNG** pour éviter la compression avec perte
- **Testez sur des copies** avant d'utiliser des images originales
- **Vérifiez la capacité** de l'image avant l'encodage
- **Conservez les images originales** pour comparaison
- **Utilisez des messages courts** pour une meilleure discrétion

### Dépannage

#### Erreur "Message trop long"
- L'image est trop petite pour le message
- Utilisez une image de plus grande résolution
- Ou réduisez la taille du message

#### Erreur "Signature stéganographique absente"
- L'image ne contient pas de message caché
- Vérifiez que l'image a bien été encodée
- Assurez-vous d'utiliser la bonne image

#### Erreur "Impossible de lire l'image"
- Vérifiez le chemin du fichier
- Assurez-vous que le format est supporté
- Vérifiez les permissions de lecture


## Steganographie

### Usage

1) Compiler
javac Steg.java

2) Encoder un message (ex : avis de copyright)
java Steg encode StegaInput.png StegaOutput.png "© 2025 Ludovic Lefebvre — Tous droits réservés"

3) Décoder
java Steg decode StegaOutput.png