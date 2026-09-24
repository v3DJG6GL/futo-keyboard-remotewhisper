package org.futo.inputmethod.latin.fork.whisper

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.fork.whisper.remote.TestConnectionResult
import org.futo.inputmethod.latin.fork.whisper.remote.WhisperApiClient
import org.futo.inputmethod.latin.uix.USE_SYSTEM_VOICE_INPUT
import org.futo.inputmethod.latin.uix.settings.NavigationItemStyle
import org.futo.inputmethod.latin.uix.settings.Tip
import org.futo.inputmethod.latin.uix.settings.UserSetting
import org.futo.inputmethod.latin.uix.settings.UserSettingsMenu
import org.futo.inputmethod.latin.uix.settings.useDataStoreValue
import org.futo.inputmethod.latin.uix.settings.userSettingDecorationOnly
import org.futo.inputmethod.latin.uix.settings.userSettingNavigationItem

const val CUSTOM_WHISPER_SERVER_NAV_PATH = "customWhisperServer"

/** Entry in upstream's VoiceInputMenu (pages/VoiceInput.kt) that opens [CustomWhisperServerMenu]. */
val remoteWhisperNavItem = userSettingNavigationItem(
    title = R.string.fork_voice_input_settings_custom_server_title,
    subtitle = R.string.fork_voice_input_settings_custom_server_subtitle,
    style = NavigationItemStyle.Misc,
    navigateTo = CUSTOM_WHISPER_SERVER_NAV_PATH
).copy(visibilityCheck = @Composable {
    useDataStoreValue(USE_SYSTEM_VOICE_INPUT) == false
})

/** Registered in upstream's SettingsMenus (SettingsNavigator.kt). */
val CustomWhisperServerMenu = UserSettingsMenu(
    title = R.string.fork_voice_input_settings_custom_server_title,
    navPath = CUSTOM_WHISPER_SERVER_NAV_PATH, registerNavPath = true,
    settings = listOf(
        userSettingDecorationOnly {
            Tip(stringResource(R.string.fork_voice_input_settings_custom_server_description))
        },
        userSettingDecorationOnly {
            Tip(stringResource(R.string.fork_voice_input_settings_custom_server_cleartext_warning))
        },
        UserSetting(name = R.string.fork_voice_input_settings_custom_server_url) {
            ForkSettingTextField(
                title = stringResource(R.string.fork_voice_input_settings_custom_server_url),
                placeholder = stringResource(R.string.fork_voice_input_settings_custom_server_url_placeholder),
                field = CUSTOM_WHISPER_SERVER_URL,
                keyboardType = KeyboardType.Uri
            )
        },
        UserSetting(name = R.string.fork_voice_input_settings_custom_server_api_key) {
            ForkSettingTextField(
                title = stringResource(R.string.fork_voice_input_settings_custom_server_api_key),
                placeholder = stringResource(R.string.fork_voice_input_settings_custom_server_api_key_placeholder),
                field = CUSTOM_WHISPER_API_KEY,
                isPassword = true
            )
        },
        UserSetting(name = R.string.fork_voice_input_settings_custom_server_model) {
            ForkSettingTextField(
                title = stringResource(R.string.fork_voice_input_settings_custom_server_model),
                placeholder = stringResource(R.string.fork_voice_input_settings_custom_server_model_placeholder),
                field = CUSTOM_WHISPER_MODEL
            )
        },
        userSettingDecorationOnly {
            TestConnectionButton()
        }
    )
)

private sealed interface TestState {
    data object Idle : TestState
    data object Testing : TestState
    data class Ok(val message: String) : TestState
    data class Error(val message: String) : TestState
}

@Composable
private fun TestConnectionButton() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<TestState>(TestState.Idle) }

    Column(modifier = Modifier
        .fillMaxWidth()
        .padding(16.dp, 8.dp)) {
        Button(
            enabled = state != TestState.Testing,
            onClick = {
                state = TestState.Testing
                scope.launch {
                    val result = withContext(Dispatchers.IO) {
                        WhisperApiClient().testConnection(RemoteVoiceInput.configFromSettings(context))
                    }
                    state = when (result) {
                        is TestConnectionResult.Success -> TestState.Ok(result.message)
                        is TestConnectionResult.Failure -> TestState.Error(result.message)
                    }
                }
            }
        ) {
            if (state == TestState.Testing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = LocalContentColor.current
                )
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.fork_voice_input_settings_custom_server_testing))
            } else {
                Text(stringResource(R.string.fork_voice_input_settings_custom_server_test))
            }
        }

        AnimatedVisibility(visible = state is TestState.Ok || state is TestState.Error) {
            val success = state is TestState.Ok
            val message = when (val s = state) {
                is TestState.Ok -> s.message
                is TestState.Error -> s.message
                else -> ""
            }
            TestResultCard(success = success, message = message)
        }
    }
}

@Composable
private fun TestResultCard(success: Boolean, message: String) {
    val container = if (success) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.errorContainer
    }
    val onContainer = if (success) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onErrorContainer
    }

    Surface(
        color = container,
        shape = RoundedCornerShape(4.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painterResource(if (success) R.drawable.check_circle else R.drawable.close),
                contentDescription = null,
                tint = onContainer
            )
            Spacer(Modifier.width(8.dp))
            Text(message, color = onContainer)
        }
    }
}
