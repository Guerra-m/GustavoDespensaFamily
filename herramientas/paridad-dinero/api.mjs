// Cliente minimo de la API de DespensaFamily para los scripts de paridad.
// Mantiene la cookie de sesion y manda el Origin que exige FiltroDeOrigen.

export async function conectar(base, usuario, password) {
  base = base.replace(/\/$/, "");
  let cookie = "";

  async function pedir(metodo, ruta, cuerpo, extra = {}) {
    const headers = { Origin: base, ...extra };
    if (cookie) headers.Cookie = cookie;
    if (cuerpo !== undefined) headers["Content-Type"] = "application/json";
    const res = await fetch(base + ruta, {
      method: metodo,
      headers,
      body: cuerpo !== undefined ? JSON.stringify(cuerpo) : undefined,
    });
    const nuevas = res.headers.getSetCookie?.() ?? [];
    if (nuevas.length) cookie = nuevas.map((c) => c.split(";")[0]).join("; ");
    const texto = await res.text();
    let json = null;
    try {
      json = texto ? JSON.parse(texto) : null;
    } catch {
      json = texto;
    }
    return { status: res.status, json, texto };
  }

  const login = await pedir("POST", "/usuario/login", { username: usuario, password });
  if (login.status !== 200) throw new Error(`login fallo: ${login.status} ${login.texto}`);

  const ok = async (metodo, ruta, cuerpo, extra) => {
    const r = await pedir(metodo, ruta, cuerpo, extra);
    if (r.status < 200 || r.status >= 300) {
      throw new Error(`${metodo} ${ruta} -> ${r.status} ${r.texto.slice(0, 300)}`);
    }
    return r.json;
  };

  return { pedir, ok };
}

/** Pasa un importe a centavos enteros: suma sin arrastre de double en JS. */
export const centavos = (x) => (x == null ? null : Math.round(Number(x) * 100));
/** Pasa una cantidad a gramos enteros. */
export const gramos = (x) => (x == null ? null : Math.round(Number(x) * 1000));
