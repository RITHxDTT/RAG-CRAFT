/* eslint-disable @typescript-eslint/no-require-imports -- CommonJS harness installs a TypeScript loader for isolated service tests. */
// Exercise real demo services with isolated browser adapters; no user data is touched.
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

module.exports = { files, store };
