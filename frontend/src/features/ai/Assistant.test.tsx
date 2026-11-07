import { afterEach, expect, it } from 'vitest';
import { renderToStaticMarkup } from 'react-dom/server';
import { MemoryRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { Answer, AnswerText, Assistant } from './Assistant';
const caches: QueryClient[] = [];
afterEach(() => caches.splice(0).forEach(c => c.clear()));
function client() { const c = new QueryClient({ defaultOptions: { queries: { staleTime: Infinity, retry: false } } }); caches.push(c); return c; }
it('explains no-key mode without showing a broken chat form', () => {
  const cache = client(); cache.setQueryData(['ai-status', 'project-a'], { configured: false });
  const html = renderToStaticMarkup(<QueryClientProvider client={cache}><MemoryRouter><Assistant projectId="project-a"/></MemoryRouter></QueryClientProvider>);
  expect(html).toContain('AI is not configured'); expect(html).toContain('uploads and downloads remain available');
  expect(html).not.toContain('Ask project assistant</button>');
});
it('shows question input and empty guidance when AI is configured', () => {
  const cache = client(); cache.setQueryData(['ai-status', 'project-a'], { configured: true });
  const html = renderToStaticMarkup(<QueryClientProvider client={cache}><MemoryRouter><Assistant projectId="project-a"/></MemoryRouter></QueryClientProvider>);
  expect(html).toContain('Question about this project'); expect(html).toContain('Only indexed documents');
});
it('renders citations as authenticated download controls and escapes answer text', () => {
  const cache = client();
  const html = renderToStaticMarkup(<QueryClientProvider client={cache}><Answer projectId="project-a" result={{ answer: '<script>untrusted</script>', retrievalCount: 1, citations: [{ chunkId: 'chunk', documentId: 'doc', filename: 'meeting.pdf', pageNumber: 3, excerpt: 'Synthetic source excerpt' }] }}/></QueryClientProvider>);
  expect(html).toContain('&lt;script&gt;'); expect(html).not.toContain('<script>');
  expect(html).toContain('meeting.pdf · page 3'); expect(html).toContain('Synthetic source excerpt');
  expect(html).not.toContain('href="/api'); expect(html).toContain('Verify decisions');
});

it('requires a ready document and links to the library when the project is empty', () => {
  const cache = client(); cache.setQueryData(['ai-status', 'project-a'], { configured: true });
  cache.setQueryData(['documents', 'project-a', 0], { items: [], page: 0, size: 12, hasNext: false, maxUploadBytes: 10485760 });
  const html = renderToStaticMarkup(<QueryClientProvider client={cache}><MemoryRouter><Assistant projectId="project-a"/></MemoryRouter></QueryClientProvider>);
  expect(html).toContain('Your knowledge starts with a document.');
  expect(html).toContain('href="/?tab=documents"');
  expect(html).toMatch(/disabled="">Ask project assistant/);
});
it('formats emphasis and lists while treating HTML and links as untrusted text', () => {
  const html = renderToStaticMarkup(<AnswerText text={'**Owner:** Maya\n\n- Review due Friday\n- <img src=x onerror=alert(1)>\n\n[jump](javascript:alert(1))'}/>);
  expect(html).toContain('<strong>Owner:</strong>');
  expect(html).toContain('<ul>'); expect(html).toContain('&lt;img');
  expect(html).not.toContain('<img'); expect(html).not.toContain('href=');
});
