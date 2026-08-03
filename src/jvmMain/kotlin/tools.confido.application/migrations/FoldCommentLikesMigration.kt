package tools.confido.application.migrations

import com.mongodb.client.model.Filters
import org.bson.Document

/**
 * Folds the standalone `commentLikes` collection into a `likes` array of user ids on each
 * comment document (both `questionComments` and `roomComments`), then empties
 * `commentLikes` — the CommentLike entity is gone from the model.
 *
 * Shape-conditional: a comment that already has a `likes` field is left untouched, so a
 * re-run over already-migrated data (e.g. after a lost ledger) is a no-op — by then
 * `commentLikes` is empty anyway.
 */
object FoldCommentLikesMigration : Migration {
    override val id = "003_fold_comment_likes"
    override val description = "Fold comment likes into a likes array on comment documents"
    override val collections = listOf("questionComments", "roomComments", "commentLikes")

    override suspend fun run(ctx: MigrationContext) {
        val likesByComment = mutableMapOf<String, MutableList<String>>()
        ctx.collection("commentLikes").find().toList().forEach { doc ->
            val comment = doc.getString("comment") ?: return@forEach
            val user = doc.getString("user") ?: return@forEach
            val users = likesByComment.getOrPut(comment) { mutableListOf() }
            if (user !in users) users.add(user)
        }

        listOf("questionComments", "roomComments").forEach { collName ->
            val coll = ctx.collection(collName)
            coll.find().toList().forEach { doc ->
                if (doc.containsKey("likes")) return@forEach
                val likes = likesByComment[doc.getString("_id")] ?: emptyList<String>()
                coll.updateOne(
                    Filters.eq("_id", doc.getString("_id")),
                    Document("\$set", Document("likes", likes)),
                )
            }
        }

        // Retire the like data: promotion copies this emptied working copy over the live
        // collection. (The pre-migration state stays in commentLikes_pre_<id> for manual
        // rollback, as with every migration.)
        ctx.collection("commentLikes").deleteMany(Document())
    }
}
