const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { spawnSync } = require('node:child_process');
const root = path.resolve(__dirname, '..');
const directory = fs.mkdtempSync(path.join(os.tmpdir(), 'hchat-step-policy-'));
try {
    for (const [cmd, args] of [[process.env.JAVAC || 'javac', ['-encoding', 'UTF-8', '-d', directory,
        'app/src/main/java/h/Hchat/hooks/items/sportstep/StepMultiplierPolicy.java',
        'app/src/main/java/h/Hchat/hooks/items/sportstep/StepRequestState.java',
        'app/src/main/java/h/Hchat/hooks/items/sportstep/StepScopeKey.java',
        'scripts/tests/sportstep/StepMultiplierPolicyRegression.java',
        'scripts/tests/sportstep/StepRequestStateRegression.java',
        'scripts/tests/sportstep/StepScopeKeyRegression.java']],
        [process.env.JAVA || 'java', ['-cp', directory, 'StepMultiplierPolicyRegression']],
        [process.env.JAVA || 'java', ['-cp', directory, 'StepRequestStateRegression']],
        [process.env.JAVA || 'java', ['-cp', directory, 'StepScopeKeyRegression']]]) {
        const result = spawnSync(cmd, args, { cwd: root, encoding: 'utf8', timeout: 60000 });
        process.stdout.write(result.stdout || ''); process.stderr.write(result.stderr || '');
        if (result.error || result.status !== 0) throw result.error || new Error(`${cmd} failed`);
    }
} finally { fs.rmSync(directory, { recursive: true, force: true }); }
