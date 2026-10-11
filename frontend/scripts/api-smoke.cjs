/* eslint-disable @typescript-eslint/no-require-imports -- CommonJS harness installs a TypeScript loader for isolated service tests. */
// Runs the real frontend services in API mode against a running gateway (craftrag_db behind the Spring services).
//   services/scripts/run-craftrag.sh up
//   node scripts/api-smoke.cjs [http://localhost:8090]
process.env.NEXT_PUBLIC_DATA_SOURCE = 'api';
process.env.NEXT_PUBLIC_API_URL = process.argv[2] || 'http://localhost:8090';
const assert = require('node:assert/strict');
require('./test-harness.cjs');
const load = name => require(`../src/${name}.ts`);
const { authService } = load('services/auth.service');
const { chatbotService } = load('services/chatbot.service');
const { catalogService } = load('services/catalog.service');
const { knowledgeService } = load('services/knowledge.service');
const { channelService } = load('services/channel.service');
const { adminService } = load('services/admin.service');
const { appealService } = load('services/appeal.service');
const { auditService } = load('services/audit.service');
const { platformService } = load('services/platform.service');
const { quotaService } = load('services/quota.service');
const { playgroundService } = load('services/playground.service');
const { chatbotRepository } = load('repositories/chatbot.repository');
const { knowledgeRepository } = load('repositories/knowledge.repository');
const { channelRepository } = load('repositories/channel.repository');
const { apiMode } = load('config/data-source');
const sleep = ms => new Promise(resolve => setTimeout(resolve, ms));
const rejects = (promise, pattern, code) => assert.rejects(promise, error => { assert.match(error.message, pattern); if (code) assert.equal(error.code, code); return true; });
let passed = 0;
const ok = label => { passed++; console.log(`  PASS  ${label}`); };
(async () => {
  assert.equal(apiMode, true, 'API mode must be on for this test');
  const run = Math.random().toString(16).slice(2, 8);
  const email = `front-${run}@example.com`, password = `front-pass-${run}`, botName = `Front Bot ${run}`;
  let userId = null, botId = null;
  try {
    console.log('Accounts');
    await rejects(authService.register({ full_name: 'F', email, password: 'short', confirm_password: 'short', terms: true }), /8–64/);
    const registered = await authService.register({ full_name: 'Front Tester', email, password, confirm_password: password, terms: true });
    userId = registered.id; assert.equal(registered.status, 'ACTIVE'); ok('register returns the mapped user without signing in');
    await assert.rejects(authService.me(), /sign in|Please/i); ok('no session until sign-in');
    await rejects(authService.login(email, 'wrong-pass-1'), /Invalid email or password/, 'INVALID_CREDENTIALS');
    const user = await authService.login(email, password);
    assert.equal(user.email, email); assert.equal(user.role, 'USER'); assert.equal(user.language, 'en'); ok('sign in maps the server profile');
    assert.equal((await authService.me()).id, userId); ok('me() with the stored token');
    const themed = await authService.updateProfile({ theme: 'dark', display_name: 'Tester' }); assert.equal(themed.theme, 'dark'); assert.equal(themed.display_name, 'Tester'); ok('profile update');
    await rejects(authService.changePassword('nope', 'brand-new-pass-3', 'brand-new-pass-3'), /Current password is incorrect/); ok('password change needs the current password');
    await rejects(authService.beginTotpSetup(), /not available with the database yet/); ok('unsupported features say so clearly');

    console.log('Catalog, limits, quota');
    const models = await catalogService.models(); const embeddings = await catalogService.embeddingModels();
    assert.ok(models.length >= 1 && models.every(m => m.kind === 'LLM') && embeddings.length >= 1 && embeddings.every(m => m.kind === 'EMBEDDING')); ok('chat and embedding models are separate');
    assert.deepEqual(Object.keys((await platformService.get()).limits).sort(), ['chunk_overlap', 'chunk_size', 'max_context_tokens', 'max_tokens', 'temperature', 'top_k']); ok('admin limits load');
    assert.deepEqual((await quotaService.usage()).bots, { used: 0, limit: 5 }); ok('quota usage');

    console.log('Chatbot lifecycle');
    await rejects(chatbotService.create({ name: 'T', description: '', settings: { temperature: 1.5 } }), /Temperature/); ok('admin limit enforced');
    const bot = await chatbotService.create({ name: botName, description: 'api smoke', starter_questions: ['What is the refund policy?'],
      settings: { model_id: models[0].id, tone: 'FRIENDLY', top_k: 4, embedding_model: embeddings[0].model_identifier } });
    botId = bot.id;
    assert.equal(bot.status, 'DRAFT'); assert.equal(bot.ownerId, userId); assert.equal(bot.settings.top_k, 4); assert.equal(bot.settings.temperature, 0.4); assert.equal(bot.settings.model_name, models[0].model_identifier); ok('create maps every setting');
    assert.equal(chatbotRepository.all().find(b => b.id === botId)?.name, botName); ok('bot mirrored into the local cache');
    await rejects(chatbotService.update(botId, { name: botName, description: '', settings: { embedding_model: 'other' } }), /cannot be changed/); ok('embedding model is locked');
    assert.equal((await chatbotService.update(botId, { name: botName, description: 'edited' })).description, 'edited'); ok('update');
    await rejects(chatbotService.publish(botId), /Add at least one document/, 'BAD_TRANSITION'); ok('a draft cannot be published');
    assert.equal((await chatbotService.list({ search: run })).length, 1); ok('list filters by search');

    console.log('Knowledge and channels');
    const file = text => new File([text], `policy-${run}.txt`, { type: 'text/plain' });
    const doc = await knowledgeService.upload(botId, file('Refunds are accepted within 30 days with proof of purchase.\n\n'.repeat(15)));
    assert.equal(doc.status, 'QUEUED'); assert.equal(doc.chatbot_id, botId); ok('upload');
    await rejects(knowledgeService.upload(botId, new File(['different text'], `POLICY-${run}.txt`, { type: 'text/plain' })), /already exists/, 'DUPLICATE_NAME'); ok('same name asks for Replace or Skip');
    assert.equal((await knowledgeService.upload(botId, new File(['x'], `policy-${run}.txt`), 'SKIP')).id, doc.id); ok('Skip returns the existing document');
    let docs = [];
    for (let i = 0; i < 40; i++) { docs = await knowledgeService.list(botId); if (docs[0]?.status === 'READY') break; await sleep(1500); }
    assert.equal(docs[0].status, 'READY'); assert.ok(docs[0].chunkCount > 0); ok('simulated ingestion reaches READY on the server');
    assert.equal(knowledgeRepository.all().filter(d => d.chatbot_id === botId && d.status === 'READY').length, 1); ok('documents mirrored into the local cache');
    assert.equal((await chatbotService.get(botId)).status, 'PENDING'); ok('a READY document moves the chatbot to PENDING');
    await rejects(chatbotService.publish(botId), /one channel/, 'CHECKLIST'); ok('publish needs a channel');
    const channel = await channelService.create(botId, 'PUBLIC_LINK');
    assert.equal(channel.channel, 'PUBLIC_LINK'); assert.ok(channel.public_id); assert.ok(channel.url?.includes(channel.public_id)); assert.equal(channelRepository.all().filter(c => c.chatbot_id === botId).length, 1); ok('channel created and mirrored');
    assert.deepEqual(await chatbotService.checklist(botId), { hasDocument: true, modelSelected: true, channelReady: true, ready: true }); ok('checklist ready');
    assert.equal((await chatbotService.publish(botId)).status, 'ACTIVE'); ok('publish');
    assert.equal((await chatbotService.pause(botId)).status, 'PAUSED'); assert.equal((await chatbotService.resume(botId)).status, 'ACTIVE'); ok('pause and resume');
    const answer = await playgroundService.ask(botId, 'What is the refund policy?', null);
    assert.ok(answer.answer.length > 0 && answer.sources.length >= 1); ok('browser-local playground works on a database-backed chatbot (hybrid mode)');
    const refreshed = await channelService.toggle(botId, channel); assert.equal(refreshed.enabled, false); ok('channel toggle');

    console.log('Admin moderation, appeals, audit');
    await authService.logout();
    await rejects(chatbotService.list(), /sign in|Please/i); ok('after sign-out the services refuse');
    await authService.login('admin@gmail.com', '123');
    const listed = (await adminService.users(run)).find(u => u.email === email);
    assert.equal(listed.status, 'ACTIVE'); assert.equal(listed.quota.max_bots, 5); assert.equal(listed.chatbot_count, 1); assert.equal(listed.builtIn, false); ok('admin user list maps status, quota and counts');
    assert.ok((await adminService.bots()).some(b => b.id === botId && b.owner_email === email)); ok('admin chatbot list');
    await adminService.disableBot(botId, 'Spam content'); ok('admin disables the chatbot with a reason');
    await authService.logout(); await authService.login(email, password);
    const disabled = await chatbotService.get(botId); assert.equal(disabled.status, 'DISABLED'); assert.equal(disabled.disabled_reason, 'Spam content'); ok('owner sees DISABLED and the reason');
    const appeal = await appealService.submit(botId, 'It was a mistake.'); assert.equal(appeal.status, 'PENDING'); assert.equal((await appealService.forBot(botId)).length, 1); ok('owner appeals');
    await authService.logout(); await authService.login('admin@gmail.com', '123');
    assert.ok((await appealService.pendingCount()) >= 1); await appealService.reject(appeal.id, 'Still violates policy'); ok('admin rejects with a reason');
    await authService.logout(); await authService.login(email, password);
    const second = await appealService.submit(botId, 'Content removed.');
    await authService.logout(); await authService.login('admin@gmail.com', '123');
    await appealService.approve(second.id, 'Looks fine now');
    const entries = await auditService.list({ target: botName });
    assert.deepEqual(entries.map(e => e.action).sort(), ['APPROVE_APPEAL', 'FORCE_DISABLE_CHATBOT', 'REJECT_APPEAL']); assert.ok(entries.every(e => e.reason && e.admin_email === 'admin@gmail.com')); ok('audit log has every decision with its reason');
    await authService.logout(); await authService.login(email, password);
    assert.equal((await chatbotService.get(botId)).status, 'PAUSED'); ok('approval returns the chatbot as PAUSED');
  } finally {
    console.log('Cleanup');
    try {
      await authService.logout().catch(() => {});
      if (botId) { await authService.login(email, password); await chatbotService.delete(botId, botName); assert.equal(chatbotRepository.all().some(b => b.id === botId), false); ok('chatbot deleted (name retyped) and removed from the cache'); await authService.logout(); }
      if (userId) { await authService.login('admin@gmail.com', '123'); await adminService.remove(userId); ok('test user removed'); await authService.logout(); }
    } catch (error) { console.error('cleanup problem:', error.message); process.exitCode = 1; }
  }
  console.log(`\n${passed} checks passed against ${process.env.NEXT_PUBLIC_API_URL}`);
})().catch(error => { console.error('\nFAILED:', error); process.exitCode = 1; });
