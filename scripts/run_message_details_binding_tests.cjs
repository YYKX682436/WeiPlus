// Exercises the production binding/geometry policy and guards the renderer against regressions.
const fs = require('node:fs');
const path = require('node:path');
const os = require('node:os');
const { spawnSync, execFileSync } = require('node:child_process');
const lib = process.argv[2] || process.env.KOTLIN_LIB_DIR;
if (!lib) throw new Error('Pass the directory containing Kotlin compiler and stdlib JARs.');
const root = path.resolve(__dirname, '..');
const packageDir = path.join(root, 'app/src/main/java/h/Hchat/hooks/items/hchatextra');
const modelFile = path.join(packageDir, 'MessageDetailsLayoutModel.kt');
const bindingIndexFile = path.join(packageDir, 'MessageDetailsBindingIndex.kt');
const productionFile = path.join(packageDir, 'HchatExtraFeature.kt');
const testFile = path.join(root, 'scripts/tests/message_details_binding/BindingRegression.kt');
const jars = fs.readdirSync(lib).filter(name => /^(kotlin-compiler-embeddable-|kotlin-stdlib-|kotlin-reflect-|kotlin-script-runtime-|kotlinx-coroutines-core-jvm-|annotations-|trove4j-).*\.jar$/.test(name));
const stdlib = jars.find(name => /^kotlin-stdlib-[0-9]/.test(name));
if (!stdlib || !jars.some(name => name.startsWith('kotlin-compiler-embeddable-'))) throw new Error('Missing Kotlin compiler or stdlib');
const output = fs.mkdtempSync(path.join(os.tmpdir(), 'hchat-message-details-binding-tests-'));
process.once('exit', () => fs.rmSync(output, { recursive: true, force: true }));
const jar = path.join(output, 'binding.jar');
const java = process.env.JAVA || 'java';
function run(args, expected = 0) {
  const result = spawnSync(java, args, { cwd: root, encoding: 'utf8', timeout: 60_000 });
  if (result.stdout) process.stdout.write(result.stdout);
  if (result.stderr) process.stderr.write(result.stderr);
  if (result.error || result.status !== expected) throw result.error || new Error(`JVM check returned ${result.status}, expected ${expected}`);
}
function compile(source) {
  run(['-Xmx256m', '-cp', jars.map(name => path.join(lib, name)).join(path.delimiter),
    'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler', '-no-stdlib', '-no-reflect',
    '-classpath', path.join(lib, stdlib), '-d', jar, ...[].concat(source), testFile]);
  run(['-cp', jar + path.delimiter + path.join(lib, stdlib), 'h.Hchat.hooks.items.hchatextra.BindingRegressionKt']);
}
function body(source, name, nextName) {
  const start = source.search(new RegExp(`(?:private )?fun ${name}\\(`));
  if (start < 0) throw new Error(`Missing production function ${name}`);
  const end = nextName ? source.search(new RegExp(`(?:private )?fun ${nextName}\\(`, 'g')) : source.length;
  if (end < 0) throw new Error(`Missing next function ${nextName}`);
  return source.slice(start, end);
}
function assertRenderer(source, layoutSource, modelSource) {
  const capture = body(source, 'captureMessageDetailsBindState', 'hookOnce');
  if (/removeTaggedMessageDetailsViews|removeMessageDetailsLabel|\.removeView\(/.test(capture)) throw new Error('bind capture must preserve a reusable label');
  if (!capture.includes('messageDetailsRowLabels.get(root)')) throw new Error('bind identity checks must use the row label index before any hierarchy fallback');
  const avatar = body(source, 'addAvatarDetailsView', 'ownedAvatarBranchOffset');
  if (!avatar.includes('MessageDetailsAvatarLayout.placement') || !avatar.includes('setOwnedAvatarBranchOffset')) throw new Error('avatar label must use measured geometry and reserve above space through the owned native branch');
  if (/\.layout\s*\(|scheduleAvatarDetailsPosition|setPadding\(|RelativeLayout\.(?:BELOW|ABOVE)/.test(avatar)) throw new Error('avatar renderer contains manual positioning, fixed padding, or anchor-relative constraints that bypass row measurement');
  if (!avatar.includes('label.measure(') || !avatar.includes('label.measuredHeight') || !avatar.includes('MessageDetailsAvatarViewLayout.attach')) throw new Error('avatar renderer must measure and attach the label as a native row child');
  if (!layoutSource.includes('addRule(RelativeLayout.ALIGN_PARENT_TOP)') || !layoutSource.includes('parent.addView(label, params)')) throw new Error('avatar label must attach through native top alignment so RelativeLayout measures row wrap-content');
  if (!modelSource.includes('max(nativeContentBottom, avatarBottom + labelHeight') || !modelSource.includes('requiredRowBottom')) throw new Error('avatar label must contribute only the missing amount to the native row boundary');
  if (!layoutSource.includes('current?.topMargin != value.appliedTopMargin')) throw new Error('module spacing must not overwrite host-updated branch layout');
  const style = body(source, 'applyMessageDetailsLabel', 'applyForwardedRecordLabel');
  if (!style.includes('label.includeFontPadding = false') || !style.includes('label.setPadding(0, 0, 0, 0)')) throw new Error('message label must use compact font metrics without fixed padding');
  const config = body(source, 'applyPendingMessageDetailsConfig', 'destroy');
  const click = config.indexOf('if (clickChanged)');
  const color = config.indexOf('if (colorsChanged)');
  const layout = config.indexOf('if (displayOrLayoutChanged');
  if (click < 0 || color < 0 || config.slice(color, click).includes('ChatRowRefresh.request') || config.slice(click).includes('ChatRowRefresh.request')) throw new Error('color/click updates must not request adapter rebinding');
  if (layout < 0 || config.slice(color, click).includes('refreshAttachedMessageDetailsLayouts') || config.slice(click, layout).includes('refreshAttachedMessageDetailsLayouts')) throw new Error('color/click updates must not request row geometry work');
  const identity = body(source, 'messageDetailsIdentity', 'messageDetailsRenderKey');
  if (!identity.includes('messageId(message)') || !identity.includes('serverId =') || !identity.includes('fallbackMessage =')) throw new Error('binding identity must use stable scalar IDs and an idless fallback');
  const clickBehavior = body(source, 'updateMessageDetailsClickBehavior', 'observeCardDetailsLayout');
  if (!clickBehavior.includes('MessageDetailsClickBinding.update') || !clickBehavior.includes('includeContent = true')) throw new Error('detail click must resolve full details from the current binding');
  const bind = body(source, 'bindBottomMessageDetails', 'resolveMessageFromBindArgs');
  if (!bind.includes('nativeTimeLabel?.setOnClickListener(null)')) throw new Error('host time-view cleanup must remain explicitly scoped to the native time label');
  const details = body(source, 'messageDetails', 'showMessageDetailsDialog');
  if (!details.includes('if (needsContent)') || !details.includes('"time" in tokens')) throw new Error('default time display must not read message content');
  if (!source.includes('relativeMinute = if ("relativeTime" in config.tokens)') || !source.includes('scheduleMessageDetailsRelativeTimeRefresh')) throw new Error('relative-time cache must expire and refresh with the active chat page');
  const formatter = body(source, 'timeFormatter', 'applyMessageDetailsLabel');
  if (!formatter.includes('locale.toLanguageTag()') || !formatter.includes('DateTimeFormatter.ofPattern(pattern, locale)')) throw new Error('time formatter cache must follow current locale and pattern');
  const renderKey = body(source, 'messageDetailsRenderKey', 'scheduleMessageDetailsRelativeTimeRefresh');
  if (!renderKey.includes('zoneId = zone?.id') || !renderKey.includes('localeTag =')) throw new Error('display cache must invalidate for time zone and locale changes');
  if (!source.includes('Intent.ACTION_TIMEZONE_CHANGED') || !source.includes('Intent.ACTION_LOCALE_CHANGED') || !source.includes('Intent.ACTION_TIME_CHANGED')) throw new Error('active chat must refresh cached labels when clock inputs change');
  const page = body(source, 'installMessageDetails', 'hookOnce');
  if (!page.includes('unregisterMessageDetailsClockReceiver()') || !page.includes('registerMessageDetailsClockReceiver()')) throw new Error('clock refresh receiver must follow the chat page lifecycle');
  if (!page.includes('if (messageDetailsPageActive)') || !page.includes('ChatRowRefresh.requestAfterTransition()')) throw new Error('a chat already open before hook installation must get one coalesced current-chat refresh');
  const initialRefresh = body(source, 'scheduleMessageDetailsInitialPageRefresh', 'destroy');
  if (!initialRefresh.includes('postDelayed') || !source.includes('MESSAGE_DETAILS_INITIAL_PAGE_REFRESH_DELAY_MS = 500L') || !source.includes('removeCallbacks(messageDetailsInitialPageRefresh)')) throw new Error('late page state must use one bounded refresh and cancel it on lifecycle changes');
  const timeFormat = body(source, 'formatMessageDetailsLabel', 'relativeMessageTime');
  if (!timeFormat.includes('createTime?.let') || !timeFormat.includes('}.orEmpty()') || !timeFormat.includes('.atZone(ZoneId.systemDefault())')) throw new Error('missing createTime must remain empty, while long patterns receive zone-aware date fields');
  const insert = body(source, 'insertMessageDetailsLabel', 'invalidateMessageDetailsBinding');
  if (insert.includes('messageDetailsBindings.snapshot()') || insert.includes('findFirstByRoot')) throw new Error('normal label lookup must not scan the global binding index');
  if (!insert.includes('messageDetailsRowLabels.get(root)')) throw new Error('normal label lookup must use the current row index');
  if (!insert.includes('MessageDetailsDisplayInputs.capture') || !insert.includes('MessageDetailsDisplayReusePolicy.canReuse') || !insert.includes('reuseDisplay')) throw new Error('display reuse must compare the current token inputs and render configuration');
  if (!insert.includes('clearExcludedMessageDetails(root)')) throw new Error('empty/missing timestamp must cancel deferred work and restore owned row geometry');
  const labelStyle = body(source, 'applyMessageDetailsLabel', 'applyForwardedRecordLabel');
  if (!labelStyle.includes('if (textChanged)') || !labelStyle.includes('label.setTextSize')) throw new Error('label updates must write only changed display fields');
  const clear = body(source, 'clearMessageDetailsRows', 'refreshAttachedMessageDetailsLabels');
  if (!clear.includes('restoreAllOwnedAvatarBranchOffsets')) throw new Error('row cleanup must restore module-owned geometry');
}

const source = fs.readFileSync(productionFile, 'utf8');
const layoutSource = fs.readFileSync(path.join(packageDir, 'MessageDetailsAvatarViewLayout.kt'), 'utf8');
const modelSource = fs.readFileSync(modelFile, 'utf8');
assertRenderer(source, layoutSource, modelSource);
if (!layoutSource.includes('fun disable(label: TextView)') || !layoutSource.includes('fun clear(label: TextView, bindingTag: Int)')) throw new Error('click disable and label removal must have separate binding lifecycles');
if (!modelSource.includes('val message = current.nativeMessage ?: return null')) throw new Error('an unresolved recycled message must not fall back to old details');
compile([modelFile, bindingIndexFile]);

// Prove these assertions reject the reviewed production implementation.
const baseline = process.env.WEIPLUS_MESSAGE_DETAILS_BASELINE;
if (baseline) {
  const oldSource = execFileSync('git', ['show', `${baseline}:app/src/main/java/h/Hchat/hooks/items/hchatextra/HchatExtraFeature.kt`], { cwd: root, encoding: 'utf8' });
  let rejected = false;
  try { assertRenderer(oldSource, layoutSource, modelSource); } catch (_) { rejected = true; }
  if (!rejected) throw new Error(`Regression guards did not reject baseline ${baseline}`);
  console.log(`old production implementation rejected: ${baseline}`);
}
console.log(`Test artifacts: ${output}`);
