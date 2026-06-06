/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_alibaba.fastjson;

import static org.assertj.core.api.Assertions.assertThat;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.parser.ParserConfig;
import org.junit.jupiter.api.Test;

public class ThrowableDeserializerTest {
    @Test
    void parseThrowableUsesStringAndCauseConstructorWhenAvailable() {
        ParserConfig config = noAsmConfig();
        String json = """
                {
                  "message": "outer message",
                  "cause": {
                    "message": "root cause"
                  }
                }
                """;

        CauseOnlyException exception = JSON.parseObject(json, CauseOnlyException.class, config);

        assertThat(exception).isInstanceOf(CauseOnlyException.class);
        assertThat(exception.getMessage()).isEqualTo("outer message");
        assertThat(exception.getCause()).isInstanceOf(Exception.class);
        assertThat(exception.getCause()).hasMessage("root cause");
    }

    @Test
    void parseThrowableUsesStringConstructorWhenCauseConstructorIsUnavailable() {
        ParserConfig config = noAsmConfig();

        MessageOnlyException exception = JSON.parseObject(
                "{\"message\":\"message constructor\"}", MessageOnlyException.class, config);

        assertThat(exception).isInstanceOf(MessageOnlyException.class);
        assertThat(exception.getMessage()).isEqualTo("message constructor");
        assertThat(exception.getCause()).isNull();
    }

    @Test
    void parseThrowableUsesDefaultConstructorWhenOnlyDefaultConstructorIsAvailable() {
        ParserConfig config = noAsmConfig();

        DefaultOnlyException exception = JSON.parseObject(
                "{\"message\":\"default constructor\"}", DefaultOnlyException.class, config);

        assertThat(exception).isInstanceOf(DefaultOnlyException.class);
        assertThat(exception.getMessage()).isNull();
        assertThat(exception.getCause()).isNull();
    }

    private static ParserConfig noAsmConfig() {
        ParserConfig config = new ParserConfig();
        config.setAsmEnable(false);
        return config;
    }

    public static class CauseOnlyException extends Exception {
        private static final long serialVersionUID = 1L;

        public CauseOnlyException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static class MessageOnlyException extends Exception {
        private static final long serialVersionUID = 1L;

        public MessageOnlyException(String message) {
            super(message);
        }
    }

    public static class DefaultOnlyException extends Exception {
        private static final long serialVersionUID = 1L;

        public DefaultOnlyException() {
            super();
        }
    }
}
