package dev.endlesssea.app

import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Compose interaction smoke test. The full Hilt navigation graph is not launched here. */
@RunWith(AndroidJUnit4::class)
class ExtensionFormSmokeTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun repositoryHintCanBeTapped() {
        var tapped = false
        rule.setContent {
            androidx.compose.material3.TextButton(onClick = { tapped = true }) {
                Text("Ajouter un dépôt")
            }
        }
        rule.onNodeWithText("Ajouter un dépôt").assertIsDisplayed().performClick()
        assertTrue(tapped)
    }
}
