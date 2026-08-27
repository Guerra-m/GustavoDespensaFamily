import { isAdmin, clearAuth } from "../../services/auth.service";

const NAVBAR_HTML = `
<div class="navbar-overlay" id="navbarOverlay"></div>

<button class="menu-toggle" id="menuToggle" aria-label="Abrir menú">
  <div class="hamburger">
    <span></span>
    <span></span>
    <span></span>
  </div>
</button>

<nav class="navbar" id="navbar">
  <div class="navbar-header">
    <span class="logo">🏪</span>
    <h1>Despensa Family</h1>
  </div>

  <ul class="nav-links">
    <li class="nav-admin-only"><a href="../../pages/home/home.html"><span>🏠</span> Inicio</a></li>
    <li><a href="../../pages/ventas/ventas.html"><span>💰</span> Ventas</a></li>
    <li><a href="../../pages/productos/productos.html"><span>📦</span> Productos</a></li>
    <li><a href="../../pages/categorias/categorias.html"><span>🗂</span> Categorías</a></li>
    <li><a href="../../pages/compras/compras.html"><span>🛒</span> Compras</a></li>
    <li class="nav-admin-only"><a href="../../pages/reportes/reportes.html"><span>📊</span> Reportes</a></li>
    <li class="nav-admin-only"><a href="../../pages/usuarios/usuarios.html"><span>👥</span> Usuarios</a></li>
    <li><a href="../../pages/perfil/perfil.html"><span>👤</span> Mi Perfil</a></li>
    <li class="logout"><a href="#logout"><span>🚪</span> Cerrar sesión</a></li>
  </ul>

  <div class="navbar-footer">
    © 2025 Despensa Family
  </div>
</nav>`;

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
  container.innerHTML = NAVBAR_HTML;
  initNavbar();
}
