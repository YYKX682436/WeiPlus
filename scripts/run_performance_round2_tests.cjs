// Focused regression gates for round 2. Keep the production source checks before JVM tests
// so the unmodified baseline reports both performance defects explicitly.
const fs = require('node:fs');
const path = require('node:path');
const { spawnSync } = require('node:child_process');
const root = path.resolve(__dirname, '..');
const read = p => fs.readFileSync(path.join(root, p), 'utf8');
const entry = read('app/src/main/java/h/Hchat/ModuleEntry.java');
const moments = read('app/src/main/java/h/Hchat/hooks/items/moments/MomentsAutoRefreshFeature.kt');
const silk = read('app/src/main/java/me/yun/silk/SilkCodec.java');
const mediaPage = read('app/src/main/java/h/Hchat/ui/miuix/MiuixMediaPages.kt');
const forward = read('app/src/main/java/h/Hchat/hooks/items/voiceforward/VoiceForwardFeature.kt');
const failures = [];
if (/nativeLibraryLoader\.loadSilkCodec\s*\(/.test(entry)) failures.push('module startup eagerly loads Silk without audio demand');
if (/scheduleWithFixedDelay\s*\(/.test(moments)) failures.push('Moments scheduler polls on a fixed delay instead of the next deadline');
if (!/public SilkCodec\(\)\s*\{\s*SilkLibrary\.ensureLoaded\(\)/.test(silk)) failures.push('Silk JNI construction does not ensure loading');
const save=mediaPage.slice(mediaPage.indexOf('    fun startSave('),mediaPage.indexOf('    fun launchSavePicker('));
if (save.indexOf('Thread({') < 0 || save.indexOf('audioTransformValidateInput(') < save.indexOf('Thread({')) {
  failures.push('save validation can perform first Silk loading on the UI thread');
}
const favorite=forward.slice(forward.indexOf('VoiceForwardMiuixDialog.showFavoriteVoiceSendConfirm('),
  forward.indexOf('                    onDismiss = {',forward.indexOf('VoiceForwardMiuixDialog.showFavoriteVoiceSendConfirm(')));
if (!/Thread\(\{[\s\S]*sendVoiceForForward\(source, target\)[\s\S]*main\.post/.test(favorite)) {
  failures.push('favorite voice send can perform first Silk loading on the UI thread');
}
for (const failure of failures) console.error('FAIL: ' + failure);
if (failures.length) process.exit(1);
const test = spawnSync('node', ['scripts/tests/performance_round2/run.cjs'], {cwd: root, stdio: 'inherit'});
if (test.error) throw test.error;
process.exit(test.status ?? 1);
