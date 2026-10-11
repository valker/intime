# Play Console Declarations

Source/document review refreshed October11,2026; artifact checks refer to
the archived signed candidate27/2.0.0-rc3 and DEVICE_TEST_RESULTS.md.
This is a draft for Google Play Console app content forms, not evidence that
forms were submitted or accepted. Review the final artifact and current Console
questions before submission. App operation is offline; system backup and
user-selected document providers may transfer data (see PRIVACY_POLICY.md).

## Manifest Permissions

Declared permissions:

| Permission | Play Console answer | Reason |
| --- | --- | --- |
| `POST_NOTIFICATIONS` | Required for app functionality | Intime sends reminder notifications when a task becomes due or overdue. |
| `SCHEDULE_EXACT_ALARM` | Required for app functionality | Intime is a reminder app. Exact alarms are needed so user-created reminders fire at the selected time. |
| `RECEIVE_BOOT_COMPLETED` | Required for app functionality | Intime reschedules local reminders after the device restarts. |

The merged AAB also declares WAKE_LOCK, ACCESS_NETWORK_STATE and
FOREGROUND_SERVICE from WorkManager, plus the application-specific AndroidX
signature permission DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION. The current
worker does not use foreground execution. INTERNET is absent. ACCESS_NETWORK_STATE
alone does not grant network communication. Audit the merged manifest rather
than only the source manifest.

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
Intime listens for device boot completion to reschedule user-created reminders
and may show a local summary of tasks missed while the device was off. It does
not request foreground execution or perform network work in this receiver.
```

## Data Safety

Draft answers for app-operated data flows; final review remains open:

| Question | Answer |
| --- | --- |
| Does the app collect or share any required user data types? | No |
| Is all user data collected by the app encrypted in transit? | The app has no network upload flow; review applicability in the current form. Do not claim encryption for plain JSON exports. |
| Can users request that data is deleted? | Not applicable for server-side deletion: data is stored locally and can be deleted in the app or by uninstalling/clearing app data. |
| Does the app share data with third parties? | No |
| Does the app use analytics, advertising, or tracking SDKs? | No |

Privacy policy content: `PRIVACY_POLICY.md`. Before release, publish the matching
text at an accessible public URL and check the app/store links. The resources
currently contain https://valker.github.io/intime_privacy_policy.html; its
availability/content was checked by direct HTTP GET on October11 (HTTP200).
The published text is old: it describes developer log collection, cookies and
third-party services, whereas the current application has no own upload flow
or INTERNET permission. Publish the reviewed replacement from
`google_play/intime_privacy_policy.html` after approval; it has not been published.
Actual Console answers remain unverified. The existing policy is historical
text, not evidence that the current app performs those data flows.

Important consistency note: task titles, reminder intervals, and backup files are
user data stored locally on the device. Local processing alone is not data
collection under the linked Data safety definitions. The manifest permits
Android system backup and SAF can use a cloud provider selected by the user;
the policy explains both. Confirm applicable disclosures for the final build,
its dependencies and these transfers in the current Console form.

Official references rechecked October11,2026:
[Data safety definitions](https://support.google.com/googleplay/android-developer/answer/10787469),
[User Data/privacy policy requirements](https://support.google.com/googleplay/android-developer/answer/10144311),
[Android system backup](https://developer.android.com/identity/data/autobackup).
The repository review does not establish compliance or Play approval.

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
Intime works offline without accounts, ads, analytics or its own cloud sync.
Android system backup may include app data. You choose the storage provider
when importing or exporting tasks.
```

