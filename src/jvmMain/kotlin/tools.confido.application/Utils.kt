package tools.confido.application

import io.ktor.util.*

fun generateToken(): String {
    // length must correspond to TOKEN_LEN
    return generateNonce() + generateNonce()
}
