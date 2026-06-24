/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package commons_configuration.commons_configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.Arrays;

import org.apache.commons.configuration.BaseConfiguration;
import org.apache.commons.configuration.beanutils.ConfigurationDynaBean;
import org.junit.jupiter.api.Test;

public class ConfigurationDynaBeanTest {
    @Test
    public void constructorInitializesBeanAndSupportsDynaPropertyAccess() {
        BaseConfiguration configuration = new BaseConfiguration();
        ConfigurationDynaBean bean = new ConfigurationDynaBean(configuration);

        bean.set("server.host", "localhost");
        bean.set("server.ports", Arrays.asList("8080", "8443"));
        bean.set("server.aliases", new String[] {"primary", "secondary"});
        bean.set("database", "url", "jdbc:test");

        assertThat(bean.get("server.host")).isEqualTo("localhost");
        assertThat(bean.get("server.ports", 0)).isEqualTo("8080");
        assertThat(bean.get("server.ports", 1)).isEqualTo("8443");
        assertThat(bean.get("server.aliases", 0)).isEqualTo("primary");
        assertThat(bean.contains("database", "url")).isTrue();
        assertThat(bean.get("database", "url")).isEqualTo("jdbc:test");

        Object server = bean.get("server");
        assertThat(server).isInstanceOf(ConfigurationDynaBean.class);
        assertThat(((ConfigurationDynaBean) server).get("host")).isEqualTo("localhost");
    }

    @Test
    public void invalidPropertyOperationsReportClearFailures() {
        ConfigurationDynaBean bean = new ConfigurationDynaBean(new BaseConfiguration());

        assertThatNullPointerException()
                .isThrownBy(() -> bean.set("missing", null))
                .withMessage("Error trying to set property to null.");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> bean.get("missing"))
                .withMessage("Property 'missing' does not exist.");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> bean.get("missing", 0))
                .withMessage("Property 'missing' does not exist.");
    }
}
