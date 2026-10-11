import { storage } from '@/storage/local-storage';
import { KEYS } from '@/storage/keys';
import { userRepository } from '@/repositories/auth.repository';
import { chatbotRepository } from '@/repositories/chatbot.repository';
import { knowledgeRepository } from '@/repositories/knowledge.repository';
import { channelRepository } from '@/repositories/channel.repository';
import { defaultSettings, newAccount } from './defaults';
import { migrateFromV2 } from '@/storage/migrate-v2';
import { apiMode } from '@/config/data-source';
export { defaultSettings };
export function seedDemo() {
    // With the database as the source of truth the demo accounts and chatbot come from the server (SEED_DEMO_ACCOUNTS), not from the browser.
    if (apiMode)
        return;
    migrateFromV2();
    const date = new Date().toISOString();
    for (const role of ['ADMIN', 'USER'] as const) {
        const key = role.toLowerCase();
        if (!userRepository.all().some(user => user.id === `demo_${key}`))
            userRepository.put(newAccount({
                id: `demo_${key}`, full_name: role === 'ADMIN' ? 'Demo Administrator' : 'Demo User', email: `${key}@gmail.com`,
                password: '123', role, builtIn: true, organization_id: `workspace_${key}`,
                organization_name: role === 'ADMIN' ? 'Demo Platform' : 'My Workspace', created_at: date, updated_at: date,
            }));
    }
    if (storage.read(KEYS.seeded, false))
        return;
    if (!chatbotRepository.all().length) {
        chatbotRepository.put({ id: 'demo_company', ownerId: 'demo_user', organization_id: 'workspace_user', name: 'Company Assistant',
            description: 'Explore company policies, product information, and frequently asked questions.', status: 'ACTIVE',
            settings: defaultSettings, starter_questions: ['What is the annual leave policy?', 'What is the refund policy?', 'How do I get started?'],
            created_at: date, updated_at: date, document_count: 3, ready_count: 3, failed_count: 0 });
        ['Employee Handbook.pdf', 'Product Guide.docx', 'FAQ.md'].forEach((name, index) => knowledgeRepository.put({
            id: `demo_doc_${index}`, ownerId: 'demo_user', chatbot_id: 'demo_company', name, file_type: name.split('.').pop()!.toUpperCase(),
            mime_type: 'application/octet-stream', size_bytes: 120000 + index * 50000, status: 'READY', chunkCount: 24 + index * 8,
            error_message: null, created_at: date, updated_at: date, jobs: [{ id: `seed_job_${index}`, status: 'READY', error_message: null, created_at: date, updated_at: date }],
        }));
        for (const channel of ['PUBLIC_LINK', 'WEB_WIDGET', 'TELEGRAM'] as const)
            channelRepository.put({
                id: `demo_${channel}`, ownerId: 'demo_user', chatbot_id: 'demo_company', channel, public_id: crypto.randomUUID(),
                enabled: channel === 'PUBLIC_LINK', status: channel === 'PUBLIC_LINK' ? 'LIVE' : 'OFF', url: null, embed_code: null,
                telegram_username: null, token_mask: null, created_at: date, updated_at: date,
                settings: { title: 'Company Assistant', welcome: defaultSettings.welcome_message!, color: '#0d9488', position: 'BOTTOM_RIGHT', icon: '💬', domains: [], starters: ['What is the annual leave policy?'], password: '', expires: '' },
            });
    }
    storage.write(KEYS.seeded, true);
}
