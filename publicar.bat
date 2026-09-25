@echo off
setlocal enabledelayedexpansion

REM ===========================================================================
REM Publica una version nueva de DespensaFamily.
REM
REM Lo que se publica es SOLO el jar. Vite compila el frontend adentro del jar,
REM asi que un jar nuevo es la aplicacion entera nueva: backend y pantallas. Son
REM 66 MB contra los 188 MB del instalador, y la PC del negocio lo aplica sola,
REM sin asistente de instalacion, sin UAC y sin el cartel de SmartScreen.
REM
REM El setup.exe sigue haciendo falta SOLO cuando cambia Electron o el motor de
REM Java, y para instalar por primera vez. Eso se arma con empaquetado\empaquetar.bat.
REM
REM Como se usa:
REM     1. Subir la version en DespensaFamily\pom.xml  (ej: 1.1.0 -> 1.2.0)
REM     2. publicar.bat "Que trae esta version"
REM
REM Deja en empaquetado\publicacion\ los dos archivos que van a la release:
REM     DespensaFamily.jar          la aplicacion
REM     DespensaFamily.jar.sha256   su hash, que el launcher verifica antes de
REM                                 instalar nada
REM ===========================================================================

set "RAIZ=%~dp0"
set "SALIDA=%RAIZ%empaquetado\publicacion"
set "NOTAS=%~1"

if "%NOTAS%"=="" set "NOTAS=Version nueva de DespensaFamily"

echo ==============================
echo Publicando DespensaFamily
echo ==============================
echo.

REM --- Version, leida del pom para que haya un solo lugar donde cambiarla ----
set "VERSION="
for /f "tokens=3 delims=<>" %%V in ('findstr /r /c:"^	<version>" "%RAIZ%DespensaFamily\pom.xml"') do (
    if not defined VERSION set "VERSION=%%V"
)
if not defined VERSION (
    echo No se pudo leer la version de DespensaFamily\pom.xml
    pause
    exit /b 1
)
echo Version a publicar: %VERSION%
echo.

REM --- Tests: no se publica algo que no pasa la suite -----------------------
echo Corriendo los tests...
REM Los tests van contra una carpeta de datos descartable. Sin esto usaban la
REM de la instalacion real (%LOCALAPPDATA%\DespensaFamily) y pisaban su
REM credenciales-iniciales.txt (DF-TEST-001). Queda acotado por el setlocal.
set "DESPENSA_HOME=%TEMP%\DespensaFamily-tests"
call "%RAIZ%DespensaFamily\mvnw.cmd" -f "%RAIZ%DespensaFamily" test
if errorlevel 1 (
    echo.
    echo Los tests fallaron. No se publica nada.
    pause
    exit /b 1
)

REM --- Compilar frontend + jar ---------------------------------------------
echo.
echo Compilando frontend...
pushd "%RAIZ%FrontDespensaFamily"
call npm install
call npm run build
popd
if errorlevel 1 (
    echo Fallo la compilacion del frontend.
    pause
    exit /b 1
)

echo.
echo Compilando el jar...
call "%RAIZ%DespensaFamily\mvnw.cmd" -f "%RAIZ%DespensaFamily" clean package -DskipTests
if errorlevel 1 (
    echo Fallo la compilacion del backend.
    pause
    exit /b 1
)

REM --- Hash -----------------------------------------------------------------
REM certutil viene con Windows: no agrega ninguna herramienta que instalar.
if not exist "%SALIDA%" mkdir "%SALIDA%"
copy /y "%RAIZ%DespensaFamily\target\DespensaFamily.jar" "%SALIDA%\" >nul

set "HASH="
for /f "skip=1 tokens=*" %%H in ('certutil -hashfile "%SALIDA%\DespensaFamily.jar" SHA256') do (
    if not defined HASH set "HASH=%%H"
)
set "HASH=%HASH: =%"
echo %HASH%>"%SALIDA%\DespensaFamily.jar.sha256"

echo.
echo Jar:    %SALIDA%\DespensaFamily.jar
echo SHA256: %HASH%
echo.

REM --- Publicar -------------------------------------------------------------
where gh >nul 2>nul
if errorlevel 1 goto :aMano

echo Publicando la release v%VERSION% en GitHub...
gh release create "v%VERSION%" ^
    "%SALIDA%\DespensaFamily.jar" ^
    "%SALIDA%\DespensaFamily.jar.sha256" ^
    --title "DespensaFamily %VERSION%" ^
    --notes "%NOTAS%"
if errorlevel 1 (
    echo.
    echo No se pudo publicar con gh. Los archivos quedaron listos para subirlos a mano.
    goto :aMano
)

echo.
echo Listo. Las PC con DespensaFamily instalado la van a ver en las proximas horas.
pause
exit /b 0

:aMano
echo ---------------------------------------------------------------
echo Para publicarla a mano:
echo.
echo   1. Entrar a https://github.com/Guerra-m/GustavoDespensaFamily/releases/new
echo   2. Tag:   v%VERSION%          (con la v adelante)
echo   3. Subir LOS DOS archivos de %SALIDA%
echo   4. En la descripcion, que trae la version (es lo que se ve en el aviso)
echo.
echo Si falta alguno de los dos archivos, el launcher ignora la release entera:
echo sin el hash no puede verificar la descarga, y sin verificarla no la instala.
echo.
echo Para que publique solo la proxima vez:  winget install GitHub.cli
echo ---------------------------------------------------------------
pause
exit /b 0
