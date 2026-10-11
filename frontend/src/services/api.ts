// Retained error contract for UI compatibility; demo services perform no backend requests.
export class ApiError extends Error {
    /** `code` lets the UI branch on a condition, for example MFA_REQUIRED or LINK_CONFIRM_REQUIRED. */
    constructor(public status: number, message: string, public code?: string) { super(message); }
}
export function errorMessage(error: unknown): string {
    return error instanceof Error ? error.message : 'Something went wrong. Please try again.';
}
