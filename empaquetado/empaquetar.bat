@echo off
setlocal enabledelayedexpansion
chcp 65001 >nul

REM ============================================================
REM  Empaqueta DespensaFamily en un programa de escritorio de Windows.
REM  Esto corre UNA vez por version en la maquina de desarrollo. La PC
REM  del negocio no ve nada de esto (ni npm, ni Maven, ni Java).
REM
REM  Que se arma, en orden:
REM    front  -> compilado adentro del jar
REM    jar    -> DespensaFamily.jar
REM    motor  -> build\DespensaFamilyServidor (jpackage: jar + Java adentro)
REM    app    -> dist\win-unpacked (Electron: la ventana, con el motor adentro)
REM    setup  -> DespensaFamily-<version>-setup.exe (Inno Setup)
REM
REM  Salida:  empaquetado\dist\win-unpacked\DespensaFamily.exe
REM           empaquetado\DespensaFamily-<version>-setup.exe  (si hay Inno Setup)
REM ============================================================

cd /d "%~dp0\.."
set "RAIZ=%CD%"

echo.
echo [1/6] Buscando el JDK y Node...
set "JDK="
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\jpackage.exe" set "JDK=%JAVA_HOME%"
if not defined JDK (
    for /d %%D in ("%USERPROFILE%\.jdks\*") do (
        if not defined JDK if exist "%%D\bin\jpackage.exe" set "JDK=%%D"
    )
)
if not defined JDK (
    echo   ERROR: no se encontro un JDK con jpackage ^(hace falta JDK 17 o superior^).
    echo   Instalalo y volve a intentar.
    pause
    exit /b 1
)
echo   JDK: !JDK!

where npm >nul 2>&1 || (
    echo   ERROR: no se encontro npm. La ventana de escritorio se arma con Electron.
    echo   Instala Node.js y volve a intentar:  winget install OpenJS.NodeJS.LTS
    pause
    exit /b 1
)
for /f "delims=" %%V in ('node -v') do echo   Node: %%V

echo.
echo [2/6] Compilando el frontend...
pushd "%RAIZ%\FrontDespensaFamily"
call npm install || (echo   ERROR compilando el frontend & popd & pause & exit /b 1)
call npm run build || (echo   ERROR compilando el frontend & popd & pause & exit /b 1)
popd

echo.
echo [3/6] Compilando el backend ^(incluye el frontend ya compilado^)...
set "JAVA_HOME=!JDK!"
call "%RAIZ%\DespensaFamily\mvnw.cmd" -f "%RAIZ%\DespensaFamily" clean package -DskipTests || (
    echo   ERROR compilando el backend
    pause
    exit /b 1
)

echo.
REM La version sale del pom: es el unico lugar donde se cambia.
set "VERSION="
for /f "tokens=3 delims=<>" %%V in ('findstr /r /c:"^	<version>" "%RAIZ%\DespensaFamily\pom.xml"') do (
    if not defined VERSION set "VERSION=%%V"
)
if not defined VERSION set "VERSION=1.0.0"

echo [4/6] Armando el motor con jpackage ^(el servidor con su propio Java^)...
if exist "%RAIZ%\empaquetado\entrada" rmdir /s /q "%RAIZ%\empaquetado\entrada"
if exist "%RAIZ%\empaquetado\build" rmdir /s /q "%RAIZ%\empaquetado\build"
if exist "%RAIZ%\empaquetado\dist" rmdir /s /q "%RAIZ%\empaquetado\dist"
mkdir "%RAIZ%\empaquetado\entrada"
copy /y "%RAIZ%\DespensaFamily\target\DespensaFamily.jar" "%RAIZ%\empaquetado\entrada\" >nul

REM Se llama DespensaFamilyServidor y no DespensaFamily porque ya no es el
REM programa que abre el usuario: es el motor que arranca la ventana. El nombre
REM tiene que coincidir con rutaDelServidor() en escritorio\main.js.
REM
REM java.se trae todos los modulos de la plataforma: Spring resuelve mucho por
REM reflexion y recortar a mano termina en fallos raros en tiempo de ejecucion.
REM jdk.unsupported lo piden varias librerias, jdk.crypto.ec es para TLS.
"!JDK!\bin\jpackage.exe" ^
    --type app-image ^
    --name DespensaFamilyServidor ^
    --app-version !VERSION! ^
    --vendor "Martin Guerra" ^
    --description "Motor de DespensaFamily" ^
    --input "%RAIZ%\empaquetado\entrada" ^
    --main-jar DespensaFamily.jar ^
    --main-class org.springframework.boot.loader.launch.JarLauncher ^
    --icon "%RAIZ%\empaquetado\DespensaFamily.ico" ^
    --dest "%RAIZ%\empaquetado\build" ^
    --java-options "-Dfile.encoding=UTF-8" ^
    --add-modules java.se,jdk.unsupported,jdk.crypto.ec || (
    echo   ERROR en jpackage
    pause
    exit /b 1
)

echo.
echo [5/6] Armando la ventana de escritorio con Electron...
pushd "%RAIZ%\empaquetado\escritorio"
call npm install || (echo   ERROR instalando Electron & popd & pause & exit /b 1)
call npm run empaquetar || (echo   ERROR armando la ventana & popd & pause & exit /b 1)
popd

if not exist "%RAIZ%\empaquetado\dist\win-unpacked\DespensaFamily.exe" (
    echo   ERROR: Electron no dejo el ejecutable donde se esperaba.
    pause
    exit /b 1
)

echo.
echo [6/6] Armando el instalador con Inno Setup...
REM winget lo instala por usuario en LOCALAPPDATA, no en Program Files.
set "ISCC="
if exist "%LOCALAPPDATA%\Programs\Inno Setup 6\ISCC.exe" set "ISCC=%LOCALAPPDATA%\Programs\Inno Setup 6\ISCC.exe"
if not defined ISCC if exist "%ProgramFiles(x86)%\Inno Setup 6\ISCC.exe" set "ISCC=%ProgramFiles(x86)%\Inno Setup 6\ISCC.exe"
if not defined ISCC if exist "%ProgramFiles%\Inno Setup 6\ISCC.exe" set "ISCC=%ProgramFiles%\Inno Setup 6\ISCC.exe"

if not defined ISCC (
    echo   Inno Setup no esta instalado, salteo el instalador.
    echo   Para instalarlo:  winget install JRSoftware.InnoSetup
    echo.
    echo   Igual quedo listo el programa suelto en:
    echo   %RAIZ%\empaquetado\dist\win-unpacked\DespensaFamily.exe
) else (
    pushd "%RAIZ%\empaquetado"
    "!ISCC!" /DVersion=!VERSION! DespensaFamily.iss || (echo   ERROR en Inno Setup & popd & pause & exit /b 1)
    popd
    echo.
    echo   Instalador listo:
    echo   %RAIZ%\empaquetado\DespensaFamily-!VERSION!-setup.exe
)

echo.
echo Listo.
pause
