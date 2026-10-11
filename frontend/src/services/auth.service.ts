import { apiMode } from '@/config/data-source';
import { apiAuthService } from './api/auth.api';
import { userRepository, sessionRepository } from '@/repositories/auth.repository';
import { sessionsRepository, verificationRepository } from '@/repositories/v5.repository';
import { seedDemo } from '@/mocks/seed';
import { newAccount } from '@/mocks/defaults';
import { currentAccount } from './access';
import { deletionDeadline, purgeExpiredAccounts } from './account-data';
import { notify } from './notification.service';
import { ApiError } from './api';
import { storage } from '@/storage/local-storage';
import { KEYS } from '@/storage/keys';
import { demoConfig } from '@/config/demo-config';
import { delay, email, password, required, id, now, toast } from '@/utils/demo';
import { otpauthUri, randomSecret, verifyTotp } from '@/utils/totp';
import type { CurrentUser, SocialProvider } from '@/types/auth';
import type { UserAccount } from '@/types/demo';
export function publicUser(user: UserAccount): CurrentUser {
    return { id: user.id, email: user.email, role: user.role, full_name: user.full_name, organization_id: user.organization_id,
        organization_name: user.organization_name, display_name: user.display_name, bio: user.bio, avatar: user.avatar, theme: user.theme,
        language: user.language, timezone: user.timezone, pending_email: user.pending_email, status: user.status, signup_method: user.signup_method,
        totp_enabled: !!user.totp_secret, mfa_required: user.role === 'ADMIN' && !user.totp_secret, passkey_count: user.passkeys.length,
        linked_accounts: user.linked_accounts, deletion_requested_at: user.deletion_requested_at };
}
const GENERIC_LOGIN_ERROR = 'Invalid email or password.';
function deviceLabel() {
    const agent = typeof navigator !== 'undefined' ? navigator.userAgent : '';
    const browser = /Edg\//.test(agent) ? 'Edge' : /Chrome\//.test(agent) ? 'Chrome' : /Firefox\//.test(agent) ? 'Firefox' : /Safari\//.test(agent) ? 'Safari' : 'Browser';
    const system = /Windows/.test(agent) ? 'Windows' : /Mac OS X/.test(agent) ? 'macOS' : /Android/.test(agent) ? 'Android' : /iPhone|iPad/.test(agent) ? 'iOS' : /Linux/.test(agent) ? 'Linux' : 'Unknown OS';
    return `${browser} on ${system}`;
}
function startSession(user: UserAccount, remember: boolean) {
    const date = now(), sessionId = id('sess');
    sessionsRepository.put({ id: sessionId, userId: user.id, device: deviceLabel(), location: 'This device (demo)', created_at: date, last_active_at: date });
    const hours = remember ? demoConfig.rememberSessionHours : demoConfig.sessionHours;
    sessionRepository.save({ userId: user.id, email: user.email, role: user.role, loggedIn: true, sessionId, expires_at: new Date(Date.now() + hours * 3600000).toISOString() });
    const updated = { ...user, failed_attempts: 0, lock_count: 0, locked_until: null, last_login_at: date, updated_at: date };
    userRepository.put(updated);
    void purgeExpiredAccounts();
    return publicUser(updated);
}
function assertCanSignIn(user: UserAccount) {
    if (user.status === 'SUSPENDED')
        throw new ApiError(403, 'This account is suspended. Reply to the suspension email to appeal.', 'SUSPENDED');
    if (user.status === 'UNVERIFIED')
        throw new ApiError(403, 'Verify your email before signing in.', 'UNVERIFIED');
}
function issueLink(user: UserAccount, kind: 'EMAIL' | 'CHANGE_EMAIL', address: string) {
    verificationRepository.save(verificationRepository.all().filter(row => !(row.userId === user.id && row.kind === kind)));
    const token = id('verify');
    verificationRepository.put({ id: token, userId: user.id, kind, email: address, expires: Date.now() + demoConfig.linkMinutes * 60000 });
    notify(user.id, 'EMAIL_VERIFICATION', 'Verify your email', `Demo verification code: ${token} (expires in ${demoConfig.linkMinutes} minutes). No real email is sent.`, ['EMAIL']);
    return token;
}
function takeLink(token: string, kind: 'EMAIL' | 'CHANGE_EMAIL') {
    const row = verificationRepository.all().find(item => item.id === token && item.kind === kind);
    if (!row)
        throw new ApiError(404, 'This verification link is not valid.', 'LINK_INVALID');
    verificationRepository.remove(token);
    if (row.expires < Date.now())
        throw new ApiError(410, 'This link has expired. Request a new one.', 'LINK_EXPIRED');
    const user = userRepository.all().find(item => item.id === row.userId);
    if (!user)
        throw new ApiError(404, 'Account no longer exists.');
    return { row, user };
}
export const localAuthService = {
    async register(data: { full_name: string; email: string; password: string; confirm_password: string; terms?: boolean }) {
        await delay();
        seedDemo();
        const address = email(data.email);
        password(data.password, data.confirm_password, address);
        if (!data.terms)
            throw new Error('Please accept the Terms of Service and Privacy Policy.');
        if (userRepository.all().some(user => user.email === address))
            throw new Error('This email is already registered.');
        const uid = id('user');
        const verified = demoConfig.autoVerifyEmail;
        const user = newAccount({ id: uid, email: address, full_name: required(data.full_name, 'Full name'), password: data.password, role: 'USER', organization_id: uid,
            organization_name: 'My Workspace', status: verified ? 'ACTIVE' : 'UNVERIFIED', is_active: verified, email_verified: verified });
        userRepository.put(user);
        const verification_token = verified ? undefined : issueLink(user, 'EMAIL', address);
        toast(verified ? 'Account created. Sign in to continue.' : 'Account created. Verify your email to sign in.');
        return { ...publicUser(user), verification_token };
    },
    async verifyEmail(token: string) {
        const { row, user } = takeLink(token, 'EMAIL');
        userRepository.put({ ...user, status: 'ACTIVE', is_active: true, email_verified: true, email: row.email, updated_at: now() });
        toast('Email verified. You can sign in.');
    },
    /** Always answers the same way so the form cannot be used to discover accounts. */
    async resendVerification(address: string) {
        await delay();
        const user = userRepository.all().find(item => item.email === email(address));
        const token = user?.status === 'UNVERIFIED' ? issueLink(user, 'EMAIL', user.email) : undefined;
        return { message: "If an unverified account exists, we've sent a new link.", token };
    },
    async update(full_name: string) { return this.updateProfile({ full_name }); },
    async updateProfile(data: Partial<Pick<CurrentUser, 'full_name' | 'email' | 'display_name' | 'bio' | 'avatar' | 'theme' | 'language' | 'timezone'>>, newPassword?: string, confirm?: string, options: { silent?: boolean; currentPassword?: string } = {}) {
        await delay(options.silent ? 0 : undefined);
        const user = currentAccount();
        const address = email(data.email ?? user.email);
        if (user.builtIn && address !== user.email)
            throw new Error('Fixed demo account emails cannot be changed.');
        if (address !== user.email && userRepository.all().some(other => other.id !== user.id && (other.email === address || other.pending_email === address)))
            throw new Error('This email is already registered.');
        if (newPassword) {
            if (options.currentPassword !== user.password && !(user.builtIn && options.currentPassword === '123'))
                throw new Error('Current password is incorrect.');
            password(newPassword, confirm ?? '', user.email);
        }
        // Changing the e-mail address never takes effect until the new address is verified.
        const { email: _ignored, ...rest } = data;
        void _ignored;
        const pending = address !== user.email;
        const updated: UserAccount = { ...user, ...rest, email: user.email, full_name: required(data.full_name ?? user.full_name, 'Full name'),
            password: newPassword || user.password, pending_email: pending ? address : user.pending_email, updated_at: now() };
        userRepository.put(updated);
        const token = pending ? issueLink(updated, 'CHANGE_EMAIL', address) : undefined;
        if (!options.silent)
            toast(pending ? `Profile updated. Verify ${address} to finish changing your email.` : 'Profile updated.');
        return Object.assign(publicUser(updated), token ? { verification_token: token } : {});
    },
    async changePassword(current: string, value: string, confirmation: string) {
        const user = currentAccount();
        return this.updateProfile({ full_name: user.full_name }, value, confirmation, { currentPassword: current });
    },
    async confirmEmailChange(token: string) {
        const { row, user } = takeLink(token, 'CHANGE_EMAIL');
        if (userRepository.all().some(other => other.id !== user.id && other.email === row.email))
            throw new Error('This email is already registered.');
        const updated = { ...user, email: row.email, pending_email: null, updated_at: now() };
        userRepository.put(updated);
        const session = sessionRepository.get();
        if (session?.userId === user.id)
            sessionRepository.save({ ...session, email: row.email });
        toast('Email address updated.');
        return publicUser(updated);
    },
    async forgot(address: string) {
        await delay();
        seedDemo();
        const user = userRepository.all().find(user => user.email === email(address));
        // Same message whether or not the account exists; the code is returned only so the demo can open the reset page.
        const message = "If an account exists, we've sent a link. No real email is sent in this demo.";
        if (!user)
            return { message, token: undefined as string | undefined };
        const token = id('reset');
        storage.write(KEYS.reset, { token, userId: user.id, expires: Date.now() + demoConfig.linkMinutes * 60000 });
        notify(user.id, 'PASSWORD_RESET', 'Reset your password', `Demo reset code: ${token}`, ['EMAIL']);
        return { message, token: token as string | undefined };
    },
    async reset(token: string, value: string, confirmation: string) {
        await delay();
        const reset = storage.read<{ token: string; userId: string; expires: number } | null>(KEYS.reset, null);
        if (!reset || reset.token !== token || reset.expires < Date.now())
            throw new Error('This reset link has expired. Start again.');
        const user = userRepository.all().find(user => user.id === reset.userId);
        if (!user)
            throw new Error('Account no longer exists.');
        password(value, confirmation, user.email);
        userRepository.put({ ...user, password: value, failed_attempts: 0, locked_until: null, updated_at: now() });
        storage.remove(KEYS.reset);
        // Resetting a password signs the account out everywhere.
        sessionsRepository.save(sessionsRepository.all().filter(row => row.userId !== user.id));
        if (sessionRepository.get()?.userId === user.id) {
            sessionRepository.remove();
            window.dispatchEvent(new Event('ragcraft:session-expired'));
        }
        toast('Password updated.');
    },
    async me() { return publicUser(currentAccount({ allowPendingDeletion: true })); },
    /** Locks after repeated failures (1, 2, 3 … up to 15 minutes) and asks for an authenticator code when one is set up. */
    async login(address: string, value: string, options: { remember?: boolean; otp?: string } = {}) {
        await delay();
        seedDemo();
        const user = userRepository.all().find(user => user.email === email(address));
        if (!user)
            throw new ApiError(401, GENERIC_LOGIN_ERROR, 'INVALID_CREDENTIALS');
        if (user.locked_until && Date.parse(user.locked_until) > Date.now()) {
            const minutes = Math.ceil((Date.parse(user.locked_until) - Date.now()) / 60000);
            throw new ApiError(429, `Too many failed attempts. Try again in ${minutes} minute${minutes === 1 ? '' : 's'}.`, 'LOCKED');
        }
        // The fixed demo credentials always work, including after a demo password update.
        if (user.password !== value && !(user.builtIn && value === '123')) {
            const attempts = user.failed_attempts + 1;
            if (attempts >= demoConfig.maxFailedAttempts) {
                const lockCount = user.lock_count + 1, minutes = Math.min(demoConfig.maxLockMinutes, lockCount);
                userRepository.put({ ...user, failed_attempts: 0, lock_count: lockCount, locked_until: new Date(Date.now() + minutes * 60000).toISOString() });
                throw new ApiError(429, `Too many failed attempts. Try again in ${minutes} minute${minutes === 1 ? '' : 's'}.`, 'LOCKED');
            }
            userRepository.put({ ...user, failed_attempts: attempts });
            throw new ApiError(401, GENERIC_LOGIN_ERROR, 'INVALID_CREDENTIALS');
        }
        assertCanSignIn(user);
        if (user.totp_secret) {
            if (!options.otp)
                throw new ApiError(401, 'Enter the 6-digit code from your authenticator app.', 'MFA_REQUIRED');
            if (!(await verifyTotp(user.totp_secret, options.otp)))
                throw new ApiError(401, 'That code is not valid. Try again.', 'MFA_INVALID');
        }
        return startSession(user, !!options.remember);
    },
    /** Simulated: passkeys are recorded, not verified with WebAuthn. */
    async loginWithPasskey(address: string, remember = false) {
        await delay();
        seedDemo();
        const user = userRepository.all().find(user => user.email === email(address));
        if (!user || !user.passkeys.length)
            throw new ApiError(401, 'No passkey is registered for this account.', 'NO_PASSKEY');
        assertCanSignIn(user);
        return startSession(user, remember);
    },
    /** Simulated Google/GitHub sign-in for USER accounts. A matching password account must confirm its password before linking. */
    async loginWithSocial(provider: SocialProvider, address: string, fullName: string, confirmPassword?: string, remember = false) {
        await delay();
        seedDemo();
        const mail = email(address);
        let user = userRepository.all().find(item => item.email === mail);
        if (user?.role === 'ADMIN')
            throw new ApiError(403, 'Administrators sign in with email and password.', 'ADMIN_SOCIAL');
        if (!user) {
            const uid = id('user');
            user = newAccount({ id: uid, email: mail, full_name: required(fullName, 'Name'), password: id('social'), role: 'USER', organization_id: uid, organization_name: 'My Workspace',
                signup_method: provider, linked_accounts: [provider] });
            userRepository.put(user);
        }
        else if (!user.linked_accounts.includes(provider)) {
            if (confirmPassword === undefined || confirmPassword !== user.password)
                throw new ApiError(409, 'An account with this email exists. Confirm your password to link it.', 'LINK_CONFIRM_REQUIRED');
            user = { ...user, linked_accounts: [...user.linked_accounts, provider] };
            userRepository.put(user);
        }
        assertCanSignIn(user);
        return startSession(user, remember);
    },
    async linkAccount(provider: SocialProvider) {
        const user = currentAccount();
        if (user.role === 'ADMIN') throw new ApiError(403, 'Administrators cannot link social accounts.');
        if (user.linked_accounts.includes(provider)) throw new ApiError(409, `${provider} is already connected.`);
        userRepository.put({ ...user, linked_accounts: [...user.linked_accounts, provider], updated_at: now() });
        return publicUser(currentAccount());
    },
    async unlinkAccount(provider: SocialProvider) {
        const user = currentAccount();
        if (!user.linked_accounts.includes(provider)) throw new ApiError(404, `${provider} is not connected.`);
        userRepository.put({ ...user, linked_accounts: user.linked_accounts.filter(item => item !== provider), updated_at: now() });
        return publicUser(currentAccount());
    },
    async logout() {
        await delay();
        const session = sessionRepository.get();
        if (session?.sessionId)
            sessionsRepository.remove(session.sessionId);
        sessionRepository.remove();
    },
    /** Active sessions of the signed-in user; this browser's session is flagged `current`. */
    async sessions() {
        const user = currentAccount();
        const current = sessionRepository.get()?.sessionId;
        return sessionsRepository.all().filter(row => row.userId === user.id).sort((a, b) => b.last_active_at.localeCompare(a.last_active_at)).map(row => ({ ...row, current: row.id === current }));
    },
    async revokeSession(sessionId: string) {
        const user = currentAccount();
        if (!sessionsRepository.all().some(row => row.id === sessionId && row.userId === user.id))
            throw new ApiError(404, 'Session not found.');
        sessionsRepository.remove(sessionId);
        if (sessionRepository.get()?.sessionId === sessionId)
            window.dispatchEvent(new Event('ragcraft:session-expired'));
    },
    async revokeOtherSessions() {
        const user = currentAccount();
        const current = sessionRepository.get()?.sessionId;
        sessionsRepository.save(sessionsRepository.all().filter(row => row.userId !== user.id || row.id === current));
    },
    /** Step 1 of authenticator setup: returns a secret and an otpauth:// link for the QR code. */
    async beginTotpSetup() {
        const user = currentAccount();
        if (user.totp_secret) throw new ApiError(409, 'An authenticator app is already set up.');
        const secret = randomSecret();
        return { secret, otpauth_uri: otpauthUri(secret, user.email) };
    },
    /** Step 2: the first valid code proves the app is configured. */
    async enableTotp(secret: string, code: string) {
        const user = currentAccount();
        if (!(await verifyTotp(secret, code)))
            throw new ApiError(400, 'That code is not valid. Check the app and try again.');
        userRepository.put({ ...user, totp_secret: secret, updated_at: now() });
        return publicUser(currentAccount());
    },
    async disableTotp(currentPassword: string) {
        const user = currentAccount();
        if (user.role === 'ADMIN') throw new ApiError(403, 'Administrators must keep an authenticator app enabled.');
        if (currentPassword !== user.password) throw new ApiError(401, 'Current password is incorrect.');
        userRepository.put({ ...user, totp_secret: null, updated_at: now() });
        return publicUser(currentAccount());
    },
    /** Simulated passkey enrolment (no WebAuthn ceremony). */
    async addPasskey(name: string) {
        const user = currentAccount();
        userRepository.put({ ...user, passkeys: [...user.passkeys, { id: id('passkey'), name: required(name, 'Passkey name'), created_at: now() }], updated_at: now() });
        return publicUser(currentAccount());
    },
    async removePasskey(passkeyId: string) {
        const user = currentAccount();
        userRepository.put({ ...user, passkeys: user.passkeys.filter(item => item.id !== passkeyId), updated_at: now() });
        return publicUser(currentAccount());
    },
    /** Starts the 7-day restore window; chatbots go offline and every session is signed out. */
    async requestDeletion(confirmEmail: string) {
        const user = currentAccount();
        if (user.builtIn) throw new ApiError(403, 'Fixed demo accounts cannot be deleted.');
        if (confirmEmail.trim().toLowerCase() !== user.email) throw new ApiError(400, 'Retype your email address to confirm.');
        const date = now();
        userRepository.put({ ...user, status: 'PENDING_DELETION', is_active: false, deletion_requested_at: date, updated_at: date });
        sessionsRepository.save(sessionsRepository.all().filter(row => row.userId !== user.id));
        sessionRepository.remove();
        return { restorable_until: deletionDeadline(date) };
    },
    async restoreAccount() {
        const user = currentAccount({ allowPendingDeletion: true });
        if (user.status !== 'PENDING_DELETION') throw new ApiError(409, 'This account is not scheduled for deletion.');
        userRepository.put({ ...user, status: 'ACTIVE', is_active: true, deletion_requested_at: null, updated_at: now() });
        toast('Account restored.');
        return publicUser(currentAccount());
    },
};
/** Browser-only demo by default; the Spring services and craftrag_db when NEXT_PUBLIC_DATA_SOURCE=api. */
export const authService: typeof localAuthService = apiMode ? apiAuthService : localAuthService;
