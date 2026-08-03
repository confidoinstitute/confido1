package extensions.calibration_training

import components.AppStateContext
import components.presenter.PresenterPageProps
import components.presenter.presenterPageMap
import components.redesign.basic.*
import components.redesign.calibration.CalibrationGraph
import components.redesign.rooms.RoomLink
import emotion.react.css
import hooks.useSubscription
import js.objects.unsafeJso as jso
import payloads.responses.WSData
import react.*
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.h1
import react.dom.html.ReactHTML.span
import react.dom.svg.ReactSVG.path
import react.dom.svg.ReactSVG.svg
import react.router.RouteObject
import tools.confido.extensions.ClientExtension
import tools.confido.extensions.FlowItemCategory
import tools.confido.extensions.FlowItemTypeEntry
import tools.confido.extensions.SidebarPlace
import tools.confido.state.PresenterView
import tools.confido.utils.toFixed
import web.cssom.*
import web.location.location
import kotlin.reflect.KClass

/**
 * Training/fitness dumbbell icon for calibration training.
 */
external interface TrainingIconProps : PropsWithClassName {
    var size: Int?
    var color: String?
}

val TrainingIcon = FC<TrainingIconProps> { props ->
    val size = props.size ?: 24
    val color = props.color ?: "currentColor"
    
    svg {
        className = props.className
        width = size.toDouble()
        height = size.toDouble()
        viewBox = "0 0 24 24"
        fill = "none"
        
        // Dumbbell icon - horizontal bar with weights on each end
        // Left weight (outer)
        path {
            d = "M2 8.5C2 7.67 2.67 7 3.5 7C4.33 7 5 7.67 5 8.5V15.5C5 16.33 4.33 17 3.5 17C2.67 17 2 16.33 2 15.5V8.5Z"
            fill = color
        }
        // Left weight (inner)
        path {
            d = "M5 9.5C5 8.67 5.67 8 6.5 8C7.33 8 8 8.67 8 9.5V14.5C8 15.33 7.33 16 6.5 16C5.67 16 5 15.33 5 14.5V9.5Z"
            fill = color
        }
        // Center bar
        path {
            d = "M8 11.5H16V12.5H8V11.5Z"
            fill = color
        }
        // Right weight (inner)
        path {
            d = "M16 9.5C16 8.67 16.67 8 17.5 8C18.33 8 19 8.67 19 9.5V14.5C19 15.33 18.33 16 17.5 16C16.67 16 16 15.33 16 14.5V9.5Z"
            fill = color
        }
        // Right weight (outer)
        path {
            d = "M19 8.5C19 7.67 19.67 7 20.5 7C21.33 7 22 7.67 22 8.5V15.5C22 16.33 21.33 17 20.5 17C19.67 17 19 16.33 19 15.5V8.5Z"
            fill = color
        }
    }
}

// =============================================================================
// Calibration Leaderboard Presenter Page
// =============================================================================

/** Map a calibration score (0–100) to a color: red at low, yellow at mid, green at high. */
private fun scoreBarColor(score: Double): Color {
    val hue = (score / 100.0 * 120).coerceIn(0.0, 120.0)
    return Color("hsl(${hue.toFixed(0)}, 60%, 48%)")
}

/**
 * Presenter page component showing the calibration training leaderboard.
 * Supports click-through to individual calibration graphs.
 */
val CalibrationLeaderboardPP = FC<PresenterPageProps<CalibrationLeaderboardPV>> { props ->
    val scores = useSubscription<List<CalibrationLeaderboardEntry>>("/api/ext/calibration_training/leaderboard.ws")
    var selectedEntry by useState<CalibrationLeaderboardEntry?>(null)

    if (scores !is WSData) return@FC

    div {
        css {
            width = 100.vw
            height = 100.vh
            overflow = Overflow.hidden
            fontFamily = string("'Inter', 'Segoe UI', 'Helvetica Neue', sans-serif")
            display = Display.flex
            flexDirection = FlexDirection.column
            alignItems = AlignItems.center
            padding = Padding(3.vh, 4.vw)
            backgroundColor = Color("#fafafa")
        }

        if (selectedEntry != null) {
            // ── Detail view: individual calibration graph ──
            val entry = selectedEntry!!

            // Back button
            div {
                css {
                    alignSelf = AlignSelf.flexStart
                    fontSize = 2.8.vh
                    color = Color("#666")
                    cursor = Cursor.pointer
                    marginBottom = 1.vh
                    userSelect = None.none
                    hover { color = Color("#333") }
                }
                onClick = { selectedEntry = null }
                +"\u2190 Back to leaderboard"
            }

            // Title
            h1 {
                css {
                    fontSize = 5.vh
                    fontWeight = FontWeight.bold
                    color = Color("#333")
                    marginTop = 0.px
                    marginBottom = 1.vh
                }
                +"${entry.nickname}'s Calibration"
            }

            // Score summary
            div {
                css {
                    fontSize = 3.vh
                    color = Color("#555")
                    marginBottom = 2.vh
                    display = Display.flex
                    alignItems = AlignItems.center
                    gap = 2.vw
                }
                span {
                    css { fontWeight = FontWeight.bold; color = scoreBarColor(entry.score) }
                    +"Score: ${entry.score.toFixed(1)}"
                }
                span {
                    css { color = Color("#999") }
                    +"\u00B7"
                }
                span {
                    +"${entry.numAnswered} questions answered"
                }
            }

            // Calibration graph
            div {
                css {
                    width = 70.vw
                    flexGrow = number(1.0)
                    maxHeight = 70.vh
                }
                CalibrationGraph {
                    calib = entry.calibration
                    height = 60.vh
                    who = null
                    areaLabels = true
                }
            }
        } else {
            // ── Leaderboard view ──

            // Title
            div {
                css {
                    fontSize = 5.vh
                    fontWeight = FontWeight.bold
                    color = Color("#333")
                    letterSpacing = 1.px
                    marginBottom = 3.vh
                }
                +"Calibration Leaderboard"
            }

            if (scores.data.isEmpty()) {
                div {
                    css {
                        fontSize = 3.vh
                        color = Color("#999")
                        marginTop = 10.vh
                    }
                    +"No participants have answered enough questions yet."
                }
            } else {
                // Two-column row container
                div {
                    css {
                        width = 85.pct
                        maxHeight = 82.vh
                        overflow = Overflow.hidden
                        columnFill = Auto.auto
                        columnCount = integer(2)
                        columnGap = 32.px
                    }

                    val topScore = scores.data.firstOrNull()?.score

                    scores.data.forEachIndexed { idx, entry ->
                        val isLeader = entry.score == topScore && idx == 0

                        div {
                            css {
                                display = Display.flex
                                flexDirection = FlexDirection.row
                                alignItems = AlignItems.center
                                padding = Padding(10.px, 20.px)
                                marginBottom = 6.px
                                borderRadius = 8.px
                                fontSize = 2.6.vh
                                cursor = Cursor.pointer
                                breakInside = BreakInside.avoid
                                backgroundColor = if (isLeader) Color("#fff8e1") else Color("#fff")
                                if (isLeader) {
                                    borderLeft = Border(3.px, LineStyle.solid, Color("#f9a825"))
                                }
                                boxShadow = BoxShadow(0.px, 1.px, 3.px, Color("rgba(0,0,0,0.08)"))
                                hover {
                                    backgroundColor = if (isLeader) Color("#fff3cc") else Color("#f5f5f5")
                                }
                            }
                            onClick = { selectedEntry = entry }

                            // Rank
                            span {
                                css {
                                    color = Color("#999")
                                    marginRight = 12.px
                                    minWidth = 3.5.vh
                                    textAlign = TextAlign.right
                                }
                                +"${idx + 1}."
                            }

                            // Name
                            span {
                                css {
                                    flexGrow = number(1.0)
                                    color = Color("#333")
                                    fontWeight = if (isLeader) FontWeight.bold else FontWeight.normal
                                }
                                +entry.nickname
                            }

                            // Score bar + number
                            div {
                                css {
                                    display = Display.flex
                                    alignItems = AlignItems.center
                                    gap = 8.px
                                    minWidth = 18.vh
                                }
                                // Mini bar
                                div {
                                    css {
                                        width = 10.vh
                                        height = 1.2.vh
                                        backgroundColor = Color("#e8e8e8")
                                        borderRadius = 3.px
                                        overflow = Overflow.hidden
                                    }
                                    div {
                                        css {
                                            height = 100.pct
                                            width = entry.score.coerceIn(0.0, 100.0).pct
                                            backgroundColor = scoreBarColor(entry.score)
                                            borderRadius = 3.px
                                        }
                                    }
                                }
                                // Score number
                                span {
                                    css {
                                        fontWeight = FontWeight.bold
                                        fontFamily = string("monospace")
                                        fontSize = 2.8.vh
                                        color = Color("#333")
                                        minWidth = 6.vh
                                        textAlign = TextAlign.right
                                    }
                                    +entry.score.toFixed(1)
                                }
                            }

                            // Question count
                            span {
                                css {
                                    color = Color("#aaa")
                                    fontSize = 2.vh
                                    marginLeft = 12.px
                                }
                                +"${entry.numAnswered} q"
                            }
                        }
                    }
                }
            }
        }
    }
}

// =============================================================================
// Client Extension Object
// =============================================================================

/**
 * Client extension for calibration training.
 */
object CalibrationTrainingCE : ClientExtension, CalibrationTrainingExtension() {

    override fun registerPresenterPages(): Map<KClass<out PresenterView>, components.presenter.PresenterPageType> =
        mapOf(presenterPageMap(CalibrationLeaderboardPP))

    override fun registerFlowItemTypes() = listOf(
        FlowItemTypeEntry(
            key = "calibrationTrainingLeaderboard",
            label = "Calibration training leaderboard",
            category = FlowItemCategory.BOTH,
            createDefault = { ShowCalibrationLeaderboardAction() },
            editor = null,
            matches = { it is ShowCalibrationLeaderboardAction },
        )
    )

    override fun sidebarExtra(place: SidebarPlace, small: Boolean, cb: ChildrenBuilder) {
        if (place != SidebarPlace.WORKSPACE_BOTTOM) return
        
        val iconSize = if (small) 36.px else 48.px
        val iconSizeInner = if (small) 24 else 32
        
        cb.apply {
            RoomLink {
                key = "::training"
                to = "/training"
                this.small = small
                highlighted = location.pathname == "/training" || location.pathname.startsWith("/training/")
                
                div {
                    css {
                        width = iconSize
                        height = iconSize
                        display = Display.flex
                        alignItems = AlignItems.center
                        justifyContent = JustifyContent.center
                        flexShrink = number(0.0)
                    }
                    TrainingIcon {
                        size = iconSizeInner
                        color = "#bbb"
                    }
                }
                span {
                    +"Calibration training"
                }
            }
        }
    }
    
    override fun rootRoutesExtra(): List<RouteObject> {
        return listOf(
            jso {
                path = "training"
                element = TrainingPage.create()
            },
            jso {
                path = "training/stats"
                element = TrainingStatsPage.create()
            },
        )
    }
}
