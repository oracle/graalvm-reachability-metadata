/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_commons.commons_dbcp2;

import java.util.HashSet;
import java.util.Set;

public class TesterClassLoader extends ClassLoader {
    private final Set<String> loadedClasses = new HashSet<>();

    public boolean didLoad(final String className) {
        return loadedClasses.contains(className);
    }

    @Override
    protected synchronized Class<?> loadClass(final String name, final boolean resolve) throws ClassNotFoundException {
        final Class<?> clazz = super.loadClass(name, resolve);
        loadedClasses.add(name);
        return clazz;
    }
}
