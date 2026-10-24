package h.Hchat.utils
object HLog { val failures=java.util.concurrent.CopyOnWriteArrayList<Throwable>(); fun e(message: String, error: Throwable) { failures.add(error) } }
