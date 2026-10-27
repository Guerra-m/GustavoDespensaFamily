# Plan: DespensaFamily de escritorio (instalable en PC)

> Estado: **F0 a F5 hechas** (F5 parcial por decisión) y **DespensaFamily-1.0.0-setup.exe generado**.
> **El setup.exe del 30/08 quedó viejo**: no tiene el asistente de IA, ni Ajustes, ni las
> correcciones de seguridad del 01/09, ni las migraciones de esquema (sección 12), ni la
> actualización automática (sección 13). Antes de instalar en el negocio hay que reempaquetar.
> La versión pasó a **1.1.0** y vive en un solo lugar: `DespensaFamily/pom.xml`.
> Probado en la PC de Martín el 29/08/2026; los tres problemas que aparecieron están arreglados.
> **Después de esa prueba se agregó la ventana propia con Electron: la app ya no abre el navegador.**
> Ver la sección 10, que es la que manda sobre el empaquetado descrito en la 4.
> Todo commiteado en la rama `instalador-escritorio`, sin pushear ni mergear.
> Documento visual equivalente: https://claude.ai/code/artifact/e81b1e03-1399-4e25-8d85-28fb3f420b4c

Objetivo: pasar de "carpeta de proyecto que hay que compilar" a **un `setup.exe` que se instala con doble clic**
en la PC del negocio, sin que esa PC tenga Java, MySQL ni Node.

---

## 1. Dónde estamos

El producto está terminado y funcionando. Los últimos ~20 commits fueron todos de funcionalidad
(POS, reportes con gráficos, promos, responsive, seguridad, expiración de token).

Ya está resuelto el paso más importante para poder empaquetar: **Vite compila el frontend adentro
del backend** (`vite.config.ts` → `outDir: ../DespensaFamily/src/main/resources/static`), así que
hoy la app entera es **un solo jar de 63 MB**.

Números relevantes:

| Dato | Valor |
|---|---|
| Artefactos | 1 (`DespensaFamily.jar`, ~63 MB) |
| Entidades JPA | 8 (Categoria, Compra, DetalleCompra, DetalleVenta, Producto, Promocion, Usuario, Venta) |
| Clases Java | 68 |
| Pantallas del front | 11 |
| **SQL nativo** | **0** ← clave: los 3 repos con `@Query` usan JPQL puro |
| Stack | Spring Boot 4 · Java 17 · Vite + TS · MySQL 8 |

El "0 SQL nativo" es lo que habilita todo el plan: la capa de datos es portable sin tocar lógica.
(Verificado en `DetalleCompraRepository`, `DetalleVentaRepository`, `VentaRepository`.)

---

## 2. El problema

Instalar hoy en una PC nueva = **6 pasos y 3 programas ajenos**:

```
PC nueva → [JDK 17] → [MySQL 8, root sin password] → [Node + build.bat] → iniciar.bat → navegador
             ^^^^^^^^^^^^^^^^ los instala la persona, a mano ^^^^^^^^^^^^^^^^
```

Fricciones concretas:
- `iniciar.bat` busca java en PATH / `JAVA_HOME` / `~/.jdks`; si no está, muestra un cartel y se rinde.
- `build.bat` corre `npm install` + `mvnw package`: varios minutos y toolchain de desarrollo.
- `iniciar.bat` espera **10 segundos fijos** y después abre la URL a mano.
- No hay forma de cerrar la app salvo matando el proceso.
- `backend.bat` y `start_all.txt` tienen la ruta `C:\Program Files\Java\jdk-17\...` hardcodeada.

Objetivo:

```
PC nueva → [DespensaFamily-1.0-setup.exe] → ícono en el escritorio → doble clic y abre
             ^^ Java + base + front + back adentro ^^
```

---

## 3. Los tres bloqueos

### 3.1 Java es externo
**Solución:** `jlink` + `jpackage` (ya vienen con el JDK 17 instalado en `~/.jdks/ms-17.0.15`).
Generan un runtime Java recortado (~55 MB) y un ejecutable nativo que lo usa.
La PC destino no necesita Java ni sabe que existe.

### 3.2 MySQL es externo — **LA DECISIÓN ABIERTA**
Es el bloqueo más caro: servicio de Windows aparte, instalador propio, puerto 3306, credenciales
(`root` con contraseña vacía en `application.properties`).

**Recomendación: H2 en archivo.** Lo que se elimina no es la base de datos, es el *cruce de proceso*.

| Opción | Peso extra | Riesgo | Veredicto |
|---|---|---|---|
| **H2 en archivo** ⭐ | ~2,5 MB | Migrar datos existentes. No sirve para muchos usuarios concurrentes. | Para 1–2 cajas sobra. `ddl-auto=update` arma el esquema solo y el JPQL es portable → cambio de config, no de lógica. |
| MariaDB embebida (mariadb4j) | ~150 MB | Levanta binario nativo; con antivirus agresivo en Windows falla de formas difíciles de diagnosticar. | Sólo si apareciera SQL específico de MySQL, que hoy no existe. |
| Seguir con MySQL | ~350 MB | Instalador necesita admin, instala servicio, choca si la PC ya tiene algo en 3306. | Sólo si hay datos en uso que no se quieran mover. |

### 3.3 El arranque es manual
**Solución: launcher — y NO es un programa aparte.**
El `.exe` lo genera jpackage; el comportamiento de escritorio son **~40 líneas de Java dentro de la
misma app**, colgadas de `ApplicationReadyEvent`, usando `Desktop.browse()` y `SystemTray`.
Sin Electron, sin código nativo, sin dependencias nuevas.

Comportamiento al hacer doble clic:
1. ¿Ya está corriendo? (consulta el puerto + health check)
   - **Sí** → sólo abre el navegador. No levanta una segunda copia.
   - **No** → arranca la JVM incluida, Spring Boot en `127.0.0.1`.
2. Cuando el servidor responde (sondeo real, no `timeout /t 10`) → abre el navegador en el login.
3. Queda un ícono en la bandeja: **Abrir · Respaldar · Salir** (única forma de cerrarla bien).

---

## 4. Empaquetado

Todo esto corre en la máquina de desarrollo, una vez por versión, desde un `empaquetar.bat` que
reemplaza al `build.bat` actual:

```
Front  --npm run build--> static/  --+
                                     +--> DespensaFamily.jar (63 MB) --+
Backend --mvnw package--> ...  ------+                                 |
                                                                       +--jpackage--> carpeta app
Runtime Java 17 --jlink--> (~55 MB) --------------------------------- +                    |
                                                                                     Inno Setup
                                                                                           |
                                                                            setup.exe (~120 MB)
```

> Este diagrama quedó viejo con la sección 10: entre jpackage e Inno Setup ahora hay un paso más
> (Electron), y el setup.exe pasó de ~120 MB a 188 MB.

**Por qué Inno Setup y no el instalador de jpackage:** `jpackage --type exe` exige tener WiX Toolset
instalado y da poco control sobre el asistente. `--type app-image` no necesita nada extra, y desde
esa carpeta Inno Setup arma un instalador más prolijo, en castellano, con accesos directos y
desinstalador, en menos tiempo.

**Instalar en `%LOCALAPPDATA%\Programs`** en lugar de `Program Files`: no dispara el cartel de
permisos de administrador de Windows. Un paso menos donde trabarse.

---

## 5. Dónde viven los datos

Separar programa de datos es lo que hace que actualizar sea "reinstalar y listo".

```
Programa — lo pisa cada instalación
%LOCALAPPDATA%\Programs\DespensaFamily\
├── DespensaFamily.exe      (launcher generado por jpackage)
├── runtime\                (Java 17 recortado, ~55 MB)
└── app\
    └── DespensaFamily.jar  (backend + frontend compilado)

Datos — sobrevive a actualizaciones y al desinstalador
%LOCALAPPDATA%\DespensaFamily\
├── db\despensa.mv.db       (toda la base en un archivo)
├── backups\
│   ├── despensa-2026-08-28.zip
│   └── ...                 (copia al arrancar, se guardan las últimas 14)
├── logs\despensafamily.log (rotativo)
└── config.properties       (puerto, secreto JWT, acceso desde la red)
```

- **Respaldar deja de ser técnico:** hoy es `mysqldump`; con la base en un archivo, "Respaldar" de la
  bandeja copia y comprime, y Gustavo se lleva el zip en un pendrive sin llamar a nadie.
- **La configuración sale del jar:** cambiar el puerto deja de obligar a recompilar.

---

## 6. Seguridad que arrastra el instalador

No son problemas nuevos: son decisiones de desarrollo que dejan de ser aceptables cuando la app se
instala en una máquina que no controlamos. Antes de escribir este código, pasar por la biblioteca de
seguridad (`~/.claude/skills/seguridad-arquitectura/`), superficies **autenticación (AUTN)**,
**gestión de secretos (SEC)** y **backups (BKP)**.

| Hoy | Riesgo en el local | Qué hace el instalador |
|---|---|---|
| `jwt.secret` con default en `application.properties` | Está en el repo y sería idéntico en toda instalación → se puede firmar un token de admin | Generar secreto aleatorio en la primera ejecución, guardarlo en `config.properties` fuera del jar |
| `admin` / `admin123` creado por `DataInitializer` | Si nadie lo cambia, cualquiera que llegue al login entra como administrador | Forzar cambio de contraseña en el primer ingreso |
| El servidor escucha en todas las interfaces | Cualquier dispositivo del wifi del local llega al login; hoy no es una decisión, pasa solo | Escuchar sólo en `127.0.0.1` por defecto; abrir a la red = opción explícita del instalador + regla de firewall |

---

## 7. Plan por fases

Cada fase deja la app funcionando y desbloquea la siguiente. Los tiempos son estimaciones de trabajo
enfocado, no compromisos.

- [x] **F0 — Sacar la configuración del jar** · hecho el 28/08/2026
  `AppPaths` resuelve la carpeta de datos (`%LOCALAPPDATA%\DespensaFamily`, override con
  `DESPENSA_HOME`) y publica `${despensa.home|db|imagenes|backups|logs}` como propiedades del
  sistema. `application.properties` importa, si existe, `${despensa.home}/config.properties`.
  Se eliminaron tres rutas hardcodeadas: `C:/uploads/productos` en `WebConfig` y en
  `ProductoService`, y `http://localhost:8080/imagenes/` en `Mapper` (ahora URL relativa).

- [x] **F1 — Base de datos embebida** · hecho el 28/08/2026
  H2 en archivo en `%LOCALAPPDATA%\DespensaFamily\db\despensa.mv.db`. Se dejó el bloque de MySQL
  comentado en `application.properties` y la dependencia del conector, para poder volver o migrar.
  Verificado: 7/7 tests, arranque del jar, login de admin contra H2, e imágenes servidas desde la
  carpeta nueva. Hibernate parsea todas las `@Query` al levantar el contexto, así que el arranque
  limpio ya prueba que ninguna consulta JPQL se rompió con el dialecto H2.
  **Pendiente de verificación con datos reales:** los totales de Reportes y Promos, que hoy
  devuelven 0 sobre una base vacía (ver "Migración del catálogo" más abajo).

- [x] **F2 — Comportamiento de escritorio** · hecho el 28/08/2026
  `Escritorio` (lógica pura, 11 tests) + `LauncherDeEscritorio` (`@Component` colgado de
  `ApplicationReadyEvent`). El sondeo de arranque ya no es una espera fija: Spring dispara el evento
  cuando el servidor acepta pedidos de verdad. Ícono en la bandeja con **Abrir · Respaldar · Salir**,
  dibujado en memoria (todavía sin archivo de ícono propio, eso va en F3 junto con el del ejecutable).
  Instancia única resuelta en `main()`: antes de arrancar Spring pregunta por la página de login en el
  puerto configurado; si contesta, abre el navegador y sale.
  El puerto se resuelve en orden: argumento `--server.port` → `config.properties` → 8080.
  Se puede apagar todo con `despensa.escritorio=false` (lo usa el test de contexto, para no abrir un
  navegador en la máquina que corre los tests).
  **Respaldo:** usa `BACKUP TO` de H2, que escribe un zip consistente con la base en uso. Copiar el
  `.mv.db` a mano mientras la app corre puede dar un archivo a medio escribir.

- [x] **F3 — Empaquetado con jpackage** · hecho el 29/08/2026 *(falta compilar el instalador)*
  Todo el pipeline vive en `empaquetado/`:
  - `empaquetar.bat` — front → backend → jpackage → Inno Setup con un solo doble clic. Busca el JDK
    solo (`JAVA_HOME` o `~/.jdks`) y avisa qué falta en vez de fallar en silencio.
  - `GenerarIcono.java` — dibuja el `.ico` con las 6 medidas que pide Windows.
  - `DespensaFamily.iss` — instalador en castellano, en `%LOCALAPPDATA%\Programs`, con accesos
    directos y desinstalador, y **sin `[UninstallDelete]` a propósito**: desinstalar no borra los
    datos del negocio.
  - `DespensaFamily.ico` — versionado. `dist/`, `entrada/` y el setup quedaron en `.gitignore`.

  **Verificado:** el `.exe` levanta con su propio runtime (sin Java en el PATH), sirve el login,
  autentica y muestra el catálogo con imágenes. Pesa **135 MB** instalado.

  **Pendiente:** compilar el `setup.exe`. Inno Setup no está instalado en esta máquina:
  `winget install JRSoftware.InnoSetup` y volver a correr `empaquetar.bat`.

  Dos decisiones que conviene no revisar sin motivo:
  - `--add-modules java.se,jdk.unsupported,jdk.crypto.ec` en vez de recortar con `jdeps`. Spring
    resuelve mucho por reflexión y un recorte fino termina en fallos raros en tiempo de ejecución.
    Se afina midiendo, después, no antes.
  - El ícono **no** usa el emoji 🏪 del favicon: Java2D no dibuja fuentes de emoji a color y sale en
    blanco. Es una "D" blanca sobre el celeste de la navbar, igual que el ícono de la bandeja.

- [x] **F4 — Operación desatendida** · hecho el 29/08/2026
  `ServicioDeRespaldo` respalda al arrancar y conserva los últimos 14; el botón de la bandeja usa el
  mismo código, así que hay un solo lugar donde puede estar mal. `Respaldos` tiene la política de
  retención (7 tests). Logs en `%LOCALAPPDATA%\DespensaFamily\logs\despensafamily.log`, rotativo:
  5 MB por archivo, 7 archivos, 50 MB de tope.

  **Al arrancar y no en un horario fijo:** la PC del comercio se prende a la mañana y se apaga a la
  noche. Un cron a las 3 AM no correría nunca.

  **La retención sólo toca lo que generó la app.** Sólo cuenta y borra archivos con el nombre exacto
  `despensa-<fecha>_<hora>.zip`. Una copia hecha a mano antes de una migración, o cualquier cosa que
  alguien deje en esa carpeta, queda intacta — es la única copia de los datos del negocio.

  **Verificado con datos reales:** con 16 respaldos falsos en la carpeta, al arrancar borró los 3 más
  viejos, dejó 14, no tocó las dos copias manuales `.mv.db`, y el zip nuevo contiene el
  `despensa.mv.db` completo.

- [~] **F5 — Cierre de seguridad** · parcial, 29/08/2026

  **Hecho — secreto de firma por instalación.** `SecretoJwt` genera 256 bits aleatorios en el primer
  arranque y los guarda en `config.properties`, dentro de la carpeta de datos. Ya **no queda ningún
  secreto en el repositorio**: `application.properties` no define `jwt.secret`, y el
  `EnvironmentPostProcessor` agrega el valor como última alternativa, así que `JWT_SECRET` y el
  propio `config.properties` siguen ganando. 8 tests.
  *Verificado:* el login funciona y un token firmado con el secreto viejo del repo ahora da **401**.

  **Decidido no hacer** (29/08/2026, decisión de Martín):

  | Punto | Estado |
  |---|---|
  | Cambio de contraseña obligatorio en el primer ingreso | No se hace por ahora |
  | Escuchar sólo en `127.0.0.1` | **Descartado**: la app se tiene que poder usar desde el celular en el wifi del local |

  **Riesgo aceptado y anotado:** con el acceso desde la red habilitado, `admin` / `admin123` queda
  alcanzable desde cualquier dispositivo del wifi del local mientras nadie cambie esa contraseña.
  Alcanza con cambiarla a mano desde Usuarios una vez instalada; lo que no hay es nada que lo fuerce.

**Total estimado: ~18–20 h.**

---

## 9 bis. Primera prueba real (29/08/2026)

Martín probó el paquete en su propia PC y aparecieron tres cosas. Las tres están arregladas y
commiteadas.

### El instalador ya existe

Se instaló Inno Setup (`winget install JRSoftware.InnoSetup`) y se corrió `empaquetar.bat` entero:

```
empaquetado\DespensaFamily-1.0.0-setup.exe   ~74 MB
```

`winget` lo instala en `%LOCALAPPDATA%\Programs`, no en `Program Files`. `empaquetar.bat` buscaba
sólo en `Program Files` y lo salteaba en silencio; ahora mira los dos lugares.

### El celular no entraba — y no era el firewall

**Causa:** el front resuelve `import.meta.env.VITE_API_URL` **en tiempo de compilación**, y `.env`
decía `VITE_API_URL=http://localhost:8080`. Esa URL quedaba grabada en el bundle, así que el
JavaScript que corría en el celular pedía datos a *sí mismo*. La pantalla de login cargaba —es HTML
estático que sirve el jar— y no respondía a nada.

**Arreglo:** `.env.production` con `VITE_API_URL=` vacío → todos los `fetch` quedan relativos, que es
lo correcto porque el jar sirve el front y la API desde el mismo origen. `.env` sigue valiendo para
`npm run dev`. De regalo, tampoco se rompe al cambiar el puerto.

**Verificado contra la IP de red real** (`192.168.1.37`, no localhost): login, productos e imágenes
responden 200 y el bundle ya no menciona `localhost`.

### No se encontraba cómo cerrar el programa

**Causa:** Windows 11 esconde los íconos nuevos de la bandeja detrás de la flecha `^`. El ícono
estaba, pero invisible, y era el único lugar donde vivía "Salir".

**Arreglo:** al arrancar, el ícono muestra un aviso que dice que cerrar el navegador no cierra el
programa, que para cerrarlo hay que hacer clic derecho, y que si no se ve está detrás de la flecha.
El tooltip lo repite. Además se agregó una red de seguridad en el cierre: si el apagado ordenado se
cuelga, a los 8 segundos corta por lo sano.

*Descartado en el camino:* los dos procesos `DespensaFamily.exe` que se ven en el Administrador de
tareas son el lanzador de jpackage y su hijo con la JVM. Matando al hijo se cierran los dos, así que
no eran la causa.

### Pendiente, a criterio de Martín

Un botón **"Cerrar aplicación" dentro de la app**, en la navbar. Sería la forma más obvia de todas,
pero implica decidir **quién puede apagar la caja** (¿un empleado o sólo el admin?) y toca las once
pantallas. No se hizo.

---

## 7 bis. Migración del catálogo — **hecha el 28/08/2026**

Se migraron **13 categorías y 2033 productos** de MySQL a H2, y después se limpiaron **2005 de esos
productos**: eran sintéticos ("Producto Audit #N", "Producto RaceTest…"), generados por un script en
una prueba de carga anterior, no eran del negocio. **El catálogo real son 28 filas, 13 activas**, con
sus imágenes. Ventas, compras, promociones y usuarios quedaron fuera a propósito: se migró el
catálogo, no el historial.

> Lección: antes de migrar en bloque, mirar *qué* hay, no sólo cuántas filas. El "2033 productos" que
> motivó la migración era 98% basura de pruebas. Respaldo previo a la limpieza en
> `backups/despensa-previo-limpieza.mv.db`.

Quedan 8 productos que parecen de prueba pero no se tocaron, por ser una decisión del dueño de los
datos: `QA Papel Higienico Paquete`, `QA Stock Refresh`, `Producto QA Alta Editado`,
`Producto QA Con Imagen`, `TEST PESO SCAN`, `Producto Valido Test`, `Producto Con Imagen`,
`Producto Test Fase5`.

- Las 16 columnas de `producto` y las 3 de `categoria` coincidieron exactamente entre las dos bases
  (ambos esquemas los generó Hibernate desde las mismas entidades), así que no se perdió ni un campo.
- Se reiniciaron las secuencias de identidad (`producto` sigue en 2034, `categoria` en 14) para que
  un producto nuevo no reuse un id ocupado.
- Respaldo previo en `backups/despensa-previo-migracion-20260828-2349.mv.db`.
- El script de migración es de un solo uso y vive fuera del repo (no se versiona: no es código de la
  aplicación). Si hiciera falta repetirlo, está descrito acá: leer con el conector MySQL, copiar la
  intersección de columnas, reiniciar identidades.

**Verificación con datos reales** (app empaquetada corriendo en :8099):

| Chequeo | Resultado |
|---|---|
| `GET /productos` | 2018 activos de 2033 |
| `GET /categorias` | 13 |
| `imagenUrl` de un producto | `/imagenes/1767458579976_cocacola.jpg` (relativa) y el archivo responde 200 |
| 6 endpoints de `/reportes` | todos 200 con `periodo` válido (`anio` da 400, que es correcto) |
| Login `admin` contra H2 | token OK |

Queda pendiente sólo borrar `C:/uploads/productos` cuando estés conforme: las 31 imágenes ya están
copiadas en `%LOCALAPPDATA%\DespensaFamily\imagenes`.

### Estado original del MySQL (referencia)

| Tabla | Filas |
|---|---|
| producto | **2033** (22 con imagen) |
| categoria | 13 |
| usuario | 5 |
| venta | 69 |
| compra | 19 |
| promocion | 1 |

Los 5 usuarios de MySQL no se migraron: en H2 existe sólo el `admin` que crea `DataInitializer`.
Si Gustavo y los empleados tenían cuentas propias, hay que volver a crearlas (o migrarlas, los hashes
BCrypt son portables tal cual).

---

## 8. Por dónde arrancar en la próxima sesión

**F0 + F1 juntos, en una rama**, con la app corriendo contra H2 en la máquina de desarrollo antes de
tocar nada del empaquetado. Si eso anda, el resto es mecánico.

### Preguntas abiertas (bloquean el detalle, no el arranque)

1. **¿La app ya está en uso con datos reales en MySQL?**
   → Si sí, F1 suma un paso de migración con verificación. Si arranca de cero, F1 es sólo configuración.
   **Ésta es la que más cambia el plan.**
2. **¿Se va a usar desde más de un dispositivo?** (segunda caja, celular para consultar stock en el
   depósito) → cambia la respuesta sobre escuchar en la red y sobre si H2 alcanza.
3. **¿Quién instala la primera vez?** Si va Martín, el asistente puede ser mínimo. Si va por pendrive
   y lo instala Gustavo, el instalador tiene que ser a prueba de todo + video de 30 segundos.
4. **¿Firmamos el ejecutable?** Sin firma digital, SmartScreen muestra "editor desconocido" la primera
   vez (se pasa con *Más información → Ejecutar de todas formas*, pero asusta). Un certificado cuesta
   plata por año: decisión de presupuesto, no técnica.

---

## 9. Notas sueltas para no re-descubrir

- El JDK está en `%USERPROFILE%\.jdks\ms-17.0.15` y `ms-21.0.8`. **No hay `java` en el PATH** ni
  `JAVA_HOME` seteado — por eso `iniciar.bat` tiene la búsqueda en tres lugares.
- `build.bat`, `iniciar.bat`, `backend.bat`, `close_all.bat` y `start_all.txt` están en `.gitignore`
  (scripts locales). Si `empaquetar.bat` los reemplaza, decidir si se versiona.
- `DespensaFamily/src/main/resources/static/` también está en `.gitignore` (lo genera Vite).
- Hay `pnpm-lock.yaml` **y** `package-lock.json` en el front: conviene elegir uno antes de automatizar
  el build.
- Los logs `backend_dev*.log` de la raíz (varios MB) son basura de desarrollo; están ignorados por
  `*.log` pero siguen ocupando la carpeta.

---

## 10. Ventana propia con Electron — 29/08/2026

Pedido de Martín, después de la primera prueba real: *"al ejecutar el programa me gustaría que sea
una app de escritorio, no de navegador"*.

### Qué cambió

Antes, `DespensaFamily.exe` era el servidor Java: arrancaba, abría el navegador del sistema en el
login y dejaba un ícono en la bandeja explicando que cerrar el navegador **no** cerraba el programa.
Funcionaba, pero se veía y se comportaba como una página web: barra de direcciones, pestañas, y dos
cosas abiertas (navegador + bandeja) para un solo programa.

Ahora hay dos procesos con roles claros:

```
DespensaFamily.exe (Electron)          ventana, instancia única, ciclo de vida
        │  arranca como hijo, con --despensa.escritorio.modo=ventana
        ▼
resources\servidor\DespensaFamilyServidor.exe (jpackage)   servidor, datos, respaldos, bandeja
```

Se descartó **Tauri**, que habría dado un instalador mucho más chico usando el WebView2 que ya trae
Windows: necesita Rust y las Build Tools de MSVC, que en esta máquina no están (varios GB de
toolchain nuevo). Electron se arma con el Node que ya se usaba para el front.

### Decisiones que no son obvias

**Cerrar la ventana cierra el programa, y por un pipe, no por HTTP.** El servidor se arranca con
`stdin` conectado; cuando Electron termina —de forma ordenada o de golpe— el sistema cierra ese
pipe, el servidor ve el fin de la entrada y se apaga solo cerrando la base con orden. La alternativa
era un endpoint HTTP de apagado, es decir un botón de "cerrar el negocio" abierto a cualquier cosa
que corra en la misma máquina. Probado matando Electron a la fuerza: el servidor se cerró solo en
~2 segundos y liberó el puerto. Como red de seguridad, si a los 8 segundos sigue vivo, se lo mata.

**La bandeja sigue existiendo, pero recortada.** En modo ventana el menú queda en *Respaldar ahora*
y *Salir*, sin el *Abrir* (la ventana ya está) y sin el aviso de "cerrar el navegador no cierra el
programa" (ya no aplica). Se mantuvo la bandeja en vez de mover el respaldo manual a Electron
justamente para no tener que abrir un endpoint de respaldo.

**Dos modos, y el de siempre es el default.** `despensa.escritorio.modo` vale `navegador` (default:
correr el jar suelto sigue funcionando igual que antes) o `ventana`. Un valor mal escrito cae en
`navegador`: nunca puede dejar la app sin ninguna forma de abrirse.

**Instancia única la maneja Electron**, con `requestSingleInstanceLock`. Doble clic en el ícono con
la app abierta trae la ventana al frente. Y si al arrancar ya hay un servidor respondiendo (quedó de
un cierre que no terminó de bajar), no se levanta otro: se usa el que está.

**Pantalla de carga propia** (`cargando.html`), porque el servidor tarda entre 5 y 15 segundos y una
ventana en blanco parece colgada. Sin recursos externos: tiene que dibujarse antes de que exista el
servidor.

**Endurecimiento de la ventana:** `contextIsolation`, `sandbox`, sin `nodeIntegration` y sin preload
(no hay puente que exponer). La navegación queda encerrada en `http://localhost:<puerto>`; cualquier
otro destino se abre en el navegador del sistema. `about:blank` sí se permite: los tickets y los
carteles de precio se imprimen abriendo una ventana en blanco y escribiendo el HTML adentro.

**Dependencias fijadas exactas** (DEP-01), con lockfile versionado: `electron` 44.0.0 y
`electron-builder` 26.15.3. La primera instalación había traído Electron 33, que `npm audit` marca
con vulnerabilidades altas — es el motor donde corre el POS, así que se subió a la línea actual.
`npm audit`: 0 vulnerabilidades.

### Lo que cuesta

| | Antes | Ahora |
|---|---|---|
| Carpeta instalada | ~180 MB | 501 MB |
| `setup.exe` | ~90 MB | 188 MB |
| Procesos | 1 | 2 (ventana + servidor) |
| Toolchain de build | JDK + Node + Inno | JDK + Node + Inno (igual) |

### Cómo se arma

`empaquetado\empaquetar.bat`, sin cambios en la forma de usarlo. Pasó de 5 pasos a 6:

```
front -> jar -> build\DespensaFamilyServidor (jpackage) -> dist\win-unpacked (Electron) -> setup.exe
```

Para probar la ventana sin armar el instalador entero, con el motor ya generado por jpackage:

```
cd empaquetado\escritorio && npm start
```

### Pendiente

- Probarlo en la PC del negocio (acá se probó en la de desarrollo, contra una carpeta de datos
  aparte en el puerto 8099 para no tocar los datos reales).
- El menú de la ventana está oculto (`autoHideMenuBar`). Si hace falta *Imprimir* o *Recargar* a mano,
  hay que armar un menú propio en castellano.
- Sigue sin firma digital: SmartScreen va a mostrar "editor desconocido" igual que antes.

---

## 11. Auditoría integral y correcciones — 01/09/2026

Repaso completo del sistema antes de darlo por instalable. 28 hallazgos; 21 corregidos con
tests y verificados contra la aplicación corriendo. Informe visual completo:
https://claude.ai/code/artifact/e5cdbb00-001a-429b-a038-0201f6d8455c

### Lo que estaba mal, y era grave

Había una cadena de cinco eslabones que permitía a **cualquiera conectado al wifi del local**
entrar al sistema y quedarse con plata de la caja. Reproducido de punta a punta.

1. El servidor escucha en `0.0.0.0` — cualquier celular en el wifi llega al login.
2. El instalador creaba `juan`, `maria` y `carlos` con la contraseña `empleado123` escrita en
   el código, **y las volvía a crear en cada arranque**, así que borrarlas no servía. El admin
   venía con `admin123`.
3. Sin límite de intentos: 20 en 3 segundos. Y respondía distinto según el usuario existiera o
   no, lo que permitía averiguar las cuentas antes de atacar contraseñas.
4. No había ninguna regla por rol fuera de `/reportes`, `/admin`, `/configuracion` y
   `/promociones`: un empleado creaba y borraba productos y registraba compras.
5. **Un empleado podía anular sus propias ventas**: cobraba, anulaba, y la venta desaparecía
   del listado sin dejar rastro de quién vendió ni quién anuló.

### Cómo quedó

- Ningún usuario de ejemplo. El admin nace con una contraseña al azar por instalación, anotada
  en `credenciales-iniciales.txt` dentro de la carpeta de datos, con cambio obligatorio al
  entrar. Al actualizar, las cuentas viejas se borran **sólo si conservan la contraseña
  pública** — si alguien reusó el nombre para un empleado real, esa cuenta se respeta.
- Retardo progresivo por usuario y por IP, con techo de 5 minutos. Es retardo y no bloqueo de
  cuenta a propósito: bloquear sería un arma para dejar al dueño afuera fallando el login
  adrede (AUTN-03).
- Matriz de permisos: el empleado vende y consulta; el catálogo, las compras, anular y la IA
  son del dueño.
- Cada venta y compra guardan qué usuario las hizo. Anular guarda quién y cuándo, y la venta
  anulada sigue visible tachada en vez de desaparecer.
- `fechaHora` pasó de `LocalDate` a `LocalDateTime`. El campo siempre se llamó así pero
  guardaba sólo el día. **Ojo**: los reportes ahora consultan por rango completo del día y el
  de ganancia agrupa con `CAST(... AS LocalDate)` — si se toca una query de reportes, mirar eso.
- Escape de HTML en el frontend. Había 62 `innerHTML` con datos del backend y **ninguna**
  función de escape: un producto llamado `<img src=x onerror=...>` ejecutaba código en la
  sesión del dueño, y el token vive en `localStorage`.
- Límite de subida a 10 MB. Estaba en el default de Spring de **1 MB**, así que ninguna foto de
  celular entraba: el asistente de IA declaraba aceptar 8 MB y ese límite era inalcanzable.
- Stock con bloqueo pesimista y orden fijo de id. Con `@Version` solo, 20 ventas simultáneas
  agotaban el reintento y una venta fallaba de verdad (el test fallaba 1 de cada 5 corridas).
  Ahora 8 de 8.
- Redondeo de importes en un solo lugar (`Dinero`). Estaba aplicado al usar la ganancia global
  pero no al registrar una compra: quedaban precios como `164.1885`.
- Anulación de compras, que no existía. Devuelve el stock y **restaura el precio de costo
  anterior**, que se guarda en el detalle. Falla si ya se vendió parte de lo que entró.
- Detección de imagen por magic bytes también en la foto de producto (antes confiaba en el
  Content-Type del cliente), con HEIC/HEIF para los iPhone.

### Números

| Dato | Antes | Después |
|---|---|---|
| Tests backend | 71 | **135**, verdes y estables |
| Errores de typecheck | 10 | **0** |
| Test de concurrencia | fallaba 1 de 5 | 8 de 8 |

### Lo que quedó pendiente

1. **La auditoría visual de UX y responsive no se hizo.** Agent Browser no funcionó en esta
   máquina (arranques de 2-3 minutos y cuelgues al navegar). No hay capturas en ningún
   breakpoint. **Es lo que falta de la revisión, no está cubierto.**
2. **El asistente de IA nunca se probó contra Gemini.** Falta `GEMINI_API_KEY`. El código está
   verificado hasta el `503` de "no configurado"; los diez escenarios de prueba no se
   ejecutaron. `gemini-3.6-flash` sí existe y está vigente (hay uno más nuevo, 3.7).
3. **Reempaquetar.** El `setup.exe` es anterior a todo esto.
4. **Decisión pendiente**: si el acceso desde la LAN se mantiene. Es lo que permite consultar
   stock desde el celular, y ya no es la puerta abierta que era, pero conviene decidirlo a
   propósito y no por omisión.

### Una advertencia para la próxima sesión

Las primeras pruebas contra `empaquetado/entrada/*.jar` daban 403 en el endpoint de IA y
parecía un problema de seguridad. **No lo era: ese jar es del 30/08 y no contiene las clases
nuevas.** Si algo responde 403 o 404 sin explicación, verificar primero que el jar esté
recompilado antes de buscar la causa en el código.

---

## 12. Migraciones de esquema con Flyway — 01/09/2026

La base de Martín llevaba desde el 01/09 sin poder arrancar. El motivo no era un
bug de código: era `ddl-auto=update`.

### Qué pasó

`Usuario.debeCambiarPassword` es un `boolean` primitivo, o sea columna `NOT NULL`
sin default. Con `ddl-auto=update`, Hibernate intentó agregarla a una tabla
`usuario` que ya tenía cuatro filas y H2 lo rechazó. Lo importante es lo que pasó
después: Hibernate **siguió con el resto del esquema**. Cuando se revisó la base,
`venta` y `compra` ya tenían `anulada_por`, `usuario`, `estado` y
`fecha_anulacion`, `detalle_compra` tenía `precio_costo_anterior` — todo lo del
01/09 aplicado, **menos esa columna**. La base quedó en un estado intermedio que
no figura en ningún lado, y la aplicación murió al primer `select` sobre
`usuario`.

Eso con una sola PC y el desarrollador mirando. Con actualizaciones automáticas,
el mismo accidente ocurre en la PC del negocio, un martes, sin nadie que sepa
leer un stack trace.

### Cómo quedó

- `ddl-auto=validate`: Hibernate ya no toca el esquema. Compara el modelo contra
  la base y **se niega a arrancar si no coinciden**, en vez de improvisar.
- El esquema lo maneja Flyway, en `src/main/resources/db/migration/`.
- `V1__esquema_base.sql` es idempotente a propósito (`IF NOT EXISTS` en todo).
  No es adorno: cuando Flyway entra en un proyecto que venía de `ddl-auto`, las
  bases que andan dando vueltas no están todas en el mismo estado. V1 lleva las
  tres que existen de verdad al mismo lugar — una base nueva, la de Martín a
  medio migrar, y cualquier copia anterior al 01/09.
- `baseline-on-migrate=true` con `baseline-version=0`, que es lo que hace que V1
  corra también sobre las bases que ya existen.

**De acá en adelante, cada cambio de esquema es un `V2`, `V3`… nuevo. Nunca se
edita una migración ya aplicada**: Flyway guarda su checksum y se niega a
arrancar si cambió. Esa es exactamente la red que faltaba.

### Un hallazgo del camino

Los 135 tests corrían con `create-drop`, que es lo que Spring Boot le pone por
defecto a una base embebida cuando nadie dice otra cosa — el
`application.properties` de tests tapa al de producción. O sea: **la suite armaba
el esquema desde las entidades y lo tiraba al terminar, así que ninguna migración
se ejercitaba nunca**. Ahora los tests corren con `validate` + Flyway, igual que
la PC del negocio: cada corrida de la suite pasa por las mismas migraciones.

Y un segundo hallazgo, más callado: Spring Boot 4 partió las autoconfiguraciones
en módulos por tecnología. Con `flyway-core` en el classpath pero sin
`spring-boot-flyway`, **la aplicación levanta como si Flyway no existiera y las
migraciones no corren, sin dar ningún error**. Eso es lo peligroso.

### Verificación

Contra una **copia** de la base real de Martín, no contra una de laboratorio:

| | Antes | Después |
|---|---|---|
| Productos | 31 | 31 |
| Categorías | 13 | 13 |
| Ventas / Compras | 3 / 1 | 3 / 1 |
| Usuarios | 4 | 1 |
| Tests backend | 135 | **160** |

Los tres usuarios que faltan son `juan`, `maria` y `carlos`: los borró
`limpiarCuentasDeEjemplo` porque seguían con `empleado123`. Es la protección del
01/09 funcionando, no pérdida de datos. El `admin` quedó intacto, con su
contraseña, y marcado para cambiarla porque todavía usa `admin123`.

Hay dos tests que parten de una base vieja de verdad
(`MigracionBaseDeMartinTest`, `MigracionEsquemaViejoTest`). No fallan en un
assert: fallan al construir el contexto, igual que fallaba `iniciar.bat`.

---

## 13. Actualización automática — 01/09/2026

Pedido de Martín: *"que siempre que nosotros hagamos un cambio, el launcher se
actualice… o que la app le diga al otro usuario, actualización, tocá para
actualizar, y mande al launcher"*.

### Qué se actualiza: solo el jar

El dato que ordena todo el diseño: **Vite compila el frontend adentro del jar**.
Un jar nuevo es la aplicación entera nueva — backend y pantallas. Así que la
actualización de todos los días son 66 MB y un reemplazo de archivo, no los
188 MB del instalador con su asistente, su UAC y su cartel de "editor
desconocido" de SmartScreen.

El `setup.exe` queda para dos cosas: instalar por primera vez, y cuando cambie
Electron o el motor de Java.

### Quién hace qué

```
launcher (Electron)   consulta GitHub, descarga, verifica SHA-256, reemplaza el jar
      │ escribe estado.json
      ▼
servidor   GET /actualizaciones          lee ese archivo — no sale a internet
      │    POST /actualizaciones/aplicar deja una señal y se apaga (solo ADMIN)
      ▼
launcher  ve que el servidor murió + la señal → cambia el jar → lo levanta de nuevo
```

**El servidor no sale a internet, y es a propósito.** Es el proceso que atiende
la caja y está expuesto a la red del local: no tiene por qué aprender a bajar
archivos. El launcher ya es dueño de los archivos del programa y de su ciclo de
vida, que es justo lo que hace falta para reemplazar un jar y reiniciar.

Los archivos del reparto, en la carpeta de datos:

```
%LOCALAPPDATA%\DespensaFamily\actualizaciones\
├── estado.json      lo escribe el launcher; el servidor lo lee para el aviso
├── aplicar          lo escribe el servidor cuando el dueño aprueba
├── instalada.txt    qué versión de jar está puesta ahora
└── pendiente\       el jar bajado y verificado, esperando el reinicio
```

### Decisiones que no son obvias

**El aviso sale de comparar versiones, no de `estado.json`.** Ese archivo
sobrevive al reinicio: si el cartel se guiara por él, quedaría prendido para
siempre después de actualizar.

**`instalada.txt` manda sobre la versión de Electron.** Después de la primera
actualización de solo-jar las dos dejan de coincidir: el jar pasa a 1.2.0 y el
Electron sigue en el 1.1.0 que puso el `setup.exe`.

**El empleado ve el aviso pero no puede aplicarlo.** Si la caja se va a
reiniciar, quien está atendiendo tiene que poder entender por qué. Pero aplicarlo
apaga el servidor, y esa decisión no se toma en el medio de una venta.

**Hay un endpoint que apaga el servidor**, que es justo lo que en la sección 10
se había evitado a propósito. Lo que lo hace aceptable son tres condiciones
juntas: pide ADMIN, **solo funciona si hay un jar descargado y verificado
esperando** (así que no sirve como apagado genérico), y el launcher lo vuelve a
levantar enseguida.

**El jar viejo se guarda como `.anterior`.** Si la versión nueva resulta estar
rota, hay a qué volver sin bajar nada.

**La versión vive en un solo lugar**, el `pom.xml`, y viaja adentro del jar
(`build-info`), no en su nombre. El jar pasó a llamarse `DespensaFamily.jar`,
fijo: el `.cfg` que genera jpackage lo apunta por nombre, así que un nombre con
la versión adentro obligaría a reescribir ese archivo en cada actualización.

### Integridad, y qué NO cubre

Siguiendo CRI-01 de la biblioteca de seguridad: para **integridad de una
descarga** va SHA-256. El launcher baja el jar y su `.sha256` de la misma
release, compara, y **recién después** mueve el archivo a su lugar. Se vuelve a
verificar en el arranque, porque entre la descarga y el reinicio el jar estuvo en
disco. Si no coincide, se descarta y no se instala nada. Además: solo HTTPS, solo
hosts de GitHub (los redirects vuelven a pasar por esa lista), tope de tamaño y
timeouts.

**Lo que esto NO cubre es autoría.** Si alguien se queda con la cuenta de GitHub,
publica un jar y su hash, y los dos verifican bien. El ancla de confianza real es
HTTPS más la seguridad de la cuenta del repositorio — que además es público.
Cerrar eso pide una firma (Ed25519, con la clave pública dentro del launcher);
el formato de `estado.json` está pensado para poder agregarla sin romper nada.
**Es una decisión pendiente, no un olvido.**

### Cómo se publica una versión

```
1. Subir la versión en DespensaFamily\pom.xml   (1.1.0 -> 1.2.0)
2. publicar.bat "Qué trae esta versión"
```

Corre los tests (si fallan no publica nada), compila front + jar, calcula el
SHA-256 y publica la release con `gh`. Si `gh` no está instalado deja los dos
archivos en `empaquetado\publicacion\` y explica qué subir a mano.

Si a una release le falta alguno de los dos archivos, el launcher **ignora la
release entera**: sin el hash no puede verificar, y sin verificar no instala.

### Verificado

Contra la aplicación corriendo, con usuarios reales y JWT reales:

| Caso | Resultado |
|---|---|
| Sin launcher (jar suelto) | `gestionadoPorLauncher: false`, no se ofrece nada |
| Sin token | 403 en las dos rutas |
| Empleado real, `GET /actualizaciones` | 200 — ve el aviso |
| Empleado real, `POST .../aplicar` | **403** |
| Admin, con la descarga a medias | **409**, no deja señal |
| Admin, con la descarga lista | 202, señal `1.2.0`, servidor apagado con exit 0 |

Y el reemplazo del jar, con archivos reales (`node --test`): reemplaza y guarda
el anterior; **un jar que no coincide con su hash no se instala** y el viejo
queda intacto; sin señal no toca nada; señal sin jar descargado no rompe el
arranque.

Totales: **160 tests backend**, **11 del launcher**, **0 errores de typecheck**.

### El bucle completo, corrido de verdad

Con Electron levantado (`npm start`) contra una carpeta de datos aparte en el
puerto 8095, un motor de jpackage en 1.1.0 y un jar 1.2.0 **compilado de
verdad** esperando en `pendiente\` con su SHA-256:

| Paso | Resultado |
|---|---|
| El launcher arranca el servidor | responde en 8095 |
| `GET /actualizaciones` | `versionInstalada 1.1.0`, `versionDisponible 1.2.0`, `listaParaAplicar true` |
| `POST .../aplicar` | 202 |
| El servidor baja, el launcher cambia el jar y lo vuelve a levantar | **arriba de nuevo a los ~10 s** |
| `GET /actualizaciones` después | **`versionInstalada 1.2.0`, `hayActualizacion false`** |
| El jar en disco | 1.2.0, con el 1.1.0 guardado como `.anterior` |
| `instalada.txt` / `estado.json` | `1.2.0` / `al-dia` |
| Señal `aplicar` y jar pendiente | consumidos los dos |

**La prueba negativa, que es la que importa.** Se puso en `pendiente\` un jar
cuyo contenido no corresponde al hash declarado — lo que se vería si alguien
cambiara el archivo entre la descarga y el reinicio:

```
hash declarado: a2e8737533c7db19...
hash real:      03de04580d985680...
→ [actualizaciones] El jar pendiente no coincide con su hash: se descarto
→ VERSION QUE QUEDO CORRIENDO: 1.2.0   (no se instaló nada)
```

Y de yapa, del log del propio launcher: `No se pudo consultar: Respuesta 404`.
Esa es la consulta real a `api.github.com` a los 60 segundos del arranque —
llegó por HTTPS, pasó la lista de hosts permitidos y manejó "todavía no hay
ninguna release" sin voltear nada.

### Lo que falta

1. **La descarga desde una release no se ejercitó.** La consulta a la API sí
   (404 manejado correctamente), pero bajar el jar y su `.sha256` de una release
   real no, porque no hay ninguna publicada y `gh` no está instalado acá. Todo
   lo que viene después de la descarga sí está probado con archivos reales.
2. **El aviso no se vio con los ojos.** Se verificó que el código viaja dentro
   del jar y que el endpoint devuelve exactamente lo que la cinta necesita, pero
   no hay captura de la cinta en pantalla. Sigue pendiente la auditoría visual
   de la sección 11.
3. **Reempaquetar el `setup.exe`**, que ahora además es lo que instala el
   launcher con capacidad de actualizarse. Sin eso, nada de esto llega al
   negocio.

---

## 14. La sesión pasó a una cookie HttpOnly — 01/09/2026

Lo encontró Martín mirando la app: *"pero no se ven las imágenes"*.

### El bug

La auditoría del 01/09 dejó `/imagenes/**` como `authenticated()`, y el criterio
era bueno: antes cualquiera en el wifi del local podía recorrer el catálogo de
fotos sin loguearse.

Lo que no se vio es que **el token vivía en `localStorage`**. Una etiqueta
`<img src="/imagenes/foto.jpg">` no puede mandar una cabecera `Authorization` —
el navegador pide la imagen y se come un 403. Desde el 01/09 **no se veía
ninguna foto de producto**, ni en Productos ni en la pantalla de Ventas
(`productos.ts:223`, `ventas.ts:211`).

Medido antes del arreglo: sin token 403, con token 200. Las 31 fotos estaban
intactas en disco todo el tiempo.

Vale la pena nombrar el patrón, porque va a volver a pasar: **un control de
acceso correcto puede romper una funcionalidad visible, y los tests no lo
notaron porque ninguno pedía una imagen como la pide un navegador.** La
auditoría cerró un agujero real y apagó las fotos del negocio en el mismo
commit.

### El arreglo

La sesión pasó a una cookie `HttpOnly`, que es lo que FE-02 recomienda para una
aplicación de navegador. El navegador la manda sola en cada pedido del dominio
— imágenes incluidas — y ningún script de la página puede leerla.

Arregla las fotos sin tocar una línea de HTML, y de paso cierra lo que la propia
auditoría había marcado y no había podido resolver: con el token en
`localStorage`, cualquier XSS era robo de sesión directo.

| | Antes | Ahora |
|---|---|---|
| Dónde vive la sesión | `localStorage` | cookie `HttpOnly` |
| La lee un script inyectado | sí | no |
| Va en `<img>`, `<a download>`, etc. | no | sí |
| El token en el cuerpo del login | sí | `@JsonIgnore` |
| Cerrar sesión | borraba el `localStorage` | lo apaga el servidor |

### Lo que costó, y cómo se pagó

Una cookie la manda el navegador **sola**, también cuando quien dispara el
pedido es otro sitio. Eso es CSRF, y no existía mientras la sesión iba en una
cabecera (SESS-10). Van dos defensas:

- `SameSite=Strict` — lo frena en el navegador. Strict y no Lax porque no hay
  ningún flujo que aterrice logueado desde afuera.
- `FiltroDeOrigen` — lo frena en el servidor, que es donde no depende de que el
  navegador se porte bien.

Dos decisiones del filtro que parecen agujeros y no lo son: **los GET no se
controlan** (no cambian nada, y CORS ya rechaza el origen ajeno igual — se
verificó: da 403), y **si no viene cabecera `Origin` se deja pasar**, porque
curl y los scripts no la mandan, mientras que un navegador la manda *siempre*
que el pedido salga de otro sitio, que es justo el caso a frenar.

### Detalles que no son obvios

**`Secure` sale de `request.isSecure()`, no de una constante.** La app corre en
HTTP sobre la red del local: con `Secure` fijo en `true` el navegador nunca
mandaría la cookie y no entraría nadie. Si algún día se le pone HTTPS adelante,
se activa solo.

**El filtro JWT sigue aceptando la cabecera `Authorization`**, después de la
cookie. Es para curl, scripts y pruebas; no debilita nada, porque un XSS no
puede leer una cookie `HttpOnly` y por lo tanto tampoco puede armar la cabecera.
Lo que sí sería un error es que el *frontend* guardara el token en los dos
lados, y por eso el login ya no lo devuelve en el cuerpo.

**`credentials: "include"` se puso envolviendo el `fetch` global**, no en los
doce servicios. En la app instalada front y back comparten origen y la cookie
viajaría igual, pero si desarrollo y producción se comportan distinto los bugs
aparecen en uno solo.

**`clearAuth()` se volvió `async` y hay que esperarla.** Si se navega antes de
que termine, el navegador cancela el pedido de logout y la sesión queda viva del
otro lado. El typecheck no lo marca: una promesa suelta es válida.

### Verificado, con la app corriendo

| Caso | Resultado |
|---|---|
| Login | la cookie sale `#HttpOnly_`, y el token **no** está en el JSON |
| Foto solo con la cookie (como un `<img>`) | **200**, 4520 bytes, `image/jpeg` |
| Foto sin cookie | 403 |
| Las 31 fotos del catálogo | **12 de 12 con URL cargan, 0 fallan** |
| `GET /productos` solo con cookie | 200 |
| `POST` con `Origin` ajeno | **403** |
| `POST` con el `Origin` propio | pasa el control |
| Cerrar sesión, y pedir una foto después | 200 y luego **403** |

**170 tests backend** (10 nuevos en `SesionEnCookieTest`), 0 errores de
typecheck.

### Lo que falta

- **No se miró con los ojos.** Se verificó que las fotos vuelven con 200 y el
  `content-type` correcto, pero la auditoría visual de la sección 11 sigue
  pendiente.
- **Sin HTTPS, la cookie viaja en claro por la red del local** — igual que
  viajaba el `Authorization` antes, así que no es una regresión, pero tampoco
  quedó resuelto. Es el mismo pendiente que la decisión sobre el acceso desde
  la LAN (sección 11).

---

## 15. Reempaquetado 1.1.0, y la versión vieja rompiendo la base — 01/09/2026

`DespensaFamily-1.1.0-setup.exe`, 189 MB. Adentro viaja `DespensaFamily.jar`
con el nombre fijo, que es lo que permite actualizar sin reinstalar.

### Dos bugs que sólo aparecieron al correr el paquete de verdad

Los dos habrían llegado al negocio. Ninguno lo detectaban los tests.

**1. El launcher empaquetado no arrancaba.** `package.json` de Electron lista
explícitamente qué archivos entran en el `app.asar`:

```json
"files": ["main.js", "cargando.html"]
```

Agregué `actualizaciones.js` y no lo agregué ahí. En desarrollo (`npm start`)
funciona, porque lee del disco. Empaquetado, `require("./actualizaciones")`
tiraba MODULE_NOT_FOUND — y como una app de Electron empaquetada corre sin
consola, **moría en silencio**: la ventana quedaba en la pantalla de carga y el
servidor no arrancaba nunca. Cero mensajes, en ningún log.

Es el tipo de bug que sólo se ve corriendo el `.exe` armado. `npm start` no lo
muestra, y los 171 tests tampoco.

**2. La versión de agosto rompe la base de la versión nueva.** Del log del
propio servidor, que registra todos los arranques:

```
13:00:02  v1.1.0           target\DespensaFamily.jar        migró bien
13:27:47  v1.1.0           target\DespensaFamily.jar        andaba
13:42:19  v0.0.1-SNAPSHOT  Programs\DespensaFamily\...      ← la de AGOSTO
14:08:17  v1.1.0           dist\win-unpacked\...            ya no arranca
```

A las 13:42 arrancó la **instalación de agosto**, que sigue en
`%LOCALAPPDATA%\Programs\DespensaFamily\` con su
`DespensaFamily-0.0.1-SNAPSHOT.jar`. Esa versión tiene `ddl-auto=update` y
`fechaHora` como `LocalDate`, así que Hibernate "corrigió" el esquema a su
propio modelo y **bajó `venta.fecha_hora` y `compra.fecha_hora` de
`TIMESTAMP(6)` a `DATE`**. La versión nueva dejó de arrancar con
*"wrong column type encountered in column [fecha_hora] in table [compra]"*.

Se descartó que fuera la migración: la sentencia de V1 se probó aislada sobre
una base de juguete y deja la columna en `TIMESTAMP(6)`, y el dump de la copia
migrada también.

`V2__fecha_hora_a_timestamp.sql` repara la base. Pero **repara una sola vez**:
Flyway no vuelve a correr una migración aplicada. Si la versión de agosto se
abre otra vez, la base vuelve a romperse y V2 ya no va a estar ahí para
arreglarla.

> **Lo único que lo resuelve de verdad es que no queden dos versiones
> conviviendo.** El `setup.exe` 1.1.0 se instala ENCIMA del de agosto (mismo
> AppId, ver sección 4), así que instalarlo elimina el problema. Hasta que eso
> pase, abrir el ícono viejo del escritorio deja la caja sin sistema.

Es, además, el mejor argumento a favor de la actualización automática de la
sección 13: el problema de fondo no es esta columna, es que una PC pueda quedar
corriendo una versión vieja contra datos nuevos.

### El test que faltaba

`MigracionEsquemaViejoTest` comprobaba que la fecha se conservara, mirando el
**valor** y sólo en `venta`. Eso no habría detectado nada de esto: una columna
`DATE` con la fecha correcta igual impide arrancar. Ahora se comprueba el
**tipo** de la columna, en `venta` y en `compra`, que es lo único que mira
`ddl-auto=validate`.

### Verificado con el paquete corriendo

| | |
|---|---|
| Servidor arriba | **8 s** |
| Login / foto / productos / categorías / ventas | 200 · 200 · 16 · 13 · 3 |
| Procesos | ventana (Electron) + motor (jpackage), como manda la sección 10 |

**171 tests backend · 11 del launcher · 0 errores de typecheck.**

### Pendiente

- **Instalar el 1.1.0** para que desaparezca la instalación de agosto. Es lo
  único que cierra el problema de las dos versiones.
- Sigue sin firma digital: SmartScreen va a mostrar "editor desconocido".
- El `DespensaFamily-1.0.0-setup.exe` viejo sigue en `empaquetado\`. Conviene
  borrarlo para no instalarlo por error.

---

## 16. Caja, ingresos, egresos y rentabilidad — 01/09/2026

Implementación completa de las 9 etapas del mapa de cambios (sección previa de
este documento), en orden, cada una con tests antes de avanzar a la
siguiente. Migraciones V4 a V8.

### Lo que se construyó

**Etapa 1 — los tres defectos de los reportes**, corregidos con test que
congela cada uno: la ganancia por fecha ignoraba las promociones (usaba
`precioUnitario` de lista en vez de `subtotal`), las compras anuladas seguían
en el listado de movimientos, y `Compra.total` se recalculaba en vez de
congelarse (V3, con backfill).

**Etapa 2 — el libro de movimientos** (`MovimientoCaja`, V4). Un movimiento
nunca se edita ni se borra: se revierte con otro de signo contrario que
apunta al original (`revierteA`). `CajaService` calcula saldo total, efectivo
físico y electrónico por separado.

**Etapa 3 — venta con método de pago** (V5). El ingreso se crea **dentro de
la misma transacción** que la venta: si el movimiento falla, la venta
tampoco se guarda. Probado con un fallo real (no simulado): pasar
`metodoPago=null` viola el `NOT NULL` de la base al hacer commit, y la venta
completa —incluido el descuento de stock— se revierte.

**Etapa 4 — pantalla de Caja** (`/caja`): saldo grande, efectivo vs.
electrónico, ingresos/egresos de hoy, últimos movimientos, y el modal para
cargar gasto/retiro/aporte/ajuste (solo ADMIN).

**Etapa 5 — compra pagada o a cuenta** (V6). Comprar a cuenta entra stock sin
tocar caja; `POST /compras/{id}/pagar` genera el egreso recién en ese
momento, con la fecha del pago y no la de la compra.

**Etapa 6 — historial con filtros + rentabilidad**. `/reportes/rentabilidad`
separa ventas, costo, ganancia bruta, margen, compras (no es un costo del
período) y gastos operativos (solo categoría GASTO — RETIRO y APORTE son
movimientos de capital, no entran). Si nadie cargó gastos en el período, la
pantalla avisa en vez de mostrar el resultado neto como si fuera un dato
real.

**Etapa 7 — arqueo**: contar el cajón genera un movimiento AJUSTE con la
diferencia contra lo esperado, sin apertura ni cierre de sesión (decisión
explícita, ver sección 6 más abajo). Si da exacto, no se crea ningún
movimiento.

**Etapa 8 — empleados** (V8, alcance agregado después del mapa original).
`Empleado` no es `Usuario`: puede existir sin cuenta de acceso, y borrar una
cuenta no borra su historial de pagos. `DiaTrabajado` es una fila por día
trabajado, no un booleano — la grilla semanal es la vista, no el dato. Un
pago es un egreso con categoría `SUELDO` propia (no se mezcla con `GASTO`) y
monto libre sin período fijo, por decisión del dueño.

**Etapa 9 — cliente opcional en la venta** (V7). Se reconectó una línea
"Cliente:" que ya existía en el ticket pero era código muerto —referenciaba
un `<select id="select-cliente">` que ya no estaba en el HTML, así que
siempre imprimía "Consumidor Final" fijo—.

### Un bug real que encontró la suite completa, no el test aislado

Al agregar overloads como `registrarVenta(detalles, metodoPago)` que delegan
a `registrarVenta(detalles, metodoPago, cliente)`, la llamada interna es una
**auto-invocación dentro del mismo bean**: el proxy de Spring que hace
funcionar `@Transactional` no intercepta llamadas de un método a otro dentro
de la misma clase. El método delegado corría sin transacción activa.

No lo detectó `VentaConCajaTest` corrido solo —pasaba, porque el mock de
Spring cachea el contexto y algo lo enmascaraba en ese subconjunto—, lo
detectó la suite completa: `mvnw test` sin filtro. Se corrigió agregando
`@Transactional` a **todos** los overloads que delegan, no solo al que hace
el trabajo real, en `VentaService` y `CajaService`. Motivo de más para correr
siempre la suite entera antes de dar algo por terminado, no solo la clase que
se acaba de tocar.

### Verificación

**213 tests backend** (0 fallidos), verificados dos veces: una vez por clase
recién escrita, después con la suite completa — que fue la que atrapó el bug
de arriba.

Cada etapa se probó además contra una instancia real corriendo (jar
compilado, HTTP real, JWT real), en una carpeta de datos descartable para no
tocar la base real hasta el final:

| Etapa | Verificado |
|---|---|
| 3 | Venta $10.000 → caja +$10.000; con tarjeta → electrónico +$10.000, efectivo sin cambios; anular → caja vuelve a 0, quedan las dos filas |
| 5 | Compra a cuenta → caja sin cambios; marcar pagada → caja −$1.000 |
| 6 | Venta $2.000/costo $1.200 + gasto $300 → ganancia bruta $800, margen 40%, neto $500 |
| 7 | Contado $8.500 vs. esperado $10.000 → ajuste EGRESO $1.500; exacto → sin movimiento |
| 8 | Empleado sin usuario, día marcado dos veces → una sola fila, pago $30.000 → caja −$30.000 y **no** cuenta como gasto operativo en rentabilidad |
| 9 | Venta con `cliente:"Maria Perez"` → aparece en la respuesta y en el bundle del ticket |

Al final, las 8 migraciones (V1 a V8) corrieron limpio sobre la base real de
Martín sin perder datos, y las tres pantallas nuevas responden 200 en la app
empaquetada.

### Lo que no se pudo verificar

**El aspecto visual.** Agent Browser se probó de nuevo a pedido explícito
—se sospechaba que el problema anterior era por tratarse de una app de
escritorio— pero **se cuelga al abrir el navegador, con y sin URL, con y sin
`--headed`**: no es un problema de la app, es el lanzamiento del navegador en
esta máquina. La verificación de estas pantallas fue por API y por contenido
del bundle servido (`grep` sobre el JavaScript compilado), no por ojos.
Ninguna de las pantallas nuevas (Caja, Empleados, los bloques de Rentabilidad
y el selector de método de pago) tiene una captura real.

### Pendiente

1. **Auditoría visual completa**, arrastrada desde la sección 11 y ahora más
   grande: se suman Caja, Empleados y los cambios de Ventas/Compras/Reportes
   a lo que ya faltaba revisar.
2. **Reempaquetar el `setup.exe`** — el jar del paquete instalado ya tiene
   todo esto, pero el instalador en `empaquetado\*.exe` sigue siendo el de la
   sección 15.
3. **Un campo pendiente de decisión**, ya anotado en la sección de empleados:
   si hace falta un "monto acordado" opcional para poder mostrar el
   porcentaje pagado que se pidió originalmente.

## 17. Clave de IA y desinstalación — 24/09/2026

### Qué se agregó al `.iss`

- **Página opcional "Asistente de IA (opcional)"**, después de la de tareas
  (`wpSelectTasks`). Un campo de contraseña (enmascarado) para la clave de
  Gemini, con el texto explicando para qué sirve (cargar productos desde una
  foto o un texto), dónde se saca gratis
  (`https://aistudio.google.com/apikey`) y que se puede dejar vacío. Si ya
  hay una clave de una instalación anterior, el texto lo avisa y sugiere
  dejar el campo vacío para conservarla.
- Al terminar la instalación (`ssPostInstall`), si el campo no está vacío,
  `GuardarClaveIA` edita `config.properties` como texto —igual que hace
  `SecretoJwt.java` del lado de Java— agregando o reemplazando solo la línea
  `despensa.ia.gemini.api-key=...`, sin tocar ninguna otra línea (en
  particular, sin tocar nunca `jwt.secret`, que es lo que no se puede perder).
  Si el campo queda vacío no se toca el archivo: una actualización conserva
  la clave que ya estaba, y una instalación nueva sin IA no crea la línea.
- Validación mínima en `NextButtonClick`: recorta espacios, rechaza espacios
  o saltos de línea internos (para no poder inyectar otra propiedad en el
  archivo), y avisa —sin bloquear— si la clave no empieza con `AIza`.
- **La clave nunca se loguea ni se muestra**: no hay `Log()` ni `MsgBox` con
  su valor en ningún punto del script, y no se pasa por línea de comandos ni
  por variable de entorno de ningún proceso hijo.
- **Por qué nunca va en el `.iss` ni en el setup.exe**: es una credencial de
  clase 4/5 (SEC-01 de `11-gestion-de-secretos.md`) — da acceso a la cuota de
  la cuenta de Gemini de quien la cargó. El repositorio es público, y
  cualquiera puede descompilar un `.exe` de Inno Setup con `innounp`; si la
  clave viajara embebida en el instalador, viajaría con él. Por eso se pide
  una vez por instalación, en memoria, y se escribe únicamente en
  `%LOCALAPPDATA%\DespensaFamily\config.properties`, que ya está fuera del
  repositorio y fuera de lo que pisa una actualización.

### Cerrar la app antes de instalar o desinstalar

`DespensaFamily.exe` y `DespensaFamilyServidor.exe` dejan sus archivos
trabados si siguen corriendo. `PrepareToInstall` (antes de copiar archivos) y
`InitializeUninstall` (antes de desinstalar) llaman a `CerrarAppSiHaceFalta`:

- Detecta si alguno de los dos procesos está corriendo con `tasklist`
  redirigido a un archivo temporal (`Exec` no da acceso directo a stdout).
- Si no hay nada corriendo, sigue sin preguntar nada.
- Si hay algo corriendo y el instalador no es silencioso
  (`WizardSilent`/`UninstallSilent`), pregunta con un `MsgBox` de
  Aceptar/Cancelar; si cancela, se aborta sin error feo (`PrepareToInstall`
  devuelve un mensaje propio, `InitializeUninstall` devuelve `False`). En
  modo silencioso cierra directo, sin preguntar.
- Cierra primero **sin** `/F` (le da tiempo a Electron a avisarle al servidor
  Java que cierre la base en orden), espera 3 segundos, y solo si algo sigue
  vivo repite con `/F`.

### La desinstalación conserva los datos siempre

Sin cambios en `[UninstallDelete]` — sigue sin existir, a propósito. Se
agregó únicamente un aviso al final (`usPostUninstall`, y solo si
`UninstallSilent()` es falso) que dice dónde quedaron los datos
(`%LOCALAPPDATA%\DespensaFamily`) y que reinstalar los hace reaparecer.

### Versión en un solo lugar

`#define Version` ahora es `#ifndef Version / #define Version "0.0.0-dev" /
#endif`, y `empaquetar.bat` le pasa `/DVersion=!VERSION!` a `ISCC.exe` (la
versión sigue saliendo del `pom.xml`, sin cambios ahí). El nombre de salida
sigue siendo `DespensaFamily-<version>-setup.exe`.

### Verificación

Compilado con `ISCC.exe /DVersion=1.1.0 DespensaFamily.iss` contra el
`dist\win-unpacked` real que ya estaba armado en esta máquina: compiló limpio
(un solo error de tipeo corregido en el camino: `TInputQueryPage` no existe,
es `TInputQueryWizardPage`) y generó
`empaquetado\DespensaFamily-1.1.0-setup.exe`. No se instaló ese setup ni se
tocó `%LOCALAPPDATA%\DespensaFamily` real. No se probó a mano el flujo
completo del wizard (la página de IA, el cierre de procesos corriendo, ni el
aviso de desinstalación) porque compilar no es lo mismo que correr el
instalador — queda para cuando el orquestador corra `empaquetar.bat` y se
pruebe la instalación real.
