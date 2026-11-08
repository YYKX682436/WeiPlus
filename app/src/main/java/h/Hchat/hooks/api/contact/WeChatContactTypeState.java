package h.Hchat.hooks.api.contact;

/** Native contact.type state bits read from the WeChat 8.0.80 contact model. */
public final class WeChatContactTypeState {
    public static final int STARRED_FRIEND = 0x40;
    public static final int DO_NOT_LET_SEE_MOMENTS = 0x100;
    public static final int ONLY_CHAT = 0x800000;

    private WeChatContactTypeState() {}

    public static boolean isEnabled(int type, int flag) {
        return (type & flag) != 0;
    }

    public static boolean matches(int type, int flag, boolean enabled) {
        return isEnabled(type, flag) == enabled;
    }
}
