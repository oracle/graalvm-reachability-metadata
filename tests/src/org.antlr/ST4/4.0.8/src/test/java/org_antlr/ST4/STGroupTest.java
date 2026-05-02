/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_antlr.ST4;

import org.junit.jupiter.api.Test;
import org.stringtemplate.v4.STGroup;

import java.net.URL;

import static org.assertj.core.api.Assertions.assertThat;

public class STGroupTest {
    @Test
    void returnsNullForMissingClasspathResourceAfterCheckingConfiguredLoaders() {
        final STGroup group = new STGroup();

        final URL resolvedResource = group.getURL("org_antlr/ST4/missing-template-resource.stg");

        assertThat(resolvedResource).isNull();
    }
}
