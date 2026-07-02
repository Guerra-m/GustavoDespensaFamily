import { checkAdmin } from "../../services/auth.service";
checkAdmin();

import { getUsuarios, createUsuario, deleteUsuario, type UsuarioDTO } from "../../services/usuarios.service";

const form = document.getElementById("form-crear-usuario") as HTMLFormElement;
const tbody = document.getElementById("tbody-usuarios") as HTMLTableSectionElement;
const toast = document.getElementById("toast") as HTMLDivElement;

let usuarioActualId: number | null = null;

async function cargarUsuarios() {
  const usuarios = await getUsuarios();
  renderTabla(usuarios);
}

function renderTabla(usuarios: UsuarioDTO[]) {
  tbody.innerHTML = "";

  usuarios.forEach(u => {
    const tr = document.createElement("tr");
    tr.innerHTML = `
      <td>${u.nombre}</td>
      <td>${u.username}</td>
      <td><span class="badge badge-${u.rol.toLowerCase()}">${u.rol}</span></td>
      <td>
        <button class="btn-eliminar" data-id="${u.id}">Eliminar</button>
      </td>
    `;
    tbody.appendChild(tr);
  });

  tbody.querySelectorAll(".btn-eliminar").forEach(btn => {
    btn.addEventListener("click", async () => {
      const id = Number((btn as HTMLElement).dataset.id);
      if (!confirm("¿Eliminar este usuario?")) return;
      try {
        await deleteUsuario(id);
        mostrarToast("Usuario eliminado");
        cargarUsuarios();
      } catch {
        mostrarToast("No se pudo eliminar el usuario", true);
      }
    });
  });
}

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
