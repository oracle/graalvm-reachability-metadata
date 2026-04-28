/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_dom4j.dom4j;

import static org.assertj.core.api.Assertions.assertThat;

import org.dom4j.DocumentFactory;
import org.dom4j.QName;
import org.dom4j.bean.BeanMetaData;
import org.junit.jupiter.api.Test;

public class BeanMetaDataTest {
    @Test
    void readsAndWritesBeanPropertyData() {
        BeanMetaData metaData = BeanMetaData.get(QName.class);
        int index = metaData.getIndex("documentFactory");
        QName qName = QName.get("chapter");
        DocumentFactory factory = new DocumentFactory();

        assertThat(index).isNotEqualTo(-1);

        metaData.setData(index, qName, factory);
        Object data = metaData.getData(index, qName);

        assertThat(data).isSameAs(factory);
        assertThat(metaData.getQName(index).getName()).isEqualTo("documentFactory");
    }
}
