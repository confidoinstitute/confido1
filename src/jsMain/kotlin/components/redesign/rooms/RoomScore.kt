package components.redesign.rooms

import components.redesign.basic.LayoutWidthWrapper
import components.redesign.basic.Stack
import components.redesign.basic.baseTableCSS
import components.rooms.RoomContext
import csstype.*
import emotion.react.css
import hooks.useSuspendResult
import io.ktor.client.call.*
import io.ktor.client.request.*
import react.FC
import react.Props
import react.dom.html.ReactHTML
import react.dom.html.ReactHTML.b
import react.dom.html.ReactHTML.i
import react.dom.html.ReactHTML.table
import react.dom.html.ReactHTML.tbody
import react.dom.html.ReactHTML.td
import react.dom.html.ReactHTML.th
import react.dom.html.ReactHTML.thead
import react.dom.html.ReactHTML.tr
import react.useContext
import tools.confido.utils.toFixed

val RoomScore = FC<Props> {
    val room = useContext(RoomContext)
    val scores = useSuspendResult(room.id) {
        Client.httpClient.get("${room.urlPrefix}/scoreboard.api").body<List<Pair<String?, Double>>>()
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
                b {+"binary questions"}
                +" only."
            }
            ReactHTML.div { css {flexGrow = number(1.0) } }
        }
    }
    LayoutWidthWrapper {
        scores?.let {
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