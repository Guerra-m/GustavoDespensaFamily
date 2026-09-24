; Instalador de DespensaFamily para Windows.
;
; Toma la carpeta que genera Electron (dist\win-unpacked) y arma un setup.exe
; con asistente en castellano, accesos directos y desinstalador. Adentro de esa
; carpeta viaja tambien el motor Java (resources\servidor), que arma jpackage.
;
; Se compila con Inno Setup 6:  iscc DespensaFamily.iss
; Si no lo tenes:               winget install JRSoftware.InnoSetup

#define Nombre        "DespensaFamily"
#define Version       "1.1.0"
#define Autor         "Martin Guerra"
#define Ejecutable    "DespensaFamily.exe"

[Setup]
; No cambiar el AppId entre versiones: es lo que hace que la 1.1 se instale
; ENCIMA de la 1.0 en vez de aparecer como un segundo programa.
AppId={{8F3A1C42-5D6E-4B7A-9C21-DF0E5A73B914}
AppName={#Nombre}
AppVersion={#Version}
AppPublisher={#Autor}
VersionInfoVersion={#Version}

; En LOCALAPPDATA y no en Program Files: asi el instalador no pide permisos de
; administrador, que es un paso menos donde la instalacion se puede trabar.
DefaultDirName={localappdata}\Programs\{#Nombre}
PrivilegesRequired=lowest
DisableProgramGroupPage=yes
DisableDirPage=no

OutputDir=.
OutputBaseFilename={#Nombre}-{#Version}-setup
SetupIconFile=DespensaFamily.ico
UninstallDisplayIcon={app}\{#Ejecutable}
WizardStyle=modern
Compression=lzma2/max
SolidCompression=yes
ArchitecturesInstallIn64BitMode=x64compatible

[Languages]
Name: "es"; MessagesFile: "compiler:Languages\Spanish.isl"

[Files]
Source: "dist\win-unpacked\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs

[Icons]
Name: "{autoprograms}\{#Nombre}"; Filename: "{app}\{#Ejecutable}"
Name: "{autodesktop}\{#Nombre}"; Filename: "{app}\{#Ejecutable}"; Tasks: iconoescritorio

[Tasks]
Name: "iconoescritorio"; Description: "Crear un acceso directo en el escritorio"; GroupDescription: "Accesos:"

[Run]
Filename: "{app}\{#Ejecutable}"; Description: "Abrir {#Nombre} ahora"; Flags: nowait postinstall skipifsilent

; OJO: no hay seccion [UninstallDelete] a proposito.
; Los datos del negocio viven en {localappdata}\DespensaFamily (base, imagenes,
; respaldos) y NO se tocan al desinstalar ni al actualizar. Esa separacion es la
; que hace que subir de version sea simplemente volver a instalar.
