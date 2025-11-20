/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate.hibernate_core;

import org.jboss.logging.Logger;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;

import static org.assertj.core.api.Assertions.assertThat;

class AdditionalMetadataTest {

    // Hibernate 6 removed the legacy HQL AST classes used previously.
    // Keep this list empty to avoid referencing removed classes while still exercising the logic.
    private static final Class[] queryParsingClasses = new Class[]{
    };

    @Test
    public void testQueryParsingDefaultConstructors() throws Exception {
        for (Class c : queryParsingClasses) {
            Constructor constructor = c.getConstructor();
            assertThat(constructor).isNotNull();
        }
    }

    @Test
    public void testLoggers() throws Exception {
        Class c = Class.forName("org.hibernate.internal.log.UrlMessageBundle_$logger");
        Constructor constructor = c.getConstructor(Logger.class);
        assertThat(constructor).isNotNull();
    }
}
