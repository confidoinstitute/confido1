package components.redesign.basic

import web.cssom.*
import emotion.css.*

// helper to undo usual browser link styling
val LinkUnstyled = ClassName {
    textDecoration = None.none
    color = Color("inherit")
}
