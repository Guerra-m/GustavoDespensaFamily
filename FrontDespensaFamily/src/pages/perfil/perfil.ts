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
  } catch (err) {
    mostrarMensaje("No se pudo cambiar la contraseña. Verificá tu contraseña actual.", "error");
  }
});

cargarPerfil();
