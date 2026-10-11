import { passwordProblem } from './password-policy';
export const id = (prefix: string) => `${prefix}_${crypto.randomUUID()}`;
export const now = () => new Date().toISOString();
export const delay = (ms = 250) => new Promise<void>(resolve => setTimeout(resolve, ms));
export function email(value: string) {
    const normalized = value.trim().toLowerCase();
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(normalized))
        throw new Error('Enter a valid email address.');
    return normalized;
}
export function password(value: string, confirmation: string, address = '') {
    if (!value.trim())
        throw new Error('Password is required.');
    if (value !== confirmation)
        throw new Error('Passwords must match.');
    const problem = passwordProblem(value, address);
    if (problem)
        throw new Error(problem);
}
export function required(value: string, label: string) {
    if (!value.trim())
        throw new Error(`${label} is required.`);
    return value.trim();
}
export function toast(message: string) { window.dispatchEvent(new CustomEvent('ragcraft:toast', { detail: message })); }
export const slug = (name: string) => name.toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '') || 'chatbot';
