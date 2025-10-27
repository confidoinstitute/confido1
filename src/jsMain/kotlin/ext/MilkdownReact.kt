@file:JsModule("@milkdown/react")
@file:JsNonModule

package ext.milkdown

import js.promise.Promise
import react.*

// MilkdownProvider component
external interface MilkdownProviderProps : PropsWithChildren

@JsName("MilkdownProvider")
external val MilkdownProvider: FC<MilkdownProviderProps>

// Milkdown component
external interface MilkdownProps : Props

@JsName("Milkdown")
external val Milkdown: FC<MilkdownProps>

// useEditor hook
@JsName("useEditor")
external fun useEditor(
    factory: (root: dynamic) -> dynamic,
    deps: Array<dynamic> = definedExternally
): dynamic

// Editor instance interface
external interface Editor {
    fun destroy()
    fun action(action: dynamic): dynamic
    fun use(plugin: dynamic): Editor
    fun config(config: dynamic): Editor
    fun create(): Promise<Editor>
}

// useInstance hook for accessing editor instance
@JsName("useInstance")
external fun useInstance(): UseInstanceReturn

external interface UseInstanceReturn {
    val loading: Boolean
    val instance: Editor?
    val error: dynamic
}
