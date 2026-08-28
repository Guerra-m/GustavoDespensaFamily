// Reemplazo de confirm() nativo por un modal propio (consistente con el
// resto de la app en vez del diálogo gris sin estilo del navegador). El
// markup (#modal-confirm) lo inyecta navbar.ts en cada página, así que
// confirmar() está disponible en cualquier página sin duplicar HTML.
export function confirmar(mensaje: string): Promise<boolean> {
  return new Promise((resolve) => {
    const modal = document.getElementById("confirm-modal") as HTMLDivElement;
    const mensajeEl = document.getElementById("confirm-modal-mensaje") as HTMLParagraphElement;
    const btnCancelar = document.getElementById("confirm-modal-cancelar") as HTMLButtonElement;
    const btnAceptar = document.getElementById("confirm-modal-aceptar") as HTMLButtonElement;

    if (!modal || !mensajeEl || !btnCancelar || !btnAceptar) {
      // Salvavidas: si por algún motivo el navbar no llegó a inyectar el
      // modal, no dejamos la acción bloqueada sin feedback.
      resolve(window.confirm(mensaje));
      return;
    }

    mensajeEl.textContent = mensaje;
    modal.classList.remove("hidden");

    function limpiar(resultado: boolean) {
      modal.classList.add("hidden");
      btnCancelar.removeEventListener("click", onCancelar);
      btnAceptar.removeEventListener("click", onAceptar);
      modal.removeEventListener("click", onFondo);
      resolve(resultado);
    }
    function onCancelar() { limpiar(false); }
    function onAceptar() { limpiar(true); }
    function onFondo(e: MouseEvent) {
      if (e.target === modal) limpiar(false);
    }

    btnCancelar.addEventListener("click", onCancelar);
    btnAceptar.addEventListener("click", onAceptar);
    modal.addEventListener("click", onFondo);
  });
}
