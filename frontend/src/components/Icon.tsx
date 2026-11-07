import type { CSSProperties } from 'react';

export type IconName = 'grid' | 'projects' | 'tasks' | 'user' | 'arrow' | 'spark' | 'file' | 'check' | 'clock' | 'layers' | 'upload' | 'logout' | 'search';
const paths: Record<IconName, string[]> = {
  grid: ['M3 3h7v7H3z M14 3h7v7h-7z M3 14h7v7H3z M14 14h7v7h-7z'],
  projects: ['M3 7h7l2 2h9v11H3z', 'M3 7V4h7l2 3'],
  tasks: ['M9 5h12 M9 12h12 M9 19h12', 'm3 5 1 1 2-2 m-3 8 1 1 2-2 m-3 8 1 1 2-2'],
  user: ['M16 7a4 4 0 1 1-8 0 4 4 0 0 1 8 0', 'M4 21v-2a8 8 0 0 1 16 0v2'],
  arrow: ['M5 12h14 m-6-6 6 6-6 6'],
  spark: ['m12 3 2.5 6.5L21 12l-6.5 2.5L12 21l-2.5-6.5L3 12l6.5-2.5z'],
  file: ['M14 3H5v18h14V8z M14 3v5h5 M8 12h8 M8 16h6'],
  check: ['m5 12 4 4L19 6'],
  clock: ['M21 12a9 9 0 1 1-18 0 9 9 0 0 1 18 0', 'M12 7v5l3 2'],
  layers: ['m12 3 10 5-10 5L2 8z', 'm2 12 10 5 10-5 M2 16l10 5 10-5'],
  upload: ['M12 16V3 m-5 5 5-5 5 5', 'M4 15v6h16v-6'],
  logout: ['M9 3H3v18h6 M9 12h12 m-5-5 5 5-5 5'],
  search: ['M17 10a7 7 0 1 1-14 0 7 7 0 0 1 14 0', 'm15 15 6 6'],
};
export function Icon({ name, size = 20, style }: { name: IconName; size?: number; style?: CSSProperties }) {
  return <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true" style={style}>{paths[name].map((d, i) => <path d={d} key={i}/>)}</svg>;
}
