package tools.confido.application.routes

import io.ktor.http.*
import payloads.responses.WSErrorType

class ResponseError(override val message: String, val httpCode: HttpStatusCode, val wsCode: WSErrorType) : Exception()

fun notFound(message: String): Nothing = throw ResponseError(message, HttpStatusCode.NotFound, WSErrorType.NOT_FOUND)
fun unauthorized(message: String): Nothing = throw ResponseError(message, HttpStatusCode.Unauthorized, WSErrorType.UNAUTHORIZED)
fun badRequest(message: String): Nothing = throw ResponseError(message, HttpStatusCode.BadRequest, WSErrorType.BAD_REQUEST)
fun serviceUnavailable(message: String): Nothing = throw ResponseError(message, HttpStatusCode.ServiceUnavailable, WSErrorType.BAD_REQUEST)