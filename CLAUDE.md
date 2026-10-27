# DespensaFamily — instrucciones del proyecto

Esto complementa (no reemplaza) las instrucciones globales del usuario. Lo de acá es específico de
este repo.

## Stack

- Backend: Spring Boot 3, Java 17, Maven (`./mvnw.cmd`).
- Base de datos: **H2 en archivo**, no MySQL (el bloque MySQL que queda comentado al final de
  `application.properties` es solo para volver atrás si hiciera falta, no está en uso).
- Esquema: **Flyway** (`src/main/resources/db/migration`). `spring.jpa.hibernate.ddl-auto=validate`
  — Hibernate NUNCA toca el esquema, solo verifica que coincida con las entidades y se niega a
  arrancar si no coincide.
- Frontend: Vite + TypeScript, sin framework. Compila adentro del jar del backend.
- Auth: JWT propio (`JwtAuthFilter` contra `UsuarioRepository`), no `UserDetailsService` de Spring.
- Empaquetado: Electron + JDK embebido para el instalador de escritorio (`empaquetado/`).

## Regla dura: Flyway

**Cada cambio de modelo (entidad JPA) es una migración `Vn__algo.sql` NUEVA.** Nunca editar una
migración ya aplicada — ni siquiera en desarrollo. Motivo real, no teórico: el 01/09/2026 un
cambio de esquema aplicado a medias (columna `NOT NULL` sin default sobre una tabla con filas) dejó
la base de la PC del negocio a mitad de camino y la app dejó de arrancar. Con `ddl-auto=validate`
y Flyway como única fuente de verdad del esquema, ese escenario está cerrado: si el modelo cambia,
el número de versión sube, nunca se reescribe uno viejo.

`spring.flyway.baseline-on-migrate=true` + `baseline-version=0` existen para que V1 también corra
sobre bases que ya existían de antes (de cuando el esquema lo manejaba `ddl-auto=update`); V1 es
idempotente a propósito.

## Puertos

- Backend: **8080**.
- Frontend dev server (Vite): **5173**. En esta máquina puede estar ocupado por otro proyecto
  (Fondo Familiar) — si el 5173 no responde o responde con otra cosa, no es el firewall, es que hay
  otro proceso ahí. Probar la app completa desde el jar (8080) en ese caso.
- Puerto de prueba puntual usado en verificaciones manuales de seguridad/QA: **18103** (arrancar con
  `--server.port=18103 --despensa.escritorio=false` y `DESPENSA_HOME` a una copia temporal de la
  carpeta de datos, nunca la real).

## No pushear

Repo público. Commits solo locales hasta que Martín pida explícitamente lo contrario. No usar
`git push` sin que lo pida.

## Tests: no pisar las credenciales reales

`DataInitializer` escribe la contraseña inicial del admin en
`%LOCALAPPDATA%\DespensaFamily\db\credenciales-iniciales.txt`. Correr `./mvnw.cmd test` sin más
pisa ese archivo real con una contraseña de prueba que no sirve. Siempre exportar `DESPENSA_HOME` a
una carpeta temporal antes de correr la suite completa (ver README). La contraseña generada
**nunca** va al log ni a la consola (DF-SEC-004 / LOG-02) — solo al archivo.

## Catálogo real

`listaDeProductos/` contiene el Excel real del negocio (2183+ productos). No se commitea (está en
`.gitignore`). Se importa por API con un importador aparte; tratarlo como dato de producción, no
como fixture de test.

## La base local es de prueba

Se puede modificar y resetear sin pedir permiso — no es la base del negocio real.

## Seguridad

Antes de tocar cualquier archivo de auth, sesiones, JWT, permisos, endpoints, validación de
entrada, DB, subida de archivos, headers, secretos, criptografía o rate limiting: consultar
`C:\Users\marti\.claude\skills\seguridad-arquitectura\SKILL.md` y aplicar los controles P0/P1 que
correspondan a esa superficie (citar el ID del control, ej. `AUTN-03`, `LOG-02`). Es la norma del
usuario para todos sus proyectos, no algo específico de acá, pero se repite porque este proyecto
maneja plata y credenciales de un negocio real.

## Documentos de referencia de este repo

- `docs/reglas-de-precio.md` — única fuente de la regla de precio de venta (fuente de verdad en
  código: `VentaService`). No reimplementar el cálculo en el frontend.
- `PLAN-INSTALADOR.md` — estado del instalador de escritorio (rama `instalador-escritorio`, sin
  mergear).
