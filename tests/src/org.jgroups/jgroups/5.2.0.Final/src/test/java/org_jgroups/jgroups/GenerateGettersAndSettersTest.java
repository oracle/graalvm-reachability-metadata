/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jgroups.jgroups;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import org.jgroups.annotations.ManagedAttribute;
import org.jgroups.annotations.Property;
import org.jgroups.util.GenerateGettersAndSetters;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class GenerateGettersAndSettersTest {
    @Test
    void mainGeneratesAccessorsForAnnotatedFields() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        PrintStream previousOut = System.out;
        PrintStream previousErr = System.err;

        try (PrintStream capturedOut = new PrintStream(out, true, StandardCharsets.UTF_8);
                PrintStream capturedErr = new PrintStream(err, true, StandardCharsets.UTF_8)) {
            System.setOut(capturedOut);
            System.setErr(capturedErr);
            GenerateGettersAndSetters.main(new String[] {
                    "-class", GenerateGettersAndSettersTarget.class.getName(), "-use-generics"
            });
        } finally {
            System.setOut(previousOut);
            System.setErr(previousErr);
        }

        assertThat(err.toString(StandardCharsets.UTF_8)).isEmpty();
        assertThat(out.toString(StandardCharsets.UTF_8))
                .contains("public int getSize() {return size;}")
                .contains("public <T extends GenerateGettersAndSettersTarget> T setSize(int s) "
                        + "{this.size=s; return (T)this;}")
                .contains("public boolean enabled() {return enabled;}")
                .contains("public <T extends GenerateGettersAndSettersTarget> T enabled(boolean e) "
                        + "{this.enabled=e; return (T)this;}")
                .doesNotContain("ignored")
                .doesNotContain("staticValue");
    }
}

class GenerateGettersAndSettersTarget {
    @Property
    private int size;

    @ManagedAttribute
    private boolean enabled;

    private String ignored;

    @Property
    private static int staticValue;
}
