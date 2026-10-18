/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.pulsar.shade.org.glassfish.hk2.utilities.HK2LoaderImpl;
import org.junit.jupiter.api.Test;

public class HK2LoaderImplTest {

    @Test
    void loadsClassesWithItsConfiguredLoader() {
        final HK2LoaderImpl loader = new HK2LoaderImpl(getClass().getClassLoader());

        assertThat(loader.loadClass(String.class.getName())).isEqualTo(String.class);
        assertThat(loader.toString()).contains("HK2LoaderImpl");
    }
}
