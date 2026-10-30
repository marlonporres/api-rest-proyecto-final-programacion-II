param(
    [ValidateSet('Iniciar', 'Compilar', 'Verificar')]
    [string]$Modo = 'Iniciar',
    [switch]$EnLinea,
    [switch]$SinCompilar,
    [switch]$PruebasSql,
    [string]$RepositorioLocal,
    [string]$MavenExecutable
)

$ErrorActionPreference = 'Stop'
$raizApi = Split-Path -Parent $PSScriptRoot
$pomApi = Join-Path $raizApi 'pom.xml'
$configuracionApi = Join-Path $raizApi 'config\database.properties'
$jarApi = Join-Path $raizApi 'target\sistema-ventas-api-1.0-SNAPSHOT.jar'

if ($PruebasSql -and $Modo -ne 'Verificar') {
    throw '-PruebasSql solo se admite junto con -Modo Verificar.'
}
if (-not (Test-Path -LiteralPath $pomApi)) { throw 'No se encontro pom.xml en este proyecto.' }

if ($env:JAVA_HOME) {
    $javaApi = Join-Path $env:JAVA_HOME 'bin\java.exe'
    if (-not (Test-Path -LiteralPath (Join-Path $env:JAVA_HOME 'bin\javac.exe'))) {
        throw 'JAVA_HOME debe apuntar a un JDK completo.'
    }
} else {
    $javaApi = (Get-Command java -ErrorAction Stop).Source
}
$versionApi = & $javaApi --version
if ($LASTEXITCODE -ne 0 -or $versionApi[0] -notmatch '(?:java|openjdk)\s+(\d+)' -or [int]$Matches[1] -lt 25) {
    throw 'Esta API requiere JDK 25 o superior.'
}

function ConvertFrom-PropiedadJava {
    param([string]$Valor)
    [regex]::Replace($Valor, '\\(u[0-9A-Fa-f]{4}|.)', {
        param($coincidencia)
        $escapeApi = $coincidencia.Groups[1].Value
        if ($escapeApi.StartsWith('u') -and $escapeApi.Length -eq 5) {
            return [string][char][Convert]::ToInt32($escapeApi.Substring(1), 16)
        }
        switch ($escapeApi) {
            't' { return "`t" }
            'r' { return "`r" }
            'n' { return "`n" }
            'f' { return [string][char]12 }
            default { return $escapeApi }
        }
    })
}

function Cargar-ConfiguracionApi {
    $propiedadesApi = @{}
    if (Test-Path -LiteralPath $configuracionApi) {
        $continuacionApi = ''
        foreach ($lineaApi in [System.IO.File]::ReadAllLines($configuracionApi, [System.Text.Encoding]::GetEncoding(28591))) {
            if ($continuacionApi.Length -gt 0) { $lineaApi = $continuacionApi + $lineaApi.TrimStart() }
            $barrasFinalesApi = [regex]::Match($lineaApi, '\\+$').Length
            if ($barrasFinalesApi % 2 -eq 1) {
                $continuacionApi = $lineaApi.Substring(0, $lineaApi.Length - 1)
                continue
            }
            $continuacionApi = ''
            if ($lineaApi -match '^\s*(db\.(?:url|user|password))\s*[:=]\s*(.*)$') {
                $propiedadesApi[$Matches[1]] = ConvertFrom-PropiedadJava -Valor $Matches[2]
            }
        }
    }
    $mapaApi = @{
        VENTAS_DB_URL = 'db.url'
        VENTAS_DB_USER = 'db.user'
        VENTAS_DB_PASSWORD = 'db.password'
    }
    foreach ($variableApi in $mapaApi.Keys) {
        if ($null -eq [Environment]::GetEnvironmentVariable($variableApi, 'Process') -and $propiedadesApi.ContainsKey($mapaApi[$variableApi])) {
            [Environment]::SetEnvironmentVariable($variableApi, $propiedadesApi[$mapaApi[$variableApi]], 'Process')
        }
        if ($null -eq [Environment]::GetEnvironmentVariable($variableApi, 'Process')) {
            throw "Falta $variableApi. Configure el entorno o el archivo privado config/database.properties."
        }
    }
}

Push-Location -LiteralPath $raizApi
try {
    if ($Modo -eq 'Iniciar' -or $PruebasSql) { Cargar-ConfiguracionApi }

    if (-not $SinCompilar -or $Modo -ne 'Iniciar') {
        if ($MavenExecutable) {
            if (-not (Test-Path -LiteralPath $MavenExecutable -PathType Leaf)) {
                throw 'La ruta indicada en -MavenExecutable no existe.'
            }
            $mavenApi = [System.IO.Path]::GetFullPath($MavenExecutable)
        } else {
            $mavenInstaladoApi = Get-Command mvn.cmd -ErrorAction SilentlyContinue
            $mavenLocalM2 = Join-Path $HOME '.m2\apache-maven-3.9.9\bin\mvn.cmd'
            if ($null -ne $mavenInstaladoApi) {
                $mavenApi = $mavenInstaladoApi.Source
            } elseif (Test-Path -LiteralPath $mavenLocalM2 -PathType Leaf) {
                $mavenApi = $mavenLocalM2
            } else {
                throw 'Instale Maven 3.9+ y agreguelo a PATH, o indique -MavenExecutable con la ruta a mvn.cmd.'
            }
        }
        $argumentosApi = @('-f', $pomApi)
        if (-not $EnLinea) { $argumentosApi += '-o' }
        if ($RepositorioLocal) {
            $argumentosApi += "-Dmaven.repo.local=$([System.IO.Path]::GetFullPath($RepositorioLocal))"
        }
        $argumentosApi += '-Dventas.api.it=' + $PruebasSql.IsPresent.ToString().ToLowerInvariant()
        if ($Modo -eq 'Compilar') {
            $argumentosApi += @('-DskipTests=true', 'package')
        } else {
            $argumentosApi += 'verify'
        }
        & $mavenApi @argumentosApi
        if ($LASTEXITCODE -ne 0) {
            throw "Maven termino con error ($LASTEXITCODE). Para descargar dependencias use -EnLinea."
        }
    }

    if ($Modo -ne 'Iniciar') {
        Write-Host 'Proceso terminado. Reportes de pruebas en target/surefire-reports.'
        return
    }
    if (-not (Test-Path -LiteralPath $jarApi)) {
        throw 'Falta el JAR. Ejecute scripts\api.cmd -Modo Compilar -EnLinea.'
    }
    $puertoApi = if ($env:VENTAS_API_PORT) { $env:VENTAS_API_PORT } else { '8080' }
    Write-Host "Iniciando API. Emulador: http://10.0.2.2:$puertoApi/api/v1/"
    Write-Host 'En celular use la IP Wi-Fi de esta computadora. Ctrl+C detiene el servidor.'
    & $javaApi -jar $jarApi
    if ($LASTEXITCODE -ne 0) { throw "La API termino con error ($LASTEXITCODE). Revise la salida del servidor." }
} finally {
    Pop-Location
}