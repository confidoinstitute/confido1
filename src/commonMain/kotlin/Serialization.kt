package tools.confido.serialization

import kotlinx.serialization.json.Json
import tools.confido.distributions.distributionsSM


val confidoSM = distributionsSM // here we can add other SMs


val confidoJSON = Json {
    allowSpecialFloatingPointValues = true // support e.g. infinite ranges
    serializersModule = confidoSM
    ignoreUnknownKeys = true
}