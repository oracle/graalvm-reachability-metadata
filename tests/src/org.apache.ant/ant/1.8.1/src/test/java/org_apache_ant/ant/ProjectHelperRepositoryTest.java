/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_ant.ant;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.tools.ant.ProjectHelper;
import org.junit.jupiter.api.Test;

public class ProjectHelperRepositoryTest {
    @Test
    void loadsConfiguredProjectHelperThroughFallbackClassLookup() {
        String propertyName = ProjectHelper.HELPER_PROPERTY;
        String previousHelper = System.getProperty(propertyName);
        Thread thread = Thread.currentThread();
        ClassLoader previousLoader = thread.getContextClassLoader();
        thread.setContextClassLoader(ClassLoader.getPlatformClassLoader());
        System.setProperty(propertyName, ConfiguredProjectHelper.class.getName());

        try {
            assertThat(ProjectHelper.getProjectHelper()).isInstanceOf(ConfiguredProjectHelper.class);
        } finally {
            thread.setContextClassLoader(previousLoader);
            if (previousHelper == null) {
                System.clearProperty(propertyName);
            } else {
                System.setProperty(propertyName, previousHelper);
            }
        }
    }

    public static class ConfiguredProjectHelper extends ProjectHelper {
        public ConfiguredProjectHelper() {
        }
    }
}
