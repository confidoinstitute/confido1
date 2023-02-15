package components.redesign.basic

import csstype.*
import react.*

interface Palette {
    val color: Color
}


enum class TextPalette(override val color: Color): Palette {
    white(Color("#FFFFFF")),
    black(Color("#000000")),
    action(Color("#631AFF")),
}

interface PaletteWithText: Palette {
    override val color: Color
    val text: TextPalette
}

enum class MainPalette(override val color: Color, override val text: TextPalette): PaletteWithText {
    primary(Color("#6319FF"), TextPalette.white),
    secondary(Color("#00C2FF"), TextPalette.white),
}

enum class RoomPalette(override val color: Color, override val text: TextPalette): PaletteWithText {
    red(Color("#F61E4B"), TextPalette.white),
    orange(Color("#FF6B00"), TextPalette.white),
    yellow(Color("#FFCA0F"), TextPalette.black),
    green(Color("#00CA39"), TextPalette.white),
    cyan(Color("#47DEFF"), TextPalette.black),
    blue(Color("#0058DC"), TextPalette.white),
    magenta(Color("#BB0092"), TextPalette.white),
    gray(Color("#505050"), TextPalette.white),
}

enum class QuestionPalette(override val color: Color): Palette {
    `open`(Color("#5433B4")),
    closed(Color("#0B65B8")),
    resolved(Color("#0A9653")),
    annulled(Color("#8B8B8B")),
}

external interface PropsWithPalette<P: Palette>: Props {
    var palette: P?
}