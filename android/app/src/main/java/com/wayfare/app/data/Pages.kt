package com.wayfare.app.data

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Follow the actual response size; a server may cap pages below our request. */
internal suspend fun <T> loadPages(fetch: suspend (Long, Long) -> List<T>): List<T> {
    val rows = mutableListOf<T>()
    while (true) {
        currentCoroutineContext().ensureActive()
        val page = fetch(rows.size.toLong(), rows.size + 499L)
        if (page.isEmpty()) return rows
        rows.addAll(page)
    }
}
