export interface Empleado {
  id: number;
  nombre: string;
  activo: boolean;
  /** Opcional: si además usa el sistema. */
  username?: string | null;
}
