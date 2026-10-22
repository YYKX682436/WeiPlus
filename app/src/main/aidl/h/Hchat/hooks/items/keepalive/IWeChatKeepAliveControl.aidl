package h.Hchat.hooks.items.keepalive;

/** The service validates Binder.getCallingUid() against the exact target package for every call. */
interface IWeChatKeepAliveControl {
    String registerTarget(
        String hostPackage,
        boolean watchdog,
        boolean networkHeartbeat
    );

    void removeTarget(String hostPackage);
}
