/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.pulsar.shade.org.glassfish.hk2.utilities.general.GeneralUtilities;
import org.junit.jupiter.api.Test;

public class GeneralUtilitiesTest {

    @Test
    void loadsOrdinaryAndMultidimensionalArrayClasses() {
        final ClassLoader loader = getClass().getClassLoader();

        assertThat(GeneralUtilities.loadClass(loader, String.class.getName())).isEqualTo(String.class);
        assertThat(GeneralUtilities.loadClass(loader, "[[Ljava.lang.String;")).isEqualTo(String[][].class);
        assertThat(GeneralUtilities.loadClass(loader, "[[I")).isEqualTo(int[][].class);
    }
}
