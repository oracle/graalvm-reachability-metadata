/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_alibaba.fastjson;

import static org.assertj.core.api.Assertions.assertThat;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.annotation.JSONCreator;
import com.alibaba.fastjson.annotation.JSONField;
import com.alibaba.fastjson.annotation.JSONPOJOBuilder;
import com.alibaba.fastjson.annotation.JSONType;
import com.alibaba.fastjson.parser.ParserConfig;
import com.alibaba.fastjson.util.JavaBeanInfo;
import java.lang.reflect.Constructor;
import java.util.Collections;
import kotlin.KotlinNullPointerException;
import kotlin.Pair;
import org.junit.jupiter.api.Test;

public class JavaBeanInfoTest {
    @Test
    void buildUsesMixinCreatorConstructorWhenTargetHasNoDefaultConstructor() {
        ParserConfig config = noAsmConfig();
        JSON.addMixInAnnotations(MixinConstructedBean.class, MixinConstructedBeanMixin.class);
        try {
            MixinConstructedBean bean = JSON.parseObject("{\"name\":\"mixed\"}", MixinConstructedBean.class, config);

            assertThat(bean.name).isEqualTo("mixed");
        } finally {
            JSON.removeMixInAnnotations(MixinConstructedBean.class);
        }
    }

    @Test
    void parseObjectWithFieldBasedConfigCollectsDeclaredFieldsFromClassHierarchy() {
        ParserConfig config = new ParserConfig(true);

        FieldBasedChildBean bean = JSON.parseObject(
                "{\"baseName\":\"base\",\"childValue\":42}", FieldBasedChildBean.class, config);

        assertThat(bean.baseName).isEqualTo("base");
        assertThat(bean.childValue).isEqualTo(42);
    }

    @Test
    void builderDeserializationFallsBackToCreateBuildMethod() {
        ParserConfig config = noAsmConfig();

        CreateOnlyBuilderBean bean = JSON.parseObject("{\"name\":\"created\"}", CreateOnlyBuilderBean.class, config);

        assertThat(bean.name).isEqualTo("created");
    }

    @Test
    void buildRecognizesKotlinConstructorMetadata() {
        JavaBeanInfo beanInfo = JavaBeanInfo.build(Pair.class, Pair.class, null);

        assertThat(beanInfo.kotlin).isTrue();
        assertThat(beanInfo.creatorConstructor).isNotNull();
        assertThat(beanInfo.creatorConstructorParameters).contains("first", "second");
    }

    @Test
    void constructorStoresKotlinDefaultConstructorWhenCreatorConstructorIsPresent() throws NoSuchMethodException {
        Constructor<KotlinNullPointerException> defaultConstructor = KotlinNullPointerException.class.getConstructor();
        Constructor<KotlinNullPointerException> creatorConstructor = KotlinNullPointerException.class.getConstructor(
                String.class);

        JavaBeanInfo beanInfo = new JavaBeanInfo(KotlinNullPointerException.class, null, defaultConstructor,
                creatorConstructor, null, null, null, Collections.emptyList());

        assertThat(beanInfo.kotlin).isTrue();
        assertThat(beanInfo.kotlinDefaultConstructor).isEqualTo(defaultConstructor);
    }

    private static ParserConfig noAsmConfig() {
        ParserConfig config = new ParserConfig();
        config.setAsmEnable(false);
        return config;
    }

    public static class MixinConstructedBean {
        public final String name;

        public MixinConstructedBean(@JSONField(name = "name") String name) {
            this.name = name;
        }
    }

    public static class MixinConstructedBeanMixin {
        @JSONCreator
        public MixinConstructedBeanMixin(String name) {
        }
    }

    public static class FieldBasedBaseBean {
        public String baseName;
    }

    public static class FieldBasedChildBean extends FieldBasedBaseBean {
        public int childValue;
    }

    @JSONType(builder = CreateOnlyBuilderBean.Builder.class)
    public static class CreateOnlyBuilderBean {
        public final String name;

        private CreateOnlyBuilderBean(String name) {
            this.name = name;
        }

        @JSONPOJOBuilder
        public static class Builder {
            private String name;

            public Builder withName(String name) {
                this.name = name;
                return this;
            }

            public CreateOnlyBuilderBean create() {
                return new CreateOnlyBuilderBean(name);
            }
        }
    }
}
