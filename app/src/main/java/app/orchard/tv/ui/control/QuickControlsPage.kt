package app.orchard.tv.ui.control

import android.app.AlertDialog
import android.widget.EditText
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.orchard.tv.CcPage
import app.orchard.tv.HomeViewModel
import app.orchard.tv.Overlay
import app.orchard.tv.R
import app.orchard.tv.settings.QuickAction
import app.orchard.tv.settings.QuickControls
import app.orchard.tv.settings.Restricted
import app.orchard.tv.system.QuickCatalog
import app.orchard.tv.ui.home.MenuRow
import app.orchard.tv.ui.theme.Glyph
import app.orchard.tv.ui.theme.Type

@Composable
internal fun QuickControlsPage(vm: HomeViewModel, takeFocus: Boolean) {
    val context = LocalContext.current
    val store = QuickControls.store
    var adding by remember { mutableStateOf(false) }
    val back = remember { FocusRequester() }
    BackHandler(enabled = adding) { adding = false }
    LaunchedEffect(takeFocus, adding) {
        if (takeFocus) { withFrameNanos { }; runCatching { back.requestFocus() } }
    }
    fun change(op: String, id: String = "", value: QuickAction? = null, on: Boolean = false, delta: Int = 0, rev: Long = store.revision) {
        vm.whenUnlocked {
            if (Restricted.active) return@whenUnlocked
            val error = store.change(op, id, value, on, delta, rev)
            if (error != null) vm.notify(context.getString(when (error) {
                "limit" -> R.string.qc_limit
                "exists" -> R.string.qc_exists
                "conflict", "missing" -> R.string.qc_conflict
                else -> R.string.qc_invalid
            }), Glyph.Info)
        }
    }
    Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        MenuRow(Glyph.ChevronLeft, stringResource(if (adding) R.string.qc_add else R.string.qc_title), focusRequester = back,
            onClick = { if (adding) adding = false else vm.overlay = Overlay.ControlCenter(CcPage.Main) })
        if (adding) {
            val catalog = QuickCatalog.available(context)
            val chosen = store.items.map { it.catalogKey }.toSet()
            listOf("system" to R.string.qc_group_system, "apps" to R.string.qc_group_apps).forEach { (group, label) ->
                val entries = catalog.filter { it.group == group }
                if (entries.isNotEmpty()) {
                    BasicText(stringResource(label), style = Type.Callout)
                    QuickTileGrid(entries.map { QuickTile(it.key, it.title, it.icon, it.control.pkg, it.key in chosen) }) { tile ->
                        val entry = entries.firstOrNull { it.key == tile.id } ?: return@QuickTileGrid
                        change("add", value = entry.control)
                        adding = false
                    }
                }
            }
        } else {
            MenuRow(Glyph.Switches, stringResource(R.string.qc_show_area),
                trailing = stringResource(if (store.enabled) R.string.st_on else R.string.st_off),
                onClick = { change("enabled", on = !store.enabled) })
            BasicText(stringResource(R.string.qc_arrange_hint), style = Type.Caption)
            val tiles = store.items.map { it.tile().copy(title = it.title + if (it.enabled) "" else " · " + context.getString(R.string.qc_hidden)) } +
                QuickTile("add", context.getString(R.string.qc_add), "add")
            QuickTileGrid(tiles) { tile ->
                if (tile.id == "add") { adding = true; return@QuickTileGrid }
                val control = store.items.firstOrNull { it.id == tile.id } ?: return@QuickTileGrid
                val index = store.items.indexOf(control)
                val revision = store.revision
                val choices = buildList<Pair<Int, () -> Unit>> {
                    add(R.string.qc_rename to {
                        val input = EditText(context).apply { setSingleLine(); setText(control.title); selectAll() }
                        val dialog = AlertDialog.Builder(context).setTitle(R.string.qc_rename).setView(input)
                            .setNegativeButton(android.R.string.cancel, null).setPositiveButton(R.string.qc_save, null).create()
                        dialog.setOnShowListener {
                            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                                val edited = control.copy(title = input.text.toString().trim())
                                if (!edited.valid()) input.error = context.getString(R.string.qc_invalid)
                                else { dialog.dismiss(); change("update", control.id, edited, rev = revision) }
                            }
                        }
                        dialog.show()
                    })
                    if (index > 0) add(R.string.qc_up to { change("move", control.id, delta = -1, rev = revision) })
                    if (index < store.items.lastIndex) add(R.string.qc_down to { change("move", control.id, delta = 1, rev = revision) })
                    add((if (control.enabled) R.string.qc_hide else R.string.qc_show) to { change("toggle", control.id, on = !control.enabled, rev = revision) })
                    add(R.string.qc_remove to { change("delete", control.id, rev = revision) })
                }
                vm.whenUnlocked {
                    AlertDialog.Builder(context).setTitle(control.title)
                        .setItems(choices.map { context.getString(it.first) }.toTypedArray()) { _, which -> choices[which].second() }
                        .setNegativeButton(android.R.string.cancel, null).show()
                }
            }
            Spacer(Modifier.height(5.dp))
        }
    }
}
