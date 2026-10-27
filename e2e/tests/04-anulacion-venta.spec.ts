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

// BUG ARREGLADO (era: `cargarVentas()` en
// FrontDespensaFamily/src/pages/ventas/ventas.ts armaba el <tr> del
// histórico con `tr.querySelector(".btn-imprimir") as HTMLButtonElement`
// -- sin "?." -- y para una venta ANULADA esa celda no tiene botón
// "imprimir" (solo el <span class="etiqueta-anulada">), así que el
// `.addEventListener` de la línea siguiente tiraba `TypeError: Cannot read
// properties of null`, cortando el `.forEach` y dejando de pintar el resto
// de las filas del histórico.
//
// Ahora `ventas.ts` usa `btnImprimir?.addEventListener(...)` (ver línea
// ~270), igual que ya hacía `btnAnular`. Este test verifica que, con una
// venta anulada en el listado, el resto de las filas siguen apareciendo.
test(
  "'Ventas Registradas' lista bien una venta anulada (no corta el render del resto de las filas)",
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
