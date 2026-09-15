package com.pocketforge.app

import java.time.Instant

enum class AgentStepState { WAITING, WORKING, DONE, FAILED }

data class AgentStep(
    val id: String,
    val agent: String,
    val title: String,
    val detail: String,
    val state: AgentStepState,
    val provider: String? = null,
    val timestamp: Long = Instant.now().toEpochMilli()
)

data class AgentRunResult(
    val answer: String,
    val providers: List<String>,
    val plan: AiPlan,
    val critique: String?,
    val synthesis: String
)

object AgentOrchestrator {
    private const val ARCHITECT_SYSTEM = """
You are the Architect in a software-building agent team. Analyze the user's goal and produce a compact implementation plan.
Focus on product intent, architecture, scope, dependencies, risks, and what must not be broken.
Do not claim code has been changed. Do not reveal hidden chain-of-thought. Return a concise work summary that another coding agent can act on.
"""

    private const val REVIEWER_SYSTEM = """
You are the independent Reviewer in a software-building agent team. Critique another agent's implementation plan.
Look for missing requirements, unsafe broad rewrites, Android/Gradle pitfalls, data/security issues, testing gaps, and ways to simplify.
Return only actionable review notes. Do not reveal hidden chain-of-thought.
"""

    private const val LEAD_SYSTEM = """
You are the Lead agent. Combine the user's goal, an architect plan, and an independent review into one clear execution contract.
Return JSON only with these string keys: summary, scope, protected, verify, implementationNotes.
The contract must be implementable, minimize unrelated changes, and require a green build before completion.
"""

    fun runGoal(
        goal: String,
        vault: SecretVault,
        emit: (AgentStep) -> Unit
    ): AgentRunResult {
        val providers = AiRouter.configuredProviders(vault)
        require(providers.isNotEmpty()) { "Connect at least one free AI provider in Integration Center." }

        emit(AgentStep("understand", "PocketForge", "Understanding your request", "Turning plain English into a software job.", AgentStepState.WORKING))

        val architectProvider = providers.first()
        emit(AgentStep("architect", "Architect", "Designing the safest implementation", "Checking scope, architecture and protected behavior.", AgentStepState.WORKING, architectProvider.label))
        val architect = AiRouter.ask(
            architectProvider,
            ARCHITECT_SYSTEM.trim(),
            goal,
            vault
        )
        emit(AgentStep("architect", "Architect", "Architecture ready", architect.take(500), AgentStepState.DONE, architectProvider.label))

        val reviewerProvider = providers.firstOrNull { it != architectProvider }
        val critique = if (reviewerProvider != null) {
            emit(AgentStep("review", "Reviewer", "Challenging the plan", "A different model is looking for omissions and fragile assumptions.", AgentStepState.WORKING, reviewerProvider.label))
            val review = AiRouter.ask(
                reviewerProvider,
                REVIEWER_SYSTEM.trim(),
                "USER GOAL:\n$goal\n\nARCHITECT PLAN:\n$architect",
                vault
            )
            emit(AgentStep("review", "Reviewer", "Review complete", review.take(500), AgentStepState.DONE, reviewerProvider.label))
            review
        } else {
            emit(AgentStep("review", "Reviewer", "Single-provider mode", "Connect a second free AI to enable independent review.", AgentStepState.DONE, architectProvider.label))
            null
        }

        val leadProvider = providers.firstOrNull { it != architectProvider && it != reviewerProvider }
            ?: reviewerProvider
            ?: architectProvider
        emit(AgentStep("lead", "Lead", "Reconciling the team", "Combining the goal, architecture and independent review into one contract.", AgentStepState.WORKING, leadProvider.label))

        val synthesisInput = buildString {
            appendLine("USER GOAL:")
            appendLine(goal)
            appendLine()
            appendLine("ARCHITECT PLAN:")
            appendLine(architect)
            if (!critique.isNullOrBlank()) {
                appendLine()
                appendLine("INDEPENDENT REVIEW:")
                appendLine(critique)
            }
        }
        val finalRouted = AiRouter.askBest(
            LEAD_SYSTEM.trim(),
            synthesisInput,
            vault,
            exclude = emptySet(),
            expectJson = true
        )
        val plan = parsePlan(finalRouted.provider, finalRouted.text)
        emit(AgentStep("lead", "Lead", "Execution contract ready", plan.summary, AgentStepState.DONE, finalRouted.provider))
        emit(AgentStep("verify", "Verifier", "Verification gate prepared", plan.verify, AgentStepState.DONE, "Compiler + CI"))

        val answer = buildString {
            append(plan.summary)
            append("\n\nScope: ")
            append(plan.scope)
            append("\n\nProtected: ")
            append(plan.protected)
            append("\n\nVerification: ")
            append(plan.verify)
        }

        return AgentRunResult(
            answer = answer,
            providers = listOfNotNull(architectProvider.label, reviewerProvider?.label, finalRouted.provider).distinct(),
            plan = plan,
            critique = critique,
            synthesis = finalRouted.text
        )
    }

    private fun parsePlan(provider: String, raw: String): AiPlan {
        val cleaned = raw.trim()
            .removePrefix("```json")
            .removePrefix("```JSON")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
        val json = runCatching { org.json.JSONObject(cleaned) }.getOrNull()
        return AiPlan(
            provider = provider,
            summary = json?.optString("summary")?.takeIf { it.isNotBlank() } ?: raw.take(1000),
            scope = json?.optString("scope")?.takeIf { it.isNotBlank() } ?: "Requested change only",
            protected = json?.optString("protected")?.takeIf { it.isNotBlank() } ?: "Unrelated working behavior",
            verify = json?.optString("verify")?.takeIf { it.isNotBlank() } ?: "Compile, test, lint and reject a red build",
            implementationNotes = json?.optString("implementationNotes")?.takeIf { it.isNotBlank() } ?: "Use the smallest safe implementation."
        )
    }
}
