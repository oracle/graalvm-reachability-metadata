/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_sourceforge_htmlunit.htmlunit_core_js;

import net.sourceforge.htmlunit.corejs.javascript.ImplementationVersion;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ImplementationVersionTest {
    @Test
    void getReadsImplementationVersionFromAvailableManifests() {
        String implementationVersion = ImplementationVersion.get();

        assertThat(implementationVersion).startsWith("Rhino ");
    }
}
