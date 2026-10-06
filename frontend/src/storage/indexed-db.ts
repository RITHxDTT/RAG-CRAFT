const DATABASE = 'ragcraft:v2:files';
function open(): Promise<IDBDatabase> {
    return new Promise((resolve, reject) => {
        const request = indexedDB.open(DATABASE, 1);
        request.onupgradeneeded = () => request.result.createObjectStore('files');
        request.onsuccess = () => resolve(request.result);
        request.onerror = () => reject(new Error('File storage is unavailable.'));
    });
}
async function run<T>(mode: IDBTransactionMode, action: (store: IDBObjectStore) => IDBRequest<T>): Promise<T> {
    const db = await open();
    return new Promise((resolve, reject) => {
        const tx = db.transaction('files', mode);
        const request = action(tx.objectStore('files'));
        tx.oncomplete = () => { db.close(); resolve(request.result); };
        tx.onerror = () => { db.close(); reject(new Error('Unable to store the selected file.')); };
        tx.onabort = tx.onerror;
    });
}
export const fileStorage = {
    put: (id: string, file: Blob) => run('readwrite', store => store.put(file, id)),
    get: (id: string) => run<Blob | undefined>('readonly', store => store.get(id)),
    remove: (id: string) => run('readwrite', store => store.delete(id)),
    clear: () => run('readwrite', store => store.clear()),
};
