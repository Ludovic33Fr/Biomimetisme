@echo off
set JAVA_HOME=C:\Program Files\Microsoft\jdk-17.0.2.8-hotspot
set PATH=%JAVA_HOME%\bin;%PATH%

echo ========================================
echo Construction du JAR executable
echo ========================================

echo.
echo Compilation et packaging...
mvnw.cmd clean package -DskipTests

if %ERRORLEVEL% NEQ 0 (
    echo Erreur lors de la construction du JAR!
    pause
    exit /b 1
)

echo.
echo ========================================
echo JAR construit avec succes!
echo Fichier: target/EP-Catalog-0.0.1-SNAPSHOT.jar
echo ========================================
echo.
echo Pour tester le JAR:
echo   java -jar target/EP-Catalog-0.0.1-SNAPSHOT.jar
echo.
pause 