import React, { createContext, useContext, useEffect, useState, type ReactNode } from 'react';
import ReactDOM from 'react-dom/client';
import { BrowserRouter, Link, Navigate, NavLink, Route, Routes, useNavigate, useParams } from 'react-router-dom';
import { QueryClient, QueryClientProvider, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import * as api from './lib/api';
import './styles.css';
import { ErrorMessage } from './components/Feedback';
import { Tasks } from './features/work/Tasks';
import { Dashboard } from './features/work/Dashboard';
import { Documents } from './features/documents/Documents';

const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false, staleTime: 10_000 } } });
const Auth = createContext<{ user: api.User | null; signIn: (session: api.Session) => void; logout: () => void }>({ user: null, signIn: () => {}, logout: () => {} });
function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<api.User | null>(null);
  const [expiresAt, setExpiresAt] = useState(0);
  function logout() { api.setAccessToken(null); setUser(null); setExpiresAt(0); queryClient.clear(); }
  useEffect(() => { api.onSessionExpired(logout); return () => api.onSessionExpired(() => {}); }, []);
  useEffect(() => {
    if (!expiresAt) return;
    const timer = window.setTimeout(logout, Math.max(0, expiresAt - Date.now()));
    return () => window.clearTimeout(timer);
  }, [expiresAt]);
  function signIn(session: api.Session) {
    queryClient.clear(); api.setAccessToken(session.accessToken); setUser(session.user);
    setExpiresAt(Date.now() + session.expiresIn * 1000);
  }
  return <Auth.Provider value={{ user, signIn, logout }}>{children}</Auth.Provider>;
}
function Login() {
  const auth = useContext(Auth);
  const [email, setEmail] = useState(''); const [password, setPassword] = useState('');
  const submit = useMutation({ mutationFn: () => api.login(email, password), onSuccess: session => { setPassword(''); auth.signIn(session); } });
  if (auth.user) return <Navigate to="/" replace/>;
  return <main className="login-page"><Link to="/" className="brand">StructIQ</Link><section className="panel login-panel"><div className="eyebrow">PROJECT CLARITY, FROM THE GROUND UP</div><h1>Welcome back.</h1><p>Sign in to your project workspace.</p><form onSubmit={e => { e.preventDefault(); submit.mutate(); }}>
    <label>Email address<input type="email" autoComplete="username" required maxLength={254} value={email} onChange={e => setEmail(e.target.value)}/></label>
    <label>Password<input type="password" autoComplete="current-password" required maxLength={72} value={password} onChange={e => setPassword(e.target.value)}/></label>
    <ErrorMessage error={submit.error}/><button className="primary" disabled={submit.isPending}>{submit.isPending ? 'Signing in…' : 'Sign in'}</button>
  </form><p className="hint">Demo accounts are created with <code>make seed</code>. Your generated password is in the local credentials file.</p><p className="hint">For your security, refresh signs you out. Sessions expire after 15 minutes.</p></section></main>;
}
const statuses: api.ProjectStatus[] = ['PLANNED', 'ACTIVE', 'COMPLETED', 'ARCHIVED'];
const roles: api.MemberRole[] = ['MANAGER', 'MEMBER', 'VIEWER'];
function readable(value: string) { return value.charAt(0) + value.slice(1).toLowerCase(); }
function ProjectForm({ project, onDone, onCancel }: { project?: api.Project; onDone: (project: api.Project) => void; onCancel: () => void }) {
  const [name, setName] = useState(project?.name ?? ''); const [description, setDescription] = useState(project?.description ?? '');
  const [status, setStatus] = useState<api.ProjectStatus>(project?.status ?? 'PLANNED');
  const [startDate, setStartDate] = useState(project?.startDate ?? ''); const [targetDate, setTargetDate] = useState(project?.targetDate ?? '');
  const mutation = useMutation({ mutationFn: () => api.saveProject({ name, description, status, startDate: startDate || null, targetDate: targetDate || null }, project?.id), onSuccess: onDone });
  return <section className="panel"><h2>{project ? 'Edit project' : 'Create a project'}</h2><form onSubmit={e => { e.preventDefault(); mutation.mutate(); }}>
    <label>Project name<input required maxLength={150} value={name} onChange={e => setName(e.target.value)}/></label>
    <label>Description<textarea maxLength={4000} rows={3} value={description} onChange={e => setDescription(e.target.value)}/></label>
    <div className="form-row"><label>Status<select value={status} onChange={e => setStatus(e.target.value as api.ProjectStatus)}>{statuses.map(s => <option key={s}>{s}</option>)}</select></label>
    <label>Start date<input type="date" value={startDate} onChange={e => setStartDate(e.target.value)}/></label>
    <label>Target date<input type="date" min={startDate} value={targetDate} onChange={e => setTargetDate(e.target.value)}/></label></div>
    {project && <p className="hint">Empty date fields retain previously saved dates.</p>}
    <ErrorMessage error={mutation.error}/><div className="actions"><button className="primary" disabled={mutation.isPending}>{mutation.isPending ? 'Saving…' : 'Save project'}</button><button type="button" onClick={onCancel} disabled={mutation.isPending}>Cancel</button></div>
  </form></section>;
}
function Projects() {
  const { user } = useContext(Auth); const cache = useQueryClient(); const navigate = useNavigate();
  const [q, setQ] = useState(''); const [status, setStatus] = useState(''); const [page, setPage] = useState(0); const [creating, setCreating] = useState(false);
  const data = useQuery({ queryKey: ['projects', q, status, page], queryFn: ({ signal }) => api.listProjects(q, status, page, signal) });
  // Managers are project-scoped. The list includes their current role, including on archived projects.
  const permission = useQuery({ queryKey: ['create-permission'], queryFn: ({ signal }) => api.request<{ canCreate: boolean }>('/projects/permissions', { signal }) });
  const canCreate = permission.data?.canCreate;
  return <><div className="eyebrow">YOUR PROJECT WORKSPACE</div><div className="heading"><div><h1>Projects</h1><p className="intro">The people, plans, and work you have access to.</p></div>{canCreate && !creating && <button className="primary" onClick={() => setCreating(true)}>+ Create project</button>}</div>
    {creating && <ProjectForm onCancel={() => setCreating(false)} onDone={p => { void cache.invalidateQueries(); navigate(`/projects/${p.id}`); }}/>}<div className="toolbar"><label>Search projects<input type="search" maxLength={150} placeholder="Find a project…" value={q} onChange={e => { setQ(e.target.value); setPage(0); }}/></label><label>Status<select value={status} onChange={e => { setStatus(e.target.value); setPage(0); }}><option value="">All statuses</option>{statuses.map(s => <option key={s} value={s}>{readable(s)}</option>)}</select></label></div>
    {data.isPending ? <p role="status">Loading your projects…</p> : data.isError ? <><ErrorMessage error={data.error}/><button onClick={() => void data.refetch()}>Try again</button></> : <>{data.data.items.length === 0 ? <section className="panel"><h2>No projects found</h2><p>Try another search, or ask your project manager to add you by email.</p></section> : <div className="project-grid">{data.data.items.map(p => <Link className="panel project-card" key={p.id} to={`/projects/${p.id}`}><div className="section-heading"><span className={`badge ${p.status.toLowerCase()}`}>{readable(p.status)}</span><span>{readable(p.role)}</span></div><h2>{p.name}</h2><p>{p.description || 'No description yet.'}</p><div className="hint">Target: {p.targetDate ?? 'Not set'} · {p.completedTasks}/{p.totalTasks} tasks complete</div><progress value={p.progressPercent} max={100} aria-label={`${p.name}: ${p.progressPercent}% complete`}/></Link>)}</div>}<div className="pagination"><button disabled={!page} onClick={() => setPage(page - 1)}>Previous</button><span>Page {page + 1}</span><button disabled={!data.data.hasNext} onClick={() => setPage(page + 1)}>Next</button></div></>}
    <p className="hint">Open a project to view its documents. The AI assistant will arrive in a later release.</p></>;
}
function ProjectDetail() {
  const { id = '' } = useParams(); const cache = useQueryClient(); const navigate = useNavigate(); const { user } = useContext(Auth);
  const [editing, setEditing] = useState(false); const [email, setEmail] = useState(''); const [role, setRole] = useState<api.MemberRole>('MEMBER');
  const project = useQuery({ queryKey: ['project', id], queryFn: ({ signal }) => api.getProject(id, signal) });
  const members = useQuery({ queryKey: ['members', id], queryFn: ({ signal }) => api.getMembers(id, signal) });
  const refresh = () => { void cache.invalidateQueries(); };
  const add = useMutation({ mutationFn: () => api.addMember(id, email, role), onSuccess: () => { setEmail(''); refresh(); } });
  const change = useMutation({ mutationFn: ({ userId, value }: { userId: string; value: api.MemberRole }) => api.setMemberRole(id, userId, value), onSuccess: refresh });
  const remove = useMutation({ mutationFn: (userId: string) => api.removeMember(id, userId), onSuccess: refresh });
  const destroy = useMutation({ mutationFn: () => api.deleteProject(id), onSuccess: () => { refresh(); navigate('/projects'); } });
  if (project.isPending) return <p role="status">Loading project…</p>;
  if (project.isError) return <><ErrorMessage error={project.error}/><Link to="/projects">Return to projects</Link></>;
  const p = project.data; const manage = p.role === 'ADMIN' || p.role === 'MANAGER';
  const busy = add.isPending || change.isPending || remove.isPending;
  return <><Link to="/projects">← All projects</Link><div className="heading"><div><span className="eyebrow">{readable(p.role)} ACCESS</span><h1>{p.name}</h1></div><span className="badge">{readable(p.status)}</span></div>
    {editing ? <ProjectForm project={p} onCancel={() => setEditing(false)} onDone={() => { setEditing(false); refresh(); }}/>: <section className="panel"><h2>Project overview</h2><p className="description">{p.description || 'No description yet.'}</p><dl className="dates"><div><dt>Start date</dt><dd>{p.startDate ?? 'Not set'}</dd></div><div><dt>Target date</dt><dd>{p.targetDate ?? 'Not set'}</dd></div></dl>{manage && <div className="actions"><button onClick={() => setEditing(true)}>Edit project</button><button className="danger" disabled={destroy.isPending} onClick={() => { if (window.confirm(`Delete “${p.name}” and its memberships? This cannot be undone.`)) destroy.mutate(); }}>Delete project</button></div>}<ErrorMessage error={destroy.error}/></section>}
    <Tasks key={p.id} project={p} userId={user!.id}/><Documents key={`documents-${p.id}`} project={p} userId={user!.id}/><section className="panel member-panel"><h2>Project members</h2><p className="hint">Managers manage this project. Members and viewers can read it.</p>{members.isPending ? <p role="status">Loading members…</p> : members.isError ? <ErrorMessage error={members.error}/> : <ul className="member-list">{members.data.map(m => <li key={m.userId}><div><strong>{m.displayName}{m.userId === user?.id ? ' (you)' : ''}</strong><small>{m.email}</small></div>{manage ? <div className="actions"><label className="sr-only" htmlFor={`role-${m.userId}`}>Role for {m.email}</label><select id={`role-${m.userId}`} value={m.role} disabled={busy} onChange={e => change.mutate({ userId: m.userId, value: e.target.value as api.MemberRole })}>{roles.map(r => <option key={r}>{r}</option>)}</select><button className="danger" disabled={busy} onClick={() => { if (window.confirm(`Remove ${m.email} from this project?`)) remove.mutate(m.userId); }}>Remove</button></div>: <span className="badge">{readable(m.role)}</span>}</li>)}</ul>}
    {manage && <form className="add-member" onSubmit={e => { e.preventDefault(); add.mutate(); }}><h3>Add a registered user</h3><div className="form-row"><label>Email address<input type="email" required maxLength={254} value={email} onChange={e => setEmail(e.target.value)}/></label><label>Project role<select value={role} onChange={e => setRole(e.target.value as api.MemberRole)}>{roles.map(r => <option key={r}>{r}</option>)}</select></label></div><button disabled={busy}>{add.isPending ? 'Adding…' : 'Add member'}</button></form>}<ErrorMessage error={add.error || change.error || remove.error}/></section></>;
}
function Profile() {
  const { user } = useContext(Auth);
  const current = useQuery({ queryKey: ['me'], queryFn: ({ signal }) => api.request<api.User>('/auth/me', { signal }) });
  return <><h1>Your profile</h1><section className="panel"><h2>{current.data?.displayName ?? user?.displayName}</h2><p>{current.data?.email ?? user?.email}</p><p>{current.data?.globalRole ?? user?.globalRole}</p><ErrorMessage error={current.error}/><p className="hint">Tokens stay in memory and expire after 15 minutes. Sign out when you finish.</p></section></>;
}
function Workspace() {
  const { user, logout } = useContext(Auth);
  if (!user) return <Navigate to="/login" replace/>;
  return <div className="shell"><a className="skip-link" href="#main">Skip to content</a><aside className="sidebar"><Link to="/projects" className="brand"><span className="brand-mark" aria-hidden="true">S</span>Struct<span>IQ</span></Link><div className="workspace-label">WORKSPACE</div><nav aria-label="Main navigation"><NavLink to="/" end>Overview</NavLink><NavLink to="/projects">Projects</NavLink><NavLink to="/tasks">My Tasks</NavLink><NavLink to="/profile">Profile</NavLink></nav><div className="sidebar-note">Signed in as<p>{user.displayName}</p><button onClick={logout}>Sign out</button></div></aside><div className="content"><header><span>Workspace</span><button className="mobile-signout" onClick={logout}>Sign out</button><span>{user.displayName}</span></header><main id="main"><Routes><Route path="/projects" element={<Projects/>}/><Route path="/tasks" element={<><h1>My tasks</h1><Tasks userId={user.id}/></>}/><Route path="/projects/:id" element={<ProjectDetail/>}/><Route path="/profile" element={<Profile/>}/><Route path="/" element={<Dashboard/>}/><Route path="*" element={<><h1>Page not found</h1><Link to="/projects">Return to projects</Link></>}/></Routes></main><footer>StructIQ <span>Project clarity, from the ground up.</span></footer></div></div>;
}
function App() { return <Routes><Route path="/login" element={<Login/>}/><Route path="*" element={<Workspace/>}/></Routes>; }
ReactDOM.createRoot(document.getElementById('root')!).render(<React.StrictMode><QueryClientProvider client={queryClient}><AuthProvider><BrowserRouter><App/></BrowserRouter></AuthProvider></QueryClientProvider></React.StrictMode>);
