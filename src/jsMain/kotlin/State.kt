package tools.confido.state

import tools.confido.refs.*

class ClientState : GlobalState() {

}

inline fun <reified T: ClientImmediateDerefEntity> Ref<T>.deref() =
    @OptIn(RefInternalAPI::class)
    clientState.maybeDeref(T::class.simpleName!!, this)

val clientState = ClientState()
actual val globalState: GlobalState = clientState