export function getToken(): string | null {
  return localStorage.getItem("token");
}

export function getRol(): string | null {
  return localStorage.getItem("rol");
}

export function isAdmin(): boolean {
  return getRol() === "ADMIN";
}

export function getAuthHeaders(): Record<string, string> {
  const token = getToken();
  return token ? { Authorization: `Bearer ${token}` } : {};
}

let interceptor401Instalado = false;

function instalarInterceptor401(): void {
  if (interceptor401Instalado) return;
  interceptor401Instalado = true;

  const fetchOriginal = window.fetch.bind(window);
  window.fetch = async (...args: Parameters<typeof fetch>) => {
    const response = await fetchOriginal(...args);
    if (response.status === 401) {
      clearAuth();
      window.location.href = "../../pages/login/login.html";
      // No resolvemos: dejamos que la navegación ocurra antes de que el
      // caller intente leer la respuesta y muestre su alert() genérico.
      return new Promise<Response>(() => {});
    }
    return response;
  };
}

export function checkAuth(): void {
  instalarInterceptor401();
  if (!getToken()) {
    window.location.href = "../../pages/login/login.html";
  }
}

export function checkAdmin(): void {
  checkAuth();
  if (!isAdmin()) {
    window.location.href = "../../pages/ventas/ventas.html";
  }
}

export function getUsername(): string | null {
  return localStorage.getItem("username");
}

export function clearAuth(): void {
  localStorage.removeItem("token");
  localStorage.removeItem("rol");
  localStorage.removeItem("auth");
  localStorage.removeItem("usuario");
  localStorage.removeItem("username");
}
