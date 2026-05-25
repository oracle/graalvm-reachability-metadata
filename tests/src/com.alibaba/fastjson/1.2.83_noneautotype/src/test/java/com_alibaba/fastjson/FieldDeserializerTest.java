/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_alibaba.fastjson;

import static org.assertj.core.api.Assertions.assertThat;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.parser.ParserConfig;
import com.alibaba.fastjson.parser.deserializer.DefaultFieldDeserializer;
import com.alibaba.fastjson.util.FieldInfo;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

public class FieldDeserializerTest {
    @Test
    void parseObjectUpdatesGetterOnlyAtomicMapAndCollectionProperties() {
        GetterOnlyPropertiesBean bean = JSON.parseObject("""
                {
                  "intValue": 7,
                  "longValue": 9,
                  "enabled": true,
                  "attributes": {"answer": 42},
                  "tags": ["new"]
                }
                """, GetterOnlyPropertiesBean.class, noAsmConfig());

        assertThat(bean.getIntValue().get()).isEqualTo(7);
        assertThat(bean.getLongValue().get()).isEqualTo(9L);
        assertThat(bean.getEnabled().get()).isTrue();
        assertThat(bean.getAttributes()).containsEntry("answer", 42);
        assertThat(bean.getTags()).containsExactly("new");
    }

    @Test
    void parseObjectUpdatesFinalAtomicMapAndCollectionFields() {
        FinalFieldPropertiesBean bean = JSON.parseObject("""
                {
                  "intValue": 11,
                  "longValue": 13,
                  "enabled": true,
                  "attributes": {"size": 5},
                  "tags": ["final"]
                }
                """, FinalFieldPropertiesBean.class, noAsmConfig());

        assertThat(bean.intValue.get()).isEqualTo(11);
        assertThat(bean.longValue.get()).isEqualTo(13L);
        assertThat(bean.enabled.get()).isTrue();
        assertThat(bean.attributes).containsEntry("size", 5);
        assertThat(bean.tags).containsExactly("final");
    }

    @Test
    void parseObjectFallsBackToFieldAssignmentWhenGetterReturnsNull() {
        NullMapGetterBean bean = JSON.parseObject("""
                {
                  "settings": {"retries": 3}
                }
                """, NullMapGetterBean.class, noAsmConfig());

        assertThat(bean.getSettings()).containsEntry("retries", 3);
    }

    @Test
    void setValueFallsBackToMatchingSetterWhenGetterReturnsNullAndNoFieldIsAvailable() throws Exception {
        Method getter = SetterFallbackBean.class.getMethod("getCounter");
        FieldInfo fieldInfo = new FieldInfo("counter", getter, null, SetterFallbackBean.class,
                SetterFallbackBean.class, 0, 0, 0, null, null, null);
        DefaultFieldDeserializer fieldDeserializer = new DefaultFieldDeserializer(
                noAsmConfig(), SetterFallbackBean.class, fieldInfo);
        SetterFallbackBean bean = new SetterFallbackBean();

        fieldDeserializer.setValue(bean, new AtomicInteger(17));

        assertThat(bean.getCounter().get()).isEqualTo(17);
    }

    private static ParserConfig noAsmConfig() {
        ParserConfig config = new ParserConfig();
        config.setAsmEnable(false);
        return config;
    }

    public static class GetterOnlyPropertiesBean {
        private final AtomicInteger intValue = new AtomicInteger(1);
        private final AtomicLong longValue = new AtomicLong(2L);
        private final AtomicBoolean enabled = new AtomicBoolean(false);
        private final Map<String, Integer> attributes = new LinkedHashMap<>();
        private final List<String> tags = new ArrayList<>();

        public GetterOnlyPropertiesBean() {
            attributes.put("old", -1);
            tags.add("old");
        }

        public AtomicInteger getIntValue() {
            return intValue;
        }

        public AtomicLong getLongValue() {
            return longValue;
        }

        public AtomicBoolean getEnabled() {
            return enabled;
        }

        public Map<String, Integer> getAttributes() {
            return attributes;
        }

        public Collection<String> getTags() {
            return tags;
        }
    }

    public static class FinalFieldPropertiesBean {
        public final AtomicInteger intValue = new AtomicInteger(1);
        public final AtomicLong longValue = new AtomicLong(2L);
        public final AtomicBoolean enabled = new AtomicBoolean(false);
        public final Map<String, Integer> attributes = new LinkedHashMap<>();
        public final List<String> tags = new ArrayList<>();
    }

    public static class NullMapGetterBean {
        private Map<String, Integer> settings;

        public Map<String, Integer> getSettings() {
            return settings;
        }
    }

    public static class SetterFallbackBean {
        private AtomicInteger counter;

        public AtomicInteger getCounter() {
            return counter;
        }

        public void setCounter(AtomicInteger counter) {
            this.counter = counter;
        }
    }
}
