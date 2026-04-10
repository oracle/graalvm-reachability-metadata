/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_json.json;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;

import org.json.JSONObject;
import org.json.JSONPropertyName;
import org.junit.jupiter.api.Test;

public class JsonTest {
    @Test
    void readsSelectedPublicFieldsAndNames() {
        final PublicFieldBean bean = new PublicFieldBean();
        final JSONObject jsonObject = new JSONObject(bean, "count", "label", "missing");
        final String[] names = JSONObject.getNames(bean);

        assertThat(jsonObject.getInt("count")).isEqualTo(7);
        assertThat(jsonObject.getString("label")).isEqualTo("widget");
        assertThat(jsonObject.has("missing")).isFalse();
        assertThat(names).containsExactlyInAnyOrder("count", "label");
    }

    @Test
    void populatesBeanPropertiesFromMethodsAndHierarchyAnnotations() {
        final HierarchyBean bean = new HierarchyBean();
        final JSONObject jsonObject = new JSONObject(bean);

        assertThat(jsonObject.getString("regularValue")).isEqualTo("regular");
        assertThat(jsonObject.getString("interfaceAlias")).isEqualTo("from-interface");
        assertThat(jsonObject.getString("superAlias")).isEqualTo("from-superclass");
        assertThat(jsonObject.has("interfaceValue")).isFalse();
        assertThat(jsonObject.has("superValue")).isFalse();
    }

    @Test
    void populatesBootstrapLoadedBeansUsingDeclaredMethods() {
        final Locale locale = Locale.forLanguageTag("en-US");
        final JSONObject jsonObject = new JSONObject(locale);

        assertThat(Locale.class.getClassLoader()).isNull();
        assertThat(jsonObject.getString("language")).isEqualTo("en");
        assertThat(jsonObject.getString("country")).isEqualTo("US");
    }

    public static final class PublicFieldBean {
        public final int count = 7;
        public final String label = "widget";
    }

    public interface InterfaceAnnotatedValue {
        @JSONPropertyName("interfaceAlias")
        String getInterfaceValue();
    }

    public static class SuperclassAnnotatedValue {
        @JSONPropertyName("superAlias")
        public String getSuperValue() {
            return "from-superclass";
        }
    }

    public static final class HierarchyBean extends SuperclassAnnotatedValue implements InterfaceAnnotatedValue {
        public String getRegularValue() {
            return "regular";
        }

        @Override
        public String getInterfaceValue() {
            return "from-interface";
        }

        @Override
        public String getSuperValue() {
            return "from-superclass";
        }
    }
}
