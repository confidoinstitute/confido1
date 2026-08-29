package tools.confido.utils

/**
 * Typed path templates (PERSISTENCE_SYNC_REDESIGN.md §4.1): one object per path shape,
 * serving *construction* (typed arguments in, path string out — client subscriptions,
 * link building) and *matching* (path string in, typed arguments out — server topic
 * registration) from the same declaration, so the two sides cannot drift and parameter
 * names/positions never appear as strings outside the template.
 *
 * Templates compose with `/`: a literal keeps the arity, a [PathParam] raises it —
 * `path("room") / roomRefParam / "comments"` is a `PathTemplate1<Ref<Room>>`. The
 * fixed arity family (0–2) covers every current path with headroom; add an arity if a
 * wider path ever appears (same pattern as `combineViews`'s arities).
 *
 * [PathParam.decode] returning null means "no match" — it validates *shape* only
 * (e.g. non-empty id). Existence checks stay in handlers/gates, preserving NOT_FOUND
 * semantics and gate recovery.
 */
class PathParam<T : Any>(
    val name: String,
    val encode: (T) -> String,
    val decode: (String) -> T?,
)

sealed interface PathSeg
data class LitSeg(val text: String) : PathSeg
data class ParamSeg(val param: PathParam<*>) : PathSeg

private fun litSegs(text: String): List<PathSeg> =
    text.split('/').map { require(it.isNotEmpty()) { "empty path segment in '$text'" }; LitSeg(it) }

sealed class PathTemplate(val segments: List<PathSeg>) {
    /** Human-readable pattern, e.g. `room/{room}/comments`. */
    val pattern: String get() = segments.joinToString("/") {
        when (it) {
            is LitSeg -> it.text
            is ParamSeg -> "{${it.param.name}}"
        }
    }
    override fun toString() = pattern

    /** Positional decoded parameter values, or null if [pathSegments] doesn't match. */
    fun matchValues(pathSegments: List<String>): List<Any>? {
        if (pathSegments.size != segments.size) return null
        val out = ArrayList<Any>(2)
        for (i in segments.indices) {
            when (val seg = segments[i]) {
                is LitSeg -> if (seg.text != pathSegments[i]) return null
                is ParamSeg -> out.add(seg.param.decode(pathSegments[i]) ?: return null)
            }
        }
        return out
    }

    protected fun render(args: List<Any>): String {
        var next = 0
        return segments.joinToString("/") {
            when (it) {
                is LitSeg -> it.text
                is ParamSeg -> {
                    @Suppress("UNCHECKED_CAST")
                    (it.param as PathParam<Any>).encode(args[next++])
                }
            }
        }
    }
}

class PathTemplate0 internal constructor(segments: List<PathSeg>) : PathTemplate(segments) {
    operator fun invoke(): String = render(emptyList())
    operator fun div(lit: String) = PathTemplate0(segments + litSegs(lit))
    operator fun <A : Any> div(p: PathParam<A>) = PathTemplate1<A>(segments + ParamSeg(p))
}

class PathTemplate1<A : Any> internal constructor(segments: List<PathSeg>) : PathTemplate(segments) {
    operator fun invoke(a: A): String = render(listOf(a))
    operator fun div(lit: String) = PathTemplate1<A>(segments + litSegs(lit))
    operator fun <B : Any> div(p: PathParam<B>) = PathTemplate2<A, B>(segments + ParamSeg(p))
    @Suppress("UNCHECKED_CAST")
    fun match(pathSegments: List<String>): A? = matchValues(pathSegments)?.let { it[0] as A }
}

class PathTemplate2<A : Any, B : Any> internal constructor(segments: List<PathSeg>) : PathTemplate(segments) {
    operator fun invoke(a: A, b: B): String = render(listOf(a, b))
    operator fun div(lit: String) = PathTemplate2<A, B>(segments + litSegs(lit))
    @Suppress("UNCHECKED_CAST")
    fun match(pathSegments: List<String>): Pair<A, B>? =
        matchValues(pathSegments)?.let { it[0] as A to it[1] as B }
}

fun path(first: String) = PathTemplate0(litSegs(first))
