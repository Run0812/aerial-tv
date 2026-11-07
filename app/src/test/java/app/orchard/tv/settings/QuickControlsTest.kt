package app.orchard.tv.settings

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class QuickControlsTest {
    private val context: Context = RuntimeEnvironment.getApplication()

    @Test fun migratesEnabledClashOnceEvenWhenItIsLaterDeleted() {
        context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().putBoolean("clash_shortcut", true).commit()
        val store = QuickControlStore(context)
        assertTrue(store.enabled)
        val clash = store.items.single()
        assertEquals("clash", clash.kind)
        assertNull(store.change("delete", clash.id))
        assertTrue(QuickControlStore(context).items.isEmpty())
    }

    @Test fun editsAndOrderSurviveReloadWithoutReenablingHiddenItems() {
        val store = QuickControlStore(context)
        val one = QuickAction(title = "Clash Meta")
        val two = QuickAction(title = "Player", kind = "app", pkg = "org.example.player")
        assertNull(store.change("add", value = one))
        assertTrue(store.enabled)
        assertNull(store.change("add", value = two))
        assertNull(store.change("move", two.id, delta = -1))
        assertNull(store.change("toggle", one.id, on = false))
        assertNull(store.change("update", two.id, two.copy(title = "Watch")))
        assertNull(store.change("enabled", on = false))
        val reloaded = QuickControlStore(context)
        assertFalse(reloaded.enabled)
        assertEquals(listOf("Watch", "Clash Meta"), reloaded.items.map { it.title })
        assertEquals(listOf(true, false), reloaded.items.map { it.enabled })
        assertEquals(store.revision, reloaded.revision)
    }

    @Test fun stalePhoneEditCannotOverwriteNewerTvEdit() {
        val store = QuickControlStore(context)
        val one = QuickAction(title = "Original")
        assertNull(store.change("add", value = one))
        val oldRevision = store.revision
        assertNull(store.change("update", one.id, one.copy(title = "TV name")))
        assertEquals("conflict", store.change("update", one.id, one.copy(title = "Old phone name"), expected = oldRevision))
        assertEquals("TV name", QuickControlStore(context).items.single().title)
    }

    @Test fun rejectsInvalidExplicitActionsWithoutChangingRevisionOrList() {
        val store = QuickControlStore(context)
        val item = QuickAction(title = "Action", kind = "broadcast", pkg = "org.example.app", component = ".Receiver")
        for (invalid in listOf(item.copy(pkg = "bad package"), item.copy(component = ""),
            item.copy(uri = "file:///sdcard/private"), item.copy(uri = "noscheme"), item.copy(extras = "{\"bad\":[1]}"))) {
            assertEquals("invalid", store.change("add", value = invalid))
        }
        assertEquals(0L, store.revision)
        assertTrue(store.items.isEmpty())
        assertNull(store.change("add", value = item.copy(extras = "{\"index\":3,\"enabled\":true}")))
    }

    @Test fun capsTheListAndDoesNotReplaceAnExistingId() {
        val store = QuickControlStore(context)
        repeat(QuickControlStore.MAX_ITEMS) { assertNull(store.change("add", value = QuickAction(title = "Button $it", kind = "app", pkg = "org.example.app$it"))) }
        assertEquals("limit", store.change("add", value = QuickAction(title = "One too many", kind = "system", action = "mute")))
        val first = store.items.first()
        assertEquals("invalid", store.change("add", value = first.copy(title = "Duplicate")))
        assertEquals("Button 0", store.items.first().title)
    }

    @Test fun draggingToAnAbsolutePositionPersistsAndRejectsStaleOrInvalidDrops() {
        val store = QuickControlStore(context)
        val actions = listOf("mute", "network", "settings").map { QuickAction(title = it, kind = "system", action = it) }
        actions.forEach { assertNull(store.change("add", value = it)) }
        val revision = store.revision
        assertNull(store.change("place", actions.first().id, delta = 2))
        assertEquals(listOf("network", "settings", "mute"), QuickControlStore(context).items.map { it.action })
        assertEquals("conflict", store.change("place", actions[1].id, delta = 0, expected = revision))
        assertEquals("invalid", store.change("place", actions.first().id, delta = 3))
        assertEquals("exists", store.change("add", value = actions.first().copy(id = "another")))
        assertEquals("invalid", store.change("add", value = QuickAction(title = "Unknown", kind = "system", action = "shell")))
        assertEquals(3, store.items.size)
    }
}
