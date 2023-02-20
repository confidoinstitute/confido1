package components.redesign.layout

import components.AppStateWebsocketProvider
import react.FC
import react.Props

val RootLayout = FC<Props> {
    AppStateWebsocketProvider {
        loadingComponent = LoadingLayout
        RootLayoutInner {}
    }
}

val RootLayoutInner = FC<Props> {

}