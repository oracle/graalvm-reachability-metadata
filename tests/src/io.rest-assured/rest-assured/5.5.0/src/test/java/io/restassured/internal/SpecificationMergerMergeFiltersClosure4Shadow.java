/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io.restassured.internal;

import java.util.Collection;

import groovy.lang.Closure;
import groovy.lang.Reference;

// CheckStyle: start generated
class SpecificationMerger$_mergeFilters_closure4 extends Closure<Object> {
    private final Reference<Collection<?>> thisFilters;

    SpecificationMerger$_mergeFilters_closure4(
            Object owner,
            Object thisObject,
            Reference<Collection<?>> thisFilters) {
        super(owner, thisObject);
        this.thisFilters = thisFilters;
    }

    public Object doCall(Object filter) {
        Class<?> objectClass = class$("java.lang.Object");
        if (!objectClass.isInstance(filter)) {
            throw new IllegalArgumentException(String.valueOf(filter));
        }
        return !thisFilters.get().contains(filter);
    }

    static Class<?> class$(String className) {
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException exception) {
            throw new NoClassDefFoundError(exception.getMessage());
        }
    }
}
// CheckStyle: stop generated
