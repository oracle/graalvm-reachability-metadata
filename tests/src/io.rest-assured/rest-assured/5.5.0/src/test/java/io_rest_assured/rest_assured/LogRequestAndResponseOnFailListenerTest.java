/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_rest_assured.rest_assured;

import io.restassured.config.LogConfig;
import io.restassured.config.RestAssuredConfig;
import io.restassured.internal.LogRequestAndResponseOnFailListener;
import io.restassured.internal.ResponseParserRegistrar;
import io.restassured.internal.ResponseSpecificationImpl;
import io.restassured.internal.log.LogRepository;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

public class LogRequestAndResponseOnFailListenerTest {
    @Test
    void writesBufferedRequestAndResponseLogsToConfiguredStreamOnFailure() {
        ByteArrayOutputStream validationFailureLog = new ByteArrayOutputStream();
        PrintStream logStream = new PrintStream(validationFailureLog, true, StandardCharsets.UTF_8);
        LogRepository logRepository = new LogRepository();
        registerLog(logRepository::registerRequestLog, "request log");
        registerLog(logRepository::registerResponseLog, "response log");
        ResponseSpecificationImpl responseSpecification = new ResponseSpecificationImpl(
                "",
                null,
                new ResponseParserRegistrar(),
                RestAssuredConfig.config().logConfig(LogConfig.logConfig().defaultStream(logStream)),
                logRepository);

        new LogRequestAndResponseOnFailListener().onFailure(null, responseSpecification, null);

        logStream.flush();
        assertThat(validationFailureLog.toString(StandardCharsets.UTF_8))
                .contains("request log")
                .contains("response log");
    }

    @Test
    void resolvesClassesThroughGroovyGeneratedClassHelper() throws Exception {
        MethodHandle classHelper = MethodHandles.privateLookupIn(
                        LogRequestAndResponseOnFailListener.class,
                        MethodHandles.lookup())
                .findStatic(
                        LogRequestAndResponseOnFailListener.class,
                        "class$",
                        MethodType.methodType(Class.class, String.class));

        Class<?> resolvedClass = invokeClassHelper(classHelper, responseSpecificationImplTypeNameSelectedAtRuntime());

        assertThat(resolvedClass).isSameAs(ResponseSpecificationImpl.class);
    }

    private static Class<?> invokeClassHelper(MethodHandle classHelper, String typeName) throws Exception {
        try {
            return (Class<?>) classHelper.invoke(typeName);
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Throwable e) {
            throw new AssertionError(e);
        }
    }

    private static String responseSpecificationImplTypeNameSelectedAtRuntime() {
        String[] nameParts = new String[] {"io.restassured.internal.", "ResponseSpecificationImpl" };
        return nameParts[0] + nameParts[1];
    }

    private static void registerLog(LogRegistration logRegistration, String value) {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        buffer.writeBytes(value.getBytes(StandardCharsets.UTF_8));
        logRegistration.register(buffer);
    }

    private interface LogRegistration {
        void register(ByteArrayOutputStream buffer);
    }
}
