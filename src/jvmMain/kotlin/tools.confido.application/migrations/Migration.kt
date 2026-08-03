package tools.confido.application.migrations

import com.mongodb.client.model.Filters
import org.bson.Document
import org.litote.kmongo.coroutine.CoroutineCollection
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
 *
 * A migration must declare every collection it modifies in [collections] and access
 * them only through [MigrationContext.collection] — the context hands out *working
 * copies*, never the live collections (accessing an undeclared collection throws).
 */
interface Migration {
    /** Unique identifier. Use format "NNN_short_name" for ordering. */
    val id: String
    /** Human-readable description of what this migration does. */
    val description: String
    /** Every collection this migration modifies. */
    val collections: List<String>
    /** Execute the migration. All writes go to working copies obtained from [ctx]. */
    suspend fun run(ctx: MigrationContext)
}

/** Resolves declared collection names to this run's working copies. */
class MigrationContext internal constructor(
    private val db: CoroutineDatabase,
    private val migrationId: String,
    private val workingNames: Map<String, String>,
) {
    fun collection(name: String): CoroutineCollection<Document> =
        db.getCollection(workingNames[name]
            ?: error("Migration $migrationId accessed collection '$name' not declared in its `collections`"))
}

/**
 * Runs pending database migrations at server startup.
 *
 * Call [runPending] between `serverState.initialize()` and `serverState.load()`
 * to ensure documents are migrated before they are deserialized into Kotlin objects.
 *
 * Safety model — **copy, transform aside, verify, then promote**
 * (see PERSISTENCE_SYNC_REDESIGN.md §6):
 *
 * 1. Each declared collection is copied to a working copy `<name>_mig_<id>`; the
 *    migration transforms only the copies. **Live collections are untouched until
 *    the transform has completed and verified**, so no failure or crash before
 *    promotion can damage or roll back live data. Working copies are disposable:
 *    a leftover one (from a crash or a failed run, where it is deliberately kept
 *    for inspection) is dropped and rebuilt on the next attempt, so migrations do
 *    not need to be idempotent.
 * 2. **Verify pass**: each working copy is strictly decoded with the real entity
 *    serializers (see `serverState.collectionVerifiers()`) before promotion,
 *    catching a transform that completed but produced broken documents.
 * 3. **Promotion** copies each verified working copy over its live collection via
 *    `$out` (atomic per collection; preserves the live collection's indexes),
 *    bracketed by a two-phase ledger entry (`promoting` → `done`). After a crash
 *    mid-promotion, the next boot finishes promoting the remaining verified
 *    working copies instead of re-running the transform.
 * 4. Just before promotion, the pre-migration state of each live collection is
 *    saved to `<name>_pre_<id>` for **manual** rollback or forensics. No code path
 *    ever reads these backups — in particular, a lost or damaged ledger can not
 *    cause an old backup to be silently restored; the worst case of a lost ledger
 *    entry is a re-transform of *current* data, gated by the verify pass.
 *    (Defense in depth: prefer shape-conditional transforms anyway, so a re-run
 *    over already-migrated data is a no-op.)
 */
object MigrationRunner {
    private const val PHASE_PROMOTING = "promoting"
    private const val PHASE_DONE = "done"

    /** All migrations in order. Append new migrations at the end of this list. */
    private val migrations: List<Migration> = listOf(
        RoleFormatMigration,
        QuestionGroupsToTagsMigration,
        FoldCommentLikesMigration,
    )

    /**
     * Check for and execute any migrations that haven't been applied yet.
     *
     * @param verifiers per-collection checks, keyed by live collection name; called
     *   with the name of the collection to actually read (the working copy) and
     *   should throw if any document fails to deserialize.
     */
    suspend fun runPending(db: CoroutineDatabase, verifiers: Map<String, suspend (String) -> Unit> = emptyMap()) {
        val col = db.getCollection<Document>("_migrations")
        val ledger = col.find().toList()
        // Entries written before the two-phase scheme have no "phase" field; treat them as done.
        val applied = ledger.filter { it.getString("phase") != PHASE_PROMOTING }.mapNotNull { it.getString("id") }.toSet()
        val promoting = ledger.filter { it.getString("phase") == PHASE_PROMOTING }.mapNotNull { it.getString("id") }.toSet()

        for (migration in migrations) {
            if (migration.id in applied) continue
            val working = migration.collections.associateWith { "${it}_mig_${migration.id}" }

            if (migration.id in promoting) {
                // Crashed after verification, during promotion. The remaining working
                // copies are verified-good; finish promoting them, do not re-transform.
                println("Migration ${migration.id}: finishing interrupted promotion.")
                promote(db, working)
                markDone(col, migration.id)
                println("Migration ${migration.id} completed.")
                continue
            }

            println("Running migration: ${migration.id} — ${migration.description}")
            val existing = db.listCollectionNames().toSet()
            working.forEach { (orig, work) ->
                if (work in existing) db.getCollection<Document>(work).drop() // stale earlier attempt
                if (orig in existing) copyCollection(db, orig, work)
            }

            try {
                migration.run(MigrationContext(db, migration.id, working))
                working.forEach { (orig, work) ->
                    verifiers[orig]?.let {
                        println("  Verifying $orig...")
                        it(work)
                    }
                }
            } catch (e: Exception) {
                println("Migration ${migration.id} FAILED: ${e.message}")
                println("  Live collections are untouched. Working copies (${working.values.joinToString()}) kept for inspection.")
                throw e
            }

            // Keep a manual-rollback backup of the pre-migration state, then promote.
            working.keys.forEach { orig ->
                if (orig in existing) copyCollection(db, orig, "${orig}_pre_${migration.id}")
            }
            col.insertOne(Document(mapOf(
                "id" to migration.id,
                "description" to migration.description,
                "phase" to PHASE_PROMOTING,
            )))
            promote(db, working)
            markDone(col, migration.id)
            println("Migration ${migration.id} completed.")
        }

        // Sweep working copies of applied migrations (left over e.g. when a run was
        // interrupted after its ledger entry was written). Only reached when no
        // migration failed, so copies kept for inspection are never swept.
        val names = db.listCollectionNames().toSet()
        migrations.forEach { m ->
            m.collections.forEach { orig ->
                val work = "${orig}_mig_${m.id}"
                if (work in names) {
                    println("Dropping stale migration working copy $work.")
                    db.getCollection<Document>(work).drop()
                }
            }
        }
    }

    /** Copy each existing working copy over its live collection, then drop it. */
    private suspend fun promote(db: CoroutineDatabase, working: Map<String, String>) {
        val present = db.listCollectionNames().toSet()
        working.forEach { (orig, work) ->
            if (work in present) {
                copyCollection(db, work, orig) // $out: atomic replace, preserves orig's indexes
                db.getCollection<Document>(work).drop()
            }
        }
    }

    private suspend fun markDone(col: CoroutineCollection<Document>, id: String) {
        col.updateOne(
            Filters.eq("id", id),
            Document("\$set", Document(mapOf("phase" to PHASE_DONE, "executedAt" to java.util.Date()))),
        )
    }

    /** Replace the contents of collection [to] with a copy of collection [from]. */
    private suspend fun copyCollection(db: CoroutineDatabase, from: String, to: String) {
        db.getCollection<Document>(from).aggregate<Document>(
            listOf(Document("\$match", Document()), Document("\$out", to))
        ).toList()
    }
}
