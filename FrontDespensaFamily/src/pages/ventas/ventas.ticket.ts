// pages/ventas/ventas.ticket.ts
// Impresión del ticket de venta, separada de ventas.ts (Fase 3, DF-DEBT-002).
import { getDetalleVenta } from "../../services/ventas.service";
import { formatearCantidad, esc, formatearMoneda, formatearFechaHora } from "../../utils/format";
import { formatearCantidadConPaquete } from "../../utils/producto";
import type { Producto } from "../../models/Producto";
import type { Venta } from "../../models/Venta";
import type { MetodoPago } from "../../models/MetodoPago";
import type { Cotizacion } from "../../models/Cotizacion";

export interface DepsImpresorTicket {
  /** El catálogo puede no estar cargado todavía si se reimprime un ticket
   * viejo desde el historial sin haber pasado por la pantalla de carga. */
  obtenerProductos(): Producto[];
  cargarProductos(): Promise<void>;
}

function formatearNroTicket(id: number): string {
  return id.toString().padStart(8, "0");
}

/** También la usa el historial de ventas de ventas.ts, no solo el ticket. */
export function etiquetaMetodoPago(metodo: MetodoPago): string {
  const etiquetas: Record<MetodoPago, string> = {
    EFECTIVO: "Efectivo",
    TARJETA: "Tarjeta",
    TRANSFERENCIA: "Transferencia",
    OTRO: "Otro",
  };
  return etiquetas[metodo];
}

// Antes era un <select id="select-cliente"> que ya no existe en el HTML: la
// función siempre devolvía "Consumidor Final" a secas, sin importar nada.
// Ahora usa el nombre que se cargó al confirmar la venta (Fase 9, opcional).
function obtenerCliente(venta: Venta): string {
  return venta.cliente?.trim() || "Consumidor Final";
}

export function crearImpresorTicket(deps: DepsImpresorTicket) {
  async function imprimirTicket(venta: Venta, _cotizacion?: Cotizacion | null) {
    let productos = deps.obtenerProductos();
    if (!productos.length) {
      await deps.cargarProductos();
      productos = deps.obtenerProductos();
    }

    const response = await getDetalleVenta(venta.id!);
    const nroTicket = formatearNroTicket(venta.id!);
    const detalles = response.detalles;

    let filas = "";

    detalles.forEach(d => {
      const producto = productos.find(p => p.id === d.productoId);
      const nombre = producto ? producto.nombre : "Producto eliminado";

      // DF-UX-005: antes el ticket mostraba "1 u × $2860 = $286" sin ninguna
      // aclaración de por qué el precio unitario no coincide con el subtotal.
      // Se lee del DETALLE guardado en la base (d.descuentoPorcentaje /
      // d.promoDescripcion, ver V15), no de la cotización efímera del carrito:
      // así también sale bien al reimprimir un ticket viejo desde el
      // historial, donde no hay ninguna cotización en memoria.
      const indicador = d.descuentoPorcentaje
        ? ` <span style="font-size:8px">(-${formatearCantidad(d.descuentoPorcentaje)}%)</span>`
        : d.promoDescripcion
          ? ` <span style="font-size:8px">(${esc(d.promoDescripcion)})</span>`
          : "";

      filas += `
        <tr>
          <td>${esc(nombre)}${indicador}</td>
          <td style="text-align:center">${formatearCantidadConPaquete(d.cantidad, producto)}</td>
          <td style="text-align:right">${formatearMoneda(d.precioUnitario)}</td>
          <td style="text-align:right">${formatearMoneda(d.subtotal)}</td>
        </tr>
      `;
    });

    const ticketHtml = `
<html>
  <head>
    <title>Ticket Venta #${venta.id}</title>

    <style>
      @page {
        size: 80mm auto;
        margin: 0;
      }

      body {
        width: 65mm;
        margin: 0;
        padding: 2mm;
        font-family: monospace;
        font-size: 10px;
        letter-spacing: -0.2px;
      }

      h2, p {
        margin: 2px 0;
      }

      h2 {
        text-align: center;
      }

      .center {
        text-align: center;
      }

      .row {
        display: flex;
        justify-content: space-between;
        margin: 2px 0;
      }

      .line {
        border-top: 1px dashed #000;
        margin: 4px 0;
      }

      table {
        width: 100%;
        border-collapse: collapse;
        table-layout: fixed;
        font-size: 10px;
      }

      th, td {
        padding: 1px 0;
      }

      th {
        border-bottom: 1px dashed #000;
      }

      /* Producto (más chico) */
th:nth-child(1),
td:nth-child(1) {
  width: 38%;
  word-break: break-word;
}

/* Cant (más pegado al producto) */
th:nth-child(2),
td:nth-child(2) {
  width: 9%;
  text-align: left;
}

/* Precio: antes 20% con "$2.860,00" (9 caracteres) no entraba en una
   línea y el navegador partía el contenido de la celda en dos renglones.
   Se agranda la columna y se fuerza una sola línea (el ticket ya no
   repite la unidad acá -- "u"/"kg" -- porque la columna Cant de al lado
   ya la muestra). */
th:nth-child(3),
td:nth-child(3) {
  width: 25%;
  text-align: right;
  padding-left: 4px;
  white-space: nowrap;
}

/* Subtotal (más separado) */
th:nth-child(4),
td:nth-child(4) {
  width: 28%;
  text-align: right;
  padding-left: 6px;
  white-space: nowrap;
}

      /* Números más legibles */
      td:nth-child(2),
      td:nth-child(3),
      td:nth-child(4) {
        font-size: 10px;
      }

      .total {
        font-size: 12px;
        font-weight: bold;
        text-align: right;
        margin-top: 4px;
      }
    </style>
  </head>

  <body>

    <h2>Despensa Family</h2>
    <p class="center">Ticket de Venta</p>

    <div class="line"></div>

    <div class="row">
      <span>Fecha: ${formatearFechaHora(venta.fechaHora)}</span>
      <span>Nº: ${nroTicket}</span>
    </div>

    <div class="line"></div>

    <p>Cliente: ${esc(obtenerCliente(venta))}</p>

    <div class="line"></div>

    <table>
      <thead>
        <tr>
          <th style="text-align:left">Prod</th>
          <th>Cant</th>
          <th>Precio</th>
          <th>Subt</th>
        </tr>
      </thead>
      <tbody>
        ${filas}
      </tbody>
    </table>

    <div class="line"></div>

    <p class="total">TOTAL: ${formatearMoneda(venta.total)}</p>
    ${venta.metodoPago ? `<p class="center">${etiquetaMetodoPago(venta.metodoPago)}</p>` : ""}

    <div class="line"></div>

    <p class="center">Gracias por su compra</p>

    <div class="line"></div>

    <p class="center" style="font-size:9px;">
      Ticket no válido como factura
    </p>

    <script>
      window.print();
      window.onafterprint = () => window.close();
    </script>

  </body>
</html>
`;

    const ventana = window.open("", "_blank", "width=350,height=600");
    ventana!.document.write(ticketHtml);
    ventana!.document.close();
  }

  return { imprimirTicket };
}
