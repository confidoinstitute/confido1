package tools.confido.application

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun genericRoutes(routing: Routing) = routing.apply {
    postST("/feedback") {
        val url = call.parameters["url"]
        val feedback = call.receiveText()

        val feedbackAddress = System.getenv("CONFIDO_MAIL_FEEDBACK_TO") ?: "feedback@confido.tools"

        // Prefix each line of feedback by [FB]
        val prefixedFeedback = feedback.lines().joinToString("\n") { "[FB] $it" }
        call.application.log.info("Received feedback:\n$prefixedFeedback")

        call.application.log.info("Sending feedback by email")
        val instanceName = System.getenv("CONFIDO_BASE_URL") ?: "?"
        call.mailer.sendFeedbackMail(feedbackAddress, feedback, instanceName)

        call.respond(HttpStatusCode.OK)
    }
}
