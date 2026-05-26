# Play Console Declarations

This file is the release checklist for Google Play Console app content forms.
It is aligned with `app/src/main/AndroidManifest.xml` and the app's local-only
privacy model.

## Manifest Permissions

Declared permissions:

| Permission | Play Console answer | Reason |
| --- | --- | --- |
| `POST_NOTIFICATIONS` | Required for app functionality | Intime sends reminder notifications when a task becomes due or overdue. |
| `SCHEDULE_EXACT_ALARM` | Required for app functionality | Intime is a reminder app. Exact alarms are needed so user-created reminders fire at the selected time. |
| `RECEIVE_BOOT_COMPLETED` | Required for app functionality | Intime reschedules local reminders after the device restarts. |

Not declared:

| Permission/API | Play Console answer |
| --- | --- |
| `USE_EXACT_ALARM` | Not used. The app uses `SCHEDULE_EXACT_ALARM` and exposes system settings when exact alarms are unavailable. |
| Calendar permissions | Not used. The app does not read or write the user's calendar. |
| Location permissions | Not used. |
| Contacts permissions | Not used. |
| Photos/videos/audio permissions | Not used. |
| Advertising ID | Not used. |

## Exact Alarms

Use case: **Alarm, timer, calendar, or reminder app**

Suggested declaration text:

```text
Intime is an offline reminder app. Users create tasks with reminder intervals,
and the app schedules a local alarm for the next due task. Exact alarms are
needed so reminders appear at the intended time. If exact alarms are unavailable,
the app shows a settings action and falls back to less precise scheduling.
```

Implementation notes for reviewer context:

- The app checks exact-alarm availability before scheduling exact alarms.
- The Settings screen explains exact-alarm status and opens the Android system
  exact-alarm settings page.
- Alarms are scheduled locally with `AlarmManager`; there is no server component.

## Notifications

Use case: **Task/reminder notifications**

Suggested declaration text:

```text
Intime uses notifications only to remind users about their own local tasks.
Notifications may include an acknowledge action for a newly overdue task. The app
does not use notifications for ads, promotions, account messages, or marketing.
```

Android 13+ runtime permission behavior:

- The app requests `POST_NOTIFICATIONS` at runtime.
- If notification permission is denied, the app shows an in-app warning.
- Denied notification permission does not mark tasks as notified.

## Boot Completed

Use case: **Restore reminders after restart**

Suggested declaration text:

```text
Intime listens for device boot completion only to reschedule reminders that the
user previously created. It does not start a foreground service, collect data,
or perform network work after boot.
```

## Data Safety

Recommended answers:

| Question | Answer |
| --- | --- |
| Does the app collect or share any required user data types? | No |
| Is all user data collected by the app encrypted in transit? | Not applicable: no data is transmitted off device. |
| Can users request that data is deleted? | Not applicable for server-side deletion: data is stored locally and can be deleted in the app or by uninstalling/clearing app data. |
| Does the app share data with third parties? | No |
| Does the app use analytics, advertising, or tracking SDKs? | No |

Privacy policy URL/content: use `PRIVACY_POLICY.md`.

Important consistency note: task titles, reminder intervals, and backup files are
user data stored locally on the device. They should not be reported as collected
in Data safety because the app does not transmit them off device.

## Ads

Answer: **No ads**

The app does not contain ad SDKs and does not use Advertising ID.

## App Access

Answer: **All functionality is available without special access**

The app does not require accounts, credentials, subscriptions, region-specific
access, organization membership, or reviewer login.

## Content Rating

Recommended category: **Utility / Productivity**

Suggested answers:

- No violence, fear, sexual content, gambling, controlled substances, or user
  generated public content.
- No location sharing.
- No purchases.
- No browser or unrestricted web access.

## Target Audience

Recommended answer: **General audience / not designed for children**

The app is a productivity utility for personal task reminders and is not designed
or marketed specifically for children.

## Store Listing Privacy Notes

Short privacy summary for the listing:

```text
Intime works offline. Your tasks and reminders stay on your device. The app does
not use accounts, ads, analytics, tracking, or cloud sync.
```

