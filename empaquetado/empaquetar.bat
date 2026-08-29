@echo off
setlocal enabledelayedexpansion
chcp 65001 >nul

REM ============================================================
REM  Empaqueta DespensaFamily en un ejecutable de Windows.
REM  Reemplaza al viejo build.bat: esto corre UNA vez por version
REM  en la maquina de desarrollo. La PC del negocio no ve nada de
REM  esto (ni npm, ni Maven, ni Java).
REM
REM  Salida:  empaquetado\dist\DespensaFamily\DespensaFamily.exe
REM           empaquetado\DespensaFamily-1.0.0-setup.exe  (si hay Inno Setup)
REM ============================================================

cd /d "%~dp0\.."
set "RAIZ=%CD%"

echo.
echo [1/5] Buscando el JDK...
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
echo   Usando: !JDK!

echo.
echo [2/5] Compilando el frontend...
pushd "%RAIZ%\FrontDespensaFamily"
call npm install || (echo   ERROR compilando el frontend & popd & pause & exit /b 1)
call npm run build || (echo   ERROR compilando el frontend & popd & pause & exit /b 1)
popd

echo.
echo [3/5] Compilando el backend ^(incluye el frontend ya compilado^)...
set "JAVA_HOME=!JDK!"
call "%RAIZ%\DespensaFamily\mvnw.cmd" -f "%RAIZ%\DespensaFamily" clean package -DskipTests || (
    echo   ERROR compilando el backend
    pause
    exit /b 1
)

echo.
echo [4/5] Armando el ejecutable con jpackage...
if exist "%RAIZ%\empaquetado\entrada" rmdir /s /q "%RAIZ%\empaquetado\entrada"
if exist "%RAIZ%\empaquetado\dist" rmdir /s /q "%RAIZ%\empaquetado\dist"
mkdir "%RAIZ%\empaquetado\entrada"
copy /y "%RAIZ%\DespensaFamily\target\DespensaFamily-0.0.1-SNAPSHOT.jar" "%RAIZ%\empaquetado\entrada\" >nul

REM java.se trae todos los modulos de la plataforma: Spring resuelve mucho por
REM reflexion y recortar a mano termina en fallos raros en tiempo de ejecucion.
REM jdk.unsupported lo piden varias librerias, jdk.crypto.ec es para TLS.
"!JDK!\bin\jpackage.exe" ^
    --type app-image ^
    --name DespensaFamily ^
    --app-version 1.0.0 ^
    --vendor "Martin Guerra" ^
    --description "Gestion de stock y ventas" ^
    --input "%RAIZ%\empaquetado\entrada" ^
    --main-jar DespensaFamily-0.0.1-SNAPSHOT.jar ^
    --main-class org.springframework.boot.loader.launch.JarLauncher ^
    --icon "%RAIZ%\empaquetado\DespensaFamily.ico" ^
    --dest "%RAIZ%\empaquetado\dist" ^
    --java-options "-Dfile.encoding=UTF-8" ^
    --add-modules java.se,jdk.unsupported,jdk.crypto.ec || (
    echo   ERROR en jpackage
    pause
    exit /b 1
)

echo.
echo [5/5] Armando el instalador con Inno Setup...
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
    echo   %RAIZ%\empaquetado\dist\DespensaFamily\DespensaFamily.exe
) else (
    pushd "%RAIZ%\empaquetado"
    "!ISCC!" DespensaFamily.iss || (echo   ERROR en Inno Setup & popd & pause & exit /b 1)
    popd
    echo.
    echo   Instalador listo:
    echo   %RAIZ%\empaquetado\DespensaFamily-1.0.0-setup.exe
)

echo.
echo Listo.
pause
