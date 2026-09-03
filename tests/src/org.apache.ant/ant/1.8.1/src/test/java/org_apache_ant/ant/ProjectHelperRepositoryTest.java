/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_ant.ant;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.apache.tools.ant.ProjectHelper;
import org.apache.tools.ant.ProjectHelperRepository;
import org.apache.tools.ant.helper.ProjectHelper2;
import org.junit.jupiter.api.Test;

public class ProjectHelperRepositoryTest {
    @Test
    void loadsConfiguredAndServiceProjectHelpers() {
        String propertyName = ProjectHelper.HELPER_PROPERTY;
        String previousHelper = System.getProperty(propertyName);
        Thread thread = Thread.currentThread();
        ClassLoader previousLoader = thread.getContextClassLoader();
        System.setProperty(propertyName, LoadedProjectHelper.class.getName());

        try {
            assertThat(ProjectHelper.getProjectHelper()).isInstanceOf(LoadedProjectHelper.class);
            assertThat(hasServiceHelper(helpers())).isTrue();
        } finally {
            thread.setContextClassLoader(previousLoader);
            if (previousHelper == null) {
                System.clearProperty(propertyName);
            } else {
                System.setProperty(propertyName, previousHelper);
            }
        }
    }

    private List<ProjectHelper> helpers() {
        List<ProjectHelper> helpers = new ArrayList<>();
        Iterator<?> iterator = ProjectHelperRepository.getInstance().getHelpers();
        while (iterator.hasNext()) {
            helpers.add((ProjectHelper) iterator.next());
        }
        return helpers;
    }

    private boolean hasServiceHelper(List<ProjectHelper> helpers) {
        for (ProjectHelper helper : helpers) {
            if (helper instanceof ServiceProjectHelper) {
                return true;
            }
        }
        return false;
    }

    public static class LoadedProjectHelper extends ProjectHelper2 {
        public LoadedProjectHelper() {
            Thread.currentThread().setContextClassLoader(ClassLoader.getPlatformClassLoader());
        }
    }

    public static class ServiceProjectHelper extends ProjectHelper {
        public ServiceProjectHelper() {
        }
    }
}
