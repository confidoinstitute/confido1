package tools.confido.extensions

import extensions.AutoNavigateCE
import extensions.MillionaireCE
import extensions.QuestionGroupsCE
import extensions.UpdateScatterPlotCE
import extensions.flow_automation.FlowAutomationCE


actual val registeredExtensions = listOf<Extension>(
    UpdateScatterPlotCE,
    QuestionGroupsCE,
    MillionaireCE,
    FlowAutomationCE,
    AutoNavigateCE,
)