import { useRef, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import * as api from '../../lib/api';
import { ErrorMessage } from '../../components/Feedback';

const failures: Record<string, string> = {
  NO_TEXT: 'No text found. Scanned PDFs need OCR; use a text-based PDF or TXT file.',
  INVALID_PDF: 'The PDF could not be read. Export a new PDF and upload it again.',
  INVALID_TEXT: 'The file is not supported plain UTF-8 text.',
  ENCRYPTED_PDF: 'Password-protected PDFs are not supported. Upload an unencrypted copy.',
  EXTRACTION_LIMIT: 'This document exceeds the text, page, or parsing limits. Split it into smaller documents.',
  EXTRACTION_TIMEOUT: 'Text extraction timed out. Try a smaller document.',
  PARSER_UNAVAILABLE: 'Text extraction is unavailable. The stored file can still be downloaded; retry later.',
  PROCESSING_INTERRUPTED: 'Text extraction was interrupted. Retry to process the stored file.',
};
export function documentStatus(document: api.DocumentMetadata): string {
  if (document.processingStatus === 'FAILED') return failures[document.processingCode] ?? 'Text extraction failed. Retry or replace this document.';
  if (document.processingStatus === 'PROCESSING') return 'Extracting text…';
  if (document.processingStatus === 'READY') return 'Ready for AI';
  return document.extractedPages ? `Text extracted from ${document.extractedPages} page(s). Ready to index for AI.` : 'Stored. Text extraction pending.';
}
export function Documents({ project, userId }: { project: api.Project; userId: string }) {
  const cache = useQueryClient(); const inputRef = useRef<HTMLInputElement>(null);
  const [page, setPage] = useState(0); const [file, setFile] = useState<File | null>(null); const [fileError, setFileError] = useState<Error | null>(null);
  const [downloadName, setDownloadName] = useState('');
  const query = useQuery({ queryKey: ['documents', project.id, page], queryFn: ({ signal }) => api.listDocuments(project.id, page, signal),
    refetchInterval: q => q.state.data?.items.some(d => d.processingStatus === 'PROCESSING') ? 2000 : false });
  const uploadAllowed = project.role !== 'VIEWER'; const manage = project.role === 'ADMIN' || project.role === 'MANAGER';
  const refresh = () => { void cache.invalidateQueries({ queryKey: ['documents', project.id] }); };
  const upload = useMutation({ mutationFn: (value: File) => api.uploadDocument(project.id, value), onSuccess: () => { setFile(null); if (inputRef.current) inputRef.current.value = ''; setPage(0); refresh(); } });
  const remove = useMutation({ mutationFn: (id: string) => api.deleteDocument(project.id, id), onSuccess: refresh });
  const retry = useMutation({ mutationFn: (id: string) => api.retryDocument(project.id, id), onSuccess: refresh });
  const index = useMutation({ mutationFn: (id: string) => api.indexDocument(project.id, id), onSuccess: refresh });
  const aiStatus = useQuery({ queryKey: ['ai-status', project.id], queryFn: ({ signal }) => api.getAiStatus(project.id, signal) });
  const download = useMutation({ mutationFn: async (d: api.DocumentMetadata) => {
    const blob = await api.downloadDocument(project.id, d.id);
    const url = URL.createObjectURL(blob); const link = document.createElement('a');
    link.href = url; link.download = d.filename; document.body.appendChild(link); link.click(); link.remove();
    window.setTimeout(() => URL.revokeObjectURL(url), 30_000); setDownloadName(d.filename);
  } });
  const maxBytes = query.data?.maxUploadBytes ?? 10 * 1024 * 1024;
  function choose(files: FileList | null) {
    setFileError(null); upload.reset();
    if (!files?.length) { setFile(null); return; }
    const value = files[0];
    if (files.length !== 1 || !/\.(pdf|txt)$/i.test(value.name) || !value.size || value.size > maxBytes) {
      setFile(null); setFileError(new Error(`Choose one non-empty PDF or UTF-8 TXT file, up to ${(maxBytes / 1024 / 1024).toFixed(1)} MiB.`)); return;
    }
    setFile(value);
  }
  return <section className="document-section" aria-labelledby="documents-heading"><div className="section-heading"><h2 id="documents-heading">Project documents</h2><span>Private project files</span></div>
    {uploadAllowed && <form className="panel document-upload" onSubmit={e => { e.preventDefault(); if (file) upload.mutate(file); }}>
      <div className="drop-zone" onDragOver={e => e.preventDefault()} onDrop={e => { e.preventDefault(); if (!upload.isPending) choose(e.dataTransfer.files); }}>
        <label>Drop a document here or choose a file<input ref={inputRef} type="file" accept=".pdf,.txt,application/pdf,text/plain" disabled={upload.isPending || !query.data} onChange={e => choose(e.target.files)}/></label>
        <p className="hint">PDF or plain UTF-8 TXT · up to {(maxBytes / 1024 / 1024).toFixed(1)} MiB. Scanned and encrypted PDFs cannot be extracted.</p></div>
      {file && <p className="hint">Selected: {file.name}</p>}<ErrorMessage error={fileError || upload.error}/>
      <button className="primary" disabled={!file || upload.isPending || !query.data}>{upload.isPending ? 'Uploading and extracting text…' : 'Upload document'}</button>
      {upload.isPending && <p role="status">Keep this page open while the document is stored and processed.</p>}
    </form>}
    <ErrorMessage error={remove.error || retry.error || index.error || download.error}/>{downloadName && <p className="hint" role="status">Download requested: {downloadName}</p>}
    {query.isPending ? <p role="status">Loading documents…</p> : query.isError ? <><ErrorMessage error={query.error}/><button onClick={() => void query.refetch()}>Try again</button></> : <>
      {!query.data.items.length ? <div className="panel"><h3>No documents yet</h3><p>{uploadAllowed ? 'Upload a meeting report or project document to keep it with this project.' : 'A project member can upload documents for you to read.'}</p></div> : <ul className="document-list">{query.data.items.map(d => <li className="panel document-card" key={d.id}>
        <div><h3>{d.filename}</h3><p className="hint">{d.mimeType === 'application/pdf' ? 'PDF' : 'TXT'} · {(d.sizeBytes / 1024).toFixed(1)} KiB · {d.createdAt.slice(0, 10)}</p><p className={d.processingStatus === 'FAILED' ? 'document-failed' : 'hint'}>{documentStatus(d)}</p></div>
        <div className="actions"><button disabled={download.isPending} onClick={() => download.mutate(d)}>Download<span className="sr-only"> {d.filename}</span></button>
          {uploadAllowed && aiStatus.data?.configured && d.extractedPages > 0 && d.processingStatus !== 'PROCESSING' && <button disabled={index.isPending} onClick={() => index.mutate(d.id)}>{index.isPending ? 'Indexing…' : d.processingStatus === 'READY' ? 'Reindex for AI' : 'Index for AI'}</button>}
          {uploadAllowed && d.processingStatus === 'FAILED' && <button disabled={retry.isPending} onClick={() => retry.mutate(d.id)}>{retry.isPending ? 'Retrying…' : 'Retry extraction'}</button>}
          {uploadAllowed && (manage || d.uploadedBy === userId) && <button className="danger" disabled={remove.isPending} onClick={() => { if (window.confirm(`Delete document “${d.filename}” and its extracted data?`)) remove.mutate(d.id); }}>Delete<span className="sr-only"> {d.filename}</span></button>}</div>
      </li>)}</ul>}
      <div className="pagination"><button disabled={!page} onClick={() => setPage(page - 1)}>Previous documents</button><span>Page {page + 1}</span><button disabled={!query.data.hasNext} onClick={() => setPage(page + 1)}>Next documents</button></div></>}
    <p className="hint">Downloads require your current project access. Ask the project assistant after documents are indexed.</p>
  </section>;
}
