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
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

public class ResourcesInnerShadowResourceBundleTest extends Resources.ShadowResourceBundle {
    public ResourcesInnerShadowResourceBundleTest() throws IOException {
        super();
    }

    @Test
    public void loadsPropertiesThroughShadowResourceBundle() {
        assertThat(getString("message")).isEqualTo("shadow bundle loaded");
        assertThat(getString("format")).isEqualTo("Hello {0}");
        assertThat(Collections.list(getKeys())).contains("message", "format");
    }
}
