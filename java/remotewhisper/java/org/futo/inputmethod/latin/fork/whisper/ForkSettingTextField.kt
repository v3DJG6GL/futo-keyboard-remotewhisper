package org.futo.inputmethod.latin.fork.whisper

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.uix.SettingsKey
import org.futo.inputmethod.latin.uix.getSettingBlocking
import org.futo.inputmethod.latin.uix.settings.ScreenTitle
import org.futo.inputmethod.latin.uix.settings.useDataStore

/**
 * Variant of upstream's SettingTextField (uix/settings/Components.kt) with a keyboard type and
 * an optional masked mode for secrets.
 */
@Composable
fun ForkSettingTextField(
    title: String,
    placeholder: String,
    field: SettingsKey<String>,
    keyboardType: KeyboardType = KeyboardType.Text,
    isPassword: Boolean = false
) {
    val context = LocalContext.current

    val setting = useDataStore(field)
    val textFieldValue = remember { mutableStateOf(context.getSettingBlocking(
        field.key, field.default)) }

    // For secret fields: reveal while the field is fresh (empty), mask once a value is saved.
    // The trailing eye toggle flips this either way.
    val revealed = remember { mutableStateOf(textFieldValue.value.isEmpty()) }

    LaunchedEffect(textFieldValue.value) {
        setting.setValue(textFieldValue.value)
    }

    ScreenTitle(title)

    TextField(
        value = textFieldValue.value,
        onValueChange = {
            textFieldValue.value = it
        },
        placeholder = { Text(placeholder) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        visualTransformation = if (isPassword && !revealed.value) {
            PasswordVisualTransformation()
        } else {
            VisualTransformation.None
        },
        trailingIcon = if (isPassword) {
            {
                IconButton(onClick = { revealed.value = !revealed.value }) {
                    Icon(
                        painterResource(
                            if (revealed.value) R.drawable.fork_eye_off else R.drawable.eye
                        ),
                        contentDescription = stringResource(
                            if (revealed.value) {
                                R.string.fork_voice_input_settings_hide_api_key
                            } else {
                                R.string.fork_voice_input_settings_show_api_key
                            }
                        )
                    )
                }
            }
        } else null,
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp, 4.dp),
    )
}
