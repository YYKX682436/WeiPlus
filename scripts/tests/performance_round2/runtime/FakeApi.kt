package h.Hchat.hooks.api.core
import java.util.concurrent.atomic.AtomicInteger
object WeChatApis {
    val requests=AtomicInteger()
    @JvmStatic fun snsApi():Sns = Sns()
    class Sns { fun refreshTimeline():Boolean { requests.incrementAndGet(); return true } }
}
