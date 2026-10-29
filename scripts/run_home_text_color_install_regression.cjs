// Guards the production install path against the C-2A R1 #52 failure modes.
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

console.log('PASS: home text color install control flow remains retryable and independent');
