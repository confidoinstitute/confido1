package tools.confido.state


class ServerGlobalState : GlobalState() {

}

actual val globalState: GlobalState = ServerGlobalState()