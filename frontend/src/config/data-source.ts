/**
 * "local" (default): everything lives in the browser, as in the Vercel demo.
 * "api": accounts, chatbots, catalog, knowledge sources and channels come from the Spring services behind the API gateway
 * (PostgreSQL, database craftrag_db). Opt in with NEXT_PUBLIC_DATA_SOURCE=api and NEXT_PUBLIC_API_URL=<gateway URL>.
 * Next.js inlines NEXT_PUBLIC_* values at build time, so restart `next dev` after changing them.
 */
export const API_URL = (process.env.NEXT_PUBLIC_API_URL ?? '').replace(/\/+$/, '');
export const apiMode = process.env.NEXT_PUBLIC_DATA_SOURCE === 'api' && API_URL.length > 0;
