/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_bytebuddy.byte_buddy;

import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;

import net.bytebuddy.agent.builder.LambdaFactory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;

public class LambdaFactoryTest {
    @Test
    void releasingAnUnregisteredTransformerDoesNotRequestMetafactoryReset() {
        ClassFileTransformer transformer = new ClassFileTransformer() {
            @Override
            public byte[] transform(ClassLoader loader, String name, Class<?> type,
                                    ProtectionDomain protectionDomain, byte[] bytes) {
                throw new AssertionError("Releasing a transformer must not execute it");
            }
        };
        assertFalse(LambdaFactory.release(transformer));
        assertFalse(LambdaFactory.release(transformer));
    }
}
