package h.Hchat.hooks.items.batch

import h.Hchat.hooks.api.core.WeChatApis
import h.Hchat.utils.HLog

/** Uses the host application's notification API and never retains an Activity or Compose object. */
internal object BatchTaskCompletionToastNotifier : BatchTaskCompletionNotifier {
    override fun onStarted() {
        WeChatApis.notifyApi()?.showToast("已开始执行")
    }

    override fun notify(snapshot: BatchTaskSnapshot) {
        if (snapshot.operation == BatchOperation.UNFOLLOW_OFFICIAL_ACCOUNTS) {
            val report = snapshot.report
            HLog.i("取关诊断 stage=notification status=${snapshot.status} " +
                "total=${report?.total ?: snapshot.total} success=${report?.success ?: 0} " +
                "failed=${report?.failed ?: 0} skipped=${report?.skipped ?: 0} " +
                "unconfirmed=${report?.unconfirmed ?: 0} reason=${report?.stopReason ?: snapshot.failure}")
        }
        BatchTaskCompletionMessage.from(snapshot)?.let { WeChatApis.notifyApi()?.showToast(it) }
    }
}

internal object BatchTaskCompletionMessage {
    fun from(snapshot: BatchTaskSnapshot): String? = when {
        snapshot.status == BatchTaskStatus.FAILED || (snapshot.report?.failed ?: 0) > 0 -> "操作失败"
        else -> null
    }
}
