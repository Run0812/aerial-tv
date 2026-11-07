package app.orchard.tv.ui.control

import android.app.AlertDialog
import android.content.Context
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
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
import app.orchard.tv.apps.AppCatalog
import app.orchard.tv.apps.AppNames
import app.orchard.tv.settings.QuickAction
import app.orchard.tv.settings.QuickControlStore
import app.orchard.tv.settings.QuickControls
import app.orchard.tv.settings.Restricted
import app.orchard.tv.ui.home.MenuRow
import app.orchard.tv.ui.theme.Glyph
import app.orchard.tv.ui.theme.Type

@Composable
internal fun QuickControlsPage(vm: HomeViewModel, takeFocus: Boolean) {
    val context = LocalContext.current
    val store = QuickControls.store
    val back = remember { FocusRequester() }
    LaunchedEffect(takeFocus) {
        if (takeFocus) { withFrameNanos { }; runCatching { back.requestFocus() } }
    }

    fun change(op: String, id: String = "", value: QuickAction? = null, on: Boolean = false, delta: Int = 0, rev: Long = store.revision) {
        vm.whenUnlocked {
            if (Restricted.active) return@whenUnlocked
            val error = store.change(op, id, value, on, delta, rev)
            if (error != null) vm.notify(context.getString(quickErrorMessage(error)), Glyph.Info)
        }
    }

    fun edit(control: QuickAction, adding: Boolean, revision: Long = store.revision) {
        editQuickAction(context, control) { edited ->
            change(if (adding) "add" else "update", edited.id, edited, rev = revision)
        }
    }

    Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState())) {
        MenuRow(Glyph.ChevronLeft, stringResource(R.string.qc_title), focusRequester = back,
            onClick = { vm.overlay = Overlay.ControlCenter(CcPage.Main) })
        MenuRow(Glyph.Switches, stringResource(R.string.qc_show_area),
            trailing = stringResource(if (store.enabled) R.string.st_on else R.string.st_off),
            onClick = { change("enabled", on = !store.enabled) })
        MenuRow(Glyph.Pencil, stringResource(R.string.qc_add), trailing = "›", onClick = {
            if (store.items.size >= QuickControlStore.MAX_ITEMS) vm.notify(context.getString(R.string.qc_limit), Glyph.Info)
            else vm.whenUnlocked { chooseQuickKind(context) { edit(it, true) } }
        })
        if (store.items.isEmpty()) BasicText(stringResource(R.string.qc_empty), style = Type.Caption)
        store.items.forEachIndexed { index, control ->
            key(control.id) {
                MenuRow(Glyph.Switches, control.title,
                    trailing = stringResource(if (control.enabled) R.string.st_on else R.string.st_off) + " ›",
                    onClick = {
                        vm.whenUnlocked {
                            val revision = store.revision
                            val choices = buildList<Pair<Int, () -> Unit>> {
                                add(R.string.qc_edit to { edit(control, false, revision) })
                                add((if (control.enabled) R.string.qc_hide else R.string.qc_show) to {
                                    change("toggle", control.id, on = !control.enabled, rev = revision)
                                })
                                if (index > 0) add(R.string.qc_up to { change("move", control.id, delta = -1, rev = revision) })
                                if (index < store.items.lastIndex) add(R.string.qc_down to { change("move", control.id, delta = 1, rev = revision) })
                                add(R.string.qc_delete to { change("delete", control.id, rev = revision) })
                            }
                            AlertDialog.Builder(context).setTitle(control.title)
                                .setItems(choices.map { context.getString(it.first) }.toTypedArray()) { _, which -> choices[which].second() }
                                .setNegativeButton(android.R.string.cancel, null).show()
                        }
                    })
            }
        }
    }
}

private fun chooseQuickKind(context: Context, onChoose: (QuickAction) -> Unit) {
    val labels = arrayOf("Clash Meta", context.getString(R.string.qc_app), context.getString(R.string.qc_activity), context.getString(R.string.qc_broadcast))
    AlertDialog.Builder(context).setTitle(R.string.qc_add).setItems(labels) { _, which ->
        when (which) {
            0 -> onChoose(QuickAction(title = "Clash Meta"))
            1 -> {
                val apps = AppCatalog.loaded.filter { it.source == null && it.folder == null && it.shortcut == null }
                    .sortedBy { (AppNames.of(it.key) ?: it.label).lowercase() }
                if (apps.isEmpty()) onChoose(QuickAction(title = "", kind = "app"))
                else AlertDialog.Builder(context).setTitle(R.string.qc_app)
                    .setItems(apps.map { AppNames.of(it.key) ?: it.label }.toTypedArray()) { _, i ->
                        val app = apps[i]
                        onChoose(QuickAction(title = (AppNames.of(app.key) ?: app.label).take(64), kind = "app", pkg = app.packageName))
                    }.setNegativeButton(android.R.string.cancel, null).show()
            }
            2 -> onChoose(QuickAction(title = "", kind = "activity"))
            else -> onChoose(QuickAction(title = "", kind = "broadcast"))
        }
    }.setNegativeButton(android.R.string.cancel, null).show()
}

/** Native text fields let TV keyboards and connected keyboards enter the exact action parameters. */
private fun editQuickAction(context: Context, control: QuickAction, onSave: (QuickAction) -> Unit) {
    val box = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        val pad = (24 * resources.displayMetrics.density).toInt()
        setPadding(pad, 0, pad, pad / 2)
    }
    fun field(label: Int, value: String, multiline: Boolean = false): EditText {
        box.addView(TextView(context).apply { setText(label) })
        return EditText(context).apply {
            contentDescription = context.getString(label)
            inputType = InputType.TYPE_CLASS_TEXT or if (multiline) InputType.TYPE_TEXT_FLAG_MULTI_LINE else InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            setSingleLine(!multiline)
            if (multiline) minLines = 2
            setText(value)
            box.addView(this)
        }
    }
    val title = field(R.string.qc_name, control.title)
    val pkg = if (control.kind != "clash") field(R.string.qc_package, control.pkg) else null
    val explicit = control.kind == "activity" || control.kind == "broadcast"
    val component = if (explicit) field(R.string.qc_component, control.component) else null
    val action = if (explicit) field(R.string.qc_action, control.action) else null
    val uri = if (explicit) field(R.string.qc_uri, control.uri) else null
    val extras = if (explicit) field(R.string.qc_extras, control.extras, multiline = true) else null
    val scroll = ScrollView(context).apply { addView(box) }
    val dialog = AlertDialog.Builder(context).setTitle(R.string.qc_edit).setView(scroll)
        .setNegativeButton(android.R.string.cancel, null).setPositiveButton(R.string.qc_save, null).create()
    dialog.setOnShowListener {
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val edited = control.copy(title = title.text.toString().trim(), pkg = pkg?.text?.toString()?.trim().orEmpty(),
                component = component?.text?.toString()?.trim().orEmpty(), action = action?.text?.toString()?.trim().orEmpty(),
                uri = uri?.text?.toString()?.trim().orEmpty(), extras = extras?.text?.toString()?.trim().orEmpty().ifBlank { "{}" })
            if (!edited.valid()) title.error = context.getString(R.string.qc_invalid)
            else { dialog.dismiss(); onSave(edited) }
        }
    }
    dialog.show()
}

private fun quickErrorMessage(error: String): Int = when (error) {
    "limit" -> R.string.qc_limit
    "conflict", "missing" -> R.string.qc_conflict
    else -> R.string.qc_invalid
}
