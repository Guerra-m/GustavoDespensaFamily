; Instalador de DespensaFamily para Windows.
;
; Toma la carpeta que genera Electron (dist\win-unpacked) y arma un setup.exe
; con asistente en castellano, accesos directos y desinstalador. Adentro de esa
; carpeta viaja tambien el motor Java (resources\servidor), que arma jpackage.
;
; Se compila con Inno Setup 6:  iscc DespensaFamily.iss
; Si no lo tenes:               winget install JRSoftware.InnoSetup

#define Nombre        "DespensaFamily"
#ifndef Version
  #define Version "0.0.0-dev"
#endif
#define Autor         "Martin Guerra"
#define Ejecutable    "DespensaFamily.exe"
#define EjecutableServidor "DespensaFamilyServidor.exe"

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

[Code]

const
  ClaveIAPropiedad = 'despensa.ia.gemini.api-key=';

var
  // Pagina opcional para cargar la clave de Gemini. Vive solo en memoria durante
  // el instalador: no se guarda en ningun lado hasta ssPostInstall, y nunca se
  // escribe al log de Inno (no hay Log() ni MsgBox con el valor en ningun lado
  // de este archivo).
  PaginaIA: TInputQueryWizardPage;

function RutaCarpetaDeDatos(): String;
begin
  Result := ExpandConstant('{localappdata}\DespensaFamily');
end;

function RutaConfigProperties(): String;
begin
  Result := RutaCarpetaDeDatos() + '\config.properties';
end;

// Ya hay una clave de Gemini cargada de una instalacion anterior? Se usa solo
// para ajustar el texto de la pagina, nunca se muestra el valor en si.
function TieneClaveIA(): Boolean;
var
  Lineas: TArrayOfString;
  i: Integer;
  Valor: String;
begin
  Result := False;
  if not FileExists(RutaConfigProperties()) then
    Exit;
  if not LoadStringsFromFile(RutaConfigProperties(), Lineas) then
    Exit;
  for i := 0 to GetArrayLength(Lineas) - 1 do
  begin
    if Pos(ClaveIAPropiedad, Lineas[i]) = 1 then
    begin
      Valor := Copy(Lineas[i], Length(ClaveIAPropiedad) + 1, MaxInt);
      Result := Trim(Valor) <> '';
      Exit;
    end;
  end;
end;

// Actualiza (o agrega) la linea de la clave de Gemini en config.properties,
// preservando todas las demas lineas (incluido el secreto de sesion que
// escribe SecretoJwt.java en el primer arranque). Se edita como texto plano,
// igual que hace el backend, para no perder comentarios ni orden.
procedure GuardarClaveIA(const Clave: String);
var
  Lineas: TArrayOfString;
  i, Cant: Integer;
  Reemplazada: Boolean;
begin
  ForceDirectories(RutaCarpetaDeDatos());

  Reemplazada := False;
  if FileExists(RutaConfigProperties()) then
  begin
    if not LoadStringsFromFile(RutaConfigProperties(), Lineas) then
      SetArrayLength(Lineas, 0);
  end
  else
  begin
    SetArrayLength(Lineas, 0);
  end;

  for i := 0 to GetArrayLength(Lineas) - 1 do
  begin
    if Pos(ClaveIAPropiedad, Lineas[i]) = 1 then
    begin
      Lineas[i] := ClaveIAPropiedad + Clave;
      Reemplazada := True;
    end;
  end;

  if not Reemplazada then
  begin
    Cant := GetArrayLength(Lineas);
    SetArrayLength(Lineas, Cant + 2);
    Lineas[Cant] := '# Clave de la API de Gemini, cargada desde el instalador.';
    Lineas[Cant + 1] := ClaveIAPropiedad + Clave;
  end;

  // Sin el parametro UTF8: escribe ANSI/ASCII sin BOM. La clave de Gemini es
  // ASCII puro, asi que no hay perdida, y evita meter un BOM que complique la
  // lectura de config.properties del lado de Java (Properties la carga como
  // ISO-8859-1/UTF-8 segun el metodo, y un BOM no es valido en ninguno).
  SaveStringsToFile(RutaConfigProperties(), Lineas, False);
end;

// Verdadero si el proceso indicado aparece en la lista de tareas. Se usa
// tasklist redirigido a un archivo temporal porque Exec no da acceso directo
// a la salida estandar del proceso que lanza.
function ProcesoCorriendo(const NombreExe: String): Boolean;
var
  ArchivoTemp: String;
  Contenido: AnsiString;
  ResultCode: Integer;
begin
  Result := False;
  ArchivoTemp := ExpandConstant('{tmp}\df_tasklist.txt');
  if Exec(ExpandConstant('{cmd}'),
     '/C tasklist /FI "IMAGENAME eq ' + NombreExe + '" /NH > "' + ArchivoTemp + '"',
     '', SW_HIDE, ewWaitUntilTerminated, ResultCode) then
  begin
    if LoadStringFromFile(ArchivoTemp, Contenido) then
      Result := Pos(Lowercase(NombreExe), Lowercase(String(Contenido))) > 0;
  end;
  if FileExists(ArchivoTemp) then
    DeleteFile(ArchivoTemp);
end;

// Si DespensaFamily o su servidor estan corriendo, los archivos quedan
// trabados y la instalacion/desinstalacion falla a mitad de camino. Avisa y,
// si el usuario acepta (o el instalador corre silencioso), los cierra:
// primero sin /F para darle al servidor Java la chance de cerrar la base en
// orden, y recien si siguen vivos despues de unos segundos, con /F.
function CerrarAppSiHaceFalta(Silencioso: Boolean): Boolean;
var
  ResultCode: Integer;
begin
  Result := True;
  if not (ProcesoCorriendo('{#Ejecutable}') or ProcesoCorriendo('{#EjecutableServidor}')) then
    Exit;

  if not Silencioso then
  begin
    if MsgBox('Hay que cerrar DespensaFamily para continuar.' + #13#10 + #13#10 +
       'Setup va a cerrar la aplicacion y su servidor ahora. Guarda lo que este' + #13#10 +
       'haciendo antes de aceptar.', mbConfirmation, MB_OKCANCEL) = IDCANCEL then
    begin
      Result := False;
      Exit;
    end;
  end;

  Exec(ExpandConstant('{cmd}'),
    '/C taskkill /IM {#Ejecutable} /T & taskkill /IM {#EjecutableServidor} /T',
    '', SW_HIDE, ewWaitUntilTerminated, ResultCode);
  Sleep(3000);

  if ProcesoCorriendo('{#Ejecutable}') or ProcesoCorriendo('{#EjecutableServidor}') then
  begin
    Exec(ExpandConstant('{cmd}'),
      '/C taskkill /IM {#Ejecutable} /T /F & taskkill /IM {#EjecutableServidor} /T /F',
      '', SW_HIDE, ewWaitUntilTerminated, ResultCode);
    Sleep(1000);
  end;
end;

procedure InitializeWizard();
var
  Texto: String;
begin
  Texto := 'DespensaFamily puede usar el asistente de IA (Gemini, de Google) para cargar' + #13#10 +
    'productos a partir de una foto o un texto, en vez de tipearlos a mano.' + #13#10 + #13#10 +
    'Es opcional: si dejas esto vacio, la aplicacion funciona igual, solo que sin el' + #13#10 +
    'asistente. Lo podes cargar o cambiar despues desde Ajustes, dentro de la aplicacion.' + #13#10 + #13#10 +
    'Sacar una clave gratis en https://aistudio.google.com/apikey y pegarla aca.';

  if TieneClaveIA() then
    Texto := Texto + #13#10 + #13#10 + 'Ya hay una clave cargada; deja esto vacio para conservarla.';

  PaginaIA := CreateInputQueryPage(wpSelectTasks,
    'Asistente de IA (opcional)',
    'Cargar productos con una foto o un texto',
    Texto);
  PaginaIA.Add('Clave de la API de Gemini:', True);
end;

function NextButtonClick(CurPageID: Integer): Boolean;
var
  Clave: String;
begin
  Result := True;
  if (PaginaIA = nil) or (CurPageID <> PaginaIA.ID) then
    Exit;

  Clave := Trim(PaginaIA.Values[0]);
  PaginaIA.Values[0] := Clave;
  if Clave = '' then
    Exit;

  if (Pos(' ', Clave) > 0) or (Pos(#9, Clave) > 0) or (Pos(#13, Clave) > 0) or (Pos(#10, Clave) > 0) then
  begin
    MsgBox('Esa clave no puede tener espacios ni saltos de linea.' + #13#10 +
      'Revisala (que no se haya copiado texto de mas) e intenta de nuevo.', mbError, MB_OK);
    Result := False;
    Exit;
  end;

  if Copy(Clave, 1, 4) <> 'AIza' then
  begin
    if MsgBox('Las claves de Gemini suelen empezar con "AIza" y esta no.' + #13#10 +
      'Puede ser valida igual. Queres usarla como esta?', mbConfirmation, MB_YESNO) = IDNO then
    begin
      Result := False;
      Exit;
    end;
  end;
end;

function PrepareToInstall(var NeedsRestart: Boolean): String;
begin
  Result := '';
  if not CerrarAppSiHaceFalta(WizardSilent()) then
    Result := 'Instalacion cancelada: hay que cerrar DespensaFamily primero.';
end;

function InitializeUninstall(): Boolean;
begin
  Result := CerrarAppSiHaceFalta(UninstallSilent());
end;

procedure CurStepChanged(CurStep: TSetupStep);
var
  Clave: String;
begin
  if CurStep <> ssPostInstall then
    Exit;
  if PaginaIA = nil then
    Exit;

  Clave := Trim(PaginaIA.Values[0]);
  if Clave <> '' then
    GuardarClaveIA(Clave);
  // Si esta vacio no se toca el archivo: una actualizacion conserva la clave
  // que ya estaba, y una instalacion nueva sin IA no crea la linea.
end;

procedure CurUninstallStepChanged(CurUninstallStep: TUninstallStep);
begin
  if CurUninstallStep <> usPostUninstall then
    Exit;
  if UninstallSilent() then
    Exit;

  MsgBox('Tus datos (ventas, productos y respaldos) quedaron guardados en:' + #13#10 + #13#10 +
    RutaCarpetaDeDatos() + #13#10 + #13#10 +
    'Si reinstalas, vuelven a aparecer.', mbInformation, MB_OK);
end;
