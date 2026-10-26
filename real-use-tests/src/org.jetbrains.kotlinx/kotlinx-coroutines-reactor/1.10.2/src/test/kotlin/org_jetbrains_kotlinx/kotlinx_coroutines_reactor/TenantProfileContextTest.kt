/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jetbrains_kotlinx.kotlinx_coroutines_reactor

import java.time.Duration
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.reactor.ReactorContext
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.mono
import kotlinx.coroutines.yield
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import reactor.util.context.Context

/**
 * Exercises ReactorContext propagation for a tenant-scoped coroutine profile endpoint.
 * Subscriber identity follows a suspending handler into an awaited reactive directory lookup.
 */
@Timeout(60)
class TenantProfileContextTest {
    @Test
    fun preservesTenantAndRequestIdentityAcrossTheCoroutineBoundary(): Unit {
        val directory: ProfileDirectory = ProfileDirectory(
            mapOf(
                "north" to mapOf("user-7" to "Nora North"),
                "south" to mapOf("user-7" to "Sam South")
            )
        )
        val handler: ProfileHandler = ProfileHandler(directory)
        val response: Mono<ProfileResponse> = mono { handler.profile("user-7") }

        StepVerifier.create(response.contextWrite(Context.of("tenant", "north", "requestId", "req-101")))
            .expectNext(ProfileResponse("req-101", "north", "user-7", "Nora North"))
            .expectComplete()
            .verify(Duration.ofSeconds(10))

        StepVerifier.create(response.contextWrite(Context.of("tenant", "south", "requestId", "req-102")))
            .expectNext(ProfileResponse("req-102", "south", "user-7", "Sam South"))
            .expectComplete()
            .verify(Duration.ofSeconds(10))

        StepVerifier.create(response.contextWrite(Context.of("tenant", "north", "requestId", "req-103")))
            .expectNext(ProfileResponse("req-103", "north", "user-7", "Nora North"))
            .expectComplete()
            .verify(Duration.ofSeconds(10))
    }

    private class ProfileHandler(private val directory: ProfileDirectory) {
        suspend fun profile(userId: String): ProfileResponse {
            val context: Context = requireNotNull(currentCoroutineContext()[ReactorContext]).context
            val requestId: String = context.get("requestId")
            yield()
            val profile: TenantProfile = directory.find(userId).awaitSingle()
            return ProfileResponse(requestId, profile.tenant, userId, profile.displayName)
        }
    }

    private class ProfileDirectory(private val profiles: Map<String, Map<String, String>>) {
        fun find(userId: String): Mono<TenantProfile> = Mono.deferContextual { context ->
            val tenant: String = context.get("tenant")
            Mono.just(TenantProfile(tenant, profiles.getValue(tenant).getValue(userId)))
        }
    }

    private data class TenantProfile(val tenant: String, val displayName: String)

    private data class ProfileResponse(
        val requestId: String,
        val tenant: String,
        val userId: String,
        val displayName: String
    )
}
