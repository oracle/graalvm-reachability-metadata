/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_seata.seata_all;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.apache.seata.common.util.BeanUtils;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class BeanUtilsTest {
    @Test
    void beanToStringIncludesOnlyNonNullDeclaredFields() {
        String rendered = BeanUtils.beanToString(new PrintableBean("seata", 7, null));

        assertThat(rendered)
                .startsWith("[")
                .endsWith("]")
                .contains("name=seata")
                .contains("count=7")
                .doesNotContain("ignored");
    }

    @Test
    void mapToObjectPopulatesSupportedFieldTypesFromStringValues() {
        long timestamp = 1712345678901L;
        Map<String, String> source = new HashMap<>();
        source.put("createdAt", Long.toString(timestamp));
        source.put("id", "42");
        source.put("count", "7");
        source.put("amount", "19.5");
        source.put("name", "metadata-forge");

        ConvertibleBean bean = (ConvertibleBean) BeanUtils.mapToObject(source, ConvertibleBean.class);

        assertThat(bean.getCreatedAt()).isEqualTo(new Date(timestamp));
        assertThat(bean.getId()).isEqualTo(42L);
        assertThat(bean.getCount()).isEqualTo(7);
        assertThat(bean.getAmount()).isEqualTo(19.5D);
        assertThat(bean.getName()).isEqualTo("metadata-forge");
    }

    @Test
    void objectToMapSerializesDatesAndNullValues() {
        long timestamp = 1712345678901L;
        MappableBean bean = new MappableBean();
        bean.setCreatedAt(new Date(timestamp));
        bean.setName("seata");
        bean.setDescription(null);

        Map<String, String> map = BeanUtils.objectToMap(bean);

        assertThat(map)
                .containsEntry("createdAt", Long.toString(timestamp))
                .containsEntry("name", "seata")
                .containsEntry("description", "");
    }

    public static final class PrintableBean {
        private final String name;
        private final Integer count;
        private final String ignored;

        public PrintableBean(String name, Integer count, String ignored) {
            this.name = name;
            this.count = count;
            this.ignored = ignored;
        }
    }

    public static final class ConvertibleBean {
        private Date createdAt;
        private Long id;
        private Integer count;
        private Double amount;
        private String name;

        public ConvertibleBean() {
        }

        public Date getCreatedAt() {
            return createdAt;
        }

        public Long getId() {
            return id;
        }

        public Integer getCount() {
            return count;
        }

        public Double getAmount() {
            return amount;
        }

        public String getName() {
            return name;
        }
    }

    public static final class MappableBean {
        private Date createdAt;
        private String name;
        private String description;

        public Date getCreatedAt() {
            return createdAt;
        }

        public void setCreatedAt(Date createdAt) {
            this.createdAt = createdAt;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }
    }
}
