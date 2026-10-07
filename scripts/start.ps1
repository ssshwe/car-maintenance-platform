param([switch]$Build)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
if ($Build) { & (Join-Path $PSScriptRoot 'build.ps1') }
$envPath = Join-Path $projectRoot '.env'
if (-not (Test-Path -LiteralPath $envPath)) { throw '缺少.env，请先运行scripts\setup-mysql.ps1配置数据库' }
foreach ($line in [IO.File]::ReadAllLines($envPath)) {
  if ($line -match '^([A-Z_]+)=(.*)$') { [Environment]::SetEnvironmentVariable($Matches[1], $Matches[2], 'Process') }
}
$jarPath = Join-Path $projectRoot 'backend\target\maintenance-platform-1.0.0.jar'
if (-not (Test-Path -LiteralPath $jarPath)) { & (Join-Path $PSScriptRoot 'build.ps1') }
$port = if ($env:PORT) { [int]$env:PORT } else { 8080 }
$logPath = Join-Path $projectRoot 'logs'
New-Item -ItemType Directory -Force -Path $logPath | Out-Null
if (Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue) { throw "端口$port已被占用。请停止现有项目进程或修改.env中的PORT。" }
$javaPath = (Get-Command java.exe -ErrorAction Stop).Source
# Windows Java NIO的本地socket临时路径长度有限，使用较短的项目专用临时目录。
$javaTempPath = Join-Path ([Environment]::GetFolderPath('LocalApplicationData')) 'Temp\carcare'
New-Item -ItemType Directory -Force -Path $javaTempPath | Out-Null
$process = Start-Process -FilePath $javaPath -ArgumentList @('-Dfile.encoding=UTF-8','-Duser.timezone=Asia/Shanghai',('"-Djdk.net.unixdomain.tmpdir=' + $javaTempPath + '"'),('"-Djava.io.tmpdir=' + $javaTempPath + '"'),'-jar',('"' + $jarPath + '"')) -WorkingDirectory $projectRoot -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $logPath 'app.log') -RedirectStandardError (Join-Path $logPath 'app-error.log')
[IO.File]::WriteAllText((Join-Path $logPath 'process.json'), (@{pid=$process.Id;jar=$jarPath;port=$port} | ConvertTo-Json), [Text.UTF8Encoding]::new($false))
for ($attempt=0;$attempt -lt 50;$attempt++) {
  Start-Sleep -Milliseconds 600
  try { $health = Invoke-RestMethod -Uri "http://localhost:$port/actuator/health" -TimeoutSec 1; if ($health.status -eq 'UP') { Write-Output "项目已启动：http://localhost:$port （MySQL模式，PID=$($process.Id)）"; exit 0 } } catch {}
  if ($process.HasExited) { throw "启动失败，请查看logs\app.log和logs\app-error.log" }
}
throw '启动尚未完成，请查看logs\app.log'
