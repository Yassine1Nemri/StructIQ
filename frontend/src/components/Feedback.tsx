import { ApiError } from '../lib/api';
export function ErrorMessage({ error }: { error: Error | null }) {
  if (!error) return null;
  return <div className="notice error" role="alert">{error.message}{error instanceof ApiError && error.correlationId && <small>Reference: {error.correlationId}</small>}</div>;
}
