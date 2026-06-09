/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_rest_assured.rest_assured;

import groovy.lang.Writable;
import io.restassured.internal.RestAssuredResponseOptionsGroovyImpl;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class RestAssuredResponseOptionsGroovyImplInner_toString_closure2Test {
    @Test
    void rendersWritableContentWithStreamingMarkupBuilderClosure() {
        RestAssuredResponseOptionsGroovyImpl responseOptions = new RestAssuredResponseOptionsGroovyImpl();
        Writable node = writer -> {
            writer.write("<message>hello</message>");
            return writer;
        };
        Object rendered = responseOptions.toString(node);

        assertThat(rendered).asString().contains("hello");
    }
}
