/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_cxf.cxf_rt_core;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.cxf.service.factory.DefaultServiceConfiguration;
import org.junit.jupiter.api.Test;

public class DefaultServiceConfigurationTest {
    @Test
    void recognizesAndResolvesGeneratedStyleHolderClasses() {
        DefaultServiceConfiguration configuration = new DefaultServiceConfiguration();

        assertThat(configuration.isHolder(Holder.class, Holder.class)).isTrue();
        assertThat(configuration.getHolderType(Holder.class, Holder.class)).isEqualTo(String.class);
        assertThat(configuration.isHolder(String.class, String.class)).isFalse();
    }

    public static final class Holder {
        public String value;
    }
}
