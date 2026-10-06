// Browser persistence for a demonstration only. Never use this for production credentials.
export const storage = {
    read<T>(key: string, fallback: T): T {
        if (typeof window === 'undefined')
            throw new Error('Browser storage is unavailable.');
        try {
            const value = window.localStorage.getItem(key);
            return value === null ? fallback : JSON.parse(value) as T;
        }
        catch {
            throw new Error('Browser data cannot be read. Enable storage or reset RAG Craft demo data.');
        }
    },
    write<T>(key: string, value: T) {
        try {
            window.localStorage.setItem(key, JSON.stringify(value));
        }
        catch {
            throw new Error('Browser storage is full or disabled. Remove some demo data and try again.');
        }
        window.dispatchEvent(new Event('ragcraft:data-changed'));
    },
    remove(key: string) { window.localStorage.removeItem(key); },
    clearDemo() {
        Object.keys(window.localStorage).filter(key => key.startsWith('ragcraft:v2:')).forEach(key => window.localStorage.removeItem(key));
    },
};
