# 交付包第三方运行时

便携版携带以下未经修改的运行组件，以及运行所需的部分原始文件：

- Eclipse Temurin Java 17 Windows x64 JRE。官方来源：https://adoptium.net/temurin/releases/ 。保留运行时随附的LICENSE、NOTICE、legal目录及其第三方许可。运行时bin目录随附的Microsoft C/C++运行库文件同时用于包内MySQL程序，相关许可保留在Java运行时中。
- MySQL Community Server 8.0.41 Windows x64。官方来源：https://downloads.mysql.com/archives/community/ 。保留MySQL的LICENSE、README、构建与来源说明；不携带作者电脑上的Data、Uploads、my.ini或系统服务配置。对应源代码可通过同一官方归档页选择8.0.41的Source Code下载。
- Spring Boot、Vue及其依赖由项目构建工具按声明版本产生，后端依赖及其随附声明保留在可执行Jar中；前端生产依赖的原始LICENSE、NOTICE等文件另行汇集到便携版的licenses/frontend目录。

运行时二进制仅放入便携版。源码版本保留依赖声明、构建脚本与本说明，发布者重新分发时应一并保留各组件的许可和声明。
