package tools.confido.refs

import kotlinx.serialization.Serializable
import tools.confido.state.globalState
import kotlin.jvm.JvmInline



interface Entity {
    val id: String
}
inline fun <reified T: ImmediateDerefEntity> Ref<T>.deref(): T? {
    @OptIn(RefInternalAPI::class)
    return globalState.derefNonBlocking(T::class.simpleName!!, id) as T?
}
inline suspend fun <reified T: Entity> Ref<T>.derefBlocking(): T? {
    @OptIn(RefInternalAPI::class)
    return globalState.derefBlocking(T::class.simpleName!!, id) as T?
}
inline fun <reified T: Entity> Ref<T>.derefNonBlocking(): T? {
    @OptIn(RefInternalAPI::class)
    return globalState.derefNonBlocking(T::class.simpleName!!, id) as T?
}

// Used to mark entities that can always be dereferenced without suspending...
// .. on client
interface ClientImmediateDerefEntity : Entity {}
// .. on server
interface ServerImmediateDerefEntity : Entity {}
// .. on both
interface ImmediateDerefEntity : ClientImmediateDerefEntity, ServerImmediateDerefEntity {}

@RequiresOptIn(message = "Ref internal API.")
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION, AnnotationTarget.CONSTRUCTOR)
annotation class RefInternalAPI

@Serializable
@JvmInline
// using optin instead of making constructor private because private members
// cannot be called from public inline functions
value class Ref<T: Entity> @RefInternalAPI  constructor(val id: String) {
}

inline val <reified  T: Entity> T.ref: Ref<T> get() {
    @OptIn(RefInternalAPI::class)
    return Ref<T>(this.id)
}

// COMPARING BY ID - `eqid` infix operator; can take entities, refs and string ids
inline infix fun <T: Entity> T.eqid(other: T) =
    this.id == other.id
inline infix fun <T: Entity> T.eqid(other: Ref<T>) =
    this.id == other.id
inline infix fun Entity.eqid(other: String) =
    this.id == other
inline infix fun String.eqid(other: Entity) =
    this == other.id
inline infix fun <T: Entity> Ref<T>.eqid(other: T) =
    this.id == other.id
inline infix fun <T: Entity> Ref<T>.eqid(other: Ref<T>) =
    this.id == other.id
inline infix fun <T: Entity> Ref<T>.eqid(other: String) =
    this.id == other
inline infix fun <T: Entity> String.eqid(other: Ref<T>) =
    this == other.id

fun <T: Entity> List<T>.indexOfById(what: T) = this.indexOfFirst { it eqid what }
fun <T: Entity> List<T>.findById(what: T) = this.find { it eqid what }

fun <T: Entity> MutableList<T>.insertById(what: T, replace: Boolean = false) {
    val index = this.indexOfById(what)
    if (index == -1) {
        this.add(what)
    } else if (!replace) {
        this[index] = what
    }
}
fun <T: Entity> MutableList<T>.removeById(what: T) = this.removeAll { it eqid what }

fun <T: Entity, V> Map<String, V>.get(what: T) = this[what.id]
fun <T: Entity> MutableMap<String, T>.insert(what: T) = this.set(what.id, what)
fun <T: Entity> MutableMap<String, T>.remove(what: T) = this.remove(what.id)
