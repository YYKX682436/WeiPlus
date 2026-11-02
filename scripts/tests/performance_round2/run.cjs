const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const {spawnSync} = require('node:child_process');
const root = path.resolve(__dirname, '../../..');
const out = fs.mkdtempSync(path.join(os.tmpdir(), 'weiplus-round2-tests-'));
try {
  const files = [
    'scripts/tests/performance_round2/stubs/android/content/Context.java',
    'scripts/tests/performance_round2/stubs/h/Hchat/loader/utils/NativeLibraryLoader.java',
    'app/src/main/java/h/Hchat/loader/utils/SilkLibrary.java',
    'app/src/main/java/me/yun/silk/SilkCodec.java',
    'app/src/main/java/h/Hchat/hooks/items/moments/MomentsRefreshScheduler.java',
    'scripts/tests/performance_round2/SilkDemandRegression.java',
    'scripts/tests/performance_round2/SilkPrewarmRegression.java',
    'scripts/tests/performance_round2/SilkAsyncReadyRegression.java',
    'scripts/tests/performance_round2/MomentsRefreshSchedulerRegression.java',
    'scripts/tests/performance_round2/MomentsOverlapRegression.java'
  ];
  for (const [cmd,args] of [
    ['javac',['-d',out,...files]],
    ['java',['-cp',out,'SilkDemandRegression']],
    ['java',['-cp',out,'SilkPrewarmRegression']],
    ['java',['-cp',out,'SilkAsyncReadyRegression']],
    ['java',['-cp',out,'h.Hchat.hooks.items.moments.MomentsRefreshSchedulerRegression']],
    ['java',['-cp',out,'h.Hchat.hooks.items.moments.MomentsOverlapRegression']],
    ['node',['scripts/tests/startup/run.js']]
  ]) {
    const r=spawnSync(cmd,args,{cwd:root,stdio:'inherit'});
    if (r.error || r.status!==0) throw r.error || new Error(`${cmd} exited ${r.status}`);
  }
  const cache=path.join(os.homedir(),'.gradle/caches/modules-2/files-2.1');
  function jar(group,name,version) {
    const base=path.join(cache,group,name,version);
    for (const hash of fs.readdirSync(base)) {
      const file=path.join(base,hash,`${name}-${version}.jar`);
      if (fs.existsSync(file)) return file;
    }
    throw new Error(`Missing Kotlin test dependency: ${name} ${version}`);
  }
  const stdlib=jar('org.jetbrains.kotlin','kotlin-stdlib','2.4.20');
  const compiler=[jar('org.jetbrains.kotlin','kotlin-compiler-embeddable','2.4.20'),stdlib,
    jar('org.jetbrains.kotlin','kotlin-reflect','2.4.0'),
    jar('org.jetbrains.kotlin','kotlin-script-runtime','2.4.20'),
    jar('org.jetbrains.kotlinx','kotlinx-coroutines-core-jvm','1.8.0'),
    jar('org.jetbrains','annotations','13.0')];
  const runtime=path.join(out,'runtime'); fs.mkdirSync(runtime);
  const fixture='scripts/tests/performance_round2/runtime';
  const sources=fs.readdirSync(path.join(root,fixture)).filter(f=>f.endsWith('.kt')).map(f=>path.join(fixture,f));
  const checks=[
    ['javac',['-d',runtime,'app/src/main/java/h/Hchat/hooks/items/moments/MomentsRefreshScheduler.java']],
    ['java',['-cp',compiler.join(path.delimiter),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler',
      '-no-stdlib','-no-reflect','-classpath',[runtime,stdlib].join(path.delimiter),'-d',runtime,
      'app/src/main/java/h/Hchat/hooks/items/moments/MomentsAutoRefreshSettings.kt',
      'app/src/main/java/h/Hchat/hooks/items/moments/MomentsAutoRefreshFeature.kt',...sources]],
    ['java',['-cp',[runtime,stdlib].join(path.delimiter),'RuntimeRegressionKt']]
  ];
  for (const [cmd,args] of checks) {
    const r=spawnSync(cmd,args,{cwd:root,stdio:'inherit'});
    if (r.error || r.status!==0) throw r.error || new Error(`${cmd} exited ${r.status}`);
  }
  for (const script of ['run_redpacket_gate.cjs', 'run_transfer_gate.cjs', 'run_group_gate.cjs']) {
    const r=spawnSync('node',[path.join('scripts/tests/performance_round2/payment',script)],{cwd:root,stdio:'inherit'});
    if (r.error || r.status!==0) throw r.error || new Error(`${script} exited ${r.status}`);
  }
  const race=spawnSync('node',['scripts/tests/performance_round2/race/run.cjs'],{cwd:root,stdio:'inherit'});
  if (race.error || race.status!==0) throw race.error || new Error(`silk race exited ${race.status}`);
} finally { fs.rmSync(out,{recursive:true,force:true}); }
