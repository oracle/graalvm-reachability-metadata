/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven.maven_artifact;

import org.apache.maven.artifact.resolver.ResolutionListener;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ResolutionListenerAnonymous1Test {
    @Test
    void roleInitializesResolutionListenerInterface() {
        assertThat(ResolutionListener.ROLE).isEqualTo(ResolutionListener.class.getName());
    }
}
