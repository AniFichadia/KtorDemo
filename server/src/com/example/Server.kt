package com.example

import io.ktor.application.Application
import io.ktor.application.ApplicationCall
import io.ktor.application.ApplicationStopping
import io.ktor.application.call
import io.ktor.application.feature
import io.ktor.application.install
import io.ktor.application.log
import io.ktor.client.HttpClient
import io.ktor.client.engine.apache.Apache
import io.ktor.client.features.HttpTimeout
import io.ktor.client.features.json.JsonFeature
import io.ktor.client.features.json.serializer.KotlinxSerializer
import io.ktor.client.request.parameter
import io.ktor.client.request.request
import io.ktor.features.CallLogging
import io.ktor.features.ContentNegotiation
import io.ktor.features.DataConversion
import io.ktor.features.DefaultHeaders
import io.ktor.features.DoubleReceive
import io.ktor.features.ParameterConversionException
import io.ktor.features.StatusPages
import io.ktor.html.respondHtml
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.locations.Location
import io.ktor.locations.Locations
import io.ktor.locations.get
import io.ktor.request.ApplicationRequest
import io.ktor.request.contentType
import io.ktor.request.httpMethod
import io.ktor.request.receive
import io.ktor.request.uri
import io.ktor.response.ApplicationResponse
import io.ktor.response.respond
import io.ktor.routing.HttpMethodRouteSelector
import io.ktor.routing.Route
import io.ktor.routing.Routing
import io.ktor.routing.routing
import io.ktor.serialization.json
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.util.error
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.html.InputType
import kotlinx.html.b
import kotlinx.html.body
import kotlinx.html.br
import kotlinx.html.div
import kotlinx.html.form
import kotlinx.html.h1
import kotlinx.html.h2
import kotlinx.html.head
import kotlinx.html.id
import kotlinx.html.input
import kotlinx.html.label
import kotlinx.html.p
import kotlinx.html.title
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.Serializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import org.slf4j.event.Level
import java.io.PrintWriter
import java.io.StringWriter
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit
import kotlin.random.Random
import kotlin.random.nextInt

object Server {
    @JvmStatic
    fun main(args: Array<String>) {
        embeddedServer(
            Netty,
            8080,
            module = Application::module,
            // TODO: autoreloading! Check out gradlew installDist -t (run config)
            watchPaths = listOf("classes", "resources"),
        ).start()
    }
}

val defaultDateFormat = DateTimeFormatter.ISO_OFFSET_DATE_TIME

fun Application.module(testing: Boolean = false) {
    // TODO: configure an HTTP client. This can be JVM agnostic and used in clients
    val httpClient = HttpClient(Apache) {
        install(JsonFeature) {
            serializer = KotlinxSerializer(Json {})
        }
        // TODO: don't forget to configure a timeout
        install(HttpTimeout) {
            val timeoutMillis = TimeUnit.SECONDS.toMillis(10)
            requestTimeoutMillis = timeoutMillis
            connectTimeoutMillis = timeoutMillis
            socketTimeoutMillis = timeoutMillis
        }
    }.apply {
        // TODO: close the client when the services stops
        environment.monitor.subscribe(ApplicationStopping) {
            close()
        }
    }



    install(DefaultHeaders)
    install(DoubleReceive)
    install(CallLogging) {
        logger = log
        level = Level.DEBUG
        format { call: ApplicationCall ->
            val request: ApplicationRequest = call.request
            val uri = request.uri
            val method = request.httpMethod.value
            val requestHasBody = request.receiveChannel().availableForRead > 0

            val response: ApplicationResponse = call.response
            val responseStatus = response.status()

            "$uri ($method, body: $requestHasBody) -> $responseStatus"
        }
    }
    install(StatusPages) {
        exception<Throwable> { cause ->
            log.error(cause)

            // TODO: note better status pages, you can test this with http://localhost:8080/?to=asdf
            val httpStatusCode = when (cause) {
                is ParameterConversionException -> HttpStatusCode.BadRequest
                else -> HttpStatusCode.InternalServerError
            }

            // If content type is JSON, respond with JSON, else respond HTML
            val contentType = context.request.contentType()
            when (contentType) {
                ContentType.Application.Json -> {
                    call.respond(httpStatusCode)
                }
                else -> {
                    // TODO: maybe don't actually dump a stack trace in prod :P
                    call.respondHtml(httpStatusCode) {
                        head {
                            title { +"Error: ${httpStatusCode.value}" }
                        }
                        body {
                            h1 { +"Error: ${httpStatusCode.value}" }
                            p {
                                b { +"${cause::class.simpleName} ${cause.message}" }
                            }
                            p {
                                val stackTraceString = cause.stackTraceString()
                                for (s in stackTraceString.split("\n")) {
                                    +s
                                    br {}
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    install(Routing)
    install(Locations)
    install(DataConversion) {
        convert<ZonedDateTime> {
            decode { values, _ ->
                values.singleOrNull()?.let { ZonedDateTime.parse(it, defaultDateFormat) }
            }

            encode { value ->
                when (value) {
                    null -> listOf()
                    is ZonedDateTime -> listOf(value.format(defaultDateFormat))
                    else -> throw Exception()
                }
            }
        }
    }
    install(ContentNegotiation) {
        json(
            Json { prettyPrint = true },
            ContentType.Application.Json
        )
    }

    routing {
        if (testing) {
            trace { log.debug(it.buildText()) }
        }


        get<RandomLocation> { request ->
            // TODO: we've converted this route to respond with JSON!
            val count = request.count
            val from = request.from
            val to = request.to
            if (count !in 1..1000) {
                return@get call.respond(HttpStatusCode.BadRequest)
            }

            // TODO: do a little bit of coroutine fun, do these asynchronously
            val randomResults = (1..count)
                .map {
                    async(Dispatchers.IO) {
                        delay(Random.nextLong(5, 100))

                        Random.nextInt(from..to)
                    }
                }
                .awaitAll()

            call.respond(randomResults)
        }

        get<RootLocation> { request ->
            // TODO: this route now serves HTML and does some serverside stuff

            val count = request.count
            val from = request.from
            val to = request.to

            val randomInts: List<Int> = if (count != null || from != null || to != null) {
                // TODO: use the client, note ... this is janky and doesn't use error handling etc
                httpClient.request {
                    url {
                        port = 8080
                        path("/random")
                    }

                    parameter("count", count)
                    parameter("from", from)
                    parameter("to", to)
                }
            } else {
                emptyList()
            }

            // TODO: serve up some HTML. You can use a bunch of templating engines like FTL, etc, but here we're using kotlinx html
            call.respondHtml {
                head {
                    title { +"RNG-ify" }
                }
                body {
                    h1 { +"Lets get random!" }

                    form {
                        label {
                            htmlFor = "count"
                            +"Count: "
                        }
                        input(type = InputType.number) {
                            id = "count"
                            name = "count"
                            min = "1"
                            max = "1000"
                            required = true
                            value = count?.toString() ?: ""
                        }
                        br {}
                        label {
                            htmlFor = "from"
                            +"From: "
                        }
                        input(type = InputType.number) {
                            id = "from"
                            name = "from"
                            required = true
                            value = from?.toString() ?: ""
                        }
                        br {}
                        label {
                            htmlFor = "to"
                            +"To: "
                        }
                        input(type = InputType.number) {
                            id = "to"
                            name = "to"
                            required = true
                            value = to?.toString() ?: ""
                        }
                        br {}
                        br {}
                        input(type = InputType.submit)
                    }

                    if (randomInts.isNotEmpty()) {
                        br { }
                        br { }

                        h2 { +"Your random ints" }
                        div {
                            p { +randomInts.joinToString() }
                        }
                    }
                }
            }
        }


        get<HoursDiffLocation> {
            val requestBody = call.receive<HoursDiffRequest>()

            val hoursBetween = ChronoUnit.HOURS.between(requestBody.from, requestBody.to)

            call.respond(
                HoursDiffResponse(
                    from = requestBody.from,
                    to = requestBody.to,
                    hoursBetween = hoursBetween,
                )
            )
        }
    }

    if (testing) {
        val root = feature(Routing)
        val allRoutes = allRoutes(root)
        val allRoutesWithMethod = allRoutes.filter { it.selector is HttpMethodRouteSelector }
        allRoutesWithMethod.forEach {
            log.info("route: $it")
        }
    }
}

fun allRoutes(root: Route): List<Route> {
    return listOf(root) + root.children.flatMap { allRoutes(it) }
}

fun Throwable.stackTraceString(): String {
    val sw = StringWriter()
    val pw = PrintWriter(sw)
    printStackTrace(pw)
    return sw.toString()
}

@Location("")
data class RootLocation(
    val count: Int? = null,
    val from: Int? = null,
    val to: Int? = null,
)

@Location("/random")
data class RandomLocation(val count: Int = 1, val from: Int = 0, val to: Int = Int.MAX_VALUE)

@Location("/hoursDiff")
class HoursDiffLocation


@Serializer(forClass = ZonedDateTime::class)
class ZonedDateTimeSerializer : KSerializer<ZonedDateTime> {

    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("ZonedDateTime", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): ZonedDateTime {
        val text = decoder.decodeString()
        return ZonedDateTime.parse(text, defaultDateFormat)
    }

    override fun serialize(encoder: Encoder, value: ZonedDateTime) {
        encoder.encodeString(value.format(defaultDateFormat))
    }
}

@Serializable
data class HoursDiffRequest(
    @Serializable(with = ZonedDateTimeSerializer::class)
    val from: ZonedDateTime,
    @Serializable(with = ZonedDateTimeSerializer::class)
    val to: ZonedDateTime,
)

@Serializable
data class HoursDiffResponse(
    @Serializable(with = ZonedDateTimeSerializer::class)
    val from: ZonedDateTime,
    @Serializable(with = ZonedDateTimeSerializer::class)
    val to: ZonedDateTime,
    val hoursBetween: Long,
)
