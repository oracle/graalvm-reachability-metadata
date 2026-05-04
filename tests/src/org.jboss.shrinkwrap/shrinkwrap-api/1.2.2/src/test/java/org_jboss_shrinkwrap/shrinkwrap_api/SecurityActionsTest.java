/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_shrinkwrap.shrinkwrap_api;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import org.junit.jupiter.api.Test;

public class SecurityActionsTest {
    @Test
    void stringBasedNewInstanceLoadsClassAndInvokesConstructor() throws Throwable {
        Class<?> securityActionsType = Class.forName("org.jboss.shrinkwrap.api.SecurityActions");
        MethodHandle newInstance = MethodHandles.privateLookupIn(securityActionsType, MethodHandles.lookup())
            .findStatic(securityActionsType, "newInstance", MethodType.methodType(Object.class, String.class,
                Class[].class, Object[].class, Class.class, ClassLoader.class));

        Object instance = newInstance.invoke(SecurityActionsTarget.class.getName(),
            new Class<?>[] {String.class, int.class }, new Object[] {"created", 2 }, SecurityActionsTarget.class,
            SecurityActionsTarget.class.getClassLoader());

        assertThat(instance).isInstanceOf(SecurityActionsTarget.class);
        SecurityActionsTarget target = (SecurityActionsTarget) instance;
        assertThat(target.name()).isEqualTo("created");
        assertThat(target.count()).isEqualTo(2);
    }

    public static final class SecurityActionsTarget {
        private final String name;
        private final int count;

        public SecurityActionsTarget(String name, int count) {
            this.name = name;
            this.count = count;
        }

        String name() {
            return name;
        }

        int count() {
            return count;
        }
    }
}
