/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_beachape.enumeratum_macros_3

sealed abstract class PrecompiledArticleStatus(val value: String)

case object PrecompiledDraft extends PrecompiledArticleStatus("draft")
case object PrecompiledPublished extends PrecompiledArticleStatus("published")
