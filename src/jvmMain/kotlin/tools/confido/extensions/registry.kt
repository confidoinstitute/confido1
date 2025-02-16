package tools.confido.extensions

import tools.confido.extensions.flow_automation.FlowAutomationSE


actual val registeredExtensions = listOf<Extension>(
    UpdateScatterPlotSE,
    QuestionGroupsSE,
    MillionaireServerExtension,
    FlowAutomationSE,
    AutoNavigateSE,
    ReferenceForecastSE
)
