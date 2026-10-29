// Guards the production install and page-retry paths against C-2A R1-R3 regressions.
const fs = require('node:fs');
const assert = require('node:assert/strict');

const source = fs.readFileSync(
  'app/src/main/java/h/Hchat/hooks/items/hometextcolor/HomeTextColorFeature.kt',
  'utf8'
);

function check(condition, message) {
  if (!condition) {
    console.error('FAIL: ' + message);
    process.exit(1);
  }
}

check(!/modernConversationUnavailable/.test(source),
  'the production modern-conversation locator still has a permanent unavailable latch');
check(/HomeTextColorInstallCoordinator\.isComplete/.test(source),
  'the primary install result must be decided by the independent home conversation paths');
check(/registry\.hookOnce\(/.test(source),
  'HomeTextColor hooks must reuse active HookRegistry handles');
check(/DexInstallScheduler\.Stage\.WARMUP/.test(source),
  'DexKit-backed lookup must wait for warmup and use the bounded scheduler');
check(/PAGE_INSTALL_ID/.test(source) && /cancel\(PAGE_INSTALL_ID\)/.test(source),
  'optional page hooks need their own scheduler key and lifecycle cancellation');
check(/installPrimary\(allowDexSearch = true\)/.test(source) && /installOptionalPages\(/.test(source),
  'primary conversation success must not consume optional page installation retries');

console.log('PASS: home text color install control flow remains retryable and independent');
