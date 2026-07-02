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

export function checkAuth(): void {
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

export function clearAuth(): void {
  localStorage.removeItem("token");
  localStorage.removeItem("rol");
  localStorage.removeItem("auth");
  localStorage.removeItem("usuario");
}
