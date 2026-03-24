@echo off
echo ==============================
echo Iniciando solo el backend de DespensaFamily
echo ==============================
echo.

REM Ejecutar backend minimizado
start "" /min "C:\Program Files\Java\jdk-17\bin\java.exe" -jar "DespensaFamily\target\DespensaFamily-0.0.1-SNAPSHOT.jar"

echo Backend iniciado. Espera unos segundos antes de usar la app.
pause