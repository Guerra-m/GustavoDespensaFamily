import { habilitarCierreConEscape } from "../../utils/modales";
import { esc } from "../../utils/format";
import { checkAdmin, getUsername } from "../../services/auth.service";
checkAdmin();

import { getUsuarios, createUsuario, deleteUsuario, actualizarPermisos, type UsuarioDTO } from "../../services/usuarios.service";
import { login } from "../../services/api";

const selectRolCrear = document.getElementById("select-rol-crear") as HTMLSelectElement;
const permisosCrear = document.getElementById("permisos-crear") as HTMLDivElement;

function sincronizarPermisosCrear() {
  permisosCrear.classList.toggle("hidden", selectRolCrear.value !== "EMPLEADO");
}
selectRolCrear.addEventListener("change", sincronizarPermisosCrear);
sincronizarPermisosCrear();

const form = document.getElementById("form-crear-usuario") as HTMLFormElement;
const tbody = document.getElementById("tbody-usuarios") as HTMLTableSectionElement;
const toast = document.getElementById("toast") as HTMLDivElement;

const modalEliminar = document.getElementById("modal-eliminar") as HTMLDivElement;
const modalNombre = document.getElementById("modal-usuario-nombre") as HTMLElement;
const modalPassword = document.getElementById("modal-password") as HTMLInputElement;
const modalError = document.getElementById("modal-error") as HTMLParagraphElement;
const btnModalCancelar = document.getElementById("btn-modal-cancelar") as HTMLButtonElement;
const btnModalConfirmar = document.getElementById("btn-modal-confirmar") as HTMLButtonElement;

let idAEliminar: number | null = null;

async function cargarUsuarios() {
  const usuarios = await getUsuarios();
  renderTabla(usuarios);
}

function renderTabla(usuarios: UsuarioDTO[]) {
  tbody.innerHTML = "";

  usuarios.forEach(u => {
    const tr = document.createElement("tr");
    const esAdmin = u.rol === "ADMIN";
    tr.innerHTML = `
      <td>${esc(u.nombre)}</td>
      <td>${esc(u.username)}</td>
      <td><span class="badge badge-${u.rol.toLowerCase()}">${esc(u.rol)}</span></td>
      <td>
        ${esAdmin
          ? `<span class="permisos-admin-nota">Todos (admin)</span>`
          : `<label class="checkbox-label checkbox-permiso">
              <input type="checkbox" class="chk-permiso" data-id="${u.id}" data-permiso="puedeGestionarProductos" ${u.puedeGestionarProductos ? "checked" : ""} />
              Stock
            </label>
            <label class="checkbox-label checkbox-permiso">
              <input type="checkbox" class="chk-permiso" data-id="${u.id}" data-permiso="puedeVerCaja" ${u.puedeVerCaja ? "checked" : ""} />
              Caja
            </label>`}
      </td>
      <td>
        <button class="btn-eliminar" data-id="${u.id}" data-nombre="${esc(u.nombre)}">Eliminar</button>
      </td>
    `;
    tbody.appendChild(tr);
  });

  tbody.querySelectorAll(".btn-eliminar").forEach(btn => {
    btn.addEventListener("click", () => {
      idAEliminar = Number((btn as HTMLElement).dataset.id);
      const nombre = (btn as HTMLElement).dataset.nombre ?? "este usuario";
      abrirModal(nombre);
    });
  });

  tbody.querySelectorAll<HTMLInputElement>(".chk-permiso").forEach(chk => {
    chk.addEventListener("change", async () => {
      const id = Number(chk.dataset.id);
      const usuario = usuarios.find(u => u.id === id);
      if (!usuario) return;

      const permisos = {
        puedeGestionarProductos: usuario.puedeGestionarProductos,
        puedeVerCaja: usuario.puedeVerCaja,
        [chk.dataset.permiso as string]: chk.checked
      };

      chk.disabled = true;
      try {
        await actualizarPermisos(id, permisos);
        mostrarToast("Permiso actualizado — se aplica en el próximo login de esa persona");
      } catch {
        chk.checked = !chk.checked;
        mostrarToast("No se pudo actualizar el permiso", true);
      } finally {
        chk.disabled = false;
      }
    });
  });
}

function abrirModal(nombre: string) {
  modalNombre.textContent = nombre;
  modalPassword.value = "";
  modalError.classList.add("hidden");
  btnModalConfirmar.disabled = false;
  modalEliminar.classList.remove("hidden");
  modalPassword.focus();
}

function cerrarModal() {
  modalEliminar.classList.add("hidden");
  idAEliminar = null;
}

btnModalCancelar.addEventListener("click", cerrarModal);

modalEliminar.addEventListener("click", (e) => {
  if (e.target === modalEliminar) cerrarModal();
});

btnModalConfirmar.addEventListener("click", async () => {
  const password = modalPassword.value.trim();
  if (!password) {
    modalError.textContent = "Ingresá tu contraseña para confirmar.";
    modalError.classList.remove("hidden");
    return;
  }

  const username = getUsername();
  if (!username) {
    modalError.textContent = "No se pudo identificar al administrador. Iniciá sesión nuevamente.";
    modalError.classList.remove("hidden");
    return;
  }

  btnModalConfirmar.disabled = true;

  try {
    await login(username, password);
  } catch {
    modalError.textContent = "Contraseña incorrecta. Intentá de nuevo.";
    modalError.classList.remove("hidden");
    btnModalConfirmar.disabled = false;
    return;
  }

  try {
    await deleteUsuario(idAEliminar!);
    cerrarModal();
    mostrarToast("Usuario eliminado");
    cargarUsuarios();
  } catch {
    cerrarModal();
    mostrarToast("No se pudo eliminar el usuario", true);
  }
});

form.addEventListener("submit", async e => {
  e.preventDefault();

  const data = {
    nombre: (form.elements.namedItem("nombre") as HTMLInputElement).value,
    username: (form.elements.namedItem("username") as HTMLInputElement).value,
    password: (form.elements.namedItem("password") as HTMLInputElement).value,
    rol: (form.elements.namedItem("rol") as HTMLSelectElement).value,
    puedeGestionarProductos: (form.elements.namedItem("puedeGestionarProductos") as HTMLInputElement).checked,
    puedeVerCaja: (form.elements.namedItem("puedeVerCaja") as HTMLInputElement).checked
  };

  try {
    await createUsuario(data);
    form.reset();
    mostrarToast("Usuario creado correctamente");
    cargarUsuarios();
  } catch (err: any) {
    mostrarToast(err.message || "Error al crear usuario", true);
  }
});

function mostrarToast(mensaje: string, error = false) {
  toast.textContent = mensaje;
  toast.classList.remove("hidden", "error");
  if (error) toast.classList.add("error");
  setTimeout(() => toast.classList.add("hidden"), 3000);
}

cargarUsuarios();

// Escape cierra el modal visible en esta pantalla (misma logica en todas las pantallas con modal).
habilitarCierreConEscape();
