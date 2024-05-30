
import components.redesign.questions.predictions.MyPredictionDescriptionProps
import components.rooms.RoomListProps
import react.ChildrenBuilder
import tools.confido.state.appConfig

interface ClientExtension : Extension {

    companion object {
        val registry: MutableMap<String, ClientExtension> = mutableMapOf()
        fun register(ext: ClientExtension) {
            registry[ext.extensionId] = ext
        }

        val enabled get() = appConfig.enabledExtensionIds.mapNotNull { registry[it] }

        inline fun forEach(f: (ClientExtension)->Unit) {  enabled.forEach(f) }
    }

    fun rootRoutes(cb: ChildrenBuilder) {}
    fun clientInit() {}
    fun roomListExtra(props: RoomListProps, cb: ChildrenBuilder) {}

    fun myPredictionDescriptionExtra(props: MyPredictionDescriptionProps, cb: ChildrenBuilder) {}
}