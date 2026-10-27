/**
 * Escape cierra el modal visible en cualquier pantalla. Dispara el mismo
 * botón de cerrar/cancelar que ya tiene cada modal (id que empieza con
 * "btn-cerrar" o "btn-cancelar") en vez de solo ocultarlo, para no saltarse
 * la limpieza que ese botón ya hace (por ejemplo, cerrarScanner() en Ventas).
 * Si el modal no tiene uno de esos botones, lo oculta directamente.
 */
export function habilitarCierreConEscape(): void {
  document.addEventListener("keydown", e => {
    if (e.key !== "Escape") return;

    const modalesVisibles = document.querySelectorAll(".modal:not(.hidden)");
    if (modalesVisibles.length === 0) return;

    const modal = modalesVisibles[modalesVisibles.length - 1];
    const botonCerrar = modal.querySelector<HTMLButtonElement>(
      "[id^='btn-cerrar'], [id^='btn-cancelar']"
    );

    if (botonCerrar) {
      botonCerrar.click();
    } else {
      modal.classList.add("hidden");
    }
  });
}
