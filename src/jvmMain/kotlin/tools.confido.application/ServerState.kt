package tools.confido.state

import tools.confido.state.GlobalState

class ServerGlobalState : GlobalState {

}

actual val globalState: GlobalState = ServerGlobalState()