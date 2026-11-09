package h.Hchat.hooks.api.contact;

/**
 * Mirrors WeChat 8.0.80's followed brand-contact query: a live contact relation,
 * the brand-account verify bit, and a matching bizinfo row are all required.
 */
public final class FollowedOfficialAccountPolicy {
    public static final int VERIFY_FLAG_BRAND_ACCOUNT = 8;
    public static final int TYPE_IN_CONTACTS = 1;

    private FollowedOfficialAccountPolicy() {}

    public static boolean isFollowed(
            String username,
            int verifyFlag,
            int contactType,
            String bizInfoUsername,
            int bizInfoType,
            String selfUsername) {
        String user = clean(username);
        if (user.isEmpty()
                || user.equals(clean(selfUsername))
                || !user.equals(clean(bizInfoUsername))
                || bizInfoType < 0
                || bizInfoType > 2
                || isSystemReserved(user)
                || user.endsWith("@chatroom")
                || user.endsWith("@im.chatroom")) {
            return false;
        }
        return (verifyFlag & VERIFY_FLAG_BRAND_ACCOUNT) != 0
                && (contactType & TYPE_IN_CONTACTS) != 0;
    }

    public static boolean isSystemReserved(String username) {
        switch (clean(username)) {
            // Mirrors built-in contacts filtered from WeChat 8.0.80's BrandService list.
            case "gh_3dfda90e39d6": // Contact.z3.V4
            case "gh_b4af18eac3d5": // Contact.z3.X4
            case "gh_f0a92aa7146c": // Contact.z3.W4
            case "gh_e087bb5b95e6": // Contact.z3.X4
            case "gh_051d9102de63": // Contact.z3.r4
            case "gh_43f2581f6fd6": // BrandService list filter
            case "weixin":
            case "gh_9639b5a92773": // Contact.z3.A4
                return true;
            default:
                return false;
        }
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
