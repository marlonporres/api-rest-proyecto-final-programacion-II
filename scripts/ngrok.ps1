param([switch]$SoloVerificar)
$ErrorActionPreference='Stop'
$raizTunel=Split-Path -Parent $PSScriptRoot
$exeTunel=Join-Path $raizTunel 'tools\ngrok.exe'
if(-not(Test-Path -LiteralPath $exeTunel)) {
    $instalado=Get-Command ngrok.exe -ErrorAction SilentlyContinue
    if(-not $instalado){throw 'Instale ngrok desde Microsoft Store (https://ngrok.com/download/windows), abra otra consola y configure su token privado con ngrok config add-authtoken.'}
    $exeTunel=$instalado.Source
}
if($SoloVerificar) { & $exeTunel version; & $exeTunel config check; exit $LASTEXITCODE }
$salud=Invoke-RestMethod 'http://localhost:8080/api/v1/salud' -TimeoutSec 5
if($salud.estado -ne 'OK' -or $salud.baseDatos -ne 'DISPONIBLE'){throw 'Primero inicie SQL Server y la API; la salud debe confirmar ambos.'}
$salidaTunel=Join-Path $raizTunel 'target'
New-Item -ItemType Directory -Force -Path $salidaTunel | Out-Null
# No se detiene ni se reutiliza un inspector ajeno al proceso nuevo.
try {Invoke-RestMethod 'http://127.0.0.1:4040/api/tunnels' -TimeoutSec 2 | Out-Null; throw 'Ya hay un inspector en 4040. Compruebe el túnel existente antes de abrir otro.'} catch {if($_.Exception.Message -like 'Ya hay*'){throw}}
$procesoTunel=Start-Process -FilePath $exeTunel -ArgumentList @('http','8080','--log=stdout') -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $salidaTunel 'ngrok.out.log') -RedirectStandardError (Join-Path $salidaTunel 'ngrok.err.log')
$procesoTunel.Id | Set-Content (Join-Path $salidaTunel 'ngrok.pid')
for($intentoTunel=0;$intentoTunel -lt 20;$intentoTunel++) {
    Start-Sleep -Seconds 1
    $procesoTunel.Refresh()
    if($procesoTunel.HasExited){throw 'ngrok terminó. Configure su cuenta/token localmente y revise target/ngrok.err.log sin compartir secretos.'}
    try {
        $tuneles=(Invoke-RestMethod 'http://127.0.0.1:4040/api/tunnels' -TimeoutSec 2).tunnels
        $publico=$tuneles | Where-Object {$_.proto -eq 'https' -and $_.config.addr -match '8080'} | Select-Object -First 1
        if($publico){
            $urlBase=$publico.public_url.TrimEnd('/')+'/api/v1/'
            $prueba=Invoke-RestMethod ($urlBase+'salud') -Headers @{'ngrok-skip-browser-warning'='ventas-demo'} -TimeoutSec 10
            if($prueba.estado -ne 'OK' -or $prueba.baseDatos -ne 'DISPONIBLE'){throw 'Salud pública inválida'}
            $urlBase | Set-Content (Join-Path $salidaTunel 'url-publica.txt')
            Write-Host "URL BASE PARA ANDROID: $urlBase"
            Write-Host 'Inspector de POST: http://127.0.0.1:4040 . Cierre el túnel al terminar.'
            return
        }
    } catch { if($intentoTunel -eq 19){throw} }
}
throw 'No se obtuvo un túnel HTTPS verificable.'
