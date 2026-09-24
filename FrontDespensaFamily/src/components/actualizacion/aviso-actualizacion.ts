import { isAdmin } from "../../services/auth.service";
import { confirmar } from "../../services/confirm.service";
import { esc } from "../../utils/format";
import {
  aplicarActualizacion,
  esperarAlServidor,
  getEstadoDeActualizacion,
  type EstadoDeActualizacion
} from "../../services/actualizaciones.service";

/**
 * La cinta que avisa que hay una versión nueva.
 *
 * La ve todo el que esté adentro, empleado incluido: si la caja se va a
 * reiniciar, quien está atendiendo tiene que poder entender por qué. Pero el
 * botón para aplicarla es solo del dueño — aplicarla apaga el servidor, y esa
 * decisión no se toma en el medio de una venta.
 */

const ID = "aviso-actualizacion";

function contenedor(): HTMLElement {
  let el = document.getElementById(ID);
  if (!el) {
    el = document.createElement("div");
    el.id = ID;
    el.className = "aviso-actualizacion";
    document.body.prepend(el);
  }
  return el;
}

function pintar(estado: EstadoDeActualizacion): void {
  const el = contenedor();
  const version = esc(estado.versionDisponible);

  // esc() en todo lo que viene del backend. Las notas salen del cuerpo de la
  // release en GitHub: es texto que escribimos nosotros, pero llega por la red
  // y termina en innerHTML, así que se escapa igual (regla del 01/09).
  const notas = estado.notas
    ? `<span class="aviso-actualizacion-notas">${esc(estado.notas)}</span>`
    : "";

  if (!estado.listaParaAplicar) {
    // Ya se sabe que existe pero todavía se está bajando. Se avisa igual: son
    // 66 MB y en la conexión del local pueden ser varios minutos.
    el.innerHTML = `
      <span class="aviso-actualizacion-texto">
        Hay una versión nueva (${version}). Descargándola…
      </span>`;
    el.classList.add("visible");
    return;
  }

  const boton = isAdmin()
    ? `<button type="button" id="aviso-actualizacion-boton">Actualizar ahora</button>`
    : `<span class="aviso-actualizacion-nota">Le va a aparecer al dueño para instalarla</span>`;

  el.innerHTML = `
    <span class="aviso-actualizacion-texto">
      Actualización lista (${version}) ${notas}
    </span>
    ${boton}`;
  el.classList.add("visible");

  document.getElementById("aviso-actualizacion-boton")?.addEventListener("click", aplicar);
}

async function aplicar(): Promise<void> {
  const ok = await confirmar(
    "La aplicación se va a cerrar y volver a abrir sola para actualizarse. " +
      "Tarda menos de un minuto. ¿Querés hacerlo ahora?"
  );
  if (!ok) return;

  const el = contenedor();
  el.innerHTML = `<span class="aviso-actualizacion-texto">Actualizando… no cierres la ventana.</span>`;

  try {
    await aplicarActualizacion();
  } catch (error) {
    el.innerHTML = `<span class="aviso-actualizacion-texto">${esc(
      (error as Error).message
    )}</span>`;
    return;
  }

  if (await esperarAlServidor()) {
    window.location.reload();
    return;
  }

  // El servidor no volvió en dos minutos. No se puede arreglar desde acá, pero
  // sí decirle a la persona qué hacer en vez de dejarla mirando una cinta.
  el.innerHTML = `
    <span class="aviso-actualizacion-texto">
      La actualización está tardando más de lo normal. Cerrá y volvé a abrir DespensaFamily.
    </span>`;
}

export function iniciarAvisoDeActualizacion(): void {
  getEstadoDeActualizacion()
    .then(estado => {
      // Sin launcher (jar suelto, o desarrollo) no hay nada que ofrecer, y sin
      // versión nueva tampoco: en los dos casos no se dibuja nada.
      if (estado.gestionadoPorLauncher && estado.hayActualizacion) pintar(estado);
    })
    .catch(() => {
      // La página de login no tiene sesión y este endpoint pide estar adentro.
      // Que falle acá es lo normal, no un problema: no se muestra nada.
    });
}
