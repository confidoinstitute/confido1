package hooks

import browser.document
import react.useLayoutEffect

fun useDocumentTitle(title: String) {
    useLayoutEffect {
        document.title = title
    }
}
