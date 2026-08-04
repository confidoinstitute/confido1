package tools.confido.store

import tools.confido.question.Question
import tools.confido.state.serverState

object questionStore : MongoStore<Question>(
    serverState.database.getCollection("questions"),
    { e, id -> e.copy(id = id) },
)

/** Register the question store. Called once from server startup. */
fun registerQuestionStores() {
    Stores.register(questionStore)
    Stores.resident[Question::class] = questionStore
}
