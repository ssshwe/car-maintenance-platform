param([string]$AdminUser='root', [string]$AdminPassword='')
$ErrorActionPreference='Stop'
$projectRoot=Split-Path -Parent $PSScriptRoot
$mysqlCommand=Get-Command mysql.exe -ErrorAction SilentlyContinue
$mysqlPath=if($mysqlCommand){$mysqlCommand.Source}else{'C:\Program Files\MySQL\MySQL Server 9.5\bin\mysql.exe'}
if(-not $AdminPassword){$securePassword=Read-Host 'MySQL管理员密码（用于创建独立临时测试库）' -AsSecureString;$AdminPassword=[Net.NetworkCredential]::new('',$securePassword).Password}
$suffix=[Guid]::NewGuid().ToString('N').Substring(0,10)
$testDatabase="maintenance_test_$suffix"
$testUser="maint_test_$suffix"
$testBytes=New-Object byte[] 24
$generator=[Security.Cryptography.RandomNumberGenerator]::Create();$generator.GetBytes($testBytes);$generator.Dispose()
$testPassword=[BitConverter]::ToString($testBytes).Replace('-','')
$previousMysqlPassword=$env:MYSQL_PWD
function Invoke-TestSql([string]$sql){$sql | & $mysqlPath --host=127.0.0.1 --user=$AdminUser --batch --skip-column-names;if($LASTEXITCODE -ne 0){throw 'MySQL测试库操作失败'}}
$created=$false
try{
  $env:MYSQL_PWD=$AdminPassword
  Invoke-TestSql "CREATE DATABASE $testDatabase CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci; CREATE USER '$testUser'@'localhost' IDENTIFIED BY '$testPassword'; GRANT SELECT,INSERT,UPDATE,DELETE,CREATE,ALTER,INDEX,REFERENCES ON $testDatabase.* TO '$testUser'@'localhost';"
  $created=$true
  $env:TEST_DB_URL="jdbc:mysql://127.0.0.1:3306/${testDatabase}?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai"
  $env:TEST_DB_USERNAME=$testUser;$env:TEST_DB_PASSWORD=$testPassword;$env:TEST_DB_DRIVER='com.mysql.cj.jdbc.Driver'
  Push-Location (Join-Path $projectRoot 'backend')
  try { & mvn.cmd -s ..\.mvn\settings.xml -B -ntp test; if($LASTEXITCODE -ne 0){throw 'MySQL集成测试失败'} } finally {Pop-Location}
  Write-Output 'MySQL集成测试全部通过。'
}finally{
  if($created -and $testDatabase -match '^maintenance_test_[a-f0-9]{10}$' -and $testUser -match '^maint_test_[a-f0-9]{10}$') {Invoke-TestSql "DROP DATABASE $testDatabase; DROP USER '$testUser'@'localhost';"}
  $env:MYSQL_PWD=$previousMysqlPassword
  foreach($key in @('TEST_DB_URL','TEST_DB_USERNAME','TEST_DB_PASSWORD','TEST_DB_DRIVER')){[Environment]::SetEnvironmentVariable($key,$null,'Process')}
  $AdminPassword=$null
}
