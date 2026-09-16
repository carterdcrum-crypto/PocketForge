package com.pocketforge.app

import org.junit.Assert.assertEquals
import org.junit.Test

class AiRouterTest {
    @Test
    fun tinyVisualEditUsesQuickReasoning() {
        assertEquals(
            AiJobComplexity.QUICK,
            AiRouter.classify("Change the button color and rename its label to Build")
        )
    }

    @Test
    fun normalFeatureUsesStandardReasoning() {
        assertEquals(
            AiJobComplexity.STANDARD,
            AiRouter.classify("Add a settings screen where the user can choose whether notifications are enabled")
        )
    }

    @Test
    fun repositoryBuildRepairUsesDeepReasoning() {
        assertEquals(
            AiJobComplexity.DEEP,
            AiRouter.classify("Inspect the GitHub repository, fix the failing Gradle build, repair dependencies, and verify the CI workflow")
        )
    }
}
