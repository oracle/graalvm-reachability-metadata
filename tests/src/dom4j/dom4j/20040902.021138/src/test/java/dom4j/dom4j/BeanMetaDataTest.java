/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package dom4j.dom4j;

import static org.assertj.core.api.Assertions.assertThat;

import org.dom4j.QName;
import org.dom4j.bean.BeanMetaData;
import org.dom4j.rule.Rule;
import org.junit.jupiter.api.Test;

public class BeanMetaDataTest {
    @Test
    void readsAndWritesBeanPropertiesThroughMetadata() {
        BeanMetaData metaData = BeanMetaData.get(Rule.class);
        Rule rule = new Rule();
        int modeIndex = metaData.getIndex("mode");

        metaData.setData(modeIndex, rule, "edit");
        Object mode = metaData.getData(modeIndex, rule);

        assertThat(modeIndex).isNotNegative();
        assertThat(mode).isEqualTo("edit");
        assertThat(rule.getMode()).isEqualTo("edit");
    }

    @Test
    void resolvesPropertyIndexByQNameBeforeAccessingBeanData() {
        BeanMetaData metaData = new BeanMetaData(Rule.class);
        Rule rule = new Rule();
        int priorityIndex = metaData.getIndex(QName.get("priority"));

        metaData.setData(priorityIndex, rule, Double.valueOf(4.5));
        Object priority = metaData.getData(priorityIndex, rule);

        assertThat(priorityIndex).isNotNegative();
        assertThat(priority).isEqualTo(Double.valueOf(4.5));
        assertThat(rule.getPriority()).isEqualTo(4.5);
    }
}
