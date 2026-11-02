const fs=require('node:fs'),os=require('node:os'),path=require('node:path');const {spawnSync}=require('node:child_process');
const root=path.resolve(__dirname,'../../../..'),out=fs.mkdtempSync(path.join(os.tmpdir(),'weiplus-silk-race-'));
try {
 for(const [cmd,args] of [
  ['javac',['-d',out,'scripts/tests/performance_round2/stubs/android/content/Context.java','scripts/tests/performance_round2/race/h/Hchat/loader/utils/NativeLibraryLoader.java','app/src/main/java/h/Hchat/loader/utils/NativeLoadCache.java','app/src/main/java/h/Hchat/loader/utils/SilkLibrary.java','app/src/main/java/me/yun/silk/SilkCodec.java','scripts/tests/performance_round2/race/SilkAsyncRaceRegression.java']],
  ['java',['-cp',out,'SilkAsyncRaceRegression']]
 ]) {const r=spawnSync(cmd,args,{cwd:root,stdio:'inherit'});if(r.error||r.status!==0)throw r.error||new Error(`${cmd} exited ${r.status}`);}
} finally {fs.rmSync(out,{recursive:true,force:true});}
