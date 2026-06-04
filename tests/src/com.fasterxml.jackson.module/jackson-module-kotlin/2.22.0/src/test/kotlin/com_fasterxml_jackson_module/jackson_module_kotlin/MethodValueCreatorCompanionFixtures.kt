/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml_jackson_module.jackson_module_kotlin

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty

class PrivateCompanionFactoryValue private constructor(
    @get:JsonProperty("value")
    val value: String
) {
    private companion object {
        @JvmStatic
        @JsonCreator
        fun create(@JsonProperty("value") value: String): PrivateCompanionFactoryValue =
            PrivateCompanionFactoryValue(value)
    }
}
