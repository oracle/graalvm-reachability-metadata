/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_alibaba.fastjson;

import static org.assertj.core.api.Assertions.assertThat;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import org.junit.jupiter.api.Test;

public class AnnotationSerializerTest {
    @Test
    void runtimeAnnotationProxySerializesDeclaredMembers() {
        Class<AnnotatedTarget> annotatedTargetAnnotationAccess = AnnotatedTarget.class;
        SerializedAnnotation annotation = annotatedTargetAnnotationAccess.getAnnotation(SerializedAnnotation.class);

        String json = JSON.toJSONString(annotation);
        JSONObject object = JSON.parseObject(json);

        assertThat(object.getString("name")).isEqualTo("fastjson");
        assertThat(object.getInteger("priority")).isEqualTo(83);
        assertThat(object.getString("status")).isEqualTo("ACTIVE");
        assertThat(object.getJSONArray("tags")).containsExactly("annotation", "serializer");
    }

    @SerializedAnnotation(
            name = "fastjson",
            priority = 83,
            status = Status.ACTIVE,
            tags = {"annotation", "serializer"})
    private static class AnnotatedTarget {
    }

    @Retention(RetentionPolicy.RUNTIME)
    public @interface SerializedAnnotation {
        String name();

        int priority();

        Status status();

        String[] tags();
    }

    public enum Status {
        ACTIVE,
        INACTIVE
    }
}
