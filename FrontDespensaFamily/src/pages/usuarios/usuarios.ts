import { esc } from "../../utils/format";
import { checkAdmin, getUsername } from "../../services/auth.service";
checkAdmin();

import { getUsuarios, createUsuario, deleteUsuario, type UsuarioDTO } from "../../services/usuarios.service";
import { login } from "../../services/api";

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
    tr.innerHTML = `
      <td>${esc(u.nombre)}</td>
      <td>${esc(u.username)}</td>
      <td><span class="badge badge-${u.rol.toLowerCase()}">${esc(u.rol)}</span></td>
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
    rol: (form.elements.namedItem("rol") as HTMLSelectElement).value
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
