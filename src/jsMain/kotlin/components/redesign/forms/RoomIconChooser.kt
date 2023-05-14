package components.redesign.forms

import components.redesign.basic.*
import components.showError
import csstype.*
import emotion.react.*
import io.ktor.client.call.*
import io.ktor.http.*
import react.*
import react.dom.html.ReactHTML.div
import rooms.RoomColor
import utils.runCoroutine

external interface RoomIconChooserProps : Props {
    var icon: String?
    var onChange: ((String?) -> Unit)?
    var color: RoomColor
}

val RoomIconChooser = FC<RoomIconChooserProps> { props ->
    var iconNames by useState<List<String>>(emptyList())

    useEffectOnce {
        runCoroutine {
            Client.send("/rooms/icons",
                method = HttpMethod.Get,
                onError = {
                    showError(it)
                }) {
                iconNames = body()
            }
        }
    }


    if (iconNames.isNotEmpty()) {
        Stack {
            direction = FlexDirection.column
            css {
                gap = 10.px
            }
            div {
                css {
                    fontFamily = sansSerif
                    fontWeight = integer(500)
                    fontSize = 14.px
                    lineHeight = 17.px
                }
                +"Icon"
            }
            Stack {
                direction = FlexDirection.row
                css {
                    backgroundColor = Color("#FFFFFF")
                    justifyContent = JustifyContent.start
                    rowGap = 6.px
                    columnGap = 15.px
                    padding = 5.px
                    borderRadius = 8.px
                    height = 130.px
                    overflow = Auto.auto
                    flexWrap = FlexWrap.wrap
                }

                iconNames.map { icon ->
                    val checked = props.icon == icon
                    Checkbox {
                        mask = url("/static/icons/$icon")
                        this.palette = props.color.palette
                        this.checked = checked
                        maskColor = if (checked) {
                            props.color.palette.text.color
                        } else {
                            Color("#000000")
                        }
                        noCheckmark = true
                        alwaysColorBackground = false
                        onChange = { props.onChange?.invoke(icon) }
                    }
                }
            }
        }
    }
}
