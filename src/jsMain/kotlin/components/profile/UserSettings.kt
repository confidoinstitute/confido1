package components.profile

import components.AppStateContext
import mui.material.*
import mui.material.styles.TypographyVariant
import mui.system.sx
import react.*
import react.dom.onChange
import payloads.requests.SetNick
import payloads.requests.StartEmailVerification
import react.dom.html.ReactHTML.br
import users.UserType
import utils.eventValue
import utils.isEmailValid
import utils.themed

val UserSettings = FC<Props> {
    val (appState, stale) = useContext(AppStateContext)
    val user = appState.session.user ?: run {
        console.error("No user")
        return@FC
    }

    var name by useState(user.nick ?: "")
    var email by useState(user.email ?: "")

    var emailError by useState<String?>(null);

    var nameUpdated by useState(false);

    // Note that this is currently only stored on the frontend.
    // Navigating away from user settings and coming back will show the user the old email
    // without any verification prompt.
    var pendingEmailChange by useState<String?>(null)

    fun changeEmail() {
        val changed = (user.email ?: "") != email
        if (!changed) {
            emailError = "This is already your current email address.";
            return
        }
        if (!isEmailValid(email)) {
            emailError = "This email address is not valid."
            return
        }

        Client.postData("/profile/email/start_verification", StartEmailVerification(email))
        emailError = null
        pendingEmailChange = email
    }

    fun changeName() {
        Client.postData("/profile/nick", SetNick(name))
        nameUpdated = true
    }

    Typography {
        sx {
            marginBottom = themed(4)
        }
        variant = TypographyVariant.h2
        +"User settings"
    }

    Stack {
        spacing = themed(2);

        if (user.type == UserType.GUEST && user.email == null && pendingEmailChange == null) {
            Alert {
                AlertTitle {
                    +"No email is set!"
                }
                severity = AlertColor.error
                +"It is impossible to log back in without having an email set. Please set an email below if you want to return later."
            }
        } else {
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
                    br {}
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
        }

        if (nameUpdated) {
            Alert {
                severity = AlertColor.success
                +"Your name has been updated."
            }
        }

        val textFieldVariant = FormControlVariant.outlined

        Card {
            CardHeader {
                title = ReactNode("Profile")
            }
            CardContent {
                Stack {
                    TextField {
                        variant = textFieldVariant
                        id = "name-field"
                        label = ReactNode("Name")
                        helperText = ReactNode("Your name may appear whenever you comment or make predictions. You can change it at any time.")
                        value = name
                        disabled = stale
                        onChange = {
                            name = it.eventValue()
                        }
                        onKeyUp = {
                            if (it.key == "Enter") {
                                changeName()
                            }
                        }
                    }
                }
            }
            CardActions {
                sx {
                    padding = themed(2)
                }
                Button {
                    onClick = { changeName() }
                    variant = ButtonVariant.contained
                    disabled = stale

                    if (user.nick == null) {
                        +"Set name"
                    } else {
                        +"Update name"
                    }
                }
            }
        }

        Card {
            CardHeader {
                title = ReactNode("Email address")
            }

            CardContent {
                // TODO: Better explanation of email visibility
                Stack {
                    // TODO: full-width or not?
                    TextField {
                        variant = textFieldVariant
                        id = "email-field"
                        label = ReactNode("Email address")
                        helperText = emailError?.let { ReactNode(it) }
                            ?: ReactNode("This email address is used for logging into your account and may be shown to other members of the organization.")
                        error = emailError != null
                        required = true
                        value = email
                        disabled = stale
                        onChange = {
                            email = it.eventValue()
                        }
                        onKeyUp = {
                            if (it.key == "Enter") {
                                changeEmail()
                            }
                        }
                    }
                }
            }

            CardActions {
                sx {
                    padding = themed(2)
                }
                Button {
                    onClick = { changeEmail() }
                    variant = ButtonVariant.contained
                    disabled = stale
                    +"Update email"
                }
            }
        }
    }
}
