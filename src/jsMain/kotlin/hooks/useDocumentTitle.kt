package hooks

import browser.document
import react.useLayoutEffect

/**
 * Sets the document title and appends ` - Confido` to it.
 *
 * See also [useDocumentTitleRaw] in case you do want to avoid the suffix.
 */
fun useDocumentTitle(title: String) {
    useDocumentTitleRaw("$title - Confido")
}

/**
 * Sets the document title to the provided title
 *
 * See also [useDocumentTitle] in case you want to automatically append the name of the application.
 */
fun useDocumentTitleRaw(title: String) {
    useLayoutEffect {
        document.title = title
    }
}
