package extensions.calibration_training

import tools.confido.web.Client
import tools.confido.web.components.redesign.basic.*
import tools.confido.web.components.redesign.calibration.CalibrationView
import tools.confido.web.components.redesign.forms.Button
import tools.confido.web.components.redesign.forms.TextButton
import emotion.react.css
import tools.confido.web.hooks.useCoroutineLock
import io.ktor.client.call.*
import io.ktor.http.*
import tools.confido.payloads.requests.Myself
import react.*
import react.dom.html.ReactHTML.div
import react.router.useNavigate
import tools.confido.calibration.CalibrationBin
import tools.confido.calibration.CalibrationEntry
import tools.confido.calibration.CalibrationVector
import tools.confido.utils.List2
import web.cssom.*

/**
 * Full page showing calibration stats from training.
 * Replaces the former TrainingStatsDialog with a proper page layout
 * that reuses the core CalibrationView (graph + table).
 */
val TrainingStatsPage = FC<Props> {
    val navigate = useNavigate()
    var stats by useState<TrainingStats?>(null)
    var loading by useState(true)
    var error by useState<String?>(null)

    val fetchLock = useCoroutineLock()

    // Fetch stats on mount
    useEffectOnce {
        fetchLock {
            loading = true
            error = null
            Client.send("/api/training/stats", method = HttpMethod.Get, onError = { err ->
                error = err
                loading = false
            }) {
                val response: TrainingStats = body()
                stats = response
                loading = false
            }
        }
    }

    PageHeader {
        title = "Training Statistics"
        navigateBack = "/training"
        allowSidebar = true

        rightExtra = div.create {
            css {
                display = Display.flex
                alignItems = AlignItems.center
                gap = 8.px
            }
            TextButton {
                onClick = { navigate("/training") }
                +"Back to training"
            }
        }
    }

    LayoutWidthWrapper {
        css { paddingBottom = 30.px }

        if (loading) {
            div {
                css {
                    textAlign = TextAlign.center
                    padding = 40.px
                    color = Color("#666")
                }
                +"Loading..."
            }
        } else if (error != null) {
            div {
                css {
                    textAlign = TextAlign.center
                    padding = 40.px
                    color = Color("#c00")
                }
                +"Error: $error"
            }
        } else if (stats != null) {
            val currentStats = stats!!

            MobileSidePad {
                css { marginBottom = 15.px; fontSize = 0.9.rem; color = Color("#444") }
                +"${currentStats.totalAnswered} questions answered"
            }

            if (currentStats.totalAnswered == 0) {
                div {
                    css {
                        textAlign = TextAlign.center
                        padding = 32.px
                        color = Color("#666")
                    }
                    +"Answer some training questions to see your calibration stats."
                }
            } else {
                val calibVector = statsToCalibrationVector(currentStats)

                CalibrationView {
                    data = calibVector
                    who = Myself
                    graphHeight = 400.0
                }
            }
        }
    }
}

/**
 * Convert TrainingStats to CalibrationVector for display.
 */
private fun statsToCalibrationVector(stats: TrainingStats): CalibrationVector {
    val data = mutableMapOf<CalibrationBin, CalibrationEntry>()

    for (bin in CalibrationBin.entries) {
        val bucketName = formatBucketName(bin)
        val bucketStat = stats.bucketStats[bucketName]

        if (bucketStat != null && bucketStat.total > 0) {
            data[bin] = CalibrationEntry(List2(bucketStat.total - bucketStat.correct, bucketStat.correct))
        }
    }

    return CalibrationVector(data)
}

private fun formatBucketName(bin: CalibrationBin): String {
    val start = (bin.range.start * 100).toInt()
    val end = (bin.range.endInclusive * 100).toInt()
    return "$start% - $end%"
}

/**
 * Props for confirm reset dialog.
 */
external interface ConfirmResetDialogProps : BaseDialogProps {
    var onConfirm: () -> Unit
}

/**
 * Confirmation dialog for resetting training progress.
 */
val ConfirmResetDialog = FC<ConfirmResetDialogProps> { props ->
    val resetLock = useCoroutineLock()

    Dialog {
        open = props.open
        onClose = props.onClose
        title = "Reset Progress"

        Stack {
            css {
                padding = Padding(20.px, 16.px)
                gap = 20.px
            }

            div {
                css {
                    fontSize = 16.px
                    lineHeight = 1.5.em
                }
                +"Are you sure you want to reset your calibration training progress? This will delete all your answers and you'll start from the beginning."
            }

            Stack {
                direction = FlexDirection.row
                css {
                    justifyContent = JustifyContent.flexEnd
                    gap = 12.px
                }

                TextButton {
                    onClick = { props.onClose?.invoke() }
                    disabled = resetLock.running
                    +"Cancel"
                }

                Button {
                    onClick = {
                        resetLock {
                            Client.send("/api/training/reset", method = HttpMethod.Post, onError = {}) {
                                props.onConfirm()
                                props.onClose?.invoke()
                            }
                        }
                    }
                    disabled = resetLock.running
                    +"Reset"
                }
            }
        }
    }
}
