package extensions.calibration_training

import Client
import components.redesign.basic.*
import components.redesign.calibration.CalibrationGraph
import components.redesign.forms.Button
import components.redesign.forms.TextButton
import emotion.react.css
import hooks.useCoroutineLock
import io.ktor.client.call.*
import io.ktor.http.*
import payloads.requests.Myself
import react.*
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.table
import react.dom.html.ReactHTML.tbody
import react.dom.html.ReactHTML.td
import react.dom.html.ReactHTML.th
import react.dom.html.ReactHTML.thead
import react.dom.html.ReactHTML.tr
import tools.confido.calibration.CalibrationBin
import tools.confido.calibration.CalibrationEntry
import tools.confido.calibration.CalibrationVector
import tools.confido.utils.List2
import web.cssom.*

/**
 * Props for training stats dialog.
 */
external interface TrainingStatsDialogProps : BaseDialogProps {
}

/**
 * Dialog showing calibration stats from training.
 */
val TrainingStatsDialog = FC<TrainingStatsDialogProps> { props ->
    var stats by useState<TrainingStats?>(null)
    var loading by useState(true)
    var error by useState<String?>(null)
    
    val fetchLock = useCoroutineLock()
    
    // Fetch stats when dialog opens
    useEffect(props.open) {
        if (props.open) {
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
    }
    
    Dialog {
        open = props.open
        onClose = props.onClose
        title = "Training Statistics"
        
        Stack {
            css {
                padding = Padding(20.px, 16.px)
                gap = 24.px
            }
            
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
                
                // Summary
                div {
                    css {
                        fontSize = 16.px
                        textAlign = TextAlign.center
                        marginBottom = 8.px
                    }
                    +"Questions answered: ${currentStats.totalAnswered}"
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
                    // Convert to CalibrationVector for the graph
                    val calibVector = statsToCalibrationVector(currentStats)
                    
                    // Calibration graph
                    div {
                        css {
                            width = 100.pct
                        }
                        CalibrationGraph {
                            calib = calibVector
                            height = 300.px
                            who = Myself
                            areaLabels = true
                        }
                    }
                    
                    // Table view
                    TrainingCalibrationTable {
                        this.stats = currentStats
                    }
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
 * Props for training calibration table.
 */
external interface TrainingCalibrationTableProps : Props {
    var stats: TrainingStats
}

/**
 * Simple calibration table for training stats.
 */
val TrainingCalibrationTable = FC<TrainingCalibrationTableProps> { props ->
    val buckets = CalibrationBin.entries.mapNotNull { bin ->
        val bucketName = formatBucketName(bin)
        val stat = props.stats.bucketStats[bucketName]
        if (stat != null && stat.total > 0) {
            Triple(bin, bucketName, stat)
        } else {
            null
        }
    }
    
    if (buckets.isEmpty()) {
        return@FC
    }
    
    table {
        css {
            width = 100.pct
            borderCollapse = BorderCollapse.collapse
            fontSize = 14.px
            
            "th, td" {
                padding = Padding(8.px, 12.px)
                textAlign = TextAlign.center
                borderBottom = Border(1.px, LineStyle.solid, Color("#e0e0e0"))
            }
            
            "th" {
                fontWeight = integer(600)
                backgroundColor = Color("#f5f5f5")
            }
        }
        
        thead {
            tr {
                th { +"Confidence" }
                th { +"Correct" }
                th { +"Total" }
                th { +"Accuracy" }
                th { +"Result" }
            }
        }
        
        tbody {
            buckets.forEach { (bin, bucketName, stat) ->
                val accuracy = stat.accuracy
                val targetLow = bin.range.start
                val targetHigh = bin.range.endInclusive
                val targetMid = (targetLow + targetHigh) / 2
                
                val result = when {
                    accuracy >= targetLow - 0.05 && accuracy <= targetHigh + 0.05 -> "Well-calibrated"
                    accuracy < targetLow -> "Overconfident"
                    else -> "Underconfident"
                }
                
                val resultColor = when (result) {
                    "Well-calibrated" -> Color("#4caf50")
                    "Overconfident" -> Color("#9c27b0")
                    "Underconfident" -> Color("#00bcd4")
                    else -> Color("#666")
                }
                
                tr {
                    td { +bucketName }
                    td { +"${stat.correct}" }
                    td { +"${stat.total}" }
                    td { +"${(accuracy * 100).toInt()}%" }
                    td {
                        css { color = resultColor; fontWeight = integer(500) }
                        +result
                    }
                }
            }
        }
    }
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
