/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_rest_assured.rest_assured;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import io.restassured.internal.PreemptiveAuthSpecImpl;
import io.restassured.specification.RequestSpecification;
import io.restassured.specification.SpecificationQuerier;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

public class PreemptiveAuthSpecImplTest {
    private static final String USER_NAME = "preemptive-user";
    private static final String PASSWORD = "preemptive-password";
    private static final String ACCESS_TOKEN = "preemptive-token";
    private static final String BASIC_AUTHORIZATION_HEADER = "Basic " + Base64.getEncoder()
            .encodeToString((USER_NAME + ":" + PASSWORD).getBytes(StandardCharsets.ISO_8859_1));

    @Test
    void preemptiveBasicAuthenticationAddsAuthorizationHeaderDirectly() {
        RequestSpecification specification = given()
                .auth()
                .preemptive()
                .basic(USER_NAME, PASSWORD);

        String authorization = SpecificationQuerier.query(specification)
                .getHeaders()
                .getValue("Authorization");
        assertThat(authorization).isEqualTo(BASIC_AUTHORIZATION_HEADER);
    }

    @Test
    void preemptiveOAuth2AuthenticationAddsAuthorizationHeaderDirectly() {
        RequestSpecification specification = given()
                .auth()
                .preemptive()
                .oauth2(ACCESS_TOKEN);

        String authorization = SpecificationQuerier.query(specification)
                .getHeaders()
                .getValue("Authorization");
        assertThat(authorization).isEqualTo("Bearer " + ACCESS_TOKEN);
    }

    @Test
    void resolvesRequestSpecificationUsingGeneratedGroovyClassHelper() throws Throwable {
        MethodHandle classHelper = generatedClassLookup();
        String requestSpecificationClassName = RequestSpecification.class.getName();

        Class<?> result = invokeGeneratedClassLookup(classHelper, requestSpecificationClassName);

        assertThat(result).isSameAs(RequestSpecification.class);
    }

    private static MethodHandle generatedClassLookup() throws NoSuchMethodException, IllegalAccessException {
        MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(
                PreemptiveAuthSpecImpl.class,
                MethodHandles.lookup());
        return lookup.findStatic(
                PreemptiveAuthSpecImpl.class,
                "class$",
                MethodType.methodType(Class.class, String.class));
    }

    private static Class<?> invokeGeneratedClassLookup(MethodHandle classHelper, String className) throws Throwable {
        return (Class<?>) classHelper.invokeExact(className);
    }
}
