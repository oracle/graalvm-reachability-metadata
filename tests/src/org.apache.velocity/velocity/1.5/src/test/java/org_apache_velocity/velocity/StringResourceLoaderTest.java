/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_velocity.velocity;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.velocity.runtime.resource.loader.StringResourceLoader;
import org.apache.velocity.runtime.resource.util.StringResourceRepositoryImpl;
import org.junit.jupiter.api.Test;

public class StringResourceLoaderTest {
    @Test
    void exposesDefaultRepositoryImplementationClassName() {
        assertThat(StringResourceLoader.REPOSITORY_CLASS_DEFAULT)
                .isEqualTo(StringResourceRepositoryImpl.class.getName());
    }
}
