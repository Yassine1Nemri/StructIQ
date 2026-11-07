import { afterEach, describe, expect, it } from 'vitest';
import { renderToStaticMarkup } from 'react-dom/server';
import { MemoryRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { Tasks } from './Tasks';
import { Dashboard } from './Dashboard';
import type { Dashboard as Summary, Project, Task } from '../../lib/api';

const clients: QueryClient[] = [];
afterEach(() => clients.splice(0).forEach(c => c.clear()));
function client() { const value = new QueryClient({ defaultOptions: { queries: { staleTime: Infinity, retry: false } } }); clients.push(value); return value; }
const filters = { status: '', priority: '', assigneeId: '', overdue: '', dueBefore: '', sort: 'DUE_DATE', page: 0 };
const project: Project = { id: 'project-a', name: 'Test project', description: '', status: 'ACTIVE', startDate: null, targetDate: null, createdBy: 'manager', role: 'MEMBER', totalTasks: 1, completedTasks: 0, progressPercent: 0 };
const task: Task = { id: 'task-a', projectId: project.id, projectName: project.name, title: 'Inspect foundations', description: '', status: 'TODO', priority: 'HIGH', assigneeId: 'member', assigneeName: 'Test member', dueDate: '2026-11-08', createdBy: 'manager', createdAt: '', updatedAt: '', role: 'MEMBER' };
function tasksMarkup(role: 'MANAGER' | 'MEMBER' | 'VIEWER', assigneeId: string) {
  const cache = client();
  cache.setQueryData(['tasks', project.id, filters], { items: [{ ...task, role, assigneeId }], page: 0, size: 12, hasNext: false });
  cache.setQueryData(['members', project.id], []);
  return renderToStaticMarkup(<QueryClientProvider client={cache}><MemoryRouter><Tasks project={{ ...project, role }} userId="member"/></MemoryRouter></QueryClientProvider>);
}
describe('task screen permission states', () => {
  it('shows only own-status control to a member', () => {
    const own = tasksMarkup('MEMBER', 'member');
    expect(own).toContain('Status for Inspect foundations');
    expect(own).not.toContain('+ Create task');
    expect(own).not.toContain('>Delete</button>');
    expect(tasksMarkup('MEMBER', 'someone-else')).not.toContain('Status for Inspect foundations');
  });
  it('keeps viewer tasks read-only and exposes manager actions', () => {
    expect(tasksMarkup('VIEWER', 'member')).not.toContain('Status for Inspect foundations');
    const manager = tasksMarkup('MANAGER', 'member');
    expect(manager).toContain('+ Create task');
    expect(manager).toContain('>Edit</button>');
    expect(manager).toContain('>Delete</button>');
  });
});
it('renders actual dashboard counts, progress and deadline links', () => {
  const cache = client();
  const data: Summary = { asOf: '2026-11-07', totalProjects: 2, activeProjects: 1, assignedTasks: 7, openTasks: 9, overdueTasks: 3,
    projectStatuses: { PLANNED: 1, ACTIVE: 1, COMPLETED: 0, ARCHIVED: 0 }, taskStatuses: { TODO: 6, IN_PROGRESS: 2, BLOCKED: 1, DONE: 3 },
    projectProgress: [{ projectId: project.id, name: 'Actual project', totalTasks: 4, completedTasks: 1, percent: 25 }],
    overdue: [{ taskId: task.id, projectId: project.id, projectName: project.name, title: 'Late inspection', assigneeName: 'Test member', dueDate: '2026-11-06' }], upcoming: [] };
  cache.setQueryData(['dashboard'], data);
  const markup = renderToStaticMarkup(<QueryClientProvider client={cache}><MemoryRouter><Dashboard/></MemoryRouter></QueryClientProvider>);
  expect(markup).toContain('Assigned to me</h2><strong>7</strong>');
  expect(markup).toContain('Actual project: 25% complete');
  expect(markup).toContain('Late inspection');
  expect(markup).toContain('/projects/project-a');
});
