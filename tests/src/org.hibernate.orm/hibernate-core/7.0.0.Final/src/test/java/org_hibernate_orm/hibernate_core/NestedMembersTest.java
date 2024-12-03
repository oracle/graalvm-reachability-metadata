/*
 * Copyright 2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org_hibernate_orm.hibernate_core;

import java.util.Arrays;

import org.hibernate.annotations.DialectOverride;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/**
 * @author Christoph Strobl
 * @since 2024/12
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class NestedMembersTest {

    @Test
    public void getNestedMembers() {

        Class<?>[] nestMembers = DialectOverride.class.getNestMembers();
        Arrays.stream(nestMembers).forEach(System.out::println);
        if(nestMembers.length != 33) {
            throw new RuntimeException("Expected 33 members but only found %s".formatted(nestMembers.length));
        }
    }
}
