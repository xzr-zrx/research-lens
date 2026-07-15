const BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:9900/api'

async function request(path, options = {}) {
  const response = await fetch(`${BASE_URL}${path}`, options)
  const payload = await response.json().catch(() => ({ success: false, message: '服务返回了无法解析的响应' }))
  if (!response.ok || payload.success === false) {
    throw new Error(payload.message || `请求失败：${response.status}`)
  }
  return payload.data
}

function json(method, body) {
  return {
    method,
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body)
  }
}

export const api = {
  listProjects: () => request('/research/projects'),
  createProject: (name, paperTimePreset, paperStartYear, paperEndYear) =>
    request('/research/projects', json('POST', { name, paperTimePreset, paperStartYear, paperEndYear })),
  getProject: (id) => request(`/research/projects/${id}`),
  deleteProject: (id) => request(`/research/projects/${id}`, { method: 'DELETE' }),
  addTextInput: (id, text) => request(`/research/projects/${id}/inputs/text`, json('POST', { text })),
  addFileInput: (id, file) => {
    const form = new FormData(); form.append('file', file)
    return request(`/research/projects/${id}/inputs/file`, { method: 'POST', body: form })
  },
  generateProfile: (id) => request(`/research/projects/${id}/profile/generate`, { method: 'POST' }),
  saveProfile: (id, profile) => request(`/research/projects/${id}/profile`, json('PUT', profile)),
  getProfile: (id) => request(`/research/projects/${id}/profile`),
  startSearch: (id) => request(`/research/projects/${id}/search`, { method: 'POST' }),
  getTask: (taskId) => request(`/research/tasks/${taskId}`),
  listQueries: (id) => request(`/research/projects/${id}/queries`),
  listPapers: (id) => request(`/research/projects/${id}/papers`),
  selectPaper: (id, projectPaperId, selected) => request(`/research/projects/${id}/papers/${projectPaperId}/selection`, json('PUT', { selected })),
  startAnalysis: (id) => request(`/research/projects/${id}/analyze`, { method: 'POST' }),
  getReport: (id) => request(`/research/projects/${id}/report`),
  regenerateReport: (id) => request(`/research/projects/${id}/report/regenerate`, { method: 'POST' }),
  savePaper: (id, projectPaperId) => request(`/research/projects/${id}/papers/${projectPaperId}/knowledge`, { method: 'POST' }),
  savePaperAbstract: (id, projectPaperId) => request(`/research/projects/${id}/papers/${projectPaperId}/knowledge/abstract`, { method: 'POST' }),
  retryPaper: (id, projectPaperId) => request(`/research/projects/${id}/papers/${projectPaperId}/knowledge/retry`, { method: 'POST' }),
  batchSavePapers: (id, projectPaperIds) => request(`/research/projects/${id}/papers/knowledge/batch`, json('POST', { projectPaperIds })),
  uploadFullText: (id, projectPaperId, file) => {
    const form = new FormData(); form.append('file', file)
    return request(`/research/projects/${id}/papers/${projectPaperId}/fulltext`, { method: 'POST', body: form })
  },
  listKnowledge: () => request('/knowledge/files'),
  uploadKnowledge: (file) => {
    const form = new FormData(); form.append('file', file)
    return request('/knowledge/files', { method: 'POST', body: form })
  },
  deleteKnowledge: (path) => request(`/knowledge/files?path=${encodeURIComponent(path)}`, { method: 'DELETE' }),
  reindexKnowledge: () => request('/knowledge/reindex', { method: 'POST' }),
  updateSearchPreference: (id, paperTimePreset, paperStartYear, paperEndYear) =>
    request(`/research/projects/${id}/search-preference`, json('PUT', { paperTimePreset, paperStartYear, paperEndYear })),
  chat: (message, history, scope = 'ALL_KNOWLEDGE', projectId = null, paperIds = []) =>
    request('/chat', json('POST', { message, history, scope, projectId, paperIds }))
}
