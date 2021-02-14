package com.example

import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.handleRequest
import io.ktor.server.testing.withTestApplication
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
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
        withTestApplication({ module(testing = true) }) {
            val date = ZonedDateTime.now().minus(5L, ChronoUnit.DAYS)

            handleRequest(HttpMethod.Get, "/hoursDiff/${date.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)}").apply {
                assertEquals(HttpStatusCode.OK, response.status())

                assertEquals("120", response.content)
            }
        }
    }
}
