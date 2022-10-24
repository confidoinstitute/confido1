package tools.confido.application

import io.ktor.server.application.*
import io.ktor.util.*
import kotlinx.html.*
import kotlinx.html.stream.appendHTML
import org.simplejavamail.api.email.Email
import org.simplejavamail.email.EmailBuilder
import rooms.*
import users.*
import kotlin.time.*

class Mailer(
    private val origin: String,
    private val debugMode: Boolean,
    private val mailer: org.simplejavamail.api.mailer.Mailer?,
    private val senderAddress: String,
) {
    private fun sendMail(mail: Email) {
        if (debugMode) {
            println("Debug mode; not sending the following email:")
            println(mail.toString())
        } else {
            mailer!!.sendMail(mail)
        }
    }

    fun sendUserInviteMail(address: String) {
        val subject = "You have been invited to Confido"

        val body = """
            You have been invited to Confido at address:
            $origin
            """.trimIndent()

        val mail = EmailBuilder.startingBlank()
            .from(senderAddress)
            .to(address)
            .withSubject(subject)
            .withPlainText(body)
            //.withHTMLText(sb.toString())
            .buildEmail()

        sendMail(mail)
    }

    fun sendRoomInviteMail(address: String, room: Room, login: LoginLink, invitingUserAddress: String?) {
        // TODO: Improve subject
        // Subject ideas:
        // You have been invited to a Confido room
        // You have been invited to Confido           (maybe for new user only?)
        // {name} invited you to a Confido room       (when we have a username)
        // [Confido] {name} invited you to {roomName} (when we have a username)
        val subject = "You have been invited to a Confido room"
        val url = login.link(origin)

        // TODO: Add some blurb about what Confido is

        val body = """
            You have been invited to ${room.name}.
            
            Visit the following link to accept this invitation:
            $url
            """.trimIndent()

        val sb = StringBuilder()
        with(sb.appendHTML()) {
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

        val mail = EmailBuilder.startingBlank()
            .from(senderAddress)
            .to(address)
            .withReplyTo(invitingUserAddress)
            .withSubject(subject)
            .withPlainText(body)
            //.withHTMLText(sb.toString())
            .buildEmail()

        sendMail(mail)
    }

    fun sendVerificationMail(address: String, verification: EmailVerificationLink, expiration: Duration) {
        val subject = "Verify your email address"
        val url = verification.link(origin)

        val body = """
            Open the following link to verify your email address:
            $url
            
            This link will expire in ${expiration.inWholeMinutes} minutes.
            
            If you did not make this request, please ignore this email.
            """.trimIndent()

        // TODO: Send multipart with HTML version.

        val mail = EmailBuilder.startingBlank()
            .from(senderAddress)
            .to(address)
            .withSubject(subject)
            .withPlainText(body)
            .buildEmail()

        sendMail(mail)
    }

    fun sendLoginMail(address: String, login: LoginLink, expiration: Duration) {
        val subject = "Log in to Confido"
        val url = login.link(origin)

        val body = """
            Confirm that you want to log into Confido. This link will expire in ${expiration.inWholeMinutes} minutes.
            
            Open the following link to log in:
            $url
            
            If you did not make this request, please ignore this email.
            """.trimIndent()

        // TODO: Send multipart with HTML version.
        val sb = StringBuilder()
        with(sb.appendHTML()) {
            html {
                body {
                    div {
                        p {
                            +"""
                            Confirm that you want to log into Confido. This link will expire in ${expiration.inWholeMinutes} minutes.
                            Click the following button to log in:
                        """.trimIndent()
                        }
                    }
                    a {
                        href = url
                        +url
                        div {
                            +"Log in"
                        }
                    }
                    i {
                        hr {}
                        +"If you did not make this request, please ignore this email."
                    }
                }
            }
        }

        val mail = EmailBuilder.startingBlank()
            .from(senderAddress)
            .to(address)
            .withSubject(subject)
            .withPlainText(body)
            //.withHTMLText(sb.toString())
            .buildEmail()

        sendMail(mail)
    }
}

internal val MailerKey = AttributeKey<Mailer>("Mailer")

class MailingConfig {
    var urlOrigin: String = "http://localhost:8081/"
    /** Do not send emails, print to stdout instead. */
    var debugMode: Boolean = false
    var mailer: org.simplejavamail.api.mailer.Mailer? = null
    var senderAddress: String = "noreply@localhost"
}

val Mailing: RouteScopedPlugin<MailingConfig> = createRouteScopedPlugin("Mailing", ::MailingConfig) {
    if (pluginConfig.mailer == null && !pluginConfig.debugMode) {
        throw IllegalStateException("No mailer is configured and debug mode is disabled. This would prevent emails " +
                "from being sent. Either configure a mailer or enable debug mode if you do not need mails to be sent.")
    }

    application.attributes.put(
        MailerKey,
        Mailer(pluginConfig.urlOrigin, pluginConfig.debugMode, pluginConfig.mailer, pluginConfig.senderAddress)
    )
}

/**
 * Provides access to the Mailer for sending out emails.
 */
val ApplicationCall.mailer: Mailer
    get() = application.attributes[MailerKey]


