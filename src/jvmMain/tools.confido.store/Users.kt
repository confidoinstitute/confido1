package tools.confido.store

import kotlinx.coroutines.flow.toList
import com.mongodb.MongoCommandException
import com.mongodb.MongoException
import com.mongodb.client.model.Collation
import com.mongodb.client.model.CollationStrength
import com.mongodb.client.model.IndexOptions
import org.bson.Document
import tools.confido.state.serverState
import tools.confido.users.User

object userStore : MongoStore<User>(
    serverState.database.getCollection("users"),
    { e, id -> e.copy(id = id) },
) {
    /** Keyed by lowercased email (uniqueness is case-insensitive, see the Mongo index). */
    val byEmail = UniqueIndex(this) { it.email?.lowercase() }

    override suspend fun initialize() {
        // No plain email index next to the unique one: both would be auto-named `email_1`,
        // so creating them in turn just dropped and recreated the index every boot. The
        // unique partial index below serves email lookups as well.
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
                    // Drop by name: the index-info document is not a key spec.
                    coll.dropIndex(it.getString("name"))
                    println("Dropped old email index without partial filter: $it")
                } catch (e: MongoCommandException) {
                    if (e.errorCode != 27) { // 27 is "index not found"
                        throw e
                    }
                }
            }
        try {
            // Unique constraint on emails, case insensitive, ignores nulls. The collation
            // must be a real locale: "simple" means binary comparison and the server
            // silently ignores the strength (so the index used to be case-sensitive).
            // Strength SECONDARY = case-insensitive, diacritic-sensitive, matching the
            // lowercase-keyed in-memory byEmail index.
            coll.ensureUniqueIndex(
                User::email,
                indexOptions = IndexOptions()
                    .collation(
                        Collation.builder()
                            .locale("en")
                            .collationStrength(CollationStrength.SECONDARY)
                            .build()
                    )
                    .partialFilterExpression(
                        Document(User::email.name, Document("\$type", "string"))
                    )
            )
        } catch (e: MongoException) {
            println("Creating the unique email index failed — there are probably emails differing only by case: ${e.message}")
        }
    }
}

/** Register the user store. Called once from server startup. */
fun registerUserStores() {
    Stores.register(userStore)
    Stores.resident[User::class] = userStore
}
