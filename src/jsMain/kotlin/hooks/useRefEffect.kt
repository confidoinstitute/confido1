package hooks

import web.html.HTMLElement
import io.ktor.client.fetch.*
import js.array.ReadonlyArray
import js.objects.Object
import js.objects.TypedPropertyDescriptor
import js.objects.unsafeJso as jso
import react.*
import tools.confido.utils.multilet
import tools.confido.utils.multiletNotNull
import web.events.Event
import web.events.EventHandler
import web.events.EventTarget
import web.events.EventType
import web.events.HasTargets
import web.events.addEventListener
import kotlin.js.unsafeCast

// Based on ideas from https://github.com/jantimon/react-use-ref-effect
// Copyright (c) 2021 Jan Nicklas under MIT license


class RefEffectContext<T>(val current: T) {
    val cleanups: MutableList<Cleanup> = mutableListOf()
    fun cleanup(c: Cleanup) {
        cleanups.add(c)
    }
}

fun <T: Any> useRefEffect(vararg dependencies: Any?, body: RefEffectContext<T>.() -> Unit): RefObject<T> {
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
        fun onDepEffect(effectBuilder: CleanupBuilder, newDeps: ReadonlyArray<Any?>) {
            update(currentValue, newDeps)
            effectBuilder.onCleanup { cleanupIfNeeded() }
        }
        init {
            Object.defineProperty(refCallback, "current", jso<TypedPropertyDescriptor<T?>>{
                get = { currentValue }
                set = refCallback
            })
        }
        val refObject = refCallback.unsafeCast<RefObject<T>>()
    } }
    // must update with latest version of the callback in order for variables captured from render
    // function to have up-to-date values
    internalState.callback = body
    if (dependencies.isNotEmpty()) {
        useEffectWithCleanup(*dependencies) { (internalState::onDepEffect)(this, dependencies) }
    }
    return internalState.refObject
}

fun <T: Any> combineRefs(vararg refs: RefObject<in T>?) = useMemo {
    var currentValue: T? = null
    val refCallback = { newValue: T? -> currentValue = newValue; refs.forEach { it?.current = newValue } }
    Object.defineProperty(refCallback, "current", jso<TypedPropertyDescriptor<T?>>{
        get = { currentValue }
        set = refCallback
    })
    refCallback.unsafeCast<RefObject<T>>()
}

fun <T: Any> combineRefs(builder: MutableList<RefObject<in T>>.()->Unit) =
    combineRefs(*buildList(builder).toTypedArray())


inline fun <E : Event, C : EventTarget, T : EventTarget, D> useEventListener(vararg types: EventType<E>,
                                      capture: Boolean? = null, passive: Boolean? = null,
                                      preventDefault:Boolean = false,
                                       noinline callback: (D)->Unit): RefObject<T>

        where D : E, D : HasTargets<C, T>
{
    val callbackHolder = useRef(callback)
    callbackHolder.current = callback
    return useRefEffect {
        val opts = jso<AddEventListenerOptions> {
            capture?.let { this.capture = it }
            passive?.let { this.passive = it }
        }
        val effectiveCallback = { event: D ->
            callbackHolder.current?.invoke(event)
            if (preventDefault) event.preventDefault()
            Unit
        }
        types.forEach {
            current.addEventListener(it, EventHandler<E,C,T,D>(effectiveCallback), opts)
        }
        cleanup {
            types.forEach {

                current.removeEventListener(it, EventHandler<E,C,T,D>(effectiveCallback), opts)
            }
        }
    }
}
