package components.rooms

import mui.material.Typography
import mui.material.styles.TypographyVariant
import react.*

val NewRoom = FC<Props> {
    Typography {
        variant = TypographyVariant.h1
        +"Create new room"
    }
}
