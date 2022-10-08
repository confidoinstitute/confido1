package tools.confido.refs

import tools.confido.state.serverState

inline fun <reified T: ServerImmediateDerefEntity> Ref<T>.deref() =
    @OptIn(RefInternalAPI::class)
    serverState.derefNonBlocking(T::class.simpleName!!, id) as T?
