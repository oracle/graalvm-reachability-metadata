/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_hadoop.hadoop_common;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

import org.apache.hadoop.conf.Configurable;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.mapred.JobConf;
import org.apache.hadoop.mapred.JobConfigurable;
import org.apache.hadoop.util.ReflectionUtils;
import org.junit.jupiter.api.Test;

public class ReflectionUtilsTest {
    @Test
    void newInstanceUsesDeclaredConstructorAndAppliesConfiguration() {
        Configuration conf = new Configuration(false);

        PrivateConstructible result = ReflectionUtils.newInstance(PrivateConstructible.class, conf);

        assertThat(result.getConf()).isSameAs(conf);
    }

    @Test
    void setConfInvokesJobConfigurableForJobConf() {
        JobConf conf = new JobConf(false);
        JobConfiguredObject object = new JobConfiguredObject();

        ReflectionUtils.setConf(object, conf);

        assertThat(object.getConfiguredConf()).isSameAs(conf);
    }

    @Test
    void getDeclaredFieldsIncludingInheritedReturnsChildAndParentFields() {
        List<Field> fields = ReflectionUtils.getDeclaredFieldsIncludingInherited(ChildType.class);

        assertThat(fields).extracting(Field::getName).contains("childField", "parentField");
    }

    @Test
    void getDeclaredMethodsIncludingInheritedReturnsChildAndParentMethods() {
        List<Method> methods = ReflectionUtils.getDeclaredMethodsIncludingInherited(ChildType.class);

        assertThat(methods).extracting(Method::getName).contains("childMethod", "parentMethod");
    }

    public static class JobConfiguredObject implements JobConfigurable {
        private JobConf configuredConf;

        @Override
        public void configure(JobConf jobConf) {
            configuredConf = jobConf;
        }

        JobConf getConfiguredConf() {
            return configuredConf;
        }
    }

    public static class ParentType {
        private String parentField;

        public String parentMethod() {
            return parentField;
        }
    }

    public static class ChildType extends ParentType {
        private int childField;

        public int childMethod() {
            return childField;
        }
    }

    private static class PrivateConstructible implements Configurable {
        private Configuration conf;

        private PrivateConstructible() {
        }

        @Override
        public void setConf(Configuration conf) {
            this.conf = conf;
        }

        @Override
        public Configuration getConf() {
            return conf;
        }
    }
}
