/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io.restassured.internal;

import groovy.lang.Closure;
import org.codehaus.groovy.runtime.InvokerHelper;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public final class RestAssuredHttpBuilderGroovyHelperSupport {
    private RestAssuredHttpBuilderGroovyHelperSupport() {
    }

    public static Collection<String> flattenToString(Collection<?> collection) {
        List<String> result = new ArrayList<>();
        flattenInto(collection, result);
        return result;
    }

    public static Closure<?> createClosureThatCalls(Object assertionClosure) {
        return new Closure<Object>(RestAssuredHttpBuilderGroovyHelperSupport.class) {
            public Object doCall(Object response, Object content) {
                if (assertionClosure instanceof Closure<?> closure) {
                    return closure.call(response, content);
                }
                return InvokerHelper.invokeMethod(assertionClosure, "call", new Object[] {response, content});
            }
        };
    }

    private static void flattenInto(Object value, List<String> result) {
        if (value instanceof Collection<?> collection) {
            collection.forEach(item -> flattenInto(item, result));
        } else {
            result.add(value == null ? null : value.toString());
        }
    }
}
