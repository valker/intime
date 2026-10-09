"""Host-проверки на синтетических JSON/SQLite; пользовательские файлы не читаются."""
import copy
import json
import sqlite3
import tempfile
import unittest
import sys
from contextlib import closing
from pathlib import Path
sys.dont_write_bytecode = True
from real_copy_fixture import seed, compare, load_backup, COLUMNS

class RealCopyTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.source = self.root / 'input.json'
        self.target = self.root / 'seed.sqlite'
        self.body = {'meta': {'app': 'com.vpe_soft.intime.intime', 'backup_version': 1, 'db_version': 5, 'created_at': 1},
                     'tables': {'tasks': {'columns': COLUMNS, 'rows': [[19, 'Русский текст', 0, 1, 0, 0, 0, 1], [3, "Quote ' \n", 5, 4, 1234, 1200, 100, 7]]}}}
        self.write()

    def write(self):
        self.source.write_text(json.dumps(self.body, ensure_ascii=False), encoding='utf-8')

    def test_seed_exact_values_and_source_unchanged(self):
        """Даны строки с разрывами ID, Unicode, кавычкой и старым сроком; seed
        создаёт schema5 и compare5 сверяет все восемь полей. Проверяем неизменность
        исходных байтов и следующий AUTOINCREMENT=20; прежние удалённые ID не моделируем."""
        original = self.source.read_bytes()
        self.assertEqual(seed(self.source, self.target)['taskCount'], 2)
        self.assertEqual(compare(self.source, self.target, False)['status'], 'PASSED')
        self.assertEqual(self.source.read_bytes(), original)
        with closing(sqlite3.connect(self.target)) as db, db:
            db.execute("INSERT INTO tasks(description,interval,amount) VALUES ('new',1,1)")
            self.assertEqual(db.execute('SELECT max(id) FROM tasks').fetchone()[0], 20)

    def test_refuses_overwriting_database(self):
        """Дана уже созданная база; повтор seed обязан отказать и оставить все
        её байты прежними. Проверяется защита файла, не владение Android AVD."""
        seed(self.source, self.target)
        before = self.target.read_bytes()
        with self.assertRaisesRegex(ValueError, 'REFUSING_EXISTING_TARGET'):
            seed(self.source, self.target)
        self.assertEqual(self.target.read_bytes(), before)

    def test_invalid_contracts_rejected_before_database_creation(self):
        """Синтетический экспорт поочерёдно содержит дубли ID, bool вместо числа,
        перестановку столбцов или две версии meta; seed отказывает до создания
        файла. Это контроль формы экспорта, не Java-расчёта календарных сроков."""
        original = copy.deepcopy(self.body)
        for mutation in ('duplicate', 'bool', 'columns', 'meta'):
            self.body = copy.deepcopy(original)
            if mutation == 'duplicate': self.body['tables']['tasks']['rows'][1][0] = 19
            elif mutation == 'bool': self.body['tables']['tasks']['rows'][0][3] = True
            elif mutation == 'columns': self.body['tables']['tasks']['columns'] = list(reversed(COLUMNS))
            else: self.body['meta']['version'] = 1
            self.write()
            with self.subTest(mutation=mutation), self.assertRaises(ValueError):
                seed(self.source, self.target)
            self.assertFalse(self.target.exists())

    def test_detects_changed_field_in_migrated_snapshot(self):
        """Дана копия schema6 с Room identity и обоими значениями wasNotified;
        compare6 принимает исходные восемь полей. Затем меняем description и
        ожидаем отказ, не требуя нулевых флагов у просроченных задач. Миграция
        здесь моделируется SQL; настоящий Room проверяется device-сценарием."""
        seed(self.source, self.target)
        with closing(sqlite3.connect(self.target)) as db, db:
            db.execute('ALTER TABLE tasks ADD COLUMN wasNotified INTEGER NOT NULL DEFAULT 0')
            db.execute('UPDATE tasks SET wasNotified=1 WHERE id=3')
            db.execute('CREATE TABLE room_master_table (id INTEGER PRIMARY KEY, identity_hash TEXT)')
            db.execute("INSERT INTO room_master_table VALUES (42,'synthetic')")
            db.execute('PRAGMA user_version=6')
        self.assertEqual(compare(self.source, self.target, True)['notifiedCount'], 1)
        with closing(sqlite3.connect(self.target)) as db, db: db.execute("UPDATE tasks SET description='changed' WHERE id=19")
        with self.assertRaisesRegex(ValueError, 'TASK_VALUES_OR_IDS_CHANGED'):
            compare(self.source, self.target, True)

if __name__ == '__main__':
    unittest.main()
