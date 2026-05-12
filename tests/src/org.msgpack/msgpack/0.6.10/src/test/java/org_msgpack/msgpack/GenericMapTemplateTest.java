/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_msgpack.msgpack;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.msgpack.MessagePack;
import org.msgpack.template.GenericMapTemplate;
import org.msgpack.template.MapTemplate;
import org.msgpack.template.Template;
import org.msgpack.template.TemplateRegistry;
import org.msgpack.template.Templates;

public class GenericMapTemplateTest {
    @Test
    void buildsMapTemplateFromKeyAndValueTemplates() throws Exception {
        final TemplateRegistry registry = new TemplateRegistry(null);
        final GenericMapTemplate genericTemplate = new GenericMapTemplate(registry, MapTemplate.class);

        @SuppressWarnings("unchecked")
        final Template<Map<String, Integer>> mapTemplate = (Template<Map<String, Integer>>) (Template<?>) genericTemplate
                .build(new Template<?>[] {Templates.TString, Templates.TInteger });

        final Map<String, Integer> source = new LinkedHashMap<String, Integer>();
        source.put("alpha", 1);
        source.put("beta", 2);

        final MessagePack messagePack = new MessagePack();
        final byte[] packed = messagePack.write(source, mapTemplate);

        final Map<String, Integer> unpacked = messagePack.read(packed, mapTemplate);

        assertThat(unpacked).containsExactlyInAnyOrderEntriesOf(source);
    }
}
