@file:JsModule("@milkdown/crepe")
@file:JsNonModule

package ext.milkdown

external interface Ctx {}
external interface ListenerMananager {
    fun markdownUpdated(fn: (ctx: Ctx, markdown: String, prevMarkdown: String)->Unit)
}

// Crepe class constructor
@JsName("Crepe")
external class Crepe(options: CrepeOptions) {
    fun on(builder: (ListenerMananager)->Unit)
}

external interface CrepeOptions {
    var root: dynamic
    var defaultValue: String?
    var featureConfigs: dynamic
    var onChange: ((markdown: String) -> Unit)?
}

// Import CSS manually in component
// "@milkdown/crepe/theme/common/style.css"
// "@milkdown/crepe/theme/frame.css"
