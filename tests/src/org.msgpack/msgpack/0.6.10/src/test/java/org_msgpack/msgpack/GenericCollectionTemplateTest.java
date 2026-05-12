/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_msgpack.msgpack;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.msgpack.MessagePack;
import org.msgpack.template.GenericCollectionTemplate;
import org.msgpack.template.ListTemplate;
import org.msgpack.template.Template;
import org.msgpack.template.TemplateRegistry;
import org.msgpack.template.Templates;

public class GenericCollectionTemplateTest {
    @Test
    void buildsCollectionTemplateFromElementTemplate() throws Exception {
        final TemplateRegistry registry = new TemplateRegistry(null);
        final GenericCollectionTemplate genericTemplate = new GenericCollectionTemplate(registry, ListTemplate.class);

        @SuppressWarnings("unchecked")
        final Template<List<String>> listTemplate =
                (Template<List<String>>) (Template<?>) genericTemplate.build(new Template<?>[] { Templates.TString });

        final MessagePack messagePack = new MessagePack();
        final List<String> source = Arrays.asList("alpha", "beta", "gamma");
        final byte[] packed = messagePack.write(source, listTemplate);

        final List<String> unpacked = messagePack.read(packed, listTemplate);

        assertThat(unpacked).containsExactly("alpha", "beta", "gamma");
    }
}
