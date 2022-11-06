package components

import react.*
import mui.material.*
import mui.system.sx
import utils.themed

val DemoEmailAlert = FC<Props> {
    Alert {
        sx {
            marginBottom = themed(2)
        }
        AlertTitle {
            +"This is a demo"
        }
        severity = AlertColor.info
        Typography {
            +"Please use a made-up email address, such as john.doe@confido.example. The address you enter will be visible to other users of the demo."
        }
        Typography {
            +"No emails are sent to users. You may also log in into any account without a password after logging out."
        }
    }
}