package tools.confido.refs
import tools.confido.state.clientState

inline fun <reified T: ClientImmediateDerefEntity> Ref<T>.deref() =
    @OptIn(DelicateRefAPI::class)
    clientState.derefNonBlocking(T::class.simpleName!!, id) as T?

