/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_sun_xml_bind.jaxb_xjc;

import com.sun.tools.xjc.BadCommandLineException;
import com.sun.tools.xjc.Options;
import com.sun.tools.xjc.Plugin;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class OptionsTest {
    @Test
    void discoversAndActivatesBundledPlugins() throws Exception {
        Options options = new Options();
        List<Plugin> plugins = options.getAllPlugins();
        assertThat(plugins).extracting(Plugin::getOptionName)
                .contains("Xinject-code", "Xlocator", "Xsync-methods", "mark-generated", "episode");

        assertThat(options.parseArgument(new String[]{"-Xsync-methods"}, 0)).isEqualTo(1);
        assertThat(options.activePlugins).extracting(Plugin::getOptionName)
                .containsExactly("Xsync-methods");
        assertThat(options.getAllPlugins()).containsExactlyElementsOf(plugins);
    }

    @Test
    void reportsMissingPackageArgument() {
        Options options = new Options();
        assertThatThrownBy(() -> options.parseArgument(new String[]{"-p"}, 0))
                .isInstanceOf(BadCommandLineException.class)
                .hasMessageContaining("operand");
    }
}
