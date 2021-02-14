package com.example

import io.ktor.application.Application
import io.ktor.application.ApplicationCall
import io.ktor.application.call
import io.ktor.application.feature
import io.ktor.application.install
import io.ktor.application.log
import io.ktor.features.CallLogging
import io.ktor.features.DefaultHeaders
import io.ktor.features.DoubleReceive
import io.ktor.features.StatusPages
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.request.ApplicationRequest
import io.ktor.request.httpMethod
import io.ktor.request.uri
import io.ktor.response.ApplicationResponse
import io.ktor.response.respond
import io.ktor.response.respondText
import io.ktor.routing.HttpMethodRouteSelector
import io.ktor.routing.Route
import io.ktor.routing.Routing
import io.ktor.routing.get
import io.ktor.routing.post
import io.ktor.routing.route
import io.ktor.routing.routing
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.util.error
import org.slf4j.event.Level
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

            call.respond(HttpStatusCode.InternalServerError)
        }
    }
    install(Routing)

    routing {
        if (testing) {
            trace { log.debug(it.buildText()) }
        }

        route("/") {
            get {
                val request: ApplicationRequest = call.request
                val name = request.queryParameters["name"]

                val text = if (name != null) {
                    "Hello $name!"
                } else {
                    "Hello world!"
                }

                call.respondText(text, contentType = ContentType.Text.Plain)
            }

            post("/") {
                call.respond(HttpStatusCode.InternalServerError)
            }
        }


        get("/random") {
            val from = try {
                call.request.queryParameters["from"]?.toInt() ?: 0
            } catch (exception: Throwable) {
                return@get call.respond(HttpStatusCode.BadRequest)
            }
            val to = try {
                call.request.queryParameters["to"]?.toInt() ?: Int.MAX_VALUE
            } catch (exception: Throwable) {
                return@get call.respond(HttpStatusCode.BadRequest)
            }

            val randomValue = Random.nextInt(from..to)

            call.respondText(randomValue.toString(), contentType = ContentType.Text.Plain)
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
