package tools.confido.extensions

import extensions.*
import extensions.flow_automation.FlowAutomationCE

actual val registeredExtensions: List<Extension> = listOf(
    UpdateScatterPlotCE,
    QuestionGroupsCE,
    MillionaireCE,
    FlowAutomationCE,
    AutoNavigateCE,
    ReferenceForecastCE,
    PointEstimateCE,
    PredictionShowcaseCE,
)
