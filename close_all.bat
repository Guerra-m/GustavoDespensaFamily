@echo off
echo ==============================
echo Cerrando proyecto DespensaFamily
echo ==============================
echo.

REM Cerrar backend (Java)
echo Cerrando backend...
taskkill /IM java.exe /F

REM Cerrar frontend (Node / Vite)
echo Cerrando frontend...
taskkill /IM node.exe /F

echo.
echo Todos los procesos han sido finalizados.
pause