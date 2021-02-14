package com.example

import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.handleRequest
import io.ktor.server.testing.setBody
import io.ktor.server.testing.withTestApplication
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ServerTest {

    @Test
    fun testRoot() {
        withTestApplication({ module(testing = true) }) {
            handleRequest(HttpMethod.Get, "/").apply {
                assertEquals(HttpStatusCode.OK, response.status())
                assertEquals("Hello world!", response.content)
            }

            handleRequest(HttpMethod.Get, "/?name=ani").apply {
                assertEquals(HttpStatusCode.OK, response.status())
                assertEquals("Hello ani!", response.content)
            }
        }
    }

    @Test
    fun testRandom() {
        withTestApplication({ module(testing = true) }) {
            handleRequest(HttpMethod.Get, "/random").apply {
                assertEquals(HttpStatusCode.OK, response.status())

                println(response.content)
                val contentInt = response.content!!.toInt()
                assertTrue { contentInt in 0..Int.MAX_VALUE }
            }


            handleRequest(HttpMethod.Get, "/random?from=notAnInt").apply {
                assertEquals(HttpStatusCode.BadRequest, response.status())
            }

            handleRequest(HttpMethod.Get, "/random?from=10&to=100").apply {
                assertEquals(HttpStatusCode.OK, response.status())

                val contentInt = response.content!!.toInt()
                assertTrue { contentInt in 10..100 }
            }
        }
    }


    @Test
    fun testHoursDiff() {
        val jsonSerializer = Json { prettyPrint = true }

        withTestApplication({ module(testing = true) }) {
            val now = ZonedDateTime.now()
            val date = now.minus(5L, ChronoUnit.DAYS)
            val request = HoursDiffRequest(
                from = date,
                to = now,
            )

            handleRequest (HttpMethod.Get, "/hoursDiff"){
                addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                setBody(jsonSerializer.encodeToString(request))
            }.apply {
                assertEquals(HttpStatusCode.OK, response.status())

                val response = jsonSerializer.decodeFromString<HoursDiffResponse>(response.content!!)

                assertEquals(120, response.hoursBetween)
            }
        }
    }
}
