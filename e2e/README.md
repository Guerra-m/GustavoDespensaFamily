# E2E — flujo de venta (Playwright)

Suite end-to-end del flujo de venta de DespensaFamily. Cubre:

1. Venta con descuento por % — lo que muestra el carrito es lo que cobra el ticket.
2. Venta de un pesable — cantidad con 3 decimales, redondeo al gramo, total correcto.
3. Doble clic en "Confirmar Venta" — una sola venta.
4. Anulación de una venta — vuelve el stock y aparece como anulada.
5. Empleado sin `PERM_CAJA` — solo ve sus ventas del día, no ve costos ni Compras.

## Cómo correr

Desde la raíz del repo:

```bash
npm run e2e
```

o directo desde acá:

```bash
cd e2e
npm install    # una sola vez
npx playwright test
```

`global-setup.ts` hace todo el trabajo antes de que corra el primer test:

1. Compila el frontend (`npm run build` en `FrontDespensaFamily`, instalando sus dependencias si
   hace falta).
2. Arranca el backend (`mvnw spring-boot:run`) con una base H2 **nueva** en una carpeta temporal
   (`DESPENSA_HOME`), puerto **18106**, `--despensa.escritorio=false` y el JDK 17 del proyecto
   (`~/.jdks/ms-17.0.15`).
3. Espera a que conteste y a que `DataInitializer` escriba `credenciales-iniciales.txt` (el admin
   nace con una contraseña al azar y `debeCambiarPassword=true`).
4. Loguea como ese admin y le cambia la contraseña por la constante de la suite
   (`fixtures/env.ts`), para que cada spec se loguee sin leer ningún archivo.

Al terminar, mata el proceso del backend (todo el árbol, no solo `cmd.exe`) y borra la carpeta
temporal.

No depende del catálogo real ni de ninguna base existente: cada spec arma sus propios productos,
descuentos y usuarios por API (`fixtures/api.ts`), con nombres únicos por corrida.

## Puerto 18106

Ni 8080 (puede haber una instancia real corriendo en la PC) ni 5173 (Fondo Familiar, otro proyecto
de esta máquina — ver `CLAUDE.md`). El front compilado sirve desde el mismo origen que la API
(`VITE_API_URL` vacío en build de producción), así que un solo puerto alcanza para todo.

## Selectores

Se usan los ids y clases que ya tiene el HTML de `ventas.html` (roles/labels donde los hay,
`data-testid` no hizo falta agregar ninguno — la pantalla ya es suficientemente estable con lo que
tiene). No se tocó `ventas.ts` más allá de nada (no se tocó en absoluto): otra rama lo va a
refactorizar después.

## Bugs encontrados mientras se escribían los flujos

Ninguno bloqueante para estos 5 flujos. Ver el informe de la tarea para el detalle de lo que sí se
encontró y se dejó documentado (sin arreglar, según lo pedido).
