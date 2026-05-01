/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_datanucleus.javax_jdo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.Map;

import javax.jdo.JDOFatalInternalException;
import javax.jdo.JDOHelper;
import javax.jdo.PersistenceManagerFactory;

import org.junit.jupiter.api.Test;

public class JDOHelperAnonymous15Test {

    @Test
    void getPersistenceManagerFactoryLooksUpImplementationFactoryMethod() {
        PmfImplementation.invocationCount = 0;
        Map<String, String> properties = new HashMap<>();
        properties.put(
                JDOHelper.PROPERTY_PERSISTENCE_MANAGER_FACTORY_CLASS,
                PmfImplementation.class.getName()
        );

        assertThatThrownBy(() -> JDOHelper.getPersistenceManagerFactory(properties, getClass().getClassLoader()))
                .isInstanceOf(JDOFatalInternalException.class)
                .hasMessageContaining(PmfImplementation.class.getName());

        assertThat(PmfImplementation.invocationCount).isEqualTo(1);
    }

    public static final class PmfImplementation {

        private static int invocationCount;

        private PmfImplementation() {
        }

        public static PersistenceManagerFactory getPersistenceManagerFactory(Map<?, ?> properties) {
            assertThat(properties.get(JDOHelper.PROPERTY_PERSISTENCE_MANAGER_FACTORY_CLASS))
                    .isEqualTo(PmfImplementation.class.getName());
            invocationCount++;
            return null;
        }
    }
}
