package com.example

import io.ktor.application.Application
import io.ktor.application.ApplicationCall
import io.ktor.application.call
import io.ktor.application.feature
import io.ktor.application.install
import io.ktor.application.log
import io.ktor.features.CallLogging
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
import io.ktor.request.uri
import io.ktor.response.ApplicationResponse
import io.ktor.response.respond
import io.ktor.response.respondText
import io.ktor.routing.HttpMethodRouteSelector
import io.ktor.routing.Route
import io.ktor.routing.Routing
import io.ktor.routing.routing
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.util.error
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
    // TODO: Install data conversion feature. This is for query params only!
    install(DataConversion) {
        convert<ZonedDateTime> {
            val format = DateTimeFormatter.ISO_OFFSET_DATE_TIME

            decode { values, _ ->
                values.singleOrNull()?.let { ZonedDateTime.parse(it, format) }
            }

            encode { value ->
                when (value) {
                    null -> listOf()
                    is ZonedDateTime -> listOf(value.format(format))
                    else -> throw Exception()
                }
            }
        }
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


        // TODO: new route now handles ZonedDateTime! Try commenting out the data conversion block
        get<HoursDiffLocation> { request ->
            val from = request.from
            val now = ZonedDateTime.now()

            val between = ChronoUnit.HOURS.between(from, now)

            call.respondText(between.toString(), contentType = ContentType.Text.Plain)
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

@Location("/hoursDiff/{from}")
data class HoursDiffLocation(val from: ZonedDateTime)
