/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ch_qos_logback_contrib.logback_json_classic;

import ch.qos.logback.contrib.json.JsonFormatter;

import java.util.Map;

public class CustomJsonFormatter implements JsonFormatter {
    @Override
    public String toJsonString(Map m) {
        return m.toString();
    }
}
