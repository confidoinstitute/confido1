package tools.confido.web.hooks

import tools.confido.web.components.*

fun useTheme(): Theme {
    return emotion.react.useTheme().unsafeCast<Theme>()
}
