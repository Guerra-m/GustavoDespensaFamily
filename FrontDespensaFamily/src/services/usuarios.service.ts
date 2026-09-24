import { getAuthHeaders } from "./auth.service";

const BASE_URL = `${import.meta.env.VITE_API_URL}/admin/usuarios`;

export interface UsuarioDTO {
  id: number;
  username: string;
  nombre: string;
  rol: string;
  puedeGestionarProductos: boolean;
  puedeVerCaja: boolean;
}

export interface CrearUsuarioRequest {
  username: string;
  password: string;
  nombre: string;
  rol: string;
  puedeGestionarProductos?: boolean;
  puedeVerCaja?: boolean;
}

export interface PermisosUsuario {
  puedeGestionarProductos: boolean;
  puedeVerCaja: boolean;
}

export async function getUsuarios(): Promise<UsuarioDTO[]> {
  const res = await fetch(BASE_URL, { headers: getAuthHeaders() });
  if (!res.ok) throw new Error("Error al obtener usuarios");
  return res.json();
}

export async function createUsuario(data: CrearUsuarioRequest): Promise<UsuarioDTO> {
  const res = await fetch(BASE_URL, {
    method: "POST",
    headers: { "Content-Type": "application/json", ...getAuthHeaders() },
    body: JSON.stringify(data)
  });
  if (!res.ok) {
    const text = await res.text();
    throw new Error(text || "Error al crear usuario");
  }
  return res.json();
}

export async function actualizarPermisos(id: number, permisos: PermisosUsuario): Promise<UsuarioDTO> {
  const res = await fetch(`${BASE_URL}/${id}/permisos`, {
    method: "PATCH",
    headers: { "Content-Type": "application/json", ...getAuthHeaders() },
    body: JSON.stringify(permisos)
  });
  if (!res.ok) throw new Error("No se pudieron actualizar los permisos");
  return res.json();
}

export async function deleteUsuario(id: number): Promise<void> {
  const res = await fetch(`${BASE_URL}/${id}`, {
    method: "DELETE",
    headers: getAuthHeaders()
  });
  if (!res.ok) throw new Error("Error al eliminar usuario");
}
