package extensions.pseudonyms

import components.redesign.forms.*
import web.cssom.*
import emotion.react.css
import react.*
import react.dom.html.ReactHTML.a
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.span
import rooms.Room
import tools.confido.extensions.ClientExtension
import tools.confido.extensions.ExtensionData
import tools.confido.extensions.get
import tools.confido.extensions.with

object PseudonymsCE : ClientExtension, PseudonymsExtension() {
    override fun editRoomSettingsExtra(
        room: Room?,
        onExtensionDataChange: ((ExtensionData) -> ExtensionData) -> Unit,
        cb: ChildrenBuilder,
    ) {
        val existingList = room?.extensionData?.get(PseudonymsListKey) ?: emptyList()
        var rawText by useState(existingList.joinToString("\n"))
        var showPseudonyms by useState(existingList.isNotEmpty())

        cb.apply {
            FormSection {
                if (showPseudonyms) {
                    title = "Pseudonyms"
                    FormField {
                        this.title = "Pseudonym list"
                        comment = "Enter pseudonyms, one per line. Duplicates will be removed and the list will be sorted alphabetically."
                        MultilineTextInput {
                            placeholder = "e.g.\nAlpha\nBravo\nCharlie\nDelta"
                            value = rawText
                            autoHeight = true
                            onChange = { event ->
                                val newText = event.target.value
                                rawText = newText
                                val parsed = newText.lines()
                                    .map { it.trim() }
                                    .filter { it.isNotEmpty() }
                                onExtensionDataChange { ed -> ed.with(PseudonymsListKey, parsed) }
                            }
                        }
                    }
                    val pseudonymCount = rawText.lines()
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                        .distinct()
                        .size
                    val memberCount = room?.members?.size ?: 0
                    div {
                        css {
                            fontSize = 12.px
                            fontFamily = components.redesign.basic.sansSerif
                            lineHeight = 14.px
                        }
                        span {
                            css {
                                color = if (pseudonymCount < memberCount) Color("#F35454") else Color("#AAAAAA")
                            }
                            +"$pseudonymCount pseudonym${if (pseudonymCount != 1) "s" else ""}"
                        }
                        span {
                            css { color = Color("#AAAAAA") }
                            +", $memberCount member${if (memberCount != 1) "s" else ""}"
                        }
                        if (pseudonymCount < memberCount) {
                            span {
                                css { color = Color("#F35454") }
                                +" — not enough pseudonyms for all members"
                            }
                        }
                    }
                    div {
                        a {
                            href = "#"
                            +"Disable pseudonyms"
                            onClick = {
                                rawText = ""
                                showPseudonyms = false
                                onExtensionDataChange { ed -> ed.with(PseudonymsListKey, emptyList()) }
                                it.preventDefault()
                            }
                        }
                    }
                } else {
                    a {
                        href = "#"
                        +"Configure pseudonyms"
                        onClick = {
                            showPseudonyms = true
                            it.preventDefault()
                        }
                    }
                }
            }
        }
    }
}
