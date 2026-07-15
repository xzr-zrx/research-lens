# 本次功能改造说明

## 已完成的核心功能

1. **分级关键词研究卡片**
   - 新增核心关键词、方法关键词、扩展关键词、排除关键词和关键词组合。
   - 兼容旧版 `keywordsEn`；旧卡片会自动将原英文关键词作为核心关键词读取。
   - 前端支持新增、删除、移动关键词分类和编辑组合词，保存后可以重新检索。

2. **论文检索与排序**
   - 检索式按核心对象、问题、方法、扩展方向和缩写组合生成。
   - OpenAlex 与 arXiv 分别转换成兼容的查询语法。
   - OpenAlex 自定义年份改为通过官方 `filter=from_publication_date...,to_publication_date...` 提交。
   - 最终得分：关键词 60% + 语义 30% + 论文质量 10%。
   - 关键词内部：核心覆盖 40% + 标题匹配 25% + 组合匹配 20% + 摘要匹配 15%。
   - 支持排除词、关键词硬过滤和语义高相似度兜底。

3. **全文优先加入 RAG**
   - 本地已上传全文优先。
   - 依次尝试 arXiv PDF、OpenAlex PDF、论文公开落地页和 DOI 落地页。
   - 落地页只识别公开声明的 `citation_pdf_url`、`application/pdf` 或直接 PDF 链接。
   - 不携带登录态，不绕过付费墙。
   - 校验 URL、重定向、内网地址、下载超时、大小、PDF 文件头、HTML 错误页和扫描版无文本问题。
   - 下载、解析或 Milvus 写入失败时不会误标为全文已入库，也不会静默降级为摘要。

4. **知识库状态与批量入库**
   - 状态包括：未入库、获取全文、解析全文、生成向量、仅摘要、全文已入库、需要手动上传、失败。
   - “仅摘要入库”必须由用户明确选择。
   - 支持多选论文后批量获取全文，并复用任务轮询展示进度和每篇论文状态。
   - 同一篇论文在不同项目中按 `ProjectPaper` 独立建立向量索引，避免跨项目覆盖。

5. **指定范围知识问答**
   - 支持全部知识库、当前项目、指定论文和当前勾选论文。
   - 指定多篇论文时按每篇论文分别召回，再合并、去重和重排。
   - 实验数据问题优先 Results、Experimental Setup、Table、Appendix 等全文片段。
   - 仅摘要时拒绝编造具体实验数值，并保留原有数值证据校验。

6. **前端体验**
   - 增加五步流程条、关键词编辑器、论文状态标签、入库进度条和具体错误原因。
   - 增加全文状态筛选、近三年筛选以及综合/关键词/语义/年份/引用量排序。
   - 展示综合分、关键词分、语义分、质量分、命中关键词、命中组合和推荐原因。
   - 增加快捷对比问题和指定论文选择器。

## 数据库变化

新增字段由现有 `spring.jpa.hibernate.ddl-auto=update` 自动创建，同时提供：

```text
src/main/resources/db/manual-migration/V2__keyword_ranking_and_fulltext_status.sql
```

旧版已入库论文无法可靠确认是否为全文，因此启动迁移时安全标记为 `ABSTRACT_ONLY`，不会误标为全文证据。

## 新增或修改的主要接口

```text
POST /api/research/projects/{id}/papers/{projectPaperId}/knowledge
POST /api/research/projects/{id}/papers/{projectPaperId}/knowledge/abstract
POST /api/research/projects/{id}/papers/{projectPaperId}/knowledge/retry
POST /api/research/projects/{id}/papers/knowledge/batch
POST /api/research/projects/{id}/papers/{projectPaperId}/fulltext
POST /api/chat
```

`POST /api/chat` 新增 `scope`、`projectId` 和 `paperIds`。

## 检查结果

- 前端：Vite 生产构建通过。
- Vue：单文件组件编译通过。
- Java：92 个 Java 源文件语法解析通过，无语法错误。
- 后端 Maven 完整构建未执行：当前处理环境没有 Maven，且系统软件源无法完成安装。请在安装 Maven 3.9+ 的本机执行：

```bash
mvn clean package -DskipTests
```

## 启动验证

```bash
# 1. 启动 Milvus
docker compose -f vector-database.yml up -d

# 2. 启动后端
mvn spring-boot:run

# 3. 启动前端
cd frontend
npm install
npm run dev
```

重点验证：生成并编辑分级关键词、重新检索、查看 60/30/10 评分详情、批量全文入库、手动上传失败回退，以及指定多篇论文对比问答。
