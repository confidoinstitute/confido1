package tools.confido.application

import io.ktor.server.application.*
import io.ktor.util.*
import kotlinx.html.*
import kotlinx.html.dom.createHTMLDocument
import rooms.*
import users.*
import kotlin.time.*

class Mailer(
    private val origin: String
) {
    private fun sendMail(address: String, subject: String, body: String) {
        println("WARNING: NOT SENDING EMAIL; TODO implement me; printing instead")
        println("mail to: $address")
        println("Subject: $subject")
        println(body)
    }

    fun sendInviteMail(address: String, room: Room, invite: InviteLink) {
        // TODO: Improve subject
        // Subject ideas:
        // You have been invited to a Confido room
        // You have been invited to Confido           (maybe for new user only?)
        // {name} invited you to a Confido room       (when we have a username)
        // [Confido] {name} invited you to {roomName} (when we have a username)
        val subject = "You have been invited to a Confido room"
        val url = "TODO" // TODO !!

        // TODO: Add some blurb about what Confido is

        val body = """
            You have been invited to ${room.name}.
            
            Visit $url to accept this invitation.
            """.trimIndent()

        val document = createHTMLDocument().html {
            body {
                h1 {
                    +"You have been invited to "
                    a {
                        href = url
                        +room.name
                    }
                    +"."
                }
                div {
                    +"Visit "
                    a {
                        href = url
                        +url
                    }
                    +" to accept this invitation."
                }
            }
        }
        // TODO: Send multipart with HTML version.

        sendMail(address, subject, body)
    }

    fun sendLoginMail(address: String, login: LoginLink, expiration: Duration) {
        val subject = "Your Confido login link"
        val url = login.link(origin)

        val body = """
            Confirm that you want to log into Confido. This link will expire in ${expiration.inWholeMinutes} minutes.
            
            Open the following link to log in:
            $url
            
            If you did not make this request, please ignore this email.
            """.trimIndent()

        val document = createHTMLDocument().html {
            body {
                div {
                    p {
                        +"""
                            Confirm that you want to log into Confido. This link will expire in 30 minutes.
                            Open the following link to log in:
                        """.trimIndent()
                    }
                }
                div {
                    +"Log in: "
                    a {
                        href = url
                        +url
                    }
                }
                i {
                    hr {}
                    +"If you did not make this request, please ignore this email."
                }
            }
        }
        // TODO: Send multipart with HTML version.

        sendMail(address, subject, body)
    }
}

internal val MailerKey = AttributeKey<Mailer>("Mailer")

class MailingConfig {
    var urlOrigin: String = "http://localhost:8081/"
}

val Mailing: RouteScopedPlugin<MailingConfig> = createRouteScopedPlugin("Mailing", ::MailingConfig) {
    application.attributes.put(MailerKey, Mailer(pluginConfig.urlOrigin))
}

/**
 * Provides access to the Mailer for sending out emails.
 */
val ApplicationCall.mailer: Mailer
    get() = application.attributes[MailerKey]


