param(
    [switch]$Aplicar,
    [string]$ManifestPath,
    [string]$RepositorioLocal,
    [string]$JdbcJar,
    [Alias('h')][switch]$Ayuda
)
$ErrorActionPreference = 'Stop'
$raizImagenes = Split-Path -Parent $PSScriptRoot
$javaImagenes = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin/java.exe' } else { (Get-Command java -ErrorAction Stop).Source }
$javacImagenes = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin/javac.exe' } else { (Get-Command javac -ErrorAction Stop).Source }
$clasesImagenes = Join-Path $raizImagenes 'target/herramientas-imagenes'
New-Item -ItemType Directory -Force -Path $clasesImagenes | Out-Null
& $javacImagenes -encoding UTF-8 -d $clasesImagenes (Join-Path $PSScriptRoot 'CargarImagenes.java')
if ($LASTEXITCODE -ne 0) { throw 'No se pudo compilar CargarImagenes.java.' }
if ($Ayuda -or $ManifestPath -eq '--ayuda' -or $ManifestPath -eq '-h') {
    & $javaImagenes -cp $clasesImagenes CargarImagenes --ayuda
    return
}
if (-not $ManifestPath) { $ManifestPath = Join-Path $raizImagenes 'docs/imagenes/manifest.json' }
if (-not $RepositorioLocal) { $RepositorioLocal = Join-Path $env:USERPROFILE '.m2/repository' }
if (-not $JdbcJar) { $JdbcJar = Join-Path $RepositorioLocal 'com/microsoft/sqlserver/mssql-jdbc/12.8.1.jre11/mssql-jdbc-12.8.1.jre11.jar' }
if (-not (Test-Path -LiteralPath $JdbcJar -PathType Leaf)) { throw 'Falta el controlador JDBC. Compile primero la API con Maven o indique -JdbcJar.' }
if (-not (Test-Path -LiteralPath $ManifestPath -PathType Leaf)) { throw 'Falta el manifiesto de imagenes.' }
$jsonImagenes = Get-Content -LiteralPath $ManifestPath -Raw -Encoding UTF8 | ConvertFrom-Json
$entradasImagenes = if ($jsonImagenes -is [System.Array]) { $jsonImagenes } elseif ($null -ne $jsonImagenes) { @($jsonImagenes) } else { @() }
$lineasImagenes = @()
$conImagen = 0
$codigosImagenes = @{}
foreach ($imagen in $entradasImagenes) {
    $codigo = [string]$imagen.codigo
    if ([string]::IsNullOrWhiteSpace($codigo) -or $codigosImagenes.ContainsKey($codigo)) { throw 'El manifiesto tiene un codigo vacio o repetido.' }
    $codigosImagenes[$codigo] = $true
    $rutaRelativa = [string]$imagen.rutaRelativa
    $archivo = [string]$imagen.archivo
    if ([string]::IsNullOrWhiteSpace($rutaRelativa) -and [string]::IsNullOrWhiteSpace($archivo)) {
        $campos = @($codigo, '', [string]$imagen.textoAlternativo, '', '')
        $codificados = @($campos | ForEach-Object { [Convert]::ToBase64String([System.Text.Encoding]::UTF8.GetBytes($_)) })
        $lineasImagenes += ($codificados -join "`t")
        continue
    }
    if ([string]::IsNullOrWhiteSpace($rutaRelativa) -or [string]::IsNullOrWhiteSpace($archivo)) {
        throw "Entrada incompleta (archivo/rutaRelativa) para $codigo."
    }
    if ($archivo -notmatch '^[A-Za-z0-9][A-Za-z0-9_-]{0,119}\.(jpg|jpeg|png|webp)$') { throw "Archivo de imagen no permitido para $codigo." }
    if ($rutaRelativa -cne ('/media/productos/' + $archivo)) { throw "Ruta incoherente para $codigo." }
    $archivoLocal = Join-Path $raizImagenes ('src/main/resources/static/media/productos/' + $archivo)
    if (-not (Test-Path -LiteralPath $archivoLocal -PathType Leaf)) { throw "Falta el archivo local de $codigo." }
    if (((Get-Item -LiteralPath $archivoLocal).Attributes -band [System.IO.FileAttributes]::ReparsePoint) -ne 0) { throw 'No se permiten enlaces de archivos de imagen.' }
    $conImagen++
    $campos = @($codigo, $rutaRelativa, [string]$imagen.textoAlternativo, [string]$imagen.fuenteUrl, [string]$imagen.licencia)
    $codificados = @($campos | ForEach-Object { [Convert]::ToBase64String([System.Text.Encoding]::UTF8.GetBytes($_)) })
    $lineasImagenes += ($codificados -join "`t")
}
if ($conImagen -eq 0) { throw 'El manifiesto no contiene fotografias locales para cargar.' }
$modoImagenes = if ($Aplicar) { '--aplicar' } else { '--simular' }
$cpImagenes = "$clasesImagenes;$JdbcJar"
$lineasImagenes | & $javaImagenes -cp $cpImagenes CargarImagenes $modoImagenes (Join-Path $raizImagenes 'config/database.properties') (Join-Path $raizImagenes 'docs/imagenes/migracion_producto_imagen.sql')
if ($LASTEXITCODE -ne 0) { throw 'La carga no se confirmo. Revise el mensaje del cargador; no se imprimen credenciales.' }