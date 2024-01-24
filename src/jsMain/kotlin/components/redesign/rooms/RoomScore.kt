package components.redesign.rooms

import components.AppStateContext
import components.redesign.basic.*
import components.redesign.forms.InlineHelpButton
import components.rooms.RoomContext
import csstype.*
import emotion.react.css
import hooks.useSuspendResult
import io.ktor.client.call.*
import io.ktor.client.request.*
import normalizedBrier
import react.FC
import react.Props
import react.dom.html.AnchorTarget
import react.dom.html.ReactHTML
import react.dom.html.ReactHTML.a
import react.dom.html.ReactHTML.b
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.i
import react.dom.html.ReactHTML.li
import react.dom.html.ReactHTML.p
import react.dom.html.ReactHTML.table
import react.dom.html.ReactHTML.tbody
import react.dom.html.ReactHTML.td
import react.dom.html.ReactHTML.th
import react.dom.html.ReactHTML.thead
import react.dom.html.ReactHTML.tr
import react.dom.html.ReactHTML.ul
import react.useContext
import react.useState
import rooms.RoomPermission
import tools.confido.utils.formatPercent
import tools.confido.utils.toFixed

val ScoringHelpDialog = FC<DialogProps> { props->
    Dialog {
        +props
        title = "Scoring"
        p {
            +"The scoring is based on a normalized "
            a {
                css { color = Globals.inherit }
                +"Brier score"
                href = "https://en.wikipedia.org/wiki/Brier_score"
                target = AnchorTarget._blank
            }
            +". Scores can be both positive and negative. Example prediction scores:"
        }

        table {
            css {
                fontSize = 0.9.rem
                margin = Margin(0.px, Auto.auto)
                borderCollapse = BorderCollapse.collapse
                "td, th" {
                    border = Border(1.px, LineStyle.solid, Color("#666"))
                    textAlign = TextAlign.left
                }
            }
            thead {
                tr {
                    th { +"Prediction confidence" }
                    th { +"Score if correct" }
                    th { +"Score if wrong" }
                }
            }
            tbody {
                listOf(0.5, 0.6, 0.7, 0.8, 0.9, 1.0).forEach {
                    tr {
                        td { +formatPercent(it) }
                        td { +normalizedBrier(it, true).toFixed(1) }
                        td { +normalizedBrier(it, false).toFixed(1) }
                    }
                }
            }
        }
    }

}

val RoomScore = FC<Props> {
    val room = useContext(RoomContext)
    val (appState,stale) = useContext(AppStateContext)
    val scores = useSuspendResult(room.id) {
        Client.httpClient.get("${room.urlPrefix}/scoreboard.api").body<List<Pair<String?, Double>>>()
    }
    var helpOpen by useState(false)
    ScoringHelpDialog {
        open = helpOpen
        onClose = {helpOpen = false}
    }
    RoomHeader {
        Stack {
            direction = FlexDirection.row
            css {
                flexGrow = number(1.0)
                alignItems = AlignItems.center
                justifyItems = JustifyItems.center
                marginLeft = 5.px
                marginRight = 5.px
                gap = 0.px
                fontSize = 14.px
                fontWeight = integer(500)
                color = Color("#888")
            }
            ReactHTML.div { css {flexGrow = number(1.0) } }
            ReactHTML.div {
                +"This scoring considers "
                b {+"yes-no questions"}
                +" only and uses a normalized Brier score."
                InlineHelpButton {
                    onClick = { helpOpen = true }
                }
            }
            ReactHTML.div { css {flexGrow = number(1.0) } }
        }
    }
    LayoutWidthWrapper {
        if (scores == null) {
            div {
                css {
                    fontStyle = FontStyle.italic
                    textAlign = TextAlign.center
                }
                +"Loading..."
            }
        } else if (scores.isEmpty()) {
            p { b { +"There are no scored predictions in this room yet." } }
            // Only show the tips to moderators, who can do something about them.
            if (appState.hasPermission(room, RoomPermission.MANAGE_QUESTIONS))
            p {
                +"In order for a prediction on a question to be scored:"
                ul {
                    li { +"The question must be a yes-no question." }
                    li { +"The question must have a resolution and be in the Resolved state." }
                    li { +"The question must have a score time configured in its schedule." }
                    li { +"The prediction must be submitted before the score time." }
                }
            }
        } else {
            table {
                className = baseTableCSS
                thead {
                    tr {
                        th { +"Nickname" }
                        th { +"Score" }
                    }
                }
                tbody {
                    scores.forEach { (nick,score)->
                        tr {
                            td {
                                if (nick.isNullOrBlank()) i { +"(anonymous)" }
                                else {
                                    +nick
                                }
                            }
                            td {
                                +score.toFixed(1)
                            }
                        }
                    }
                }
            }
        }
    }

}