package h.Hchat.hooks.api.contact;

/**
 * The six-argument WeChat onGYNetEnd callback is (netId, errType, errCode, message, network, data).
 * Keeping extraction here makes the otherwise easy-to-confuse leading network ID explicit.
 */
final class NativeSceneResponse {
    final int errorType;
    final int errorCode;

    private NativeSceneResponse(int errorType, int errorCode) {
        this.errorType = errorType;
        this.errorCode = errorCode;
    }

    static NativeSceneResponse fromArguments(Object[] args) {
        if (args == null || args.length != 6 || !(args[0] instanceof Number)
                || !(args[1] instanceof Number) || !(args[2] instanceof Number)) {
            return null;
        }
        return new NativeSceneResponse(
                ((Number) args[1]).intValue(),
                ((Number) args[2]).intValue());
    }

    boolean isAccepted() {
        return errorType == 0 && errorCode == 0;
    }
}
