# Intime Product Notes

Intime is a simple and reliable reminder app for periodic tasks. It is intended
for things that must be repeated after a given interval: take a break, water
plants, submit meter readings, renew a contract, or perform any other recurring
maintenance task.

## Product Principles

- Reliability is more important than visual novelty.
- The app must work offline.
- The app should not require accounts, internet access, ads, or paid features.
- The user should always understand when the next reminder will happen.
- Existing users should be able to update without losing their tasks.
- The UI should stay simple enough for quick daily use.

## Core Concepts

### Task

A task is a periodic reminder item. It has:

- description;
- interval type;
- interval amount;
- next reminder time;
- optional caution time;
- last acknowledgement time;
- quantization value;
- notification state.

Future versions should also store reminder and acknowledgement timestamps as a
history log. This history can later support analytics and possible
recommendations, but it is not required for the first v2 release.

### Interval

An interval defines how often the task repeats. Supported interval types should
include:

- minutes;
- hours;
- days;
- weeks;
- months;
- years.

### Acknowledgement

Acknowledgement means the user confirms that the task has been done. After
acknowledgement, Intime calculates the next reminder time.

The next reminder is always calculated from the current acknowledgement time.
This is a key product feature of Intime: the schedule follows the real moment
when the user actually completed the task, not the previous planned reminder
time.

### Caution Time

Caution time is an optional earlier state before the task becomes due. It can be
used to visually warn the user that the task is approaching. For now, caution
time is calculated as a fixed percentage of the full interval.

### Due and Overdue

A task is due when the current time reaches or passes its next reminder time. A
task is overdue while it remains due and has not yet been acknowledged.

An overdue task waits for acknowledgement. It does not become "overdue several
times" while waiting. The app may remind the user that there are overdue tasks,
but these reminders should be calm and not too intrusive.

## Required User Flows

### View Tasks

The user opens the app and sees all tasks ordered by next reminder time. Tasks
that need attention are visually distinct.

### Create Task

The user enters a description, chooses an interval, chooses an amount, optionally
chooses quantization, and saves the task. Intime calculates the first reminder.

### Acknowledge Task

The user confirms that a task is done. Intime updates the last acknowledgement
time and calculates the next reminder.

### Edit Task

The user changes the task description or interval settings. Intime updates the
stored task and recalculates future reminder behavior if needed.

### Delete Task

The user deletes a task after confirmation.

### Receive Notification

When a task becomes due, Intime shows a notification. Tapping the notification
should take the user to a useful place, such as the task details screen or the
main task list.

When the user opens the app while a task notification is still visible—whether
from the launcher or by tapping the notification—all active notifications from
Intime must be dismissed. The user should not need to clear them manually from
the shade.

There are two notification intentions:

- A notification for a task that has just become overdue may include an `ACK`
  action for quick acknowledgement.
- Later reminder notifications about existing overdue tasks should not include an
  `ACK` action. They should open the task list so the user can assess the full
  overdue state.

### Import and Export

The user can export tasks to a local backup and import tasks from a backup file.
Import must not silently corrupt or lose data if the file is invalid.

For v2, import fully replaces the current task list. Its primary purpose is
debugging and migration testing: export data from the old version, import it into
the new version, and verify behavior with realistic tasks.

## Non-Goals for the First v2 Release

These features are intentionally outside the first releasable v2 scope unless
the roadmap changes:

- cloud sync;
- user accounts;
- collaboration or shared tasks;
- ads;
- paid features;
- server-side logic;
- complex calendar recurrence rules;
- machine-learning recommendations.

Timestamp history for analysis and recommendations is a future direction, but
recommendation features themselves are outside the first v2 release.
