/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.jboss.arquillian.core.spi;

import java.util.Collection;
import java.util.Collections;

import org.jboss.arquillian.core.spi.context.Context;

public final class SecurityActionsInvoker {
    private static final String MANAGER_IMPL_CLASS = "org.jboss.arquillian.core.impl.ManagerImpl";

    private SecurityActionsInvoker() {
    }

    public static Manager createManagerWithClassLoader(ClassLoader classLoader) {
        Collection<Class<? extends Context>> contexts = Collections.emptySet();
        Collection<Class<?>> extensions = Collections.emptySet();
        return SecurityActions.newInstance(
                MANAGER_IMPL_CLASS,
                new Class<?>[] {Collection.class, Collection.class},
                new Object[] {contexts, extensions},
                Manager.class,
                classLoader);
    }
}
