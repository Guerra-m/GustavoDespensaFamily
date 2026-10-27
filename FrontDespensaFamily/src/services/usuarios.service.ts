import { fetchApi } from "./api";

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
  const res = await fetchApi(BASE_URL, {}, "No se pudieron obtener los usuarios");
  return res.json();
}

export async function createUsuario(data: CrearUsuarioRequest): Promise<UsuarioDTO> {
  const res = await fetchApi(
    BASE_URL,
    {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(data)
    },
    "No se pudo crear el usuario"
  );
  return res.json();
}

export async function actualizarPermisos(id: number, permisos: PermisosUsuario): Promise<UsuarioDTO> {
  const res = await fetchApi(
    `${BASE_URL}/${id}/permisos`,
    {
      method: "PATCH",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(permisos)
    },
    "No se pudieron actualizar los permisos"
  );
  return res.json();
}

export async function deleteUsuario(id: number): Promise<void> {
  await fetchApi(`${BASE_URL}/${id}`, { method: "DELETE" }, "No se pudo eliminar el usuario");
}
