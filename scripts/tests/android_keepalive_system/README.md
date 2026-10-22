# Isolated Android keep-alive system acceptance

This fixture compiles the production keep-alive Service, AIDL, package policy and Runtime into a disposable module APK. The probe app variants run under the two supported WeChat package names and an unrelated package, each with a distinct Android UID.

Android invokes `Service.onBind()` in the service process, so that callback cannot authenticate the remote package. The production interface validates `Binder.getCallingUid()` for every AIDL state mutation instead; the probe confirms the host UID is preserved by Binder and rejects the unrelated UID.

Build from the repository root:

```sh
./gradlew -p scripts/tests/android_keepalive_system :module:assembleDebug --rerun-tasks
./gradlew -p scripts/tests/android_keepalive_system :probe:assembleOfficialVisibleDebug :probe:assembleModifiedVisibleDebug :probe:assembleAttackerVisibleDebug :probe:assembleOfficialHiddenDebug
```

On a disposable API 35 AVD only, install `module-debug.apk` and the `*-visible-debug.apk` variants for Binder tests. The `visible` flavor declares the test module in `<queries>`; the `hidden` flavor deliberately matches stock WeChat's lack of module visibility. The probe Activity accepts these `action` extras:

- `bind_private`: verifies that Android rejects a cross-UID bind to the `exported=false` control service.
- `register` / `remove`: bind the production exported Binder service and register or remove only the caller's exact package.
- `register_then_invalid_start`: register a target without FGS promotion, then send an invalid `startForegroundService()` request while that target is active. Capture whether the system's promotion deadline is cleared or produces a timeout.
- `attack`: tries to register the official target from the unrelated UID and must receive `SecurityException`.
- `module_context_bind`: tries `createPackageContext()` with the same flags used by the module runtime, logs the process UID versus the binding Context's package/UID, then binds. Run it against an installed module package absent from the probe's `<queries>` list to capture package-visibility behavior.
- `start_without_query`: makes an explicit foreground-service start without binding or a `<queries>` entry. The production service must reject the missing one-use token and stop without registering a target.
- `background_remove`: registers through Binder, then removes the target after eight seconds. Send Home during the delay; removal must not start a foreground service.
- After registering both hosts, kill only the isolated module test process. Both bound probes should reconnect and re-register; reconnection must not request foreground promotion. A later visible `register` action is the only promotion attempt. Then remove one host and confirm the other remains, and remove the last host after a service recreation; `dumpsys activity services` must show no foreground service. This catches an OS-retained FGS state after the service's local foreground flag resets.
- `background_start`: leaves the Activity in the background and attempts a test-only FGS start after eight seconds; API 35 should reject it.
- `timeout`: starts a test-only `dataSync` FGS, then relies on Android 15's actual timeout callback.
- `production_apply`: invokes the exact production `WeChatKeepAliveRuntime.apply(hostContext, allowForegroundStart = true)` call used by the WeChat settings page, after writing the matching settings. Run this using `officialHiddenDebug` first; the module must be invisible to the probe and the real production call must take its safe unavailable path. The visible variant can then prove the remaining Binder/start path when visibility is declared.
- Supply `disable_after_ms` with `production_apply` to disable that package later through the production `apply(hostContext)` path. Start both visible WeChat package variants, then schedule one to disable; service logs must show the other target still registered.
- `storage_identity_probe`: compares `Process.myUid()` with the UID attached to `createPackageContext()` and writes/reads a test SharedPreferences snapshot through both contexts. It checks the actual XML files under each `dataDir`; a module Context must not be treated as granting the module UID's private-storage access.
- `register_no_promote` followed by `invalid_start`: tests a bad foreground-start token while a target is active but the service is not foreground. Capture ActivityManager/AndroidRuntime logs for the foreground-start deadline; repeat after a valid promotion to confirm an invalid request does not stop an already-active FGS.

For the timeout case, save the old `activity_manager/data_sync_fgs_timeout_duration` value first. Set a short value with `device_config`, enable `FGS_INTRODUCE_TIME_LIMITS` for the probe package, start `timeout` while visible, then send Home and wait for `DATASYNC_ON_TIMEOUT`. Restore the original DeviceConfig value (delete the key if it was unset) and run `am compat reset FGS_INTRODUCE_TIME_LIMITS <probe-package>` afterward.

The fixture package IDs are reserved for this isolated AVD; never install the probe APKs on a user's phone. These are test fixtures, not an alternate production entry point.
