import { repository } from './repository';
import { KEYS } from '@/storage/keys';
import { storage } from '@/storage/local-storage';
import type { UserAccount, Session } from '@/types/demo';
export const userRepository = repository<UserAccount>(KEYS.users);
export const sessionRepository = {
    get: () => storage.read<Session | null>(KEYS.session, null),
    save: (session: Session) => storage.write(KEYS.session, session),
    remove: () => storage.remove(KEYS.session),
};
