package tools.confido.web.hooks

import tools.confido.web.components.redesign.*

fun useTheme(): Theme {
    return emotion.react.useTheme().unsafeCast<Theme>()
}
