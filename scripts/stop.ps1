$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$processFile = Join-Path $projectRoot 'logs\process.json'
if (-not (Test-Path -LiteralPath $processFile)) { Write-Output '未找到本项目的启动进程'; exit 0 }
$saved = [IO.File]::ReadAllText($processFile) | ConvertFrom-Json
$processInfo = Get-CimInstance Win32_Process -Filter "ProcessId=$($saved.pid)" -ErrorAction SilentlyContinue
if ($processInfo -and $processInfo.Name -eq 'java.exe' -and $processInfo.CommandLine.Contains($saved.jar)) {
  Stop-Process -Id $saved.pid
  Write-Output '本项目已停止'
} else { Write-Output '本项目进程已退出，不处理其他进程' }
