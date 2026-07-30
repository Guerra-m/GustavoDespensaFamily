import { isAdmin, clearAuth } from "../../services/auth.service";

function initNavbar() {
  const toggle = document.getElementById("menuToggle") as HTMLButtonElement;
  const navbar = document.getElementById("navbar") as HTMLElement;
  const overlay = document.getElementById("navbarOverlay") as HTMLElement;
  const navLinks = document.querySelectorAll<HTMLAnchorElement>(".nav-links a");

  // Ocultar ítems restringidos para EMPLEADO
  if (!isAdmin()) {
    document.querySelectorAll<HTMLElement>(".nav-admin-only").forEach(el => {
      el.style.display = "none";
    });
  }

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
      if (window.innerWidth <= 768) {
        navbar?.classList.remove("open");
        toggle?.classList.remove("active");
        overlay?.classList.remove("active");
      }
    });
  });

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
    if (confirm("¿Estás seguro que deseas cerrar sesión?")) {
      clearAuth();
      window.location.href = "../../pages/login/login.html";
    }
  });

  // Marcar página activa
  const currentPath = window.location.pathname;
  navLinks.forEach(link => {
    link.classList.remove("active");
    if (link.href.includes(currentPath) || link.pathname === currentPath) {
      link.classList.add("active");
    }
  });
}

const container = document.getElementById("navbar-container");
if (container) {
  const observer = new MutationObserver((mutations, obs) => {
    if (container.querySelector("#navbar")) {
      initNavbar();
      obs.disconnect();
    }
  });
  observer.observe(container, { childList: true });
} else {
  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", initNavbar);
  } else {
    initNavbar();
  }
}
