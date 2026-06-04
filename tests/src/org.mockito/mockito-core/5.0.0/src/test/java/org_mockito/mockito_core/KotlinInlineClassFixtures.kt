/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
@file:JvmName("KotlinInlineClassFixtures")

package org_mockito.mockito_core

import org.mockito.Mockito

@JvmInline
public value class InlineGreeting(public val value: String)

public interface InlineGreetingService {
    public fun greeting(): InlineGreeting
}

public fun stubInlineGreetingReturn(): String {
    val service = Mockito.mock(InlineGreetingService::class.java)
    Mockito.`when`(service.greeting()).thenReturn(InlineGreeting("Hello inline class"))
    return service.greeting().value
}
