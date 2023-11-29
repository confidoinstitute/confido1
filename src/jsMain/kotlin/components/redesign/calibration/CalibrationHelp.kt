package components.redesign.calibration

import components.redesign.basic.BaseDialogProps
import components.redesign.basic.Dialog
import components.redesign.basic.Stack
import components.redesign.basic.dialogStateWrapper
import components.redesign.forms.*
import components.redesign.layout.LayoutMode
import components.redesign.layout.LayoutModeContext
import components.redesign.questions.predictions.BinaryPrediction
import components.redesign.questions.predictions.NumericDistSpecSym
import components.redesign.questions.predictions.NumericPredGraph
import csstype.*
import emotion.css.ClassName
import emotion.react.css
import react.ChildrenBuilder
import react.FC
import react.dom.html.ReactHTML
import react.dom.html.ReactHTML.a
import react.dom.html.ReactHTML.b
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.i
import react.dom.html.ReactHTML.li
import react.dom.html.ReactHTML.ol
import react.dom.html.ReactHTML.option
import react.dom.html.ReactHTML.p
import react.dom.html.ReactHTML.small
import react.dom.html.ReactHTML.span
import react.dom.html.ReactHTML.table
import react.dom.html.ReactHTML.tbody
import react.dom.html.ReactHTML.td
import react.dom.html.ReactHTML.th
import react.dom.html.ReactHTML.thead
import react.dom.html.ReactHTML.tr
import react.dom.html.ReactHTML.ul
import react.useContext
import react.useState
import tools.confido.calibration.CalibrationBin
import tools.confido.calibration.CalibrationEntry
import tools.confido.distributions.BinaryDistribution
import tools.confido.spaces.NumericSpace
import tools.confido.utils.List2
import tools.confido.utils.formatPercent
import tools.confido.utils.toFixed

enum class CalibrationHelpSection(val title: String) {
    INTRO("Introduction"),
    CONFIDENCE("Belief and confidence"),
    ACCURACY("Accuracy; confidence bracketing"),
    RESULTS("Well-calibrated, overconfident and underconfident"),
    SCORE_DATES("Score times; why am I seeing no data?"),
    NUMERIC("What about numeric/date questions?"),
    GROUP("Group calibration"),
}
external interface CalibrationHelpDialogProps: BaseDialogProps {
    var initialSection: CalibrationHelpSection?
}

val CalibrationHelpDialog = dialogStateWrapper(FC<CalibrationHelpDialogProps> { props->
    var section by useState(props.initialSection ?: CalibrationHelpSection.INTRO)
    var numeric by useState(false)
    val layoutMode = useContext(LayoutModeContext)
    fun ChildrenBuilder.sectionLink(target: CalibrationHelpSection, text: String) {
        a {
            css { color = Globals.inherit }
            onClick = { e-> section = target; e.preventDefault() }
            +text
        }
    }
    fun ChildrenBuilder.note(body: ChildrenBuilder.() -> Unit) {
        small {
            css { color = Color("#555")}
            body()
        }
    }
    Dialog {
        title = "Calibration help"
        fullSize = true
        open = props.open
        onClose = props.onClose
        val arrowCSS = ClassName {
            flexGrow = number(0.0)
            flexShrink = number(0.0)
            alignSelf = AlignSelf.center
            "&:disabled" {
                color = Color("#999")
            }
        }
        fun advanceSec(delta: Int) {
            val newOrd = section.ordinal + delta
            if (newOrd !in CalibrationHelpSection.entries.indices) return
            section = CalibrationHelpSection.entries[newOrd]
        }
        FormSection {
            //OptionGroup<CalibrationHelpSection>()() {
            //    css { whiteSpace = WhiteSpace.nowrap; flexWrap = FlexWrap.wrap }
            //    value = section
            //    onChange = { section = it }
            //    options = chapters
            //}
            FormField {
                title = "Chapter"
                ButtonUnstyled {
                    className = arrowCSS
                    disabled = (section.ordinal == 0)
                    onClick = { advanceSec(-1) }
                    +"🞀"
                }
                Select {
                    css {
                        flexGrow = number(1.0)
                        flexShrink = number(1.0)
                        width = 100.px // XXX needed to make flexShrink work
                        textOverflow = TextOverflow.ellipsis
                    }
                    CalibrationHelpSection.entries.forEach { sec->
                        option {
                            value = sec.name
                            +sec.title
                        }
                    }
                    value = section.name
                    onChange = { ev-> CalibrationHelpSection.entries.find { it.name == ev.target.value }?.let { section = it } }
                }
                ButtonUnstyled {
                    className = arrowCSS
                    disabled = (section.ordinal == CalibrationHelpSection.entries.size - 1)
                    onClick = { advanceSec(+1) }
                    +"🞂"
                }
            }
        }
        when (section) {
            CalibrationHelpSection.INTRO-> {
                p {
                    b { +"Calibration" }
                    +" is a measure of how well the stated "
                    b { sectionLink(CalibrationHelpSection.CONFIDENCE, "confidence") }
                    +" of your predictions (how sure you are of them) "
                    +" corresponds to their actual "
                    b { sectionLink(CalibrationHelpSection.ACCURACY, "accuracy") }
                    +" (how often they turn out to be right)."
                }
                p {
                    +" Calibration is measured separately for each "
                    b { sectionLink(CalibrationHelpSection.ACCURACY, "confidence bracket") }
                    +" (e.g. 65-75%, 75-85%, ...)."
                }
                p {
                    +"Based on the results, your calibration can be classified as "
                    b { sectionLink(CalibrationHelpSection.RESULTS, "well-calibrated") }
                    +" (confidence roughly tracks accuracy), "
                    b { sectionLink(CalibrationHelpSection.RESULTS, "overconfident") }
                    +" (confidence tends to overstate accuracy), "
                    b { sectionLink(CalibrationHelpSection.RESULTS, "underconfident") }
                    +" (confidence tends to understate accuracy)."
                }
                p {
                    +"Confido can show you your calibration both within a room (that considers only "
                    +" predictions made in that room) and across the whole workspace."
                }
                p {
                    +"You can read more details in the subsequent chapters:"
                    ol {
                        CalibrationHelpSection.entries.forEach {  sec->
                            li {
                                if (sec == CalibrationHelpSection.INTRO) {
                                    span {
                                        css { color = Color("#333") }
                                        +sec.title
                                        +" (this chapter)"
                                    }
                                } else {
                                    sectionLink(sec, sec.title)
                                }
                            }
                        }

                    }
                }
            }
            CalibrationHelpSection.CONFIDENCE-> {
                p {
                    i { +"Confidence" }
                    +" is a percentage expressing one's level of certainty regarding a specific "
                    +" statement (usually a prediction or estimate) that can turn out to be either true or false."
                }
                p {
                    +" Confidence can range from 0% (absolute certainty the statement is false) through 50% "
                    +" (equally likely to be true or false, representing zero knowledge about the statement)"
                    +" to 100% (absolute certainty the statement is true)."
                }
                p {
                    +"For purposes of calibration, we always consider statements with confidence greater"
                    +" than 50% ("
                    i { +"'beliefs'" }
                    +")"
                }

                p{
                    if (layoutMode >= LayoutMode.TABLET)
                    div {
                        css {
                            float = Float.right
                            width = 140.px
                        }

                        BinaryPrediction {
                            dist = BinaryDistribution(0.4)
                            baseHeight = 80.px
                        }
                    }
                    +" For example a prediction of 40% on a yes/no question "
                    i { +"Will we finish the project on time?" }

                    if (layoutMode == LayoutMode.PHONE)
                    BinaryPrediction {
                        dist = BinaryDistribution(0.4)
                        baseHeight = 80.px
                    }
                    else +" "
                    +"implies a 40% confidence in the statement "
                    i { +"We will finish the project on time" }
                    +" and a 60% confidence in the statement "
                    i { +"We will finish the project late." }
                }
                p {
                    +"These confidences always add up to 100% and are basically two ways"
                    +" of talking about the same thing. We will consider only the latter"
                    +" when computing calibration."
                }
                p {
                    +"Thus the confidence axis ranges only from 50% to 100%."
                    +" On this range, "
                    b{+"greater numbers represent more certainty"}
                    +"."
                    note {
                        +"(This would not be true on the whole 0% to 100% range, as both 0% and 100% represent absolute certainty.)"
                    }
                }
                p {
                    sectionLink(CalibrationHelpSection.NUMERIC, "What about numeric/date questions?")
                }
            }
            CalibrationHelpSection.ACCURACY-> {
                p {
                    +"For a given "
                    sectionLink(CalibrationHelpSection.CONFIDENCE, "confidence level")
                    +", your "
                    b{+"accuracy"}
                    +" is simply the proportion of your "
                    sectionLink(CalibrationHelpSection.CONFIDENCE, "beliefs")
                    +" with that confidence that turn out to be correct."
                }
                p {
                    +"For example, if you made 10 predictions at 80% confidence and"
                    +" 7 of them turned out correct, your accuracy would be 70%"
                    +" (making you slightly "
                    sectionLink(CalibrationHelpSection.RESULTS, "overconfident")
                    +")."
                }
                Stack {
                    css {
                        gap = 5.px
                        margin = Margin(10.px, 20.px)
                    }
                    AccuracyBar {
                        this.entry = CalibrationEntry(List2(3, 7))
                    }
                    PercentBar {}
                }
                p {
                    +"As accuracy is an average metric, we need to aggregate "
                    +" enough predictions with a given confidence level to get a meaningful result. "
                    note {
                        +" (For example, if you made only two 80% predictions, one correct, one not,"
                        +" for an accuracy of 50%, it is hard to say whether you are overconfident"
                        +" or just had bad luck.)"
                    }
                }
                p {
                    +"To achieve that, instead of computing accuracy separately for each exact confidence level "
                    +" (such as 80%, 81%, or even 80.5%),"
                    +" Confido groups predictions with similar confidence levels into "
                    b{ +"confidence brackets" }
                    +" and then computes accuracy for each bracket. "
                }
                p {
                    +"For example, the 80% bracket contains all predictions with confidence in the range from 75% to 85%."
                    +" If you made four predictions with confidence levels 78%, 80%, 80%, 84% and three of them were correct,"
                    +" your confidence in the 80% bracket would be 75%."
                }
                p{
                note {
                    +"(This makes the result less precise because of mixing beliefs with different confidence"
                    +" levels but this is compensated by averaging more predictions when computing accuracy,"
                    +" thus reducing effects of random chance.)"
                }
                }
            }
            CalibrationHelpSection.RESULTS-> {
                p {
                    +"Based on your results, your calibration in a given "
                    sectionLink(CalibrationHelpSection.ACCURACY, "bracket")
                    +" can be classified as:"
                    ul {
                        li {
                            b { +"Well-calibrated" }
                            +" when your "
                            sectionLink(CalibrationHelpSection.CONFIDENCE, "confidence")
                            +" tends to roughly match your "
                            sectionLink(CalibrationHelpSection.ACCURACY, "accuracy")
                            +"."
                            +" In that case, your confidence can be used as an indicator of how likely you are to be right "
                            +" for predictions where the answer is not yet known. This is what you strive for."
                        }
                        li {
                            b { +"Overconfident" }
                            +" if your confidence tends to be larger than the resulting accuracy, i.e. "
                            +" you tend to be more strongly convinced of your beliefs than is warranted. "
                            +" Thus other people (and probably even you yourself) should take them with a grain of salt."
                            +" Most people on Earth start out overconfident, so if that includes you, don't worry "
                            +" about it too much. There is a lot of room for improvement."
                        }
                        li {
                            b { +"Underconfident" }
                            +" in the much rarer opposite case when you tend to understate your confidence, claim to "
                            +" know less than you actually do."
                        }
                    }
                }
                p {
                    +"It is important to note that well-calibratedness, overconfidence and underconfidence are a "
                    b { +"spectrum." }
                    +" There is no sharp boundary between being well-calibrated and over-/under-confident."
                }
                p {
                    +" Confido classifies your calibration into several discrete categories (well calibrated, slightly "
                    +" overconfident, overconfident, ...) but these are artificial and purely for easier visual orientation "
                    +" and color coding. The boundaries between these categories should not be taken very seriously."

                }
            }
            CalibrationHelpSection.SCORE_DATES-> {
                p {
                    +"In order for a prediction to be included in calibration computation, several conditions "
                    +"must hold:"
                    ul {
                        li {
                            +"The question must have a "
                            b {+"score time"}
                            +" set in its schedule. This determines the time point from which predictions are "
                            +" used for calibration computation."
                        }
                        li {
                            +"You must have made at least one prediction "
                            b{+"before"}
                            +" the score time "
                            note { +"(if you updated it afterwards, the last update made before the score time "
                                +" is used for purposes of calibration and further updates are ignored)." }
                        }
                        li {
                            +"The question must have a resolution and be marked as "
                            b{+"resolved"}
                            +"."
                        }
                    }
                }
                p {
                    +"The score time is set by the question author or another moderator, either when creating the question "
                    +" or later when resolving it. "
                    +" It should be set based on a compromise between the forecasters having enough time to think "
                    +" about the question and the answer not yet being too obvious."
                }
            }

            CalibrationHelpSection.NUMERIC-> {
                p {
                    +"Computing calibration requires converting your predictions into "
                    sectionLink(CalibrationHelpSection.CONFIDENCE, "belief + confidence")
                    +" pairs, where the beliefs can be later judged as either correct or incorrect."
                }
                p {
                    +"This is simple for yes/no questions. "
                    note { +"(We just take whichever of the two possible outcomes you "
                    + " consider more likely, plus the probability you assigned to that outcome.)" }
                }
                p {
                    +"But for numeric questions, the situation is a bit more complicated, as there are basically "
                    +" infinitely many possible outcomes."
                }
                p {
                    +"To address this, Confido converts each numeric prediction into multiple "
                    +" beliefs based on its "
                    b{+"confidence intervals"}
                    +"."
                }
                p {
                    +"For example, consider a numeric prediction like this:"
                }
                val space = NumericSpace(0.0, 80.0)
                val dist = NumericDistSpecSym(space, 30.0, 20.0).dist!!
                div {
                    css {
                        padding = Padding(0.px, 40.px)
                    }
                    NumericPredGraph {
                        graphHeight = 60.0
                        maxZoom = 1.0
                        this.space = space
                        this.dist = dist
                    }
                }
                p {
                    +"From such a prediction (probability distribution), Confido derives "
                    +" one belief based on a confidence interval for each confidence bracket:"
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
                        th { +"Bracket" }
                        th { +"Confidence" }
                        th { +"Conf. int." }
                        th { +"Belief" }
                    }
                    tbody {
                        CalibrationBin.values().forEach { bin->
                            val ci = dist.confidenceInterval(bin.mid)
                            tr {
                                if (bin == CalibrationBin.BIN_80) {
                                    css {
                                        fontWeight = integer(600)
                                    }
                                }
                                td {
                                    +"${formatPercent(bin.range.start)} - ${formatPercent(bin.range.endInclusive)}"
                                }
                                td {
                                    +((100.0 * bin.mid).toFixed(1) + "%")
                                }
                                td {
                                    ReactHTML.span {
                                        css { whiteSpace = WhiteSpace.nowrap }
                                        +space.formatValue(ci.start, showUnit = false)
                                    }
                                    ReactHTML.span {
                                        css { whiteSpace = WhiteSpace.nowrap }
                                        +" to "
                                        +space.formatValue(ci.endInclusive, showUnit = true)
                                    }
                                }
                                td {
                                    +"Answer is between "
                                    ReactHTML.span {
                                        css { whiteSpace = WhiteSpace.nowrap }
                                        +space.formatValue(ci.start, showUnit = false)
                                    }
                                    ReactHTML.span {
                                        css { whiteSpace = WhiteSpace.nowrap }
                                        +" and "
                                        +space.formatValue(ci.endInclusive, showUnit = true)
                                    }
                                }
                            }
                        }
                    }
                }
                p {
                    +"The confidences are chosen as the midpoints of each bracket. The 80% confidence "
                    +" interval (bolded in the table) is what you enter when creating a prediction, the "
                    +" others are mathematically derived from the probability distribution."
                }
                p {
                    +"Thus a single prediction is converted into ${CalibrationBin.entries.size} belief+confidence pairs"
                    +" (one in each confidence bracket), "
                    +" which are then each considered separately for purposes of calibration, "
                    +" as if you had made ${CalibrationBin.entries.size} separate yes/no predictions."
                }
            }
            CalibrationHelpSection.GROUP-> {
                p {
                    +"Apart from your individual calibration, Confido can also compute "
                    b{+"group calibration."}
                    +" But it is important to clarify what that means."
                }
                p {
                    +"Group calibration is calibration computed based on the "
                    b{+"group prediction"}
                    +", i.e. calibration of a hypothetical individual whose predictions were equal to the group prediction "
                    +" for each question."
                }
                p {
                    b { +"Group prediction" }
                    +" is the average of all the individual predictions on a given question. "
                    +" For yes/no questions, it is simply the average of everyone's confidences. "
                }
                p {
                    +" For numeric/date questions, we are averaging probability distributions, which "
                    +" is beyond the scope of this explanation."
                }
                p {
                    +" In either case, you can see the group prediction (if you have permissions to view it) "
                    +" on the "
                    i{+"Group prediction"}
                    +" tab of each question."
                }
                p {
                    +"It is important to note that group calibration is "
                    b{+"not"}
                    +" an average of the individual calibrations."
                }

            }
        }
    }
})
