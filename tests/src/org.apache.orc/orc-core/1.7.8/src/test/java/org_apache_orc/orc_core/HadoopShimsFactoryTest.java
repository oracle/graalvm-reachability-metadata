/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_orc.orc_core;

import org.apache.orc.impl.HadoopShimsFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class HadoopShimsFactoryTest {

    @Test
    void createsVersionSpecificHadoopShim() {
        final Object shim = HadoopShimsFactory.get();

        assertThat(shim).isNotNull();
        assertThat(shim.getClass().getName()).startsWith("org.apache.orc.impl.HadoopShims");
    }

    @Test
    void reusesCreatedHadoopShim() {
        final Object firstShim = HadoopShimsFactory.get();
        final Object secondShim = HadoopShimsFactory.get();

        assertThat(secondShim).isSameAs(firstShim);
    }
}
