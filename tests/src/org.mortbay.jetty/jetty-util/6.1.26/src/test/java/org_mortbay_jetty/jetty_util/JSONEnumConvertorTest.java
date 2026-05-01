/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mortbay_jetty.jetty_util;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mortbay.util.ajax.JSON;
import org.mortbay.util.ajax.JSONEnumConvertor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class JSONEnumConvertorTest {
    @Test
    void convertsJsonObjectToEnumConstantWhenFromJsonIsEnabled() {
        JSONEnumConvertor convertor = new JSONEnumConvertor(true);
        Map<String, Object> properties = new HashMap<>();
        properties.put("class", SampleStatus.class.getName());
        properties.put("value", SampleStatus.COMPLETE.name());

        Object converted = convertor.fromJSON(properties);

        assertThat(converted).isSameAs(SampleStatus.COMPLETE);
    }

    @Test
    void roundTripsEnumThroughJsonConvertorRegistration() {
        JSON json = new JSON();
        json.addConvertor(SampleStatus.class, new JSONEnumConvertor(true));

        String serialized = json.toJSON(SampleStatus.STARTED);
        Object parsed = json.fromJSON(serialized);

        assertThat(serialized).contains("\"class\":\"" + SampleStatus.class.getName() + "\"");
        assertThat(serialized).contains("\"value\":\"STARTED\"");
        assertThat(parsed).isSameAs(SampleStatus.STARTED);
    }

    @Test
    void rejectsJsonObjectsWhenFromJsonIsDisabled() {
        JSONEnumConvertor convertor = new JSONEnumConvertor();

        assertThatThrownBy(() -> convertor.fromJSON(new HashMap<>()))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    public enum SampleStatus {
        STARTED,
        COMPLETE
    }
}
