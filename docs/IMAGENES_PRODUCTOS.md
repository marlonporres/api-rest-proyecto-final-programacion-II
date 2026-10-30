# Imágenes de referencia de productos

## Contrato aditivo y compatible

`GET /api/v1/productos` y las líneas `detalles` de las respuestas de órdenes incluyen dos campos opcionales:

```json
{
  "imagenUrl": "/media/productos/laptop-estudio-14.jpg",
  "imagenDescripcion": "Imagen de referencia de laptop"
}
```

Los dos campos son `null` cuando no hay metadatos válidos. Se conservan todos los campos anteriores y los constructores Java anteriores de `ProductoResponse` y `DetalleOrdenResponse`. Los archivos locales se sirven desde `src/main/resources/static/media/productos/`. Las fotografías son referencias visuales: no acreditan el modelo exacto de un artículo del catálogo.

Android debe resolver `imagenUrl` desde el origen de la URL del servidor actualmente configurado, conservando el `/` inicial. Por ejemplo, una base `http://192.168.1.51:8080/api/v1/` produce `http://192.168.1.51:8080/media/productos/laptop-estudio-14.jpg`, no una ruta bajo `/api/v1/`. La URL se recalcula cuando cambia el servidor. Si el campo es nulo o la descarga falla, mostrar un placeholder estable y el nombre del producto; no interrumpir la orden.

## Datos y seguridad

Se consulta `dbo.producto_imagen` con columnas `id_producto`, `ruta_relativa` y `texto_alternativo`. La tabla tiene relación 1:1 con producto; `fuente_url` y `licencia` conservan la atribución y su procedencia en la base. El script y el manifiesto de fotografías se preparan por separado.

`ruta_relativa` debe guardarse como `/media/productos/archivo.jpg`. El lector admite también el mismo valor sin la barra inicial y la normaliza. El archivo usa solo letras ASCII, números, guion y guion bajo, con extensión jpg, jpeg, png o webp. No se publican URLs externas, subcarpetas, recorridos `..`, escapes `%`, parámetros ni fragmentos almacenados en la base. Las filas con rutas no permitidas se omiten.

Antes de la migración se comprueba expresamente la existencia de la tabla con `OBJECT_ID`. Si no existe, la API conserva su comportamiento anterior y devuelve imágenes nulas. Los fallos de conexión y los errores del esquema existente no se ocultan como fotografías ausentes.

Las imágenes se recuperan por conjunto de productos una sola vez por catálogo, detalle o listado completo de órdenes. El repositorio usa parámetros JDBC y lotes de hasta 1000 identificadores; repetir el mismo producto en muchas órdenes no genera consultas adicionales de imagen por línea. Esta garantía corresponde al enriquecimiento de imágenes, no modifica las consultas anteriores de stock o documentos asociados.

## Verificación del 26 de octubre de 2026

Maven `verify`: **BUILD SUCCESS**, 70 casos descubiertos: **61 aprobados, nueve SQL omitidos y cero fallos o errores**. Se comprobaron campos nulos y con imagen, compatibilidad HTTP, lectura en lote, rechazo de rutas inseguras y propagación de fallos reales de SQL. Se conservaron las 51 clases del núcleo.

No se aplicó el script SQL ni se inició un servidor durante este cambio. Queda pendiente comprobar el flujo real base de datos → API → carga de fotografías en Android tras aplicar la migración y copiar las imágenes.