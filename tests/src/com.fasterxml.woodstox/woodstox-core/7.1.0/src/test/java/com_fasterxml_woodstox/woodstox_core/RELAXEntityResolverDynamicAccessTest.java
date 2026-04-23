/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml_woodstox.woodstox_core;

import static org.assertj.core.api.Assertions.assertThat;

import com.ctc.wstx.shaded.msv.org_jp_gr_xml.sax.RELAXEntityResolver;
import org.junit.jupiter.api.Test;
import org.xml.sax.InputSource;

public class RELAXEntityResolverDynamicAccessTest {
    @Test
    void resolvesBundledRelaxEntities() {
        RELAXEntityResolver resolver = new RELAXEntityResolver();
        InputSource source = resolver.resolveEntity(
                "-//RELAX//DTD RELAX Core 1.0//JA",
                "http://www.xml.gr.jp/relax/core1/relaxCore.dtd");

        assertThat(source).isNotNull();
        assertThat(source.getSystemId()).contains("relaxCore.dtd");
    }
}
