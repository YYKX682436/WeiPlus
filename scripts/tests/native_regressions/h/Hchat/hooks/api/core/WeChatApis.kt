package h.Hchat.hooks.api.core

import h.Hchat.hooks.api.runtime.WeChatDatabaseListenerApi

object WeChatApis {
    var databaseChangesApi: WeChatDatabaseListenerApi? = null
    fun databaseChanges(): WeChatDatabaseListenerApi? = databaseChangesApi
}
