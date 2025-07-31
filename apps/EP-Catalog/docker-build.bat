@echo off
echo ========================================
echo Construction et execution Docker
echo ========================================

echo.
echo 1. Compilation de l'application...
call .\compile.bat
if %ERRORLEVEL% NEQ 0 (
    echo Erreur lors de la compilation!
    pause
    exit /b 1
)

echo.
echo 2. Construction de l'image Docker...
docker build -t ep-catalog:latest .
if %ERRORLEVEL% NEQ 0 (
    echo Erreur lors de la construction Docker!
    pause
    exit /b 1
)

echo.
echo 3. Demarrage avec Docker Compose...
docker-compose up -d

echo.
echo ========================================
echo Application demarree!
echo URL: http://localhost:8080
echo API: http://localhost:8080/api/products
echo ========================================
echo.
echo Commandes utiles:
echo   docker-compose logs -f    - Voir les logs
echo   docker-compose down       - Arreter l'application
echo   docker-compose restart    - Redemarrer l'application
echo.
pause 