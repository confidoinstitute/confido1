package components.redesign.calibration

import components.redesign.basic.BaseDialogProps
import components.redesign.basic.Dialog
import components.redesign.basic.Stack
import components.redesign.basic.dialogStateWrapper
import components.redesign.forms.FormSection
import components.redesign.forms.OptionGroup
import components.redesign.questions.predictions.BinaryPrediction
import csstype.*
import emotion.react.css
import react.FC
import react.dom.html.ReactHTML
import react.dom.html.ReactHTML.b
import react.dom.html.ReactHTML.i
import react.dom.html.ReactHTML.p
import react.useState
import tools.confido.distributions.BinaryDistribution

enum class CalibrationHelpSection {
    INTRO, CONFIDENCE, ACCURACY
}
external interface CalibrationHelpDialogProps: BaseDialogProps {
    var initialSection: CalibrationHelpSection?
}

val CalibrationHelpDialog = dialogStateWrapper(FC<CalibrationHelpDialogProps> { props->
    var section by useState(props.initialSection ?: CalibrationHelpSection.INTRO)
    var numeric by useState(false)
    Dialog {
        title = "Calibration help"
        open = props.open
        onClose = props.onClose
        FormSection {
            OptionGroup<CalibrationHelpSection>()() {
                value = section
                onChange = { section = it }
                options = listOf(
                    CalibrationHelpSection.INTRO to "Intro",
                    CalibrationHelpSection.CONFIDENCE to "Beliefs and confidence",
                    CalibrationHelpSection.ACCURACY to "Accuracy",
                )
            }
        }
        when (section) {
            CalibrationHelpSection.INTRO->
                ReactHTML.p {
                    ReactHTML.i {}
                }
            CalibrationHelpSection.CONFIDENCE-> {
                p {
                    i{+"Confidence"}
                    +" is a percentage number expressing one's level of certainty regarding a specific belief "
                    +" or statement."
                }
                p {
                    +"Beliefs are related to answers (predictions, estimates) you give in Confido "
                    +"but they are not the same thing."
                }
                FormSection{
                    OptionGroup<Boolean>()() {
                        css {
                            fontSize = 90.pct
                        }
                        value = numeric
                        onChange = { numeric = it }
                        options = listOf(
                            false to "Yes/no questions",
                            true to "Numeric and date questions"
                        )
                    }
                }
                if (numeric == false) {
                    p {
                        +"A "
                        b { +"yes/no" }
                        +" prediction always entails two beliefs. For example consider a prediction of 40% on a yes/no question "
                        i { +"Will we finish the project on time?" }

                        BinaryPrediction {
                            dist = BinaryDistribution(0.4)
                            baseHeight = 80.px
                        }

                        +"It implies a 40% confidence in the statement "
                        i { +"We will finish the project on time" }
                        +" and a 60% confidence in the statement "
                        i { +"We will finish the project late." }
                    }
                    p {
                        +"These confidences always add up to 100% and are basically two ways"
                        +" of talking about the same thing."
                    }
                    p {
                        +"For computing calibration, we always take the belief you assign"
                        +" a larger than 50% confidence. Thus the confidence axis ranges"
                        +" only from 50% to 100%."
                    }
                } else {

                }
            }
            else->{}
        }
    }
})
