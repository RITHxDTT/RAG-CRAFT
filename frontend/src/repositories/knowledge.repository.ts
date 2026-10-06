import { repository } from './repository';
import { KEYS } from '@/storage/keys';
import type { DocumentDetail } from '@/types/document';
export const knowledgeRepository = repository<DocumentDetail>(KEYS.knowledge);
