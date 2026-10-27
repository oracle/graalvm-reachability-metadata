/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate.hibernate_core;

import org.hibernate.boot.xsd.LocalXsdResolver;
import org.junit.jupiter.api.Test;

import java.net.URL;

import static org.assertj.core.api.Assertions.assertThat;

public class LocalXsdResolverTest {

    @Test
    public void locatesHibernateSchemasFromTheLibraryJar() {
        URL schema = LocalXsdResolver.resolveLocalXsdUrl(
                "org/hibernate/xsd/cfg/legacy-configuration-4.0.xsd"
        );

        assertThat(schema).isNotNull();
    }
}
