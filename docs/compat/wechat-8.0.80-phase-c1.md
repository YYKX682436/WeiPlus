# WeChat 8.0.80 Phase C-1: startup identity and database listener

Date: 2026-10-29  
Source baseline: `25240914aa7c08452a05c9c49538428f0186d94b`  
Device: Redmi K50 Ultra (`22081212C`), Android, LSPosed 2.1.0

## Result

- The repeated APK ZIP central-directory walk is now shared per process and per stable archive snapshot. The temporary real-package counter measured one ZIP enumeration for each stable APK identity, despite 151 and 163 runtime-key calls. The measurement counter and diagnostic logging were removed before the final build.
- Database candidate selection now intersects both string anchors and validates the candidate's insert/update/delete shape. A temporary runtime diagnostic build selected the structurally valid wrapper in both WeChat versions and installed active wrapper hooks. Generic SQLite hooks alone remain non-operational.
- The final signed, uninstrumented APK was installed over `h.Hchat`. No app was uninstalled and no WeChat data was cleared. Natural message-change end-to-end observation remains **NOT VERIFIED** because no message write occurred during passive observation; no message was sent and no database was modified.

## APK identity cache

`DexMethodCache.runtimeKey()` still reaches `WeChatVersionApi.buildCacheKey()` and `DexArchiveIdentity.forApplication()`. The cache is in that shared archive-identity layer, so callers through `DexFinder`, settings and tablet code share it as well.

The LRU holds at most 128 archive fingerprints. Its snapshot key is canonical path, filesystem file key, device, inode, size, mode, nanosecond modification time and nanosecond change time. Base-versus-split validation is not part of the cache key; the cached result separately records whether the archive contains DEX, so a resource-only split cannot be scanned again when the same path is later checked as a base APK.

On a cache miss, one synchronized ZIP walk records the sorted DEX entry names/CRC/size and SHA-256 of all non-directory ZIP entry names/CRC/size. Resource/config split metadata remains part of the application identity. The returned identity continues to include APK size and modification time and is combined with the existing package/version/client/Tinker/runtime-key fields. It does not reduce identity to `versionCode`.

The cache retains only snapshots and identity strings. It does not retain a `Context`, `Activity`, `ClassLoader` or `ZipFile`. A process restart gets a fresh cache. Concurrent calls are serialized through one cache lock, so a stable archive is scanned once; different cache misses are serialized too. Failed reads and archives with no usable entries are not cached. A snapshot is checked again after enumeration; if the path changes during the walk, that result is not published and the next call retries.

APK replacement is detected through inode/file-key and change-time metadata even if size and modification time are restored. Resource split replacement/content changes follow the same rule. Tinker patch files now add a metadata-only change token containing the same file identity and nanosecond change-time fields, covering same-size writes with restored modification time without reading patch payloads. `WeChatVersionApi` keeps its existing short-lived value cache and explicit refresh path for known runtime/Tinker transitions. ClassLoader switching does not rescan an unchanged APK; loader diagnostics remain separate from the archive cache. No identity is shared across processes.

`ModuleEntry` queues the first archive fingerprint in its existing `DexInstallScheduler` EARLY stage before Application-level identity approval. The measured first ZIP walk ran on `Hchat-DexInstall`; runtime-key calls on the main thread were cache hits. No timer, polling task, new resident thread or DexKit 30-second idle-reclaim change was added. A cache miss can still be synchronous if a caller arrives before the EARLY task has run or if the early read fails; that case was not forced during the measured launches.

### Performance measurements

The baseline and C-1 diagnostic counter were applied to the production runtime-key chain. `runtimeKey` aggregate CPU/wall measurements below are the instrument's cumulative timings; **they are not WeChat startup duration**.

| Package | C-0 runtimeKey calls / ZIP walks | C-0 runtimeKey aggregate (median) | C-0 ZIP-walk aggregate (median) | C-1 diagnostic calls / ZIP walks | C-1 ZIP walk | C-1 runtimeKey aggregate (median) | Main-thread runtimeKey calls |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| Official 8.0.80 (`com.tencent.mm`) | 71 / 71 | 4,596.72 ms (64.441 ms) | 4,537.39 ms (63.610 ms) | 151 / 1 | 81.795 ms, `Hchat-DexInstall` | 168.365 ms (0.867 ms) | 1 cache hit |
| Modified 8.0.76 (`com.tencent.mm.wx`) | 103 / 103 | 6,700.43 ms (65.099 ms) | 6,653.26 ms (64.682 ms) | 163 / 1 | 81.020 ms, `Hchat-DexInstall` | 123.541 ms (0.605 ms) | 2 cache hits |

The required production-path regression first reproduced the old behavior as three `DexMethodCache.runtimeKey()` calls and three real ZIP enumerations. With the cache enabled, the same diagnostic path measured three calls and one enumeration. The temporary counter used for those counts has been removed from production source; the committed tests exercise the production call chain, identity changes, concurrency and bounded capacity without a persistent measurement hook.

Final uninstrumented signed-package `am start -W` samples after force-stop:

| Package | Cold `TotalTime` samples | Cold median | Usable warm/resume sample |
| --- | ---: | ---: | ---: |
| Official 8.0.80 | 1,159 ms; 1,201 ms | 1,180 ms | 1,168 ms (`LaunchState=HOT`, same PID) |
| Modified 8.0.76 | 4,109 ms; 3,332 ms | 3,720.5 ms | 1,202 ms (`LaunchState=WARM`, same PID) |

The later warm attempts returned `LaunchState=UNKNOWN` and `TotalTime=0 ms`, so they are excluded. Cold launches completed and returned a process ID. These are small point-in-time samples, not a controlled comparison with C-0. The final modified package's 3.3–4.1 second cold sample includes WeChat's own startup work; no matched baseline isolates that variation to this change. No FATAL or ANR was found in the inspected C-1 launch logs. The logs do contain non-fatal existing feature errors, including preference-key length errors, SNS cache-store lookup failures, and early database-object lookup failures; they are not reported as startup crashes.

## Database message-change listener

### Root cause and APK evidence

C-0's `databaseHooks=31 wrapperHooks=0 wrapperInserts=0` was an early state in which `DexFinder.sqliteDbWrapperClass` was still null while DexKit resolution was warming. It did not establish that the wrapper's method shape had changed. The listener now reports non-operational until a uniquely selected wrapper has active insert, update and delete handles in the current `HookRegistry` generation.

DexSQL static comparisons used the official 8.0.80 and modified 8.0.76 APKs:

| APK | `MicroMsg.SqliteDB` candidates | `sql is null ` candidates | Joint anchor | Shape-validated candidate |
| --- | --- | --- | --- | --- |
| Official 8.0.80 | `ai5.d0` | `ai5.b0`, `ai5.d0` | `ai5.d0` | `ai5.d0` |
| Modified 8.0.76 | `ka5.b0` | `ka5.b0`, `ka5.z` | `ka5.b0` | `ka5.b0` |

The intersection rejects the error-string-only candidate instead of accepting the first string match. The locator also refuses selection if more than one joint candidate has a complete method shape. No obfuscated class name is hard-coded in production.

DexSQL shows that the official wrapper implements `gg5.k0`; message storage `g9` writes through the wrapper's `l/m/v(String, String, ContentValues): long` insert family, `p(String, ContentValues, String, String[]): int` update and `delete(String, String, String[]): int` delete paths. The modified wrapper implements `s85.k0`; message storage `f9` uses ORM insert/replace paths that call wrapper `m/w(String, String, ContentValues): long`, with the corresponding `q(...): int` update and delete paths. The return values and parameter shapes consumed by the listener are present in both APKs.

Temporary runtime diagnostics on both packages observed `sqliteDbWrapperClass=null` before DexKit warm-up, then the unique candidate above. The 8.0.76 diagnostic recorded 3 wrapper insert methods, 1 update, 1 delete and 31 generic hooks with `operational=true`; the official 8.0.80 diagnostic recorded the corresponding validated wrapper methods and `operational=true`. This confirms that the initial null was a readiness state and that the generic hook count was not used to claim operational status. The measurement-only diagnostic package was not retained or used as the final APK.

### Listener guarantees and remaining boundaries

- Active hook handles and their installation generation are checked on every operational-state query. Revoked registrations are removed from reported counts; a later generation can install again. A moved/re-resolved wrapper replaces stale wrapper hooks.
- Only validated wrapper insert/update/delete hooks make the listener operational. Generic WCDB/Android hooks remain a fallback and cannot set operational state by themselves.
- The wrapper and nested WCDB/SQLite layers are de-duplicated with per-thread mutation depth. Events are dispatched only after a successful outer call and successful result; write exceptions propagate and do not dispatch.
- Message SQL, `ContentValues` and business fields are not changed. Existing downstream consumers still hydrate only the message fields they request.
- Regression coverage includes both named and obfuscated wrapper shapes, false/ambiguous candidate rejection, both anchor intersection, wrapper relocation, repeated install, revoked generations, nested duplicate paths, failed/zero-row results, exception propagation and unchanged arguments/content.

On the final non-instrumented launch, the early startup log still showed `wrapper=null`/`operational=false` before resolver warm-up, as expected at that point. The final release did not retain diagnostic logging for the later operational state. Separately, the existing message-query API emitted `未找到 SqliteDB wrapper` during passive `.wx` launch; this is a live database-object lookup/hydration path, not the static locator's string-candidate selection. Therefore final signed-package live-message hydration and a naturally occurring real message-change dispatch are **NOT VERIFIED**. No message was sent, no database was written, and no end-to-end PASS is claimed.

## Build, install and scope

- `:app:testDebugUnitTest`, `:app:assembleDebug`, `:app:assembleRelease`, `:app:lintDebug` and `:app:lintRelease` passed. The release build ran R8.
- Final artifact: `dist/Hchat-release-signed.apk`; SHA-256 `d8dee040621bfe43062dcead7eb85941ed6e35109474ebed9b94a048e37c396b`.
- Signer certificate SHA-256: `5de821feb8cf9dc1be4f1a656664f5d4d88755465d390b0a32a4050d0922a3fd`.
- The APK was installed with `adb install -r`; the installed `/data/app/.../base.apk` SHA-256 matched the artifact.
- During `.wx` acceptance, LSPosed scope temporarily included `com.tencent.mm.wx`. It was restored afterward to the original state: `h.Hchat` enabled with only `com.tencent.mm` in scope. WeChat data and module state were preserved.
- The full repository `scripts/run_*tests.cjs` set and `scripts/tests/phase_b/run.js` passed. No 87-item feature run was started.
