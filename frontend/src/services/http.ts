import { API_URL } from '@/config/data-source';
import { KEYS } from '@/storage/keys';
import { storage } from '@/storage/local-storage';
import { ApiError } from './api';
interface StoredToken { token: string; expires_at: string }
/** The JWT issued by identity-service. It lives in localStorage and is sent as a Bearer header. */
export const apiToken = {
    get(): string | null {
        try {
            const stored = storage.read<StoredToken | null>(KEYS.apiToken, null);
            return stored && Date.parse(stored.expires_at) > Date.now() ? stored.token : null;
        }
        catch {
            return null;
        }
    },
    save(token: string, expiresInSeconds: number) { storage.writeQuiet<StoredToken>(KEYS.apiToken, { token, expires_at: new Date(Date.now() + expiresInSeconds * 1000).toISOString() }); },
    clear() { storage.remove(KEYS.apiToken); },
};
/** Names the address that failed. A browser reports a CORS block exactly like a dead server, so the origin is part of the message. */
function unreachable() {
    const origin = typeof window === 'undefined' ? '' : window.location.origin;
    return `Cannot reach the API at ${API_URL}${origin ? ` from ${origin}` : ''}. Check that the gateway is running and that this page's address is listed in CORS_ORIGINS.`;
}
type Query = Record<string, string | number | boolean | null | undefined>;
interface Options { body?: unknown; query?: Query; form?: FormData; auth?: boolean; method?: string }
function url(path: string, query?: Query) {
    const params = Object.entries(query ?? {}).filter(([, value]) => value !== undefined && value !== null && value !== '');
    return `${API_URL}${path}${params.length ? `?${new URLSearchParams(params.map(([key, value]) => [key, String(value)]))}` : ''}`;
}
function describe(body: unknown, status: number): { message: string; code?: string } {
    const record = (body ?? {}) as { detail?: unknown; code?: string; issues?: { field: string; message: string }[] };
    const issues = Array.isArray(record.issues) && record.issues.length ? record.issues.map(issue => `${issue.field}: ${issue.message}`).join('; ') : '';
    const detail = typeof record.detail === 'string' ? record.detail : '';
    return { message: issues ? `${detail} ${issues}`.trim() : detail || `The server returned an error (${status}).`, code: record.code };
}
/** Calls the gateway. Failures become ApiError with the server's message and optional code; a rejected token signs the user out. */
export async function http<T>(method: string, path: string, options: Options = {}): Promise<T> {
    const headers: Record<string, string> = { Accept: 'application/json' };
    const token = options.auth === false ? null : apiToken.get();
    if (token)
        headers.Authorization = `Bearer ${token}`;
    let body: BodyInit | undefined;
    if (options.form)
        body = options.form;
    else if (options.body !== undefined) {
        headers['Content-Type'] = 'application/json';
        body = JSON.stringify(options.body);
    }
    let response: Response;
    try {
        response = await fetch(url(path, options.query), { method, headers, body });
    }
    catch {
        throw new ApiError(0, unreachable(), 'NETWORK');
    }
    if (response.status === 204)
        return undefined as T;
    const text = await response.text();
    let parsed: unknown = null;
    try {
        parsed = text ? JSON.parse(text) : null;
    }
    catch {
        parsed = null;
    }
    if (!response.ok) {
        const { message, code } = describe(parsed, response.status);
        // A token the server no longer accepts ends the session (sign-in itself reports its own 401 without this).
        if (response.status === 401 && token && !path.startsWith('/api/auth/login')) {
            apiToken.clear();
            window.dispatchEvent(new Event('ragcraft:session-expired'));
        }
        throw new ApiError(response.status, message, code);
    }
    return parsed as T;
}
export const get = <T,>(path: string, query?: Query) => http<T>('GET', path, { query });
export const post = <T,>(path: string, body?: unknown, options: Options = {}) => http<T>('POST', path, { ...options, body });
export const put = <T,>(path: string, body?: unknown) => http<T>('PUT', path, { body });
export const patch = <T,>(path: string, body?: unknown) => http<T>('PATCH', path, { body });
export const del = <T,>(path: string, query?: Query) => http<T>('DELETE', path, { query });
/** Downloads a protected file as a Blob (a plain link cannot carry the Authorization header). */
export async function download(path: string): Promise<Blob> {
    const token = apiToken.get();
    let response: Response;
    try {
        response = await fetch(url(path), { headers: token ? { Authorization: `Bearer ${token}` } : {} });
    }
    catch {
        throw new ApiError(0, unreachable(), 'NETWORK');
    }
    if (!response.ok)
        throw new ApiError(response.status, response.status === 404 ? 'The original file is not available.' : `The file could not be downloaded (${response.status}).`);
    return response.blob();
}
