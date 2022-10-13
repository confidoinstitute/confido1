package  components
import mui.material.Button
import mui.material.Size
import react.FC
import react.Fragment
import react.PropsWithChildren
import react.useState


val SpoilerButton = FC<PropsWithChildren> { props ->
    var shown by useState(false)

    when(shown) {
        false -> Button {
            +"Show"
            size = Size.small
            onClick = { shown = true }
        }
        true -> Fragment {
            this.children = props.children
        }
    }
}
