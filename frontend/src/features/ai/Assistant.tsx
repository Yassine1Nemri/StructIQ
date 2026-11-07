import { useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { useMutation, useQuery } from '@tanstack/react-query';
import * as api from '../../lib/api';
import { ErrorMessage } from '../../components/Feedback';
import { Icon } from '../../components/Icon';

// React escapes all source/model text. Only a small display-only subset is formatted.
function InlineText({ text }: { text: string }) {
  return <>{text.split(/(\*\*[^*]+\*\*)/g).map((part, i) => part.startsWith('**') && part.endsWith('**') ? <strong key={i}>{part.slice(2, -2)}</strong> : part)}</>;
}
export function AnswerText({ text }: { text: string }) {
  return <div className="answer-prose">{text.split(/\n\s*\n/).map((block, i) => {
    const lines = block.split('\n');
    if (lines.every(line => /^\s*([-*]|\d+[.)])\s/.test(line))) return <ul key={i}>{lines.map((line, j) => <li key={j}><InlineText text={line.replace(/^\s*([-*]|\d+[.)])\s+/, '')}/></li>)}</ul>;
    if (/^#{1,3}\s/.test(block)) return <h4 key={i}><InlineText text={block.replace(/^#{1,3}\s+/, '')}/></h4>;
    return <p key={i}><InlineText text={block}/></p>;
  })}</div>;
}
export function Answer({ result, projectId }: { result: api.AiAnswer; projectId: string }) {
  const download = useMutation({ mutationFn: async (citation: api.Citation) => {
    const blob = await api.downloadDocument(projectId, citation.documentId);
    const url = URL.createObjectURL(blob); const link = document.createElement('a');
    link.href = url; link.download = citation.filename; document.body.appendChild(link); link.click(); link.remove();
    window.setTimeout(() => URL.revokeObjectURL(url), 30_000);
  } });
  return <div className="ai-answer"><AnswerText text={result.answer}/>{result.citations.length > 0 && <><div className="source-heading"><Icon name="check" size={14}/><h3>Sources</h3><span>{result.citations.length} cited {result.citations.length === 1 ? 'passage' : 'passages'}</span></div><ul className="citation-list">{result.citations.map((c, i) => <li key={c.chunkId}><span className="citation-number">{i + 1}</span><div><button onClick={() => download.mutate(c)} disabled={download.isPending}><Icon name="file" size={15}/>{c.filename} · page {c.pageNumber}<Icon name="arrow" size={14}/></button><blockquote>{c.excerpt}</blockquote></div></li>)}</ul></>}
    <ErrorMessage error={download.error}/><p className="answer-caution">AI answers may be incomplete or incorrect. Verify decisions against the source documents.</p></div>;
}
const suggestions = [
  { icon: 'layers' as const, title: 'Give me the briefing', question: 'Summarize the key decisions, outstanding actions, and deadlines in the project documents.' },
  { icon: 'tasks' as const, title: 'Find the next steps', question: 'Which actions are still pending, and who is responsible for each one?' },
  { icon: 'clock' as const, title: 'Check the deadlines', question: 'What deadlines and upcoming meetings are mentioned in the project documents?' },
  { icon: 'search' as const, title: 'Spot the blockers', question: 'What risks, blockers, or unresolved issues are explicitly mentioned in the documents?' },
];
export function Assistant({ projectId }: { projectId: string }) {
  const [question, setQuestion] = useState('');
  const [history, setHistory] = useState<{ question: string; result: api.AiAnswer }[]>([]);
  const input = useRef<HTMLTextAreaElement>(null);
  const latest = useRef<HTMLDivElement>(null);
  const status = useQuery({ queryKey: ['ai-status', projectId], queryFn: ({ signal }) => api.getAiStatus(projectId, signal), refetchInterval: 30_000 });
  const documents = useQuery({ queryKey: ['documents', projectId, 0], queryFn: ({ signal }) => api.listDocuments(projectId, 0, signal) });
  const ready = documents.data?.items.filter(d => d.processingStatus === 'READY') ?? [];
  const knownEmpty = !!documents.data && !ready.length && !documents.data.hasNext;
  const ask = useMutation({ mutationFn: (value: string) => api.askAi(projectId, value), onSuccess: (result, value) => {
    setHistory(old => [...old.slice(-9), { question: value, result }]); setQuestion('');
  } });
  useEffect(() => { if (history.length) latest.current?.scrollIntoView({ behavior: 'smooth', block: 'nearest' }); }, [history.length]);
  function choose(value: string) { setQuestion(value); input.current?.focus(); }
  function submit() { if (question.trim() && !ask.isPending && !knownEmpty) ask.mutate(question.trim()); }
  return <section id="assistant" className="assistant-workspace" aria-labelledby="assistant-heading"><div className="assistant-topline"><div><span className="eyebrow">PROJECT INTELLIGENCE</span><h2 id="assistant-heading">Less searching. More knowing.</h2><p>Answers from this project's documents, with the evidence to back them up.</p></div><span className="badge"><Icon name="spark" size={14}/>Project AI assistant</span></div>
    <div className="assistant-layout"><div className="assistant-conversation">
    {status.isPending ? <p role="status">Checking AI availability…</p> : status.isError ? <><ErrorMessage error={status.error}/><button onClick={() => void status.refetch()}>Retry connection</button></> : !status.data.configured ? <div className="ai-unconfigured" role="status"><Icon name="spark" size={26}/><h3>AI is not configured</h3><p>Project management, uploads and downloads remain available. To enable answers, configure local Ollama models or Gemini on the internal service, then index your documents.</p></div> : <>
      {!history.length && <div className="assistant-welcome"><span className="assistant-emblem"><Icon name="spark" size={32}/></span><span className="eyebrow">YOUR PROJECT, IN CONTEXT</span><h3>What would you like<br/>to get clarity on?</h3><p>Turn meeting notes and project reports into a useful next step. Start with a question below.</p><div className="prompt-grid">{suggestions.map(s => <button key={s.title} disabled={ask.isPending || knownEmpty} onClick={() => choose(s.question)}><Icon name={s.icon} size={19}/><strong>{s.title}</strong><Icon name="arrow" size={15}/></button>)}</div></div>}
      <div className="ai-history" aria-live="polite">{history.map((entry, i) => <article key={i}><div className="question-bubble"><span className="eyebrow">YOU ASKED</span><h3>{entry.question}</h3></div><div className="answer-heading"><span className="assistant-mini"><Icon name="spark" size={16}/></span><strong>StructIQ assistant</strong><span>From your documents</span></div><Answer result={entry.result} projectId={projectId}/></article>)}<div ref={latest}/></div>
      {ask.isPending && <div className="thinking" role="status"><span className="thinking-dots"><i/><i/><i/></span><div><strong>Connecting the details…</strong><small>Searching your documents and preparing an answer. Local models may take a moment.</small></div></div>}
      {knownEmpty && <div className="assistant-empty"><Icon name="file"/><div><strong>Your knowledge starts with a document.</strong><p>Upload a PDF or TXT and wait for Ready for AI, then ask your first question.</p><Link to="?tab=documents">Open project documents →</Link></div></div>}
      <form className="ai-composer" onSubmit={e => { e.preventDefault(); submit(); }}><label htmlFor="project-question">Question about this project</label><textarea id="project-question" ref={input} rows={3} maxLength={2000} required placeholder="Ask about decisions, owners, deadlines…" value={question} disabled={ask.isPending} onChange={e => setQuestion(e.target.value)} onKeyDown={e => { if (e.key === 'Enter' && (e.ctrlKey || e.metaKey)) { e.preventDefault(); submit(); } }}/><div className="composer-bottom"><span>{question.length}/2000 <span className="keyboard-hint">· Ctrl + Enter to send</span></span><button className="primary" disabled={!question.trim() || ask.isPending || knownEmpty}>{ask.isPending ? 'Preparing answer…' : 'Ask project assistant'}<Icon name="arrow" size={16}/></button></div></form><ErrorMessage error={ask.error}/><div className="conversation-note"><span>Each question is independent · Only indexed documents are searched</span>{history.length > 0 && <button className="text-button" disabled={ask.isPending} onClick={() => { setHistory([]); ask.reset(); }}>Clear conversation</button>}</div>
    </>}</div><aside className="assistant-sources"><div className="source-library-heading"><Icon name="layers" size={19}/><h3>Project knowledge</h3></div><p>Only this project's indexed documents can inform an answer.</p>{documents.isPending ? <p role="status">Loading sources…</p> : documents.isError ? <><ErrorMessage error={documents.error}/><button onClick={() => void documents.refetch()}>Retry sources</button></> : <><div className="knowledge-count"><strong>{ready.length}{documents.data.hasNext ? '+' : ''}</strong><span>ready {ready.length === 1 ? 'document' : 'documents'}{documents.data.hasNext ? ' shown' : ''}</span></div><ul className="knowledge-files">{documents.data.items.map(d => <li key={d.id}><Icon name="file" size={18}/><div><strong>{d.filename}</strong><small>{d.processingStatus === 'READY' ? 'Ready for AI' : 'Not indexed'} · {d.extractedPages} {d.extractedPages === 1 ? 'page' : 'pages'}</small></div><span className={`file-dot ${d.processingStatus === 'READY' ? 'ready' : ''}`} aria-label={d.processingStatus === 'READY' ? 'Ready' : 'Not indexed'}/></li>)}</ul>{documents.data.hasNext && <p className="hint">First 12 documents shown. Open Documents for the full library.</p>}</>}<Link to="?tab=documents" className="source-manage"><Icon name="upload" size={16}/>Manage documents <Icon name="arrow" size={14}/></Link><div className="source-rule"><Icon name="check" size={17}/><div><strong>Grounded in your sources</strong><p>Follow each citation to its document. If the evidence is missing, the assistant will say so.</p></div></div></aside></div>
  </section>;
}
