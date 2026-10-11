export const KEYS = {
    users: 'ragcraft:v5:users', session: 'ragcraft:v5:session', chatbots: 'ragcraft:v5:chatbots',
    knowledge: 'ragcraft:v5:knowledge', channels: 'ragcraft:v5:channels', conversations: 'ragcraft:v5:conversations',
    analytics: 'ragcraft:v5:analytics', preferences: 'ragcraft:v5:preferences', models: 'ragcraft:v5:models',
    prompts: 'ragcraft:v5:prompts', reset: 'ragcraft:v5:reset', seeded: 'ragcraft:v5:seeded',
    sessions: 'ragcraft:v5:sessions', appeals: 'ragcraft:v5:appeals', audit: 'ragcraft:v5:audit',
    notifications: 'ragcraft:v5:notifications', feedback: 'ragcraft:v5:feedback', tombstones: 'ragcraft:v5:tombstones',
    platform: 'ragcraft:v5:platform', apiToken: 'ragcraft:v5:api-token', verification: 'ragcraft:v5:verification', migrated: 'ragcraft:v5:migrated-from-v2',
} as const;
/** Earlier demo namespace, read once by the v2 -> v5 migration and removed on demo reset. */
export const LEGACY_PREFIX = 'ragcraft:v2:';
export const CURRENT_PREFIX = 'ragcraft:v5:';
