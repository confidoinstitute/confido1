package tools.confido.web.components

import web.dom.document
import tools.confido.web.components.layout.PresenterLayout
import tools.confido.web.components.redesign.Header
import web.cssom.*
import js.objects.unsafeJso as jso
import react.*
import react.router.RouteObject
import react.router.RouterProvider
import react.router.dom.createBrowserRouter
import tools.confido.state.appConfig
import web.location.location
import web.url.URL

val LoginContext = createContext<Login>()
data class Login(val isLoggedIn: Boolean, private val changeState: (Boolean) -> Unit) {
    /** Change state to logged in. The session cookie must be set already. **/
    fun login() = changeState(true)
    /** Change state to logged out. **/
    fun logout() {
        // The session cookie itself is HttpOnly; the server clears it on POST /logout.
        // Here we can only clear the login hint cookie, which matters when the server
        // has already dropped the session (websocket closed as unauthorized).
        document.cookie = "logged_in=; Path=/; expires=Thu, 01 Jan 1970 00:00:01 GMT"
        changeState(false)
    }
}


external interface AppProps : Props {
    var isLoggedIn: Boolean
}



val AppMobile = memo(FC<AppProps> {props ->
    val isLoggedIn = props.isLoggedIn
    
    val router = useMemo(isLoggedIn) {
        val layout = if (!isLoggedIn)
            tools.confido.web.components.redesign.layout.NoUserLayout
        else
            tools.confido.web.components.redesign.layout.RootLayout
            
        createBrowserRouter(arrayOf(
            jso<RouteObject> {
                path = "/*"
                element = layout.create {}
            },
            jso<RouteObject> {
                path = "/presenter"
                element = PresenterLayout.create()
            }
        ))
    }
    
    RouterProvider {
        this.router = router
    }
})

fun mobileFlag(): Boolean {
    val params = URL(location.href).searchParams
    val version = params.get("version")?.let {
        web.storage.localStorage.setItem("layoutVersion", it)
        it
    } ?: web.storage.localStorage.getItem("layoutVersion") ?: "mobile"
    return version == "mobile"
}

// The session cookie is HttpOnly and thus invisible here; the server maintains a
// separate non-HttpOnly hint cookie that mirrors whether a user is logged in.
fun cookieSet() = document.cookie.contains("\\blogged_in=".toRegex())

val App = FC<Props> {
    // TODO: initial state from cookie
    var isLoggedIn by useState(cookieSet())

    val mobileFlag = mobileFlag()

    Header {
        title = ""
    }

    LoginContext.Provider {
        value = Login(isLoggedIn) { isLoggedIn = it }

        AppMobile { this.isLoggedIn = isLoggedIn }
    }
}
