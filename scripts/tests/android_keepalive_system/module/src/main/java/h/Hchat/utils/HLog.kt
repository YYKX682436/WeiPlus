package h.Hchat.utils

import android.util.Log

/** Test fixture logger; production keep-alive sources are otherwise compiled unchanged. */
object HLog {
    @JvmStatic
    fun e(message: String) = Log.e("WeiPlusR2", message)

    @JvmStatic
    fun e(message: String, throwable: Throwable?) = Log.e("WeiPlusR2", message, throwable)
}
