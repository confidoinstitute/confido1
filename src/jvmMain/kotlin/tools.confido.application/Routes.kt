package tools.confido.application

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import tools.confido.application.sessions.userSession

fun genericRoutes(routing: Routing) = routing.apply {
    postST("/feedback") {
        val url = call.parameters["url"]
        val feedback = call.receiveText()

        // TODO actually send it!
        println("=== FEEDBACK ===")
        println("URL: $url")
        call.userSession?.let {
            println("App state: ${StateCensor(it).censor()}")
        }
        println(feedback)

        call.respond(HttpStatusCode.OK)
    }
}
