/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_github_resilience4j.resilience4j_core;

import io.github.resilience4j.core.CompletionStageUtils;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Recovers failed asynchronous operations with Resilience4j's completion-stage subsystem.
 *
 * A profile service keeps a locally available profile when its remote lookup fails, while a
 * successful remote lookup continues through unchanged. The application observes both outcomes
 * as completed profile values without handling completion-stage exceptional results itself.
 */
class CompletionStageRecoveryTest {

    @Test
    void suppliesFallbackForFailedProfileLookupWithoutChangingSuccess() {
        CompletionStage<String> unavailableProfile = CompletableFuture.failedFuture(
                new IllegalStateException("profile service unavailable"));
        CompletionStage<String> recoveredProfile = CompletionStageUtils.recover(
                unavailableProfile,
                IllegalStateException.class,
                failure -> "cached-profile");

        CompletionStage<String> remoteProfile = CompletableFuture.completedFuture("remote-profile");
        CompletionStage<String> unchangedProfile = CompletionStageUtils.recover(
                remoteProfile,
                IllegalStateException.class,
                failure -> "cached-profile");

        assertThat(recoveredProfile.toCompletableFuture().join()).isEqualTo("cached-profile");
        assertThat(unchangedProfile.toCompletableFuture().join()).isEqualTo("remote-profile");
    }
}
