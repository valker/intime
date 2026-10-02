# Working on Intime

Use the active Java/XML, Room-backed application. Do not restore deleted legacy
UI or SQLite runtime paths.

## Testing

Run relevant tests proactively when changing this project. The user has authorized
local tests and instrumented tests on the dedicated project test emulator.
See TESTING.md for commands and environment requirements.

- Domain calculations, JSON backup, DAO, repository and scheduling changes:
  run local tests, preferably the affected class first and the full suite after.
- Database/schema/migration changes: also run device tests.
- Android lifecycle, permissions and notification changes: run applicable local
  and device tests; report missing coverage and manual checks explicitly.
- Documentation-only changes do not require rebuilding the application.
- Use scripts/test.ps1. Default online mode can fetch dependencies; -Offline is
  appropriate after a successful bootstrap. Do not interpret old reports as a
  current pass. A zero test count is not a pass.
- In a restricted execution environment Gradle may require an approved escalation
  for the user cache and downloads. These instructions do not override sandbox
  access controls. Report infrastructure failures separately from failing tests.
- The runner creates .test-tools/avd/Intime_Test_API35.avd. Use only this dedicated
  emulator for autonomous instrumented tests. Never install, reset, clear data,
  reboot or run tests on a personal phone or the user's Pixel_8 AVD implicitly.
- Avoid parallel runs against the same checkout. Keep logs under .test-tools/runs;
  terminate only processes started by the current test invocation.
- Do not read or alter the user's main*.db files as part of test setup.
