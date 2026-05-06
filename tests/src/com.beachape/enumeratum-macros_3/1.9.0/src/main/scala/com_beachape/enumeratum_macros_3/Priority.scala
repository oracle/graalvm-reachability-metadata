/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_beachape.enumeratum_macros_3

sealed abstract class Priority(val value: Int)
case object LowPriority extends Priority(1)
case object MediumPriority extends Priority(5)
case object HighPriority extends Priority(10)
