/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven.maven_profile;

import org.apache.maven.profiles.MavenProfilesBuilder;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class MavenProfilesBuilderAnonymous1Test {
    @Test
    void roleInitializesMavenProfilesBuilderInterface() {
        assertThat(MavenProfilesBuilder.ROLE).isEqualTo(MavenProfilesBuilder.class.getName());
    }
}
