**Repositorio de entrega:** [ventas-api-rest](https://github.com/marlonporres/api-rest-proyecto-final-programacion-II)

> **Entrega vigente: Android + API REST (8 puntos).** Leer [estudio y defensa](docs/GUIA_ESTUDIO_8_PUNTOS.md), [demostración con ngrok](docs/DEMOSTRACION_8_PUNTOS.md) y [enlaces de entrega](docs/ENTREGA_REPOSITORIOS.md). El flujo evaluado es Android → ngrok → API → SQL → respuesta.

# Ventas · API REST

Backend Java con **Spring Boot 4.1.1, JDK 25 y SQL Server** para la aplicación Android de órdenes de venta. Este repositorio compila y empaqueta su propio JAR ejecutable (`sistema-ventas-api-1.0-SNAPSHOT.jar`) e incluye los recursos estáticos de imágenes del catálogo de computación (`/media/productos/`).

## Requisitos

- **JDK 25** o superior.
- **Maven 3.9+** (en `PATH` o en `~/.m2/apache-maven-3.9.9`).
- **SQL Server** con la base `SistemaVentas` creada por el repositorio `ventas-escritorio`.

## Configuración e inicio

1. Copiar `config/database.properties.example` a `config/database.properties` y configurar las credenciales locales (archivo excluido por `.gitignore`).
2. Ejecutar desde PowerShell:

```powershell
# Compilar y ejecutar pruebas unitarias y de contrato HTTP (sin SQL):
mvn verify

# Compilar y levantar el servidor en el puerto 8080:
.\scripts\api.cmd -EnLinea

# Levantar el servidor directamente usando el JAR ya compilado:
.\scripts\api.cmd -SinCompilar
```

Para cargar o verificar de manera idempotente las 20 fotografías locales asociadas a los 24 productos activos en `dbo.producto_imagen`:

```powershell
.\scripts\cargar-imagenes.cmd -Aplicar
```

## Conexión desde Android

| Entorno | URL base |
| --- | --- |
| Computadora del servidor / USB (`adb reverse`) | `http://localhost:8080/api/v1/` |
| Emulador Android Studio | `http://10.0.2.2:8080/api/v1/` |
| Teléfono en el mismo Wi-Fi | `http://IP_WIFI_DEL_SERVIDOR:8080/api/v1/` |

Verificación rápida de salud:

```powershell
Invoke-RestMethod 'http://localhost:8080/api/v1/salud'
# {"estado":"OK","baseDatos":"DISPONIBLE"}
```

## Contrato HTTP (`/api/v1/`)

Consulte el detalle completo en [docs/CONTRATO_API.md](docs/CONTRATO_API.md) y el soporte de imágenes en [docs/IMAGENES_PRODUCTOS.md](docs/IMAGENES_PRODUCTOS.md).

| Método | Ruta | Función |
| --- | --- | --- |
| `GET` | `/api/v1/salud` | Comprobar disponibilidad de API y SQL Server |
| `GET` | `/api/v1/clientes` | Catálogo de clientes activos |
| `GET` | `/api/v1/productos` | Catálogo de productos activos, stock e `imagenUrl` |
| `GET` | `/api/v1/ordenes-venta` | Listar órdenes; soporta `?estado=PENDIENTE`, `COMPLETADA` o `CANCELADA` |
| `GET` | `/api/v1/ordenes-venta/{id}` | Consultar documento detallado de orden |
| `POST` | `/api/v1/ordenes-venta` | Crear orden en estado `PENDIENTE` |
| `PUT` | `/api/v1/ordenes-venta/{id}` | Editar orden pendiente sin despacho ni factura |
| `DELETE` | `/api/v1/ordenes-venta/{id}` | Cancelar orden conservando historial (`CANCELADA`) |
| `GET` | `/media/productos/{archivo}` | Servir fotografía ilustrativa del producto |

## Pruebas

```powershell
# Ejecutar las 70 pruebas (incluyendo las 9 de integración SQL):
.\scripts\api.cmd -Modo Verificar -PruebasSql
```
