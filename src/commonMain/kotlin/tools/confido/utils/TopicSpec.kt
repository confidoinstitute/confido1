package tools.confido.utils

import kotlinx.serialization.KSerializer

/**
 * A [PathTemplate] bound to its payload type (PERSISTENCE_SYNC_REDESIGN.md §4.1):
 * the single source of truth for a topic's name shape *and* wire payload. Server
 * registration takes the spec and type-checks the handler's payload against
 * [serializer]; invoking a spec with entity refs yields a [BoundTopic], which carries
 * the serializer to the client subscription — so `useSubscription(Topics.x(ref))`
 * infers the payload type and cannot assert a wrong one.
 */
class BoundTopic<P>(val name: String, val serializer: KSerializer<P>) {
    override fun toString() = name
}

class TopicSpec0<P>(val path: PathTemplate0, val serializer: KSerializer<P>) {
    operator fun invoke() = BoundTopic(path(), serializer)
}

class TopicSpec1<A : Any, P>(val path: PathTemplate1<A>, val serializer: KSerializer<P>) {
    operator fun invoke(a: A) = BoundTopic(path(a), serializer)
}

class TopicSpec2<A : Any, B : Any, P>(val path: PathTemplate2<A, B>, val serializer: KSerializer<P>) {
    operator fun invoke(a: A, b: B) = BoundTopic(path(a, b), serializer)
}
