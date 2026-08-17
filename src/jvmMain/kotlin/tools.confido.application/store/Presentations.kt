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
        // As a before-delete hook the clear stages into the deleting transaction, so the
        // store removal and the run-state cell change land as one observable tick
        // (§3.5's cross-kind atomicity; an onChange listener fired one tick later).
        beforeEntityDelete { pres ->
            serverState.setPresentationRunState(Ref(pres.room.id), null)
        }
    }
}

/** Register the presentation store. Called once from server startup. */
fun registerPresentationStores() {
    Stores.register(presentationStore)
    Stores.resident[Presentation::class] = presentationStore
}
