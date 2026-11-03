@file:JsModule("react-helmet")
@file:JsNonModule
package tools.confido.web.ext.helmet

import react.ComponentType
import react.PropsWithChildren

external interface HelmetProps : PropsWithChildren {
    var titleTemplate: String
    var defaultTitle: String
}

@JsName("Helmet")
external val Helmet: ComponentType<HelmetProps>