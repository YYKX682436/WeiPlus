package android.view
open class View { var isShown = true; var tag: Any? = null; var layouts = 0; fun requestLayout() { layouts++ } }
open class ViewGroup : View() {
    val children = ArrayList<View>()
    val childCount get() = children.size
    fun getChildAt(index: Int) = children[index]
}
