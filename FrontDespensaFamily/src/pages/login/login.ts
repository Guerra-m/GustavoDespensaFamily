import { login } from "../../services/api";

const form = document.getElementById("loginForm") as HTMLFormElement;
const mensaje = document.getElementById("mensaje") as HTMLParagraphElement;

form.addEventListener("submit", async (e) => {
  e.preventDefault();

  const username = (document.getElementById("username") as HTMLInputElement).value;
  const password = (document.getElementById("password") as HTMLInputElement).value;

  try {
    const data = await login(username, password);

    console.log("Usuario recibido:", data);

    // ✅ guardamos sesión (simple y estable)
    localStorage.setItem("auth", "true");

    // (opcional, si el backend devuelve info del usuario)
    // localStorage.setItem("usuario", JSON.stringify(data.usuario));

    window.location.href = "../home/home.html";
  } catch (err) {
    console.error(err);
    mensaje.textContent = "Usuario o contraseña incorrectos ❌";
    mensaje.style.display = "block";
  }
});
