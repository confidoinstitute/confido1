package tools.confido.extensions

import extensions.MillionaireCE
import extensions.QuestionGroupsCE
import extensions.UpdateScatterPlotCE


actual val registeredExtensions = listOf<Extension>(
    UpdateScatterPlotCE,
    QuestionGroupsCE,
    MillionaireCE,
)