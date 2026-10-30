/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_sun_xml_bind.jaxb_core;

import com.sun.xml.bind.util.Which;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class WhichTest {
    @Test
    void locatesALibraryClassResource() {
        String location = Which.which(Which.class);

        assertThat(location).isNotNull().endsWith("com/sun/xml/bind/util/Which.class");
        assertThat(Which.which("com.sun.xml.bind.util.Which", Which.class.getClassLoader()))
                .isEqualTo(location);
    }

    @Test
    void returnsNullForAnAbsentClassResource() {
        assertThat(Which.which("com_sun_xml_bind.jaxb_core.AbsentModel", WhichTest.class.getClassLoader()))
                .isNull();
    }
}
