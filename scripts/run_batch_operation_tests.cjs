const fs = require('node:fs');
const path = require('node:path');
const os = require('node:os');
const { spawnSync } = require('node:child_process');

const root = path.resolve(__dirname, '..');
const output = fs.mkdtempSync(path.join(os.tmpdir(), 'hchat-batch-operation-tests-'));

function run(command, args) {
    const result = spawnSync(command, args, { cwd: root, encoding: 'utf8', timeout: 60000 });
    process.stdout.write(result.stdout || '');
    process.stderr.write(result.stderr || '');
    if (result.error || result.status !== 0) {
        throw result.error || new Error(`Command failed: ${command}`);
    }
}

try {
    run(process.env.JAVAC || 'javac', [
        '-encoding', 'UTF-8', '-d', output,
        'app/src/main/java/h/Hchat/hooks/api/conversation/WeChatConversationIdentity.java',
        'app/src/main/java/h/Hchat/hooks/api/contact/ContactLabelIds.java',
        'app/src/main/java/h/Hchat/hooks/items/batch/BatchSelectionPolicy.java',
        'app/src/main/java/h/Hchat/hooks/items/batch/BatchStatePolicy.java',
        'app/src/main/java/h/Hchat/hooks/items/batch/BatchTaskRunner.java',
        'scripts/tests/batch_operations/BatchOperationRegression.java'
    ]);
    run(process.env.JAVA || 'java', ['-cp', output, 'BatchOperationRegression']);
} finally {
    fs.rmSync(output, { recursive: true, force: true });
}
