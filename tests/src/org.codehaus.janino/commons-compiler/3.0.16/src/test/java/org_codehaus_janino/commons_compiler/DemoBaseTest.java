/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_codehaus_janino.commons_compiler;

import static org.assertj.core.api.Assertions.assertThat;

import org.codehaus.commons.compiler.samples.DemoBase;
import org.junit.jupiter.api.Test;

public class DemoBaseTest {

    @Test
    void createsObjectsThroughEmptyAndStringConstructors() throws Exception {
        Object emptyString = DemoBase.createObject(String.class, "");
        Object populatedString = DemoBase.createObject(String.class, "janino");

        assertThat(emptyString).isEqualTo("");
        assertThat(populatedString).isEqualTo("janino");
    }

    @Test
    void resolvesNamedArrayTypes() {
        assertThat(DemoBase.stringToType("java.lang.String[]")).isEqualTo(String[].class);
    }
}
