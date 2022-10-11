package components.profile

import components.AppStateContext
import csstype.*
import emotion.react.css
import mui.material.*
import mui.material.styles.TypographyVariant
import mui.system.sx
import react.*
import react.dom.html.ReactHTML.div
import react.dom.onChange
import payloads.requests.SetNick
import react.dom.html.ReactHTML.br
import utils.eventValue
import utils.themed

val SetNickForm = FC<Props> {
    val (appState, stale) = useContext(AppStateContext)
    var name by useState("")

    val user = appState.session.user ?: return@FC
    Paper {
        sx {
            marginTop = themed(2)
            padding = themed(2)
        }
        Typography {
            variant = TypographyVariant.body1
            +"From state: your name is ${user.nick ?: "not set"} and language is ${appState.session.language}."
            br {}
            +"Your type is ${user.type}."
        }
        div {
            css {
                marginTop = themed(1)
                display = Display.flex
                alignItems = AlignItems.flexEnd
            }
            TextField {
                variant = FormControlVariant.standard
                id = "name-field"
                label = ReactNode("Name")
                value = name
                disabled = stale
                onChange = {
                    name = it.eventValue()
                }
            }
            Button {
                onClick = {
                    Client.postData("/setName", SetNick(name))
                }
                disabled = stale
                +"Set name"
            }
        }
    }
}
