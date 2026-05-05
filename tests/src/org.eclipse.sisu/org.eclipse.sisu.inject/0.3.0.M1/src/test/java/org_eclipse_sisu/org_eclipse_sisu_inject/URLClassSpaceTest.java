/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_sisu.org_eclipse_sisu_inject;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URL;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

import org.eclipse.sisu.space.URLClassSpace;
import org.junit.jupiter.api.Test;

public class URLClassSpaceTest {
    private static final String RESOURCE_NAME =
        "org_eclipse_sisu/org_eclipse_sisu_inject/url-class-space-resource.txt";

    @Test
    void loadClassDelegatesToBackingClassLoader() {
        URLClassSpace classSpace = new URLClassSpace(URLClassSpaceTest.class.getClassLoader());

        Class<?> loadedClass = classSpace.loadClass(URLClassSpace.class.getName());

        assertThat(loadedClass).isSameAs(URLClassSpace.class);
    }

    @Test
    void getResourceDelegatesToBackingClassLoader() {
        URLClassSpace classSpace = new URLClassSpace(URLClassSpaceTest.class.getClassLoader());

        URL resource = classSpace.getResource(RESOURCE_NAME);

        assertThat(resource).isNotNull();
        assertThat(resource.toString()).endsWith(RESOURCE_NAME);
    }

    @Test
    void getResourcesDelegatesToBackingClassLoader() {
        URLClassSpace classSpace = new URLClassSpace(URLClassSpaceTest.class.getClassLoader());

        Enumeration<URL> resourceEnumeration = classSpace.getResources(RESOURCE_NAME);
        List<URL> resources = Collections.list(resourceEnumeration);

        assertThat(resources).isNotEmpty();
        assertThat(resources).allSatisfy(resource -> assertThat(resource.toString()).endsWith(RESOURCE_NAME));
    }
}
