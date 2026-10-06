/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_yaml.snakeyaml;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.extensions.compactnotation.PackageCompactConstructor;

public class PackageCompactConstructorTest {

    @Test
    void loadsCompactBeanBySimpleNameFromConfiguredPackage() {
        String yaml = "PackageCompactBean(exampleName)";

        PackageCompactBean bean = (PackageCompactBean) new Yaml(
                new PackageCompactConstructor(PackageCompactBean.class.getPackageName())).load(yaml);

        assertThat(bean.getName()).isEqualTo("exampleName");
    }
}

final class PackageCompactBean {
    private final String name;

    private PackageCompactBean(String name) {
        this.name = name;
    }

    String getName() {
        return name;
    }
}
