package com.example

import io.ktor.application.Application
import io.ktor.application.ApplicationCall
import io.ktor.application.call
import io.ktor.application.feature
import io.ktor.application.install
import io.ktor.application.log
import io.ktor.features.CallLogging
import io.ktor.features.ContentNegotiation
import io.ktor.features.DataConversion
import io.ktor.features.DefaultHeaders
import io.ktor.features.DoubleReceive
import io.ktor.features.ParameterConversionException
import io.ktor.features.StatusPages
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.locations.Location
import io.ktor.locations.Locations
import io.ktor.locations.get
import io.ktor.request.ApplicationRequest
import io.ktor.request.httpMethod
import io.ktor.request.receive
import io.ktor.request.uri
import io.ktor.response.ApplicationResponse
import io.ktor.response.respond
import io.ktor.response.respondText
import io.ktor.routing.HttpMethodRouteSelector
import io.ktor.routing.Route
import io.ktor.routing.Routing
import io.ktor.routing.routing
import io.ktor.serialization.json
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.util.error
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.Serializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import org.slf4j.event.Level
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.random.Random
import kotlin.random.nextInt

object Server {
    @JvmStatic
    fun main(args: Array<String>) {
        embeddedServer(
            Netty,
            8080,
            module = Application::module
        ).start()
    }
}

val defaultDateFormat = DateTimeFormatter.ISO_OFFSET_DATE_TIME

fun Application.module(testing: Boolean = false) {

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

            when (cause) {
                is ParameterConversionException -> call.respond(HttpStatusCode.BadRequest)
                else -> call.respond(HttpStatusCode.InternalServerError)
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
    // TODO: install content negotiation and serialization
    // TODO: Note new dependencies and plugin applied
    install(ContentNegotiation) {
        // TODO: Configure JSON serialization
        json(
            // TODO: configure Kotlinx Serialization
            Json {
                prettyPrint = true

                serializersModule = SerializersModule {
                    // TODO: if you need custom serialization, you can set it up here. Things like Polymorphic serialization can be ... "fun" ...
                }
            },
            ContentType.Application.Json
        )
        // TODO: you can register different serializers here
    }

    routing {
        if (testing) {
            trace { log.debug(it.buildText()) }
        }

        get<RootLocation> { request ->
            val name = request.name
            val text = if (!name.isNullOrBlank()) {
                "Hello $name!"
            } else {
                "Hello world!"
            }

            call.respondText(text, contentType = ContentType.Text.Plain)
        }


        get<RandomLocation> { request ->
            val (from, to) = request

            val randomValue = Random.nextInt(from..to)

            call.respondText(randomValue.toString(), contentType = ContentType.Text.Plain)
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

@Location("")
data class RootLocation(val name: String? = null)

@Location("/random")
data class RandomLocation(val from: Int = 0, val to: Int = Int.MAX_VALUE)

@Location("/hoursDiff")
class HoursDiffLocation


// TODO: setup Serializer
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

// TODO: setup request class
@Serializable
data class HoursDiffRequest(
    @Serializable(with = ZonedDateTimeSerializer::class)
    val from: ZonedDateTime,
    @Serializable(with = ZonedDateTimeSerializer::class)
    val to: ZonedDateTime,
)

// TODO: setup response class
@Serializable
data class HoursDiffResponse(
    @Serializable(with = ZonedDateTimeSerializer::class)
    val from: ZonedDateTime,
    @Serializable(with = ZonedDateTimeSerializer::class)
    val to: ZonedDateTime,
    val hoursBetween: Long,
)
