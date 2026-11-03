# 面向个性化学习的智能题库平台设计与实现

Spring Boot 4、Vue 3、MySQL 8实现的在线学习与题库管理系统。包括课程层级、客观题练习与判题、答题卡、错题、收藏、学习统计、Excel批量导入导出，以及三个可选的AI辅助入口。

## 数据与演示账号

随附SQL保留全部703道题、2236个选项、4个科目、15个章节、70个知识点。包含原有题干、选项、答案、解析、难度和状态。题目创建人统一关联演示管理员，原个人账号及答题、错题、收藏记录已移除。两份交付物使用相同SQL。

| 角色 | 用户名 | 密码 |
| --- | --- | --- |
| 管理员 | admin | Admin@123 |
| 学生 | student | Student@123 |

这些账号只用于本地演示。普通学习功能不需要AI密钥；AI调用需要用户自行提供有效密钥并连接网络。

## 开发运行

需要JDK 17、MySQL 8、Node.js 22 LTS及npm。后端带有Maven Wrapper。

1. 首次部署在独立MySQL环境导入`sql/study_system.sql`。SQL会重建同名业务表，勿向有用数据的数据库重复导入。
2. 在后端终端设置环境变量（填写自己的数据库密码）：

```powershell
$env:DB_URL='jdbc:mysql://127.0.0.1:3306/study_system?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true'
$env:DB_USERNAME='your_mysql_user'
$env:DB_PASSWORD='your_mysql_password'
# 可选：稳定JWT密钥应为随机生成的Base64字符串。
# 不设置时使用本次进程临时密钥，重启后旧登录凭证失效。
# $env:JWT_SECRET='your_base64_secret'
# 可选：两者均不设置时，普通学习功能仍可使用。
# $env:DEEPSEEK_API_KEY='your_key'
# $env:QWEN_API_KEY='your_key'
cd study-server
.\mvnw.cmd spring-boot:run
```

3. 新开终端运行前端：

```powershell
cd study-web
npm ci
npm run dev
```

访问`http://localhost:30080`，Vite将`/api`转发至8080端口的后端。

## 构建和检查

```powershell
cd study-server
.\mvnw.cmd test
.\mvnw.cmd package -DskipTests
cd ../study-web
npm run test
npm run build
```

完整后端测试依赖已经导入SQL的隔离测试数据库，切勿在有个人学习数据的库上运行测试。

生产演示可让后端统一提供前端：启动Jar时添加`--app.web.enabled=true`及`--spring.web.resources.static-locations=file:/绝对路径/dist/`。该开关只放行明确列出的页面和静态资源；`/api`仍执行原有认证和角色检查。默认关闭，不影响开发模式。

## 项目结构与交付

- `study-server/`：后端源码、测试、Maven Wrapper。
- `study-web/`：前端源码、测试、依赖锁文件。
- `sql/study_system.sql`：唯一的公开演示数据库初始化文件。
- `docs/DEPLOYMENT.md`：部署与打包说明。
- `delivery/`：便携启动器、构建工具和第三方运行时说明。

本源码目录不含Java/MySQL二进制、依赖缓存、个人论文、原数据库数据目录及私有Git历史。上传GitHub时上传本目录内容；便携版压缩包可另外提供下载。平台保持前后端分层，不包含在线搜索或聊天数据库持久化。
