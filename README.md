ResearchLens 本地科研助手

ResearchLens 是一个本地运行的科研辅助系统，面向论文检索、论文管理和个人知识库问答场景。

用户输入研究方向或实验想法后，系统可以生成结构化研究卡片，并从检索相关论文。用户可以选择论文、自动获取开放全文并加入 RAG 知识库，随后针对当前项目或指定论文进行总结、提问和对比。

主要功能

- 根据研究方向生成可编辑的研究卡片和英文关键词；
- 从 OpenAlex、arXiv 检索论文，并提供可解释的相关性排序；
- 自动获取公开可访问的论文全文，解析后写入 Milvus；
- 明确区分“仅摘要入库”和“全文已入库”；
- 支持批量入库、论文筛选、指定论文问答和多论文对比；
- 项目、论文和任务数据保存在本地 H2 数据库中。

技术组成

- 后端：Java 17、Spring Boot 3、Spring Data JPA；
- 前端：Vue 3、Vite；
- 大模型与向量模型：DashScope；
- 论文来源：OpenAlex、arXiv；
- 向量数据库：Milvus；
- 本地数据库：H2；
- 运行环境：Docker Compose。

---

一、推荐方式：Docker 部署

Docker Compose 会启动 Milvus、MinIO、etcd、Attu 和后端服务。前端仍需单独启动。

1. 安装环境

请先安装：

- Docker Desktop；
- Node.js 18 或更高版本；
- npm。

Windows 用户需要启动 Docker Desktop，并使用 WSL 2 后端。

2. 配置 API Key

在项目根目录复制环境变量模板：

Windows PowerShell

```powershell
Copy-Item .env.example .env
```

Linux / macOS

```bash
cp .env.example .env
```

打开 `.env`，填写自己的 Key：

```env
DASHSCOPE_API_KEY=你的DashScope_API_Key
OPENALEX_API_KEY=你的OpenAlex_API_Key
```

说明：

- DashScope Key 用于研究卡片生成、论文分析、Embedding 和知识问答；
- OpenAlex Key 用于论文检索；
- 不要将包含真实 Key 的 `.env` 提交到公开仓库。

3. 启动后端和 Milvus

在项目根目录执行：

```bash
docker compose up -d --build
```

首次构建需要下载镜像和 Maven 依赖，等待时间可能较长。

查看服务状态：

```bash
docker compose ps
```

查看后端日志：

```bash
docker compose logs -f backend
```

后端默认地址：

```text
http://localhost:9900
```

Milvus 管理页面 Attu：

```text
http://localhost:8000
```

4. 启动前端

打开新的终端，在项目根目录执行：

```bash
cd frontend
npm install
npm run dev
```

浏览器访问：

```text
http://localhost:5173
```

5. 停止服务

```bash
docker compose down
```

该命令不会删除本地数据库、上传文件和 Milvus 数据。

---

二、本地开发启动

本方式适合需要修改和调试后端代码的开发者。

1. 安装环境

请安装：

- JDK 17；
- Maven 3.9 或更高版本；
- Node.js 18 或更高版本；
- Docker Desktop。

2. 启动 Milvus

Windows PowerShell

```powershell
.\scripts\start-infra.ps1
```

Linux / macOS

```bash
chmod +x scripts/start-infra.sh
./scripts/start-infra.sh
```

也可以直接执行：

```bash
docker compose -f vector-database.yml up -d
```

3. 配置后端环境变量

Windows PowerShell

```powershell
$env:SPRING_AI_DASHSCOPE_API_KEY="你的DashScope_API_Key"
$env:ACADEMIC_OPENALEX_API_KEY="你的OpenAlex_API_Key"
```

Linux / macOS

```bash
export SPRING_AI_DASHSCOPE_API_KEY="你的DashScope_API_Key"
export ACADEMIC_OPENALEX_API_KEY="你的OpenAlex_API_Key"
```

直接使用 Maven 启动时，项目根目录下的 `.env` 不会被 Spring Boot 自动读取，因此需要设置以上环境变量。

4. 启动后端

在项目根目录执行：

```bash
mvn spring-boot:run
```

后端地址：

```text
http://localhost:9900
```

H2 控制台：

```text
http://localhost:9900/h2-console
```

连接参数：

```text
JDBC URL: jdbc:h2:file:./data/research-assistant
User Name: sa
Password: 留空
```

5. 启动前端

打开新的终端：

```bash
cd frontend
npm install
npm run dev
```

访问：

```text
http://localhost:5173
```

---

三、基本使用流程

1. 新建研究项目并输入研究方向；
2. 生成研究卡片，检查并编辑英文关键词；
3. 设置论文时间范围并开始检索；
4. 查看关键词、语义和质量评分；
5. 选择一篇或多篇论文，点击“获取全文并入库”；
6. 自动获取失败时，可以重新获取或手动上传 PDF；
7. 在知识问答页面选择当前项目或指定论文进行提问和对比。

系统只会自动获取明确公开访问的论文 PDF，不会绕过登录、付费墙或版权限制。

---

四、数据保存与升级

主要数据保存在：

```text
data/       H2 项目、论文和任务数据
uploads/    研究资料和论文全文
volumes/    Milvus、MinIO 和 etcd 数据
```

升级项目代码时不要删除这些目录。

使用 Docker 更新后端代码后，建议重新构建镜像：

```bash
docker compose down
docker compose build --no-cache backend
docker compose up -d
```

如果页面提示接口不存在，例如：

```text
No static resource api/research/...
```

通常表示前端已经更新，但仍在运行旧版后端，需要重新构建并启动后端镜像。

---

五、常用命令

查看后端日志：

```bash
docker compose logs -f backend
```

查看全部容器：

```bash
docker compose ps
```

重新启动后端：

```bash
docker compose restart backend
```

构建前端生产文件：

```bash
cd frontend
npm run build
```

构建结果位于：

```text
frontend/dist/
```
