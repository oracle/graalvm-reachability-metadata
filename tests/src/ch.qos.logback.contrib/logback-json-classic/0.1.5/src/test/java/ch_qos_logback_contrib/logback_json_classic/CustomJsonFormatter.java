package ch_qos_logback_contrib.logback_json_classic;

import ch.qos.logback.contrib.json.JsonFormatter;

import java.util.Map;

public class CustomJsonFormatter implements JsonFormatter {
    @Override
    public String toJsonString(Map m) {
        return m.toString();
    }
}
