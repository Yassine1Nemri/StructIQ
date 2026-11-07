import { afterEach, expect, it } from 'vitest';
import { renderToStaticMarkup } from 'react-dom/server';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { Documents } from './Documents';
import type { DocumentMetadata, Project } from '../../lib/api';
const caches: QueryClient[] = [];
afterEach(() => caches.splice(0).forEach(c => c.clear()));
const project: Project = { id: 'project-a', name: 'Fictional site', description: '', status: 'ACTIVE', startDate: null, targetDate: null, createdBy: 'manager', role: 'MANAGER', totalTasks: 0, completedTasks: 0, progressPercent: 0 };
const metadata: DocumentMetadata = { id: 'document-a', projectId: project.id, filename: 'meeting.txt', mimeType: 'text/plain', sizeBytes: 1024, processingStatus: 'UPLOADED', processingCode: 'TEXT_EXTRACTED_AI_PENDING', extractedPages: 2, uploadedBy: 'member', createdAt: '2026-11-07T00:00:00Z' };
function render(role: Project['role'], uploadedBy = 'member', status: DocumentMetadata['processingStatus'] = 'UPLOADED') {
  const client = new QueryClient({ defaultOptions: { queries: { staleTime: Infinity, retry: false } } }); caches.push(client);
  client.setQueryData(['documents', project.id, 0], { items: [{ ...metadata, uploadedBy, processingStatus: status, processingCode: status === 'FAILED' ? 'NO_TEXT' : metadata.processingCode }], page: 0, size: 12, hasNext: false, maxUploadBytes: 10 * 1024 * 1024 });
  return renderToStaticMarkup(<QueryClientProvider client={client}><Documents project={{ ...project, role }} userId="member"/></QueryClientProvider>);
}
it('lets a viewer download but hides upload, retry and deletion controls', () => {
  const markup = render('VIEWER', 'member', 'FAILED');
  expect(markup).toContain('Download');
  expect(markup).not.toContain('Upload document');
  expect(markup).not.toContain('Retry extraction');
  expect(markup).not.toContain('>Delete');
});
it('restricts member deletion to own uploads and lets managers delete others', () => {
  expect(render('MEMBER')).toContain('>Delete');
  expect(render('MEMBER', 'someone-else')).not.toContain('>Delete');
  expect(render('MANAGER', 'someone-else')).toContain('>Delete');
});
it('shows extracted text as pending AI indexing and exposes useful scanned-PDF failures', () => {
  expect(render('MEMBER')).toContain('Ready to index for AI');
  const failed = render('MEMBER', 'member', 'FAILED');
  expect(failed).toContain('Scanned PDFs need OCR');
  expect(failed).toContain('Retry extraction');
});
