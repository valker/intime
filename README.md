# InTime

A simple, reliable app for recurring task reminders: take a break every hour, water plants every three days, submit meter readings monthly, renew a contract yearly.

When you open the app, you see a list of tasks sorted by due time. The soonest tasks appear at the top.

When a task is due, the app notifies you. If you open the app later, tasks that became due while it was closed are highlighted in red.

Long-press a task to open the menu: **Acknowledge**, **Edit**, **Delete**.

- **Acknowledge** — the next due time is calculated from now plus the task’s interval.
- **Edit** — change the task name or interval.
- **Delete** — remove the task.

The app is free, has no ads, no in-app purchases, and does not require internet access.

---

## Database structure

Data is stored in a SQLite database named `main` (schema version **5**).

### Table: `main.tasks`

| Column        | Type    | Description |
|---------------|---------|-------------|
| `id`          | INTEGER | Primary key, auto-increment, unique. |
| `description` | TEXT    | User-visible task name. |
| `interval`    | INTEGER | Interval type (e.g. minutes, hours, days). |
| `amount`      | INTEGER | Number of interval units until next alarm. |
| `next_alarm`  | INTEGER | Next alarm time (Unix timestamp, seconds). |
| `next_caution`| INTEGER | Next caution time (Unix timestamp, seconds). |
| `last_ack`    | INTEGER | Last acknowledge time (Unix timestamp, seconds). |
| `quant`       | INTEGER | Interval divider (default 1). |

---

## Export file format

From **Settings → Export** you can save the current task list to a JSON file. The system file picker lets you choose the path and filename.

### Structure

- **Root**
  - `meta` — backup metadata.
  - `tables` — exported tables (e.g. `tasks`).

- **meta**
  - `app` — application ID (e.g. `com.vpe_soft.intime.intime`).
  - `backup_version` — backup format version (currently `1`).
  - `db_version` — database schema version at export time (e.g. `5`).
  - `created_at` — export time (Unix timestamp, milliseconds).

- **tables.tasks**
  - `columns` — array of column names in order.
  - `rows` — array of rows; each row is an array of values in the same order as `columns`.

### Column order in export

`id`, `description`, `interval`, `amount`, `next_alarm`, `next_caution`, `last_ack`, `quant`

### Example

```json
{
  "meta": {
    "app": "com.vpe_soft.intime.intime",
    "backup_version": 1,
    "db_version": 5,
    "created_at": 1736345234000
  },
  "tables": {
    "tasks": {
      "columns": [
        "id",
        "description",
        "interval",
        "amount",
        "next_alarm",
        "next_caution",
        "last_ack",
        "quant"
      ],
      "rows": [
        [1, "Drink water", 1, 30, 1700000000, 0, 0, 1],
        [2, "Stretch", 2, 2, 1700003600, 0, 0, 1]
      ]
    }
  }
}
```

File encoding is UTF-8.
