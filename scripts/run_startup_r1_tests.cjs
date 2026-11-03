const { spawnSync } = require('node:child_process');
const path = require('node:path');

const root = path.resolve(__dirname, '..');
const commands = [
  ['node', ['scripts/tests/startup_recovery/R1StartupSourceRegression.cjs']],
  ['node', ['scripts/tests/startup_recovery/run_feature_manager.cjs']],
  ['node', ['scripts/tests/startup_recovery/run_scheduler.cjs']]
];
for (const [command, args] of commands) {
  const result = spawnSync(command, args, { cwd: root, stdio: 'inherit' });
  if (result.error) throw result.error;
  if (result.status !== 0) process.exit(result.status || 1);
}
process.stdout.write('R1_STARTUP_REGRESSIONS 3/3 passed\n');
