package tools.confido.extensions

import extensions.UpdateScatterPlotExt
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.datetime.Instant
import rooms.RoomPermission
import tools.confido.application.routes.*
import tools.confido.refs.ref
import tools.confido.state.serverState
import tools.confido.utils.List2

object UpdateScatterPlotSE: ServerExtension, UpdateScatterPlotExt {
    override fun initRoutes(r: Routing) {
        r.apply {
            getST("/api$questionUrl/update_plot") {
                withQuestion {
                    assertPermission(
                        RoomPermission.VIEW_INDIVIDUAL_PREDICTIONS,
                        "need permession to view individual predictions"
                    )
                    val times = List2("t1", "t2").map {
                        call.request.queryParameters[it]?.toLong()?.let { Instant.fromEpochSeconds(it) }
                            ?: Instant.DISTANT_FUTURE
                    }
                    val users = serverState.userPred[question.ref]?.keys ?: emptySet()
                    val preds = users.mapNotNull { u->
                        val r = times.map { t-> serverState.userPredHistManager.at(question.ref, u, t) }
                        if (r == List2(null,null)) null else r
                    }
                    call.respond(preds)
                }
            }
        }
    }
}