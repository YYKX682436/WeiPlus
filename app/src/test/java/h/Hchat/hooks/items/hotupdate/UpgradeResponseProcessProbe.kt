package h.Hchat.hooks.items.hotupdate

import java.io.File

/** Forked JVM exercise of the production file lock and marker. */
object UpgradeResponseProcessProbe {
    @JvmStatic fun main(args: Array<String>) {
        val dir = File(args[0])
        val identity = "com.tencent.mm|8.0.80|3220|client|tinker|patch|1700000000|dex-crc|patch-files|stable"
        UpgradeResponseEmptyCache(dir).withIdentity(identity, "v1") { state ->
            if (!state.isEmpty()) {
                File(dir, "queries").appendText("query\n")
                Thread.sleep(60)
                state.markEmpty()
            }
        }
    }
}
