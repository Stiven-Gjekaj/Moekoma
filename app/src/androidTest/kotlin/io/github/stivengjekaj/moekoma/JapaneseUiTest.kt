package io.github.stivengjekaj.moekoma

import android.app.Application
import android.content.res.Configuration
import android.os.LocaleList
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.stivengjekaj.moekoma.core.Book
import io.github.stivengjekaj.moekoma.core.BookFormat
import io.github.stivengjekaj.moekoma.ui.MoekomaRoot
import io.github.stivengjekaj.moekoma.ui.Words
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.Instant
import java.util.Locale
import java.util.UUID

/** The app in Japanese: the words of the screens, the values in a template, and the labels for TalkBack. */
@RunWith(AndroidJUnit4::class)
class JapaneseUiTest {
    @get:Rule val compose = createComposeRule()

    private val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application
    private val root = File(app.cacheDir, "japanese-${UUID.randomUUID()}")

    @After fun backToEnglish() {
        Words.use(app, "en")
        root.deleteRecursively()
    }

    @Test fun showsTheLibraryInJapanese() {
        val container = AppContainer(app, root)
        val book = Book(title = "Night Harbor", format = BookFormat.Comic, fileName = "Night", pageCount = 20, page = 2, lastRead = Instant.now())
        container.model.change { it.copy(books = it.books + book) }

        compose.setContent {
            val japanese = Configuration(LocalConfiguration.current).apply { setLocales(LocaleList(Locale.JAPAN)) }
            CompositionLocalProvider(LocalConfiguration provides japanese) { MoekomaRoot(container) }
        }
        compose.onNodeWithText("読書リスト").assertIsDisplayed()
        compose.onNodeWithText("設定").assertIsDisplayed()
        // "Page 3 of 20" goes through a template, and the title of the book stays as it is.
        compose.onNodeWithText("3 / 20 ページ").assertIsDisplayed()
        compose.onAllNodesWithText("Night Harbor").onFirst().assertIsDisplayed()
        compose.onNodeWithContentDescription("ライブラリを検索").assertExists()
    }
}
