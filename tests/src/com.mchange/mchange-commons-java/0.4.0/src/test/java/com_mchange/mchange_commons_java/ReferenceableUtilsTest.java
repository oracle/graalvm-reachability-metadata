/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_mchange.mchange_commons_java;

import com.mchange.v2.naming.ReferenceableUtils;
import com.mchange.v2.naming.SecurityConfigKey;
import org.junit.jupiter.api.Test;

import javax.naming.Context;
import javax.naming.Name;
import javax.naming.Reference;
import javax.naming.StringRefAddr;
import javax.naming.spi.ObjectFactory;
import java.util.Hashtable;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

public class ReferenceableUtilsTest {
    @Test
    void assertAcceptableNameAllowsLocalJndiNamesWithDefaultGuard() {
        String nameGuardProperty = SecurityConfigKey.NAME_GUARD_CLASS_NAME;
        String previousNameGuardClassName = System.getProperty(nameGuardProperty);
        try {
            System.clearProperty(nameGuardProperty);

            assertThatCode(
                () -> ReferenceableUtils.assertAcceptableName("java:comp/env/jdbc/example", null)
            ).doesNotThrowAnyException();
        } finally {
            if (previousNameGuardClassName == null) {
                System.clearProperty(nameGuardProperty);
            } else {
                System.setProperty(nameGuardProperty, previousNameGuardClassName);
            }
        }
    }

    @Test
    void referenceToObjectInstantiatesConfiguredFactoryAndDelegatesReferenceResolution() throws Exception {
        Reference reference = new Reference(String.class.getName(), CapturingObjectFactory.class.getName(), null);
        reference.add(new StringRefAddr("value", "alpha"));
        Hashtable<String, Object> environment = new
            Hashtable<>();
        environment.put("prefix", "resolved");

        Set<String> allowedFactoryClassNames = Set.of(CapturingObjectFactory.class.getName());

        Object resolved = ReferenceableUtils.referenceToObject(
            reference,
            null,
            null,
            environment,
            allowedFactoryClassNames
        );

        assertThat(resolved).isEqualTo("resolved:alpha");
    }

    public static class CapturingObjectFactory implements ObjectFactory {
        public CapturingObjectFactory() {
        }

        @Override
        public Object getObjectInstance(Object obj, Name name, Context nameCtx, Hashtable<?, ?> environment) {
            Reference reference = (Reference) obj;
            String prefix = (String) environment.get("prefix");
            String value = (String) reference.get("value").getContent();
            return prefix + ":" + value;
        }
    }
}
