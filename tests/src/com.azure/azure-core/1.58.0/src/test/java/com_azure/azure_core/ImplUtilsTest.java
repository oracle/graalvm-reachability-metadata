/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_azure.azure_core;

import static org.assertj.core.api.Assertions.assertThat;

import com.azure.core.http.policy.ExponentialBackoff;
import com.azure.core.http.policy.RetryStrategy;
import com.azure.core.implementation.ImplUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(60)
public class ImplUtilsTest {
    @Test
    void resolvesAConfiguredRetryStrategyType() {
        Class<? extends RetryStrategy> strategyType =
                ImplUtils.getClassByName("com.azure.core.http.policy.ExponentialBackoff");

        assertThat(strategyType).isEqualTo(ExponentialBackoff.class);
        assertThat(RetryStrategy.class).isAssignableFrom(strategyType);
    }
}
