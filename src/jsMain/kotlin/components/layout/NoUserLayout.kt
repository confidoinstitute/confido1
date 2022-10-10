package components.layout

import components.nouser.LandingPage
import components.rooms.RoomInviteForm
import mui.material.Toolbar
import mui.system.*
import react.*
import react.router.Route
import react.router.Routes
import utils.byTheme

val NoUserLayout = FC<Props> {
    RootAppBar {
        hasDrawer = false
    }
    Toolbar {}
    Box {
        sx {
            margin = byTheme("auto")
            maxWidth = byTheme("lg")
        }
        Routes {
            Route {
                index = true
                path = "/*"
                this.element = LandingPage.create()
            }
            Route {
                path = "room/:roomID/invite/:inviteToken"
                this.element = RoomInviteForm.create()
            }
        }
    }
}
