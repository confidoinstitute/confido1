package components.profile

import components.AppStateContext
import csstype.*
import mui.material.*
import mui.material.styles.TypographyVariant
import mui.system.sx
import react.*
import react.dom.onChange
import payloads.requests.SetNick
import payloads.requests.StartEmailVerification
import react.dom.html.ReactHTML.br
import utils.eventValue
import utils.themed

val UserSettings = FC<Props> {
    val (appState, stale) = useContext(AppStateContext)
    val user = appState.session.user ?: run {
        console.error("No user")
        return@FC
    }

    var name by useState(user.nick ?: "")
    var email by useState(user.email ?: "")

    var pendingEmailChange by useState<String?>(null)

    Paper {
        sx {
            marginTop = themed(2)
            padding = themed(2)
        }

        Typography {
            variant = TypographyVariant.h4
            +"User settings"
        }

        if (!user.emailVerified || pendingEmailChange != null) {
            Alert {
                AlertTitle {
                    +"Your email is not verified!"
                }
                severity = AlertColor.warning
                if (pendingEmailChange != null) {
                    +"We have sent you a verification email, please check your inbox."
                } else {
                    +"To resolve this, we need to send you a verification email."
                }
                br{}
                Box {
                    sx {
                        marginTop = themed(1)
                    }
                    Button {
                        onClick = {
                            Client.postData("/profile/email/start_verification", StartEmailVerification(email))
                        }
                        disabled = stale

                        if (pendingEmailChange != null) {
                            +"Resend verification email"
                        } else {
                            +"Send verification email"
                        }
                    }
                }
            }
        }

        Box {
            sx {
                marginTop = themed(2)
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
                val changed = user.nick != name
                disabled = stale || !changed
                +"Change name"
            }
        }

        Box {
            sx {
                marginTop = themed(1)
                display = Display.flex
                alignItems = AlignItems.flexEnd
            }
            TextField {
                variant = FormControlVariant.standard
                id = "email-field"
                label = ReactNode("Email address")
                value = email
                disabled = stale
                onChange = {
                    email = it.eventValue()
                }
            }

            Button {
                onClick = {
                    Client.postData("/profile/email/start_verification", StartEmailVerification(email))
                    pendingEmailChange = email
                }
                val changed = user.email != email
                disabled = stale || !changed || pendingEmailChange != null
                +"Change email"
            }
        }
    }
}
