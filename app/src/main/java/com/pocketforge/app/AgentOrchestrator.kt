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

        emit(
            AgentStep(
                "understand",
                "PocketForge",
                "Understanding your request",
                "Turning plain English into a software job.",
                AgentStepState.WORKING
            )
        )
        emit(
            AgentStep(
                "understand",
                "PocketForge",
                "Request understood",
                "Scope is ready for the architect team.",
                AgentStepState.DONE,
                "Auto router"
            )
        )

        emit(
            AgentStep(
                "architect",
                "Architect",
                "Designing the safest implementation",
                "Auto will hand off to another connected provider if one is busy or unavailable.",
                AgentStepState.WORKING,
                "Auto failover"
            )
        )
        val architectRouted = AiRouter.askBest(
            ARCHITECT_SYSTEM.trim(),
            goal,
            vault
        )
        val architect = architectRouted.text
        val architectProvider = providers.firstOrNull { it.label == architectRouted.provider }
        emit(
            AgentStep(
                "architect",
                "Architect",
                "Architecture ready",
                architect.take(500),
                AgentStepState.DONE,
                architectRouted.provider
            )
        )

        val reviewExclusions = architectProvider?.let { setOf(it) }.orEmpty()
        val hasIndependentReviewer = providers.any { it !in reviewExclusions }
        var reviewerLabel: String? = null
        val critique = if (hasIndependentReviewer) {
            emit(
                AgentStep(
                    "review",
                    "Reviewer",
                    "Challenging the plan",
                    "A different connected provider will review the architecture, with failover if needed.",
                    AgentStepState.WORKING,
                    "Independent auto"
                )
            )
            val reviewResult = runCatching {
                AiRouter.askBest(
                    REVIEWER_SYSTEM.trim(),
                    "USER GOAL:\n$goal\n\nARCHITECT PLAN:\n$architect",
                    vault,
                    exclude = reviewExclusions
                )
            }
            reviewResult.fold(
                onSuccess = { routed ->
                    reviewerLabel = routed.provider
                    emit(
                        AgentStep(
                            "review",
                            "Reviewer",
                            "Review complete",
                            routed.text.take(500),
                            AgentStepState.DONE,
                            routed.provider
                        )
                    )
                    routed.text
                },
                onFailure = { error ->
                    emit(
                        AgentStep(
                            "review",
                            "Reviewer",
                            "Independent review unavailable",
                            "The backup reviewers are temporarily unavailable, so PocketForge will continue to the lead instead of stopping. ${error.message.orEmpty()}",
                            AgentStepState.DONE,
                            "Auto continued"
                        )
                    )
                    null
                }
            )
        } else {
            emit(
                AgentStep(
                    "review",
                    "Reviewer",
                    "Single-provider mode",
                    "Connect a second AI provider to enable independent review.",
                    AgentStepState.DONE,
                    architectRouted.provider
                )
            )
            null
        }

        emit(
            AgentStep(
                "lead",
                "Lead",
                "Reconciling the team",
                "Auto will use any healthy connected provider to create the final execution contract.",
                AgentStepState.WORKING,
                "Auto failover"
            )
        )

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
            expectJson = true
        )
        val plan = parsePlan(finalRouted.provider, finalRouted.text)
        emit(
            AgentStep(
                "lead",
                "Lead",
                "Execution contract ready",
                plan.summary,
                AgentStepState.DONE,
                finalRouted.provider
            )
        )
        emit(
            AgentStep(
                "verify",
                "Verifier",
                "Verification gate prepared",
                plan.verify,
                AgentStepState.DONE,
                "Compiler + CI"
            )
        )

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
            providers = listOfNotNull(
                architectRouted.provider,
                reviewerLabel,
                finalRouted.provider
            ).distinct(),
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
