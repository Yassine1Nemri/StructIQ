import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import * as api from '../../lib/api';
import { ErrorMessage } from '../../components/Feedback';
import { Icon } from '../../components/Icon';

export const taskStatuses: api.TaskStatus[] = ['TODO', 'IN_PROGRESS', 'BLOCKED', 'DONE'];
const priorities: api.TaskPriority[] = ['HIGH', 'MEDIUM', 'LOW'];
export const label = (text: string) => text.toLowerCase().replaceAll('_', ' ').replace(/^./, c => c.toUpperCase());
export function canManageTask(role: string) { return role === 'ADMIN' || role === 'MANAGER'; }
export function canChangeStatus(task: api.Task, userId: string) {
  return canManageTask(task.role) || (task.role === 'MEMBER' && task.assigneeId === userId);
}
const emptyFilters: api.TaskFilters = { status: '', priority: '', assigneeId: '', overdue: '', dueBefore: '', sort: 'DUE_DATE', page: 0 };

function TaskForm({ projectId, task, onClose, onSaved }: { projectId: string; task?: api.Task; onClose: () => void; onSaved: () => void }) {
  const [input, setInput] = useState<api.TaskInput>({ title: task?.title ?? '', description: task?.description ?? '', status: task?.status ?? 'TODO', priority: task?.priority ?? 'MEDIUM', assigneeId: task?.assigneeId ?? null, dueDate: task?.dueDate ?? null });
  const members = useQuery({ queryKey: ['members', projectId], queryFn: ({ signal }) => api.getMembers(projectId, signal) });
  const save = useMutation({ mutationFn: () => task ? api.updateTask(projectId, task.id, input) : api.createTask(projectId, input), onSuccess: onSaved });
  const field = <K extends keyof api.TaskInput>(key: K, value: api.TaskInput[K]) => setInput(old => ({ ...old, [key]: value }));
  return <section className="panel task-editor" aria-label={task ? 'Edit task' : 'Create task'}><h2>{task ? 'Edit task' : 'Create task'}</h2>
    <form onSubmit={e => { e.preventDefault(); save.mutate(); }}>
      <label>Task title<input autoFocus required maxLength={200} value={input.title} onChange={e => field('title', e.target.value)}/></label>
      <label>Task description<textarea rows={3} maxLength={4000} value={input.description} onChange={e => field('description', e.target.value)}/></label>
      <div className="form-row"><label>Task status<select value={input.status} onChange={e => field('status', e.target.value as api.TaskStatus)}>{taskStatuses.map(s => <option key={s} value={s}>{label(s)}</option>)}</select></label>
        <label>Priority<select value={input.priority} onChange={e => field('priority', e.target.value as api.TaskPriority)}>{priorities.map(p => <option key={p} value={p}>{label(p)}</option>)}</select></label>
        <label>Due date (UTC)<input type="date" value={input.dueDate ?? ''} onChange={e => field('dueDate', e.target.value || null)}/></label></div>
      <label>Assignee<select value={input.assigneeId ?? ''} disabled={!members.data} onChange={e => field('assigneeId', e.target.value || null)}><option value="">Unassigned</option>{members.data?.map(m => <option key={m.userId} value={m.userId}>{m.displayName} · {label(m.role)}</option>)}</select></label>
      {members.isPending && <p role="status">Loading project members…</p>}<ErrorMessage error={members.error}/><ErrorMessage error={save.error}/>
      <div className="actions"><button className="primary" disabled={save.isPending || !members.data}>{save.isPending ? 'Saving…' : 'Save task'}</button><button type="button" onClick={onClose} disabled={save.isPending}>Cancel</button></div>
    </form>
  </section>;
}
export function Tasks({ project, userId }: { project?: api.Project; userId: string }) {
  const cache = useQueryClient();
  const [filters, setFilters] = useState(emptyFilters);
  const [view, setView] = useState<'board' | 'list'>(project ? 'board' : 'list');
  const [editor, setEditor] = useState<api.Task | 'new' | null>(null);
  const data = useQuery({ queryKey: ['tasks', project?.id ?? 'mine', filters], queryFn: ({ signal }) => api.listTasks(filters, project?.id, signal) });
  const members = useQuery({ queryKey: ['members', project?.id], queryFn: ({ signal }) => api.getMembers(project!.id, signal), enabled: !!project });
  function refresh() { void cache.invalidateQueries(); }
  const status = useMutation({ mutationFn: ({ task, value }: { task: api.Task; value: api.TaskStatus }) => api.updateTask(task.projectId, task.id, { status: value }), onSuccess: refresh });
  const remove = useMutation({ mutationFn: (task: api.Task) => api.deleteTask(task.projectId, task.id), onSuccess: refresh });
  function filter<K extends keyof api.TaskFilters>(key: K, value: api.TaskFilters[K]) { setFilters(old => ({ ...old, [key]: value, page: 0 })); }
  const manage = !!project && canManageTask(project.role);
  const groups = view === 'board' ? taskStatuses.map(value => ({ name: value, items: data.data?.items.filter(task => task.status === value) ?? [] })) : [{ name: 'all', items: data.data?.items ?? [] }];
  return <section aria-labelledby="tasks-heading"><div className="section-heading"><h2 id="tasks-heading">{project ? 'Project tasks' : 'My tasks'}</h2><div className="actions"><div className="view-switch" aria-label="Task view"><button aria-pressed={view === 'board'} onClick={() => setView('board')}><Icon name="grid" size={15}/>Board</button><button aria-pressed={view === 'list'} onClick={() => setView('list')}><Icon name="tasks" size={15}/>List</button></div>{manage && !editor && <button className="primary" onClick={() => setEditor('new')}>+ Create task</button>}</div></div>
    {editor && <TaskForm key={editor === 'new' ? 'new' : editor.id} projectId={editor === 'new' ? project!.id : editor.projectId} task={editor === 'new' ? undefined : editor} onClose={() => setEditor(null)} onSaved={() => { setEditor(null); refresh(); }}/>}<div className="task-filters">
      <label>Task status<select value={filters.status} onChange={e => filter('status', e.target.value)}><option value="">All statuses</option>{taskStatuses.map(s => <option key={s} value={s}>{label(s)}</option>)}</select></label>
      <label>Priority<select value={filters.priority} onChange={e => filter('priority', e.target.value)}><option value="">All priorities</option>{priorities.map(p => <option key={p} value={p}>{label(p)}</option>)}</select></label>
      {project && <label>Assigned to<select value={filters.assigneeId} onChange={e => filter('assigneeId', e.target.value)}><option value="">Anyone</option>{members.data?.map(m => <option key={m.userId} value={m.userId}>{m.displayName}</option>)}</select></label>}
      <label>Deadline<select value={filters.overdue} onChange={e => filter('overdue', e.target.value)}><option value="">Any deadline</option><option value="true">Overdue</option><option value="false">Not overdue</option></select></label>
      <label>Due on or before<input type="date" value={filters.dueBefore} onChange={e => filter('dueBefore', e.target.value)}/></label>
      <label>Sort by<select value={filters.sort} onChange={e => filter('sort', e.target.value as api.TaskSort)}><option value="DUE_DATE">Due date</option><option value="PRIORITY">Priority</option><option value="UPDATED">Recently updated</option></select></label></div>
    <ErrorMessage error={status.error || remove.error}/>{data.isPending ? <p role="status">Loading tasks…</p> : data.isError ? <><ErrorMessage error={data.error}/><button onClick={() => void data.refetch()}>Try again</button></> : <>
      {!data.data.items.length ? <div className="panel"><h3>No tasks found</h3><p>{project ? 'Adjust your filters, or ask a manager to create a task.' : 'Tasks assigned to you in your current projects will appear here.'}</p></div> : <div className={view === 'board' ? 'task-board' : 'task-list-wrap'}>{groups.map(group => <div className={view === 'board' ? `board-column status-${group.name.toLowerCase()}` : 'task-list'} key={group.name}>{view === 'board' && <div className="board-column-heading"><span><i className="status-dot"/>{label(group.name)}</span><span className="count-pill">{group.items.length}</span></div>}{view === 'board' && !group.items.length && <p className="board-empty">No tasks on this page</p>}{group.items.map(task => <article className="panel task-card" key={task.id}>
        <div className="task-card-heading"><div><Link to={`/projects/${task.projectId}`}>{task.projectName}</Link><h3>{task.title}</h3></div><span className={`badge priority-${task.priority.toLowerCase()}`}>{label(task.priority)}</span></div>
        {task.description && <p className="description">{task.description}</p>}<p className="hint">{task.assigneeName ?? 'Unassigned'} · Due {task.dueDate ?? 'not set'}</p>
        <div className="task-actions">{canChangeStatus(task, userId) ? <label>Status for {task.title}<select value={task.status} disabled={status.isPending} onChange={e => status.mutate({ task, value: e.target.value as api.TaskStatus })}>{taskStatuses.map(s => <option key={s} value={s}>{label(s)}</option>)}</select></label> : <span className="badge">{label(task.status)}</span>}
          {canManageTask(task.role) && <div className="actions"><button onClick={() => setEditor(task)}>Edit</button><button className="danger" disabled={remove.isPending} onClick={() => { if (window.confirm(`Delete task “${task.title}”?`)) remove.mutate(task); }}>Delete</button></div>}</div>
      </article>)}</div>)}</div>}<div className="pagination"><button disabled={!filters.page} onClick={() => setFilters(old => ({ ...old, page: old.page - 1 }))}>Previous</button><span>Page {filters.page + 1} · up to 12 tasks</span><button disabled={!data.data.hasNext} onClick={() => setFilters(old => ({ ...old, page: old.page + 1 }))}>Next</button></div></>}
    <p className="hint">Members can update the status of their assigned tasks. Viewers have read access. Deadline comparisons use UTC.</p>
  </section>;
}
