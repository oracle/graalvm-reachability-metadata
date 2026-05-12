/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_msgpack.msgpack;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;

import org.junit.jupiter.api.Test;
import org.msgpack.MessagePack;
import org.msgpack.template.Template;
import org.msgpack.template.TemplateRegistry;
import org.msgpack.template.builder.ArrayTemplateBuilder;

public class ArrayTemplateBuilderTest {
    @Test
    void buildsTemplateForTwoDimensionalReferenceArray() throws IOException {
        final Template<String[][]> template = buildTemplate(String[][].class);
        final String[][] source = new String[][] {
                {"alpha", "beta" },
                {"gamma" },
                {"delta", "epsilon", "zeta" }
        };

        final String[][] unpacked = roundTrip(source, template);

        assertThat(unpacked).isDeepEqualTo(source);
    }

    @Test
    void buildsTemplateForThreeDimensionalReferenceArray() throws IOException {
        final Template<String[][][]> template = buildTemplate(String[][][].class);
        final String[][][] source = new String[][][] {
                {
                        {"alpha", "beta" },
                        {"gamma" }
                },
                {
                        {"delta" },
                        {"epsilon", "zeta" }
                }
        };

        final String[][][] unpacked = roundTrip(source, template);

        assertThat(unpacked).isDeepEqualTo(source);
    }

    @SuppressWarnings("unchecked")
    private static <T> Template<T> buildTemplate(final Class<T> arrayClass) {
        final TemplateRegistry registry = new TemplateRegistry(null);
        final ArrayTemplateBuilder builder = new ArrayTemplateBuilder(registry);
        return (Template<T>) builder.buildTemplate(arrayClass);
    }

    private static <T> T roundTrip(final T source, final Template<T> template) throws IOException {
        final MessagePack messagePack = new MessagePack();
        final byte[] packed = messagePack.write(source, template);
        return messagePack.read(packed, template);
    }
}
