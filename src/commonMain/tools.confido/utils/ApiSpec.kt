package tools.confido.utils

import kotlinx.serialization.KSerializer

/**
 * A [PathTemplate] bound to an HTTP method and the endpoint's wire types — the
 * [TopicSpec] idea applied to the request/response API: one declaration per endpoint
 * (tools.confido.api.Api) serves server registration (`apiST(spec) { args, req -> resp }`,
 * with the handler's parameter and return types checked against the spec) and client
 * invocation (`Client.sendData(spec(ref), payload)`), so URL shape, parameter types and
 * body types cannot drift between the two sides.
 *
 * `Unit` on either side means "no body": the client sends none / ignores the response
 * body, the server skips receive / responds with a bare 200 (see [encodesUnit]).
 */
enum class ApiMethod { GET, POST, PUT, DELETE }

/** An endpoint invoked with its path arguments: a concrete URL plus the wire types. */
class BoundApi<Req, Resp>(
    val method: ApiMethod,
    val url: String,
    val reqSer: KSerializer<Req>,
    val respSer: KSerializer<Resp>,
) {
    override fun toString() = "${method.name} $url"
}

/** The serializer-level "no body" marker backing the `Unit` convention above. */
internal val KSerializer<*>.encodesUnit get() = descriptor.serialName == "kotlin.Unit"

class ApiSpec0<Req, Resp>(
    val method: ApiMethod, val path: PathTemplate0,
    val reqSer: KSerializer<Req>, val respSer: KSerializer<Resp>,
) {
    operator fun invoke() = BoundApi(method, "/" + path(), reqSer, respSer)
}

class ApiSpec1<A : Any, Req, Resp>(
    val method: ApiMethod, val path: PathTemplate1<A>,
    val reqSer: KSerializer<Req>, val respSer: KSerializer<Resp>,
) {
    operator fun invoke(a: A) = BoundApi(method, "/" + path(a), reqSer, respSer)
}

class ApiSpec2<A : Any, B : Any, Req, Resp>(
    val method: ApiMethod, val path: PathTemplate2<A, B>,
    val reqSer: KSerializer<Req>, val respSer: KSerializer<Resp>,
) {
    operator fun invoke(a: A, b: B) = BoundApi(method, "/" + path(a, b), reqSer, respSer)
}

// Arity-selecting factory: the path template's type picks the spec class, so
// declarations never spell the digit. (Reified Req/Resp serializers are not
// offered for the same reason as in Topics: explicit type arguments cannot be
// partial, so they would force spelling the path-arg types too.)

fun <Req, Resp> ApiSpec(method: ApiMethod, path: PathTemplate0, reqSer: KSerializer<Req>, respSer: KSerializer<Resp>) =
    ApiSpec0(method, path, reqSer, respSer)

fun <A : Any, Req, Resp> ApiSpec(method: ApiMethod, path: PathTemplate1<A>, reqSer: KSerializer<Req>, respSer: KSerializer<Resp>) =
    ApiSpec1(method, path, reqSer, respSer)

fun <A : Any, B : Any, Req, Resp> ApiSpec(method: ApiMethod, path: PathTemplate2<A, B>, reqSer: KSerializer<Req>, respSer: KSerializer<Resp>) =
    ApiSpec2(method, path, reqSer, respSer)
