export interface SystemStatus { name: string; phase: 'SECURE_RAG'; aiStatus: 'OPTIONAL_GEMINI' }
export interface User { id: string; email: string; displayName: string; globalRole: 'ADMIN' | 'USER' }
export type ProjectStatus = 'PLANNED' | 'ACTIVE' | 'COMPLETED' | 'ARCHIVED';
export type MemberRole = 'MANAGER' | 'MEMBER' | 'VIEWER';
export interface Project { id: string; name: string; description: string; status: ProjectStatus; startDate: string | null; targetDate: string | null; createdBy: string; role: MemberRole | 'ADMIN'; totalTasks: number; completedTasks: number; progressPercent: number }
export interface ProjectInput { name: string; description: string; status: ProjectStatus; startDate: string | null; targetDate: string | null }
export interface Member { userId: string; email: string; displayName: string; role: MemberRole }
export interface Page { items: Project[]; page: number; size: number; hasNext: boolean }
export interface Session { accessToken: string; tokenType: string; expiresIn: number; user: User }
export class ApiError extends Error {
  constructor(public status: number, public code: string, message: string, public correlationId?: string) { super(message); }
}
let accessToken: string | null = null;
let expired: () => void = () => {};
export function setAccessToken(value: string | null) { accessToken = value; }
export function onSessionExpired(handler: () => void) { expired = handler; }

export async function fetchResponse(path: string, options: RequestInit = {}): Promise<Response> {
  const token = accessToken;
  const headers = new Headers(options.headers);
  if (token) headers.set('Authorization', `Bearer ${token}`);
  if (options.body && !(options.body instanceof FormData)) headers.set('Content-Type', 'application/json');
  let response: Response;
  try { response = await fetch(`/api${path}`, { ...options, headers, credentials: 'omit' }); }
  catch (error) {
    if (error instanceof DOMException && error.name === 'AbortError') throw error;
    throw new Error('Cannot connect to StructIQ. Check your connection and try again.');
  }
  if (!response.ok) {
    const payload = await response.json().catch(() => ({})) as Record<string, unknown>;
    if (response.status === 401 && token && token === accessToken) expired();
    throw new ApiError(response.status, typeof payload.code === 'string' ? payload.code : 'REQUEST_FAILED',
      typeof payload.message === 'string' ? payload.message : 'The request failed. Please try again.',
      typeof payload.correlationId === 'string' ? payload.correlationId : undefined);
  }
  return response;
}
export async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  const response = await fetchResponse(path, options);
  if (response.status === 204) return undefined as T;
  return await response.json() as T;
}
export async function getSystemStatus(signal?: AbortSignal): Promise<SystemStatus> {
  const data = await request<SystemStatus>('/system/status', { signal });
  if (data.name !== 'StructIQ' || data.phase !== 'SECURE_RAG' || data.aiStatus !== 'OPTIONAL_GEMINI')
    throw new Error('The API returned an unexpected response.');
  return data;
}
export const login = (email: string, password: string) => request<Session>('/auth/login', { method: 'POST', body: JSON.stringify({ email, password }) });
export const listProjects = (q: string, status: string, page: number, signal?: AbortSignal) => request<Page>(`/projects?${new URLSearchParams({ q, ...(status ? { status } : {}), page: String(page), size: '12' })}`, { signal });
export const getProject = (id: string, signal?: AbortSignal) => request<Project>(`/projects/${id}`, { signal });
export const getMembers = (id: string, signal?: AbortSignal) => request<Member[]>(`/projects/${id}/members`, { signal });
export const saveProject = (input: ProjectInput, id?: string) => request<Project>(`/projects${id ? `/${id}` : ''}`, { method: id ? 'PATCH' : 'POST', body: JSON.stringify(input) });
export const deleteProject = (id: string) => request<void>(`/projects/${id}`, { method: 'DELETE' });
export const addMember = (id: string, email: string, role: MemberRole) => request<Member>(`/projects/${id}/members`, { method: 'POST', body: JSON.stringify({ email, role }) });
export const setMemberRole = (id: string, userId: string, role: MemberRole) => request<void>(`/projects/${id}/members/${userId}`, { method: 'PATCH', body: JSON.stringify({ role }) });
export const removeMember = (id: string, userId: string) => request<void>(`/projects/${id}/members/${userId}`, { method: 'DELETE' });

export type TaskStatus = 'TODO' | 'IN_PROGRESS' | 'BLOCKED' | 'DONE';
export type TaskPriority = 'LOW' | 'MEDIUM' | 'HIGH';
export type TaskSort = 'DUE_DATE' | 'PRIORITY' | 'UPDATED';
export interface Task { id: string; projectId: string; projectName: string; title: string; description: string; status: TaskStatus; priority: TaskPriority; assigneeId: string | null; assigneeName: string | null; dueDate: string | null; createdBy: string; createdAt: string; updatedAt: string; role: MemberRole | 'ADMIN' }
export interface TaskInput { title: string; description: string; status: TaskStatus; priority: TaskPriority; assigneeId: string | null; dueDate: string | null }
export interface TaskPage { items: Task[]; page: number; size: number; hasNext: boolean }
export interface TaskFilters { status: string; priority: string; assigneeId: string; overdue: string; dueBefore: string; sort: TaskSort; page: number }
export interface ProjectProgress { projectId: string; name: string; totalTasks: number; completedTasks: number; percent: number }
export interface Deadline { taskId: string; projectId: string; projectName: string; title: string; assigneeName: string | null; dueDate: string }
export interface Dashboard { asOf: string; totalProjects: number; activeProjects: number; assignedTasks: number; openTasks: number; overdueTasks: number; projectStatuses: Record<ProjectStatus, number>; taskStatuses: Record<TaskStatus, number>; projectProgress: ProjectProgress[]; overdue: Deadline[]; upcoming: Deadline[] }
export const getDashboard = (signal?: AbortSignal) => request<Dashboard>('/dashboard/summary', { signal });
export const listTasks = (filters: TaskFilters, projectId?: string, signal?: AbortSignal) => {
  const query = new URLSearchParams({ page: String(filters.page), size: '12', sort: filters.sort });
  for (const key of ['status', 'priority', 'assigneeId', 'overdue', 'dueBefore'] as const) if (filters[key]) query.set(key, filters[key]);
  return request<TaskPage>(`${projectId ? `/projects/${projectId}/tasks` : '/tasks/mine'}?${query}`, { signal });
};
export const createTask = (projectId: string, input: TaskInput) => request<Task>(`/projects/${projectId}/tasks`, { method: 'POST', body: JSON.stringify(input) });
export const updateTask = (projectId: string, taskId: string, input: Partial<TaskInput>) => request<Task>(`/projects/${projectId}/tasks/${taskId}`, { method: 'PATCH', body: JSON.stringify(input) });
export const deleteTask = (projectId: string, taskId: string) => request<void>(`/projects/${projectId}/tasks/${taskId}`, { method: 'DELETE' });


export interface DocumentMetadata { id: string; projectId: string; filename: string; mimeType: string; sizeBytes: number; processingStatus: 'UPLOADED' | 'PROCESSING' | 'READY' | 'FAILED'; processingCode: string; extractedPages: number; uploadedBy: string; createdAt: string }
export interface DocumentPage { items: DocumentMetadata[]; page: number; size: number; hasNext: boolean; maxUploadBytes: number }
export const listDocuments = (projectId: string, page: number, signal?: AbortSignal) => request<DocumentPage>(`/projects/${projectId}/documents?page=${page}&size=12`, { signal });
export const uploadDocument = (projectId: string, file: File) => {
  const body = new FormData(); body.append('file', file);
  return request<DocumentMetadata>(`/projects/${projectId}/documents`, { method: 'POST', body });
};
export const downloadDocument = async (projectId: string, documentId: string) => (await fetchResponse(`/projects/${projectId}/documents/${documentId}/download`)).blob();
export const deleteDocument = (projectId: string, documentId: string) => request<void>(`/projects/${projectId}/documents/${documentId}`, { method: 'DELETE' });
export const retryDocument = (projectId: string, documentId: string) => request<DocumentMetadata>(`/projects/${projectId}/documents/${documentId}/retry`, { method: 'POST' });

export interface AiStatus { configured: boolean }
export interface Citation { chunkId: string; documentId: string; filename: string; pageNumber: number; excerpt: string }
export interface AiAnswer { answer: string; citations: Citation[]; retrievalCount: number }
export const getAiStatus = (projectId: string, signal?: AbortSignal) => request<AiStatus>(`/projects/${projectId}/ai/status`, { signal });
export const askAi = (projectId: string, question: string) => request<AiAnswer>(`/projects/${projectId}/ai/ask`, { method: 'POST', body: JSON.stringify({ question }) });
export const indexDocument = (projectId: string, documentId: string) => request<{ indexedChunks: number }>(`/projects/${projectId}/documents/${documentId}/index`, { method: 'POST' });
