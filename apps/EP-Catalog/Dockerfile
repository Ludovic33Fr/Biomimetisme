# Utiliser l'image OpenJDK 17 officielle
FROM openjdk:17-jdk-slim

# Définir le répertoire de travail
WORKDIR /app

# Copier le fichier JAR de l'application
COPY target/*.jar app.jar

# Exposer le port 8080
EXPOSE 8080

# Commande pour démarrer l'application
ENTRYPOINT ["java", "-jar", "/app/app.jar"] 