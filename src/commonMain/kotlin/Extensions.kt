package tools.confido.extensions

import kotlinx.serialization.modules.PolymorphicModuleBuilder
import tools.confido.state.PresenterView
import tools.confido.state.appConfig

expect val registeredExtensions: List<Extension>

interface Extension {
    fun registerPresenterViews(builder: PolymorphicModuleBuilder<PresenterView>) {
    }

    val extensionId: String
    companion object {
        val enabled by lazy { registeredExtensions.filter { it.extensionId in appConfig.enabledExtensionIds } }
        inline fun forEach(f: (Extension)->Unit) {  enabled.forEach(f) }
    }
}
