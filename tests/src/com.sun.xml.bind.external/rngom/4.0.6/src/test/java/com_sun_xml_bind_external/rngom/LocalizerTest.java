/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_sun_xml_bind_external.rngom;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.tools.rngom.binary.ListPattern;
import com.sun.tools.rngom.util.Localizer;

import org.junit.jupiter.api.Test;

public class LocalizerTest {
    @Test
    void loadsMessagesResourceBundleForClassPackage() {
        Localizer localizer = new Localizer(ListPattern.class);

        assertThat(localizer.message("missing_start_element")).isEqualTo("missing \"start\" element");
        assertThat(localizer.message("reference_to_undefined", "address"))
                .isEqualTo("reference to undefined pattern \"address\"");
        assertThat(localizer.message("unrecognized_datatype", "urn:example", "identifier"))
                .isEqualTo("datatype \"identifier\" from library \"urn:example\" not recognized");
        Object[] datatypeArguments = {"urn:example", "identifier", "missing"};
        assertThat(localizer.message("unsupported_datatype_detail", datatypeArguments))
                .isEqualTo("datatype \"identifier\" from library \"urn:example\" not supported: missing");
    }
}
