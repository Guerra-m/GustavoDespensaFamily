import { habilitarCierreConEscape } from "../../utils/modales";
import { checkAdmin } from "../../services/auth.service";
checkAdmin();

import { confirmar } from "../../services/confirm.service";
import { esc } from "../../utils/format";
import type { Empleado } from "../../models/Empleado";
import type { MetodoPago } from "../../models/MetodoPago";
import {
  getEmpleados,
  crearEmpleado,
  desactivarEmpleado,
  getDiasTrabajados,
  marcarDia,
  desmarcarDia,
  pagarEmpleado
} from "../../services/empleados.service";

const toast = document.getElementById("toast") as HTMLDivElement;

function mostrarToast(mensaje: string, error = false) {
  toast.textContent = mensaje;
  toast.classList.remove("hidden", "error");
  if (error) toast.classList.add("error");
  setTimeout(() => toast.classList.add("hidden"), 3000);
}

let empleados: Empleado[] = [];
/** Claves "empleadoId-YYYY-MM-DD" de los días marcados en la semana visible. */
let diasMarcados = new Set<string>();

// ---------------- Semana visible ----------------

const NOMBRES_DIAS = ["Lun", "Mar", "Mié", "Jue", "Vie", "Sáb", "Dom"];

function lunesDeLaSemana(fecha: Date): Date {
  const d = new Date(fecha);
  // getDay(): 0=domingo. Se quiere que la semana arranque el lunes.
  const diff = (d.getDay() + 6) % 7;
  d.setDate(d.getDate() - diff);
  d.setHours(0, 0, 0, 0);
  return d;
}

function formatISO(d: Date): string {
  return d.toISOString().split("T")[0];
}

let inicioSemana = lunesDeLaSemana(new Date());

function diasDeLaSemana(): Date[] {
  return Array.from({ length: 7 }, (_, i) => {
    const d = new Date(inicioSemana);
    d.setDate(d.getDate() + i);
    return d;
  });
}

// ---------------- DOM ----------------

const btnNuevoEmpleado = document.getElementById("btn-nuevo-empleado") as HTMLButtonElement;
const btnSemanaAnterior = document.getElementById("btn-semana-anterior") as HTMLButtonElement;
const btnSemanaSiguiente = document.getElementById("btn-semana-siguiente") as HTMLButtonElement;
const spanSemanaLabel = document.getElementById("empleados-semana-label") as HTMLSpanElement;
const filaDiasHeader = document.getElementById("fila-dias-header") as HTMLTableRowElement;
const cuerpoGrilla = document.getElementById("cuerpo-grilla") as HTMLTableSectionElement;
const grillaVacio = document.getElementById("grilla-vacio") as HTMLParagraphElement;
const cuerpoEmpleados = document.getElementById("cuerpo-empleados") as HTMLTableSectionElement;

// ---------------- Cargar todo ----------------

async function cargarTodo() {
  try {
    empleados = await getEmpleados();

    const dias = diasDeLaSemana();
    const desde = formatISO(dias[0]);
    const hasta = formatISO(dias[6]);
    const marcados = await getDiasTrabajados(desde, hasta);
    diasMarcados = new Set(marcados.map(d => `${d.empleadoId}-${d.fecha}`));

    renderGrilla();
    renderListado();
  } catch (error) {
    mostrarToast((error as Error).message || "Error al cargar empleados", true);
  }
}

// ---------------- Grilla semanal ----------------

function renderGrilla() {
  const dias = diasDeLaSemana();

  spanSemanaLabel.textContent =
    `${dias[0].toLocaleDateString("es-AR", { day: "2-digit", month: "2-digit" })} — ` +
    `${dias[6].toLocaleDateString("es-AR", { day: "2-digit", month: "2-digit", year: "numeric" })}`;

  filaDiasHeader.innerHTML = "<th>Empleado</th>" +
    dias.map((d, i) => `<th>${NOMBRES_DIAS[i]}<br>${d.getDate()}</th>`).join("");

  const activos = empleados.filter(e => e.activo);
  cuerpoGrilla.innerHTML = "";
  grillaVacio.classList.toggle("hidden", activos.length > 0);

  activos.forEach(empleado => {
    const tr = document.createElement("tr");
    const celdas = dias.map(d => {
      const fecha = formatISO(d);
      const marcado = diasMarcados.has(`${empleado.id}-${fecha}`);
      return `<td class="dia-celda">
        <button type="button" class="dia-toggle ${marcado ? "marcado" : ""}"
                data-empleado="${empleado.id}" data-fecha="${fecha}">✓</button>
      </td>`;
    }).join("");
    tr.innerHTML = `<td class="empleados-nombre-col">${esc(empleado.nombre)}</td>${celdas}`;
    cuerpoGrilla.appendChild(tr);
  });

  cuerpoGrilla.querySelectorAll<HTMLButtonElement>(".dia-toggle").forEach(btn => {
    btn.addEventListener("click", () => alternarDia(btn));
  });
}

async function alternarDia(btn: HTMLButtonElement) {
  const empleadoId = Number(btn.dataset.empleado);
  const fecha = btn.dataset.fecha!;
  const clave = `${empleadoId}-${fecha}`;
  const yaMarcado = diasMarcados.has(clave);

  // Optimista: se ve el cambio al toque, y se revierte si falla el pedido.
  btn.classList.toggle("marcado", !yaMarcado);
  try {
    if (yaMarcado) {
      await desmarcarDia(empleadoId, fecha);
      diasMarcados.delete(clave);
    } else {
      await marcarDia(empleadoId, fecha);
      diasMarcados.add(clave);
    }
    renderListado(); // el conteo de "días este período" cambió
  } catch (error) {
    btn.classList.toggle("marcado", yaMarcado); // revertir
    mostrarToast((error as Error).message || "No se pudo actualizar el día", true);
  }
}

btnSemanaAnterior.addEventListener("click", () => {
  inicioSemana.setDate(inicioSemana.getDate() - 7);
  cargarTodo();
});
btnSemanaSiguiente.addEventListener("click", () => {
  inicioSemana.setDate(inicioSemana.getDate() + 7);
  cargarTodo();
});

// ---------------- Listado ----------------

function diasTrabajadosDe(empleadoId: number): number {
  let contador = 0;
  diasMarcados.forEach(clave => {
    if (clave.startsWith(`${empleadoId}-`)) contador++;
  });
  return contador;
}

function renderListado() {
  cuerpoEmpleados.innerHTML = "";

  empleados.forEach(empleado => {
    const tr = document.createElement("tr");
    tr.innerHTML = `
      <td>${esc(empleado.nombre)}</td>
      <td>${empleado.username ? esc(empleado.username) : "—"}</td>
      <td>${empleado.activo
        ? '<span class="etiqueta-activo">Activo</span>'
        : '<span class="etiqueta-inactivo">Inactivo</span>'}</td>
      <td>${diasTrabajadosDe(empleado.id)}</td>
      <td class="empleados-acciones">
        ${empleado.activo ? `<button class="btn-pagar-empleado" data-id="${empleado.id}">Pagar</button>` : ""}
        ${empleado.activo ? `<button class="btn-desactivar-empleado" data-id="${empleado.id}">Desactivar</button>` : ""}
      </td>
    `;
    cuerpoEmpleados.appendChild(tr);

    tr.querySelector(".btn-pagar-empleado")?.addEventListener("click", () => abrirModalPagar(empleado));
    tr.querySelector(".btn-desactivar-empleado")?.addEventListener("click", () => confirmarDesactivar(empleado));
  });
}

async function confirmarDesactivar(empleado: Empleado) {
  const confirmado = await confirmar(
    `¿Desactivar a ${empleado.nombre}? Su historial de pagos y días trabajados se conserva.`);
  if (!confirmado) return;
  try {
    await desactivarEmpleado(empleado.id);
    mostrarToast("Empleado desactivado");
    await cargarTodo();
  } catch (error) {
    mostrarToast((error as Error).message || "No se pudo desactivar", true);
  }
}

// ---------------- Modal: nuevo empleado ----------------

const modalEmpleado = document.getElementById("modal-empleado") as HTMLDivElement;
const formEmpleado = document.getElementById("form-empleado") as HTMLFormElement;
const btnCancelarEmpleado = document.getElementById("btn-cancelar-empleado") as HTMLButtonElement;

btnNuevoEmpleado.addEventListener("click", () => {
  formEmpleado.reset();
  modalEmpleado.classList.remove("hidden");
});
btnCancelarEmpleado.addEventListener("click", () => modalEmpleado.classList.add("hidden"));
modalEmpleado.addEventListener("click", e => {
  if (e.target === modalEmpleado) modalEmpleado.classList.add("hidden");
});

formEmpleado.addEventListener("submit", async e => {
  e.preventDefault();
  const datos = new FormData(formEmpleado);
  try {
    await crearEmpleado({
      nombre: String(datos.get("nombre") || "").trim(),
      username: String(datos.get("username") || "").trim() || undefined
    });
    mostrarToast("Empleado creado");
    modalEmpleado.classList.add("hidden");
    await cargarTodo();
  } catch (error) {
    mostrarToast((error as Error).message || "No se pudo crear el empleado", true);
  }
});

// ---------------- Modal: pagar ----------------

const modalPagar = document.getElementById("modal-pagar") as HTMLDivElement;
const formPagar = document.getElementById("form-pagar") as HTMLFormElement;
const spanPagarNombre = document.getElementById("pagar-nombre") as HTMLSpanElement;
const btnCancelarPagar = document.getElementById("btn-cancelar-pagar") as HTMLButtonElement;
let empleadoAPagar: Empleado | null = null;

function abrirModalPagar(empleado: Empleado) {
  empleadoAPagar = empleado;
  spanPagarNombre.textContent = empleado.nombre;
  formPagar.reset();
  modalPagar.classList.remove("hidden");
}
btnCancelarPagar.addEventListener("click", () => modalPagar.classList.add("hidden"));
modalPagar.addEventListener("click", e => {
  if (e.target === modalPagar) modalPagar.classList.add("hidden");
});

formPagar.addEventListener("submit", async e => {
  e.preventDefault();
  if (!empleadoAPagar) return;
  const datos = new FormData(formPagar);
  try {
    await pagarEmpleado(empleadoAPagar.id, {
      monto: Number(datos.get("monto")),
      metodoPago: datos.get("metodoPago") as MetodoPago,
      descripcion: String(datos.get("descripcion") || "") || undefined
    });
    mostrarToast(`Pago registrado para ${empleadoAPagar.nombre}`);
    modalPagar.classList.add("hidden");
  } catch (error) {
    mostrarToast((error as Error).message || "No se pudo registrar el pago", true);
  }
});

// ---------------- INIT ----------------
cargarTodo();

// Escape cierra el modal visible en esta pantalla (misma logica en todas las pantallas con modal).
habilitarCierreConEscape();
