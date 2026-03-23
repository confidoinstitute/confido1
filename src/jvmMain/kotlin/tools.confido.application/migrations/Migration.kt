package tools.confido.application.migrations

import org.bson.Document
import org.litote.kmongo.coroutine.CoroutineDatabase

/**
 * A database migration that transforms raw MongoDB documents.
 *
 * Migrations operate on raw [Document] objects, completely independent of
 * the Kotlin data classes and their serialization. This ensures migrations
 * work regardless of how the data classes have changed.
 *
 * Each migration has a unique [id] (by convention, a numeric prefix + short name,
 * e.g. "001_role_format"). Migrations are executed in the order they appear in
 * [MigrationRunner.migrations]. Once a migration runs successfully, its [id] is
 * recorded in the `_migrations` collection so it won't run again.
 */
interface Migration {
    /** Unique identifier. Use format "NNN_short_name" for ordering. */
    val id: String
    /** Human-readable description of what this migration does. */
    val description: String
    /** Execute the migration using raw MongoDB operations. */
    suspend fun run(db: CoroutineDatabase)
}

/**
 * Runs pending database migrations at server startup.
 *
 * Call [runPending] between `serverState.initialize()` and `serverState.load()`
 * to ensure documents are migrated before they are deserialized into Kotlin objects.
 */
object MigrationRunner {
    /** All migrations in order. Append new migrations at the end of this list. */
    private val migrations: List<Migration> = listOf(
    )

    /**
     * Check for and execute any migrations that haven't been applied yet.
     */
    suspend fun runPending(db: CoroutineDatabase) {
        val col = db.getCollection<Document>("_migrations")
        val applied = col.find().toList().map { it.getString("id") }.toSet()

        for (migration in migrations) {
            if (migration.id in applied) continue
            println("Running migration: ${migration.id} — ${migration.description}")
            migration.run(db)
            col.insertOne(
                Document(
                    mapOf(
                        "id" to migration.id,
                        "description" to migration.description,
                        "executedAt" to java.util.Date(),
                    )
                )
            )
            println("Migration ${migration.id} completed.")
        }
    }
}
