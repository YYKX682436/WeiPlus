# Isolated Android keep-alive system acceptance

This fixture compiles the production keep-alive Service, AIDL, package policy and Runtime into a disposable module APK. The probe app variants run under the two supported WeChat package names and an unrelated package, each with a distinct Android UID.

Android invokes `Service.onBind()` in the service process, so that callback cannot authenticate the remote package. The production interface validates `Binder.getCallingUid()` for every AIDL state mutation instead; the probe confirms the host UID is preserved by Binder and rejects the unrelated UID.

Build from the repository root:

```sh
./gradlew -p scripts/tests/android_keepalive_system :module:assembleDebug --rerun-tasks
./gradlew -p scripts/tests/android_keepalive_system :probe:assembleOfficialDebug :probe:assembleModifiedDebug :probe:assembleAttackerDebug
```

On a fresh API 35 AVD only, install `module-debug.apk` and the three `probe-*-debug.apk` variants. The probe Activity accepts these `action` extras:

- `bind_private`: verifies that Android rejects a cross-UID bind to the `exported=false` control service.
- `register` / `remove`: bind the production exported Binder service and register or remove only the caller's exact package.
- `attack`: tries to register the official target from the unrelated UID and must receive `SecurityException`.
- `module_context_bind`: tries `createPackageContext()` with the same flags used by the module runtime, logs the process UID versus the binding Context's package/UID, then binds. Run it against an installed module package absent from the probe's `<queries>` list to capture package-visibility behavior.
- `start_without_query`: makes an explicit foreground-service start without binding or a `<queries>` entry. The production service must reject the missing one-use token and stop without registering a target.
- `background_remove`: registers through Binder, then removes the target after eight seconds. Send Home during the delay; removal must not start a foreground service.
- After registering both hosts, kill only the isolated module test process. Both bound probes should reconnect and re-register; reconnection must not request foreground promotion. A later visible `register` action is the only promotion attempt. Then remove one host and confirm the other remains, and remove the last host after a service recreation; `dumpsys activity services` must show no foreground service. This catches an OS-retained FGS state after the service's local foreground flag resets.
- `background_start`: leaves the Activity in the background and attempts a test-only FGS start after eight seconds; API 35 should reject it.
- `timeout`: starts a test-only `dataSync` FGS, then relies on Android 15's actual timeout callback.

For the timeout case, save the old `activity_manager/data_sync_fgs_timeout_duration` value first. Set a short value with `device_config`, enable `FGS_INTRODUCE_TIME_LIMITS` for the probe package, start `timeout` while visible, then send Home and wait for `DATASYNC_ON_TIMEOUT`. Restore the original DeviceConfig value (delete the key if it was unset) and run `am compat reset FGS_INTRODUCE_TIME_LIMITS <probe-package>` afterward.

The fixture package IDs are reserved for this isolated AVD; never install the probe APKs on a user's phone.
