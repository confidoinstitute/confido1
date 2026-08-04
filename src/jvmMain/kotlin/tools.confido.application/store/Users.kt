package tools.confido.store

import com.mongodb.MongoCommandException
import com.mongodb.MongoException
import com.mongodb.client.model.Collation
import com.mongodb.client.model.CollationStrength
import com.mongodb.client.model.IndexOptions
import org.bson.Document
import org.litote.kmongo.*
import org.litote.kmongo.coroutine.*
import tools.confido.state.serverState
import users.User

object userStore : MongoStore<User>(
    serverState.database.getCollection("users"),
    { e, id -> e.copy(id = id) },
) {
    /** Keyed by lowercased email (uniqueness is case-insensitive, see the Mongo index). */
    val byEmail = UniqueIndex(this) { it.email?.lowercase() }

    override suspend fun initialize() {
        coll.ensureIndex(User::email)
        // Bugfix: originally we created a unique index on email without a null filter.
        // This prevented multiple user entries with null email. And also it had incorrect
        // collation so it wasn't case-insensitive.
        coll.listIndexes<Document>()
            .toList().find { index ->
                val isEmailIndex = index.get("key", Document::class.java)
                    ?.containsKey(User::email.name) == true
                val isUnique = index.getBoolean("unique") ?: false
                val hasNoPartialFilter = !index.containsKey("partialFilterExpression")

                isEmailIndex && isUnique && hasNoPartialFilter
            }
            ?.let {
                try {
                    coll.dropIndex(it)
                    println("Dropped old email index without partial filter: $it")
                } catch (e: MongoCommandException) {
                    if (e.errorCode != 27) { // 27 is "index not found"
                        throw e
                    }
                }
            }
        try {
            // Unique constraint on emails, case insensitive, ignores nulls.
            coll.ensureUniqueIndex(
                User::email,
                indexOptions = IndexOptions()
                    .collation(
                        Collation.builder()
                            .locale("simple")
                            .collationStrength(CollationStrength.SECONDARY)
                            .build()
                    )
                    .partialFilterExpression(
                        Document(User::email.name, Document("\$type", "string"))
                    )
            )
        } catch (e: MongoException) {
            println("Creating unique index failed, there are probably duplicate emails already")
        }
    }
}

/** Register the user store. Called once from server startup. */
fun registerUserStores() {
    Stores.register(userStore)
    Stores.resident[User::class] = userStore
}
