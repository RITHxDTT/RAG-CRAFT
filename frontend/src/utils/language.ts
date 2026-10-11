import type { BotLanguage } from '@/types/chatbot';
export type ReplyLanguage = Exclude<BotLanguage, 'AUTO'>;
/** Majority script wins for mixed text; Latin text (and anything undetected) is English. */
export function detectLanguage(text: string): ReplyLanguage {
    let km = 0, ko = 0, latin = 0;
    for (const char of text) {
        const code = char.codePointAt(0)!;
        if (code >= 0x1780 && code <= 0x17ff) km++;
        else if ((code >= 0xac00 && code <= 0xd7af) || (code >= 0x1100 && code <= 0x11ff) || (code >= 0x3130 && code <= 0x318f)) ko++;
        else if (/[A-Za-z]/.test(char)) latin++;
    }
    if (km > ko && km > latin) return 'KM';
    if (ko > km && ko > latin) return 'KO';
    return 'EN';
}
export const NOT_FOUND_MESSAGE: Record<ReplyLanguage, string> = {
    EN: "Sorry, I couldn't find that in my knowledge base.",
    KM: 'សូមអភ័យទោស ខ្ញុំរកមិនឃើញព័ត៌មាននោះនៅក្នុងមូលដ្ឋានចំណេះដឹងរបស់ខ្ញុំទេ។',
    KO: '죄송합니다. 지식 베이스에서 해당 내용을 찾을 수 없습니다.',
};
export function replyLanguage(setting: BotLanguage | undefined, question: string): ReplyLanguage {
    return setting && setting !== 'AUTO' ? setting : detectLanguage(question);
}
