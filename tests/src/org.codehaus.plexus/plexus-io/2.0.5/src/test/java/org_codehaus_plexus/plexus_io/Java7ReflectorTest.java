/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_codehaus_plexus.plexus_io;

import org.codehaus.plexus.components.io.attributes.Java7Reflector;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class Java7ReflectorTest {
    @Test
    public void detectsJavaNioFileSupport() {
        assertThat(Java7Reflector.isJava7()).isTrue();
    }
}
