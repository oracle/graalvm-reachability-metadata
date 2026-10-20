/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kylin.kylin_jdbc;

import org.apache.kylin.jdbc.shaded.org.apache.calcite.avatica.util.Base64;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class Base64Anonymous1Test {
    @Test
    void decodesAnObjectWithTheProvidedApplicationClassLoader() throws Exception {
        String encoded = Base64.encodeObject("kylin");

        Object decoded = Base64.decodeToObject(encoded, Base64.NO_OPTIONS, getClass().getClassLoader());

        assertThat(decoded).isEqualTo("kylin");
    }
}
