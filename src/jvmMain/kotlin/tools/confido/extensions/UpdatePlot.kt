package tools.confido.extensions

import extensions.UpdateReferenceQuestionKey
import extensions.UpdateScatterPlotExt
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import rooms.RoomPermission
import tools.confido.application.routes.*
import tools.confido.refs.ref
import tools.confido.state.serverState
import tools.confido.utils.List2
import tools.confido.refs.*

object UpdateScatterPlotSE: ServerExtension, UpdateScatterPlotExt {
    var referenceTime: Instant = Instant.DISTANT_FUTURE
    override fun initRoutes(r: Routing) {
        r.apply {
            postST("/api/ext/update_plot/reference_time") {
                referenceTime = Clock.System.now()
                call.respond(HttpStatusCode.OK)
            }
            getST("/api/ext/update_plot/reference_time") {
                call.respond(referenceTime.epochSeconds)
            }
            getST("/api$questionUrl/update_plot") {
                withQuestion {
                    assertPermission(
                        RoomPermission.VIEW_INDIVIDUAL_PREDICTIONS,
                        "need permession to view individual predictions"
                    )
                    val referenceQ = call.request.queryParameters["qref"]?.let { if (it=="") null else Ref(it) } ?: question.extensionData[UpdateReferenceQuestionKey]
                    val qs = List2(referenceQ ?: question.ref, question.ref)
                    val times = List2("t1", "t2").map {
                        call.request.queryParameters[it]?.let { if (it=="null") null else it}?.toLong()?.let { Instant.fromEpochSeconds(it) }
                            ?: if (it == "t1" && referenceQ == null) referenceTime else Instant.DISTANT_FUTURE
                    }
                    val users = serverState.userPred[question.ref]?.keys ?: emptySet()
                    val preds = users.mapNotNull { u->
                        val r = times.zip(qs) { t,q-> serverState.userPredHistManager.at(q, u, t) }
                        if (r == List2(null,null)) null else r
                    }
                    call.respond(preds)
                }
            }
        }
    }
}