package com.pocketforge.app

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.Assert.assertThrows

class StudioSpecTest {
    @Test fun startersAreValidAndRoundTrip() {
        StudioStarters.all.forEach { starter ->
            val valid = starter.spec.validate()
            val parsed = try {
                StudioSpec.parse(valid.json().toString())
            } catch (error: Throwable) {
                throw AssertionError("Could not round-trip starter '${starter.title}': ${error.message}", error)
            }
            assertEquals("Round-trip starter '${starter.title}'", valid, parsed)
        }
    }

    @Test fun unsupportedIdsAreRejected() {
        val base = StudioStarters.all.first().spec
        assertThrows(IllegalArgumentException::class.java) {
            base.copy(screens = base.screens + StudioScreen("Bad ID", "Bad", "list", fields = listOf(StudioField("entry", "Entry")))).validate()
        }
    }

    @Test fun protectedScreensCannotBeRemovedDuringAnEdit() {
        val base = StudioStarters.all.first().spec
        assertThrows(IllegalArgumentException::class.java) { base.copy(screens = base.screens.drop(1)).validate(base) }
    }
}
