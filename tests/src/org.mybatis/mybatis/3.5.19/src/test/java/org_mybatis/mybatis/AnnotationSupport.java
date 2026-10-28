/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mybatis.mybatis;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import org.apache.ibatis.javassist.bytecode.ConstPool;
import org.apache.ibatis.javassist.bytecode.annotation.Annotation;

public final class AnnotationSupport {
    private AnnotationSupport() {
    }

    public static Annotation annotation(Class<?> type, ConstPool pool) {
        return new Annotation(type.getName(), pool);
    }

    @Retention(RetentionPolicy.RUNTIME)
    public @interface Label {
        String value();
    }

    public @interface Names {
        String[] value();
    }

    public @interface Mode {
        RetentionPolicy value();
    }

    public @interface Type {
        Class<?> value();
    }

    @Label("worker")
    public static class Annotated {
    }
}
