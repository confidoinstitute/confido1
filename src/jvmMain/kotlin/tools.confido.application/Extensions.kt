package tools.confido.application

import Extension
import io.ktor.server.routing.*
import tools.confido.question.Prediction
import tools.confido.question.Question
import tools.confido.state.appConfig

interface ServerExtension: Extension {
    suspend fun onPrediction(q: Question, p: Prediction) {

    }

    suspend fun serverInit() {

    }

    fun initRoutes(r: Routing) {

    }

    companion object {
        val registry: MutableMap<String, ServerExtension> = mutableMapOf()
        fun register(ext: ServerExtension) {
            registry[ext.extensionId] = ext
        }
        val enabled get() = appConfig.enabledExtensionIds.mapNotNull { registry[it] }
        inline fun forEach(f: (ServerExtension)->Unit) {  enabled.forEach(f) }
    }
}