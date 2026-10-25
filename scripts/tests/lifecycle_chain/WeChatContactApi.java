package h.Hchat.hooks.api.contact;
public final class WeChatContactApi {
 public boolean isGroup(String id){return id.endsWith("@chatroom");}
 public String getDisplayName(String id){return id;}
}
