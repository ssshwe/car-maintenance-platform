param([switch]$NoBrowser)

$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [Text.UTF8Encoding]::new($false)
$projectRoot = Split-Path -Parent $PSScriptRoot

function Test-DatabasePort([string]$DatabaseHost, [int]$DatabasePort) {
  $connection = [Net.Sockets.TcpClient]::new()
  try {
    return $connection.ConnectAsync($DatabaseHost, $DatabasePort).Wait(1500) -and $connection.Connected
  } catch { return $false }
  finally { $connection.Dispose() }
}

function Test-ProjectHealth([string]$HealthUrl) {
  try { return (Invoke-RestMethod -Uri $HealthUrl -TimeoutSec 2).status -eq 'UP' }
  catch { return $false }
}

Push-Location -LiteralPath $projectRoot
try {
  Write-Host '车途养护 · 一键启动' -ForegroundColor Cyan
  $envPath = Join-Path $projectRoot '.env'
  if (-not (Test-Path -LiteralPath $envPath)) {
    throw '缺少数据库配置 .env，请先运行 scripts\setup-mysql.ps1。'
  }
  $configuration = @{}
  foreach ($line in [IO.File]::ReadAllLines($envPath)) {
    if ($line -match '^([A-Z_]+)=(.*)$') { $configuration[$Matches[1]] = $Matches[2] }
  }
  $appPort = 8080
  if ($configuration['PORT']) {
    if (-not [int]::TryParse($configuration['PORT'], [ref]$appPort) -or $appPort -lt 1 -or $appPort -gt 65535) {
      throw '.env 中的 PORT 必须是 1–65535 之间的数字。'
    }
  }
  $appUrl = "http://localhost:$appPort/"
  $healthUrl = "$($appUrl)actuator/health"
  $jarPath = Join-Path $projectRoot 'backend\target\maintenance-platform-1.0.0.jar'
  $listeners = @(Get-NetTCPConnection -LocalPort $appPort -State Listen -ErrorAction SilentlyContinue)

  # 只复用启动记录、端口和Java命令行均匹配的本项目进程。
  $alreadyRunning = $false
  if ($listeners.Count) {
    $processFile = Join-Path $projectRoot 'logs\process.json'
    if (Test-Path -LiteralPath $processFile) {
      try {
        $savedProcess = [IO.File]::ReadAllText($processFile) | ConvertFrom-Json
        $processInfo = Get-CimInstance Win32_Process -Filter "ProcessId=$($savedProcess.pid)"
        $alreadyRunning = $processInfo -and $processInfo.Name -eq 'java.exe' -and
          $savedProcess.jar -eq $jarPath -and $processInfo.CommandLine.Contains($jarPath) -and
          ($listeners.OwningProcess -contains [int]$savedProcess.pid)
      } catch { $alreadyRunning = $false }
    }
    if (-not $alreadyRunning) {
      throw "端口 $appPort 已被其他程序占用。请关闭对应程序，或修改 .env 中的 PORT。"
    }
  }

  # 仅在配置指向本机且数据库端口未开放时启动唯一的MySQL服务。
  $databaseHost = '127.0.0.1'
  $databasePort = 3306
  if ($configuration['DB_URL']) {
    try {
      if (-not $configuration['DB_URL'].StartsWith('jdbc:mysql://')) { throw 'Invalid database URL' }
      $databaseUri = [Uri]$configuration['DB_URL'].Substring(5)
      $databaseHost = $databaseUri.Host
      if ($databaseUri.Port -gt 0) { $databasePort = $databaseUri.Port }
    } catch { throw '.env 中的 DB_URL 不是有效的 MySQL JDBC 地址。' }
  }
  if (-not (Test-DatabasePort $databaseHost $databasePort)) {
    $localDatabase = $databaseHost -in @('localhost', '127.0.0.1', '::1', '[::1]')
    $mysqlServices = @(Get-Service | Where-Object { $_.Name -like '*mysql*' -or $_.DisplayName -like '*mysql*' })
    if ($localDatabase -and $mysqlServices.Count -eq 1 -and $mysqlServices[0].Status -eq 'Stopped') {
      Write-Host "正在启动 MySQL 服务：$($mysqlServices[0].Name)…"
      try { Start-Service -Name $mysqlServices[0].Name }
      catch { throw '无法启动 MySQL 服务。请右键一键启动脚本，以管理员身份运行，或手动启动 MySQL 服务。' }
      for ($attempt = 0; $attempt -lt 30; $attempt++) {
        if (Test-DatabasePort $databaseHost $databasePort) { break }
        Start-Sleep -Milliseconds 500
      }
    }
    if (-not (Test-DatabasePort $databaseHost $databasePort)) {
      throw "MySQL 尚未就绪，请确认配置的数据库服务已启动（端口 $databasePort）。"
    }
  }

  if ($alreadyRunning) {
    Write-Host '项目已在运行，正在检查服务状态…'
    for ($attempt = 0; $attempt -lt 15; $attempt++) {
      if (Test-ProjectHealth $healthUrl) { break }
      Start-Sleep -Milliseconds 600
    }
  } else {
    Write-Host '正在启动项目；尚未构建时会自动构建，请稍候…'
    & (Join-Path $PSScriptRoot 'start.ps1')
    if ($LASTEXITCODE -ne 0) { throw '项目启动失败，请查看 logs\app.log 和 logs\app-error.log。' }
  }
  if (-not (Test-ProjectHealth $healthUrl)) {
    throw '服务尚未就绪，请查看 logs\app.log 和 logs\app-error.log。'
  }
  Write-Host "启动成功：$appUrl" -ForegroundColor Green
  if (-not $NoBrowser) {
    try { Start-Process -FilePath $appUrl }
    catch { Write-Host "未能自动打开浏览器，请手动访问 $appUrl" -ForegroundColor Yellow }
  }
  exit 0
} catch {
  Write-Host "启动未完成：$($_.Exception.Message)" -ForegroundColor Red
  exit 1
} finally { Pop-Location }
