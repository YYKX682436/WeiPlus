package h.Hchat.hooks.items.hchatextra

private fun assertThat(value: Boolean, message: String) {
    check(value) { message }
}

fun main() {
    val original = Any()
    val sameNativeRow = Any()
    val a = MessageDetailsIdentity("filehelper", 41L, 9001L, 1_800_000_000_000L, 1, true, original)
    val sameMessage = MessageDetailsIdentity("filehelper", 41L, 9001L, 1_800_000_000_000L, 1, true, sameNativeRow)
    val b = MessageDetailsIdentity("filehelper", 42L, 9002L, 1_800_000_001_000L, 1, true, Any())
    assertThat(a.sameMessage(sameMessage), "stable scalar identity must survive native object replacement")
    assertThat(!a.sameMessage(b), "A to B holder reuse must change message identity")
    val serverIdResolved = MessageDetailsIdentity("filehelper", 41L, 9200L, 1_800_000_000_000L, 1, true, original)
    assertThat(a.sameMessage(serverIdResolved), "server ID resolution after send must retain the local message identity")
    val serverIdOnlyA = MessageDetailsIdentity("filehelper", 0L, 9001L, 1_800_000_000_000L, 1, true, original)
    val serverIdOnlyAAgain = MessageDetailsIdentity("filehelper", 0L, 9001L, 1_800_000_000_000L, 1, true, sameNativeRow)
    assertThat(serverIdOnlyA.sameMessage(serverIdOnlyAAgain), "server ID must identify a row across native object replacement")

    val idlessSame = MessageDetailsIdentity("filehelper", 0L, 0L, 0L, 1, true, original)
    val idlessSameAgain = MessageDetailsIdentity("filehelper", 0L, 0L, 0L, 1, true, original)
    val idlessDifferent = MessageDetailsIdentity("filehelper", 0L, 0L, 0L, 1, true, sameNativeRow)
    assertThat(idlessSame.sameMessage(idlessSameAgain), "idless repeat bind must match only the same fallback object")
    assertThat(!idlessSame.sameMessage(idlessDifferent), "idless distinct messages must not alias")
    val typeChanged = MessageDetailsIdentity("filehelper", 41L, 9001L, 1_800_000_000_000L, 3, true, original)
    assertThat(!a.sameMessage(typeChanged), "message type change must invalidate binding identity")

    val generation = MessageDetailsBindingGeneration()
    val oldCallback = generation.token()
    generation.invalidate()
    val currentCallback = generation.token()
    assertThat(!generation.isCurrent(oldCallback), "A callback must be invalid after holder reuse")
    assertThat(generation.isCurrent(currentCallback), "new binding callback must remain current")

    val shortRow = MessageDetailsAvatarLayout.requiredRowBottom(170, 159, 35, 0, 4)
    assertThat(shortRow == 198, "short message must reserve only the missing label height and trailing space")
    assertThat(MessageDetailsAvatarLayout.additionalRowHeight(174, 170, 159, 35, 0, 4) == 24,
        "short row must grow by the exact deficit once")
    val tallRow = MessageDetailsAvatarLayout.requiredRowBottom(600, 159, 35, 0, 4)
    assertThat(tallRow == 604, "tall content must dominate label height")
    assertThat(MessageDetailsAvatarLayout.additionalRowHeight(604, 600, 159, 35, 0, 4) == 0,
        "tall message must not receive duplicate label height")
    assertThat(MessageDetailsAvatarLayout.requiredRowBottom(170, 159, 35, 6, 4) == 204,
        "configured gap must be applied once")
    assertThat(MessageDetailsAvatarLayout.avatarBranchTopOffset(0, 0, 40, 0) == 40,
        "avatar-above must reserve one measured label height on the native row branch")
    assertThat(MessageDetailsAvatarLayout.avatarBranchTopOffset(12, 30, 40, 4) == 26,
        "avatar-above reserve must account for existing padding and available space")
    assertThat(MessageDetailsAvatarLayout.avatarBranchTopOffset(12, 60, 40, 4) == 0,
        "avatar-above must not move a branch when space is sufficient")

    println("message-details binding identity and row geometry regressions passed")
}
