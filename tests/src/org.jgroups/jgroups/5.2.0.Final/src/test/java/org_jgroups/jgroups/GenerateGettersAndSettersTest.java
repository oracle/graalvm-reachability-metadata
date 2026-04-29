/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jgroups.jgroups;

import org.jgroups.stack.Protocol;
import org.jgroups.util.GenerateGettersAndSetters;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

public class GenerateGettersAndSettersTest {
    @Test
    void generatesAccessorsForManagedAndPropertyFields() throws Exception {
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        PrintStream capture = new PrintStream(stdout, true, StandardCharsets.UTF_8);

        try {
            System.setOut(capture);
            GenerateGettersAndSetters.main(new String[] {"-class", Protocol.class.getName(), "-use-generics"});
        } finally {
            System.setOut(originalOut);
            capture.close();
        }

        String generatedAccessors = stdout.toString(StandardCharsets.UTF_8);

        assertThat(generatedAccessors)
            .contains("public boolean stats() {return stats;}")
            .contains("public <T extends Protocol> T stats(boolean s) {this.stats=s; return (T)this;}")
            .contains("public Address getLocalAddr() {return local_addr;}")
            .contains("public <T extends Protocol> T setLocalAddr(Address l) {this.local_addr=l; return (T)this;}")
            .doesNotContain("getLog() {return log;}");
    }
}
