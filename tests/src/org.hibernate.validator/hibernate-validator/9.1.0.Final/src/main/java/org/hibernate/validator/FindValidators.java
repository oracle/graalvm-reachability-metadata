/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.hibernate.validator;

import jakarta.validation.ConstraintValidator;
import org.reflections.Reflections;

import java.io.IOException;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.util.Set;

import static org.reflections.scanners.Scanners.SubTypes;

// Uses Reflections to find all subtypes of ConstraintValidator and formats them as reflect-config.json entries
class FindValidators {
    public static void main(String[] args) throws IOException {
        Reflections reflections = new Reflections("org.hibernate.validator");
        Set<Class<?>> subTypes = reflections.get(SubTypes.of(ConstraintValidator.class).asClass());
        String template = new String(FindValidators.class.getResourceAsStream("/template.txt").readAllBytes(), StandardCharsets.UTF_8);

        for (Class<?> subType : subTypes) {
            if (!Modifier.isPublic(subType.getModifiers()) || subType.isInterface() || Modifier.isAbstract(subType.getModifiers())) {
                continue;
            }
            System.out.printf(template, subType.getName());
        }
    }
}
