// Demo-phase switches. Production behaviour (Keycloak, e-mail, push) replaces these when a backend exists.
export const demoConfig = {
    /** When false, new accounts start UNVERIFIED and must confirm a (simulated) e-mail link before signing in. */
    autoVerifyEmail: true,
    /** Failed sign-ins before an account is temporarily locked. */
    maxFailedAttempts: 5,
    /** Lock time grows by one minute per lock, up to this many minutes. */
    maxLockMinutes: 15,
    /** Days an account stays restorable after deletion is requested. */
    deletionGraceDays: 7,
    /** Session lifetime in hours; "remember me" uses the long value. */
    sessionHours: 24,
    rememberSessionHours: 24 * 30,
    /** Expiry of verification and reset links, in minutes. */
    linkMinutes: 30,
};
