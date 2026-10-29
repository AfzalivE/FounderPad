package com.startupassistant

import com.startupassistant.data.BooksRepository
import com.startupassistant.data.EventRepository
import com.startupassistant.data.HiringRepository
import com.startupassistant.data.InMemoryBooksRepository
import com.startupassistant.data.MockEventRepository
import com.startupassistant.data.MockHiringRepository

/**
 * Process-wide singletons. The UI and the AppFunction service (called by the
 * in-app agent or by the Gemini app) share the same data.
 */
object AppGraph {
    val events: EventRepository by lazy { MockEventRepository() }
    val hiring: HiringRepository by lazy { MockHiringRepository() }
    val books: BooksRepository by lazy { InMemoryBooksRepository() }
}
