package tools.confido.application

import io.ktor.util.*

fun generateToken(): String {
    return generateNonce() + generateNonce()
}
