package tools.confido.refs

import kotlinx.serialization.Serializable
import rooms.Room
import tools.confido.question.Question
import tools.confido.state.globalState
import tools.confido.utils.generateId
import users.User
import kotlin.jvm.JvmInline


interface HasId {
    val id: String
}

interface Entity: HasId {
}
inline fun <reified T: ImmediateDerefEntity> Ref<T>.deref(): T? {
    @OptIn(DelicateRefAPI::class)
    return globalState.derefNonBlocking(T::class.simpleName!!, id) as T?
}
inline suspend fun <reified T: Entity> Ref<T>.derefBlocking(): T? {
    @OptIn(DelicateRefAPI::class)
    return globalState.derefBlocking(T::class.simpleName!!, id) as T?
}
inline fun <reified T: Entity> Ref<T>.derefNonBlocking(): T? {
    @OptIn(DelicateRefAPI::class)
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
annotation class DelicateRefAPI

@Serializable
@JvmInline
// using optin instead of making constructor private because private members
// cannot be called from public inline functions
value class Ref<T: Entity> (val id: String) {
}

inline val <reified  T: Entity> T.ref: Ref<T> get() {
    @OptIn(DelicateRefAPI::class)
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

// HACK: we cannot call copy() on T:Entity because compiler does not know it is a data class
inline fun <reified  T: Entity> T.withId(id: String): T =
    when (this) {
        is Question -> copy(id = id) as T
        is Room -> copy(id = id) as T
        is User -> copy(id = id) as T
        else -> throw NotImplementedError()
    }

inline fun <reified  T: Entity> T.assignId() = withId(generateId())

inline fun <reified T: Entity> T.assignIdIfNeeded() = if (id.isEmpty()) assignId() else this
