# Plan: DespensaFamily de escritorio (instalable en PC)

> Estado: **F0 a F5 hechas** (F5 parcial por decisión) y **DespensaFamily-1.0.0-setup.exe generado**.
> Probado en la PC de Martín el 29/08/2026; los tres problemas que aparecieron están arreglados.
> Todo commiteado en la rama `instalador-escritorio` (6 commits sobre `f198e3f`), sin pushear ni mergear.
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
| Artefactos | 1 (`DespensaFamily-0.0.1-SNAPSHOT.jar`, ~63 MB) |
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
