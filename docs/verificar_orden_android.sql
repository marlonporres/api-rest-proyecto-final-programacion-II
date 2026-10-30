-- SOLO LECTURA: sustituya el número por el devuelto a Android en la demostración.
USE SistemaVentas;
DECLARE @numero_orden VARCHAR(60) = 'PEGAR_NUMERO_OV_REAL';
SELECT * FROM dbo.OrdenVenta WHERE numero_orden = @numero_orden;
SELECT d.* FROM dbo.DetalleOrdenVenta d
INNER JOIN dbo.OrdenVenta o ON o.id = d.id_orden
WHERE o.numero_orden = @numero_orden;
