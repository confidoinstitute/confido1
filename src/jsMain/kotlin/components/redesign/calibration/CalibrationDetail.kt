package components.redesign.calibration

import components.redesign.basic.BaseDialogProps
import components.redesign.basic.Dialog
import components.redesign.basic.Stack
import components.redesign.basic.css
import components.redesign.forms.FormField
import components.redesign.forms.FormSection
import components.redesign.forms.InlineHelpButton
import components.redesign.forms.OptionGroup
import components.redesign.questions.predictions.binaryColors
import components.redesign.questions.predictions.binaryNames
import csstype.*
import emotion.css.ClassName
import emotion.react.css
import hooks.useEffectNotFirst
import payloads.responses.BinaryCalibrationQuestion
import payloads.responses.CalibrationQuestion
import payloads.responses.NumericCalibrationQuestion
import react.*
import react.dom.html.AnchorTarget
import react.dom.html.ReactHTML
import react.dom.html.ReactHTML.a
import react.dom.html.ReactHTML.b
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.span
import react.dom.html.ReactHTML.table
import react.dom.html.ReactHTML.tbody
import react.dom.html.ReactHTML.td
import react.dom.html.ReactHTML.th
import react.dom.html.ReactHTML.thead
import react.dom.html.ReactHTML.tr
import react.router.dom.Link
import tools.confido.calibration.CalibrationBin
import tools.confido.calibration.CalibrationEntry
import tools.confido.question.Question
import tools.confido.refs.deref
import tools.confido.spaces.NumericSpace
import tools.confido.utils.List2
import tools.confido.utils.endpoints
import tools.confido.utils.size
import tools.confido.utils.toFixed
import utils.except


external interface ConfidenceBarProps: Props {
    var bin: CalibrationBin
}
val ConfidenceBar = FC<ConfidenceBarProps> {props->
    val bin = props.bin

    Stack {
        direction = FlexDirection.row
        css {
            height = 10.px
            alignItems = AlignItems.stretch
        }
        calibrationBands.forEach { band->
            val range = band.absRange(bin) ?: return@forEach
            div {
                css {
                    backgroundColor = Color(band.color)
                    width = (range.size * 100.0).pct
                }
            }
        }
        div {
            css {
                height = 24.px
                position = Position.relative
            }
            
        }
    }
}

external interface AccuracyBarProps: Props {
    var entry: CalibrationEntry
}

val AccuracyBar  = FC<AccuracyBarProps> { props->
    val entry = props.entry
    val total = entry.total
    Stack {
        Stack {
            direction = FlexDirection.row
            css {
                justifyContent = JustifyContent.spaceBetween
                marginTop = 2.px
            }
            listOf(true, false).forEach { correct ->
                div {
                    css {
                        color = binaryColors[correct]
                        fontWeight = integer(600)
                        textAlign = if (correct) TextAlign.left else TextAlign.right
                    }
                    val cnt = entry.counts[correct]
                    val adj = if (correct) "correct" else "incorrect"
                    +"$cnt $adj predictions"
                }
            }
        }
        Stack {
            direction = FlexDirection.row
            css {
                height = 10.px
                alignItems = AlignItems.stretch
            }
            listOf(true, false).forEach { correct ->
                val cnt = entry.counts[correct]
                if (total > 30) { // continuous display
                    div {
                        css {
                            width = (100.0 * cnt / props.entry.total).pct
                            backgroundColor = binaryColors[correct]
                            border = Border(1.px, LineStyle.solid, NamedColor.transparent)
                        }
                    }
                } else { // discrete display
                    repeat(cnt) {
                        div {
                            css {
                                width = (100.0 * 1 / props.entry.total).pct
                                backgroundColor = binaryColors[correct]
                                border = Border(1.px, LineStyle.solid, NamedColor.white)
                            }
                        }
                    }
                }
            }
        }
    }
}

external interface CalibrationBarProps: Props {
    var bin: CalibrationBin?
    var entry: CalibrationEntry
}

val PercentBar = FC<PropsWithClassName> { props->
    val clr = Color("#666")
    Stack {
        css {
            gap = 2.px
        }
        Stack {
            direction = FlexDirection.row
            repeat(10) {
                div {
                    css {
                        height = 4.px
                        val b = Border(1.px, LineStyle.solid, clr)
                        borderLeft = b
                        if (it == 9) borderRight = b
                        borderBottom = b
                        width = 10.pct
                    }
                }
            }
        }
        div {
            css(override = props) {
                position = Position.relative
                height = 16.px
                fontSize = 13.px
                color = clr
                fontWeight = integer(600)
            }
            (0..100 step 10).forEach {
                div {
                    css {
                        position = Position.absolute
                        top = 50.pct
                        left = it.pct
                        transform = translate((-50).pct, (-50).pct)
                    }
                    +"$it%"
                }
            }
        }
    }
}

val CalibrationBar = FC<CalibrationBarProps> { props->
    Stack {
        css {
            gap = 8.px
            position = Position.relative
            marginTop = 24.px
            marginBottom = 24.px
        }
        //PercentBar {}
        props.bin?.let { ConfidenceBar { bin = it } }
        val accuracy = props.entry.successRate!!
        val confidence = props.bin?.mid
        div {
            css {
                position = Position.absolute
                val b = Border(2.px, LineStyle.solid, NamedColor.black)
                if (accuracy > 0.5) {
                    right = (100.0 * (1-accuracy)).pct
                    borderRight = b
                    transform  =translatex(1.px)
                } else {
                    left = (100.0 * accuracy).pct
                    borderLeft = b
                    transform  =translatex((-1).px)
                }
                top = -16.px
                bottom = 0.px
            }
            +"Your accuracy: ${(100.0*accuracy).toFixed(1)}%"
        }
        if (confidence != null)
        div {
            css {
                position = Position.absolute
                val b = Border(2.px, LineStyle.solid, NamedColor.black)
                if (confidence > 0.5) {
                    right = (100.0 * (1-confidence)).pct
                    borderRight = b
                    transform  =translatex(1.px)
                } else {
                    left = (100.0 * confidence).pct
                    borderLeft = b
                    transform  =translatex((-1).px)
                }
                top = 22.px
                bottom = -24.px
                display = Display.flex
                flexDirection = FlexDirection.column
                justifyContent = JustifyContent.flexEnd
            }
            +"Your confidence: ${(100.0*confidence).toFixed(1)}%"
        }
    }
}

external interface CalibrationDetailProps: Props {
    var data: List<CalibrationQuestion>
    var bin: CalibrationBin?
    var onHelp: ((CalibrationHelpSection)->Unit)?
}

val CalibrationDetail = FC<CalibrationDetailProps> { props ->
    val data = props.data.sortedWith(compareBy({ !it.isCorrect }, { it.question.deref()?.name ?: "" }))
    val binData = data.mapNotNull { it as? BinaryCalibrationQuestion }
    val numData = data.mapNotNull { it as? NumericCalibrationQuestion }
    val numConfidences = numData.map { it.confidence }.toSet()
    val numConfidence = if (numConfidences.size == 1) numConfidences.toList()[0] else null
    val checkmarks = List2("❌", "✓")
    fun fmtp(p: Double) = (100 * p).toFixed(1).trimEnd('0').trimEnd('.') + "%"
    fun ChildrenBuilder.fmtBool(v: Boolean, checkmark: Boolean = false) {
        span {
            css { color = binaryColors[v]; fontWeight = integer(600) }
            +(if (checkmark) checkmarks else binaryNames)[v]
        }
    }

    fun ChildrenBuilder.questionLink(q: Question) {

        a {
            css {
                display = Display.flex
                flexDirection = FlexDirection.row
                //width = 100.pct
                textDecoration = None.none
                ".newwin" { flexBasis = 0.px; maxWidth = 0.px; width = 0.px; overflow = Overflow.hidden }
                "&:hover .newwin" {
                    display = Display.block; flexBasis = Auto.auto; width = Auto.auto; maxWidth = None.none;
                }
            }
            title = q.name
            target = AnchorTarget._blank
            href = q.room?.urlPrefix?.let { it + q.urlPrefix } ?: ""
            div {
                css {
                    whiteSpace = WhiteSpace.nowrap
                    overflow = Overflow.hidden
                    textOverflow = TextOverflow.ellipsis
                    flexShrink = number(1.0)
                    flexGrow = number(0.0)
                    textDecoration = TextDecoration.underline
                }
                +q.name
            }
            div {
                +"⧉"
                css(ClassName("newwin")) {
                    flexShrink = number(0.0)
                    textDecoration = None.none
                    color = Color("#999")
                }
            }
        }
    }

    val gridTableCSS = ClassName {

        gridTemplateRows = "repeat(auto-fit, auto)".unsafeCast<GridTemplateColumns>()
        maxWidth = 100.pct
        width = 100.pct
        display = Display.grid
        rowGap = 2.px
        columnGap = 7.px
        "tbody, thead, tr" {
            display = Display.contents
        }
        "td, th" {
            display = Display.block
            overflow = Overflow.hidden
            textAlign = TextAlign.left
        }
    }

    fun ChildrenBuilder.accuracy(data: List<CalibrationQuestion>, add:String="") {
        val total = data.size
        val correct = data.filter { it.isCorrect }.size
        div {
            css { color = Color("#666") }
            b { +"$correct" }
            +"$add answers correct out of "
            b { +"$total" }
            +" total, for an accuracy of "
            b { +fmtp(correct.toDouble() / total) }
            +"."
        }
    }

    FormSection {
        accuracy(data)
        //CalibrationBar {
        //    bin = props.bin
        //    entry = CalibrationEntry(data)
        //}
        //Stack {
        //    css { gap = 3.px}
        //    accuracy(data)
        //    AccuracyBar { entry = CalibrationEntry(data) }
        //    PercentBar{}
        //}
    }

    if (binData.isNotEmpty())
        FormSection {
            title = "Binary questions"
            accuracy(binData, add=" binary")
            table {
                css(gridTableCSS) {
                    gridTemplateColumns =
                        "1fr min-content min-content min-content min-content".unsafeCast<GridTemplateColumns>()
                }
                thead {
                    th { +"Question" }
                    th {
                        css { whiteSpace = WhiteSpace.nowrap }
                        +"Belief"
                        InlineHelpButton {
                            onClick = { props.onHelp?.invoke(CalibrationHelpSection.CONFIDENCE) }
                        }
                    }
                    th {
                        css { whiteSpace = WhiteSpace.nowrap }
                        +"Confidence"
                        InlineHelpButton {
                            onClick = { props.onHelp?.invoke(CalibrationHelpSection.CONFIDENCE) }
                        }
                    }
                    th { +"Resolution" }
                    th { +"Correct?" }
                }
                tbody {
                    binData.forEach {
                        val q = it.question.deref() ?: return@forEach
                        tr {
                            td {
                                questionLink(q)
                            }
                            td {
                                fmtBool(it.expectedOutcome)
                            }
                            td {
                                +fmtp(it.confidence)
                            }
                            td {
                                fmtBool(it.actualOutcome)
                            }
                            td {
                                css { "&&" { textAlign = TextAlign.center } }
                                fmtBool(it.isCorrect, checkmark = true)
                            }
                        }
                    }
                }
            }
        }
    if (numData.isNotEmpty())
        FormSection {
            title = "Numeric questions"
            accuracy(numData, add=" numeric")
            table {
                css(gridTableCSS) {
                    gridTemplateColumns =
                        "1fr ${if (numConfidence == null) " min-content" else ""} minmax(min-content, 50px)  min-content min-content".unsafeCast<GridTemplateColumns>()
                }
                thead {
                    th { +"Question" }
                    if (numConfidence == null) {
                        th { +"Confidence" }
                        th { +"Confidence interval" }
                    } else {
                        th { +"${fmtp(numConfidence)} confidence interval" }
                    }
                    th { +"Resolution" }
                    th { +"Correct?" }
                }
                tbody {
                    numData.forEach {
                        val q = it.question.deref() ?: return@forEach
                        val space = q.answerSpace as? NumericSpace ?: return@forEach
                        tr {
                            td {
                                questionLink(q)
                            }
                            if (numConfidence == null) {
                                td {
                                    fmtp(it.confidence)
                                }
                            }
                            td {
                                span {
                                    css { whiteSpace = WhiteSpace.nowrap }
                                    +space.formatValue(it.confidenceInterval.start, showUnit = false)
                                }
                                span {
                                    css { whiteSpace = WhiteSpace.nowrap }
                                    +" to "
                                    +space.formatValue(it.confidenceInterval.endInclusive, showUnit = true)
                                }
                            }
                            td {
                                css { whiteSpace = WhiteSpace.nowrap }
                                +space.formatValue(it.resolution)
                            }
                            td {
                                css { "&&" { textAlign = TextAlign.center } }
                                fmtBool(it.isCorrect, checkmark = true)
                            }
                        }
                    }
                }

            }

        }
}

external interface FilteringCalibrationDetailProps: CalibrationDetailProps {
}

val FilteringCalibrationDetail = FC<FilteringCalibrationDetailProps> {props->
    val nonemptyBins = props.data.map { it.bin }.toSet().sorted()
    var bin by useState(props.bin ?: nonemptyBins[0])
    useEffectNotFirst(props.bin?.mid) { bin = if (props.bin in nonemptyBins) props.bin!! else nonemptyBins[0] }
    FormSection{
        FormField {
            this.title = "Confidence bracket"
            OptionGroup<CalibrationBin>()() {
                value = bin
                onChange = { bin = it }
                options = nonemptyBins.map { it to it.formattedRange }
            }
        }
    }
    div {
        css { marginTop = (-20).px }
        CalibrationDetail {
            data = props.data.filter { it.bin == bin }
            +props.except("data")
        }
    }
}

external interface CalibrationQuestionsDialogProps: BaseDialogProps, FilteringCalibrationDetailProps {
}

val CalibrationDetailDialog = FC<CalibrationQuestionsDialogProps> { props->
    Dialog {
        open = props.open
        onClose = props.onClose
        title = "Calibration details"
        fullSize = true

        FilteringCalibrationDetail {
            +props.except("open", "onClose")
        }
    }
}
