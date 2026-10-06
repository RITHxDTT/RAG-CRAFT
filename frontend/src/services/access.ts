import { seedDemo } from '@/mocks/seed';
import { userRepository, sessionRepository } from '@/repositories/auth.repository';
import { chatbotRepository } from '@/repositories/chatbot.repository';
import { ApiError } from './api';
import type { UserAccount } from '@/types/demo';
export function currentAccount(): UserAccount {
    seedDemo();
    const session = sessionRepository.get();
    const user = userRepository.all().find(user => user.id === session?.userId && user.is_active);
    if (!session?.loggedIn || !user) {
        sessionRepository.remove();
        window.dispatchEvent(new Event('ragcraft:session-expired'));
        throw new ApiError(401, 'Please sign in to continue.');
    }
    return user;
}
export function requireAdmin() { const user = currentAccount(); if (user.role !== 'ADMIN')
    throw new ApiError(403, 'Administrator access required.'); return user; }
export function accessibleBots() { const user = currentAccount(); return chatbotRepository.all().filter(bot => user.role === 'ADMIN' || bot.ownerId === user.id); }
export function requireBot(id: string) { const bot = accessibleBots().find(bot => bot.id === id); if (!bot)
    throw new ApiError(404, 'Chatbot not found.'); return bot; }
