"""Читает только явную локальную копию синтетической базы тестового AVD.

Открытие SQLite в mode=ro запрещает изменение базы. Скопированные рядом WAL/SHM
учитываются SQLite; исходная база приложения и пользовательские main*.db не нужны.
Вывод содержит все поля задач, версию схемы и Room identity для сравнения runner.
Это чтение снимка, а не самостоятельный тест миграции.
"""
import json
import sqlite3
import sys
from pathlib import Path

path = Path(sys.argv[1]).resolve(strict=True)
with sqlite3.connect(path.as_uri() + "?mode=ro", uri=True) as connection:
    if connection.execute("PRAGMA integrity_check").fetchone()[0] != "ok":
        raise RuntimeError("Snapshot integrity check failed")
    columns = [row[1] for row in connection.execute("PRAGMA table_info(tasks)")]
    rows = connection.execute("SELECT * FROM tasks ORDER BY id").fetchall()
    room = connection.execute(
        "SELECT name FROM sqlite_master WHERE name = 'room_master_table'"
    ).fetchone()
    identity = connection.execute("SELECT identity_hash FROM room_master_table WHERE id=42").fetchone() if room else None
    print(json.dumps({"schemaVersion": connection.execute("PRAGMA user_version").fetchone()[0],
                      "columns": columns, "rows": rows, "roomIdentity": identity[0] if identity else None},
                     ensure_ascii=True))
