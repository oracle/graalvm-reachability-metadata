/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_xerial.sqlite_jdbc;

import java.net.URL;

import org.junit.jupiter.api.Test;
import org.sqlite.util.ResourceFinder;

import static org.assertj.core.api.Assertions.assertThat;

public class ResourceFinderTest {
    private static final String MISSING_RESOURCE = "resource_finder_missing_resource.txt";

    @Test
    public void returnsNullWhenReferenceClassPackageResourceIsAbsent() {
        URL resource = ResourceFinder.find(ResourceFinderTest.class, MISSING_RESOURCE);

        assertThat(resource).isNull();
    }

    @Test
    public void returnsNullWhenExplicitPackageResourceIsAbsent() {
        ClassLoader classLoader = ResourceFinderTest.class.getClassLoader();
        Package basePackage = ResourceFinderTest.class.getPackage();

        URL resource = ResourceFinder.find(classLoader, basePackage, MISSING_RESOURCE);

        assertThat(resource).isNull();
    }
}
