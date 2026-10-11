const COMMON = new Set(['password', 'password1', 'password123', '12345678', '123456789', '1234567890', 'qwerty123', 'qwertyuiop',
    'iloveyou', 'admin123', 'welcome1', 'letmein123', 'abc12345', '11111111', 'passw0rd', 'changeme', 'user1234', 'monkey123']);
/** Returns the first rule the password breaks, or null when it is acceptable. */
export function passwordProblem(value: string, email = ''): string | null {
    if (value.length < 8 || value.length > 64)
        return 'Password must be 8–64 characters.';
    if (email && value.toLowerCase() === email.trim().toLowerCase())
        return 'Password cannot be the same as your email.';
    if (COMMON.has(value.toLowerCase()))
        return 'This password is too common. Choose another.';
    return null;
}
/** 0 (very weak) – 4 (strong), for the strength indicator. */
export function passwordStrength(value: string): 0 | 1 | 2 | 3 | 4 {
    if (!value)
        return 0;
    let score = 0;
    if (value.length >= 8) score++;
    if (value.length >= 12) score++;
    if (/[a-z]/.test(value) && /[A-Z]/.test(value)) score++;
    if (/\d/.test(value) && /[^A-Za-z0-9]/.test(value)) score++;
    if (COMMON.has(value.toLowerCase()))
        score = Math.min(score, 1);
    return Math.min(4, score) as 0 | 1 | 2 | 3 | 4;
}
