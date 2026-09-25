// Flujo 5: empleado sin PERM_CAJA -- solo ve sus ventas del día, no ve costos
// ni Compras (ver PermisosDeLectura.java / SecurityConfig.java).
import { test, expect } from "@playwright/test";
import { login, irAVentas } from "../fixtures/ui.ts";
import { crearProducto, crearEmpleadoBasico, nombreUnico } from "../fixtures/api.ts";
import { ADMIN_USERNAME, ADMIN_PASSWORD, BASE_URL } from "../fixtures/env.ts";

test("empleado sin PERM_CAJA: solo sus ventas de hoy, sin costos ni Compras", async ({ page, context }) => {
  // --- Setup como admin: un producto y una venta AJENA (para probar que el
  // empleado no la ve) ---
  await login(page, ADMIN_USERNAME, ADMIN_PASSWORD);

  const nombre = nombreUnico("Galletitas Empleado");
  const producto = await crearProducto(context.request, { nombre, precioVenta: 300, stock: 20 });

  const empleado = await crearEmpleadoBasico(context.request, `empleado.e2e.${Date.now()}`);

  await irAVentas(page);
  const card = page.locator(".producto-card", { hasText: nombre });
  await card.click();
  await expect(page.locator(".carrito-item", { hasText: nombre })).toBeVisible();
  await expect(page.locator("#total")).not.toHaveText("Calculando…", { timeout: 5_000 });
  const [popupAdmin] = await Promise.all([
    page.waitForEvent("popup"),
    page.locator("#btn-confirmar").click(),
  ]);
  await popupAdmin.waitForLoadState();
  await popupAdmin.close();

  // GET /productos como el dueño SÍ trae el costo (control, no es lo que se prueba acá).
  const productosComoAdmin = await (await context.request.get(`${BASE_URL}/productos`)).json();
  expect(productosComoAdmin.find((p: any) => p.id === producto.id)).toHaveProperty("precioCosto");

  await context.request.post(`${BASE_URL}/usuario/logout`);

  // --- Como el empleado, sin PERM_CAJA ni PERM_STOCK ---
  await login(page, empleado.username, empleado.password);

  // El menú no ofrece Compras (nav-solo-stock, ver navbar.ts): la venta de
  // este empleado es "vender y consultar", no gestionar catálogo.
  await irAVentas(page);
  await expect(page.locator(".nav-links a[href*='compras.html']")).toBeHidden();
  await expect(page.locator(".nav-links a[href*='caja.html']")).toBeHidden();

  // El servidor rechaza /compras directamente (DF-SEC-003), no solo el menú
  // lo esconde -- API-04: "el frontend no lo muestra" no alcanza.
  const resCompras = await context.request.get(`${BASE_URL}/compras`);
  expect(resCompras.status()).toBe(403);

  // GET /productos sin PERM_STOCK no trae precioCosto (ProductoParaVentaDTO).
  const productosComoEmpleado = await (await context.request.get(`${BASE_URL}/productos`)).json();
  const productoParaEmpleado = productosComoEmpleado.find((p: any) => p.id === producto.id);
  expect(productoParaEmpleado).toBeDefined();
  expect(productoParaEmpleado).not.toHaveProperty("precioCosto");

  // Vende algo él mismo hoy...
  await card.click();
  await expect(page.locator(".carrito-item", { hasText: nombre })).toBeVisible();
  await expect(page.locator("#total")).not.toHaveText("Calculando…", { timeout: 5_000 });
  const [popupEmpleado] = await Promise.all([
    page.waitForEvent("popup"),
    page.locator("#btn-confirmar").click(),
  ]);
  await popupEmpleado.waitForLoadState();
  await popupEmpleado.close();

  // ...y "Ventas Registradas" solo trae SUS ventas de hoy: la del admin,
  // hecha un rato antes con el mismo usuario, no puede aparecer.
  await page.locator("#btn-ver-ventas-registradas").click();
  await expect(page.locator("#titulo-ventas-registradas")).toHaveText("Tus ventas de hoy");

  const ventasDelEmpleado: Array<{ usuario: string }> = await (
    await context.request.get(`${BASE_URL}/ventas`)
  ).json();
  expect(ventasDelEmpleado.length).toBeGreaterThan(0);
  for (const venta of ventasDelEmpleado) {
    expect(venta.usuario).toBe(empleado.username);
  }
});
