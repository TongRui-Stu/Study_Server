# 对外交付和便携部署

本项目由同一代码生成两份交付物：便携运行版面向Windows 10/11 x64体验；GitHub源码版用于代码审阅、开发与自行部署。源码导出采用明确目录白名单，不复制工作区、原私有SQL及Git历史。

便携版包含Temurin Java 17、MySQL Community 8.0.41、后端Jar、Vue构建产物、启动窗口和脱敏演示SQL。首次启动自动创建独立数据目录，所有服务绑定127.0.0.1并选择空闲端口，不安装服务或修改系统环境变量。密钥只进入本次后端进程，窗口重启或停止再启动都需要重新填写；业务数据保存在data目录。

为适配中文和空格目录，使用DefineDosDevice为本包建立空闲临时盘符，正常退出时按确切目标移除；不覆盖已有盘符。MySQL使用`--no-monitor`避免Windows监控子进程脱离启动器。启动器兼容Windows自带PowerShell 5.1，通过标准输入的UTF-8字节流导入SQL。

## 生成步骤

1. 如需刷新演示库，使用`delivery/export_demo.py`。提供MySQL客户端路径、JDK路径、Spring Security Crypto与Commons Logging类路径，数据库密码仅通过当前进程的`MYSQL_PWD`提供。脚本一致性导出原库，在唯一命名的隔离库清理原账号和个人学习记录，保留题库字段与ID，仅将题目创建人映射到演示管理员，并用内容SHA-256核查原库未变化。工作目录中的原始备份不可公开。
2. 在隔离库运行完整Maven测试，运行前端测试与`npm run build`，生成后端可执行Jar。
3. 从Adoptium官方取得Windows x64 Java 17 JRE，验证官方校验和；准备MySQL Community 8.0.41运行文件。保留组件原始许可。
4. 用Python 3运行打包脚本，输出目录必须不存在：

```powershell
python delivery/build.py --output release/output --java-runtime PATH_TO_JRE --mysql-runtime PATH_TO_MYSQL --demo-sql delivery/demo.sql
```

从已导出的源码版重新打包时，`--demo-sql`指向`sql/study_system.sql`。前端dist和后端target必须先构建完成。不要把运行后的data目录放回发行包。

5. 在另一份测试副本验证首次初始化、登录、权限、练习、导出、页面刷新、停止与二次启动数据保留。分发原始干净目录，非已运行的测试目录。

## 两份数据库的一致性

两份交付物使用字节一致的演示SQL。保留全部课程层级、题干、选项、答案、解析、题型、难度、状态和时间字段；移除个人账号、答题、错题、收藏数据。演示账号为`admin / Admin@123`及`student / Student@123`，密码以BCrypt哈希写入SQL。首次导入后不自动再次导入。

原项目的`sql/study_system.sql`仍是本地完整备份，包含个人历史记录。公开上传应使用生成的GitHub源码版，不能直接上传本工作区或推送原私有Git历史。两者文件名相同但用途不同。

## 前端托管开关

`app.web.enabled`默认关闭。便携版启动时打开该开关，并让`spring.web.resources.static-locations`指向web目录。明确的Vue页面路由回退至index.html，静态资源允许GET；API认证、管理员权限及原Controller→Service→Mapper分层保持不变。

## 测试与故障

启动器头部有`-Headless -NoBrowser`验证入口；启动成功后data/run.json提供本次端口和自有进程ID，写入data/stop.request请求停止。该入口不读取AI密钥。正常用户通过窗口操作。日常日志位于logs，不含密钥输入。

Windows架构、系统版本、第三方运行时许可和兼容性以发行包随附说明为准。本机验证不等同于已覆盖所有电脑型号或全新Windows虚拟机。
