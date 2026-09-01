import { login } from "../../services/api";

const form       = document.getElementById("loginForm")   as HTMLFormElement;
const mensaje    = document.getElementById("mensaje")      as HTMLParagraphElement;
const btnSubmit  = document.getElementById("btn-submit")   as HTMLButtonElement;
const btnText    = document.getElementById("btn-text")     as HTMLSpanElement;
const btnSpinner = document.getElementById("btn-spinner")  as HTMLSpanElement;

form.addEventListener("submit", async (e) => {
  e.preventDefault();

  const username = (document.getElementById("username") as HTMLInputElement).value;
  const password = (document.getElementById("password") as HTMLInputElement).value;

  // Estado de carga
  mensaje.textContent = "";
  btnText.classList.add("hidden");
  btnSpinner.classList.remove("hidden");
  btnSubmit.disabled = true;

  try {
    const data = await login(username, password);

    localStorage.setItem("auth", "true");
    localStorage.setItem("token", data.token);
    localStorage.setItem("rol", data.rol);
    localStorage.setItem("username", username);

    // El admin que crea el instalador entra con una contraseña generada al
    // azar y anotada en un archivo. Se lo manda a cambiarla antes de dejarlo
    // usar el sistema: si no, esa contraseña queda puesta para siempre.
    if (data.debeCambiarPassword) {
      localStorage.setItem("debeCambiarPassword", "true");
      window.location.href = "../perfil/perfil.html?cambiar=1";
      return;
    }
    localStorage.removeItem("debeCambiarPassword");

    window.location.href = "../home/home.html";
  } catch (err) {
    mensaje.textContent = (err as Error).message || "Usuario o contraseña incorrectos";
    btnText.classList.remove("hidden");
    btnSpinner.classList.add("hidden");
    btnSubmit.disabled = false;
  }
});
