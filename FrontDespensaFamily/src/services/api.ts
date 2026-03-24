const BASE_URL = import.meta.env.VITE_API_URL;

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