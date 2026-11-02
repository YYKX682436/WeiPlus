package android.os
class Bundle
object Build { object VERSION { const val SDK_INT=35 }; object VERSION_CODES { const val TIRAMISU=33 } }
object SystemClock { @JvmStatic fun elapsedRealtime():Long=System.nanoTime()/1_000_000 }
