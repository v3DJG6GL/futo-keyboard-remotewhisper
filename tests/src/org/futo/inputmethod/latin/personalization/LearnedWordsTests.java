package org.futo.inputmethod.latin.personalization;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.futo.inputmethod.latin.BinaryDictionary;
import org.futo.inputmethod.latin.NgramContext;
import org.futo.inputmethod.latin.UserHistoryDictionaryReader;
import org.futo.inputmethod.latin.makedict.WordProperty;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;
import java.util.Locale;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class LearnedWordsTests {
    private static final int TIMESTAMP = 1000;

    private Context getContext() {
        return InstrumentationRegistry.getTargetContext();
    }

    @Before
    public void setUp() {
        UserHistoryDictionaryTestsHelper.removeAllTestDictFiles(
                UserHistoryDictionaryTestsHelper.TEST_LOCALE_PREFIX, getContext());
    }

    @After
    public void tearDown() {
        UserHistoryDictionaryTestsHelper.removeAllTestDictFiles(
                UserHistoryDictionaryTestsHelper.TEST_LOCALE_PREFIX, getContext());
    }

    private UserHistoryDictionary newDictionary(final String name) {
        final Locale locale = UserHistoryDictionaryTestsHelper.getFakeLocale(name);
        final UserHistoryDictionary dict =
                PersonalizationHelper.getUserHistoryDictionary(getContext(), locale, null);
        dict.waitAllTasksForTests();
        return dict;
    }

    private static void learn(final UserHistoryDictionary dict, final String word,
            final boolean isValid, final int times) {
        final NgramContext ngramContext = NgramContext.getEmptyPrevWordsContext(
                BinaryDictionary.MAX_PREV_WORD_COUNT_FOR_N_GRAM);
        for (int i = 0; i < times; i++) {
            UserHistoryDictionary.addToDictionary(dict, ngramContext, word, isValid, TIMESTAMP);
        }
    }

    @Test
    public void testCountOfUnknownWordIsUsesMinusOne() {
        final UserHistoryDictionary dict = newDictionary("unknown_count");

        learn(dict, "zwurbelix", false /* isValid */, 1);
        assertNull(UserHistoryDictionaryReader.INSTANCE.getCount(dict, "zwurbelix"));

        learn(dict, "zwurbelix", false /* isValid */, 3);
        assertEquals(Integer.valueOf(3), UserHistoryDictionaryReader.INSTANCE.getCount(dict, "zwurbelix"));
    }

    @Test
    public void testCountOfKnownWordIsUses() {
        final UserHistoryDictionary dict = newDictionary("known_count");

        learn(dict, "hello", true /* isValid */, 2);
        assertEquals(Integer.valueOf(2), UserHistoryDictionaryReader.INSTANCE.getCount(dict, "hello"));
        assertNull(UserHistoryDictionaryReader.INSTANCE.getCount(dict, "absent"));
    }

    @Test
    public void testGetAllWordPropertiesListsLearnedWords() {
        final UserHistoryDictionary dict = newDictionary("all_words");
        learn(dict, "hello", true /* isValid */, 1);
        learn(dict, "zwurbelix", false /* isValid */, 2);

        final List<WordProperty> properties =
                UserHistoryDictionaryReader.INSTANCE.getAllWordProperties(dict);
        assertNotNull(properties);
        boolean foundHello = false;
        boolean foundZwurbelix = false;
        for (final WordProperty property : properties) {
            if (property.mWord.equals("hello")) foundHello = true;
            if (property.mWord.equals("zwurbelix")) foundZwurbelix = true;
        }
        assertTrue(foundHello);
        assertTrue(foundZwurbelix);
    }

    @Test
    public void testShouldAdd() {
        final PersonalDictionaryAutoAdd.Companion autoAdd = PersonalDictionaryAutoAdd.Companion;
        // Unknown words record uses - 1: count 3 means typed 4 times.
        assertFalse(autoAdd.shouldAdd(null, 4, false, true));
        assertFalse(autoAdd.shouldAdd(2, 4, false, true));
        assertTrue(autoAdd.shouldAdd(3, 4, false, true));
        // A manual pick adds right away with a single language...
        assertTrue(autoAdd.shouldAdd(null, 4, true, true));
        // ...but with several languages only once the word was recorded in that language.
        assertFalse(autoAdd.shouldAdd(null, 4, true, false));
        assertTrue(autoAdd.shouldAdd(1, 4, true, false));
    }

    @Test
    public void testIsPlausiblePersonalDictionaryWord() {
        assertTrue(LearnedWordsKt.isPlausiblePersonalDictionaryWord("zwurbelix"));
        assertTrue(LearnedWordsKt.isPlausiblePersonalDictionaryWord("Grüezi"));
        assertTrue(LearnedWordsKt.isPlausiblePersonalDictionaryWord("e-mail"));
        assertTrue(LearnedWordsKt.isPlausiblePersonalDictionaryWord("l'avion"));
        assertFalse(LearnedWordsKt.isPlausiblePersonalDictionaryWord("a"));
        assertFalse(LearnedWordsKt.isPlausiblePersonalDictionaryWord("42"));
        assertFalse(LearnedWordsKt.isPlausiblePersonalDictionaryWord("abc123"));
        assertFalse(LearnedWordsKt.isPlausiblePersonalDictionaryWord("two words"));
        assertFalse(LearnedWordsKt.isPlausiblePersonalDictionaryWord("😀"));
    }

    @Test
    public void testPersonalDictionaryLocale() {
        final Locale swissGerman = new Locale("de", "CH");
        assertEquals("de_CH", LearnedWordsKt.personalDictionaryLocale(swissGerman, false).toString());
        assertEquals("de", LearnedWordsKt.personalDictionaryLocale(swissGerman, true).toString());
    }

    @Test
    public void testCsvField() {
        assertEquals("plain", LearnedWordsExporter.INSTANCE.csvField("plain"));
        assertEquals("\"a,b\"", LearnedWordsExporter.INSTANCE.csvField("a,b"));
        assertEquals("\"say \"\"hi\"\"\"", LearnedWordsExporter.INSTANCE.csvField("say \"hi\""));
    }
}
