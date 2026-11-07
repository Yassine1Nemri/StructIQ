import { Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import * as api from '../../lib/api';
import { ErrorMessage } from '../../components/Feedback';
import { label } from './Tasks';

function Deadlines({ title, items }: { title: string; items: api.Deadline[] }) {
  return <section className="panel"><h2>{title}</h2>{!items.length ? <p className="hint">No unfinished tasks in this date range.</p> : <ul className="deadline-list">{items.map(t => <li key={t.taskId}><Link to={`/projects/${t.projectId}`}><strong>{t.title}</strong><small>{t.projectName} · {t.assigneeName ?? 'Unassigned'}</small></Link><time dateTime={t.dueDate}>{t.dueDate}</time></li>)}</ul>}<p className="hint">Up to 10 tasks, earliest deadline first.</p></section>;
}
function Counts({ title, values }: { title: string; values: Record<string, number> }) {
  const total = Object.values(values).reduce((sum, n) => sum + n, 0);
  return <section className="panel"><h2>{title}</h2>{Object.entries(values).map(([name, count]) => <div className="chart-row" key={name}><div><span>{label(name)}</span><strong>{count}</strong></div><meter min={0} max={Math.max(1, total)} value={count} aria-label={`${label(name)}: ${count} of ${total}`}/></div>)}{!total && <p className="hint">No data yet.</p>}</section>;
}
export function Dashboard() {
  const query = useQuery({ queryKey: ['dashboard'], queryFn: ({ signal }) => api.getDashboard(signal), refetchInterval: 30_000 });
  if (query.isPending) return <p role="status">Loading workspace overview…</p>;
  if (query.isError) return <><ErrorMessage error={query.error}/><button onClick={() => void query.refetch()}>Try again</button></>;
  const d = query.data;
  const metrics: [string, number][] = [['Accessible projects', d.totalProjects], ['Active projects', d.activeProjects], ['Assigned to me', d.assignedTasks], ['Open tasks', d.openTasks], ['Overdue tasks', d.overdueTasks]];
  return <><div className="eyebrow">YOUR WORKSPACE AT A GLANCE</div><div className="heading"><div><h1>A clearer view of the work.</h1><p className="intro">Live project and task totals from your accessible projects.</p></div><Link className="primary" to="/projects">View projects →</Link></div>
    <div className="metric-grid">{metrics.map(([name, value]) => <article className="panel metric" key={name}><h2>{name}</h2><strong>{value}</strong></article>)}</div>
    <p className="hint">As of {d.asOf} UTC. Open and overdue counts include all unfinished work in accessible projects; assigned counts include all statuses. Updates refresh every 30 seconds.</p>
    <div className="dashboard-grid"><Counts title="Project status" values={d.projectStatuses}/><Counts title="Task completion and status" values={d.taskStatuses}/></div>
    <section className="panel"><h2>Project progress</h2>{!d.projectProgress.length ? <p>No accessible projects yet. Ask your manager for access.</p> : d.projectProgress.map(p => <div className="progress-row" key={p.projectId}><div><Link to={`/projects/${p.projectId}`}>{p.name}</Link><span>{p.completedTasks} / {p.totalTasks} done · {p.percent}%</span></div><progress value={p.percent} max={100} aria-label={`${p.name}: ${p.percent}% complete`}/></div>)}<p className="hint">Up to 8 projects by name. Progress is completed tasks divided by all tasks.</p></section>
    <div className="dashboard-grid"><Deadlines title="Overdue work" items={d.overdue}/><Deadlines title="Upcoming: today through 7 days ahead" items={d.upcoming}/></div>
  </>;
}
