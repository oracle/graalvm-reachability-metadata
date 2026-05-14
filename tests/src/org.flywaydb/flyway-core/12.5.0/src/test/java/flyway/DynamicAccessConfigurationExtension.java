/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package flyway;

import org.flywaydb.core.extensibility.ConfigurationExtension;

public class DynamicAccessConfigurationExtension implements ConfigurationExtension {

    private DynamicAccessRoot root;

    @Override
    public String getNamespace() {
        return "integration";
    }

    @Override
    public String getConfigurationParameterFromEnvironmentVariable(final String environmentVariable) {
        return null;
    }

    @Override
    public int getPriority() {
        return 0;
    }

    public DynamicAccessRoot getRoot() {
        return root;
    }

    public void setRoot(final DynamicAccessRoot root) {
        this.root = root;
    }
}
