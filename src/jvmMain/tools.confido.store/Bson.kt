package tools.confido.store

import com.mongodb.MongoCommandException
import com.mongodb.client.model.Filters
import com.mongodb.client.model.IndexOptions
import com.mongodb.client.model.Indexes
import com.mongodb.client.model.Sorts
import com.mongodb.kotlin.client.coroutine.MongoCollection
import org.bson.conversions.Bson
import tools.confido.refs.Ref
import kotlin.reflect.KProperty1

/**
 * Minimal typed query helpers over the official Kotlin driver — the subset of KMongo's
 * property DSL the stores actually used, so filters, sorts and index keys stay
 * refactor-safe instead of being string literals.
 *
 * A property's BSON field name is taken to be its Kotlin name. That holds for every
 * stored field except `_id` (the only `@SerialName` in the entity classes), which is
 * addressed via [idFilter] instead. Ref-valued arguments are unwrapped to their id string
 * (refs are stored as plain ids, see `RefAsStringSerializer`) so the value never goes
 * through the codec registry.
 */
val KProperty1<*, *>.field: String get() = name

private fun bsonValue(v: Any?): Any? = if (v is Ref<*>) v.id else v

infix fun <V> KProperty1<*, V>.eq(value: V): Bson = Filters.eq(field, bsonValue(value))
infix fun <V : Comparable<V>> KProperty1<*, V>.lte(value: V): Bson = Filters.lte(field, value)
/** Array field contains [value] (Mongo's implicit element match on `{field: value}`). */
infix fun <V> KProperty1<*, out Iterable<V>?>.contains(value: V): Bson = Filters.eq(field, bsonValue(value))

fun idFilter(id: String): Bson = Filters.eq("_id", id)

fun ascending(vararg props: KProperty1<*, *>): Bson = Sorts.ascending(props.map { it.field })
fun descending(vararg props: KProperty1<*, *>): Bson = Sorts.descending(props.map { it.field })

/**
 * Ensure an ascending compound index on [props] with exactly [indexOptions]. If an index
 * on these keys already exists with *different* options, it is dropped and recreated
 * (index options are defined by the code, so the code wins) — the semantics KMongo's
 * `ensureIndex` had, which the users email index setup relies on.
 */
suspend fun <T : Any> MongoCollection<T>.ensureIndex(
    vararg props: KProperty1<*, *>,
    indexOptions: IndexOptions = IndexOptions(),
): String {
    val keys = Indexes.ascending(props.map { it.field })
    return try {
        createIndex(keys, indexOptions)
    } catch (e: MongoCommandException) {
        dropIndex(keys)
        createIndex(keys, indexOptions)
    }
}

suspend fun <T : Any> MongoCollection<T>.ensureUniqueIndex(
    vararg props: KProperty1<*, *>,
    indexOptions: IndexOptions = IndexOptions(),
): String = ensureIndex(*props, indexOptions = indexOptions.unique(true))
