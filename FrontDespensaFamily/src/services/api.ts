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
    // El backend valida el largo mínimo y rechaza contraseñas conocidas: ese
    // mensaje sirve más que un "no se pudo" genérico.
    const cuerpo = await response.json().catch(() => null);
    throw new Error(cuerpo?.error ?? "No se pudo cambiar la contraseña");
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
    // El backend distingue "credenciales inválidas" (401) de "demasiados
    // intentos, esperá" (429). Antes se pisaban las dos con el mismo texto y
    // el usuario frenado no entendía por qué no entraba ni cuánto faltaba.
    const cuerpo = await response.json().catch(() => null);
    const error = new Error(cuerpo?.error ?? "Usuario o contraseña incorrectos");
    (error as any).status = response.status;
    throw error;
  }
  return response.json();
}