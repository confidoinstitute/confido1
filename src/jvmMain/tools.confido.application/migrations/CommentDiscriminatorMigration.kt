package tools.confido.application.migrations

import com.mongodb.client.model.Filters
import com.mongodb.client.model.Updates

/**
 * Stamps the top-level `___type` discriminator onto comment documents that lack one.
 *
 * Comments written before `Comment` became a sealed class (2022) carry no discriminator
 * at all; the old codec decoded them by the concrete subclass and never noticed. The
 * archives now read and verify through the sealed parent, which requires the field
 * (see ShortSerialNamesMigration for the FQN → short-name half of this invariant).
 * The collection determines the subtype, so the stamp is unambiguous. Shape-conditional:
 * a re-run is a no-op.
 */
object CommentDiscriminatorMigration : Migration {
    override val id = "011_comment_discriminators"
    override val description = "Stamp the missing `___type` discriminator onto pre-sealed-class comment documents"
    override val collections = listOf("questionComments", "roomComments")

    private val typeByCollection = mapOf("questionComments" to "question", "roomComments" to "room")

    override suspend fun run(ctx: MigrationContext) {
        typeByCollection.forEach { (name, type) ->
            val r = ctx.collection(name).updateMany(
                Filters.exists("___type", false),
                Updates.set("___type", type),
            )
            if (r.modifiedCount > 0) println("  $name: stamped ___type=$type on ${r.modifiedCount} document(s)")
        }
    }
}
