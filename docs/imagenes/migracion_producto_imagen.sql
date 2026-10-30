-- Migracion aditiva e idempotente. No modifica productos, ventas ni existencias.
-- Ejecutar dentro de SistemaVentas; no recrear la base de datos.
-- Compatible con JDBC: este archivo no usa separadores GO.
SET XACT_ABORT ON;
DECLARE @transaccionPropia BIT = CASE WHEN @@TRANCOUNT = 0 THEN 1 ELSE 0 END;
BEGIN TRY
    IF @transaccionPropia = 1 BEGIN TRANSACTION;

    IF OBJECT_ID(N'dbo.producto', N'U') IS NULL
        THROW 51001, 'Falta dbo.producto. Instale primero el esquema actual del proyecto.', 1;

    IF OBJECT_ID(N'dbo.producto_imagen', N'U') IS NULL
    BEGIN
        CREATE TABLE dbo.producto_imagen (
            id_producto BIGINT NOT NULL,
            ruta_relativa NVARCHAR(255) NOT NULL,
            texto_alternativo NVARCHAR(250) NULL,
            fuente_url NVARCHAR(1000) NULL,
            licencia NVARCHAR(100) NULL,
            CONSTRAINT PK_producto_imagen PRIMARY KEY (id_producto),
            CONSTRAINT FK_producto_imagen_producto FOREIGN KEY (id_producto)
                REFERENCES dbo.producto(id_producto)
        );
    END;

    -- Una tabla incompatible se rechaza; nunca se sustituye ni se borra.
    IF EXISTS (
        SELECT 1
        FROM (VALUES
            (N'id_producto', N'bigint', 8, 0),
            (N'ruta_relativa', N'nvarchar', 510, 0),
            (N'texto_alternativo', N'nvarchar', 500, 1),
            (N'fuente_url', N'nvarchar', 2000, 1),
            (N'licencia', N'nvarchar', 200, 1)
        ) AS esperado(nombre, tipo, longitud, permite_null)
        LEFT JOIN sys.columns AS c
            ON c.object_id = OBJECT_ID(N'dbo.producto_imagen') AND c.name = esperado.nombre
        WHERE c.column_id IS NULL OR TYPE_NAME(c.system_type_id) <> esperado.tipo
            OR c.max_length <> esperado.longitud OR c.is_nullable <> esperado.permite_null
    )
        THROW 51002, 'dbo.producto_imagen existe con una estructura incompatible; no se modifico.', 1;

    IF NOT EXISTS (
        SELECT 1 FROM sys.key_constraints AS k
        JOIN sys.index_columns AS i ON i.object_id = k.parent_object_id AND i.index_id = k.unique_index_id
        JOIN sys.columns AS c ON c.object_id = i.object_id AND c.column_id = i.column_id
        WHERE k.parent_object_id = OBJECT_ID(N'dbo.producto_imagen') AND k.type = 'PK'
            AND c.name = N'id_producto' AND i.key_ordinal = 1
            AND NOT EXISTS (
                SELECT 1 FROM sys.index_columns AS otro
                WHERE otro.object_id = i.object_id AND otro.index_id = i.index_id AND otro.key_ordinal > 1
            )
    )
        THROW 51003, 'dbo.producto_imagen requiere la clave primaria id_producto.', 1;

    IF NOT EXISTS (
        SELECT 1 FROM sys.foreign_key_columns AS f
        JOIN sys.foreign_keys AS k ON k.object_id = f.constraint_object_id
        WHERE f.parent_object_id = OBJECT_ID(N'dbo.producto_imagen')
            AND f.referenced_object_id = OBJECT_ID(N'dbo.producto')
            AND COL_NAME(f.parent_object_id, f.parent_column_id) = N'id_producto'
            AND COL_NAME(f.referenced_object_id, f.referenced_column_id) = N'id_producto'
            AND k.is_disabled = 0 AND k.is_not_trusted = 0
    )
        THROW 51004, 'dbo.producto_imagen requiere su relacion activa con dbo.producto.', 1;

    IF @transaccionPropia = 1 COMMIT TRANSACTION;
END TRY
BEGIN CATCH
    IF @transaccionPropia = 1 AND XACT_STATE() <> 0 ROLLBACK TRANSACTION;
    THROW;
END CATCH;