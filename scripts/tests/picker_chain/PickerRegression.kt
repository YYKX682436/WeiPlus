import android.app.Activity
import android.content.Intent
import h.Hchat.hooks.core.XposedRuntime
import h.Hchat.hooks.items.custombottombar.CustomBottomBarIconPicker
import h.Hchat.hooks.items.custombottombar.CustomBottomBarIconPickResult
import h.Hchat.tests.api102.ChainFixture
import h.Hchat.tests.api102.FrameworkFixture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

fun main(){
 val framework=FrameworkFixture();XposedRuntime.initialize(framework.create())
 val resultMethod=Activity::class.java.getDeclaredMethod("onActivityResult",Int::class.javaPrimitiveType,Int::class.javaPrimitiveType,Intent::class.java)
 val destroyMethod=Activity::class.java.getDeclaredMethod("onDestroy")
 fun active()=framework.handles.filter{it.active}
 fun resultHook()=active().single{it.executable==resultMethod}
 fun cancel(activity:Activity){
  val chain=ChainFixture(resultMethod,activity,arrayOf(activity.requestCodes.last(),Activity.RESULT_CANCELED,null)){_,_->null}
  chain.run(resultHook().hooker);check(chain.proceeds==1)
 }
 val failed=Activity();val failedCallbacks=ArrayList<CustomBottomBarIconPickResult>()
 framework.failOn=destroyMethod
 CustomBottomBarIconPicker.launch(failed,"tab",failedCallbacks::add)
 check(failedCallbacks==listOf(CustomBottomBarIconPickResult.FAILED)&&failed.requestCodes.isEmpty()&&active().isEmpty())
 framework.failOn=null
 // A later registration fails while removal of the already installed result hook also fails.
 framework.failOn=destroyMethod
 framework.afterRegistration=Runnable { framework.handles.last().fail=true }
 val combined=Activity();val combinedCallbacks=ArrayList<CustomBottomBarIconPickResult>()
 CustomBottomBarIconPicker.launch(combined,"tab",combinedCallbacks::add)
 framework.afterRegistration=null;framework.failOn=null
 check(combinedCallbacks==listOf(CustomBottomBarIconPickResult.FAILED)&&combined.requestCodes.isEmpty())
 val residual=active().single();check(XposedRuntime.failedUnhookCount()==1)
 val recovery=Activity();val recoveryCallbacks=ArrayList<CustomBottomBarIconPickResult>()
 CustomBottomBarIconPicker.launch(recovery,"tab",recoveryCallbacks::add)
 val inert=ChainFixture(resultMethod,recovery,arrayOf(recovery.requestCodes.last(),Activity.RESULT_CANCELED,null)){_,_->"native"}
 check(inert.run(residual.hooker)=="native"&&inert.proceeds==1&&recoveryCallbacks.isEmpty())
 residual.fail=false
 val currentResult=active().single{it!==residual&&it.executable==resultMethod}
 val canceled=ChainFixture(resultMethod,recovery,arrayOf(recovery.requestCodes.last(),Activity.RESULT_CANCELED,null)){_,_->null}
 canceled.run(currentResult.hooker)
 check(canceled.proceeds==1&&recoveryCallbacks==listOf(CustomBottomBarIconPickResult.CANCELLED)&&active().isEmpty()&&XposedRuntime.failedUnhookCount()==0)
 println("PASS: picker combined installation/removal failure retains inert handle across retry and cancellation removes every callback")
 val unavailable=Activity().apply{failLaunch=true};val unavailableCallbacks=ArrayList<CustomBottomBarIconPickResult>()
 CustomBottomBarIconPicker.launch(unavailable,"tab",unavailableCallbacks::add)
 check(unavailableCallbacks==listOf(CustomBottomBarIconPickResult.FAILED)&&active().isEmpty())
 val nativeFailure=IllegalStateException("host result failed");val owner=Activity();val callbacks=ArrayList<CustomBottomBarIconPickResult>()
 CustomBottomBarIconPicker.launch(owner,"tab",callbacks::add)
 val oldHook=resultHook();val code=owner.requestCodes.last()
 val failedChain=ChainFixture(resultMethod,owner,arrayOf(code,Activity.RESULT_CANCELED,null)){_,_->throw nativeFailure}
 check(runCatching{failedChain.run(oldHook.hooker)}.exceptionOrNull()===nativeFailure&&failedChain.proceeds==1&&active().isEmpty()&&callbacks.isEmpty())
 val entered=CountDownLatch(1);val proceed=CountDownLatch(1);val cleanupDone=CountDownLatch(1);val cleanupStarted=CountDownLatch(1)
 framework.afterRegistration=Runnable{framework.afterRegistration=null;entered.countDown();check(proceed.await(5,TimeUnit.SECONDS))}
 val next=Activity();val nextCallbacks=ArrayList<CustomBottomBarIconPickResult>();val launchError=AtomicReference<Throwable>()
 val launch=Thread{try{CustomBottomBarIconPicker.launch(next,"tab",nextCallbacks::add)}catch(error:Throwable){launchError.set(error)}}
 val idle=CustomBottomBarIconPicker::class.java.getDeclaredMethod("releaseHooksIfIdle").apply{isAccessible=true}
 launch.start();check(entered.await(5,TimeUnit.SECONDS))
 val cleanup=Thread{cleanupStarted.countDown();idle.invoke(CustomBottomBarIconPicker);cleanupDone.countDown()}
 cleanup.start();check(cleanupStarted.await(5,TimeUnit.SECONDS))
 check(!cleanupDone.await(150,TimeUnit.MILLISECONDS)){"Idle teardown entered between hook installation and pending ownership"}
 proceed.countDown();launch.join(5000);cleanup.join(5000)
 check(!launch.isAlive&&!cleanup.isAlive&&launchError.get()==null&&active().size==2&&next.requestCodes.size==1)
 val stale=ChainFixture(resultMethod,next,arrayOf(next.requestCodes.last(),Activity.RESULT_CANCELED,null)){_,_->null}
 stale.run(oldHook.hooker);check(nextCallbacks.isEmpty()&&active().size==2)
 cancel(next);check(nextCallbacks==listOf(CustomBottomBarIconPickResult.CANCELLED)&&active().isEmpty())
 println("PASS: actual picker launch rolls back partial installation, releases unavailable/throwing requests, serializes idle teardown with pending ownership and rejects stale generations")
}
