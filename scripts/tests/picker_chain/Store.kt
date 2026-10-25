package h.Hchat.hooks.items.custombottombar
object CustomBottomBarIconStore {
 fun isSupportedTabKey(key:String)=key=="tab"
 fun saveFromUri(context:Any,key:String,uri:android.net.Uri):String?="path"
 fun delete(context:Any,path:String?)=Unit
}
