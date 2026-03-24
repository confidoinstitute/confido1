package components.redesign.rooms

import Client
import components.AppStateContext
import components.redesign.basic.*
import components.redesign.forms.InlineHelpButton
import components.redesign.questions.QuestionEstimateTabButton
import components.redesign.scores.MyScoresContent
import components.redesign.scores.scoreColor
import emotion.react.css
import hooks.useSuspendResult
import io.ktor.client.call.*
import io.ktor.client.request.*
import normalizedBrier
import payloads.responses.ScoreboardEntry
import react.FC
import react.Props
import react.dom.html.ReactHTML
import react.dom.html.ReactHTML.a
import react.dom.html.ReactHTML.b
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.i
import react.dom.html.ReactHTML.li
import react.dom.html.ReactHTML.p
import react.dom.html.ReactHTML.span
import react.dom.html.ReactHTML.table
import react.dom.html.ReactHTML.tbody
import react.dom.html.ReactHTML.td
import react.dom.html.ReactHTML.th
import react.dom.html.ReactHTML.thead
import react.dom.html.ReactHTML.tr
import react.dom.html.ReactHTML.ul
import react.use
import react.useState
import rooms.RoomPermission
import tools.confido.utils.toFixed
import web.cssom.*
import web.window.WindowTarget
import web.window._blank

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
                target = WindowTarget._blank
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
                        td { +tools.confido.utils.formatPercent(it) }
                        td { +normalizedBrier(it, true).toFixed(1) }
                        td { +normalizedBrier(it, false).toFixed(1) }
                    }
                }
            }
        }
    }

}

val RoomScore = FC<Props> {
    val room = use(RoomContext)!!
    val (appState, stale) = use(AppStateContext)!!
    val canViewScoreboard = appState.hasPermission(room, RoomPermission.VIEW_SCORES)

    var helpOpen by useState(false)
    // Default to Scoreboard when user has VIEW_SCORES, otherwise My scores
    var showMyScores by useState(!canViewScoreboard)

    ScoringHelpDialog {
        open = helpOpen
        onClose = { helpOpen = false }
    }

    // Only fetch scoreboard data when the user can view it
    val scores = useSuspendResult(room.id, canViewScoreboard) {
        if (canViewScoreboard)
            Client.httpClient.get("${room.urlPrefix}/scoreboard.api").body<List<ScoreboardEntry>>()
        else
            null
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
            ReactHTML.div { css { flexGrow = number(1.0) } }
            ReactHTML.div {
                +"This scoring considers "
                b { +"yes-no questions" }
                +" only and uses a normalized Brier score."
                InlineHelpButton {
                    onClick = { helpOpen = true }
                }
            }
            ReactHTML.div { css { flexGrow = number(1.0) } }
        }
    }

    LayoutWidthWrapper {
        // Show toggle only when the user can view the scoreboard;
        // otherwise just show "My scores" directly.
        if (canViewScoreboard) {
            Stack {
                css {
                    padding = Padding(0.px, 15.px)
                    paddingBottom = 12.px
                    background = Color("#f2f2f2")
                    borderRadius = 5.px
                }
                direction = FlexDirection.row
                QuestionEstimateTabButton {
                    +"Scoreboard"
                    active = !showMyScores
                    onClick = { showMyScores = false }
                }
                QuestionEstimateTabButton {
                    +"My scores"
                    active = showMyScores
                    onClick = { showMyScores = true }
                }
            }
        }

        if (showMyScores || !canViewScoreboard) {
            // Render the reusable MyScoresContent filtered to this room
            MyScoresContent {
                this.roomId = room.id
            }
        } else {
            // Render the scoreboard (leaderboard)
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
                    css(rowStyleTableCSS) {
                        width = 100.pct
                    }
                    thead {
                        tr {
                            th { css { width = 1.px }; +"#" }
                            th { +"Name" }
                            th { css { width = 1.px; whiteSpace = WhiteSpace.nowrap }; +"Score" }
                        }
                    }
                    tbody {
                        scores.forEachIndexed { index, entry ->
                            tr {
                                // Highlight the current user's row
                                if (entry.user.isMyself) {
                                    css {
                                        "&&" {
                                            backgroundColor = Color("#ebfaff")
                                        }
                                    }
                                }
                                td {
                                    css {
                                        "&&" {
                                            textAlign = TextAlign.center
                                            color = Color("#888")
                                            fontWeight = integer(500)
                                        }
                                    }
                                    +"${index + 1}"
                                }
                                td {
                                    Stack {
                                        direction = FlexDirection.row
                                        css {
                                            alignItems = AlignItems.center
                                            gap = 8.px
                                        }
                                        // Color circle (avatar)
                                        entry.user.color?.let { color ->
                                            div {
                                                css {
                                                    width = 24.px
                                                    height = 24.px
                                                    borderRadius = 50.pct
                                                    backgroundColor = Color(color)
                                                    flexShrink = number(0.0)
                                                }
                                            }
                                        }
                                        span {
                                            if (entry.user.isMyself) {
                                                css { fontWeight = integer(600) }
                                            }
                                            val name = entry.user.displayName
                                            if (name.isNullOrBlank()) {
                                                i { +"(anonymous)" }
                                            } else {
                                                +name
                                            }
                                        }
                                    }
                                }
                                td {
                                    css {
                                        "&&" {
                                            fontWeight = integer(600)
                                            textAlign = TextAlign.right
                                            backgroundColor = scoreColor(entry.score)
                                        }
                                    }
                                    +entry.score.toFixed(1)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
