/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package commons_configuration.commons_configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.HashMap;
import java.util.Map;

import org.apache.commons.beanutils.DynaBean;
import org.apache.commons.beanutils.DynaProperty;
import org.apache.commons.configuration.BaseConfiguration;
import org.apache.commons.configuration.beanutils.ConfigurationDynaBean;
import org.apache.commons.configuration.beanutils.ConfigurationDynaClass;
import org.junit.jupiter.api.Test;

public class ConfigurationDynaClassTest {
    @Test
    public void constructorCreatesDynaClassForConfigurationProperties() throws Exception {
        BaseConfiguration configuration = new BaseConfiguration();
        configuration.setProperty("host", "localhost");
        configuration.setProperty("enabled", Boolean.TRUE);
        configuration.setProperty("port", Integer.valueOf(8080));
        configuration.setProperty("weight", Double.valueOf(1.25));
        configuration.setProperty("grade", Character.valueOf('A'));
        configuration.setProperty("attempts", Short.valueOf((short) 3));

        ConfigurationDynaClass dynaClass = new ConfigurationDynaClass(configuration);

        assertThat(dynaClass.getName()).isEqualTo(ConfigurationDynaBean.class.getName());
        assertThat(dynaClass.getDynaProperty("host").getType()).isEqualTo(String.class);
        assertThat(dynaClass.getDynaProperty("enabled").getType()).isEqualTo(Boolean.TYPE);
        assertThat(dynaClass.getDynaProperty("port").getType()).isEqualTo(Integer.TYPE);
        assertThat(dynaClass.getDynaProperty("weight").getType()).isEqualTo(Double.TYPE);
        assertThat(dynaClass.getDynaProperty("grade").getType()).isEqualTo(Character.TYPE);
        assertThat(dynaClass.getDynaProperty("attempts").getType()).isEqualTo(Short.TYPE);
        assertThat(dynaClass.getDynaProperty("missing")).isNull();

        Map<String, Class<?>> propertyTypes = new HashMap<String, Class<?>>();
        for (DynaProperty property : dynaClass.getDynaProperties()) {
            propertyTypes.put(property.getName(), property.getType());
        }

        assertThat(propertyTypes)
                .containsEntry("host", String.class)
                .containsEntry("enabled", Boolean.TYPE)
                .containsEntry("port", Integer.TYPE)
                .containsEntry("weight", Double.TYPE)
                .containsEntry("grade", Character.TYPE)
                .containsEntry("attempts", Short.TYPE);

        DynaBean bean = dynaClass.newInstance();
        assertThat(bean).isInstanceOf(ConfigurationDynaBean.class);
        assertThat(bean.get("host")).isEqualTo("localhost");
    }

    @Test
    public void getDynaPropertyRejectsNullPropertyName() {
        ConfigurationDynaClass dynaClass = new ConfigurationDynaClass(new BaseConfiguration());

        assertThatIllegalArgumentException()
                .isThrownBy(() -> dynaClass.getDynaProperty(null))
                .withMessage("Property name must not be null!");
    }
}
