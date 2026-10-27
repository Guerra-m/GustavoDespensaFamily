import { checkAuth } from "../../services/auth.service";
checkAuth();

import { getPerfil, cambiarPassword } from "../../services/api";

interface Perfil {
  id: number;
  username: string;
  nombre: string;
  rol: string;
}

const nombreEl = document.getElementById("perfil-nombre") as HTMLElement;
const usernameEl = document.getElementById("perfil-username") as HTMLElement;
const rolEl = document.getElementById("perfil-rol") as HTMLElement;

const form = document.getElementById("form-password") as HTMLFormElement;
const inputActual = document.getElementById("password-actual") as HTMLInputElement;
const inputNueva = document.getElementById("password-nueva") as HTMLInputElement;
const inputConfirmar = document.getElementById("password-confirmar") as HTMLInputElement;
const mensaje = document.getElementById("mensaje-password") as HTMLParagraphElement;

let perfilActual: Perfil | null = null;

// Cuando se llega acá porque el sistema obliga a cambiar la contraseña
// (primer ingreso del admin creado por el instalador), se avisa por qué y no
// se deja seguir hasta que la cambie.
const cambioObligatorio =
  new URLSearchParams(location.search).has("cambiar") ||
  localStorage.getItem("debeCambiarPassword") === "true";

function mostrarMensaje(texto: string, tipo: "ok" | "error") {
  mensaje.textContent = texto;
  mensaje.className = `mensaje ${tipo}`;
}

async function cargarPerfil() {
  try {
    perfilActual = await getPerfil();
    nombreEl.textContent = perfilActual!.nombre;
    usernameEl.textContent = perfilActual!.username;
    rolEl.textContent = perfilActual!.rol === "ADMIN" ? "Administrador" : "Empleado";
  } catch (err) {
    console.error(err);
    nombreEl.textContent = "No se pudo cargar el perfil";
  }
}

form.addEventListener("submit", async (e) => {
  e.preventDefault();

  if (!perfilActual) return;

  if (inputNueva.value !== inputConfirmar.value) {
    mostrarMensaje("Las contraseñas nuevas no coinciden", "error");
    return;
  }

  try {
    await cambiarPassword(perfilActual.id, inputActual.value, inputNueva.value);
    mostrarMensaje("Contraseña actualizada correctamente", "ok");
    form.reset();

    if (cambioObligatorio) {
      localStorage.removeItem("debeCambiarPassword");
      setTimeout(() => { window.location.href = "../home/home.html"; }, 1200);
    }
  } catch (err) {
    mostrarMensaje((err as Error).message || "No se pudo cambiar la contraseña. Verificá tu contraseña actual.", "error");
  }
});

if (cambioObligatorio) {
  mostrarMensaje(
    "Antes de empezar tenés que elegir una contraseña propia. La que te dio el instalador quedó anotada en un archivo de la computadora.",
    "error");
  document.getElementById("password-nueva")?.focus();
}

cargarPerfil();
