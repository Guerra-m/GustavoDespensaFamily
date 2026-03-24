// navbar.ts

function initNavbar() {
  const toggle = document.getElementById("menuToggle") as HTMLButtonElement;
  const navbar = document.getElementById("navbar") as HTMLElement;
  const overlay = document.getElementById("navbarOverlay") as HTMLElement;
  const navLinks = document.querySelectorAll<HTMLAnchorElement>(".nav-links a");

  // Toggle menú hamburguesa
  toggle?.addEventListener("click", () => {
    navbar?.classList.toggle("open");
    toggle?.classList.toggle("active");
    overlay?.classList.toggle("active");
  });

  // Cerrar menú al hacer click en el overlay (móvil)
  overlay?.addEventListener("click", () => {
    navbar?.classList.remove("open");
    toggle?.classList.remove("active");
    overlay?.classList.remove("active");
  });

  // Cerrar menú al hacer click en cualquier link (móvil)
  navLinks.forEach(link => {
    link.addEventListener("click", () => {
      // Cerrar menú en móvil
      if (window.innerWidth <= 768) {
        navbar?.classList.remove("open");
        toggle?.classList.remove("active");
        overlay?.classList.remove("active");
      }
    });
  });

  // Manejar resize de ventana
  window.addEventListener("resize", () => {
    if (window.innerWidth > 769) {
      navbar?.classList.remove("open");
      toggle?.classList.remove("active");
      overlay?.classList.remove("active");
    }
  });

  // Logout
  const logoutLink = document.querySelector<HTMLAnchorElement>(".logout a");
  logoutLink?.addEventListener("click", (e) => {
    e.preventDefault();
    
    // Confirmación opcional
    const confirmar = confirm("¿Estás seguro que deseas cerrar sesión?");
    if (confirmar) {
      localStorage.removeItem("auth");
      localStorage.removeItem("usuario");
      window.location.href = "../../pages/login/login.html";
    }
  });

  // Marcar página activa basado en la ruta actual
  const currentPath = window.location.pathname;
  navLinks.forEach(link => {
    // Remover active de todos primero
    link.classList.remove("active");
    
    // Comparar rutas
    if (link.href.includes(currentPath) || link.pathname === currentPath) {
      link.classList.add("active");
    }
  });
}

// Esperar a que el navbar esté cargado en el DOM
const container = document.getElementById("navbar-container");
if (container) {
  const observer = new MutationObserver((mutations, obs) => {
    if (container.querySelector("#navbar")) {
      initNavbar();
      obs.disconnect(); // dejamos de observar
    }
  });
  observer.observe(container, { childList: true });
} else {
  // Si no hay container, inicializar directamente (por si el navbar ya está en el DOM)
  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", initNavbar);
  } else {
    initNavbar();
  }
}