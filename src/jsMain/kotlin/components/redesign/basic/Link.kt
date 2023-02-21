package components.redesign.basic

import csstype.Color
import csstype.None
import emotion.css.ClassName

// helper to undo usual browser link styling
val LinkUnstyled = ClassName {
    textDecoration = None.none
    color = Color("inherit")
}
