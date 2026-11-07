import { afterEach, describe, expect, it, vi } from 'vitest';
import { ApiError, getSystemStatus, onSessionExpired, request, setAccessToken } from './api';

afterEach(() => { vi.unstubAllGlobals(); setAccessToken(null); onSessionExpired(() => {}); });
describe('system status client', () => {
  it('reads the real endpoint and passes cancellation', async () => {
    const body = { name: 'StructIQ', phase: 'DOCUMENTS', aiStatus: 'NOT_IMPLEMENTED' };
    const fetcher = vi.fn().mockResolvedValue(new Response(JSON.stringify(body)));
    vi.stubGlobal('fetch', fetcher);
    const signal = new AbortController().signal;
    expect(await getSystemStatus(signal)).toEqual(body);
    expect(fetcher).toHaveBeenCalledWith('/api/system/status', expect.objectContaining({ signal, credentials: 'omit' }));
  });
  it('reports an unavailable service', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response('', { status: 503 })));
    await expect(getSystemStatus()).rejects.toThrow('request failed');
  });
  it('rejects malformed API data', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response('{"status":"ok"}')));
    await expect(getSystemStatus()).rejects.toThrow('unexpected response');
  });
});

describe('bearer session boundaries', () => {
  it('sends the current in-memory token and omits cookies', async () => {
    const fetcher = vi.fn().mockImplementation(async () => new Response('{}'));
    vi.stubGlobal('fetch', fetcher);
    setAccessToken('synthetic-token');
    await request('/auth/me');
    const options = fetcher.mock.calls[0][1] as RequestInit;
    expect(new Headers(options.headers).get('Authorization')).toBe('Bearer synthetic-token');
    expect(options.credentials).toBe('omit');
    setAccessToken(null);
    await request('/auth/me');
    expect(new Headers(fetcher.mock.calls[1][1].headers).has('Authorization')).toBe(false);
  });
  it('clears an expired session and preserves sanitized error information', async () => {
    const expire = vi.fn(); onSessionExpired(expire); setAccessToken('expired-token');
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({ code: 'UNAUTHENTICATED', message: 'Authentication required', correlationId: 'test-id' }), { status: 401 })));
    await expect(request('/projects')).rejects.toMatchObject({ status: 401, code: 'UNAUTHENTICATED', correlationId: 'test-id' });
    expect(expire).toHaveBeenCalledOnce();
  });
  it('does not discard the session on a project permission error', async () => {
    const expire = vi.fn(); onSessionExpired(expire); setAccessToken('valid-token');
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response('{}', { status: 403 })));
    await expect(request('/projects/private')).rejects.toBeInstanceOf(ApiError);
    expect(expire).not.toHaveBeenCalled();
  });
});

it('uses the project path and an isolated status patch for member updates', async () => {
  const { updateTask } = await import('./api');
  const fetcher = vi.fn().mockResolvedValue(new Response('{}'));
  vi.stubGlobal('fetch', fetcher);
  await updateTask('project-a', 'task-a', { status: 'DONE' });
  expect(fetcher.mock.calls[0][0]).toBe('/api/projects/project-a/tasks/task-a');
  expect(JSON.parse(fetcher.mock.calls[0][1].body)).toEqual({ status: 'DONE' });
});
it('preserves explicit nulls when a manager unassigns a task and clears a deadline', async () => {
  const { updateTask } = await import('./api');
  const fetcher = vi.fn().mockResolvedValue(new Response('{}'));
  vi.stubGlobal('fetch', fetcher);
  await updateTask('project-a', 'task-a', { assigneeId: null, dueDate: null });
  expect(JSON.parse(fetcher.mock.calls[0][1].body)).toEqual({ assigneeId: null, dueDate: null });
});
it('requests filtered own tasks without any browser-selected project scope', async () => {
  const { listTasks } = await import('./api');
  const fetcher = vi.fn().mockResolvedValue(new Response('{}'));
  vi.stubGlobal('fetch', fetcher);
  await listTasks({ status: 'BLOCKED', priority: '', assigneeId: '', overdue: 'true', dueBefore: '', sort: 'PRIORITY', page: 1 });
  const path = fetcher.mock.calls[0][0] as string;
  expect(path.startsWith('/api/tasks/mine?')).toBe(true);
  const params = new URL(path, 'http://localhost').searchParams;
  expect(params.get('status')).toBe('BLOCKED');
  expect(params.get('overdue')).toBe('true');
  expect(params.get('page')).toBe('1');
});

it('uploads multipart without overriding its boundary with JSON content type', async () => {
  const { uploadDocument } = await import('./api');
  const fetcher = vi.fn().mockResolvedValue(new Response('{}')); vi.stubGlobal('fetch', fetcher);
  setAccessToken('synthetic-document-token');
  await uploadDocument('project-a', new File(['synthetic text'], 'meeting.txt', { type: 'text/plain' }));
  const options = fetcher.mock.calls[0][1] as RequestInit;
  expect(fetcher.mock.calls[0][0]).toBe('/api/projects/project-a/documents');
  expect(options.body).toBeInstanceOf(FormData);
  expect(new Headers(options.headers).has('Content-Type')).toBe(false);
  expect(new Headers(options.headers).get('Authorization')).toBe('Bearer synthetic-document-token');
});
it('downloads private bytes through the authenticated scoped endpoint', async () => {
  const { downloadDocument } = await import('./api');
  const fetcher = vi.fn().mockResolvedValue(new Response('private synthetic bytes')); vi.stubGlobal('fetch', fetcher);
  setAccessToken('synthetic-document-token');
  expect(await (await downloadDocument('project-a', 'document-a')).text()).toBe('private synthetic bytes');
  expect(fetcher.mock.calls[0][0]).toBe('/api/projects/project-a/documents/document-a/download');
  expect(new Headers(fetcher.mock.calls[0][1].headers).get('Authorization')).toBe('Bearer synthetic-document-token');
});
