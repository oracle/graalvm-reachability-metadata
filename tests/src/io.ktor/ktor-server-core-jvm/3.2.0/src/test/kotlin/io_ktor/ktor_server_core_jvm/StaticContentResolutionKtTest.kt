/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_server_core_jvm

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.http.content.resolveResource
import io.ktor.server.http.content.staticResources
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

private const val STATIC_RESOURCE_PACKAGE: String = "io_ktor.ktor_server_core_jvm.static_content"
private const val STATIC_RESOURCE_BODY: String = "Ktor static resource resolution test resource"

public class StaticContentResolutionKtTest {
    @Test
    fun `application call resolves classpath resource through configured classloader`(): Unit {
        testApplication {
            routing {
                get("/resolve-directly") {
                    val content = call.resolveResource(
                        path = "message.txt",
                        resourcePackage = STATIC_RESOURCE_PACKAGE
                    )

                    if (content == null) {
                        call.respondText("missing", status = HttpStatusCode.NotFound)
                    } else {
                        call.respond(content)
                    }
                }
            }

            val response = client.get("/resolve-directly")

            assertThat(response.status).isEqualTo(HttpStatusCode.OK)
            assertThat(response.bodyAsText()).isEqualTo(STATIC_RESOURCE_BODY)
        }
    }

    @Test
    fun `static resources route resolves classpath resource through application cache`(): Unit {
        testApplication {
            routing {
                staticResources(
                    remotePath = "/static",
                    basePackage = STATIC_RESOURCE_PACKAGE,
                    index = null
                )
            }

            val response = client.get("/static/message.txt")

            assertThat(response.status).isEqualTo(HttpStatusCode.OK)
            assertThat(response.bodyAsText()).isEqualTo(STATIC_RESOURCE_BODY)
        }
    }
}
