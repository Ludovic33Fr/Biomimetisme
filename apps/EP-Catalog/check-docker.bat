@echo off
echo ========================================
echo Verification de l'environnement Docker
echo ========================================

echo.
echo 1. Verification de Docker...
docker --version >nul 2>&1
if %ERRORLEVEL% NEQ 0 (
    echo ❌ Docker n'est pas installe ou n'est pas dans le PATH
    echo.
    echo Veuillez installer Docker Desktop:
    echo 1. Suivez le guide INSTALL-DOCKER.md
    echo 2. Ou telechargez depuis: https://www.docker.com/products/docker-desktop/
    echo.
    pause
    exit /b 1
) else (
    echo ✅ Docker est installe
    docker --version
)

echo.
echo 2. Verification de Docker Compose...
docker-compose --version >nul 2>&1
if %ERRORLEVEL% NEQ 0 (
    echo ❌ Docker Compose n'est pas disponible
    echo.
    echo Docker Compose devrait etre inclus avec Docker Desktop
    echo Redemarrez Docker Desktop et reessayez
    echo.
    pause
    exit /b 1
) else (
    echo ✅ Docker Compose est disponible
    docker-compose --version
)

echo.
echo 3. Verification que Docker Desktop fonctionne...
docker run --rm hello-world >nul 2>&1
if %ERRORLEVEL% NEQ 0 (
    echo ❌ Docker Desktop ne fonctionne pas
    echo.
    echo Veuillez:
    echo 1. Demarrer Docker Desktop
    echo 2. Attendre qu'il soit completement demarre
    echo 3. Reessayer
    echo.
    pause
    exit /b 1
) else (
    echo ✅ Docker Desktop fonctionne correctement
)

echo.
echo 4. Verification du JAR...
if not exist "target\EP-Catalog-0.0.1-SNAPSHOT.jar" (
    echo ❌ JAR non trouve
    echo.
    echo Construction du JAR...
    call .\build-jar.bat
    if %ERRORLEVEL% NEQ 0 (
        echo Erreur lors de la construction du JAR
        pause
        exit /b 1
    )
) else (
    echo ✅ JAR trouve: target\EP-Catalog-0.0.1-SNAPSHOT.jar
)

echo.
echo ========================================
echo ✅ Environnement Docker pret!
echo ========================================
echo.
echo Vous pouvez maintenant utiliser:
echo   .\docker-build.bat    - Construire et demarrer
echo   .\docker-stop.bat     - Arreter l'application
echo.
pause 