package tools.confido.application

import io.ktor.server.application.*
import io.ktor.util.*
import io.ktor.util.logging.*
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.html.*
import kotlinx.html.stream.appendHTML
import org.simplejavamail.api.email.Email
import org.simplejavamail.email.EmailBuilder
import rooms.*
import tools.confido.question.Question
import tools.confido.state.appConfig
import users.*
import kotlin.time.*

class Mailer(
    private val origin: String,
    private val debugMode: Boolean,
    private val mailer: org.simplejavamail.api.mailer.Mailer?,
    private val senderAddress: String,
    private val senderName: String,
    private val logger: Logger,
) {
    private fun sendMail(mail: Email) {
        if (debugMode) {
            logger.info("Debug mode; not sending the following email:")
            logger.info(mail.toString())
        } else {
            mailer!!.sendMail(mail)
        }
    }

    /**
     * Sends an email inviting a user to the instance.
     */
    fun sendUserInviteMail(address: String) {
        if (appConfig.demoMode) return
        val subject = "You have been invited to Confido"

        val body = """
            You have been invited to Confido at address:
            $origin
            """.trimIndent()

        val mail = EmailBuilder.startingBlank()
            .from(senderName, senderAddress)
            .to(address)
            .withSubject(subject)
            .withPlainText(body)
            //.withHTMLText(sb.toString())
            .buildEmail()

        sendMail(mail)
    }

    /**
     * Sends an email inviting a user to a specific [Room].
     */
    fun sendRoomInviteMail(address: String, room: Room, login: LoginLink, invitingUserAddress: String?) {
        if (appConfig.demoMode) return
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
            .from(senderName, senderAddress)
            .to(address)
            .withReplyTo(invitingUserAddress)
            .withSubject(subject)
            .withPlainText(body)
            //.withHTMLText(sb.toString())
            .buildEmail()

        sendMail(mail)
    }

    /**
     * Sends an email with feedback about Confido.
     */
    fun sendFeedbackMail(address: String, feedback: String, instanceName: String) {
        // We cannot easily use trimIndent or trimMargin because feedback may contain newlines.
        val body = """The following feedback was sent from instance $instanceName

$feedback"""

        val mail = EmailBuilder.startingBlank()
            .from(senderName, senderAddress)
            .to(address)
            .withSubject("Confido application feedback")
            .withPlainText(body)
            .buildEmail()

        sendMail(mail)
    }

    fun sendVerificationMail(address: String, verification: EmailVerificationLink, expiration: Duration) {
        if (appConfig.demoMode) return
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
            .from(senderName, senderAddress)
            .to(address)
            .withSubject(subject)
            .withPlainText(body)
            .buildEmail()

        sendMail(mail)
    }

    fun sendPasswordChangedEmail(address: String, resetLink: PasswordResetLink) {
        if (appConfig.demoMode) return
        val subject = "Your Confido password has changed"
        val url = resetLink.link(origin)

        val body = """
            Your password to Confido ($origin) has been changed.
            
            If you did not wish to change your password, you can use the following link:
            $url
            to reset your password. This action will invalidate all your sessions.
            """.trimIndent()

        // TODO: Send multipart with HTML version.

        val mail = EmailBuilder.startingBlank()
            .from(senderName, senderAddress)
            .to(address)
            .withSubject(subject)
            .withPlainText(body)
            .buildEmail()

        sendMail(mail)
    }

    fun sendPasswordResetEmail(address: String, resetLink: PasswordResetLink, expiration: Duration) {
        if (appConfig.demoMode) return
        val subject = "Password reset request"
        val url = resetLink.link(origin)

        val body = """
            Open the following link to reset your password:
            $url
            
            This link will expire in ${expiration.inWholeMinutes} minutes.
            
            If you did not make this request, please ignore this email.
            """.trimIndent()

        // TODO: Send multipart with HTML version.

        val mail = EmailBuilder.startingBlank()
            .from(senderName, senderAddress)
            .to(address)
            .withSubject(subject)
            .withPlainText(body)
            .buildEmail()

        sendMail(mail)
    }

    /**
     * Sends an email with a magic link ([LoginLink]) for logging in.
     */
    fun sendLoginMail(address: String, login: LoginLink, expiration: Duration) {
        if (appConfig.demoMode) return
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
            .from(senderName, senderAddress)
            .to(address)
            .withSubject(subject)
            .withPlainText(body)
            //.withHTMLText(sb.toString())
            .buildEmail()

        sendMail(mail)
    }

    fun sendScheduledResolutionMail(address: String, q: Question) {
        if (appConfig.demoMode) return
        val subject = "Please resolve question '${q.name}'"
        val resolveTime = q.effectiveSchedule.resolve ?: return
        val room = q.room ?: return

        val body = """
            Question '${q.name}' has resolution time scheduled for ${resolveTime.toLocalDateTime(TimeZone.UTC).toString().replace('T',  ' ')} UTC
            but no resolution has yet been set.
            
            Please set the resolution at $origin/rooms/${room.id}/questions/${q.id}/resolve
            """.trimIndent()

        val mail = EmailBuilder.startingBlank()
            .from(senderName, senderAddress)
            .to(address)
            .withSubject(subject)
            .withPlainText(body)
            //.withHTMLText(sb.toString())
            .buildEmail()

        sendMail(mail)
    }
}

internal val MailerKey = AttributeKey<Mailer>("Mailer")
lateinit var globalMailer: Mailer

class MailingConfig {
    /** The URL of the hosted frontend used in mailed links (no trailing /) **/
    var urlOrigin: String = "http://localhost:8081/"
    /** Do not send emails, print to stdout instead. */
    var debugMode: Boolean = false
    /** The underlying mailer with SMTP configuration. */
    var mailer: org.simplejavamail.api.mailer.Mailer? = null
    /** The sender address for all emails. **/
    var senderAddress: String = "noreply@localhost"
    /** The sender name for all emails. **/
    var senderName: String = "Confido"
}

val Mailing: RouteScopedPlugin<MailingConfig> = createRouteScopedPlugin("Mailing", ::MailingConfig) {
    if (pluginConfig.mailer == null && !pluginConfig.debugMode) {
        throw IllegalStateException("No mailer is configured and debug mode is disabled. This would prevent emails " +
                "from being sent. Either configure a mailer or enable debug mode if you do not need mails to be sent.")
    }

    val mailer =
        Mailer(pluginConfig.urlOrigin, pluginConfig.debugMode, pluginConfig.mailer, pluginConfig.senderAddress, pluginConfig.senderName, application.log)
    globalMailer = mailer
    application.attributes.put(
        MailerKey,
        mailer
    )
}

/**
 * Provides access to the Mailer for sending out emails.
 */
val ApplicationCall.mailer: Mailer
    get() = application.attributes[MailerKey]


