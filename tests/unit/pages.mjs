import { test } from 'node:test';
import assert from 'node:assert/strict';
import { loadPages } from '../../src/lib/pages.ts';

test('reads past a server cap smaller than the requested page, without skipping rows', async () => {
  const source = Array.from({ length: 1003 }, (_, id) => ({ id }));
  const offsets = [];
  const result = await loadPages((from) => {
    offsets.push(from);
    return Promise.resolve({ data: source.slice(from, from + 200), error: null });
  }, new AbortController().signal);
  assert.deepEqual(result, source);
  assert.deepEqual(offsets, [0, 200, 400, 600, 800, 1000, 1003]);
});

test('a later page error never returns partial totals', async () => {
  await assert.rejects(loadPages((from) => Promise.resolve(from === 0
    ? { data: [1], error: null } : { data: null, error: { message: 'Offline' } }),
  new AbortController().signal), /Offline/);
});

test('cancelling an in-flight page prevents both publication and the next request', async () => {
  const controller = new AbortController();
  let finish;
  let calls = 0;
  const pending = loadPages(() => { calls++; return new Promise((resolve) => { finish = resolve; }); }, controller.signal);
  controller.abort();
  finish({ data: [1, 2], error: null });
  await assert.rejects(pending, { name: 'AbortError' });
  assert.equal(calls, 1);
});
