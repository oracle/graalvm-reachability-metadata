/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_univocity.univocity_parsers;

import com.univocity.parsers.annotations.Copy;
import com.univocity.parsers.annotations.helpers.AnnotationHelper;
import com.univocity.parsers.annotations.helpers.AnnotationRegistry;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;

public class AnnotationRegistryInnerAnnotationAttributesTest {
    @AfterEach
    void resetRegistry() {
        AnnotationRegistry.reset();
    }

    @Test
    void preservesFirstSingleCopiedValueWhenLaterCopiedArrayUsesSameAttribute() throws NoSuchFieldException {
        Field field = AliasedBean.class.getDeclaredField("code");

        TargetNames targetNames = AnnotationHelper.findAnnotation(field, TargetNames.class);
        assertThat(targetNames).isNotNull();

        String[] names = AnnotationRegistry.getValue(field, targetNames, "names", new String[0]);
        assertThat(names).containsExactly("primary");
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.ANNOTATION_TYPE)
    public @interface TargetNames {
        String names() default "";
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.FIELD, ElementType.ANNOTATION_TYPE})
    @ArrayNameAlias({"secondary", "backup"})
    public @interface SingleNameAlias {
        @Copy(to = TargetNames.class, property = "names")
        String value();
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.ANNOTATION_TYPE)
    @TargetNames
    public @interface ArrayNameAlias {
        @Copy(to = TargetNames.class, property = "names")
        String[] value();
    }

    public static class AliasedBean {
        @SingleNameAlias("primary")
        private String code;
    }
}
