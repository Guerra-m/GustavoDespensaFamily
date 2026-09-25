// pages/ventas/ventas.atajos.ts
// Atajos de teclado del POS, separados de ventas.ts (Fase 3, DF-DEBT-002:
// ventas.ts es un archivo enorme con demasiadas responsabilidades mezcladas).
//
// B = buscar, C = cantidad, ↑/↓ = moverse en el catálogo, Enter = confirmar
// selección (se resuelve en ventas.ts, ver inputBuscarProducto "keydown"),
// Ctrl+Enter = confirmar venta. El cartel de abajo (#leyenda-atajos) muestra
// lo mismo para quien no se los sepa.

export interface DepsNavegacionTeclado {
  productosGrid: HTMLElement;
  inputBuscarProducto: HTMLInputElement;
  inputCantidad: HTMLInputElement;
}

export interface NavegacionTeclado {
  /** El catálogo cambió (filtro o categoría): ninguna tarjeta sigue marcada. */
  resetNavegacion(): void;
  /** La tarjeta que las flechas dejaron marcada, si hay una. */
  tarjetaNavegadaActual(): HTMLDivElement | undefined;
  /** Las tarjetas de producto visibles ahora mismo en la grilla. */
  tarjetasVisibles(): HTMLDivElement[];
}

export function crearNavegacionTeclado(deps: DepsNavegacionTeclado): NavegacionTeclado {
  let indiceNavegacionGrid = -1;

  function tarjetasVisibles(): HTMLDivElement[] {
    return Array.from(deps.productosGrid.querySelectorAll<HTMLDivElement>(".producto-card"));
  }

  function marcarTarjetaNavegada(indice: number) {
    const tarjetas = tarjetasVisibles();
    tarjetas.forEach(c => c.classList.remove("navegado-teclado"));
    const card = tarjetas[indice];
    if (!card) return;
    card.classList.add("navegado-teclado");
    card.scrollIntoView({ block: "nearest" });
  }

  /**
   * Si el foco está en un campo donde la persona está escribiendo texto.
   *
   * Las flechas ↑↓ sí tienen que funcionar dentro del buscador —se tipea parte
   * del nombre y se baja por los resultados sin sacar la mano del teclado—, pero
   * las letras no: escribir "coca" en el buscador disparaba el atajo de la "c" y
   * saltaba a Cantidad, así que la palabra quedaba a medias en un campo y el
   * resto en otro. Lo reportó Martín probando la caja.
   *
   * Por eso ahora son dos preguntas distintas: {@link estaEscribiendoTexto} corta
   * los atajos de letra en cualquier campo de texto, incluido el buscador, y
   * {@link estaEnUnCampoQueUsaLasFlechas} corta las flechas solo donde ya
   * significan otra cosa.
   */
  function estaEscribiendoTexto(): boolean {
    const activo = document.activeElement;
    return activo instanceof HTMLInputElement
      || activo instanceof HTMLTextAreaElement
      || activo instanceof HTMLSelectElement;
  }

  function estaEnUnCampoQueUsaLasFlechas(): boolean {
    const activo = document.activeElement;
    if (activo === deps.inputBuscarProducto) return false; // el buscador sí participa de la navegación
    return activo instanceof HTMLInputElement
      || activo instanceof HTMLTextAreaElement
      || activo instanceof HTMLSelectElement;
  }

  document.addEventListener("keydown", (e) => {
    const tecla = e.key.toLowerCase();

    // Atajos de letra: nunca si se está escribiendo en un campo de texto,
    // el buscador incluido. Sirven para SALTAR a un campo; una vez adentro, la
    // letra es una letra.
    if (!estaEscribiendoTexto()) {
      if (tecla === "b") { e.preventDefault(); deps.inputBuscarProducto.focus(); deps.inputBuscarProducto.select(); return; }
      if (tecla === "c") { e.preventDefault(); deps.inputCantidad.focus(); return; }
    }

    // Flechas arriba/abajo: recorren el catálogo visible. Solo si el foco no
    // está en un campo donde esas teclas ya hacen otra cosa (ej: bajar en un
    // <select>).
    if ((e.key === "ArrowDown" || e.key === "ArrowUp") && !estaEnUnCampoQueUsaLasFlechas()) {
      const tarjetas = tarjetasVisibles();
      if (!tarjetas.length) return;
      e.preventDefault();
      if (e.key === "ArrowDown") {
        indiceNavegacionGrid = Math.min(indiceNavegacionGrid + 1, tarjetas.length - 1);
      } else {
        indiceNavegacionGrid = Math.max(indiceNavegacionGrid - 1, 0);
      }
      marcarTarjetaNavegada(indiceNavegacionGrid);
    }
  });

  return {
    resetNavegacion() {
      indiceNavegacionGrid = -1;
    },
    tarjetaNavegadaActual() {
      return indiceNavegacionGrid >= 0 ? tarjetasVisibles()[indiceNavegacionGrid] : undefined;
    },
    tarjetasVisibles,
  };
}
