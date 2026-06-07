/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven.maven_project;

import org.apache.maven.model.Profile;
import org.apache.maven.profiles.DefaultProfileManager;
import org.apache.maven.project.DefaultMavenProjectBuilder;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class DefaultMavenProjectBuilderTest {
    @Test
    void buildStandaloneSuperProjectLoadsBundledSuperPomResource() {
        DefaultMavenProjectBuilder builder = new DefaultMavenProjectBuilder();
        builder.initialize();

        SuperPomProfilesLoadedException exception = assertThrows(
                SuperPomProfilesLoadedException.class,
                () -> builder.buildStandaloneSuperProject(null, new SuperPomCapturingProfileManager()));

        assertThat(exception.getProfileIds()).containsExactly("release-profile");
    }

    private static final class SuperPomCapturingProfileManager extends DefaultProfileManager {
        private SuperPomCapturingProfileManager() {
            super(null);
        }

        @Override
        public void addProfiles(List profiles) {
            List profileIds = new ArrayList();
            for (Object profileObject : profiles) {
                Profile profile = (Profile) profileObject;
                profileIds.add(profile.getId());
            }
            throw new SuperPomProfilesLoadedException(profileIds);
        }
    }

    private static final class SuperPomProfilesLoadedException extends RuntimeException {
        private final List profileIds;

        private SuperPomProfilesLoadedException(List profileIds) {
            this.profileIds = profileIds;
        }

        private List getProfileIds() {
            return profileIds;
        }
    }
}
