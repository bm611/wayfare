import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { DatabaseSync } from 'node:sqlite';

test('Android trip reconciliation deletes only synced expenses for that account and trip', () => {
  const source = readFileSync(new URL('../../android/app/src/main/java/com/wayfare/app/data/local/WayfareDatabase.kt', import.meta.url), 'utf8');
  const query = source.match(/@Query\("([^"]+)"\)\s+suspend fun deleteSyncedTrip/)[1];
  const db = new DatabaseSync(':memory:');
  try {
    db.exec(`CREATE TABLE expenses (id TEXT PRIMARY KEY, accountId TEXT, tripId TEXT, syncState TEXT);
      INSERT INTO expenses VALUES
        ('deleted-remotely', 'alice', 'rome', 'Synced'),
        ('pending', 'alice', 'rome', 'Pending'),
        ('failed', 'alice', 'rome', 'Failed'),
        ('other-trip', 'alice', 'paris', 'Synced'),
        ('other-account', 'bob', 'rome', 'Synced');`);
    db.prepare(query).run({ accountId: 'alice', tripId: 'rome' });
    assert.deepEqual(db.prepare('SELECT id FROM expenses ORDER BY id').all().map((row) => row.id),
      ['failed', 'other-account', 'other-trip', 'pending']);
  } finally { db.close(); }
});
