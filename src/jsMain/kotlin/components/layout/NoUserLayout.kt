package components.layout

import components.nouser.LandingPage
import components.nouser.InviteNewUserForm
import mui.material.Toolbar
import react.*
import react.router.Route
import react.router.Routes

val NoUserLayout = FC<Props> {
    RootAppBar {
        hasDrawer = false
    }
    Toolbar {}
    Routes {
        Route {
            index = true
            path = "/*"
            this.element = LandingPage.create()
        }
        Route {
            path = "room/:roomID/invite/:inviteToken"
            this.element = InviteNewUserForm.create()
        }
    }
}
