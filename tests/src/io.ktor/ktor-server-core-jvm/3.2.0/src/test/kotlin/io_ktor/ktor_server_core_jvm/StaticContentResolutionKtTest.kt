/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_server_core_jvm

import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.http.content.OutgoingContent
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.http.content.resolveResource
import io.ktor.server.http.content.staticResources
import io.ktor.server.request.ApplicationRequest
import io.ktor.server.response.ApplicationResponse
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import io.ktor.util.Attributes
import io.ktor.util.reflect.TypeInfo
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.net.URL
import java.util.Collections
import java.util.Enumeration
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

public class StaticContentResolutionKtTest {
    @Test
    fun `application call resolves missing resource through provided class loader`(): Unit {
        val classLoader: RecordingClassLoader = RecordingClassLoader()
        val content: OutgoingContent.ReadChannelContent? = MinimalApplicationCall.resolveResource(
            path = "missing.txt",
            resourcePackage = "coverage.pkg",
            classLoader = classLoader
        )

        assertThat(content).isNull()
        assertThat(classLoader.requestedResources).containsExactly("coverage/pkg/missing.txt")
    }

    @Test
    fun `static resources route attempts classpath resource lookup before returning not found`(): Unit =
        testApplication {
            application {
                routing {
                    staticResources(
                        remotePath = "/assets",
                        basePackage = "coverage.pkg",
                        index = null
                    )
                }
            }

            val response = client.get("/assets/missing.txt")

            assertThat(response.status).isEqualTo(HttpStatusCode.NotFound)
        }

    private object MinimalApplicationCall : ApplicationCall {
        override val coroutineContext: CoroutineContext = EmptyCoroutineContext
        override val attributes: Attributes get() = error("Attributes are not needed for direct resource resolution")
        override val request: ApplicationRequest get() = error("Request is not needed for direct resource resolution")
        override val response: ApplicationResponse
            get() = error("Response is not needed for direct resource resolution")
        override val application: Application get() = error("Application is not needed when a class loader is supplied")
        override val parameters: Parameters get() = error("Parameters are not needed for direct resource resolution")

        override suspend fun <T> receiveNullable(typeInfo: TypeInfo): T? {
            error("Receiving content is not needed for direct resource resolution")
        }

        override suspend fun respond(message: Any?, typeInfo: TypeInfo?): Unit {
            error("Responding is not needed for direct resource resolution")
        }
    }

    private class RecordingClassLoader : ClassLoader(null) {
        val requestedResources: MutableList<String> = mutableListOf()

        override fun getResources(name: String): Enumeration<URL> {
            requestedResources.add(name)
            return Collections.emptyEnumeration()
        }
    }
}
