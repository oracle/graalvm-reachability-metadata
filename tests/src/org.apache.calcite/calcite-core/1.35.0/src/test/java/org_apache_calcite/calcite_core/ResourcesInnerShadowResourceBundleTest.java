/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_calcite.calcite_core;

import org.apache.calcite.runtime.Resources;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

public class ResourcesInnerShadowResourceBundleTest {
    @Test
    void loadsPropertiesThatShadowBundleClass() throws IOException {
        ShadowMessages messages = new ShadowMessages();

        assertThat(messages.getString("message")).isEqualTo("from shadow bundle");
    }
}

class ShadowMessages extends Resources.ShadowResourceBundle {
    ShadowMessages() throws IOException {
        super();
    }
}
