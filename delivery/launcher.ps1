param([switch]$Headless,[switch]$NoBrowser)
$ErrorActionPreference='Stop'
$script:Root=$PSScriptRoot
$script:Data=Join-Path $PSScriptRoot 'data'
$script:Db=$null; $script:App=$null; $script:Running=$false; $script:Busy=$false; $script:CancelRequested=$false
$script:Config=$null; $script:Lock=$null; $script:Status=$null
$script:DriveName=$null; $script:DriveTarget=$null
$utf8=New-Object System.Text.UTF8Encoding($false)

function Mount-PortablePath {
    # MySQL 8 Windows argument handling is unreliable for some non-ASCII install paths.
    # A per-user temporary drive makes all native paths ASCII, without moving user data.
    Add-Type -TypeDefinition @'
using System;
using System.Text;
using System.Runtime.InteropServices;
public static class StudyPortableDrive {
    [DllImport("kernel32.dll", CharSet=CharSet.Unicode, SetLastError=true)]
    public static extern bool DefineDosDevice(uint flags, string name, string target);
    [DllImport("kernel32.dll", CharSet=CharSet.Unicode, SetLastError=true)]
    public static extern uint QueryDosDevice(string name, StringBuilder target, int length);
    [DllImport("user32.dll")]
    public static extern bool ShowWindow(IntPtr window, int command);
}
'@
    $occupied=@([IO.DriveInfo]::GetDrives() | ForEach-Object {$_.Name.Substring(0,2)})
    foreach ($number in (90..68)) {
        $name=([string][char]$number)+':'
        $buffer=New-Object Text.StringBuilder(4096)
        if ($occupied -contains $name -or [StudyPortableDrive]::QueryDosDevice($name,$buffer,$buffer.Capacity) -ne 0) {continue}
        $target='\??\'+$PSScriptRoot
        if ([StudyPortableDrive]::DefineDosDevice(9,$name,$target)) {
            $script:DriveName=$name; $script:DriveTarget=$target
            $script:Root=$name+'\';$script:Data=Join-Path $script:Root 'data'
            return
        }
    }
    throw '无法创建临时运行盘符，请留出一个可用盘符后重试。'
}
function Unmount-PortablePath {
    if ($null -ne $script:DriveName) {
        [void][StudyPortableDrive]::DefineDosDevice(15,$script:DriveName,$script:DriveTarget)
        $script:DriveName=$null
    }
}

function Note([string]$Message) {
    if ($Headless) {Write-Host $Message}
    elseif ($null -ne $script:Status) {$script:Status.Text=$Message; [Windows.Forms.Application]::DoEvents()}
}
function Random-Secret([int]$Count=32) {
    $bytes=New-Object byte[] $Count
    $rng=[Security.Cryptography.RandomNumberGenerator]::Create()
    try {$rng.GetBytes($bytes)} finally {$rng.Dispose()}
    return [Convert]::ToBase64String($bytes)
}
function Free-Port {
    $listener=New-Object Net.Sockets.TcpListener([Net.IPAddress]::Loopback,0)
    try {$listener.Start();return $listener.LocalEndpoint.Port} finally {$listener.Stop()}
}
function Native-Arguments([string[]]$Values) {
    # Arguments here contain paths/options only; SQL and keys never enter command lines.
    return (($Values | ForEach-Object {
        if ($_.Contains('"') -or $_.Contains("`r") -or $_.Contains("`n")) {throw 'Invalid process argument'}
        '"'+$_+'"'
    }) -join ' ')
}
function Process-Info([string]$Executable,[string[]]$Arguments,[hashtable]$Environment=@{}) {
    $info=New-Object Diagnostics.ProcessStartInfo
    $info.FileName=$Executable; $info.Arguments=Native-Arguments $Arguments
    $info.WorkingDirectory=$script:Root; $info.UseShellExecute=$false; $info.CreateNoWindow=$true
    foreach ($name in @($info.EnvironmentVariables.Keys)) {
        if ($name -match '^(DEEPSEEK_|QWEN_|STUDY_SESSION_|DB_|JWT_|SPRING_)|^(JAVA_TOOL_OPTIONS|_JAVA_OPTIONS|MYSQL_PWD)$') {
            $info.EnvironmentVariables.Remove($name)
        }
    }
    foreach ($name in $Environment.Keys) {$info.EnvironmentVariables[$name]=[string]$Environment[$name]}
    return $info
}
function Check-Cancel {if ($script:CancelRequested) {throw '启动已取消'}}
function Wait-ProcessReady($Process,[int]$Seconds) {
    $until=[DateTime]::UtcNow.AddSeconds($Seconds)
    while (-not $Process.WaitForExit(150)) {
        Check-Cancel
        if (-not $Headless) {[Windows.Forms.Application]::DoEvents()}
        if ([DateTime]::UtcNow -gt $until) { $Process.Kill();throw '进程操作超时，请查看logs目录。'}
    }
    return $Process.ExitCode
}
function Mysql-Command([string]$Sql,[string]$Password,[int]$Port) {
    $info=Process-Info (Join-Path $script:Root 'runtime/mysql/bin/mysql.exe') @('--protocol=TCP','--host=127.0.0.1',"--port=$Port",'--user=root','--default-character-set=utf8mb4','--connect-timeout=2','--batch','--skip-column-names') @{MYSQL_PWD=$Password}
    $info.RedirectStandardInput=$true; $info.RedirectStandardOutput=$true; $info.RedirectStandardError=$true
    $info.StandardOutputEncoding=$utf8; $info.StandardErrorEncoding=$utf8
    $proc=New-Object Diagnostics.Process; $proc.StartInfo=$info
    try {
        [void]$proc.Start()
        $out=$proc.StandardOutput.ReadToEndAsync();$err=$proc.StandardError.ReadToEndAsync()
        $inputBytes=$utf8.GetBytes($Sql)
        $proc.StandardInput.BaseStream.Write($inputBytes,0,$inputBytes.Length)
        $proc.StandardInput.BaseStream.Flush();$proc.StandardInput.Close()
        $code=Wait-ProcessReady $proc 120
        $text=$out.GetAwaiter().GetResult();$errorText=$err.GetAwaiter().GetResult()
        if ($code -ne 0) {
            if ($Password) {$errorText=$errorText.Replace($Password,'***')}
            throw ('数据库操作失败：'+$errorText.Trim())
        }
        return $text.Trim()
    } finally {$proc.Dispose()}
}
function Backend-Ready([int]$Port) {
    try {
        $request=[Net.HttpWebRequest]::Create("http://127.0.0.1:$Port/login")
        $request.Proxy=$null;$request.Timeout=1000
        $response=$request.GetResponse()
        try {return [int]$response.StatusCode -eq 200} finally {$response.Close()}
    } catch {return $false}
}
function Stop-Platform {
    if ($null -eq $script:App -and $null -eq $script:Db -and -not $script:Running) {return}
    if ($null -ne $script:App) {
        try {if (-not $script:App.HasExited) {$script:App.Kill();[void]$script:App.WaitForExit(10000)}} catch {}
        $script:App.Dispose();$script:App=$null
    }
    if ($null -ne $script:Db) {
        try {
            if (-not $script:Db.HasExited) {
                try {[void](Mysql-Command 'SHUTDOWN;' $script:Config.rootPassword $script:DbPort)} catch {}
                if (-not $script:Db.WaitForExit(15000)) {$script:Db.Kill();[void]$script:Db.WaitForExit(5000)}
            }
        } finally {$script:Db.Dispose();$script:Db=$null}
    }
    $script:Running=$false
    $runFile=Join-Path $script:Data 'run.json'
    if (Test-Path -LiteralPath $runFile) {Remove-Item -LiteralPath $runFile -Force}
    Note '服务已停止；题库和学习记录已保留。'
}
function Start-Platform([string]$DeepSeekKey='', [string]$QwenKey='') {
    if ($script:Running) {return}
    $script:CancelRequested=$false
    if (-not [Environment]::Is64BitOperatingSystem) {throw '本版本需要64位Windows 10/11。'}
    foreach ($path in @('runtime/java/bin/java.exe','runtime/mysql/bin/mysqld.exe','app/study-server.jar','web/index.html','sql/study_system.sql')) {
        if (-not (Test-Path -LiteralPath (Join-Path $script:Root $path))) {throw "文件缺失：$path。请完整解压压缩包后运行。"}
    }
    [void][IO.Directory]::CreateDirectory($script:Data)
    [void][IO.Directory]::CreateDirectory((Join-Path $script:Root 'logs'))
    $configPath=Join-Path $script:Data 'local.json'
    $dbPath=Join-Path $script:Data 'mysql'
    $marker=Join-Path $script:Data 'initialized'
    if (Test-Path -LiteralPath $configPath) {
        $script:Config=Get-Content -LiteralPath $configPath -Raw -Encoding UTF8 | ConvertFrom-Json
    } else {
        $script:Config=[pscustomobject]@{rootPassword=(Random-Secret);appPassword=(Random-Secret);jwt=(Random-Secret)}
        [IO.File]::WriteAllText($configPath,($script:Config | ConvertTo-Json),$utf8)
    }
    $fresh=-not (Test-Path -LiteralPath $marker)
    $mysqlBase=Join-Path $script:Root 'runtime/mysql'
    if ($fresh) {
        if ((Test-Path -LiteralPath $dbPath) -and (Get-ChildItem -LiteralPath $dbPath -Force | Select-Object -First 1)) {
            throw '上次首次初始化未完成。请关闭启动器，将data目录重命名备份，再重新启动；不要覆盖已有数据。'
        }
        Note '首次运行：正在初始化独立数据库，可能需要几十秒……'
        $init=New-Object Diagnostics.Process
        $init.StartInfo=Process-Info (Join-Path $mysqlBase 'bin/mysqld.exe') @('--no-defaults','--initialize-insecure',"--basedir=$mysqlBase","--datadir=$dbPath",'--default-time-zone=+08:00',"--log-error=$(Join-Path $script:Root 'logs/mysql-init.log')")
        try {[void]$init.Start();if ((Wait-ProcessReady $init 180) -ne 0) {throw '数据库初始化失败，请查看logs/mysql-init.log。'}} finally {$init.Dispose()}
    }
    $script:DbPort=Free-Port
    $script:Db=New-Object Diagnostics.Process
    $script:Db.StartInfo=Process-Info (Join-Path $mysqlBase 'bin/mysqld.exe') @('--no-defaults','--no-monitor',"--basedir=$mysqlBase","--datadir=$dbPath",'--bind-address=127.0.0.1',"--port=$script:DbPort",'--mysqlx=0','--default-time-zone=+08:00','--character-set-server=utf8mb4','--collation-server=utf8mb4_0900_ai_ci','--innodb-buffer-pool-size=64M','--performance-schema=OFF',"--log-error=$(Join-Path $script:Root 'logs/mysql.log')", "--pid-file=$(Join-Path $script:Data 'mysql.pid')")
    Note '正在启动本包的MySQL……';[void]$script:Db.Start()
    $rootPassword=if ($fresh) {''} else {$script:Config.rootPassword}
    $ready=$false
    for ($i=0;$i -lt 60;$i++) {
        Check-Cancel
        if ($script:Db.HasExited) {throw '数据库未能启动，请查看logs/mysql.log。'}
        try {if ((Mysql-Command 'SELECT 1;' $rootPassword $script:DbPort) -eq '1') {$ready=$true;break}} catch {$databaseError=$_.Exception.Message}
        Start-Sleep -Milliseconds 300
    }
    if (-not $ready) {throw ('等待数据库启动超时。'+$databaseError)}
    if ($fresh) {
        Note '正在导入完整题库和演示账号……'
        [void](Mysql-Command (Get-Content -LiteralPath (Join-Path $script:Root 'sql/study_system.sql') -Raw -Encoding UTF8) '' $script:DbPort)
        $rp=$script:Config.rootPassword;$ap=$script:Config.appPassword
        [void](Mysql-Command "CREATE USER 'studyapp'@'localhost' IDENTIFIED BY '$ap'; GRANT ALL PRIVILEGES ON study_system.* TO 'studyapp'@'localhost'; ALTER USER 'root'@'localhost' IDENTIFIED BY '$rp';" '' $script:DbPort)
        $rootPassword=$script:Config.rootPassword
        [IO.File]::WriteAllText($marker,'ready',$utf8)
    }
    $script:WebPort=Free-Port
    $webUri=(New-Object Uri((Join-Path $script:Root 'web')+[IO.Path]::DirectorySeparatorChar)).AbsoluteUri
    $envs=@{DB_URL="jdbc:mysql://127.0.0.1:$script:DbPort/study_system?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true";DB_USERNAME='studyapp';DB_PASSWORD=$script:Config.appPassword;JWT_SECRET=$script:Config.jwt;DEEPSEEK_API_KEY=$DeepSeekKey;QWEN_API_KEY=$QwenKey}
    $script:App=New-Object Diagnostics.Process
    $script:App.StartInfo=Process-Info (Join-Path $script:Root 'runtime/java/bin/java.exe') @('-Dfile.encoding=UTF-8','-Xms64m','-Xmx512m','-jar',(Join-Path $script:Root 'app/study-server.jar'),'--server.address=127.0.0.1',"--server.port=$script:WebPort",'--app.web.enabled=true',"--spring.web.resources.static-locations=$webUri", "--logging.file.name=$(Join-Path $script:Root 'logs/server.log')",'--mybatis-plus.configuration.log-impl=org.apache.ibatis.logging.nologging.NoLoggingImpl') $envs
    Note '正在启动学习平台……';[void]$script:App.Start()
    # Clear launcher copies immediately; the child's environment lasts for this session only.
    $envs.Clear();$DeepSeekKey='';$QwenKey=''
    foreach ($name in @('DEEPSEEK_API_KEY','QWEN_API_KEY')) {$script:App.StartInfo.EnvironmentVariables.Remove($name)}
    $ready=$false
    for ($i=0;$i -lt 120;$i++) {
        Check-Cancel
        if ($script:App.HasExited) {throw '后端启动失败，请查看logs/server.log。'}
        if (Backend-Ready $script:WebPort) {$ready=$true;break}
        if (-not $Headless) {[Windows.Forms.Application]::DoEvents()}
        Start-Sleep -Milliseconds 300
    }
    if (-not $ready) {throw '等待平台启动超时，请查看logs/server.log。'}
    $script:Running=$true
    $script:Url="http://127.0.0.1:$script:WebPort"
    [IO.File]::WriteAllText((Join-Path $script:Data 'run.json'),(@{url=$script:Url;webPort=$script:WebPort;databasePort=$script:DbPort;launcherPid=$PID;javaPid=$script:App.Id;mysqlPid=$script:Db.Id} | ConvertTo-Json),$utf8)
    Note "启动成功：$script:Url  管理员：admin / Admin@123"
    if (-not $NoBrowser) {Start-Process $script:Url | Out-Null}
}

try {
    [void][IO.Directory]::CreateDirectory($script:Data)
    try {$script:Lock=[IO.File]::Open((Join-Path $script:Data 'launcher.lock'),[IO.FileMode]::OpenOrCreate,[IO.FileAccess]::ReadWrite,[IO.FileShare]::None)}
    catch {throw '这一份平台已启动，请使用已有启动器窗口；不要重复启动。'}
    Mount-PortablePath
    if ($Headless) {
        Start-Platform
        while (-not (Test-Path -LiteralPath (Join-Path $script:Data 'stop.request'))) {
            if ($script:App.HasExited -or $script:Db.HasExited) {throw '服务意外退出，请查看logs。'}
            Start-Sleep -Milliseconds 300
        }
        Remove-Item -LiteralPath (Join-Path $script:Data 'stop.request') -Force
    } else {
        Add-Type -AssemblyName System.Windows.Forms
        Add-Type -AssemblyName System.Drawing
        [Windows.Forms.Application]::EnableVisualStyles()
        $form=New-Object Windows.Forms.Form
        $form.Text='在线学习平台 · 便携启动器';$form.ClientSize=New-Object Drawing.Size(570,330)
        $form.StartPosition='CenterScreen';$form.FormBorderStyle='FixedDialog';$form.MaximizeBox=$false
        $form.Font=New-Object Drawing.Font('Microsoft YaHei UI',10)
        $form.Add_Shown({[void][StudyPortableDrive]::ShowWindow($form.Handle,5);$form.Activate()})
        $labels=@('DeepSeek API Key（可选）','千问 API Key（可选）')
        $boxes=@()
        for ($i=0;$i -lt 2;$i++) {
            $label=New-Object Windows.Forms.Label;$label.Text=$labels[$i];$label.SetBounds(22,22+75*$i,520,26);$form.Controls.Add($label)
            $box=New-Object Windows.Forms.TextBox;$box.SetBounds(22,50+75*$i,520,29);$box.UseSystemPasswordChar=$true;$form.Controls.Add($box);$boxes+=,$box
        }
        $hint=New-Object Windows.Forms.Label;$hint.Text='密钥仅本次使用，下次启动需重新填写。均留空可使用普通学习功能。';$hint.SetBounds(22,165,525,48);$form.Controls.Add($hint)
        $start=New-Object Windows.Forms.Button;$start.Text='启动平台';$start.SetBounds(22,221,155,40);$form.Controls.Add($start)
        $open=New-Object Windows.Forms.Button;$open.Text='打开浏览器';$open.SetBounds(201,221,155,40);$open.Enabled=$false;$form.Controls.Add($open)
        $stop=New-Object Windows.Forms.Button;$stop.Text='停止服务';$stop.SetBounds(382,221,155,40);$stop.Enabled=$false;$form.Controls.Add($stop)
        $script:Status=New-Object Windows.Forms.Label;$script:Status.Text='准备就绪。首次启动会初始化数据库，请保持窗口打开。';$script:Status.SetBounds(22,275,525,48);$form.Controls.Add($script:Status)
        $start.Add_Click({
            $script:Busy=$true;$start.Enabled=$false
            try {
                $dk=$boxes[0].Text.Trim();$qk=$boxes[1].Text.Trim();$boxes[0].Clear();$boxes[1].Clear()
                Start-Platform $dk $qk
                $dk='';$qk='';$open.Enabled=$true;$stop.Enabled=$true
            } catch {
                Stop-Platform
                [void][Windows.Forms.MessageBox]::Show($_.Exception.Message,'启动未完成')
                $start.Enabled=$true
            } finally {$script:Busy=$false}
        })
        $open.Add_Click({if ($script:Running) {Start-Process $script:Url | Out-Null}})
        $stop.Add_Click({$script:Busy=$true;try {Stop-Platform;$start.Enabled=$true;$open.Enabled=$false;$stop.Enabled=$false} finally {$script:Busy=$false}})
        $form.Add_FormClosing({param($sender,$eventArgs)
            if ($script:Busy) {$script:CancelRequested=$true;$eventArgs.Cancel=$true;return}
            Stop-Platform
        })
        $timer=New-Object Windows.Forms.Timer
        $timer.Interval=500
        $timer.Add_Tick({
            $request=Join-Path $script:Data 'stop.request'
            if (-not $script:Busy -and (Test-Path -LiteralPath $request)) {
                Remove-Item -LiteralPath $request -Force
                $form.Close()
            }
        })
        $timer.Start()
        [void][IO.Directory]::CreateDirectory((Join-Path $script:Root 'logs'))
        [IO.File]::AppendAllText((Join-Path $script:Root 'logs/launcher.log'),"GUI ready`r`n",$utf8)
        $dialogResult=$form.ShowDialog()
        $timer.Stop();$timer.Dispose()
        [IO.File]::AppendAllText((Join-Path $script:Root 'logs/launcher.log'),("GUI closed: "+$dialogResult+"`r`n"),$utf8)
        $form.Dispose()
    }
} catch {
    try {
        [void][IO.Directory]::CreateDirectory((Join-Path $script:Root 'logs'))
        [IO.File]::AppendAllText((Join-Path $script:Root 'logs/launcher.log'),($_.Exception.Message+"`r`n"),$utf8)
    } catch {}
    if ($Headless) {Write-Error $_.Exception.Message}
    else {Add-Type -AssemblyName System.Windows.Forms;[void][Windows.Forms.MessageBox]::Show($_.Exception.Message,'在线学习平台')}
} finally {
    Stop-Platform
    if ($null -ne $script:Lock) {$script:Lock.Dispose()}
    Unmount-PortablePath
}
