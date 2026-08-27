import { getAuthHeaders } from "./auth.service";

const BASE_URL = import.meta.env.VITE_API_URL;

export async function getPerfil() {
  const response = await fetch(`${BASE_URL}/usuario/perfil`, {
    headers: getAuthHeaders()
  });
  if (!response.ok) {
    throw new Error("No se pudo obtener el perfil");
  }
  return response.json();
}

export async function cambiarPassword(id: number, passwordActual: string, passwordNueva: string) {
  const response = await fetch(`${BASE_URL}/usuario/${id}/password`, {
    method: "PATCH",
    headers: {
      "Content-Type": "application/json",
      ...getAuthHeaders()
    },
    body: JSON.stringify({ passwordActual, passwordNueva })
  });
  if (!response.ok) {
    throw new Error("No se pudo cambiar la contraseña");
  }
  return response.json();
}

export async function login(usuario: string, password: string){
  const response = await fetch(`${BASE_URL}/usuario/login`,{
    method: "POST",
    headers:{
      "Content-Type" : "application/json"
    },
    body: JSON.stringify({
      username: usuario,
      password: password
    })
  });
  if(!response.ok){
    throw new Error("Credenciales incorrectas");
  }
  return response.json();
}