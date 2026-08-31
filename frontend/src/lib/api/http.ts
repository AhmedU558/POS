import { apiClient } from '../apiClient';

/** Spring Data page envelope, as returned inside the `data` field of every paged endpoint. */
export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
}

export function emptyPage<T>(size = 20): Page<T> {
  return { content: [], totalElements: 0, totalPages: 0, size, number: 0 };
}

/**
 * An API failure carrying the documented error code (REST API Specification section 5.2), so a
 * screen can react to a specific rule — REGISTER_SESSION_REQUIRED, CONFLICT — instead of
 * pattern-matching on message text.
 */
export class ApiError extends Error {
  constructor(
    public readonly status: number,
    message: string,
    public readonly code?: string,
    public readonly fieldErrors?: Record<string, string>
  ) {
    super(message);
    this.name = 'ApiError';
  }
}

interface ErrorBody {
  error?: {
    code?: string;
    message?: string;
    details?: { field?: string; message?: string }[] | Record<string, string>;
  };
}

function readFieldErrors(body: ErrorBody): Record<string, string> | undefined {
  const details = body.error?.details;
  if (!details) {
    return undefined;
  }
  if (Array.isArray(details)) {
    const mapped: Record<string, string> = {};
    for (const detail of details) {
      if (detail.field && detail.message) {
        mapped[detail.field] = detail.message;
      }
    }
    return Object.keys(mapped).length > 0 ? mapped : undefined;
  }
  return details as Record<string, string>;
}

export async function unwrap<T>(response: Response): Promise<T> {
  if (response.status === 204) {
    return undefined as T;
  }
  const body = await response.json().catch(() => ({}) as ErrorBody);
  if (!response.ok) {
    const error = (body as ErrorBody).error;
    throw new ApiError(
      response.status,
      error?.message || defaultMessageFor(response.status),
      error?.code,
      readFieldErrors(body as ErrorBody)
    );
  }
  return (body as { data: T }).data;
}

function defaultMessageFor(status: number): string {
  switch (status) {
    case 401:
      return 'Your session has expired. Sign in again.';
    case 403:
      return 'You do not have permission to do that.';
    case 404:
      return 'That record no longer exists.';
    case 409:
      return 'That change conflicts with existing data.';
    case 429:
      return 'Too many requests. Wait a moment and try again.';
    default:
      return status >= 500 ? 'The server could not complete the request.' : 'The request could not be completed.';
  }
}

type QueryValue = string | number | boolean | null | undefined;

/** Drops empty parameters so a blank filter never narrows a search to nothing. */
export function query(params: Record<string, QueryValue>): string {
  const search = new URLSearchParams();
  for (const [key, value] of Object.entries(params)) {
    if (value === undefined || value === null || value === '') {
      continue;
    }
    search.append(key, String(value));
  }
  const serialised = search.toString();
  return serialised ? `?${serialised}` : '';
}

export async function get<T>(path: string): Promise<T> {
  return unwrap<T>(await apiClient(path, { method: 'GET' }));
}

export async function post<T>(path: string, body?: unknown, headers?: Record<string, string>): Promise<T> {
  return unwrap<T>(
    await apiClient(path, {
      method: 'POST',
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
    })
  );
}

export async function patch<T>(path: string, body?: unknown): Promise<T> {
  return unwrap<T>(
    await apiClient(path, { method: 'PATCH', body: body === undefined ? undefined : JSON.stringify(body) })
  );
}

export async function put<T>(path: string, body?: unknown): Promise<T> {
  return unwrap<T>(await apiClient(path, { method: 'PUT', body: body === undefined ? undefined : JSON.stringify(body) }));
}

export async function del<T>(path: string): Promise<T> {
  return unwrap<T>(await apiClient(path, { method: 'DELETE' }));
}
