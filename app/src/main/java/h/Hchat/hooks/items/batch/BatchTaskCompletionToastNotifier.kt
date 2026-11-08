package h.Hchat.hooks.items.batch

import h.Hchat.hooks.api.core.WeChatApis

/** Uses the host application's notification API and never retains an Activity or Compose object. */
internal object BatchTaskCompletionToastNotifier : BatchTaskCompletionNotifier {
    override fun notify(snapshot: BatchTaskSnapshot) {
        WeChatApis.notifyApi()?.showToast(BatchTaskCompletionMessage.from(snapshot))
    }
}

internal object BatchTaskCompletionMessage {
    fun from(snapshot: BatchTaskSnapshot): String {
        val report = snapshot.report
        if (snapshot.status == BatchTaskStatus.FAILED || report == null) return "批量操作失败"
        if (snapshot.status == BatchTaskStatus.STOPPED) {
            return if (report.failed > 0 || report.unconfirmed > 0) {
                "已停止后续操作（失败 ${report.failed}，未确认 ${report.unconfirmed}）"
            } else {
                "已停止后续操作"
            }
        }
        if (report.failed == 0 && report.unconfirmed == 0 && report.skipped == 0 &&
            report.success == report.total
        ) {
            return "批量操作成功"
        }
        if (report.success == 0 && report.failed > 0 && report.unconfirmed == 0) {
            return "批量操作失败：${report.failed} 项"
        }
        if (report.failed == 0 && report.unconfirmed == 0) {
            return if (report.success == 0) {
                "批量操作完成，跳过 ${report.skipped} 项"
            } else {
                "批量操作完成：成功 ${report.success}，跳过 ${report.skipped}"
            }
        }
        return "批量操作部分完成：成功 ${report.success}，失败 ${report.failed}，未确认 ${report.unconfirmed}"
    }
}
