# Reglas de precio real

DF-DOC-001. Fuente única de esta regla: `VentaService` (calcularLinea, cotizar, registrarVenta),
`DespensaFamily/src/main/java/com/guerram/DespensaFamily/service/VentaService.java`. Si algo de
acá y el código no coinciden, gana el código — este documento se actualiza para seguirlo, no al
revés.

## Por qué existe este documento

Antes (DF-BUG-001) el frontend recalculaba el total del carrito por su cuenta en `ventas.ts`:
aplicaba promos pero nunca descuentos por %, y no redondeaba cantidades pesables. El número que el
cliente veía en el carrito podía no ser el que terminaba cobrado. La corrección fue eliminar el
cálculo paralelo: hoy existe un solo lugar que sabe calcular una línea de venta
(`calcularLinea`), y tanto `cotizar` (lo que se muestra en el carrito, sin persistir nada) como
`registrarVenta` (lo que se cobra de verdad) pasan por él. Mismo código, mismo resultado, por
construcción.

## El endpoint que hay que usar

`POST /ventas/cotizar` — llama a `VentaService.cotizar(...)`, que ejecuta exactamente el mismo
cálculo que `registrarVenta` pero de solo lectura (no descuenta stock, no persiste, no toca caja).
Cualquier pantalla que necesite saber "cuánto va a salir esto" antes de cobrar tiene que pedírselo
a este endpoint, nunca recalcularlo aparte.

## Cómo se calcula una línea (`calcularLinea`)

Para cada producto + cantidad del carrito, en este orden:

1. **Redondeo de cantidad** (`Cantidades.redondear`, `util/Cantidades.java`): la cantidad se
   redondea a 3 decimales (al gramo, la fracción mínima del negocio). Esto evita el error de coma
   flotante de `double` (restarle 1,538 a 5 no da 3,462 exacto en binario) antes de que llegue a
   mostrarse o guardarse.

2. **Validación de decimales**: si `producto.tipoCantidad` **no** es `"PESO"` y la cantidad tiene
   parte decimal, se rechaza ("Este producto no permite decimales"). La regla es al revés de lo que
   parece intuitivo: solo lo que se vende por peso admite fracciones, todo lo demás (unidad, lo que
   sea) tiene que ser entero. Un producto con `tipoCantidad` nulo también cae acá (DF-DATA-001).

3. **Stock insuficiente**: se marca (`stockInsuficiente = stock < cantidad`) pero **no se rechaza
   en este método** — es quien llama el que decide. `registrarVenta` sí tira `RuntimeException`;
   `cotizar` deja pasar la línea marcada para que el frontend avise sin romper la cotización.

4. **Precio de la línea, en este orden de prioridad** (los tres son excluyentes entre sí — el
   primero que aplica gana, no se combinan):

   a. **Descuento por %** (`DescuentoService.porcentajeEfectivoPara`): si el producto tiene un
      descuento por porcentaje activo, `precioUnitario = precioVenta`, pero el subtotal se calcula
      como `precioVenta * (1 - porcentaje/100) * cantidad`, redondeado. Aplica a cualquier
      `tipoCantidad` (unidad o peso).

   b. **Combo / promo "lleva N paga M"** (`PromocionService.promoActivaPara`): solo si
      `tipoCantidad == "UNIDAD"` y no hay descuento por % activo (paso a tiene prioridad). Con
      `lleva` y `paga` de la promo: `grupos = cantidad / lleva` (entero), `resto = cantidad % lleva`,
      `unidadesAPagar = grupos * paga + resto`. Ejemplo: promo 3x2 vendiendo 7 unidades →
      `grupos=2, resto=1 → unidadesAPagar = 2*2 + 1 = 5`. El subtotal es
      `unidadesAPagar * precioVenta`, redondeado.

   c. **Precio de lista**: si no hay descuento ni promo aplicable, `subtotal = precioVenta *
      cantidad`, redondeado.

   Un producto con descuento por % **y** combo activos a la vez: gana el descuento por %, se
   ignora el combo — caso de borde sin regla de negocio pedida explícitamente, resuelto así porque
   el descuento aplica a cualquier `tipoCantidad` y es más simple de calcular de forma consistente
   sobre el total de la línea.

5. **Redondeo del subtotal** (`Dinero.redondear`, `util/Dinero.java`): cada subtotal de línea se
   redondea a 2 decimales (el centavo) con `Math.round(importe * 100) / 100.0`. Mismo problema de
   `double` que en cantidades, pero aplicado a plata: sin esto, un costo de 123,45 con 33% de
   ganancia da 164,1885 en vez de 164,19.

## Cómo se arma el total de la venta

`registrarVenta` suma los subtotales de cada línea calculada por `calcularLinea` y vuelve a pasar
esa suma por `Dinero.redondear` antes de guardar `venta.total`. El orden de procesamiento de las
líneas es por `productoId` ascendente — no afecta el precio, es para evitar deadlocks cuando dos
ventas concurrentes tocan los mismos productos (ver comentario en `registrarVenta`).

Una venta con total en $0 (no debería poder pasar, pero está cubierto) no genera movimiento de
caja: no hay plata que haya entrado.

## Qué NO hacer

- No reimplementar este cálculo en el frontend "para mostrar más rápido" — eso es exactamente lo
  que causó DF-BUG-001. Si hace falta mostrar un preview, se pide a `/ventas/cotizar`.
- No agregar una cuarta prioridad de precio (por ejemplo, otro tipo de promo) sin decidir
  explícitamente dónde entra en el orden del punto 4 — el orden actual es intencional, no
  incidental.
- No redondear "a mano" en otro lado: usar siempre `Dinero.redondear` (plata) o
  `Cantidades.redondear` (cantidades), nunca `Math.round` suelto — son los únicos dos lugares que
  conocen el factor correcto (100 vs. 1000) y por qué.
