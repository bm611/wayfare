package com.wayfare.app

import com.wayfare.app.data.loadPages
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class PagesTest {
    @Test fun followsTheActualServerPageSize() = runBlocking {
        val source = (0..1002).toList()
        val offsets = mutableListOf<Long>()
        val rows = loadPages { from, _ ->
            offsets.add(from)
            source.drop(from.toInt()).take(200)
        }
        assertEquals(source, rows)
        assertEquals(listOf(0L, 200L, 400L, 600L, 800L, 1000L, 1003L), offsets)
    }

    @Test fun neverReconcilesAPartialOrCancelledRead() = runBlocking {
        for (failure in listOf(IllegalStateException("offline"), CancellationException("cancelled"))) {
            try {
                loadPages { from, _ -> if (from == 0L) listOf(1) else throw failure }
                fail("Partial data must not reach reconciliation")
            } catch (error: Exception) { assertEquals(failure, error) }
        }
    }
}
