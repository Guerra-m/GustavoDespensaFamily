# DespensaFamily

Sistema de gestión comercial para un negocio de barrio: control de stock, ventas, compras, caja,
empleados y reportes, con roles de acceso granulares. Pensado para correr en la PC del propio
comercio, sin depender de un servidor externo ni de internet para operar.

## Qué es

- **Backend**: `DespensaFamily/` — Spring Boot 3, Java 17, base H2 en archivo (sin instalar
  servidor de base de datos aparte), esquema versionado con Flyway, autenticación JWT propia.
- **Frontend**: `FrontDespensaFamily/` — Vite + TypeScript, sin framework. Vite compila el
  frontend **adentro** del jar del backend (`outDir` apunta a
  `DespensaFamily/src/main/resources/static`), así que la app entera termina siendo un solo `.jar`.
- **Escritorio**: `empaquetado/` — envoltorio Electron + JDK embebido que arma el instalador
  (`setup.exe`) para PCs sin Java. Ver `PLAN-INSTALADOR.md` para el estado de esa rama.

## Cómo correr en desarrollo

Backend (puerto 8080):

```bash
cd DespensaFamily
./mvnw.cmd spring-boot:run
```

Frontend (puerto 5173, con hot reload):

```bash
cd FrontDespensaFamily
npm install
npm run dev
```

En desarrollo el backend permite el origen `http://localhost:5173` (ver
`despensa.origenes-permitidos` en `application.properties`) para que el frontend de Vite pueda
pegarle a la API en el 8080.

> Nota de esta máquina: el puerto 5173 puede estar ocupado por otro proyecto (Fondo Familiar). Si
> pasa eso, probar la app completa sirviendo el jar (backend + frontend ya compilado) en el 8080 en
> vez de levantar el dev server de Vite.

Login de desarrollo: `admin` / la contraseña que generó `DataInitializer` en el primer arranque
(queda en `%LOCALAPPDATA%\DespensaFamily\db\credenciales-iniciales.txt`, nunca en el log ni en la
consola — ver DF-SEC-004). La base local es de prueba: se puede resetear sin pedir permiso.

## Cómo empaquetar una versión nueva

`publicar.bat` en la raíz publica **solo el jar** (backend + frontend compilado adentro):

```bash
1. Subir la versión en DespensaFamily/pom.xml   (ej: 1.1.0 -> 1.2.0)
2. publicar.bat "Qué trae esta versión"
```

Deja en `empaquetado/publicacion/` el `DespensaFamily.jar` y su `.sha256` (el launcher de
escritorio lo verifica antes de instalar). El instalador completo (`setup.exe`, con Electron y el
JDK embebido) solo hace falta reconstruirlo cuando cambia Electron o la versión de Java — se arma
con `empaquetado/empaquetar.bat`. Ver `PLAN-INSTALADOR.md` para el detalle de esa parte.

## Cómo correr los tests

```bash
cd DespensaFamily
./mvnw.cmd test
```

**Importante**: por defecto, cada corrida de tests que dispara `DataInitializer` pisa
`credenciales-iniciales.txt` en la carpeta de datos real de esta PC
(`%LOCALAPPDATA%\DespensaFamily\db`) con una contraseña que no sirve para nada — ver
`tests-pisan-credenciales-reales` en la memoria del proyecto. Para no pisar la carpeta real, apuntar
`DESPENSA_HOME` a una temporal antes de correr la suite:

```bash
# PowerShell
$env:DESPENSA_HOME = "$env:TEMP\despensafamily-tests"
./mvnw.cmd test

# bash / Git Bash
export DESPENSA_HOME=/c/temp/despensafamily-tests
./mvnw.cmd test
```

La suite completa no puede bajar de la cantidad de tests verdes que ya tenía antes del cambio que
se esté haciendo — si algo baja, es una regresión, no un test "viejo que ya no aplica".

## Reglas y documentos del proyecto

- `docs/reglas-de-precio.md` — cómo se calcula el precio de una venta (descuentos, combos,
  redondeo), con `VentaService` como fuente única.
- `CLAUDE.md` — convenciones específicas de este proyecto para trabajar con agentes.
- `PLAN-INSTALADOR.md` — estado del instalador de escritorio.

## Funcionalidades principales

- **Productos y stock**: alta/edición/baja, categorías, productos pesables (por gramo), fotos.
- **Ventas**: carrito, cotización en vivo (`/ventas/cotizar`), descuentos por %, combos "lleva N
  paga M", tickets, anulación (solo dueño), historial paginado.
- **Compras**: registro de mercadería, actualización automática de costo y precio de venta.
- **Caja**: saldo, movimientos, arqueo, reversión de movimientos ligada a la venta/compra original.
- **Reportes**: ventas, rentabilidad, stock bajo, con anuladas visibles (tachadas, no ocultas).
- **Usuarios y roles**: administrador y empleado, con permisos granulares (`PERM_STOCK`,
  `PERM_CAJA`) activables por persona.
- **Asistente de IA** (Gemini): carga de productos a partir de una foto o descripción.
- **Escaneo por celular**: agregar un producto al carrito escaneando su código con el teléfono.

## Capturas

### Login
![Login](images/login.jpeg)

### Reportes
![Reportes](images/reportes.jpeg)

### Gestión de usuarios y roles
![Roles](images/roles.jpeg)

### Ventas
![Ventas](images/ventas.jpeg)
![Ventas 2](images/ventas2.jpeg)
![Ventas 3](images/ventas3.jpeg)
