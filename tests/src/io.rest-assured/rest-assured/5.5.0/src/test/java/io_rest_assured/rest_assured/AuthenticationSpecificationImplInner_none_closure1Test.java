/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_rest_assured.rest_assured;

import groovy.lang.Closure;
import groovy.lang.GroovyObject;
import groovy.lang.GroovyRuntimeException;
import groovy.lang.MetaClass;
import io.restassured.RestAssured;
import io.restassured.authentication.AuthenticationScheme;
import io.restassured.authentication.ExplicitNoAuthScheme;
import io.restassured.filter.Filter;
import io.restassured.internal.AuthenticationSpecificationImpl;
import io.restassured.specification.FilterableRequestSpecification;
import io.restassured.specification.RequestSpecification;
import io.restassured.specification.SpecificationQuerier;
import io.restassured.spi.AuthFilter;
import org.codehaus.groovy.runtime.InvokerHelper;
import org.junit.jupiter.api.Test;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

public class AuthenticationSpecificationImplInner_none_closure1Test {
    @Test
    void compilerGeneratedClosureClassResolverResolvesAuthFilter() throws Throwable {
        CapturingFilterList filters = new CapturingFilterList();
        filters.add((AuthFilter) (requestSpec, responseSpec, context) -> context.next(requestSpec, responseSpec));
        RequestSpecification requestSpecification = groovyRequestSpecificationWith(filters);
        AuthenticationSpecificationImpl authenticationSpecification = new AuthenticationSpecificationImpl(
                requestSpecification);

        authenticationSpecification.none();

        Closure<?> capturedClosure = filters.getCapturedClosure();
        assertNotNull(capturedClosure);
        Object resolvedClass = invokeCompilerGeneratedClassResolver(capturedClosure.getClass());
        assertSame(AuthFilter.class, resolvedClass);
    }

    @Test
    void noneRemovesOnlyAuthenticationFiltersFromRequestSpecification() {
        AuthFilter authenticationFilter = (requestSpec, responseSpec, context) -> context.next(
                requestSpec,
                responseSpec);
        Filter ordinaryFilter = (requestSpec, responseSpec, context) -> context.next(requestSpec, responseSpec);
        RequestSpecification requestSpecification = RestAssured.given()
                .filter(authenticationFilter)
                .filter(ordinaryFilter);

        RequestSpecification configuredSpecification = requestSpecification.auth().none();

        assertSame(requestSpecification, configuredSpecification);
        List<Filter> remainingFilters = SpecificationQuerier.query(configuredSpecification).getDefinedFilters();
        assertEquals(1, remainingFilters.size());
        assertSame(ordinaryFilter, remainingFilters.get(0));
        AuthenticationScheme authenticationScheme = SpecificationQuerier.query(configuredSpecification)
                .getAuthenticationScheme();
        assertInstanceOf(ExplicitNoAuthScheme.class, authenticationScheme);
    }

    private static Object invokeCompilerGeneratedClassResolver(Class<?> closureClass) throws Throwable {
        try {
            MetaClass metaClass = InvokerHelper.getMetaClass(closureClass);
            return metaClass.invokeStaticMethod(
                    closureClass,
                    "class$",
                    new Object[] {"io.restassured.spi.AuthFilter"});
        } catch (GroovyRuntimeException groovyDispatchFailure) {
            try {
                MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(closureClass, MethodHandles.lookup());
                MethodHandle classResolver = lookup.findStatic(
                        closureClass,
                        "class$",
                        MethodType.methodType(Class.class, String.class));
                return classResolver.invokeWithArguments("io.restassured.spi.AuthFilter");
            } catch (Throwable fallbackFailure) {
                fallbackFailure.addSuppressed(groovyDispatchFailure);
                throw fallbackFailure;
            }
        }
    }

    private static RequestSpecification groovyRequestSpecificationWith(CapturingFilterList filters) {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("filters", filters);
        AtomicReference<RequestSpecification> self = new AtomicReference<>();
        InvocationHandler handler = (proxy, method, arguments) -> {
            String methodName = method.getName();
            if ("getProperty".equals(methodName)) {
                return properties.get((String) arguments[0]);
            }
            if ("setProperty".equals(methodName)) {
                properties.put((String) arguments[0], arguments[1]);
                return null;
            }
            if ("getMetaClass".equals(methodName)) {
                return InvokerHelper.getMetaClass(proxy.getClass());
            }
            if ("setMetaClass".equals(methodName) || "invokeMethod".equals(methodName)) {
                return null;
            }
            if ("removeHeader".equals(methodName)) {
                properties.put("removedHeader", arguments[0]);
                return self.get();
            }
            if ("toString".equals(methodName)) {
                return "capturingRequestSpecification";
            }
            return defaultValue(method.getReturnType());
        };
        RequestSpecification proxy = (RequestSpecification) Proxy.newProxyInstance(
                AuthenticationSpecificationImplInner_none_closure1Test.class.getClassLoader(),
                new Class<?>[] {FilterableRequestSpecification.class, GroovyObject.class},
                handler);
        self.set(proxy);
        return proxy;
    }

    private static Object defaultValue(Class<?> returnType) {
        if (!returnType.isPrimitive()) {
            return null;
        }
        if (boolean.class.equals(returnType)) {
            return false;
        }
        if (char.class.equals(returnType)) {
            return '\0';
        }
        return 0;
    }

    public static final class CapturingFilterList extends ArrayList<Filter> {
        private transient Closure<?> capturedClosure;

        @SuppressWarnings("unused")
        public boolean removeAll(Closure<?> predicate) {
            this.capturedClosure = predicate;
            boolean changed = false;
            Iterator<Filter> iterator = iterator();
            while (iterator.hasNext()) {
                Filter filter = iterator.next();
                if (Boolean.TRUE.equals(predicate.call(filter))) {
                    iterator.remove();
                    changed = true;
                }
            }
            return changed;
        }

        private Closure<?> getCapturedClosure() {
            return capturedClosure;
        }
    }
}
