// Flujo 4: anulación de una venta -> vuelve el stock y aparece como anulada.
import { test, expect } from "@playwright/test";
import { login, irAVentas } from "../fixtures/ui.ts";
import { crearProducto, nombreUnico } from "../fixtures/api.ts";
import { ADMIN_USERNAME, ADMIN_PASSWORD, BASE_URL } from "../fixtures/env.ts";

test("anular una venta por la UI devuelve el stock y la marca ANULADA (verificado por API)", async ({ page, context }) => {
  await login(page, ADMIN_USERNAME, ADMIN_PASSWORD);

  const nombre = nombreUnico("Aceite Anulacion");
  const producto = await crearProducto(context.request, { nombre, precioVenta: 200, stock: 10 });

  await irAVentas(page);

  const card = page.locator(".producto-card", { hasText: nombre });
  await card.click();
  await card.click(); // 2 unidades

  await expect(page.locator(".carrito-item", { hasText: nombre })).toBeVisible();
  await expect(page.locator("#total")).not.toHaveText("Calculando…", { timeout: 5_000 });
  await expect(page.locator("#total")).not.toHaveText("$0,00");

  const [popup] = await Promise.all([
    page.waitForEvent("popup"),
    page.locator("#btn-confirmar").click(),
  ]);
  await popup.waitForLoadState();
  await popup.close();

  // Stock descontado: 10 - 2 = 8.
  const resProductoVendido = await context.request.get(`${BASE_URL}/productos/${producto.id}`);
  expect((await resProductoVendido.json()).stock).toBe(8);

  const ventas: Array<{ id: number }> = await (await context.request.get(`${BASE_URL}/ventas`)).json();
  const ventaId = Math.max(...ventas.map(v => v.id));

  // La lista todavía no tiene ninguna venta anulada en este punto (recién se
  // va a anular la de este test), así que abrir el modal acá SÍ renderiza
  // bien -- ver el test de abajo (marcado test.fail) para el bug real que
  // aparece al volver a listar DESPUÉS de que exista una anulada.
  await page.locator("#btn-ver-ventas-registradas").click();
  await expect(page.locator("#modal-ventas-registradas")).toBeVisible();

  const fila = page.locator(`#tabla-ventas tr:has(td:text-is("${ventaId}"))`);
  await expect(fila).toBeVisible();
  await fila.locator(".btn-anular").click();

  await expect(page.locator("#confirm-modal")).toBeVisible();
  await page.locator("#confirm-modal-aceptar").click();

  // No se afirma nada sobre cómo queda pintada la fila acá: el propio click
  // de "Aceptar" dispara un `cargarVentas()` que ahora sí tiene que dibujar
  // una fila anulada, y ESO es lo que revienta (ver el test de abajo). Lo que
  // importa para este test es lo que de verdad puede pasar (o no) en la
  // base: estado y stock, por API, no la pintura de esa recarga.
  await expect
    .poll(async () => {
      const venta = await (await context.request.get(`${BASE_URL}/ventas/${ventaId}`)).json();
      return venta.estado;
    }, { timeout: 5_000 })
    .toBe("ANULADA");

  const ventaAnulada = await (await context.request.get(`${BASE_URL}/ventas/${ventaId}`)).json();
  expect(ventaAnulada.anuladaPor).toBe(ADMIN_USERNAME);
  expect(ventaAnulada.fechaAnulacion).toBeTruthy();

  // El stock vuelve: 8 + 2 = 10, el original.
  const resProductoTrasAnular = await context.request.get(`${BASE_URL}/productos/${producto.id}`);
  expect((await resProductoTrasAnular.json()).stock).toBe(10);
});

// BUG REAL encontrado escribiendo este flujo (no se arregla, según lo
// pedido -- se deja documentado y marcado como fallo esperado).
//
// `cargarVentas()` en FrontDespensaFamily/src/pages/ventas/ventas.ts arma
// cada <tr> del historial así (fragmento real, ~línea 238-269):
//
//   const btnAnular = tr.querySelector(".btn-anular") as HTMLButtonElement | null;
//   btnAnular?.addEventListener(...)                     // OK: usa "?."
//
//   const btnImprimir = tr.querySelector(".btn-imprimir") as HTMLButtonElement;
//   btnImprimir.addEventListener(...)                    // BUG: sin "?."
//
// Para una venta ANULADA, la celda de "Acción" sólo tiene el <span
// class="etiqueta-anulada">, sin botones -- ver el template unas líneas
// arriba (if (anulada) ... else `<button class="btn-imprimir">...`). Eso
// hace que `tr.querySelector(".btn-imprimir")` sea `null`, y la línea
// siguiente tira `TypeError: Cannot read properties of null (reading
// 'addEventListener')`.
//
// Como esto pasa DENTRO de un `.forEach` sobre las ventas de la página, el
// error CORTA el forEach ahí mismo: ninguna fila después de la anulada (en
// el orden que manda el backend, más nueva primero) se termina de agregar a
// la tabla. No es solo un error en consola: el historial de ventas se queda
// mostrando de golpe menos filas de las que dice el paginado (o ninguna, si
// la anulada es la más nueva), sin ningún aviso para quien lo está mirando.
//
// Se reproduce con CUALQUIER venta anulada que aparezca en el listado, no
// hace falta doble clic ni nada especial -- alcanza con volver a abrir (o
// recargar) "Ventas Registradas" una vez que ya existe una anulada.
test.fail(
  "BUG conocido: 'Ventas Registradas' rompe su render al listar una venta anulada (ventas.ts, btnImprimir sin '?.')",
  async ({ page, context }) => {
    await login(page, ADMIN_USERNAME, ADMIN_PASSWORD);

    const nombre = nombreUnico("Bug Anulada Render");
    await crearProducto(context.request, { nombre, precioVenta: 150, stock: 5 });

    await irAVentas(page);
    const card = page.locator(".producto-card", { hasText: nombre });
    await card.click();
    await expect(page.locator(".carrito-item", { hasText: nombre })).toBeVisible();
    await expect(page.locator("#total")).not.toHaveText("Calculando…", { timeout: 5_000 });

    const [popup] = await Promise.all([
      page.waitForEvent("popup"),
      page.locator("#btn-confirmar").click(),
    ]);
    await popup.waitForLoadState();
    await popup.close();

    const ventas: Array<{ id: number }> = await (await context.request.get(`${BASE_URL}/ventas`)).json();
    const ventaId = Math.max(...ventas.map(v => v.id));

    // Anular directo por API (ya probado arriba que anular por la UI llega
    // al mismo estado) para aislar el bug a lo que importa: listar después.
    await context.request.delete(`${BASE_URL}/ventas/${ventaId}`);

    // Recargar la pantalla entera: el primer `cargarVentas()` del `INIT` de
    // ventas.ts ya pisa la venta anulada -- ni hace falta abrir el modal.
    await irAVentas(page);
    await page.locator("#btn-ver-ventas-registradas").click();

    const fila = page.locator(`#tabla-ventas tr:has(td:text-is("${ventaId}"))`);
    await expect(fila.locator(".etiqueta-anulada")).toBeVisible({ timeout: 5_000 });
  }
);
