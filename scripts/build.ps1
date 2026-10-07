$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
Push-Location (Join-Path $projectRoot 'frontend')
try {
  & npm.cmd ci --no-audit --no-fund
  if ($LASTEXITCODE -ne 0) { throw '前端依赖安装失败' }
  & npm.cmd run build
  if ($LASTEXITCODE -ne 0) { throw '前端构建失败' }
} finally { Pop-Location }
$staticPath = Join-Path $projectRoot 'backend\src\main\resources\static'
New-Item -ItemType Directory -Force -Path $staticPath | Out-Null
Copy-Item -Path (Join-Path $projectRoot 'frontend\dist\*') -Destination $staticPath -Recurse -Force
Push-Location (Join-Path $projectRoot 'backend')
try {
  & mvn.cmd -s ..\.mvn\settings.xml -B -ntp verify
  if ($LASTEXITCODE -ne 0) { throw '后端构建或测试失败' }
} finally { Pop-Location }
Write-Output '构建完成。运行 .\scripts\start.ps1 启动MySQL版项目。'
