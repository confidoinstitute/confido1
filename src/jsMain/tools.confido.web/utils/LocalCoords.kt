package tools.confido.web.utils

import web.html.HTMLElement

/**
 * [clientX] in this element's own CSS px, from its left border edge.
 *
 * Client coordinates (and [HTMLElement.getBoundingClientRect]) are visual pixels:
 * under an ancestor's CSS `zoom` (the presenter screens) or a scale transform they
 * differ from the element's layout px, which is what charts draw in. The ratio of
 * the rendered to the layout width ([HTMLElement.offsetWidth]) undoes that; it is
 * 1 everywhere else.
 */
fun HTMLElement.localX(clientX: Double): Double {
    val rect = getBoundingClientRect()
    val scale = if (offsetWidth > 0 && rect.width > 0) rect.width / offsetWidth else 1.0
    return (clientX - rect.left) / scale
}
