package tools.confido.application

import tools.confido.refs.*
import tools.confido.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.datetime.Clock
import org.simplejavamail.MailException
import payloads.requests.*
import rooms.*
import tools.confido.application.sessions.transientUserData
import tools.confido.application.sessions.userSession
import tools.confido.question.Question
import tools.confido.state.*
import tools.confido.utils.randomString
import users.LoginLink
import users.User
import users.UserType
import kotlin.time.Duration.Companion.days

fun editQuestion(routing: Routing) {

}