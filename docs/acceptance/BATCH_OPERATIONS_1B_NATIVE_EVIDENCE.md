# Batch operations 1B: WeChat native evidence

## Source build

The inspection target was the installed official `com.tencent.mm` package, version 8.0.80, versionCode 3220. DexSQL inspected the APK pulled read-only from that installation (`/tmp/WeiPlus_1B_WeChat_8.0.80_3220.apk`, dataset input `dataset-3`). The `file_id` and `method_idx` values below are DexSQL locations in that APK; method descriptors identify the exact signatures.

The implementation reuses `WeChatApis` and the existing `DexFinder`/RoomSDK service getter. It does not write state bits directly to imitate a server update.

## Star friend

- The native profile menu listener `Lrz3/fb;->onMMMenuItemSelected(Landroid/view/MenuItem;I)V` (DexSQL file 2, method 57583) obtains the current `com.tencent.mm.storage.z3` from `NormalUserFooterPreference.J`. The selected menu entry calls `w41.e2.r0(z3)` to add a star or `w41.e2.C0(z3)` to remove it.
- `Lcom/tencent/mm/contact/s;->t2()Z` (file 2, method 6226) reads only `type & 0x40`.
- `Lw41/e2;->r0(Lcom/tencent/mm/storage/z3;)V` (file 2, method 61614) sets `0x40`; `C0(...)V` (file 2, method 61544) clears `0x40`. Both persist through the native contact storage path and call `j0(z3,true)`.
- `Lw41/e2;->j0(Lcom/tencent/mm/storage/z3;Z)V` (file 2, method 61598) updates the native contact storage and queues `Lip3/p0` with command 2 and payload from `e2.n(z3)`. This route has no response callback in the inspected path.
- WeiPlus reads the candidate's current contact row and native `t2()` state before calling the native helper. It reports the operation as submitted/unconfirmed because a local type update is not proof of server synchronization.

## Friend permissions

- “仅聊天／普通朋友”: `OnlyChatContactMgrUI.onActivityResult(I,I,Intent)V` (file 12, method 28984) passes selected usernames to `q61.i0(List, int, int, int)`. The scene's type argument is `0x800000`; operation 1 enables only-chat and 0 disables it. `q61.i0.onGYNetEnd(...)V` (file 11, method 50844) consumes per-user response entries and updates only the requested contact type field after server response. WeiPlus submits one selected friend per scene and rereads that bit after callback.
- “不让对方看我的朋友圈”: `SnsPermissionUI.onPreferenceTreeClick(...)Z` (file 14, method 20450) reads `com.tencent.mm.contact.s.x2()` and calls `w41.e2.B0(z3, enabled, true)`. `x2()` (file 2, method 6232) reads `type & 0x100`; `B0(...)V` (file 2, method 61542) changes that bit and calls `l0(z3,true)`. `l0(...)V` (file 2, method 61602) persists through native contact storage and enqueues command `0x34` with a `gd5.yx4` payload. No response callback was found on this helper path, so WeiPlus reports submission/unconfirmed.
- “不看对方朋友圈”: the same native preference listener checks `SnsPermissionUI.G7(username, 5L)` (file 14, method 20443), uses operation 2 to remove an existing member or operation 1 to add a nonmember, then constructs `com.tencent.mm.plugin.sns.model.j3(op, 5L, "", count, usernames, scene)` and submits it. The read path is `p4.sk():t2` (file 14, method 15774) followed by `t2.P1(String,long):boolean` (file 11, method 14560). `j3.onGYNetEnd(...)V` (file 11, method 14450) processes the server response and updates the SNS tag store. This permission is tag-5 membership, not `rcontact.type & 0x10000`; the implementation deliberately does not model it as a type bit.
- Each option is handled independently. Only-chat changes the `0x800000` field, outside permission changes `0x100`, and SNS exclusion changes the selected username's membership in tag 5. Failed state reads block submission. The SNS membership is reread after the native response; it is confirmed only when it matches the requested target.

## Save group to contacts

- The group settings screen reads/writes preference `room_save_to_contact` through its `ChatroomInfoUI` ViewModel service. The RoomSDK interface is `Lff5/f;->k(Lcom/tencent/mm/storage/z3;Z)V`, implemented by `Lm91/a;->k(Lcom/tencent/mm/storage/z3;Z)V` (DexSQL file 11, method 47785).
- The method receives the current native group contact and desired boolean. It updates the native contact storage and queues `k91.u(roomId, 1)` to save or `k91.u(roomId, 2)` to remove. There is no result callback on this method.
- WeiPlus resolves the same RoomSDK service getter already shared with conversation-group code, requires a current `rcontact` group row, rereads the native `r2()` state immediately before calling `k(z3, boolean)`, and reports submission/unconfirmed. Empty member data is not used to declare the group invalid.

## My nickname in a group

- The native editor is `ModRemarkRoomNameUI`; its scene 3 click listener is `com.tencent.mm.chatroom.ui.y5.onClick(View)V` (DexSQL file 12, method 6161). It trims text, calls `b9.n(String)`, obtains the group RoomSDK API, and calls `Lff5/f;->n(roomId, currentWxId, nickname):Lcom/tencent/mm/roomsdk/model/factory/a;`. The account ID comes from native `Lw41/z1;->u()String`.
- The RoomSDK implementation is `Lm91/a;->n(String,String,String):Lcom/tencent/mm/roomsdk/model/factory/a;` (file 11, method 47788). It builds the native room nickname request and factory. The native UI attaches `com.tencent.mm.chatroom.ui.b6` to the factory callback field, then calls `c(Context,...)`; concrete `f.c(...)V` (file 2, method 13879) shows progress and calls `f.b()V` (file 2, method 13878), which submits through the native network service.
- `b6.a(int,int,String,gf5/b)V` (file 12, method 6090) treats a zero second integer as success and then updates the chatroom row's `field_selfDisplayName`. WeiPlus installs the same callback interface, uses the exact abstract factory `a.b()V` signature, updates the same cache field only after zero response, and rereads `selfDisplayName` for confirmation. It never changes the group name or another member's display name.
- Before submission WeiPlus requires the group contact row, a successful nonempty member list containing the current account ID, and a valid changed nickname. It trims and applies WeChat's `b9.n` normalizer and enforces the native UI's 16-character limit. Empty/unknown member data blocks writing. If the callback times out after submission, the result remains unconfirmed and is not retried.

## Verification boundary

These are static call-chain findings from the specified APK. No batch business operation was run against a real friend or group because no safe test objects were designated. The four new real-account behaviors therefore remain `NOT_VERIFIED` on device.

## 1B-R1 corrections

- Unstar candidates are derived from the successful `getFriendsResult()` snapshot, then filtered by `WeChatContactTypeState.STARRED_FRIEND` (`type & 0x40`). A failed friend query remains an error. The candidate mode changes when the star action changes, and the existing picker reset key clears the previous selection before the next generation loads.
- The permission header now has three independent target rows. Each starts at `不修改`; plan generation emits operations only for explicitly changed rows. `聊天、朋友圈、微信运动等` maps to `ONLY_CHAT=false`, and `仅聊天` maps to `ONLY_CHAT=true`. The two Moments choices retain their separate native API paths.
- DexSQL inspection of the official APK confirms `Lq61/i0;->onGYNetEnd(IIILjava/lang/String;Lcom/tencent/mm/network/y0;[B)V` (DEX file 11, method 50844) and `Lcom/tencent/mm/plugin/sns/model/j3;->onGYNetEnd(IIILjava/lang/String;Lcom/tencent/mm/network/y0;[B)V` (file 11, method 14450) use the second integer as `errType` and the third as `errCode`. In `q61.i0`, the first integer register is not used for response classification; the method logs the next two with the literal labels `errType` and `errCode`, and its success branch checks those same values. In `j3`, its response branches check those same second/third integers. The first integer is the request/network ID. `NativeSceneDispatch` now passes arguments 1 and 2 to result classification.
- Permission outcomes require the native response to be accepted and a successful state reread to match before returning `CONFIRMED`. Explicit server rejection takes precedence even if a local read happens to match. Timeout, ambiguous dispatch, or state-read failure after a potentially submitted request remains unconfirmed and is never retried.
- Regression coverage includes a nonzero network ID with zero error fields, nonzero `errType`, nonzero `errCode`, no callback timeout, and duplicate callback delivery. It also covers unstar candidate filtering/reset, all-no-change permission rejection, explicit-only action mapping, partial success/failure, stop, and account switch.
