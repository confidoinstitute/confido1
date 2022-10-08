package tools.confido.state

import tools.confido.refs.*


class ServerGlobalState : GlobalState() {
}

inline fun <reified T: ServerImmediateDerefEntity> Ref<T>.deref() =
    @OptIn(RefInternalAPI::class)
    serverState.maybeDeref(T::class.simpleName!!, this)

val serverState = ServerGlobalState()
actual val globalState: GlobalState = serverState