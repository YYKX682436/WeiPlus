import android.content.Context;
import android.content.pm.*;
import com.tencent.mm.boot.BuildConfig;
import h.Hchat.hooks.api.model.WeChatVersionInfo;
import h.Hchat.hooks.api.runtime.WeChatVersionApi;
import java.io.File;
import java.nio.file.*;

public class StartupVersionRegression {
 static final class Host extends Context {
  final File files;
  final ApplicationInfo applicationInfo = new ApplicationInfo();
  int fileReads;
  Host(Path root) throws Exception {
   files=Files.createDirectories(root.resolve("files")).toFile();
   applicationInfo.dataDir=root.toString();
   Path apk=root.resolve("host.apk");
   writeArchive(apk, new byte[]{1,2,3,4}, new byte[]{5,6,7});
   applicationInfo.sourceDir=apk.toString();
  }
  static void writeArchive(Path apk, byte[] first, byte[] second) throws Exception {
   try (java.util.zip.ZipOutputStream zip=new java.util.zip.ZipOutputStream(Files.newOutputStream(apk))) {
    zip.putNextEntry(new java.util.zip.ZipEntry("classes.dex")); zip.write(first); zip.closeEntry();
    zip.putNextEntry(new java.util.zip.ZipEntry("classes2.dex")); zip.write(second); zip.closeEntry();
   }
  }
  public String getPackageName() { return "com.tencent.mm"; }
  public PackageManager getPackageManager() { return new PackageManager(); }
 public ApplicationInfo getApplicationInfo() { return applicationInfo; }
  public File getFilesDir() { fileReads++; return files; }
 }
 static final class Loader extends ClassLoader {
  String metadata;
  boolean hideBuildConfig;
  Loader(String metadata) { super(StartupVersionRegression.class.getClassLoader()); this.metadata=metadata; }
  public String toString() { return metadata; }
  protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
   if (hideBuildConfig && name.equals("com.tencent.mm.boot.BuildConfig")) throw new ClassNotFoundException(name);
   return super.loadClass(name, resolve);
  }
 }
 static int checks;
 static void eq(Object expected, Object actual, String label) {
  checks++;
  if (!expected.equals(actual)) throw new AssertionError(label+": expected="+expected+", actual="+actual);
 }
 static void write(Path file, String value) throws Exception { Files.writeString(file, value); }
 public static void main(String[] args) throws Exception {
  Path root=Files.createTempDirectory(Path.of(args[0]), "runtime-");
  Host host=new Host(root);
  Path tinker=Files.createDirectories(root.resolve("tinker"));
  Path meta=tinker.resolve("patch.properties");
  Path patch=Files.createDirectories(tinker.resolve("patch-one"));
  write(meta, "patch.client.ver=300\nclient.ver=400\nNEW_TINKER_ID=file-new\nTINKER_ID=file-old\n");
  Loader loader=new Loader("clientVersion=200 TINKER_ID=loader-id intent_patch_new_version=loader-patch");
  BuildConfig.CLIENT_VERSION_ARM64="100";
  BuildConfig.CLIENT_VERSION="101";
  WeChatVersionInfo info=WeChatVersionApi.build(host,loader);
  eq(true,info.apkDexIdentity.contains("classes.dex") && info.apkDexIdentity.contains("classes2.dex"),"base DEX CRC/size identity");
  eq("100",info.clientVersion,"BuildConfig has highest priority");
  eq("loader-id",info.tinkerId,"loader tinker priority");
  eq("loader-patch",info.patchId,"loader patch priority");
  eq(0,host.fileReads,"complete loader metadata does not access Tinker directory");
  Path split=root.resolve("split.apk");
  Host.writeArchive(split,new byte[]{8,8},new byte[]{7});
  host.applicationInfo.splitSourceDirs=new String[]{split.toString()};
  WeChatVersionInfo splitInfo=WeChatVersionApi.build(host,loader);
  eq(true,splitInfo.apkDexIdentity.contains("split0{"),"split APK identity included");
  String priorDexIdentity=splitInfo.apkDexIdentity;
  Host.writeArchive(host.files.toPath().getParent().resolve("host.apk"),new byte[]{9,2,3,4},new byte[]{5,6,7});
  WeChatVersionInfo changedDex=WeChatVersionApi.build(host,loader);
  eq(false,priorDexIdentity.equals(changedDex.apkDexIdentity),"same-size DEX CRC change invalidates identity");
  info=changedDex;
  BuildConfig.CLIENT_VERSION_ARM64="";
  eq("101",WeChatVersionApi.build(host,loader).clientVersion,"second BuildConfig field");
  BuildConfig.CLIENT_VERSION="";
  BuildConfig.CLIENT_VERSION_INT="102";
  eq("102",WeChatVersionApi.build(host,loader).clientVersion,"third BuildConfig field");
  BuildConfig.CLIENT_VERSION_INT="";
  BuildConfig.CLIENTVERSION="103";
  eq("103",WeChatVersionApi.build(host,loader).clientVersion,"fourth BuildConfig field");
  BuildConfig.CLIENTVERSION="";
  eq("200",WeChatVersionApi.build(host,loader).clientVersion,"loader beats files");
  eq(0,host.fileReads,"all complete metadata builds skip file directory");
  loader.hideBuildConfig=true;
  eq("200",WeChatVersionApi.build(host,loader).clientVersion,"missing BuildConfig fallback");
  loader.metadata="plain-loader";
  info=WeChatVersionApi.build(host,loader);
  eq("300",info.clientVersion,"patch.client.ver beats client.ver");
  eq("file-new",info.tinkerId,"NEW_TINKER_ID beats TINKER_ID");
  eq("patch-one",info.patchId,"patch directory fallback");
  String before=info.cacheKey;
  String beforeHostIdentity=WeChatVersionApi.hostIdentityKey(info);
  write(meta,"patch.client.ver=301\nclient.ver=401\nNEW_TINKER_ID=changed\nTINKER_ID=old\n");
  info=WeChatVersionApi.build(host,loader);
  eq("301",info.clientVersion,"next build observes metadata update");
  eq("changed",info.tinkerId,"next build observes Tinker update");
  eq(true,before.equals(info.cacheKey),"patch metadata alone does not invalidate loaded-code cache identity");
  eq(beforeHostIdentity,WeChatVersionApi.hostIdentityKey(info),"patch metadata alone preserves approved host identity");
  write(meta,"client.ver=402\nTINKER_ID=only-old\n");
  info=WeChatVersionApi.build(host,loader);
  eq("402",info.clientVersion,"legacy client key fallback");
  eq("only-old",info.tinkerId,"legacy Tinker key fallback");
  WeChatVersionApi api=new WeChatVersionApi(host,loader,null);
  eq("402",api.current().clientVersion,"instance initial metadata");
  write(meta,"client.ver=403\nTINKER_ID=latest\n");
  eq("402",api.current().clientVersion,"cheap runtime cache is reused inside refresh interval");
  eq("403",api.refresh().clientVersion,"explicit runtime transition refresh reads metadata");
  loader.metadata="clientVersion=900 TINKER_ID=runtime-new intent_patch_old_version=runtime-patch";
  int reads=host.fileReads;
  eq("403",api.current().clientVersion,"cached identity stays stable until refresh boundary");
  eq("900",api.refresh().clientVersion,"forced runtime refresh observes loader mutation");
  eq(reads,host.fileReads,"runtime loader fallback skips disk");

  Path patchDexJar=patch.resolve("dex/classes.dex.jar");
  writePatchDexJar(patchDexJar,new byte[]{1,2,3,4});
  Loader activePatchLoader=new Loader("AndroidNClassLoader[DexPathList[[zip file \""+patchDexJar+"\", zip file \""+host.applicationInfo.sourceDir+"\"]]]");
  WeChatVersionInfo activePatch=WeChatVersionApi.build(host,activePatchLoader);
  eq(false,activePatch.runtimeCodeIdentity.equals("none"),"active Tinker DEX archive is detected from the running ClassLoader");
  eq(WeChatVersionApi.hostIdentityKey(info),WeChatVersionApi.hostIdentityKey(activePatch),"Tinker code generation remains separate from immutable host APK identity");
  eq(false,info.cacheKey.equals(activePatch.cacheKey),"active Tinker code change invalidates locator cache identity");
  String activeCode=activePatch.runtimeCodeIdentity;
  writePatchDexJar(patchDexJar,new byte[]{4,3,2,1});
  WeChatVersionInfo changedActivePatch=WeChatVersionApi.build(host,activePatchLoader);
  eq(false,activeCode.equals(changedActivePatch.runtimeCodeIdentity),"same-size active Tinker DEX content change changes runtime code identity");
  System.out.println("StartupVersionRegression: "+checks+" checks passed");
 }

 static void writePatchDexJar(Path path,byte[] dex) throws Exception {
  Files.createDirectories(path.getParent());
  try (java.util.zip.ZipOutputStream zip=new java.util.zip.ZipOutputStream(Files.newOutputStream(path))) {
   zip.putNextEntry(new java.util.zip.ZipEntry("classes.dex")); zip.write(dex); zip.closeEntry();
  }
 }
}
