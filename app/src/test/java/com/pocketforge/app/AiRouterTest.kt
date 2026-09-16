package com.pocketforge.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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

    @Test
    fun highDemandAndRateLimitsAreRetryable() {
        assertTrue(AiRouter.isTransientFailure("This model is currently experiencing high demand."))
        assertTrue(AiRouter.isTransientFailure("HTTP 429: Too many requests"))
        assertTrue(AiRouter.isTransientFailure("HTTP 503: Service unavailable"))
    }

    @Test
    fun badCredentialsAreNotRetryable() {
        assertFalse(AiRouter.isTransientFailure("HTTP 401: invalid API key"))
        assertFalse(AiRouter.isTransientFailure("HTTP 403: permission denied"))
    }
}
