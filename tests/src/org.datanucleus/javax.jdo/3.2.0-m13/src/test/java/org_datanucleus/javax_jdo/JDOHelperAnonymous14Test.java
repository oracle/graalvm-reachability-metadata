/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_datanucleus.javax_jdo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

import javax.jdo.JDOFatalUserException;
import javax.jdo.JDOHelper;

import org.junit.jupiter.api.Test;

public class JDOHelperAnonymous14Test {
    private static final String MISSING_PROPERTIES_RESOURCE = "jdo-missing-pmf.properties";

    @Test
    void attemptsToLoadNamedPersistencePropertiesThroughResourceLoader() {
        RecordingResourceClassLoader loader = new RecordingResourceClassLoader(getClass().getClassLoader());

        assertThatThrownBy(() -> JDOHelper.getPersistenceManagerFactory(MISSING_PROPERTIES_RESOURCE, loader))
                .isInstanceOf(JDOFatalUserException.class);

        assertThat(loader.resourceStreamNames()).containsExactly(MISSING_PROPERTIES_RESOURCE);
    }

    private static final class RecordingResourceClassLoader extends ClassLoader {
        private final List<String> resourceStreamNames = new ArrayList<>();

        private RecordingResourceClassLoader(ClassLoader parent) {
            super(parent);
        }

        @Override
        public InputStream getResourceAsStream(String name) {
            resourceStreamNames.add(name);
            return null;
        }

        @Override
        public Enumeration<URL> getResources(String name) throws IOException {
            return Collections.emptyEnumeration();
        }

        private List<String> resourceStreamNames() {
            return resourceStreamNames;
        }
    }
}
