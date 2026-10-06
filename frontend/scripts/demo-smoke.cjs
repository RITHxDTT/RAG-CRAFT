/* eslint-disable @typescript-eslint/no-require-imports -- CommonJS harness installs a TypeScript loader for isolated service tests. */
// Exercise real demo services with isolated browser adapters; no user data is touched.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const Module = require('node:module');
const ts = require('typescript');
const source = path.resolve(__dirname, '../src');
const resolve = Module._resolveFilename;
Module._resolveFilename = function (name, ...args) {
  return resolve.call(this, name.startsWith('@/') ? path.join(source, name.slice(2)) : name, ...args);
};
require.extensions['.ts'] = (module, filename) => module._compile(ts.transpileModule(fs.readFileSync(filename, 'utf8'), {
  compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 },
}).outputText, filename);
const store = {};
global.window = new EventTarget();
window.location = { origin: 'http://localhost:3000' };
window.localStorage = {
  getItem: key => store[key] ?? null,
  setItem: (key, value) => { store[key] = value; Object.defineProperty(window.localStorage,key,{configurable:true,enumerable:true,get:()=>store[key]}); },
  removeItem: key => { delete store[key]; delete window.localStorage[key]; },
};
global.crypto = require('node:crypto').webcrypto;
global.CustomEvent = class extends Event { constructor(name, options) { super(name);this.detail=options?.detail; } };
const files = new Map();
const { fileStorage } = require('../src/storage/indexed-db.ts');
Object.assign(fileStorage, { put: async (id,file)=>files.set(id,file), get:async id=>files.get(id),remove:async id=>files.delete(id),clear:async()=>files.clear() });
const {authService}=require('../src/services/auth.service.ts');
const {chatbotService}=require('../src/services/chatbot.service.ts');
const {knowledgeService}=require('../src/services/knowledge.service.ts');
const {channelService}=require('../src/services/channel.service.ts');
const {playgroundService}=require('../src/services/playground.service.ts');
const {publicChatService}=require('../src/services/public-chat.service.ts');
const {adminService}=require('../src/services/admin.service.ts');
const {analyticsService}=require('../src/services/analytics.service.ts');
const {chartPreview}=require('../src/mocks/chart-preview.ts');
const {demoService}=require('../src/services/demo.service.ts');
const {knowledgeRepository}=require('../src/repositories/knowledge.repository.ts');
const {seedDemo}=require('../src/mocks/seed.ts');
const {KEYS}=require('../src/storage/keys.ts');
(async()=>{
  await assert.rejects(authService.me(),/sign in/);
  await authService.login('user@gmail.com','123');
  const seeded = await chatbotService.list(); assert.equal(seeded.length,1); seedDemo(); assert.equal((await chatbotService.list()).length,1);
  await assert.rejects(authService.register({full_name:'Tester',email:'tester@example.com',password:'abc',confirm_password:'xyz',terms:true}),/match/);
  await authService.register({full_name:'Tester',email:'tester@example.com',password:'abc',confirm_password:'abc',terms:true});
  assert.equal((await authService.me()).email,'user@gmail.com');
  await assert.rejects(authService.register({full_name:'Tester',email:'TESTER@example.com',password:'abc',confirm_password:'abc',terms:true}),/already/);
  await authService.logout(); await authService.login('tester@example.com','abc');
  assert.equal((await authService.me()).role,'USER'); assert.equal((await chatbotService.list()).length,0);
  await assert.rejects(chatbotService.get('demo_company'),/not found/);await assert.rejects(adminService.users(),/Administrator/);
  const bot=await chatbotService.create({name:'Tester Bot',description:'Testing'});assert.equal(bot.status,'DRAFT');
  const doc=await knowledgeService.upload(bot.id,{name:'policy.pdf',size:18000,type:'application/pdf'});
  assert.equal(doc.status,'QUEUED');assert.ok(files.has(doc.id));
  knowledgeRepository.put({...doc,processingStartedAt:new Date(Date.now()-5000).toISOString()});
  assert.equal((await knowledgeService.list(bot.id))[0].status,'READY');assert.equal((await knowledgeService.detail(bot.id,doc.id)).chunkCount,3);
  const website=await knowledgeService.website(bot.id,'Website','https://example.com');assert.equal(website.file_type,'WEBSITE');
  const first=await playgroundService.ask(bot.id,'What is the refund policy?',null,'Qwen');assert.ok(first.answer.includes('Qwen'));assert.equal(first.sources.length,1);
  const second=await playgroundService.ask(bot.id,'And leave?',first.conversation_id);assert.equal(second.conversation_id,first.conversation_id);assert.equal((await playgroundService.get(bot.id,first.conversation_id)).messages.length,4);
  await playgroundService.compare(bot.id,'Compare this',['Qwen','Llama 3.2 3B']);assert.equal((await chatbotService.get(bot.id)).settings.model_name,'Llama 3.2 3B');
  const channel=await channelService.create(bot.id,'PUBLIC_LINK');await assert.rejects(publicChatService.metadata('share',channel.public_id),/Active/);
  await chatbotService.update(bot.id,{name:bot.name,description:bot.description,status:'ACTIVE'});
  const settings={...channel.settings,password:'guest'};await channelService.save(bot.id,channel.id,settings);
  assert.equal((await publicChatService.metadata('share',channel.public_id)).passwordRequired,true);
  await assert.rejects(publicChatService.ask('share',channel.public_id,'hi'),/password/);await publicChatService.verify('share',channel.public_id,'guest');
  const conversationCount=(await playgroundService.list(bot.id)).length;
  await publicChatService.ask('share',channel.public_id,'hi',undefined,undefined,'guest');assert.equal((await playgroundService.list(bot.id)).length,conversationCount);
  const regenerated=await channelService.regenerate(bot.id,channel.id);await assert.rejects(publicChatService.metadata('share',channel.public_id),/no longer valid/);
  await channelService.save(bot.id,channel.id,{...settings,expires:'2000-01-01T00:00'});await assert.rejects(publicChatService.metadata('share',regenerated.public_id),/expired/);
  const overview=await analyticsService.overview(false,7);assert.equal(overview.daily.length,7);assert.equal(overview.daily.reduce((sum,day)=>sum+day.messages,0),overview.usage.total_messages);assert.equal(overview.sourceTypes.reduce((sum,type)=>sum+type.value,0),2);assert.equal(overview.botStatus.reduce((sum,status)=>sum+status.value,0),1);
  const beforePreview=JSON.stringify(store);const preview=chartPreview(30);assert.equal(preview.daily.length,30);assert.equal(preview.channels.reduce((sum,channel)=>sum+channel.value,0),preview.usage.total_messages);assert.equal(preview.chatbots.reduce((sum,bot)=>sum+bot.value,0),preview.usage.total_messages);assert.equal(JSON.stringify(store),beforePreview);
  const copy=await chatbotService.duplicate(bot.id);assert.equal(copy.status,'DRAFT');assert.equal(copy.document_count,0);
  const reset=await authService.forgot('tester@example.com');await authService.reset(reset.token,'new','new');await authService.login('tester@example.com','new');
  await authService.updateProfile({full_name:'Updated',display_name:'Test',bio:'Profile persists',email:'tester@example.com'});await authService.logout();await authService.login('tester@example.com','new');assert.equal((await authService.me()).bio,'Profile persists');
  await authService.logout();await authService.login('admin@gmail.com','123');assert.equal((await adminService.users()).length,3);
  await assert.rejects(adminService.status('demo_user',false),/protected/);await assert.rejects(adminService.remove('demo_admin'),/protected/);
  assert.ok((await adminService.usage(true)).total_messages>=10);
  const tester=(await adminService.users()).find(u=>u.email==='tester@example.com');await adminService.status(tester.id,false);await authService.logout();await assert.rejects(authService.login(tester.email,'new'),/disabled/);
  await authService.login('admin@gmail.com','123');await adminService.remove(tester.id);assert.equal((await chatbotService.list()).length,1);assert.equal(files.size,0);
  window.localStorage.setItem('unrelated-app','keep');await demoService.reset();assert.equal(window.localStorage.getItem('unrelated-app'),'keep');assert.equal(window.localStorage.getItem(KEYS.session),null);
  await authService.login('user@gmail.com','123');assert.equal((await chatbotService.list()).length,1);
  console.log('PASS: auth, fixed accounts, registration, reset, profile, ownership, admin guards, CRUD, upload, processing recovery, website, chat, compare, analytics, guest protection, expiry, regeneration, account cascade, namespaced demo reset.');
})().catch(error=>{console.error(error);process.exitCode=1;});
