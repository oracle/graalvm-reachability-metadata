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

import javax.jdo.Constants;
import javax.jdo.JDOFatalInternalException;
import javax.jdo.JDOHelper;
import javax.jdo.PersistenceManagerFactory;

import org.junit.jupiter.api.Test;

public class JDOHelperAnonymous15Test {
    @Test
    void looksUpPersistenceManagerFactoryMethodOnConfiguredImplementationClass() {
        Map<String, Object> properties = new HashMap<>();
        properties.put(
                Constants.PROPERTY_PERSISTENCE_MANAGER_FACTORY_CLASS,
                NullReturningPersistenceManagerFactoryProvider.class.getName());
        NullReturningPersistenceManagerFactoryProvider.reset();

        assertThatThrownBy(
                () -> JDOHelper.getPersistenceManagerFactory(properties, getClass().getClassLoader()))
                .isInstanceOf(JDOFatalInternalException.class);

        assertThat(NullReturningPersistenceManagerFactoryProvider.wasInvoked()).isTrue();
    }

    public static final class NullReturningPersistenceManagerFactoryProvider {
        private static boolean invoked;

        private NullReturningPersistenceManagerFactoryProvider() {
        }

        public static PersistenceManagerFactory getPersistenceManagerFactory(Map<?, ?> properties) {
            invoked = true;
            return null;
        }

        private static void reset() {
            invoked = false;
        }

        private static boolean wasInvoked() {
            return invoked;
        }
    }
}
