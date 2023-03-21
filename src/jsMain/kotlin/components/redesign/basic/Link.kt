package components.redesign.basic

import csstype.*
import emotion.css.*

// helper to undo usual browser link styling
val LinkUnstyled = ClassName {
    textDecoration = None.none
    color = Color("inherit")
}
