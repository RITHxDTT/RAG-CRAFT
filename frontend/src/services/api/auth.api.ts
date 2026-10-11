import { get, post, patch, apiToken } from '../http';
import { ApiError } from '../api';
import { sessionRepository } from '@/repositories/auth.repository';
import { cacheAccount, mapUser, startSession } from './mappers';
import type { localAuthService } from '../auth.service';
import type { CurrentUser } from '@/types/auth';
/* eslint-disable @typescript-eslint/no-explicit-any -- raw server JSON */
type Dto = Record<string, any>;
const unsupported = (feature: string) => async (): Promise<never> => {
    throw new ApiError(501, `${feature} is not available with the database yet.`, 'NOT_IMPLEMENTED');
};
function remember(auth: Dto) {
    apiToken.save(auth.access_token, auth.expires_in);
    const user = mapUser(auth.user);
    startSession(user);
    return user;
}
/** Accounts live in identity-service (craftrag_db.identity). The browser keeps only the token and a cached copy of the profile. */
export const apiAuthService: typeof localAuthService = {
    async register(data) {
        const result = await post<Dto>('/api/auth/register', { full_name: data.full_name, email: data.email, password: data.password, confirm_password: data.confirm_password, terms: data.terms });
        // Registering does not sign in: the screen sends people to the sign-in form, and an unverified account has no token yet.
        return { ...mapUser(result.user), verification_token: result.verification_token as string | undefined };
    },
    async verifyEmail(token) { await post('/api/auth/verify-email', { token }); },
    async resendVerification(address) {
        const result = await post<Dto>('/api/auth/resend-verification', { email: address });
        return { message: result.detail as string, token: result.reset_token as string | undefined };
    },
    async update(full_name) { return this.updateProfile({ full_name }); },
    async updateProfile(data, newPassword, confirm, options = {}) {
        const result = await patch<Dto>('/api/auth/me', { full_name: data.full_name, email: data.email, display_name: data.display_name, bio: data.bio, avatar: data.avatar, theme: data.theme,
            language: data.language, timezone: data.timezone, current_password: options.currentPassword || undefined, password: newPassword || undefined, confirm_password: confirm || undefined });
        const user = mapUser(result.user);
        cacheAccount(user);
        return Object.assign(user, result.verification_token ? { verification_token: result.verification_token as string } : {});
    },
    async changePassword(current, value, confirmation) { return this.updateProfile({}, value, confirmation, { currentPassword: current }); },
    async confirmEmailChange(token) {
        const user = mapUser(await post<Dto>('/api/auth/confirm-email-change', { token }, { auth: false }));
        cacheAccount(user);
        return user;
    },
    async forgot(address) {
        const result = await post<Dto>('/api/auth/forgot-password', { email: address }, { auth: false });
        return { message: result.detail as string, token: result.reset_token as string | undefined };
    },
    async reset(token, value, confirmation) { await post('/api/auth/reset-password', { token, password: value, confirm_password: confirmation }, { auth: false }); },
    async me() {
        const user = mapUser(await get<Dto>('/api/auth/me'));
        cacheAccount(user);
        return user;
    },
    async login(address, value, options = {}) {
        // otp is accepted for forward compatibility; the services do not ask for it yet.
        return remember(await post<Dto>('/api/auth/login', { email: address, password: value, remember: !!options.remember }, { auth: false }));
    },
    async logout() {
        try { await post('/api/auth/logout'); } catch { /* the token may already be gone; signing out locally is what matters */ }
        apiToken.clear();
        sessionRepository.remove();
    },
    async requestDeletion(confirmEmail) {
        const result = await post<Dto>('/api/auth/delete-account', { confirm_email: confirmEmail });
        apiToken.clear();
        sessionRepository.remove();
        return { restorable_until: result.restorable_until as string };
    },
    async restoreAccount() {
        const user: CurrentUser = mapUser(await post<Dto>('/api/auth/restore'));
        cacheAccount(user);
        return user;
    },
    loginWithPasskey: unsupported('Passkey sign-in'),
    loginWithSocial: unsupported('Google and GitHub sign-in'),
    linkAccount: unsupported('Linking accounts'),
    unlinkAccount: unsupported('Linking accounts'),
    sessions: unsupported('The session list'),
    revokeSession: unsupported('Session management'),
    revokeOtherSessions: unsupported('Session management'),
    beginTotpSetup: unsupported('Authenticator app setup'),
    enableTotp: unsupported('Authenticator app setup'),
    disableTotp: unsupported('Authenticator app setup'),
    addPasskey: unsupported('Passkeys'),
    removePasskey: unsupported('Passkeys'),
};
