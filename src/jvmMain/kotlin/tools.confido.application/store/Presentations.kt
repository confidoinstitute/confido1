package tools.confido.store

import tools.confido.refs.Ref
import tools.confido.state.Presentation
import tools.confido.state.serverState

object presentationStore : MongoStore<Presentation>(
    serverState.database.getCollection("presentations"),
    { e, id -> e.copy(id = id) },
) {
    init {
        // Clean up the (transient, in-memory) run state when a presentation is deleted.
        onChange { change ->
            if (change is Change.Removed)
                serverState.presentationRunStates.remove(Ref(change.entity.room.id))
        }
    }
}

/** Register the presentation store. Called once from server startup. */
fun registerPresentationStores() {
    Stores.register(presentationStore)
    Stores.resident[Presentation::class] = presentationStore
}
