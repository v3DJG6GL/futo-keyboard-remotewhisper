package org.futo.inputmethod.latin.uix.settings.pages.learnedwords

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.Subtypes
import org.futo.inputmethod.latin.SubtypesSetting
import org.futo.inputmethod.latin.personalization.LearnedWordsAddOnManualPick
import org.futo.inputmethod.latin.personalization.LearnedWordsAutoAddSetting
import org.futo.inputmethod.latin.personalization.LearnedWordsAutoAddUses
import org.futo.inputmethod.latin.personalization.LearnedWordsExporter
import org.futo.inputmethod.latin.personalization.LearnedWordsRepository
import org.futo.inputmethod.latin.personalization.LearnedWordsStoreLanguageOnly
import org.futo.inputmethod.latin.personalization.userHistoryDictionaryLocales
import org.futo.inputmethod.latin.uix.settings.NavigationItem
import org.futo.inputmethod.latin.uix.settings.NavigationItemStyle
import org.futo.inputmethod.latin.uix.settings.SettingSlider
import org.futo.inputmethod.latin.uix.settings.Tip
import org.futo.inputmethod.latin.uix.settings.UserSetting
import org.futo.inputmethod.latin.uix.settings.UserSettingsMenu
import org.futo.inputmethod.latin.uix.settings.useDataStoreValue
import org.futo.inputmethod.latin.uix.settings.userSettingDecorationOnly
import org.futo.inputmethod.latin.uix.settings.userSettingNavigationItem
import org.futo.inputmethod.latin.uix.settings.userSettingToggleDataStore
import java.util.Locale
import kotlin.math.roundToInt

const val LEARNED_WORDS_NAV_PATH = "learnedWords"
const val LEARNED_WORDS_REVIEW_NAV_PATH = "learnedWords/review"

private val autoAddEnabled = @Composable { useDataStoreValue(LearnedWordsAutoAddSetting) }

val LearnedWordsMenu = UserSettingsMenu(
    title = R.string.learned_words_title,
    navPath = LEARNED_WORDS_NAV_PATH, registerNavPath = true,
    settings = listOf(
        userSettingDecorationOnly {
            Tip(stringResource(R.string.learned_words_tip))
        },
        userSettingToggleDataStore(
            title = R.string.learned_words_auto_add,
            subtitle = R.string.learned_words_auto_add_subtitle,
            setting = LearnedWordsAutoAddSetting
        ),
        UserSetting(
            name = R.string.learned_words_auto_add_uses,
            visibilityCheck = autoAddEnabled
        ) {
            SettingSlider(
                title = stringResource(R.string.learned_words_auto_add_uses),
                setting = LearnedWordsAutoAddUses,
                range = 2.0f..10.0f,
                transform = { it.roundToInt() },
                steps = 7,
                subtitle = stringResource(R.string.learned_words_auto_add_uses_subtitle)
            )
        },
        userSettingToggleDataStore(
            title = R.string.learned_words_add_on_pick,
            subtitle = R.string.learned_words_add_on_pick_subtitle,
            setting = LearnedWordsAddOnManualPick
        ).copy(visibilityCheck = autoAddEnabled),
        userSettingToggleDataStore(
            title = R.string.learned_words_language_only,
            subtitle = R.string.learned_words_language_only_subtitle,
            setting = LearnedWordsStoreLanguageOnly
        ),
        userSettingNavigationItem(
            title = R.string.learned_words_review,
            subtitle = R.string.learned_words_review_subtitle,
            style = NavigationItemStyle.Misc,
            navigateTo = LEARNED_WORDS_REVIEW_NAV_PATH
        ),
        UserSetting(name = R.string.learned_words_export) {
            LearnedWordsExportItem()
        },
    )
)

fun NavGraphBuilder.addLearnedWordsNavigation(navController: NavHostController) {
    composable(LEARNED_WORDS_REVIEW_NAV_PATH) { LearnedWordsReviewScreen(navController) }
}

/** Enabled languages plus languages that still have learned words on disk. */
@Composable
internal fun learnedWordsLocales(context: Context): List<Locale> {
    val enabled = useDataStoreValue(SubtypesSetting).map {
        Subtypes.getLocale(Subtypes.convertToSubtype(it))
    }
    return remember(enabled) { (enabled + userHistoryDictionaryLocales(context)).distinct() }
}

@Composable
private fun LearnedWordsExportItem() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val locales = learnedWordsLocales(context)

    var showDialog by remember { mutableStateOf(false) }
    var format by remember { mutableStateOf(LearnedWordsExporter.Format.Json) }
    var includeNgrams by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(format.mimeType)
    ) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val message = withContext(Dispatchers.IO) {
                exportLearnedWords(context, uri, locales, format, includeNgrams)
            }
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }

    NavigationItem(
        title = stringResource(R.string.learned_words_export),
        subtitle = stringResource(R.string.learned_words_export_subtitle),
        style = NavigationItemStyle.MiscNoArrow,
        icon = painterResource(R.drawable.file_text),
        navigate = { showDialog = true }
    )

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(stringResource(R.string.learned_words_export)) },
            text = {
                Column {
                    LearnedWordsExporter.Format.entries.forEach { option ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().clickable { format = option }
                        ) {
                            RadioButton(selected = format == option, onClick = { format = option })
                            Text(stringResource(
                                when (option) {
                                    LearnedWordsExporter.Format.Json -> R.string.learned_words_export_format_json
                                    LearnedWordsExporter.Format.Csv -> R.string.learned_words_export_format_csv
                                }
                            ))
                        }
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clickable { includeNgrams = !includeNgrams }
                    ) {
                        Checkbox(checked = includeNgrams, onCheckedChange = { includeNgrams = it })
                        Text(stringResource(R.string.learned_words_export_include_ngrams))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showDialog = false
                    launcher.launch(LearnedWordsExporter.fileName(format, System.currentTimeMillis()))
                }) { Text(stringResource(R.string.learned_words_export_button)) }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text(stringResource(R.string.learned_words_export_cancel))
                }
            }
        )
    }
}

private fun exportLearnedWords(
    context: Context,
    uri: Uri,
    locales: List<Locale>,
    format: LearnedWordsExporter.Format,
    includeNgrams: Boolean,
): String = try {
    val results = LearnedWordsRepository(context).use { repository ->
        locales.mapNotNull { repository.load(it, includeNgrams) }
    }
    context.contentResolver.openOutputStream(uri)?.use { out ->
        LearnedWordsExporter.write(results, format, out)
    } ?: error("cannot open $uri")
    context.getString(R.string.learned_words_export_done, results.sumOf { it.words.size })
} catch (e: Exception) {
    context.getString(R.string.learned_words_export_failed, e.message ?: e.javaClass.simpleName)
}
