/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_htrace.htrace_core;

import java.util.Collections;

import org.apache.htrace.HTraceConfiguration;
import org.apache.htrace.Sampler;
import org.apache.htrace.SamplerBuilder;
import org.apache.htrace.impl.AlwaysSampler;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SamplerBuilderTest {
    @Test
    void buildsConfiguredSamplerFromUnqualifiedClassName() {
        HTraceConfiguration configuration = HTraceConfiguration.fromMap(Collections.singletonMap(
                SamplerBuilder.SAMPLER_CONF_KEY,
                "AlwaysSampler"));

        Sampler<?> sampler = new SamplerBuilder(configuration).build();

        assertThat(sampler).isInstanceOf(AlwaysSampler.class);
        assertThat(((AlwaysSampler) sampler).next(new Object())).isTrue();
    }
}
