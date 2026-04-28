/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_seata.seata_all;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.apache.seata.common.util.BeanUtils;
import org.junit.jupiter.api.Test;

public class BeanUtilsTest {
    @Test
    void mapConversionsAndStringRepresentationUseDeclaredBeanFields() {
        long createdTime = 1_700_000_000_000L;
        long modifiedTime = 1_700_000_123_456L;
        Map<String, String> values = new HashMap<>();
        values.put("created", String.valueOf(createdTime));
        values.put("modified", String.valueOf(modifiedTime));
        values.put("branchId", "42");
        values.put("retryCount", "7");
        values.put("ratio", "0.625");
        values.put("actionName", "commitAction");

        ConvertibleBean bean = (ConvertibleBean) BeanUtils.mapToObject(values, ConvertibleBean.class);

        assertThat(bean.getCreated()).isEqualTo(new Date(createdTime));
        assertThat(bean.getModified()).isEqualTo(new Date(modifiedTime));
        assertThat(bean.getBranchId()).isEqualTo(42L);
        assertThat(bean.getRetryCount()).isEqualTo(7);
        assertThat(bean.getRatio()).isEqualTo(0.625D);
        assertThat(bean.getActionName()).isEqualTo("commitAction");
        assertThat(bean.getNullableText()).isNull();

        Map<String, String> beanMap = BeanUtils.objectToMap(bean);

        assertThat(beanMap).containsEntry("created", String.valueOf(createdTime))
                .containsEntry("modified", String.valueOf(modifiedTime))
                .containsEntry("branchId", "42")
                .containsEntry("retryCount", "7")
                .containsEntry("ratio", "0.625")
                .containsEntry("actionName", "commitAction")
                .containsEntry("nullableText", "");

        String beanDescription = BeanUtils.beanToString(bean);

        assertThat(beanDescription).startsWith("[")
                .endsWith("]")
                .contains("branchId=42")
                .contains("retryCount=7")
                .contains("ratio=0.625")
                .contains("actionName=commitAction")
                .doesNotContain("nullableText=");
    }

    public static class ConvertibleBean {
        private Date created;
        private Date modified;
        private Long branchId;
        private Integer retryCount;
        private Double ratio;
        private String actionName;
        private String nullableText;

        public Date getCreated() {
            return created;
        }

        public Date getModified() {
            return modified;
        }

        public Long getBranchId() {
            return branchId;
        }

        public Integer getRetryCount() {
            return retryCount;
        }

        public Double getRatio() {
            return ratio;
        }

        public String getActionName() {
            return actionName;
        }

        public String getNullableText() {
            return nullableText;
        }
    }
}
