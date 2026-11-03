/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_beust.jcommander;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises JCommander's fixed-arity option subsystem for a client request CLI.
public class FixedArityOptionCommandLineTest {
    @Test
    public void collectsRepeatedNameValueOptionsForAnOutgoingRequest() {
        RequestOptions options = new RequestOptions();
        JCommander commander = JCommander.newBuilder()
                .programName("request-tool")
                .addObject(options)
                .build();

        commander.parse(
                "--url", "https://api.example.test/orders",
                "--header", "Accept", "application/json",
                "--header", "X-Request-Id", "request-42");

        assertEquals("https://api.example.test/orders", options.url);
        assertEquals(
                Map.of("Accept", "application/json", "X-Request-Id", "request-42"),
                options.headersByName());
    }

    @Parameters(commandDescription = "Send a request with custom headers")
    public static class RequestOptions {
        @Parameter(names = "--url", description = "request URL", required = true)
        private String url;

        @Parameter(
                names = "--header",
                description = "header name followed by its value",
                arity = 2,
                required = true)
        private List<String> headers = new ArrayList<>();

        private Map<String, String> headersByName() {
            Map<String, String> result = new LinkedHashMap<>();
            for (int index = 0; index < headers.size(); index += 2) {
                result.put(headers.get(index), headers.get(index + 1));
            }
            return result;
        }
    }
}
