// Profile operations share the authentication account repository.
import { authService } from './auth.service';
import type { CurrentUser } from '@/types/auth';
export const userService = {
    updateProfile: authService.updateProfile.bind(authService),
    /** Quick theme switch from the top bar; no toast so it feels instant. */
    setTheme(theme: NonNullable<CurrentUser['theme']>) { return authService.updateProfile({ theme }, undefined, undefined, { silent: true }); },
};
