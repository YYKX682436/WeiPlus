package h.Hchat.hooks.api.sns

import org.junit.Assert.assertEquals
import org.junit.Test

class NativeSnsScenePolicyTest {
    @Test
    fun `page policy separates network failure missing response and cursor state`() {
        assertEquals(
            NativeSnsPageState.READ_FAILED,
            NativeSnsPagePolicy.classify(callback(errType = 1, error = "offline"), emptyList()).state
        )
        assertEquals(
            NativeSnsPageState.READ_FAILED,
            NativeSnsPagePolicy.classify(callback(captured = false), emptyList()).state
        )
        assertEquals(
            NativeSnsPageState.READ_FAILED,
            NativeSnsPagePolicy.classify(callback(downAll = null), emptyList()).state
        )
        assertEquals(
            NativeSnsPageState.HAS_NEXT,
            NativeSnsPagePolicy.classify(callback(ids = listOf("1"), downAll = false), listOf("1")).state
        )
        assertEquals(
            NativeSnsPageState.COMPLETE,
            NativeSnsPagePolicy.classify(callback(ids = listOf("1"), downAll = true), listOf("1")).state
        )
        assertEquals(
            NativeSnsPageState.EMPTY,
            NativeSnsPagePolicy.classify(callback(ids = emptyList(), downAll = true), emptyList()).state
        )
    }

    @Test
    fun `page policy rejects count mismatch cached mapping gaps and duplicate server ids`() {
        assertEquals(
            NativeSnsPageState.READ_FAILED,
            NativeSnsPagePolicy.classify(callback(ids = listOf("1"), responseCount = 2), listOf("1")).state
        )
        assertEquals(
            NativeSnsPageState.READ_FAILED,
            NativeSnsPagePolicy.classify(callback(ids = listOf("1")), emptyList()).state
        )
        assertEquals(
            NativeSnsPageState.READ_FAILED,
            NativeSnsPagePolicy.classify(callback(ids = listOf("1", "1"), responseCount = 2), listOf("1")).state
        )
    }

    @Test
    fun `delete policy distinguishes not submitted rejected successful and uncertain states`() {
        assertEquals(
            NativeSnsDeleteDecisionKind.NOT_SUBMITTED,
            NativeSnsDeletePolicy.classify(false, null, false, true, null, null)
        )
        assertEquals(
            NativeSnsDeleteDecisionKind.UNCONFIRMED,
            NativeSnsDeletePolicy.classify(true, null, true, true, null, null)
        )
        assertEquals(
            NativeSnsDeleteDecisionKind.SERVER_REJECTED,
            NativeSnsDeletePolicy.classify(true, deleteCallback(NativeSnsDeleteCallbackKind.SERVER_REJECTED), false, true, null, null)
        )
        assertEquals(
            NativeSnsDeleteDecisionKind.SUCCESS,
            NativeSnsDeletePolicy.classify(true, deleteCallback(NativeSnsDeleteCallbackKind.SERVER_ACCEPTED), false, true, true, false)
        )
        assertEquals(
            NativeSnsDeleteDecisionKind.UNCONFIRMED,
            NativeSnsDeletePolicy.classify(true, deleteCallback(NativeSnsDeleteCallbackKind.SERVER_ACCEPTED), false, true, true, true)
        )
        assertEquals(
            NativeSnsDeleteDecisionKind.UNCONFIRMED,
            NativeSnsDeletePolicy.classify(true, deleteCallback(NativeSnsDeleteCallbackKind.SERVER_ACCEPTED), false, false, true, false)
        )
    }

    private fun callback(
        ids: List<String> = emptyList(),
        responseCount: Int = ids.size,
        captured: Boolean = true,
        downAll: Boolean? = true,
        errType: Int = 0,
        errCode: Int = 0,
        error: String = ""
    ) = NativeSnsPageCallback(ids, captured, responseCount, downAll, errType, errCode, error)

    private fun deleteCallback(kind: NativeSnsDeleteCallbackKind) =
        NativeSnsDeleteCallback(kind, 0, if (kind == NativeSnsDeleteCallbackKind.SERVER_ACCEPTED) 0 else 5, "server result")
}
