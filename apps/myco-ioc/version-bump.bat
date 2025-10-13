@echo off
REM Script Windows pour incrémenter la version de Myco-IOC
REM Usage: version-bump.bat [patch|minor|major]

if "%1"=="" (
    set TYPE=patch
) else (
    set TYPE=%1
)

echo 🔄 Incrémentation de version Myco-IOC...
node version-bump.js %TYPE%

if %ERRORLEVEL% EQU 0 (
    echo.
    echo ✅ Version mise à jour avec succès!
    echo.
    echo 📋 Prochaines étapes:
    echo 1. docker compose down
    echo 2. docker compose up -d --build
    echo 3. Vérifier la version sur http://localhost:3000
) else (
    echo ❌ Erreur lors de la mise à jour de version
    exit /b 1
)
