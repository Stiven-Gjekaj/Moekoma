package io.github.stivengjekaj.moekoma

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.view.View
import android.widget.RemoteViews
import io.github.stivengjekaj.moekoma.core.Book
import io.github.stivengjekaj.moekoma.core.BookFormat
import io.github.stivengjekaj.moekoma.data.ChapterChecks
import io.github.stivengjekaj.moekoma.ui.Words
import java.io.File

/** The widget of the books that the user reads now, with their covers, as in Asabunko. A tap on a book opens it in the app. */
class ContinueReadingWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = update(context)

    companion object {
        private val cells = listOf(
            Triple(R.id.book0, R.id.cover0, R.id.title0) to R.id.place0,
            Triple(R.id.book1, R.id.cover1, R.id.title1) to R.id.place1,
            Triple(R.id.book2, R.id.cover2, R.id.title2) to R.id.place2,
        )

        /** Draws the widgets again with the books of Continue reading. */
        fun update(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, ContinueReadingWidget::class.java))
            if (ids.isEmpty()) return
            val container = (context.applicationContext as MoekomaApp).container
            val books = container.model.library.value.continueReading.take(3)
            val views = RemoteViews(context.packageName, R.layout.widget_shelf)
            views.setViewVisibility(R.id.empty, if (books.isEmpty()) View.VISIBLE else View.GONE)
            views.setViewVisibility(R.id.books, if (books.isEmpty()) View.GONE else View.VISIBLE)
            cells.forEachIndexed { index, (ids3, place) ->
                val (cell, cover, title) = ids3
                val book = books.getOrNull(index)
                // An empty cell keeps its space, so that the books keep their size.
                views.setViewVisibility(cell, if (book == null) View.INVISIBLE else View.VISIBLE)
                if (book == null) return@forEachIndexed
                views.setTextViewText(title, book.title)
                val where = Words.of(context, placeText(book))
                views.setTextViewText(place, where)
                views.setContentDescription(cell, "${book.title}, $where")
                val bitmap = thumbnail(container.model.cover(book))
                if (bitmap != null) views.setImageViewBitmap(cover, bitmap) else views.setImageViewResource(cover, R.drawable.widget_background)
                val open = Intent(context, MainActivity::class.java).putExtra(ChapterChecks.EXTRA_BOOK, book.id.toString())
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                views.setOnClickPendingIntent(cell, PendingIntent.getActivity(context, book.id.hashCode(), open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            }
            manager.updateAppWidget(ids, views)
        }

        /** "Page 3 of 20" or "Chapter 2 of 9". */
        fun placeText(book: Book): String {
            val unit = if (book.format == BookFormat.Comic || book.format == BookFormat.Pdf) "Page" else "Chapter"
            return "$unit ${book.page + 1} of ${book.pageCount}"
        }

        /** A small cover, because a widget gets its images through a binder with a size limit. */
        private fun thumbnail(file: File): android.graphics.Bitmap? {
            if (!file.isFile) return null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.path, bounds)
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= 240) sample *= 2
            return BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
        }
    }
}
