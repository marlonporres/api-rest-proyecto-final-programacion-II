# Contrato API REST

## Endpoints y solicitudes

Todos los endpoints están bajo `/api/v1/`.

| Método | Ruta | Resultado |
| --- | --- | --- |
| GET | `salud` | Estado de API y acceso a la base de datos. |
| GET | `clientes` | Clientes para seleccionar en la orden. |
| GET | `productos` | Productos activos, precio unitario y disponibilidad. |
| GET | `ordenes-venta` | Órdenes por ID descendente; filtro opcional `estado=PENDIENTE`, `COMPLETADA` o `CANCELADA`. |
| GET | `ordenes-venta/{id}` | Cabecera y detalles de una orden. |
| POST | `ordenes-venta` | Crea una orden pendiente y devuelve 201. |
| PUT | `ordenes-venta/{id}` | Reemplaza cliente, observaciones y detalles de una orden editable. |
| DELETE | `ordenes-venta/{id}` | Cancela sin borrar el historial y devuelve la orden. |

Ejemplo de petición POST o PUT:

```json
{
  "clienteId": 1,
  "observaciones": "Entrega por la tarde",
  "detalles": [{"productoId": 1, "cantidad": 2}]
}
```

Los identificadores del ejemplo son ilustrativos: use los que devuelvan los catálogos. Precios, totales, fecha, número de orden y usuario los determina el servidor. Las respuestas usan importes como cadenas decimales de dos posiciones, fecha ISO local de Guatemala, estado y los indicadores `editable` y `cancelable`. Los objetos de respuesta no contienen referencias circulares del modelo Java.

## Reglas que conserva la API

- Crear, editar o cancelar una orden no rebaja ni reserva existencias. La confirmación del despacho sigue realizando la salida de inventario.
- La edición y la cancelación requieren una orden pendiente sin ningún despacho ni factura asociado, con comprobación transaccional.
- La edición mantiene los precios históricos de los productos que permanecen en la orden. Los productos nuevos reciben el precio vigente del catálogo.
- Una orden cancelada sigue consultable. Repetir la cancelación devuelve el mismo estado, sin borrar documentos.
- Los errores tienen `codigo` y `mensaje`. Se utilizan 400 para datos inválidos, 404 para recursos inexistentes, 409 para conflictos, 503 para conexión no disponible y 500 para fallos internos.



## Imágenes opcionales

Los productos y las líneas de órdenes incorporan los campos opcionales imagenUrl e imagenDescripcion. Consulte [el contrato de imágenes, seguridad y resolución en Android](IMAGENES_PRODUCTOS.md).
