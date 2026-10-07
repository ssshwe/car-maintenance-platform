param([string]$AdminUser = 'root', [string]$AdminPassword = '', [string]$DbHost = '127.0.0.1', [int]$DbPort = 3306)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$mysqlCommand = Get-Command mysql.exe -ErrorAction SilentlyContinue
$mysqlPath = if ($mysqlCommand) { $mysqlCommand.Source } else { 'C:\Program Files\MySQL\MySQL Server 9.5\bin\mysql.exe' }
if (-not (Test-Path -LiteralPath $mysqlPath)) { throw '找不到 mysql.exe，请把 MySQL bin 目录加入 PATH' }
if (-not $AdminPassword) {
  $securePassword = Read-Host '输入MySQL管理员密码（不保存）' -AsSecureString
  $AdminPassword = [System.Net.NetworkCredential]::new('', $securePassword).Password
}
function Invoke-ProjectSql([string]$Sql) {
  $Sql | & $mysqlPath --host=$DbHost --port=$DbPort --user=$AdminUser --default-character-set=utf8mb4 --batch --skip-column-names 2>&1
  if ($LASTEXITCODE -ne 0) { throw 'MySQL操作失败，请检查账号权限与服务状态' }
}
$previousMysqlPassword = $env:MYSQL_PWD
try {
  $env:MYSQL_PWD = $AdminPassword
  $envPath = Join-Path $projectRoot '.env'
  if (Test-Path -LiteralPath $envPath) { throw '.env已存在。为避免覆盖连接配置，初始化已停止。请使用start.ps1启动现有项目。' }
  $databaseName = 'maintenance_platform'
  $existing = Invoke-ProjectSql "SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name='$databaseName';"
  if ([int]$existing -gt 0) { $databaseName = 'maintenance_platform_' + [Guid]::NewGuid().ToString('N').Substring(0,8) }
  $applicationUser = 'maintenance_' + [Guid]::NewGuid().ToString('N').Substring(0,8)
  $passwordBytes = New-Object byte[] 24
  $secretBytes = New-Object byte[] 32
  $generator = [Security.Cryptography.RandomNumberGenerator]::Create()
  $generator.GetBytes($passwordBytes); $generator.GetBytes($secretBytes); $generator.Dispose()
  $applicationPassword = [BitConverter]::ToString($passwordBytes).Replace('-','')
  $jwtSecret = [BitConverter]::ToString($secretBytes).Replace('-','')
  # 不删除、不修改任何已有数据库或账号。专用账号只能访问本次创建的库。
  $null = Invoke-ProjectSql "CREATE DATABASE $databaseName CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci; CREATE USER '$applicationUser'@'localhost' IDENTIFIED BY '$applicationPassword'; GRANT SELECT,INSERT,UPDATE,DELETE,CREATE,ALTER,INDEX,REFERENCES ON $databaseName.* TO '$applicationUser'@'localhost';"
  $environmentText = @"
DB_URL=jdbc:mysql://${DbHost}:${DbPort}/${databaseName}?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai
DB_USERNAME=$applicationUser
DB_PASSWORD=$applicationPassword
JWT_SECRET=$jwtSecret
SPRING_PROFILES_ACTIVE=mysql
APP_SEED=true
PORT=8080
"@
  [IO.File]::WriteAllText($envPath, $environmentText, [Text.UTF8Encoding]::new($false))
  Write-Output "MySQL已配置：数据库=$databaseName；专用账号=$applicationUser；连接参数保存于.gitignore排除的.env中。"
} finally {
  $env:MYSQL_PWD = $previousMysqlPassword
  $AdminPassword = $null
}
