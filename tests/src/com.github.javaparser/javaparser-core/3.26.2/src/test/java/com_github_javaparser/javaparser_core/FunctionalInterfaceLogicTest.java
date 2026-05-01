/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_javaparser.javaparser_core;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.javaparser.resolution.MethodUsage;
import com.github.javaparser.resolution.declarations.ResolvedConstructorDeclaration;
import com.github.javaparser.resolution.declarations.ResolvedFieldDeclaration;
import com.github.javaparser.resolution.declarations.ResolvedMethodDeclaration;
import com.github.javaparser.resolution.declarations.ResolvedReferenceTypeDeclaration;
import com.github.javaparser.resolution.declarations.ResolvedTypeParameterDeclaration;
import com.github.javaparser.resolution.logic.FunctionalInterfaceLogic;
import com.github.javaparser.resolution.types.ResolvedReferenceType;
import com.github.javaparser.resolution.types.ResolvedType;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

public class FunctionalInterfaceLogicTest {
    @Test
    void returnsEmptyWhenInterfaceHasNoAbstractMethods() {
        ResolvedReferenceTypeDeclaration declaration = new EmptyInterfaceDeclaration();

        assertThat(FunctionalInterfaceLogic.getFunctionalMethod(declaration)).isEmpty();
    }

    private static final class EmptyInterfaceDeclaration implements ResolvedReferenceTypeDeclaration {
        @Override
        public List<ResolvedReferenceType> getAncestors(boolean acceptIncompleteList) {
            return Collections.emptyList();
        }

        @Override
        public List<ResolvedFieldDeclaration> getAllFields() {
            return Collections.emptyList();
        }

        @Override
        public Set<ResolvedMethodDeclaration> getDeclaredMethods() {
            return Collections.emptySet();
        }

        @Override
        public Set<MethodUsage> getAllMethods() {
            return Collections.emptySet();
        }

        @Override
        public boolean isAssignableBy(ResolvedType type) {
            return false;
        }

        @Override
        public boolean isAssignableBy(ResolvedReferenceTypeDeclaration other) {
            return false;
        }

        @Override
        public boolean hasDirectlyAnnotation(String qualifiedName) {
            return false;
        }

        @Override
        public boolean isFunctionalInterface() {
            return false;
        }

        @Override
        public List<ResolvedConstructorDeclaration> getConstructors() {
            return Collections.emptyList();
        }

        @Override
        public List<ResolvedTypeParameterDeclaration> getTypeParameters() {
            return Collections.emptyList();
        }

        @Override
        public Optional<ResolvedReferenceTypeDeclaration> containerType() {
            return Optional.empty();
        }

        @Override
        public boolean isInterface() {
            return true;
        }

        @Override
        public String getPackageName() {
            return "example";
        }

        @Override
        public String getClassName() {
            return "NoAbstractMethods";
        }

        @Override
        public String getQualifiedName() {
            return "example.NoAbstractMethods";
        }

        @Override
        public String getName() {
            return "NoAbstractMethods";
        }
    }
}
