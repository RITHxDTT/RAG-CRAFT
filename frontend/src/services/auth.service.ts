import { userRepository, sessionRepository } from '@/repositories/auth.repository';
import { seedDemo } from '@/mocks/seed';
import { currentAccount } from './access';
import { storage } from '@/storage/local-storage';
import { KEYS } from '@/storage/keys';
import { delay, email, password, required, id, now, toast } from '@/utils/demo';
import type { CurrentUser } from '@/types/auth';
import type { UserAccount } from '@/types/demo';
export function publicUser(user: UserAccount): CurrentUser {
    return { id: user.id, email: user.email, role: user.role, full_name: user.full_name, organization_id: user.organization_id,
        organization_name: user.organization_name, display_name: user.display_name, bio: user.bio, avatar: user.avatar, theme: user.theme };
}
export const authService = {
    async register(data: {
        full_name: string;
        email: string;
        password: string;
        confirm_password: string;
        terms?: boolean;
    }) {
        await delay();
        seedDemo();
        const address = email(data.email);
        password(data.password, data.confirm_password);
        if (!data.terms)
            throw new Error('Please accept the demo terms.');
        if (userRepository.all().some(user => user.email === address))
            throw new Error('This email is already registered.');
        const uid = id('user');
        const date = now();
        const user: UserAccount = { id: uid, email: address, full_name: required(data.full_name, 'Full name'), password: data.password,
            role: 'USER', builtIn: false, is_active: true, organization_id: uid, organization_name: 'My Workspace', created_at: date, updated_at: date };
        userRepository.put(user);
        toast('Account created. Sign in to continue.');
        return publicUser(user);
    },
    async update(full_name: string) { return this.updateProfile({ full_name }); },
    async updateProfile(data: Partial<Pick<CurrentUser, 'full_name' | 'email' | 'display_name' | 'bio' | 'avatar' | 'theme'>>, newPassword?: string, confirm?: string, options: { silent?: boolean } = {}) {
        await delay(options.silent ? 0 : undefined);
        const user = currentAccount();
        const address = email(data.email ?? user.email);
        if (user.builtIn && address !== user.email)
            throw new Error('Fixed demo account emails cannot be changed.');
        if (userRepository.all().some(other => other.id !== user.id && other.email === address))
            throw new Error('This email is already registered.');
        if (newPassword)
            password(newPassword, confirm ?? '');
        const updated = { ...user, ...data, email: address, full_name: required(data.full_name ?? user.full_name, 'Full name'),
            password: newPassword || user.password, updated_at: now() };
        userRepository.put(updated);
        sessionRepository.save({ userId: user.id, email: address, role: user.role, loggedIn: true });
        if (!options.silent)
            toast('Profile updated.');
        return publicUser(updated);
    },
    async forgot(address: string) {
        await delay();
        seedDemo();
        const user = userRepository.all().find(user => user.email === email(address));
        if (!user)
            throw new Error('No demo account exists with that email.');
        const token = id('reset');
        storage.write(KEYS.reset, { token, userId: user.id, expires: Date.now() + 600000 });
        return { message: 'Demo verification ready. No email has been sent.', token };
    },
    async reset(token: string, value: string, confirmation: string) {
        await delay();
        password(value, confirmation);
        const reset = storage.read<{
            token: string;
            userId: string;
            expires: number;
        } | null>(KEYS.reset, null);
        if (!reset || reset.token !== token || reset.expires < Date.now())
            throw new Error('This demo reset has expired. Start again.');
        const user = userRepository.all().find(user => user.id === reset.userId);
        if (!user)
            throw new Error('Account no longer exists.');
        userRepository.put({ ...user, password: value, updated_at: now() });
        storage.remove(KEYS.reset);
        if (sessionRepository.get()?.userId === user.id) {
            sessionRepository.remove();
            window.dispatchEvent(new Event('ragcraft:session-expired'));
        }
        toast('Password updated.');
    },
    async me() { return publicUser(currentAccount()); },
    async login(address: string, value: string) {
        await delay();
        seedDemo();
        const user = userRepository.all().find(user => user.email === email(address));
        // The fixed credentials always work, including after a demo password update.
        if (!user || !user.is_active || (user.password !== value && !(user.builtIn && value === '123')))
            throw new Error('Invalid credentials or disabled account.');
        sessionRepository.save({ userId: user.id, email: user.email, role: user.role, loggedIn: true });
        return publicUser(user);
    },
    async logout() { await delay(); sessionRepository.remove(); },
};
