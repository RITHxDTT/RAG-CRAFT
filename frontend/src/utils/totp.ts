// RFC 6238 TOTP (SHA-1, 6 digits, 30 s) on Web Crypto, so the demo authenticator flow really works with an authenticator app.
const ALPHABET = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ234567';
export function randomSecret(bytes = 20): string {
    const data = crypto.getRandomValues(new Uint8Array(bytes));
    let bits = '';
    data.forEach(byte => { bits += byte.toString(2).padStart(8, '0'); });
    return (bits.match(/.{1,5}/g) ?? []).map(chunk => ALPHABET[parseInt(chunk.padEnd(5, '0'), 2)]).join('');
}
function decode(secret: string): Uint8Array {
    const bits = secret.replace(/=+$/, '').toUpperCase().split('').map(char => ALPHABET.indexOf(char).toString(2).padStart(5, '0')).join('');
    return new Uint8Array((bits.match(/.{8}/g) ?? []).map(byte => parseInt(byte, 2)));
}
export async function totp(secret: string, time = Date.now()): Promise<string> {
    const counter = new DataView(new ArrayBuffer(8));
    counter.setBigUint64(0, BigInt(Math.floor(time / 30000)));
    const key = await crypto.subtle.importKey('raw', decode(secret) as BufferSource, { name: 'HMAC', hash: 'SHA-1' }, false, ['sign']);
    const hash = new Uint8Array(await crypto.subtle.sign('HMAC', key, counter));
    const offset = hash[hash.length - 1] & 15;
    const value = ((hash[offset] & 127) << 24) | (hash[offset + 1] << 16) | (hash[offset + 2] << 8) | hash[offset + 3];
    return String(value % 1_000_000).padStart(6, '0');
}
/** Accepts the previous, current and next window to tolerate clock drift. */
export async function verifyTotp(secret: string, code: string, time = Date.now()): Promise<boolean> {
    if (!/^\d{6}$/.test(code.trim()))
        return false;
    for (const drift of [-1, 0, 1])
        if (await totp(secret, time + drift * 30000) === code.trim())
            return true;
    return false;
}
export const otpauthUri = (secret: string, email: string) => `otpauth://totp/RAG%20Craft:${encodeURIComponent(email)}?secret=${secret}&issuer=RAG%20Craft&digits=6&period=30`;
