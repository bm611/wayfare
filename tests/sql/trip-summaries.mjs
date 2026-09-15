// Run against an isolated in-memory PostgreSQL instance, never a live project.
// PGLITE_MODULE=/path/to/@electric-sql/pglite/dist/index.js node tests/sql/trip-summaries.mjs
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { pathToFileURL } from 'node:url';
const { PGlite } = await import(pathToFileURL(process.env.PGLITE_MODULE).href);
const db = new PGlite();
try {
  await db.exec(`
    CREATE ROLE authenticated; CREATE ROLE anon;
    CREATE TABLE trips (id text PRIMARY KEY, user_id text);
    CREATE TABLE expenses (id int PRIMARY KEY, trip_id text, amount numeric);
    CREATE TABLE trip_members (trip_id text, user_id text);
    ALTER TABLE trips ENABLE ROW LEVEL SECURITY;
    ALTER TABLE expenses ENABLE ROW LEVEL SECURITY;
    CREATE POLICY trip_read ON trips TO authenticated USING (
      user_id = current_setting('test.account') OR id IN
        (SELECT trip_id FROM trip_members WHERE user_id = current_setting('test.account')));
    CREATE POLICY expense_read ON expenses TO authenticated USING (
      trip_id IN (SELECT trip_id FROM trip_members WHERE user_id = current_setting('test.account')));
    GRANT SELECT ON trips, expenses, trip_members TO authenticated;
    INSERT INTO trips VALUES ('shared', 'alice'), ('empty', 'alice'), ('private', 'bob');
    INSERT INTO trip_members VALUES ('shared', 'alice'), ('shared', 'bob'), ('private', 'bob');
    INSERT INTO expenses SELECT n, 'shared', 0.10 FROM generate_series(1, 1501) n;
    INSERT INTO expenses VALUES (1502, 'private', 999);
  `);
  await db.exec(readFileSync(new URL('../../supabase/migrations/20260915120000_trip_summaries.sql', import.meta.url), 'utf8'));
  await db.exec("SET ROLE authenticated; SET test.account = 'alice'");
  let result = await db.query('SELECT id, spent::text, entries::text FROM trip_summaries ORDER BY id');
  assert.deepEqual(result.rows, [
    { id: 'empty', spent: '0', entries: '0' }, { id: 'shared', spent: '150.10', entries: '1501' },
  ]);
  await db.exec("SET test.account = 'bob'");
  result = await db.query('SELECT id, spent::text FROM trip_summaries ORDER BY id');
  assert.deepEqual(result.rows, [{ id: 'private', spent: '999' }, { id: 'shared', spent: '150.10' }]);
  await db.exec("SET test.account = 'outsider'");
  assert.equal((await db.query('SELECT * FROM trip_summaries')).rows.length, 0);
  await db.exec('RESET ROLE; SET ROLE anon');
  await assert.rejects(db.query('SELECT * FROM trip_summaries'), /permission denied/);
  console.log('Trip summaries: exact totals beyond a page, empty trips, member isolation and anonymous denial passed.');
} finally { await db.close(); }
