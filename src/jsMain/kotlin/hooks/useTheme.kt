package hooks

import components.redesign.*

fun useTheme(): Theme {
    return emotion.react.useTheme().unsafeCast<Theme>()
}
