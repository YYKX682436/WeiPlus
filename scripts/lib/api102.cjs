// Use the published API 102 declarations in JVM tests; no hand-written API stubs.
const fs = require('node:fs');
const path = require('node:path');
const os = require('node:os');
const {spawnSync} = require('node:child_process');
function prepare(output) {
    const cache = process.env.GRADLE_USER_HOME || path.join(os.homedir(), '.gradle');
    let api = process.env.LIBXPOSED_API_JAR;
    if (!api) {
        const dir = path.join(cache, 'caches/modules-2/files-2.1/io.github.libxposed/api/102.0.0');
        const aar = fs.existsSync(dir) && fs.readdirSync(dir).map(hash => path.join(dir, hash, 'api-102.0.0.aar')).find(fs.existsSync);
        if (!aar) throw Error('Resolve io.github.libxposed:api:102.0.0 with Gradle, or set LIBXPOSED_API_JAR to its published classes.jar');
        api = path.join(output, 'libxposed-api-102.0.0.jar');
        const result = spawnSync('unzip', ['-p', aar, 'classes.jar']);
        if (result.status !== 0) throw Error('Unable to extract the published API AAR');
        fs.writeFileSync(api, result.stdout);
    }
    const sdk = process.env.ANDROID_HOME || process.env.ANDROID_SDK_ROOT || path.join(os.homedir(), 'Library/Android/sdk');
    const platforms = fs.readdirSync(path.join(sdk, 'platforms')).filter(name => /^android-\d+(?:\.\d+)?$/.test(name)).sort((a,b) => parseInt(b.slice(8))-parseInt(a.slice(8)));
    const android = path.join(sdk, 'platforms', platforms[0], 'android.jar');
    return {api, android, classpath: [api, android].join(path.delimiter)};
}
module.exports = {prepare};
