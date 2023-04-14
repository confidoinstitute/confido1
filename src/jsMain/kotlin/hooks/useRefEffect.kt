package hooks

import dom.html.HTMLElement
import io.ktor.client.fetch.*
import kotlinx.js.Object
import kotlinx.js.PropertyDescriptor
import kotlinx.js.ReadonlyArray
import kotlinx.js.jso
import react.*
import tools.confido.utils.multilet
import tools.confido.utils.multiletNotNull

// Based on ideas from https://github.com/jantimon/react-use-ref-effect
// Copyright (c) 2021 Jan Nicklas under MIT license


class RefEffectContext<T>(val current: T) {
    val cleanups: MutableList<Cleanup> = mutableListOf()
    fun cleanup(c: Cleanup) {
        cleanups.add(c)
    }
}

fun <T: Any> useRefEffect(vararg dependencies: Any?, body: RefEffectContext<T>.() -> Unit): MutableRefObject<T> {
    val internalState = useMemo { object {
        var callback = body
        var currentValue: T? = null
        var currentDeps = dependencies
        var cleanups: List<Cleanup> = emptyList()
        fun cleanupIfNeeded() {
            cleanups.forEach {  it() }
            cleanups = emptyList()
        }
        // Compare dependency arrays for element-wise object identity.
        // This seems to be incorrect in the original version, which compares the arrays themselves for identity
        fun depsChanged(newDeps: ReadonlyArray<Any?>) =
                if (currentDeps.size != newDeps.size) true
                else currentDeps.zip(newDeps).all { (oldVal, newVal) -> oldVal === newVal }
        fun update(newValue: T?, newDeps: ReadonlyArray<Any?>) {
            if (newValue !== currentValue || depsChanged(newDeps)) {
                currentValue = newValue
                currentDeps = newDeps.copyOf()
                cleanupIfNeeded()
                multiletNotNull(currentValue, currentDeps) { cur, _ ->
                    val ctx = RefEffectContext(cur)
                    callback(ctx)
                    this.cleanups = ctx.cleanups
                }
            }
        }
        val refCallback = { newValue: T? -> this.update(newValue, this.currentDeps) }
        fun onDepEffect(effectBuilder: EffectBuilder, newDeps: ReadonlyArray<Any?>) {
            update(currentValue, newDeps)
            effectBuilder.cleanup { cleanupIfNeeded() }
        }
        init {
            Object.defineProperty(refCallback, "current", jso<PropertyDescriptor<T?>>{
                get = { currentValue }
                set = refCallback
            })
        }
        val refObject = refCallback.unsafeCast<MutableRefObject<T>>()
    } }
    // must update with latest version of the callback in order for variables captured from render
    // function to have up-to-date values
    internalState.callback = body
    if (dependencies.isNotEmpty()) {
        useEffect(*dependencies) { (internalState::onDepEffect)(this, dependencies) }
    }
    return internalState.refObject
}

fun <T: Any> combineRefs(vararg refs: MutableRefObject<in T>) = useMemo {
    var currentValue: T? = null
    val refCallback = { newValue: T? -> currentValue = newValue; refs.forEach { it.current = newValue } }
    Object.defineProperty(refCallback, "current", jso<PropertyDescriptor<T?>>{
        get = { currentValue }
        set = refCallback
    })
    refCallback.unsafeCast<MutableRefObject<T>>()
}

fun <T: Any> combineRefs(builder: MutableList<MutableRefObject<in T>>.()->Unit) =
    combineRefs(*buildList(builder).toTypedArray())


fun <T: HTMLElement> useEventListener(vararg types: String,
                                      capture: Boolean? = null, passive: Boolean? = null,
                                      preventDefault:Boolean = false,
                                      callback: (org.w3c.dom.events.Event)->Unit): MutableRefObject<T> {
    val callbackHolder = useRef(callback)
    callbackHolder.current = callback
    return useRefEffect {
        val opts = jso<AddEventListenerOptions> {
            capture?.let { this.capture = it }
            passive?.let { this.passive = it }
        }
        val effectiveCallback = { event: org.w3c.dom.events.Event ->
            callbackHolder.current?.invoke(event)
            if (preventDefault) event.preventDefault()
            Unit
        }
        types.forEach {
            current.addEventListener(it, effectiveCallback, opts)
        }
        cleanup {
            types.forEach {

                current.removeEventListener(it, effectiveCallback, opts)
            }
        }
    }
}
