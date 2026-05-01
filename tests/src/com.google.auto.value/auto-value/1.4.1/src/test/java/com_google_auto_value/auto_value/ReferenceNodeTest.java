/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_auto_value.auto_value;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.auto.value.processor.escapevelocity.Template;
import java.io.StringReader;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

public class ReferenceNodeTest {
    @Test
    void methodReferenceUsesPublicAncestorForNonPublicJdkCollection() throws Exception {
        Template template = Template.parseFrom(new StringReader("$list.size()"));
        List<String> list = Collections.singletonList("value");

        String evaluated = template.evaluate(Collections.singletonMap("list", list));

        assertEquals("1", evaluated);
    }
}
