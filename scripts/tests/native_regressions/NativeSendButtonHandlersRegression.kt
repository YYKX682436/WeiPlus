package h.Hchat.hooks.items.sendbutton

fun main() {
    val errors = mutableListOf<String>()
    val handlers = NativeSendButtonHandlers { id, _ -> errors += id }
    var oldCalls = 0
    handlers.register("feature", { oldCalls++; false })
    var nextCalls = 0
    handlers.register("feature", { text -> text == "hello" })
    handlers.register("later", { nextCalls++; false })
    check(handlers.dispatch("hello"))
    check(oldCalls == 0 && nextCalls == 0)
    handlers.register("failure", { error("isolated failure") })
    handlers.register("fallthrough", { false })
    check(!handlers.dispatch("ordinary"))
    check(errors == listOf("failure"))
    handlers.clear()
    check(!handlers.dispatch("hello"))
    check(runCatching { handlers.register("", { false }) }.isFailure)
    println("PASS: native send handlers replace duplicate subscribers, short-circuit intercepts, isolate errors, and clear")
}
