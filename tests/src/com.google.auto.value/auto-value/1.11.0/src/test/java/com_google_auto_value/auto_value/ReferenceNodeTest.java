/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_auto_value.auto_value;

import static org.assertj.core.api.Assertions.assertThat;

import autovalue.shaded.com.google.escapevelocity.Template;
import java.io.StringReader;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class ReferenceNodeTest {
    @Test
    void invokesMethodWithTemplateArguments() throws Exception {
        Template template = Template.parseFrom(new StringReader("$words.join()"));

        String output = template.evaluate(Map.of("words", new Words()));

        assertThat(output).isEqualTo("native-image");
    }

    public static final class Words {
        public String join() {
            return "native-image";
        }
    }
}
