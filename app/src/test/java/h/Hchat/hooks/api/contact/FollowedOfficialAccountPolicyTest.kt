package h.Hchat.hooks.api.contact

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FollowedOfficialAccountPolicyTest {
    @Test
    fun `requires brand bit active contact relation and matching bizinfo`() {
        assertTrue(FollowedOfficialAccountPolicy.isFollowed(
            "gh_service", 8, 1, "gh_service", 1, "self"
        ))
        assertFalse(FollowedOfficialAccountPolicy.isFollowed(
            "gh_not_followed", 8, 0, "gh_not_followed", 1, "self"
        ))
        assertFalse(FollowedOfficialAccountPolicy.isFollowed(
            "gh_not_brand", 1, 1, "gh_not_brand", 1, "self"
        ))
        assertFalse(FollowedOfficialAccountPolicy.isFollowed(
            "gh_no_bizinfo", 8, 1, "", 1, "self"
        ))
        assertFalse(FollowedOfficialAccountPolicy.isFollowed(
            "gh_invalid_biz_type", 8, 1, "gh_invalid_biz_type", 3, "self"
        ))
    }

    @Test
    fun `excludes self groups and system-reserved accounts`() {
        assertFalse(FollowedOfficialAccountPolicy.isFollowed(
            "self", 8, 1, "self", 1, "self"
        ))
        assertFalse(FollowedOfficialAccountPolicy.isFollowed(
            "room@chatroom", 8, 1, "room@chatroom", 1, "self"
        ))
        assertFalse(FollowedOfficialAccountPolicy.isFollowed(
            "gh_43f2581f6fd6", 8, 1, "gh_43f2581f6fd6", 1, "self"
        ))
    }

    @Test
    fun `brand bit rather than any nonzero verify flag identifies public accounts`() {
        assertFalse(FollowedOfficialAccountPolicy.isFollowed(
            "gh_wrong_flag", 2, 1, "gh_wrong_flag", 1, "self"
        ))
        assertTrue(FollowedOfficialAccountPolicy.isFollowed(
            "brand_without_gh_prefix", 8, 1, "brand_without_gh_prefix", 0, "self"
        ))
    }
}
