package com.khanz1.serlok

import android.util.Log
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import org.junit.Assert.assertTrue

internal fun ComposeContentTestRule.assertNoUnlabeledActions() {
    val interactive = onAllNodes(
        SemanticsMatcher("click or long-click action") { node ->
            node.config.contains(SemanticsActions.OnClick) ||
                node.config.contains(SemanticsActions.OnLongClick)
        },
    ).fetchSemanticsNodes()
    val unlabeled = interactive.filter { node ->
        val hasText = node.config.getOrNull(SemanticsProperties.Text)?.isNotEmpty() == true
        val hasDescription =
            node.config.getOrNull(SemanticsProperties.ContentDescription)?.isNotEmpty() == true
        val hasState = node.config.getOrNull(SemanticsProperties.StateDescription)?.isNotBlank() == true
        !hasText && !hasDescription && !hasState
    }

    assertTrue("Unlabeled interactive semantics nodes: ${unlabeled.size}", unlabeled.isEmpty())
    Log.i(
        "SerlokA11y",
        "[PASS] Accessibility semantics audit | expected: 0 unlabeled actions | actual: 0 findings",
    )
}
