/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_ow2_asm.asm;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.signature.SignatureReader;
import org.objectweb.asm.signature.SignatureVisitor;
import org.objectweb.asm.signature.SignatureWriter;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Parses generic method signatures for an application's schema inspection subsystem. */
public class GenericSignatureInspectionTest {
    @Test
    void readsAndRewritesAGenericMethodContract() {
        String methodSignature =
                "<T:Ljava/lang/Object;>(Ljava/util/List<+TT;>;)"
                        + "Ljava/util/Map<Ljava/lang/String;TT;>;^Ljava/io/IOException;";
        List<String> events = new ArrayList<>();

        new SignatureReader(methodSignature).accept(new RecordingSignatureVisitor(events));
        SignatureWriter writer = new SignatureWriter();
        new SignatureReader(methodSignature).accept(writer);

        assertEquals(
                List.of(
                        "formal:T",
                        "class-bound",
                        "class:java/lang/Object",
                        "parameter-type",
                        "class:java/util/List",
                        "wildcard:+",
                        "type-variable:T",
                        "return-type",
                        "class:java/util/Map",
                        "wildcard:=",
                        "class:java/lang/String",
                        "wildcard:=",
                        "type-variable:T",
                        "exception-type",
                        "class:java/io/IOException"),
                events);
        assertEquals(methodSignature, writer.toString());
    }

    private static final class RecordingSignatureVisitor extends SignatureVisitor {
        private final List<String> events;

        private RecordingSignatureVisitor(List<String> events) {
            super(Opcodes.ASM9);
            this.events = events;
        }

        @Override
        public void visitFormalTypeParameter(String name) {
            events.add("formal:" + name);
        }

        @Override
        public SignatureVisitor visitClassBound() {
            events.add("class-bound");
            return child();
        }

        @Override
        public SignatureVisitor visitParameterType() {
            events.add("parameter-type");
            return child();
        }

        @Override
        public SignatureVisitor visitReturnType() {
            events.add("return-type");
            return child();
        }

        @Override
        public SignatureVisitor visitExceptionType() {
            events.add("exception-type");
            return child();
        }

        @Override
        public void visitClassType(String name) {
            events.add("class:" + name);
        }

        @Override
        public void visitTypeVariable(String name) {
            events.add("type-variable:" + name);
        }

        @Override
        public void visitTypeArgument() {
            events.add("unbounded-wildcard");
        }

        @Override
        public SignatureVisitor visitTypeArgument(char wildcard) {
            events.add("wildcard:" + wildcard);
            return child();
        }

        private RecordingSignatureVisitor child() {
            return new RecordingSignatureVisitor(events);
        }
    }
}
