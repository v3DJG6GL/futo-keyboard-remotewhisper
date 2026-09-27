package org.futo.inputmethod.latin.fork.whisper

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.navigation.NavHostController
import kotlinx.coroutines.runBlocking
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.uix.FileKind
import org.futo.inputmethod.latin.uix.ResourceHelper
import org.futo.inputmethod.latin.uix.icon
import org.futo.inputmethod.latin.uix.kindTitle
import org.futo.inputmethod.latin.uix.settings.pages.modelmanager.openModelImporter
import org.futo.inputmethod.updates.openURI
import java.util.Locale

/** The voice-input backend currently selected for a language. */
enum class VoiceBackend { None, LocalFile, RemoteServer }

/**
 * Replaces upstream's ConfirmResourceActionDialog for voice input models in LanguagesScreen
 * (pages/Languages.kt): works out which backend the language uses and offers the matching
 * actions, including the custom Whisper server.
 */
@Composable
fun ForkVoiceInputResourceDialog(
    locale: Locale,
    navController: NavHostController,
    onDismissRequest: () -> Unit,
) {
    val context = LocalContext.current
    val urlSet = RemoteVoiceInput.isServerConfigured(context)
    val remoteEnabled = RemoteVoiceInput.isEnabledForLocale(context, locale)
    val fileSet = runBlocking {
        ResourceHelper.findFileForKind(context, locale, FileKind.VoiceInput)?.exists() == true
    }
    val backend = when {
        remoteEnabled && urlSet -> VoiceBackend.RemoteServer
        fileSet -> VoiceBackend.LocalFile
        else -> VoiceBackend.None
    }

    VoiceInputModelActionDialog(
        onDismissRequest = onDismissRequest,
        backend = backend,
        hasBuiltInFallback = ResourceHelper.BuiltInVoiceInputFallbacks[locale.language] != null,
        locale = locale,
        onImport = {
            openModelImporter(context)
            onDismissRequest()
        },
        onExplore = {
            context.openURI(FileKind.VoiceInput.getAddonUrlForLocale(locale), true)
            onDismissRequest()
        },
        onUseCustomServer = {
            RemoteVoiceInput.setEnabledForLocale(context, locale, true)
            onDismissRequest()
            // Enabling without a server configured would be a no-op, so guide the user there.
            if (!RemoteVoiceInput.isServerConfigured(context)) {
                navController.navigate(CUSTOM_WHISPER_SERVER_NAV_PATH)
            }
        },
        onConfigureServer = {
            onDismissRequest()
            navController.navigate(CUSTOM_WHISPER_SERVER_NAV_PATH)
        },
        onRemove = {
            ResourceHelper.deleteResourceForLanguage(context, FileKind.VoiceInput, locale)
            onDismissRequest()
        },
    )
}

/**
 * Dedicated voice-input model picker. Unlike upstream's ConfirmResourceActionDialog (which is
 * file-centric and only offers two actions), this presents 3+ mutually-exclusive choices as a
 * vertically stacked, full-width list — the Material 3 layout for dialogs whose actions don't fit
 * on one row — and shows text/actions appropriate to the current backend (none / local file /
 * custom server).
 */
@Composable
fun VoiceInputModelActionDialog(
    onDismissRequest: () -> Unit,
    backend: VoiceBackend,
    hasBuiltInFallback: Boolean,
    locale: Locale,
    onImport: () -> Unit,
    onExplore: () -> Unit,
    onUseCustomServer: () -> Unit,
    onConfigureServer: () -> Unit,
    onRemove: () -> Unit,
) {
    val bodyText = when (backend) {
        VoiceBackend.None ->
            stringResource(R.string.fork_language_settings_resource_voice_input_none)
        VoiceBackend.LocalFile ->
            stringResource(R.string.language_settings_resource_voice_input_selected) +
                if (!hasBuiltInFallback) {
                    "\n\n" + stringResource(R.string.language_settings_resource_voice_input_selected_no_default_warning)
                } else ""
        VoiceBackend.RemoteServer ->
            stringResource(R.string.fork_language_settings_resource_voice_input_remote_selected) +
                if (!hasBuiltInFallback) {
                    "\n\n" + stringResource(R.string.language_settings_resource_voice_input_selected_no_default_warning)
                } else ""
    }

    Dialog(onDismissRequest = onDismissRequest) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Icon(
                    painterResource(id = FileKind.VoiceInput.icon()),
                    contentDescription = null,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "${locale.displayLanguage} - ${FileKind.VoiceInput.kindTitle(LocalResources.current)}",
                    style = MaterialTheme.typography.headlineSmall
                )
                Spacer(Modifier.height(16.dp))
                Text(text = bodyText, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(24.dp))

                when (backend) {
                    VoiceBackend.None -> {
                        DialogActionButton(R.string.language_settings_resource_import_file_button, onClick = onImport)
                        DialogActionButton(R.string.fork_language_settings_resource_use_custom_server_button, onClick = onUseCustomServer)
                        DialogActionButton(R.string.language_settings_resource_explore_online_button, onClick = onExplore)
                    }
                    VoiceBackend.LocalFile -> {
                        DialogActionButton(R.string.language_settings_resource_replace_button, onClick = onImport)
                        DialogActionButton(R.string.fork_language_settings_resource_use_custom_server_button, onClick = onUseCustomServer)
                        DialogActionButton(
                            if (hasBuiltInFallback) R.string.language_settings_resource_revert_to_default_button
                            else R.string.language_settings_resource_remove_button,
                            destructive = true, onClick = onRemove
                        )
                    }
                    VoiceBackend.RemoteServer -> {
                        DialogActionButton(R.string.fork_language_settings_resource_configure_server_button, onClick = onConfigureServer)
                        DialogActionButton(R.string.fork_language_settings_resource_use_local_model_button, onClick = onImport)
                        DialogActionButton(R.string.fork_language_settings_resource_disable_custom_server_button, destructive = true, onClick = onRemove)
                    }
                }

                DialogActionButton(R.string.fork_language_settings_resource_cancel_button, onClick = onDismissRequest)
            }
        }
    }
}

@Composable
private fun DialogActionButton(
    textRes: Int,
    destructive: Boolean = false,
    onClick: () -> Unit
) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = if (destructive) {
            ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
        } else {
            ButtonDefaults.textButtonColors()
        }
    ) {
        Text(
            stringResource(textRes),
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Start
        )
    }
}
