package components.redesign.basic

import csstype.*
import react.*
import rooms.RoomColor
import tools.confido.question.QuestionState

interface Palette {
    val color: Color
}

val UIGrayBg = Color("#F2F2F2")



enum class TextPalette(override val color: Color): Palette {
    white(Color("#FFFFFF")),
    black(Color("#000000")),
    almostBlack(Color("#222222")),
    gray(Color("#DDDDDD")),
    action(Color("#631AFF")),
    login(Color("#6733DA")),

    ;
    val hoverColor: Color get() = Color(color.toString() + "10")
}

interface PaletteWithText: Palette {
    override val color: Color
    val text: TextPalette
}

enum class MainPalette(override val color: Color, override val text: TextPalette): PaletteWithText {
    default(Color("#FFFFFF"), TextPalette.black),
    primary(Color("#6319FF"), TextPalette.white),
    secondary(Color("#00C2FF"), TextPalette.white),
    login(Color("#6733DA"), TextPalette.white),
    loginInverted(Color("#FFFFFF"), TextPalette.login),
    center(Color("#FF8A00"), TextPalette.white),
}

enum class RoomPalette(override val color: Color, override val text: TextPalette): PaletteWithText {
    red(Color("#F61E4B"), TextPalette.white),
    orange(Color("#FF8E3C"), TextPalette.white),
    yellow(Color("#FFD600"), TextPalette.almostBlack),
    green(Color("#00CA39"), TextPalette.white),
    cyan(Color("#5FE0FF"), TextPalette.almostBlack),
    blue(Color("#3055F1"), TextPalette.white),
    magenta(Color("#AF66E1"), TextPalette.white),
    gray(Color("#505050"), TextPalette.white),
}

enum class QuestionPalette(override val color: Color): Palette {
    draft(Color("#ADBDC2")),
    `open`(Color("#4F24CA")),
    closed(Color("#0079D0")),
    resolved(Color("#00AA59")),
    annulled(Color("#765953")),
}

external interface PropsWithPalette<P: Palette>: Props {
    var palette: P?
}

val RGB_RE = Regex("^#([0-9a-fA-F]{2})([0-9a-fA-F]{2})([0-9a-fA-F]{2})$")
fun Color.addAlpha(alpha: Double): Color {
    val s = this.toString()
    val m = RGB_RE.matchEntire(s)
    if (m == null) return this // cannot handle this color
    val r = m.groupValues[1].toInt(16)
    val g = m.groupValues[2].toInt(16)
    val b = m.groupValues[3].toInt(16)
    return rgba(r,g,b,alpha)
}

val RoomColor.palette: RoomPalette get() {
    return when (this) {
        RoomColor.RED -> RoomPalette.red
        RoomColor.ORANGE -> RoomPalette.orange
        RoomColor.YELLOW -> RoomPalette.yellow
        RoomColor.GREEN -> RoomPalette.green
        RoomColor.CYAN -> RoomPalette.cyan
        RoomColor.BLUE -> RoomPalette.blue
        RoomColor.MAGENTA -> RoomPalette.magenta
        RoomColor.GRAY -> RoomPalette.gray
    }
}

val QuestionState.palette get() = when (this) {
    QuestionState.DRAFT -> QuestionPalette.draft
    QuestionState.OPEN -> QuestionPalette.open
    QuestionState.CLOSED -> QuestionPalette.closed
    QuestionState.RESOLVED -> QuestionPalette.resolved
    QuestionState.CANCELLED -> QuestionPalette.annulled
}