package tools.confido.extensions

import io.ktor.server.routing.*
import tools.confido.question.Prediction
import tools.confido.question.Question

interface ServerExtension: Extension {
    suspend fun onPrediction(q: Question, p: Prediction) {

    }

    suspend fun serverInit() {

    }

    fun initRoutes(r: Routing) {

    }

    companion object {
        val enabled get() = Extension.enabled.map{ it as ServerExtension }
        inline fun forEach(f: (ServerExtension)->Unit) {  enabled.forEach(f) }
    }
}