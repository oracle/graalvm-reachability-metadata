/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_jetty.jetty_util_ajax;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jetty.util.ajax.JSON;
import org.eclipse.jetty.util.ajax.JSONCollectionConvertor;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class JSONCollectionConvertorTest {
    @Test
    void recreatesCollectionDeclaredByClassField() {
        JSON json = new JSON();
        json.addConvertor(ArrayList.class, new JSONCollectionConvertor());
        List<String> source = new ArrayList<>();
        source.add("alpha");
        source.add("beta");

        Object parsed = json.parse(new JSON.StringSource(json.toJSON(source)));

        assertThat(parsed).isInstanceOf(ArrayList.class);
        assertThat(parsed).isEqualTo(source);
    }
}
