<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { marked } from 'marked'
import { api } from './api'

const route = ref(parseRoute())
const projects = ref([])
const loading = ref(false)
const error = ref('')
const notice = ref('')
let taskTimer = null

const currentProject = ref(null)
const inputs = ref([])
const profile = reactive(emptyProfile())
const queries = ref([])
const papers = ref([])
const task = ref(null)
const report = ref(null)
const knowledgeFiles = ref([])
const chatMessages = ref([])
const chatInput = ref('')
const chatProjects = ref([])
const chatPapers = ref([])
const chatScope = reactive({ scope: 'ALL_KNOWLEDGE', projectId: null, paperIds: [] })
const keywordDraft = reactive({ coreKeywords: '', methodKeywords: '', expandedKeywords: '', excludedKeywords: '' })
const paperFilter = reactive({ availability: 'ALL', recentOnly: false, sort: 'FINAL' })

const visiblePapers = computed(() => {
  let result = [...papers.value]
  if (paperFilter.availability === 'AVAILABLE') result = result.filter(p => p.fullTextAvailable || p.paper?.localFullTextPath)
  if (paperFilter.availability === 'FULL_TEXT_INDEXED') result = result.filter(p => resolvedKnowledgeStatus(p) === 'FULL_TEXT_INDEXED')
  if (paperFilter.availability === 'NOT_INDEXED') result = result.filter(p => ['NOT_INDEXED','FAILED','MANUAL_UPLOAD_REQUIRED'].includes(resolvedKnowledgeStatus(p)))
  if (paperFilter.recentOnly) result = result.filter(p => Number(p.paper?.publicationYear || 0) >= currentYear() - 2)
  const selectors = {
    FINAL: p => p.projectPaper.finalScore,
    KEYWORD: p => p.projectPaper.keywordScore,
    SEMANTIC: p => p.projectPaper.embeddingScore,
    YEAR: p => p.paper?.publicationYear,
    CITATION: p => p.paper?.citationCount
  }
  const selector = selectors[paperFilter.sort] || selectors.FINAL
  return result.sort((a,b) => Number(selector(b) || 0) - Number(selector(a) || 0))
})

const newProject = reactive({ name: '', text: '', file: null })
const showTimeEditor = ref(false)
const timeRange = reactive({
  preset: 'RECENT_5_YEARS',
  startYear: null,
  endYear: null
})
const timeRangeLabel = computed(() => {
  if (currentProject.value) {
    const s = currentProject.value.paperStartYear
    const e = currentProject.value.paperEndYear
    if (s && e) return `${s}—${e}`
    return '不限时间'
  }
  return ''
})
const projectId = computed(() => route.value.params.id)
const renderedReport = computed(() => report.value?.reportMarkdown ? marked.parse(report.value.reportMarkdown) : '')

function emptyProfile() {
  return {
    domain: '', researchObject: '', researchProblem: '', proposedMethod: '',
    variables: [], keywordsZh: [], keywordsEn: [], coreKeywords: [], methodKeywords: [], expandedKeywords: [],
    excludedKeywords: [], keywordGroups: [], relatedFields: [], researchQuestions: [], confirmed: false
  }
}

function parseRoute() {
  const hash = location.hash.replace(/^#/, '') || '/projects'
  const parts = hash.split('/').filter(Boolean)
  // /projects/new → 新建项目页（必须优先匹配，避免 "new" 被当作项目 ID）
  if (parts[0] === 'projects' && parts[1] === 'new') return { name: 'new', params: {} }
  if (parts[0] === 'projects' && parts[1] && parts[2]) {
    return { name: parts[2], params: { id: Number(parts[1]) } }
  }
  // 纯数字字符串才算有效的项目 ID
  if (parts[0] === 'projects' && parts[1] && /^\d+$/.test(parts[1])) return { name: 'project', params: { id: Number(parts[1]) } }
  return { name: parts[0] || 'projects', params: {} }
}

function go(path) { location.hash = path }
function clearMessage() { error.value = ''; notice.value = '' }
function showError(e) { error.value = e?.message || String(e); notice.value = '' }
function showNotice(text) { notice.value = text; error.value = '' }

function currentYear() { return new Date().getFullYear() }
function resolveTimeRangeYears(preset) {
  const y = currentYear()
  switch (preset) {
    case 'RECENT_3_YEARS': return { start: y - 2, end: y }
    case 'RECENT_5_YEARS': return { start: y - 4, end: y }
    case 'SINCE_2020': return { start: 2020, end: y }
    default: return { start: null, end: null }
  }
}
function timeRangeValid() {
  const p = timeRange.preset
  if (p === 'ALL_TIME') return true
  if (p === 'CUSTOM') {
    if (!timeRange.startYear || !timeRange.endYear) return false
    if (timeRange.startYear > timeRange.endYear) return false
    if (timeRange.endYear > currentYear()) return false
    return true
  }
  return ['RECENT_3_YEARS', 'RECENT_5_YEARS', 'SINCE_2020'].includes(p)
}
function initTimeRange(project) {
  if (project) {
    timeRange.preset = project.paperTimePreset || 'RECENT_5_YEARS'
    timeRange.startYear = project.paperStartYear || null
    timeRange.endYear = project.paperEndYear || null
  }
}
function onPresetChange() {
  if (timeRange.preset !== 'CUSTOM' && timeRange.preset !== 'ALL_TIME') {
    const r = resolveTimeRangeYears(timeRange.preset)
    timeRange.startYear = r.start
    timeRange.endYear = r.end
  }
}
async function saveTimeRange() {
  if (!timeRangeValid()) return showError(new Error('请选择合法的论文时间范围'))
  await withLoading(async () => {
    const start = timeRange.preset === 'ALL_TIME' ? null : (timeRange.preset === 'CUSTOM' ? timeRange.startYear : resolveTimeRangeYears(timeRange.preset).start)
    const end = timeRange.preset === 'ALL_TIME' ? null : (timeRange.preset === 'CUSTOM' ? timeRange.endYear : resolveTimeRangeYears(timeRange.preset).end)
    const data = await api.updateSearchPreference(projectId.value, timeRange.preset, start, end)
    currentProject.value = data
    showNotice('检索时间范围已更新')
  })
}

async function withLoading(action) {
  clearMessage(); loading.value = true
  try { return await action() } catch (e) { showError(e); throw e } finally { loading.value = false }
}

async function loadProjects() {
  projects.value = await api.listProjects()
}

async function loadProject(id) {
  const data = await api.getProject(id)
  currentProject.value = data.project
  initTimeRange(data.project)
  inputs.value = data.inputs || []
  Object.assign(profile, emptyProfile(), data.profile || {})
}

async function onRouteChanged() {
  route.value = parseRoute()
  clearMessage()
  stopTaskPolling()
  if (route.value.name === 'new') {
    timeRange.preset = 'RECENT_5_YEARS'
    onPresetChange()
  }
  try {
    if (route.value.name === 'projects') await loadProjects()
    if (route.value.params.id) await loadProject(route.value.params.id)
    if (route.value.name === 'search') await loadSearch(route.value.params.id)
    if (route.value.name === 'report') await loadReport(route.value.params.id)
    if (route.value.name === 'knowledge') await loadKnowledge()
    if (route.value.name === 'chat') { chatProjects.value = await api.listProjects(); await loadChatPapers(true) }
  } catch (e) { showError(e) }
}

async function createProject() {
  if (!newProject.name.trim()) return showError(new Error('请输入项目名称'))
  if (!timeRangeValid()) return showError(new Error('请选择合法的论文时间范围'))
  await withLoading(async () => {
    const start = timeRange.preset === 'ALL_TIME' ? null : (timeRange.preset === 'CUSTOM' ? timeRange.startYear : resolveTimeRangeYears(timeRange.preset).start)
    const end = timeRange.preset === 'ALL_TIME' ? null : (timeRange.preset === 'CUSTOM' ? timeRange.endYear : resolveTimeRangeYears(timeRange.preset).end)
    const project = await api.createProject(newProject.name.trim(), timeRange.preset, start, end)
    if (newProject.text.trim()) await api.addTextInput(project.id, newProject.text.trim())
    if (newProject.file) await api.addFileInput(project.id, newProject.file)
    newProject.name = ''; newProject.text = ''; newProject.file = null
    go(`/projects/${project.id}/profile`)
  })
}

async function deleteProject(id) {
  if (!confirm('确认删除该项目？本地上传文件不会自动清除。')) return
  await withLoading(async () => { await api.deleteProject(id); await loadProjects() })
}

async function addInputText() {
  const text = prompt('请输入补充的研究想法或实验记录：')
  if (!text?.trim()) return
  await withLoading(async () => { await api.addTextInput(projectId.value, text.trim()); await loadProject(projectId.value) })
}

async function addInputFile(event) {
  const file = event.target.files?.[0]
  if (!file) return
  await withLoading(async () => { await api.addFileInput(projectId.value, file); await loadProject(projectId.value) })
  event.target.value = ''
}

async function generateProfile() {
  await withLoading(async () => {
    const data = await api.generateProfile(projectId.value)
    Object.assign(profile, emptyProfile(), data)
    showNotice('研究卡片已生成，请检查并确认。')
  })
}

function linesToArray(value) {
  if (Array.isArray(value)) return value
  return String(value || '').split('\n').map(v => v.trim()).filter(Boolean)
}

async function saveProfile(confirmProfile = false) {
  await withLoading(async () => {
    const payload = {
      ...profile,
      variables: linesToArray(profile.variables),
      keywordsZh: linesToArray(profile.keywordsZh),
      coreKeywords: linesToArray(profile.coreKeywords),
      methodKeywords: linesToArray(profile.methodKeywords),
      expandedKeywords: linesToArray(profile.expandedKeywords),
      excludedKeywords: linesToArray(profile.excludedKeywords),
      keywordGroups: normalizeKeywordGroups(profile.keywordGroups),
      keywordsEn: [...new Set([...linesToArray(profile.coreKeywords), ...linesToArray(profile.methodKeywords), ...linesToArray(profile.expandedKeywords)])],
      relatedFields: linesToArray(profile.relatedFields),
      researchQuestions: linesToArray(profile.researchQuestions),
      confirmed: confirmProfile
    }
    const data = await api.saveProfile(projectId.value, payload)
    Object.assign(profile, emptyProfile(), data)
    showNotice(confirmProfile ? '研究卡片已确认，可以开始检索。' : '研究卡片已保存。')
  })
}

async function startSearch() {
  if (!profile.confirmed) return showError(new Error('请先确认研究卡片'))
  await withLoading(async () => {
    task.value = await api.startSearch(projectId.value)
    startTaskPolling(task.value.id, async () => await loadSearch(projectId.value))
  })
}

async function loadSearch(id) {
  queries.value = await api.listQueries(id).catch(() => [])
  papers.value = await api.listPapers(id).catch(() => [])
  const saved = localStorage.getItem(`paper-filter-${id}`)
  if (saved) { try { Object.assign(paperFilter, JSON.parse(saved)) } catch (_) {} }
}
function savePaperFilter() { if (projectId.value) localStorage.setItem(`paper-filter-${projectId.value}`, JSON.stringify(paperFilter)) }

function startTaskPolling(taskId, onComplete) {
  stopTaskPolling()
  taskTimer = setInterval(async () => {
    try {
      task.value = await api.getTask(taskId)
      if (task.value.taskType === 'PAPER_KNOWLEDGE_BATCH' && projectId.value) await loadSearch(projectId.value)
      if (['COMPLETED', 'FAILED', 'INTERRUPTED'].includes(task.value.status)) {
        stopTaskPolling()
        if (task.value.status === 'COMPLETED') {
          await onComplete?.()
          showNotice(task.value.message || '任务完成')
        } else showError(new Error(task.value.errorMessage || task.value.message || '任务未完成'))
      }
    } catch (e) { stopTaskPolling(); showError(e) }
  }, 1500)
}
function stopTaskPolling() { if (taskTimer) clearInterval(taskTimer); taskTimer = null }

async function togglePaper(item, event) {
  const selected = event.target.checked
  item.projectPaper.selected = selected
  try { await api.selectPaper(projectId.value, item.projectPaper.id, selected) }
  catch (e) { item.projectPaper.selected = !selected; showError(e) }
}

async function startAnalysis() {
  if (!papers.value.some(p => p.projectPaper.selected)) return showError(new Error('请至少选择一篇论文'))
  await withLoading(async () => {
    task.value = await api.startAnalysis(projectId.value)
    startTaskPolling(task.value.id, async () => go(`/projects/${projectId.value}/report`))
  })
}

async function savePaper(item) {
  clearMessage(); item.projectPaper.knowledgeStatus = 'FETCHING_FULL_TEXT'
  try {
    task.value = await api.batchSavePapers(projectId.value, [item.projectPaper.id])
    startTaskPolling(task.value.id, async () => await loadSearch(projectId.value))
  } catch (e) { await loadSearch(projectId.value); showError(e) }
}
async function saveAbstractOnly(item) {
  if (!confirm('确认仅将标题和摘要加入知识库？摘要不能支持具体实验数据问答。')) return
  item.busy = true; clearMessage()
  try { await api.savePaperAbstract(projectId.value, item.projectPaper.id); await loadSearch(projectId.value); showNotice('已按你的选择仅将摘要加入知识库。') }
  catch (e) { showError(e) } finally { item.busy = false }
}
async function retryPaper(item) { await savePaper(item) }
async function batchSavePapers() {
  const ids = papers.value.filter(p => p.projectPaper.selected).map(p => p.projectPaper.id)
  if (!ids.length) return showError(new Error('请至少选择一篇论文'))
  await withLoading(async () => {
    task.value = await api.batchSavePapers(projectId.value, ids)
    startTaskPolling(task.value.id, async () => await loadSearch(projectId.value))
  })
}

async function uploadPaperFullText(item, event) {
  const file = event.target.files?.[0]
  if (!file) return
  await withLoading(async () => {
    await api.uploadFullText(projectId.value, item.projectPaper.id, file)
    await loadSearch(projectId.value)
    showNotice('全文已上传并写入知识库，后续分析将优先使用全文。')
  })
  event.target.value = ''
}

async function loadReport(id) {
  report.value = await api.getReport(id)
  papers.value = await api.listPapers(id).catch(() => [])
}

function exportReport() {
  if (!report.value?.reportMarkdown) return
  const blob = new Blob([report.value.reportMarkdown], { type: 'text/markdown;charset=utf-8' })
  const a = document.createElement('a')
  a.href = URL.createObjectURL(blob)
  a.download = `research-report-${projectId.value}-v${report.value.version}.md`
  a.click(); URL.revokeObjectURL(a.href)
}

async function loadKnowledge() { knowledgeFiles.value = await api.listKnowledge() }
async function uploadKnowledge(event) {
  const file = event.target.files?.[0]
  if (!file) return
  await withLoading(async () => { await api.uploadKnowledge(file); await loadKnowledge(); showNotice('文件已上传并建立索引。') })
  event.target.value = ''
}
async function deleteKnowledge(path) {
  if (!confirm('确认删除该文件及其向量索引？')) return
  await withLoading(async () => { await api.deleteKnowledge(path); await loadKnowledge() })
}
async function reindexKnowledge() {
  await withLoading(async () => { const result = await api.reindexKnowledge(); await loadKnowledge(); showNotice(`重建完成：成功 ${result.successCount}，失败 ${result.failCount}`) })
}

async function sendChat() {
  const message = chatInput.value.trim()
  if (!message || loading.value) return
  if (chatScope.scope !== 'ALL_KNOWLEDGE' && !chatScope.projectId) return showError(new Error('请先选择研究项目'))
  if (chatScope.scope === 'SPECIFIC_PAPERS' && !chatScope.paperIds.length) return showError(new Error('请至少选择一篇论文'))
  chatMessages.value.push({ role: 'user', content: message })
  chatInput.value = ''
  await withLoading(async () => {
    const history = chatMessages.value.slice(0, -1).map(({ role, content }) => ({ role, content }))
    const result = await api.chat(message, history, chatScope.scope, chatScope.projectId, chatScope.paperIds)
    chatMessages.value.push({ role: 'assistant', content: result.answer, sources: result.sources || [], diagnostics: result.diagnostics || null })
  }).catch(() => {})
}

function normalizeKeywordGroups(groups) {
  if (!Array.isArray(groups)) return []
  return groups.map(g => Array.isArray(g) ? g.map(v => String(v).trim()).filter(Boolean) : String(g).split('+').map(v => v.trim()).filter(Boolean)).filter(g => g.length >= 2)
}
function addKeyword(field) {
  const value = keywordDraft[field].trim(); if (!value) return
  profile[field] = [...new Set([...(profile[field] || []), value])]; keywordDraft[field] = ''
}
function removeKeyword(field, index) { profile[field] = (profile[field] || []).filter((_,i) => i !== index) }
function moveKeyword(field, index, target) {
  const value = profile[field]?.[index]; if (!value || target === field) return
  removeKeyword(field, index); profile[target] = [...new Set([...(profile[target] || []), value])]
}
function addKeywordGroup() { profile.keywordGroups = [...(profile.keywordGroups || []), ['', '']] }
function updateKeywordGroup(index, value) { profile.keywordGroups[index] = String(value).split('+').map(v => v.trim()).filter(Boolean) }
function removeKeywordGroup(index) { profile.keywordGroups = profile.keywordGroups.filter((_,i) => i !== index) }
async function loadChatPapers(preserveSelection = false) {
  const previous = preserveSelection ? [...chatScope.paperIds] : []
  if (!chatScope.projectId) { chatPapers.value = []; chatScope.paperIds = []; return }
  chatPapers.value = await api.listPapers(chatScope.projectId).catch(() => [])
  const validIds = new Set(chatPapers.value.map(item => item.paper.id))
  chatScope.paperIds = previous.filter(id => validIds.has(id))
}
function onChatProjectChange() { loadChatPapers(false) }
function toggleChatPaper(paperId, checked) {
  chatScope.paperIds = checked ? [...new Set([...chatScope.paperIds, paperId])] : chatScope.paperIds.filter(id => id !== paperId)
}
function askQuick(text) { chatInput.value = text; sendChat() }
async function startPaperChat(item) {
  chatScope.scope = 'SPECIFIC_PAPERS'
  chatScope.projectId = projectId.value
  await loadChatPapers(false)
  chatScope.paperIds = [item.paper.id]
  go('/chat')
}
function formatDate(value) { return value ? new Date(value).toLocaleString() : '-' }
function formatScore(value) { return Number(value || 0).toFixed(3) }
function arrayText(value) { return Array.isArray(value) ? value.join('\n') : String(value || '') }
function setArray(field, event) { profile[field] = linesToArray(event.target.value) }
function sourceLabel(level) { return level === 'FULL_TEXT' ? '全文' : '摘要' }
const knowledgeStatusMap = {
  NOT_INDEXED: '未入库', FETCHING_FULL_TEXT: '正在获取全文', PARSING_FULL_TEXT: '正在解析全文',
  INDEXING: '正在生成向量', ABSTRACT_ONLY: '仅摘要入库', FULL_TEXT_INDEXED: '全文已入库',
  MANUAL_UPLOAD_REQUIRED: '需要手动上传', FAILED: '全文入库失败'
}
function resolvedKnowledgeStatus(item) {
  if (item.projectPaper.knowledgeStatus) return item.projectPaper.knowledgeStatus
  if (item.projectPaper.savedToKnowledge && item.projectPaper.evidenceLevel === 'FULL_TEXT') return 'FULL_TEXT_INDEXED'
  if (item.projectPaper.savedToKnowledge) return 'ABSTRACT_ONLY'
  return 'NOT_INDEXED'
}
function paperEvidenceText(item) { return knowledgeStatusMap[resolvedKnowledgeStatus(item)] || '未入库' }
function paperEvidenceClass(item) { return resolvedKnowledgeStatus(item).toLowerCase() }
function knowledgeInProgress(item) { return ['FETCHING_FULL_TEXT','PARSING_FULL_TEXT','INDEXING'].includes(item.projectPaper.knowledgeStatus) || item.busy }
function progressPercent(item) { return {FETCHING_FULL_TEXT:25,PARSING_FULL_TEXT:55,INDEXING:80,FULL_TEXT_INDEXED:100}[item.projectPaper.knowledgeStatus] || 0 }
function sourceEvidenceLabel(source) {
  if (source.chunkType === 'TABLE' || /table/i.test(source.section || '')) return '实验表格证据'
  if (source.evidenceLevel === 'FULL_TEXT') return '全文证据'
  return '摘要证据'
}
function sourceEvidenceClass(source) {
  if (source.chunkType === 'TABLE' || /table/i.test(source.section || '')) return 'evidence-table'
  if (source.evidenceLevel === 'FULL_TEXT') return 'evidence-fulltext'
  return 'evidence-abstract'
}
function sourceMetaText(source) {
  const parts = []
  if (source.section) parts.push(source.section)
  if (source.page) parts.push(`第 ${source.page} 页`)
  if (source.tableNumber) parts.push(`表 ${source.tableNumber}`)
  return parts.join(' · ')
}

onMounted(() => { window.addEventListener('hashchange', onRouteChanged); onRouteChanged() })
onBeforeUnmount(() => { window.removeEventListener('hashchange', onRouteChanged); stopTaskPolling() })
</script>

<template>
  <div class="app-shell">
    <aside class="sidebar">
      <div class="brand" @click="go('/projects')">
        <div class="brand-mark">R</div>
        <div><strong>ResearchLens</strong><span>本地科研检索助手</span></div>
      </div>
      <nav>
        <button :class="{active: route.name === 'projects' || route.name === 'new'}" @click="go('/projects')">研究项目</button>
        <button :class="{active: route.name === 'knowledge'}" @click="go('/knowledge')">个人知识库</button>
        <button :class="{active: route.name === 'chat'}" @click="go('/chat')">知识问答</button>
      </nav>
      <div class="sidebar-note">本项目仅在本地运行。外部论文搜索基于 OpenAlex 与 arXiv，重要结论请阅读全文核对。</div>
    </aside>

    <main class="main-panel">
      <header class="topbar">
        <div>
          <h1 v-if="route.name === 'projects'">研究项目</h1>
          <h1 v-else-if="route.name === 'new'">新建研究项目</h1>
          <h1 v-else-if="currentProject">{{ currentProject.name }}</h1>
          <h1 v-else-if="route.name === 'knowledge'">个人知识库</h1>
          <h1 v-else>知识问答</h1>
          <p v-if="currentProject">状态：{{ currentProject.status }}</p>
        </div>
        <button v-if="route.params.id" class="ghost" @click="go('/projects')">返回项目列表</button>
      </header>

      <div v-if="error" class="alert error">{{ error }} <button @click="error=''">×</button></div>
      <div v-if="notice" class="alert success">{{ notice }} <button @click="notice=''">×</button></div>
      <div v-if="loading" class="progress-line"></div>

      <section v-if="route.name === 'projects'" class="page">
        <div class="section-head"><div><h2>你的研究空间</h2><p>保存研究想法、实验记录、检索结果和分析报告。</p></div><button class="primary" @click="go('/projects/new')">新建项目</button></div>
        <div v-if="!projects.length" class="empty">还没有研究项目。创建一个项目开始分析。</div>
        <div class="project-grid">
          <article v-for="item in projects" :key="item.id" class="card project-card">
            <div class="card-head"><span class="status">{{ item.status }}</span><button class="icon-danger" @click="deleteProject(item.id)">删除</button></div>
            <h3>{{ item.name }}</h3><p>更新于 {{ formatDate(item.updatedAt) }}</p>
            <div class="actions"><button @click="go(`/projects/${item.id}/profile`)">研究卡片</button><button @click="go(`/projects/${item.id}/search`)">论文检索</button><button @click="go(`/projects/${item.id}/report`)">分析报告</button></div>
          </article>
        </div>
      </section>

      <section v-else-if="route.name === 'new'" class="page narrow">
        <div class="card form-card">
          <h2>创建研究项目</h2>
          <label>项目名称<input v-model="newProject.name" placeholder="例如：DPC Blind PTF Calibration"></label>
          <label>研究想法或实验现象<textarea v-model="newProject.text" rows="10" placeholder="描述你想研究的问题、当前实验条件、已有尝试和拟采用的方法。"></textarea></label>
          <label class="file-label">可选：上传实验记录<input type="file" accept=".txt,.md,.markdown,.note,.pdf,.doc,.docx" @change="e => newProject.file=e.target.files?.[0] || null"><span>{{ newProject.file?.name || '选择文件' }}</span></label>
          <div class="time-range-card">
            <label>论文检索时间范围</label>
            <div class="preset-options">
              <label class="radio-label"><input type="radio" v-model="timeRange.preset" value="RECENT_3_YEARS" @change="onPresetChange"><span>近 3 年</span></label>
              <label class="radio-label"><input type="radio" v-model="timeRange.preset" value="RECENT_5_YEARS" @change="onPresetChange"><span>近 5 年（默认）</span></label>
              <label class="radio-label"><input type="radio" v-model="timeRange.preset" value="SINCE_2020" @change="onPresetChange"><span>2020 年至今</span></label>
              <label class="radio-label"><input type="radio" v-model="timeRange.preset" value="CUSTOM"><span>自定义年份</span></label>
              <label class="radio-label"><input type="radio" v-model="timeRange.preset" value="ALL_TIME" @change="onPresetChange"><span>不限时间</span></label>
            </div>
            <div v-if="timeRange.preset === 'CUSTOM'" class="custom-years">
              <label>开始年份<input type="number" v-model.number="timeRange.startYear" :min="1900" :max="currentYear()" placeholder="2023"></label>
              <label>结束年份<input type="number" v-model.number="timeRange.endYear" :min="1900" :max="currentYear()" placeholder="2026"></label>
            </div>
            <p v-if="timeRange.preset !== 'CUSTOM' && timeRange.preset !== 'ALL_TIME'" class="hint">检索范围：{{ resolveTimeRangeYears(timeRange.preset).start }}—{{ resolveTimeRangeYears(timeRange.preset).end }}</p>
          </div>
          <div class="actions"><button class="primary" @click="createProject">创建并生成研究卡片</button><button @click="go('/projects')">取消</button></div>
        </div>
      </section>

      <section v-else-if="route.name === 'profile' || route.name === 'project'" class="page">
        <div class="steps workflow-steps"><span class="active">1 输入与卡片</span><span :class="{active: profile.confirmed}">2 关键词确认</span><span>3 论文检索</span><span>4 全文入库</span><span>5 知识问答</span></div>
        <div class="card" style="margin-bottom:12px"><div class="section-head"><div><strong>检索时间范围：</strong><span>{{ timeRangeLabel }}</span></div></div></div>
        <div class="two-column">
          <div class="card">
            <div class="section-head"><div><h2>原始输入</h2><p>系统会综合这些内容理解你的研究问题。</p></div><div class="actions"><button @click="addInputText">补充文字</button><label class="button-label">上传文件<input type="file" @change="addInputFile"></label></div></div>
            <div v-if="!inputs.length" class="empty small">暂无输入。</div>
            <article v-for="item in inputs" :key="item.id" class="input-item"><strong>{{ item.inputType === 'FILE' ? item.fileName : '文字输入' }}</strong><p>{{ (item.parsedText || '').slice(0, 320) }}{{ (item.parsedText || '').length > 320 ? '…' : '' }}</p></article>
            <button class="primary wide" @click="generateProfile">{{ profile.domain ? '重新生成研究卡片' : '生成研究卡片' }}</button>
          </div>

          <div class="card profile-card">
            <div class="section-head"><div><h2>结构化研究卡片</h2><p>检索前请检查专业术语是否准确。</p></div><span v-if="profile.confirmed" class="confirmed">已确认</span></div>
            <label>研究领域<input v-model="profile.domain"></label>
            <label>研究对象<textarea v-model="profile.researchObject" rows="2"></textarea></label>
            <label>核心问题<textarea v-model="profile.researchProblem" rows="4"></textarea></label>
            <label>拟采用方法<textarea v-model="profile.proposedMethod" rows="4"></textarea></label>
            <label>优化变量 / 关键变量（每行一个）<textarea :value="arrayText(profile.variables)" @input="setArray('variables',$event)" rows="3"></textarea></label>
            <label>中文关键词（每行一个）<textarea :value="arrayText(profile.keywordsZh)" @input="setArray('keywordsZh',$event)" rows="3"></textarea></label>
            <div class="keyword-editor">
              <div class="keyword-editor-head"><div><strong>分级英文检索关键词</strong><p>核心词权重最高，方法词和扩展词用于补充召回；可直接移动分类。</p></div></div>
              <div v-for="cfg in [
                {field:'coreKeywords',label:'核心关键词',hint:'研究对象、核心问题、必要术语'},
                {field:'methodKeywords',label:'方法关键词',hint:'算法、实验方法和技术路线'},
                {field:'expandedKeywords',label:'扩展关键词',hint:'近义表达、邻近领域和迁移方法'},
                {field:'excludedKeywords',label:'排除关键词',hint:'标题命中将过滤，摘要命中会降权'}
              ]" :key="cfg.field" class="keyword-group-card">
                <div class="keyword-group-title"><strong>{{ cfg.label }}</strong><span>{{ cfg.hint }}</span></div>
                <div class="keyword-chips">
                  <span v-for="(word,index) in profile[cfg.field] || []" :key="`${cfg.field}-${word}-${index}`" class="keyword-chip">
                    {{ word }}
                    <select v-if="cfg.field !== 'excludedKeywords'" title="移动分类" @change="moveKeyword(cfg.field,index,$event.target.value);$event.target.value=cfg.field">
                      <option :value="cfg.field">移动</option><option value="coreKeywords">核心</option><option value="methodKeywords">方法</option><option value="expandedKeywords">扩展</option>
                    </select>
                    <button type="button" @click="removeKeyword(cfg.field,index)">×</button>
                  </span>
                  <span v-if="!(profile[cfg.field] || []).length" class="chip-empty">暂无</span>
                </div>
                <div class="keyword-add"><input v-model="keywordDraft[cfg.field]" :placeholder="`添加${cfg.label}`" @keydown.enter.prevent="addKeyword(cfg.field)"><button type="button" @click="addKeyword(cfg.field)">添加</button></div>
              </div>
              <div class="keyword-group-card">
                <div class="keyword-group-title"><strong>关键词组合</strong><span>每行用 + 连接两个或以上共同概念</span></div>
                <div v-for="(group,index) in profile.keywordGroups || []" :key="index" class="keyword-combo-row"><input :value="group.join(' + ')" @input="updateKeywordGroup(index,$event.target.value)" placeholder="例如：DPC + blind estimation"><button type="button" class="icon-danger" @click="removeKeywordGroup(index)">删除</button></div>
                <button type="button" @click="addKeywordGroup">新增组合</button>
              </div>
            </div>
            <label>邻近研究领域（每行一个）<textarea :value="arrayText(profile.relatedFields)" @input="setArray('relatedFields',$event)" rows="3"></textarea></label>
            <label>需要回答的问题（每行一个）<textarea :value="arrayText(profile.researchQuestions)" @input="setArray('researchQuestions',$event)" rows="4"></textarea></label>
            <div class="actions"><button @click="saveProfile(false)">保存草稿</button><button class="primary" @click="saveProfile(true)">确认并进入检索</button><button v-if="profile.confirmed" @click="go(`/projects/${projectId}/search`)">查看论文检索</button></div>
          </div>
        </div>
      </section>

      <section v-else-if="route.name === 'search'" class="page">
        <div class="steps workflow-steps"><span>1 输入与卡片</span><span>2 关键词确认</span><span class="active">3 论文检索</span><span>4 全文入库</span><span>5 知识问答</span></div>
        <div class="card task-card">
          <div class="section-head"><div><h2>外部论文检索</h2><p>检索时间：<strong>{{ timeRangeLabel }}</strong> &nbsp; <button class="ghost" style="font-size:0.85em;padding:2px 8px" @click="showTimeEditor=!showTimeEditor">修改</button></p></div><button class="primary" @click="startSearch">开始 / 重新检索</button></div>
          <div v-if="showTimeEditor" class="time-range-card" style="margin-top:8px;padding:12px;background:#f8f9fa;border-radius:8px">
            <div class="preset-options">
              <label class="radio-label"><input type="radio" v-model="timeRange.preset" value="RECENT_3_YEARS" @change="onPresetChange"><span>近 3 年</span></label>
              <label class="radio-label"><input type="radio" v-model="timeRange.preset" value="RECENT_5_YEARS" @change="onPresetChange"><span>近 5 年</span></label>
              <label class="radio-label"><input type="radio" v-model="timeRange.preset" value="SINCE_2020" @change="onPresetChange"><span>2020 年至今</span></label>
              <label class="radio-label"><input type="radio" v-model="timeRange.preset" value="CUSTOM"><span>自定义年份</span></label>
              <label class="radio-label"><input type="radio" v-model="timeRange.preset" value="ALL_TIME" @change="onPresetChange"><span>不限时间</span></label>
            </div>
            <div v-if="timeRange.preset === 'CUSTOM'" class="custom-years">
              <label>开始年份<input type="number" v-model.number="timeRange.startYear" :min="1900" :max="currentYear()" placeholder="开始"></label>
              <label>结束年份<input type="number" v-model.number="timeRange.endYear" :min="1900" :max="currentYear()" placeholder="结束"></label>
            </div>
            <button class="primary" style="margin-top:8px" @click="saveTimeRange(); showTimeEditor=false">保存时间范围</button>
          </div>
          <div v-if="task" class="task-progress"><div><strong>{{ task.stage }}</strong><span>{{ task.message }}</span></div><div class="bar"><i :style="{width:`${task.progress}%`}"></i></div><b>{{ task.progress }}%</b></div>
        </div>
        <div v-if="queries.length" class="card"><h2>检索式</h2><div class="query-list"><span v-for="q in queries" :key="q.id"><b>{{ q.source }} · {{ q.queryType }}</b>{{ q.queryText }} <em>{{ q.resultCount }}</em></span></div></div>
        <div class="section-head"><div><h2>相关论文</h2><p>关键词匹配占 60%，语义匹配占 30%，论文质量占 10%。加入知识库时会优先自动获取开放全文。</p></div><div class="actions"><button v-if="papers.length" @click="batchSavePapers">批量获取全文并入库</button><button v-if="papers.length" class="primary" @click="startAnalysis">分析已选论文</button></div></div>
        <div v-if="papers.length" class="card paper-toolbar">
          <label>入库/全文状态<select v-model="paperFilter.availability" @change="savePaperFilter"><option value="ALL">全部论文</option><option value="AVAILABLE">可获取全文</option><option value="FULL_TEXT_INDEXED">全文已入库</option><option value="NOT_INDEXED">未入库或失败</option></select></label>
          <label>排序<select v-model="paperFilter.sort" @change="savePaperFilter"><option value="FINAL">综合相关度</option><option value="KEYWORD">关键词得分</option><option value="SEMANTIC">语义得分</option><option value="YEAR">发表年份</option><option value="CITATION">引用量</option></select></label>
          <label class="inline-check"><input type="checkbox" v-model="paperFilter.recentOnly" @change="savePaperFilter">仅看近三年</label>
          <span>显示 {{ visiblePapers.length }} / {{ papers.length }} 篇</span>
        </div>
        <div v-if="!papers.length" class="empty">尚无论文结果。确认研究卡片后开始检索。</div>
        <div v-else-if="!visiblePapers.length" class="empty">当前筛选条件下没有论文，请调整筛选条件。</div>
        <article v-for="item in visiblePapers" :key="item.projectPaper.id" :class="['card','paper-card',{selected:item.projectPaper.selected}]">
          <div class="paper-select"><input type="checkbox" :checked="item.projectPaper.selected" @change="togglePaper(item,$event)"><span>#{{ item.projectPaper.rankNumber }}</span></div>
          <div class="paper-main">
            <div class="paper-title"><h3>{{ item.paper.title }}</h3><span :class="['evidence-badge', paperEvidenceClass(item)]">{{ paperEvidenceText(item) }}</span></div>
            <p class="meta">{{ (item.authors || []).slice(0,4).join(', ') }} · {{ item.paper.publicationYear || '年份未知' }} · {{ item.paper.venue || item.paper.source }} · 引用 {{ item.paper.citationCount || 0 }}</p>
            <div class="match-tags"><span v-for="word in item.matchedCoreKeywords" :key="word" class="match-tag core">{{ word }}</span><span v-for="word in item.matchedMethodKeywords" :key="word" class="match-tag method">{{ word }}</span><span v-if="item.fullTextAvailable" class="match-tag fulltext">{{ item.directFullTextAvailable ? '开放全文可获取' : '可尝试从落地页获取' }}</span></div>
            <p v-if="resolvedKnowledgeStatus(item) === 'ABSTRACT_ONLY'" class="abstract-warning">⚠️ 当前仅摘要入库，不能作为具体实验数值的依据。</p>
            <p v-if="item.projectPaper.failureReason" class="paper-error">{{ item.projectPaper.failureReason }}</p>
            <div v-if="knowledgeInProgress(item)" class="knowledge-progress"><div class="bar"><i :style="{width:`${progressPercent(item)}%`}"></i></div><span>{{ paperEvidenceText(item) }}</span></div>
            <p class="abstract">{{ item.paper.abstractText || '该记录没有摘要。' }}</p>
            <div class="score-row"><span>综合 {{ formatScore(item.projectPaper.finalScore) }}</span><span>关键词 {{ formatScore(item.projectPaper.keywordScore) }}</span><span>语义 {{ formatScore(item.projectPaper.embeddingScore) }}</span><span>质量 {{ formatScore(item.projectPaper.qualityScore) }}</span></div>
            <details class="match-details"><summary>查看匹配详情</summary><div class="score-detail-grid"><span>核心覆盖 {{ formatScore(item.projectPaper.coreCoverageScore) }}</span><span>标题匹配 {{ formatScore(item.projectPaper.titleScore) }}</span><span>组合匹配 {{ formatScore(item.projectPaper.phraseGroupScore) }}</span><span>摘要匹配 {{ formatScore(item.projectPaper.abstractMatchScore) }}</span></div><p>{{ item.projectPaper.recommendationReason || '基于关键词、语义和论文质量综合推荐。' }}</p><p v-if="item.matchedKeywordGroups?.length">命中组合：{{ item.matchedKeywordGroups.map(g=>g.join(' + ')).join('；') }}</p><p v-if="item.projectPaper.chunkCount">已解析 {{ item.projectPaper.parsedPageCount || '-' }} 页、{{ item.projectPaper.sectionCount || '-' }} 个章节、{{ item.projectPaper.chunkCount }} 个分片。</p></details>
            <div class="actions"><a v-if="item.paper.landingUrl" :href="item.paper.landingUrl" target="_blank">查看原文</a><button class="primary" :disabled="knowledgeInProgress(item) || resolvedKnowledgeStatus(item) === 'FULL_TEXT_INDEXED'" @click="savePaper(item)">{{ resolvedKnowledgeStatus(item) === 'FULL_TEXT_INDEXED' ? '全文已入库' : '获取全文并入库' }}</button><button v-if="['FAILED','MANUAL_UPLOAD_REQUIRED'].includes(resolvedKnowledgeStatus(item))" @click="retryPaper(item)">重新获取</button><button v-if="['FAILED','MANUAL_UPLOAD_REQUIRED','NOT_INDEXED'].includes(resolvedKnowledgeStatus(item))" @click="saveAbstractOnly(item)">仅摘要入库</button><label class="button-label">手动上传全文<input type="file" accept=".pdf,.doc,.docx,.txt,.md" @change="uploadPaperFullText(item,$event)"></label><button @click="startPaperChat(item)">开始问答</button></div>
          </div>
        </article>
      </section>

      <section v-else-if="route.name === 'report'" class="page report-page">
        <div class="steps workflow-steps"><span>1 输入与卡片</span><span>2 关键词确认</span><span>3 论文检索</span><span class="active">4 分析报告</span><span>5 知识问答</span></div>
        <div class="section-head"><div><h2>研究分析报告</h2><p v-if="report">版本 {{ report.version }} · {{ formatDate(report.createdAt) }}</p></div><div class="actions"><button @click="exportReport">导出 Markdown</button><button class="primary" @click="go(`/projects/${projectId}/search`)">返回论文列表</button></div></div>
        <div v-if="!report" class="empty">尚未生成报告。请先在论文检索页面选择论文并启动分析。</div>
        <article v-else class="card markdown-body" v-html="renderedReport"></article>
      </section>

      <section v-else-if="route.name === 'knowledge'" class="page">
        <div class="section-head"><div><h2>个人知识库</h2><p>上传实验记录、笔记或论文全文，建立可追溯的本地 RAG 知识库。</p></div><div class="actions"><label class="button-label primary">上传文件<input type="file" @change="uploadKnowledge"></label><button @click="reindexKnowledge">重建索引</button></div></div>
        <div v-if="!knowledgeFiles.length" class="empty">知识库中还没有文件。</div>
        <div class="file-list"><article v-for="file in knowledgeFiles" :key="file.path" class="card file-item"><div><strong>{{ file.name }}</strong><span>{{ file.path }}</span></div><button class="icon-danger" @click="deleteKnowledge(file.path)">删除</button></article></div>
      </section>

      <section v-else-if="route.name === 'chat'" class="page chat-page">
        <div class="chat-intro"><h2>基于个人资料提问</h2><p>可限定当前项目或指定论文。多论文对比会为每篇论文分别召回内容，避免单篇论文占满结果。</p></div>
        <div class="card chat-scope-card">
          <label>问答范围<select v-model="chatScope.scope"><option value="ALL_KNOWLEDGE">全部知识库</option><option value="CURRENT_PROJECT">当前研究项目</option><option value="SPECIFIC_PAPERS">指定论文</option><option value="SELECTED_PAPERS">项目中当前勾选论文</option></select></label>
          <label v-if="chatScope.scope !== 'ALL_KNOWLEDGE'">研究项目<select v-model.number="chatScope.projectId" @change="onChatProjectChange"><option :value="null">请选择项目</option><option v-for="p in chatProjects" :key="p.id" :value="p.id">{{ p.name }}</option></select></label>
          <div v-if="chatScope.scope === 'SPECIFIC_PAPERS' && chatPapers.length" class="chat-paper-picker"><label v-for="item in chatPapers" :key="item.paper.id"><input type="checkbox" :checked="chatScope.paperIds.includes(item.paper.id)" @change="toggleChatPaper(item.paper.id,$event.target.checked)"><span>{{ item.paper.title }}</span><em>{{ paperEvidenceText(item) }}</em></label></div>
        </div>
        <div class="quick-questions"><button @click="askQuick('总结这些论文的共同研究问题')">共同研究问题</button><button @click="askQuick('对比各论文的方法差异和创新点')">方法与创新对比</button><button @click="askQuick('提取并对比实验数据与评价指标')">实验数据对比</button><button @click="askQuick('分析各论文的局限并找出研究空白')">局限与研究空白</button></div>
        <div class="messages">
          <div v-if="!chatMessages.length" class="empty">例如：哪些论文与我的 PTF 联合优化想法最相关？我的实验记录中尝试过哪些正则项？</div>
          <article v-for="(message,index) in chatMessages" :key="index" :class="['message',message.role]">
            <div v-if="message.role === 'assistant'" class="message-content" v-html="marked.parse(message.content)"></div>
            <div v-else class="message-content">{{ message.content }}</div>
            <div v-if="message.diagnostics" class="diagnostics">
              <strong>诊断</strong>
              <span>摘要论文 {{ message.diagnostics.abstractPaperCount }}</span>
              <span>全文论文 {{ message.diagnostics.fullTextPaperCount }}</span>
              <span>全文分片 {{ message.diagnostics.fullTextChunkCount }}</span>
              <span>命中 摘要 {{ message.diagnostics.abstractChunkHits }} / 全文 {{ message.diagnostics.fullTextChunkHits }} / 表格 {{ message.diagnostics.tableChunkHits }}</span>
              <span v-if="message.diagnostics.dataQuery" class="tag-data">实验数据查询</span>
              <span v-if="message.diagnostics.unverifiedNumbers?.length" class="tag-warn">未验证数值 {{ message.diagnostics.unverifiedNumbers.length }}</span>
            </div>
            <div v-if="message.sources?.length" class="sources">
              <strong>检索来源</strong>
              <details v-for="(source,i) in message.sources" :key="i">
                <summary>{{ i+1 }}. {{ source.titleOrFilename }} <span :class="['evidence-tag', sourceEvidenceClass(source)]">{{ sourceEvidenceLabel(source) }}</span><span class="src-meta">{{ sourceMetaText(source) }}</span><span class="src-score">{{ formatScore(source.score) }}</span></summary>
                <p>{{ source.contentSnippet }}</p>
              </details>
            </div>
          </article>
        </div>
        <div class="chat-box"><textarea v-model="chatInput" rows="3" placeholder="输入问题，Ctrl + Enter 发送" @keydown.ctrl.enter.prevent="sendChat"></textarea><button class="primary" @click="sendChat">发送</button></div>
      </section>
    </main>
  </div>
</template>
