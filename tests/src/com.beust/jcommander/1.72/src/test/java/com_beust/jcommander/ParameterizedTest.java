/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_beust.jcommander;

import com.beust.jcommander.DynamicParameter;
import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParametersDelegate;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ParameterizedTest {
    @Test
    void parsesFieldBackedParametersAndDelegates() {
        FieldBackedOptions options = new FieldBackedOptions();

        JCommander.newBuilder()
                .addObject(options)
                .build()
                .parse("--count", "7", "--label", "release", "-Dmode=native");

        assertThat(options.count()).isEqualTo(7);
        assertThat(options.delegateLabel()).isEqualTo("release");
        assertThat(options.properties()).containsEntry("mode", "native");
    }

    @Test
    void parsesMethodBackedParameterWithGetter() {
        GetterBackedOptions options = new GetterBackedOptions();

        JCommander.newBuilder()
                .addObject(options)
                .build()
                .parse("--name", "metadata");

        assertThat(options.getName()).isEqualTo("metadata");
        assertThat(options.setterCalls()).isEqualTo(1);
    }

    @Test
    void parsesMethodBackedParameterWithOnlyBackingField() {
        FieldFallbackOptions options = new FieldFallbackOptions();

        JCommander.newBuilder()
                .addObject(options)
                .build()
                .parse("--secret", "covered");

        assertThat(options.revealSecret()).isEqualTo("covered");
        assertThat(options.setterCalls()).isEqualTo(1);
    }

    public static class FieldBackedOptions {
        @Parameter(names = "--count")
        private int count = 3;

        @DynamicParameter(names = "-D")
        private Map<String, String> properties = new HashMap<>();

        @ParametersDelegate
        private DelegateOptions delegate = new DelegateOptions();

        int count() {
            return count;
        }

        String delegateLabel() {
            return delegate.label();
        }

        Map<String, String> properties() {
            return properties;
        }
    }

    public static class DelegateOptions {
        @Parameter(names = "--label")
        private String label = "snapshot";

        String label() {
            return label;
        }
    }

    public static class GetterBackedOptions {
        private String name = "default";
        private int setterCalls;

        @Parameter(names = "--name")
        public void setName(String name) {
            setterCalls++;
            this.name = name;
        }

        public String getName() {
            return name;
        }

        int setterCalls() {
            return setterCalls;
        }
    }

    public static class FieldFallbackOptions {
        private String secret = "initial";
        private int setterCalls;

        @Parameter(names = "--secret")
        public void setSecret(String secret) {
            setterCalls++;
            this.secret = secret;
        }

        String revealSecret() {
            return secret;
        }

        int setterCalls() {
            return setterCalls;
        }
    }
}
