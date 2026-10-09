"""Создаёт только новую тестовую schema5 из явно предоставленного JSON.

Исходный файл не меняется. Это логическая копия задач, без исторического
sqlite_sequence, WAL и preferences. Вывод CLI не содержит описаний задач.
"""
import hashlib
import json
import sqlite3
import sys
from contextlib import closing
from pathlib import Path

COLUMNS = ['id', 'description', 'interval', 'amount', 'next_alarm', 'next_caution', 'last_ack', 'quant']
DDL = ('CREATE TABLE tasks (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL UNIQUE,'
       'description TEXT NOT NULL, interval INTEGER NOT NULL, amount INTEGER NOT NULL,'
       'next_alarm INTEGER NOT NULL DEFAULT 0,next_caution INTEGER NOT NULL DEFAULT 0,'
       'last_ack INTEGER NOT NULL DEFAULT 0,quant INTEGER NOT NULL DEFAULT 1)')

def load_backup(source):
    path = Path(source).resolve(strict=True)
    if path.suffix.lower() != '.json' or path.stat().st_size > 10 * 1024 * 1024:
        raise ValueError('EXPECTED_JSON_UP_TO_10_MIB')
    payload = path.read_bytes()
    body = json.loads(payload.decode('utf-8-sig'))
    meta = body.get('meta', {})
    if (meta.get('app') != 'com.vpe_soft.intime.intime'
            or type(meta.get('backup_version')) is not int or meta['backup_version'] != 1
            or type(meta.get('db_version')) is not int or meta['db_version'] != 5
            or 'version' in meta or type(meta.get('created_at')) is not int or meta['created_at'] <= 0):
        raise ValueError('EXPECTED_PLAY25_META')
    tasks = body.get('tables', {}).get('tasks', {})
    rows = tasks.get('rows')
    if tasks.get('columns') != COLUMNS or not isinstance(rows, list) or not rows:
        raise ValueError('EXPECTED_NONEMPTY_TASKS_CONTRACT')
    ids = set()
    for row in rows:
        if not isinstance(row, list) or len(row) != 8 or not isinstance(row[1], str):
            raise ValueError('INVALID_ROW_SHAPE')
        if any(type(row[index]) is not int for index in (0, 2, 3, 4, 5, 6, 7)):
            raise ValueError('EXPECTED_INTEGER_FIELDS')
        if not 0 < row[0] < 2**63 - 1 or row[0] in ids:
            raise ValueError('INVALID_OR_DUPLICATE_ID')
        if not 0 <= row[2] <= 5 or not 0 < row[3] < 2**31 or not 0 < row[7] < 2**31:
            raise ValueError('INVALID_PERIOD_FIELDS')
        if any(not 0 <= row[index] < 2**63 for index in (4, 5, 6)):
            raise ValueError('INVALID_TIMESTAMP')
        ids.add(row[0])
    return sorted(rows, key=lambda row: row[0]), hashlib.sha256(payload).hexdigest()

def seed(source, target):
    rows, digest = load_backup(source)
    target = Path(target).resolve()
    if target.exists():
        raise ValueError('REFUSING_EXISTING_TARGET')
    # Exclusive create исключает замену существующей базы между проверкой и connect.
    with target.open('xb'):
        pass
    with closing(sqlite3.connect(target)) as connection, connection:
        connection.execute(DDL)
        connection.executemany('INSERT INTO tasks VALUES (?,?,?,?,?,?,?,?)', rows)
        connection.execute('PRAGMA user_version=5')
    return {'status': 'SEEDED', 'taskCount': len(rows), 'sourceSha256': digest}

def compare(source, snapshot, migrated):
    rows, digest = load_backup(source)
    path = Path(snapshot).resolve(strict=True)
    with closing(sqlite3.connect(path.as_uri() + '?mode=ro', uri=True)) as connection:
        if connection.execute('PRAGMA integrity_check').fetchone()[0] != 'ok':
            raise ValueError('SNAPSHOT_INTEGRITY_FAILED')
        columns = [row[1] for row in connection.execute('PRAGMA table_info(tasks)')]
        expected = COLUMNS + (['wasNotified'] if migrated else [])
        if columns != expected or connection.execute('PRAGMA user_version').fetchone()[0] != (6 if migrated else 5):
            raise ValueError('SNAPSHOT_SCHEMA_MISMATCH')
        actual = [list(row) for row in connection.execute('SELECT * FROM tasks ORDER BY id')]
        if [row[:8] for row in actual] != rows:
            raise ValueError('TASK_VALUES_OR_IDS_CHANGED')
        room = connection.execute("SELECT name FROM sqlite_master WHERE name='room_master_table'").fetchone()
        if bool(room) != migrated:
            raise ValueError('ROOM_TABLE_MISMATCH')
        if migrated:
            identity = connection.execute('SELECT identity_hash FROM room_master_table WHERE id=42').fetchone()
            if not identity or not identity[0] or any(row[8] not in (0, 1) for row in actual):
                raise ValueError('INVALID_ROOM_IDENTITY_OR_FLAGS')
    return {'status': 'PASSED', 'taskCount': len(rows), 'sourceSha256': digest,
            'schemaVersion': 6 if migrated else 5,
            'notifiedCount': sum(row[8] for row in actual) if migrated else None}

if __name__ == '__main__':
    try:
        if len(sys.argv) != 4 or sys.argv[1] not in ('seed', 'compare5', 'compare6'):
            raise ValueError('INVALID_ARGUMENTS')
        mode, source, target = sys.argv[1:4]
        result = seed(source, target) if mode == 'seed' else compare(source, target, mode == 'compare6') if mode in ('compare5', 'compare6') else None
        print(json.dumps(result))
    except Exception as error:
        # Не выводим sqlite/JSON исключение, оно может включать пользовательский текст.
        allowed = error.args[0] if isinstance(error, ValueError) and error.args and isinstance(error.args[0], str) and error.args[0].replace('_', '').isalnum() else 'LOCAL_INPUT_OR_SNAPSHOT_ERROR'
        print(json.dumps({'status': 'FAILED', 'code': allowed}))
        raise SystemExit(1)
