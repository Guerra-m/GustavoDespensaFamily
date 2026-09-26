import {
  isAdmin,
  clearAuth,
  puedeVerCaja,
  puedeGestionarProductos,
  EVENTO_PERMISOS_CAMBIADOS,
} from "../../services/auth.service";
import { confirmar } from "../../services/confirm.service";
import { getConfiguracion } from "../../services/configuracion.service";
import { iniciarAvisoDeActualizacion } from "../actualizacion/aviso-actualizacion";

const NAVBAR_HTML = `
<div class="navbar-overlay" id="navbarOverlay"></div>

<!-- Modal de confirmación genérico, compartido por toda la app (ver
     services/confirm.service.ts) — se inyecta acá porque el navbar ya está
     presente en todas las páginas. -->
<div id="confirm-modal" class="confirm-modal hidden">
  <div class="confirm-modal-content">
    <p id="confirm-modal-mensaje"></p>
    <div class="confirm-modal-acciones">
      <button type="button" id="confirm-modal-cancelar">Cancelar</button>
      <button type="button" id="confirm-modal-aceptar">Aceptar</button>
    </div>
  </div>
</div>

<button class="menu-toggle" id="menuToggle" aria-label="Abrir menú">
  <div class="hamburger">
    <span></span>
    <span></span>
    <span></span>
  </div>
</button>

<nav class="navbar" id="navbar">
  <div class="navbar-header">
    <span class="logo"><span class="icon" style="--icono:url('/icons/store.svg')"></span></span>
    <h1 id="navbar-nombre-negocio">Despensa Family</h1>
  </div>

  <ul class="nav-links">
    <li class="nav-admin-only"><a href="../../pages/home/home.html"><span class="icon" style="--icono:url('/icons/house.svg')"></span> Inicio</a></li>
    <li><a href="../../pages/ventas/ventas.html"><span class="icon" style="--icono:url('/icons/shopping-cart.svg')"></span> Ventas</a></li>
    <li class="nav-solo-caja"><a href="../../pages/caja/caja.html"><span class="icon" style="--icono:url('/icons/piggy-bank.svg')"></span> Caja</a></li>
    <li><a href="../../pages/productos/productos.html"><span class="icon" style="--icono:url('/icons/package.svg')"></span> Productos</a></li>
    <li class="nav-admin-only"><a href="../../pages/asistente-ia/asistente-ia.html"><span class="icon" style="--icono:url('/icons/trending-up.svg')"></span> Asistente IA</a></li>
    <li><a href="../../pages/categorias/categorias.html"><span class="icon" style="--icono:url('/icons/layers.svg')"></span> Categorías</a></li>
    <li class="nav-solo-stock"><a href="../../pages/compras/compras.html"><span class="icon" style="--icono:url('/icons/shopping-bag.svg')"></span> Compras</a></li>
    <li class="nav-admin-only"><a href="../../pages/promos/promos.html"><span class="icon" style="--icono:url('/icons/tag.svg')"></span> Promos</a></li>
    <li class="nav-admin-only"><a href="../../pages/reportes/reportes.html"><span class="icon" style="--icono:url('/icons/bar-chart-3.svg')"></span> Reportes</a></li>
    <li class="nav-admin-only"><a href="../../pages/usuarios/usuarios.html"><span class="icon" style="--icono:url('/icons/users.svg')"></span> Usuarios</a></li>
    <li class="nav-admin-only"><a href="../../pages/empleados/empleados.html"><span class="icon" style="--icono:url('/icons/clipboard-list.svg')"></span> Empleados</a></li>
    <li class="nav-admin-only"><a href="../../pages/ajustes/ajustes.html"><span class="icon" style="--icono:url('/icons/wallet.svg')"></span> Ajustes</a></li>
    <li><a href="../../pages/perfil/perfil.html"><span class="icon" style="--icono:url('/icons/user.svg')"></span> Mi Perfil</a></li>
    <li class="logout"><a href="#logout"><span class="icon" style="--icono:url('/icons/log-out.svg')"></span> Cerrar sesión</a></li>
  </ul>

  <div class="navbar-footer">
    © <span id="navbar-anio"></span> Despensa Family
  </div>
</nav>`;

/**
 * Muestra u oculta cada ítem según lo mismo que decide el backend (ver
 * SecurityConfig). Muestra también, no solo oculta: se vuelve a llamar cuando
 * cambian los permisos, y un permiso recién dado tiene que aparecer.
 */
function aplicarPermisosAlMenu() {
  const visible = (selector: string, puede: boolean) =>
    document.querySelectorAll<HTMLElement>(selector).forEach(el => {
      el.style.display = puede ? "" : "none";
    });
  visible(".nav-admin-only", isAdmin());
  // Caja: del dueño, o de quien tenga PERM_CAJA prendido.
  visible(".nav-solo-caja", puedeVerCaja());
  // Compras: tienen costos y proveedores, así que del dueño o de quien
  // gestiona productos (PERM_STOCK). DF-SEC-003.
  visible(".nav-solo-stock", puedeGestionarProductos());
}

function initNavbar() {
  const toggle = document.getElementById("menuToggle") as HTMLButtonElement;
  const navbar = document.getElementById("navbar") as HTMLElement;
  const overlay = document.getElementById("navbarOverlay") as HTMLElement;
  const navLinks = document.querySelectorAll<HTMLAnchorElement>(".nav-links a");

  // Año del pie de página: estaba escrito a mano ("© 2025"), así que quedaba
  // mal apenas empezaba el año siguiente. new Date().getFullYear() no se
  // desactualiza nunca.
  const anioEl = document.getElementById("navbar-anio");
  if (anioEl) anioEl.textContent = String(new Date().getFullYear());

  // Ocultar ítems restringidos para EMPLEADO
  //
  // El menú conocía un solo permiso —ser ADMIN o no serlo— y nunca se enteró
  // de los permisos sueltos que se agregaron después. Por eso Caja aparecía
  // para todo el mundo, que es como Martín encontró que un empleado sin
  // permiso de caja igual la veía. Ahora cada ítem se muestra según lo mismo
  // que decide el backend (ver SecurityConfig): el menú no puede ofrecer algo
  // que el servidor va a rechazar, ni esconder algo que sí se puede usar.
  aplicarPermisosAlMenu();

  // Si el dueño le cambia los permisos a esta persona con la sesión abierta,
  // el primer pedido de la pantalla ya trae los nuevos (ver
  // actualizarPermisosDesde en auth.service): el menú se redibuja ahí, y si la
  // pantalla actual dejó de estar permitida se va a Ventas, que es de todos.
  window.addEventListener(EVENTO_PERMISOS_CAMBIADOS, () => {
    aplicarPermisosAlMenu();
    const actual = document.querySelector<HTMLElement>(".nav-links a.active")?.closest("li");
    if (actual && actual.style.display === "none") {
      location.replace("../../pages/ventas/ventas.html");
    }
  });

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
  logoutLink?.addEventListener("click", async (e) => {
    e.preventDefault();
    if (await confirmar("¿Estás seguro que deseas cerrar sesión?")) {
      // Con await: clearAuth le pide al servidor que apague la cookie, y si
      // navegamos antes de que termine el navegador cancela ese pedido y la
      // sesión queda viva del otro lado.
      await clearAuth();
      window.location.href = "../../pages/login/login.html";
    }
  });

  // Nombre del negocio configurable (ver pages/ajustes) — si falla (por
  // ejemplo, la página de login no tiene sesión) se queda con el nombre
  // por defecto ya escrito en el HTML, no rompe nada.
  const nombreEl = document.getElementById("navbar-nombre-negocio");
  getConfiguracion()
    .then(config => {
      if (nombreEl && config.nombreNegocio) nombreEl.textContent = config.nombreNegocio;
    })
    .catch(() => {});

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

  // El aviso de actualizacion se engancha aca por el mismo motivo que el modal
  // de confirmacion: el navbar es lo unico que esta en todas las paginas de
  // adentro. En login no hay navbar-container, asi que no se consulta nada.
  iniciarAvisoDeActualizacion();
}
