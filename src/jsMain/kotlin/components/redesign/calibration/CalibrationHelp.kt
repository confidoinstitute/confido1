package components.redesign.calibration

import components.redesign.basic.BaseDialogProps
import components.redesign.basic.Dialog
import components.redesign.basic.Stack
import components.redesign.basic.dialogStateWrapper
import components.redesign.forms.*
import components.redesign.layout.LayoutMode
import components.redesign.layout.LayoutModeContext
import components.redesign.questions.predictions.BinaryPrediction
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
import react.dom.html.ReactHTML.option
import react.dom.html.ReactHTML.p
import react.dom.html.ReactHTML.small
import react.useContext
import react.useState
import tools.confido.calibration.CalibrationEntry
import tools.confido.distributions.BinaryDistribution
import tools.confido.utils.List2
import utils.buildProps

enum class CalibrationHelpSection(val title: String) {
    INTRO("Introduction"),
    CONFIDENCE("Belief and confidence"),
    ACCURACY("Accuracy; confidence bracketing"),
    RESULTS("Well-calibrated, overconfident and underconfident"),
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
            CalibrationHelpSection.INTRO->
                ReactHTML.p {
                    ReactHTML.i {}
                }
            CalibrationHelpSection.CONFIDENCE-> {
                p {
                    i { +"Confidence" }
                    +" is a percentage expressing one's level of certainty regarding a specific "
                    +" statement (usually a prediction or estimate) that can turn out to be either true or false."
                }
                p {
                    +" Confidence can range from 0% (absolute certainty the statement is false) through 50% "
                    +" (equally likely to be true or false, representing zero knowledge about the satement)"
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
            }
            CalibrationHelpSection.ACCURACY-> {
                p {
                    +"For a given "
                    sectionLink(CalibrationHelpSection.CONFIDENCE, "confidence level")
                    +", your accuracy is simply the proportion of your "
                    sectionLink(CalibrationHelpSection.CONFIDENCE, "beliefs")
                    +" with that confidence that turn out to be correct."
                }
                p {
                    +"For example, if you have made 10 predictions at 80% confidence and"
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
                    +"As accuracy is an aggregate metric, you would need to aggregate"
                    +" enough predictions with a given confidence level to get a meaningful result"
                    note {
                        +" (for example, if you made only two 80% predictions, one correct, one not,"
                        +" for an accuracy of 50%, it is hard to say whether you are overconfident"
                        +" or just had bad luck)"
                    }
                }
            }
            else->{}
        }
    }
})
