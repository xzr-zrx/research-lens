# 开发说明

本地版已经完成三段核心流程：

1. 研究项目、研究输入和可确认的结构化研究卡片；
2. OpenAlex/arXiv 多查询检索、论文标准化、去重与综合排序；
3. 逐篇分析、证据校验、对比报告、论文收藏和带来源的 RAG 问答。

当前边界：

- 外部论文默认基于标题和摘要分析；全文需要用户手动上传；
- 扫描 PDF 暂不支持 OCR；
- 系统用于发现相关工作和整理差异，不证明绝对创新性；
- API Key 在 `application.yml` 中以占位符提供，由用户在本机自行替换。

交付前已完成：POM XML 检查、Java 源码语法扫描、Vue SFC 脚本/模板编译检查、前端 JavaScript 语法检查和敏感信息扫描。由于当前沙箱没有 Maven，且原压缩包内的前端依赖是 Windows 平台版本，未在沙箱执行完整 Maven/Vite 构建；请按 README 在本机执行 `mvn test`、`mvn clean package` 和 `npm install && npm run build`。
