import { userRepository } from '@/repositories/auth.repository';
import { chatbotRepository } from '@/repositories/chatbot.repository';
import { knowledgeRepository } from '@/repositories/knowledge.repository';
import { channelRepository } from '@/repositories/channel.repository';
import { conversationRepository, analyticsRepository } from '@/repositories/conversation.repository';
import { appealRepository, feedbackRepository, notificationRepository, sessionsRepository, tombstoneRepository } from '@/repositories/v5.repository';
import { fileStorage } from '@/storage/indexed-db';
import { demoConfig } from '@/config/demo-config';
/** Removes one chatbot's data (documents, files, channels, conversations, feedback) and leaves tombstones for its public links. */
export async function deleteBotData(botId: string) {
    const docs = knowledgeRepository.all().filter(doc => doc.chatbot_id === botId);
    await Promise.all(docs.map(doc => fileStorage.remove(doc.id)));
    knowledgeRepository.save(knowledgeRepository.all().filter(doc => doc.chatbot_id !== botId));
    channelRepository.all().filter(row => row.chatbot_id === botId).forEach(row => tombstoneRepository.put({ id: row.public_id }));
    channelRepository.save(channelRepository.all().filter(row => row.chatbot_id !== botId));
    conversationRepository.save(conversationRepository.all().filter(row => row.chatbot_id !== botId));
    analyticsRepository.save(analyticsRepository.all().filter(row => row.chatbot_id !== botId));
    feedbackRepository.save(feedbackRepository.all().filter(row => row.chatbot_id !== botId));
    appealRepository.save(appealRepository.all().filter(row => row.chatbot_id !== botId));
    chatbotRepository.remove(botId);
}
/** Permanently removes an account and everything it owns. */
export async function deleteAccountData(userId: string) {
    for (const bot of chatbotRepository.all().filter(row => row.ownerId === userId))
        await deleteBotData(bot.id);
    sessionsRepository.save(sessionsRepository.all().filter(row => row.userId !== userId));
    notificationRepository.save(notificationRepository.all().filter(row => row.userId !== userId));
    userRepository.remove(userId);
}
export const deletionDeadline = (requestedAt: string) => new Date(Date.parse(requestedAt) + demoConfig.deletionGraceDays * 86400000).toISOString();
/** Called opportunistically; accounts past the restore window are erased. */
export async function purgeExpiredAccounts() {
    const expired = userRepository.all().filter(user => user.status === 'PENDING_DELETION' && user.deletion_requested_at && Date.parse(deletionDeadline(user.deletion_requested_at)) <= Date.now());
    for (const user of expired)
        await deleteAccountData(user.id);
}
