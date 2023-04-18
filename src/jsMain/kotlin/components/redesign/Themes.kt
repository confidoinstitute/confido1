package components.redesign

import csstype.*
import react.*

external interface ThemeProviderProps : PropsWithChildren {
    var theme: (outerTheme: Theme) -> Theme
}

val ThemeProvider = FC<ThemeProviderProps> { props ->
    emotion.react.ThemeProvider {
        theme = {
            props.theme(it.unsafeCast<Theme>())
        }
        +props.children
    }
}

data class Theme(
    val colors: ThemeColors
) : emotion.react.Theme

data class ThemeColors(
    val primary: Color,
    val form: FormColors,
)

data class FormColors(
    val background: Color,
    val inputBackground: Color,
)

val DefaultFormColors = FormColors(
    background = Color("#FFFFFF"),
    inputBackground = Color("#F8F8F8"),
)

// TODO: Figure out a name for this
val AltFormColors = FormColors(
    background = Color("#F2F2F2"),
    inputBackground = Color("#FFFFFF"),
)

val DefaultColors = ThemeColors(
    primary = Color("#6319FF"),
    form = DefaultFormColors,
)

val DefaultTheme = Theme(
    colors = DefaultColors,
)

