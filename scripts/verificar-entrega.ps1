param([Parameter(Mandatory=$true)][uri]$UrlPublica,[Parameter(Mandatory=$true)][int]$OrdenId)
$ErrorActionPreference='Stop'
if($UrlPublica.Scheme -ne 'https' -or $UrlPublica.AbsolutePath -notmatch '/api/v1/?$'){throw 'Utilice la URL HTTPS pública seguida de /api/v1/.'}
if($OrdenId -le 0){throw 'Use el ID real devuelto al teléfono.'}
$base=$UrlPublica.AbsoluteUri.TrimEnd('/')+'/'
$headers=@{'ngrok-skip-browser-warning'='ventas-demo'}
$salud=Invoke-RestMethod ($base+'salud') -Headers $headers -TimeoutSec 15
if($salud.estado -ne 'OK' -or $salud.baseDatos -ne 'DISPONIBLE'){throw 'API/base no disponibles'}
$orden=Invoke-RestMethod ($base+'ordenes-venta/'+$OrdenId) -Headers $headers -TimeoutSec 15
if($orden.id -ne $OrdenId -or @($orden.detalles).Count -eq 0){throw 'Orden incompleta o ID incorrecto'}
$registro=[ordered]@{fecha=(Get-Date).ToString('o');urlPublica=$base;id=$orden.id;numeroOrden=$orden.numeroOrden;total=$orden.total;lineas=@($orden.detalles).Count;consultaHttp='APROBADA';postDesdeAndroid='PENDIENTE_EVIDENCIA_TELEFONO';consultaSqlDirecta='PENDIENTE_CONSULTA_SQL'}
$destino=Join-Path (Split-Path -Parent $PSScriptRoot) 'target'
New-Item -ItemType Directory -Force -Path $destino | Out-Null
$registro | ConvertTo-Json | Set-Content (Join-Path $destino 'evidencia-entrega.json') -Encoding UTF8
$registro | ConvertTo-Json
