@echo off
echo ========================================
echo Arret de l'application Docker
echo ========================================

echo.
echo Arret des conteneurs...
docker-compose down

echo.
echo Nettoyage des images (optionnel)...
echo Voulez-vous supprimer l'image Docker? (y/N)
set /p choice=
if /i "%choice%"=="y" (
    docker rmi ep-catalog:latest
    echo Image supprimee.
) else (
    echo Image conservee.
)

echo.
echo ========================================
echo Application arretee!
echo ========================================
pause 