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
import utils.eventValue

val SetNickForm = FC<Props> {
    val appState = useContext(AppStateContext)
    var name by useState<String>("")

    val user = appState.state.session.user ?: return@FC
    Paper {
        sx {
            marginTop = 10.px
            padding = 10.px
        }
        Typography {
            variant = TypographyVariant.body1
            +"From state: your name is ${user.nick ?: "not set"} and language is ${appState.state.session.language}."
        }
        div {
            css {
                marginTop = 5.px
                display = Display.flex
                alignItems = AlignItems.flexEnd
            }
            TextField {
                variant = FormControlVariant.standard
                id = "name-field"
                label = ReactNode("Name")
                value = name
                disabled = appState.stale
                onChange = {
                    name = it.eventValue()
                }
            }
            Button {
                onClick = {
                    Client.postData("/setName", SetNick(name))
                }
                disabled = appState.stale
                +"Set name"
            }
        }
    }
}
