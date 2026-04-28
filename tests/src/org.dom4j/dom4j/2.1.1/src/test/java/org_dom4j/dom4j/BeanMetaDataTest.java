/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_dom4j.dom4j;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;
import java.util.Map;

import org.dom4j.DocumentFactory;
import org.dom4j.bean.BeanMetaData;
import org.junit.jupiter.api.Test;

public class BeanMetaDataTest {
    @Test
    void readsAndWritesBeanPropertyData() {
        BeanMetaData metaData = BeanMetaData.get(DocumentFactory.class);
        int index = metaData.getIndex("XPathNamespaceURIs");
        DocumentFactory factory = new DocumentFactory();
        Map<String, String> namespaces = Collections.singletonMap("example",
                "urn:example");

        assertThat(index).isNotEqualTo(-1);

        metaData.setData(index, factory, namespaces);
        Object data = metaData.getData(index, factory);

        assertThat(data).isSameAs(namespaces);
        assertThat(metaData.getQName(index).getName()).isEqualTo("XPathNamespaceURIs");
    }
}
