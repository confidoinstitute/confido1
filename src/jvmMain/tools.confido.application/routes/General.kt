package tools.confido.application.routes

import tools.confido.api.Api
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import tools.confido.application.mailer

fun genericRoutes(routing: Routing) = routing.apply {
    apiST(Api.feedback) { feedback ->
        val feedbackAddress = System.getenv("CONFIDO_MAIL_FEEDBACK_TO") ?: "feedback@confido.tools"

        // Prefix each line of feedback by [FB]
        val prefixedFeedback = feedback.lines().joinToString("\n") { "[FB] $it" }
        call.application.log.info("Received feedback:\n$prefixedFeedback")

        call.application.log.info("Sending feedback by email")
        val instanceName = System.getenv("CONFIDO_BASE_URL") ?: "?"
        call.mailer.sendFeedbackMail(feedbackAddress, feedback, instanceName)
    }
}
