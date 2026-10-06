// Retained error contract for UI compatibility; demo services perform no backend requests.
export class ApiError extends Error {
    constructor(public status: number, message: string) { super(message); }
}
export function errorMessage(error: unknown): string {
    return error instanceof Error ? error.message : 'Something went wrong. Please try again.';
}
