/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_rest_assured.rest_assured;

import java.lang.reflect.Method;
import java.util.List;

import io.restassured.builder.RequestSpecBuilder;
import io.restassured.config.HeaderConfig;
import io.restassured.config.RestAssuredConfig;
import io.restassured.specification.QueryableRequestSpecification;
import io.restassured.specification.RequestSpecification;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class SpecificationMergerInner_mergeConfig_closure1Test {
    private static final String CLOSURE_CLASS_NAME = "io.restassured.internal.SpecificationMerger"
            + "$_mergeConfig_closure1";
    private static final String CONFIGURED_HEADER = "X-Config-Merged";

    @Test
    void mergesUserConfiguredRequestConfigsBeforeMergingHeaders() throws Throwable {
        RestAssuredConfig preservingHeaderConfig = RestAssuredConfig.config()
                .headerConfig(HeaderConfig.headerConfig().mergeHeadersWithName(CONFIGURED_HEADER));
        RestAssuredConfig overwritingHeaderConfig = RestAssuredConfig.config()
                .headerConfig(HeaderConfig.headerConfig().overwriteHeadersWithName(CONFIGURED_HEADER));

        RequestSpecification otherSpecification = new RequestSpecBuilder()
                .setConfig(overwritingHeaderConfig)
                .addHeader(CONFIGURED_HEADER, "merged")
                .build();
        RequestSpecification mergedSpecification = new RequestSpecBuilder()
                .setConfig(preservingHeaderConfig)
                .addHeader(CONFIGURED_HEADER, "initial")
                .addRequestSpecification(otherSpecification)
                .build();

        QueryableRequestSpecification queryableSpecification =
                (QueryableRequestSpecification) mergedSpecification;
        assertEquals(List.of("merged"), queryableSpecification.getHeaders().getValues(CONFIGURED_HEADER));
        assertEquals(RestAssuredConfig.class, invokeGeneratedClassLookup(RestAssuredConfig.class.getName()));
    }

    private static Class<?> invokeGeneratedClassLookup(String className) throws Throwable {
        Class<?> closureClass = SpecificationMergerInner_mergeConfig_closure1Test.class
                .getClassLoader()
                .loadClass(CLOSURE_CLASS_NAME);
        Method classHelper = closureClass.getDeclaredMethod("class$", String.class);
        classHelper.setAccessible(true);
        return (Class<?>) classHelper.invoke(null, className);
    }
}
