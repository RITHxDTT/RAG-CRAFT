import { storage } from '@/storage/local-storage';
export function repository<T extends {
    id: string;
}>(key: string) {
    return {
        all: () => storage.read<T[]>(key, []),
        save(rows: T[]) { storage.write(key, rows); },
        /** Replaces the rows without announcing the change (cache mirroring). */
        saveQuiet(rows: T[]) { storage.writeQuiet(key, rows); },
        put(row: T) { const rows = this.all(); this.save([...rows.filter(item => item.id !== row.id), row]); return row; },
        remove(id: string) { this.save(this.all().filter(row => row.id !== id)); },
    };
}
