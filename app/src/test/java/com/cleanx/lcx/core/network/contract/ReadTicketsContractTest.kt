package com.cleanx.lcx.core.network.contract

import com.cleanx.lcx.feature.tickets.data.ApiResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contract integration tests for Android ticket reads through the PWA API.
 */
class ReadTicketsContractTest : ContractTestBase() {

    @Test
    fun `list tickets requests API list endpoint with limit`() = runTest {
        enqueue("""{"data": [${ContractTestFixtures.ticketJson()}]}""")

        val result = repository.getTickets(limit = 200)
        val request = server.takeRequest()

        assertEquals("GET", request.method)
        assertEquals("/api/tickets?limit=200", request.path)
        assertTrue(result is ApiResult.Success)
        val tickets = (result as ApiResult.Success).data
        assertEquals(1, tickets.size)
        assertEquals(ContractTestFixtures.TICKET_ID, tickets.first().id)
    }

    @Test
    fun `ticket detail requests API detail endpoint`() = runTest {
        enqueue("""{"data": ${ContractTestFixtures.ticketJson()}}""")

        val result = repository.getTicket(ContractTestFixtures.TICKET_ID)
        val request = server.takeRequest()

        assertEquals("GET", request.method)
        assertEquals("/api/tickets/${ContractTestFixtures.TICKET_ID}", request.path)
        assertTrue(result is ApiResult.Success)
        assertEquals(ContractTestFixtures.TICKET_ID, (result as ApiResult.Success).data?.id)
    }

    @Test
    fun `ticket detail 404 preserves nullable detail behavior`() = runTest {
        enqueue(ContractTestFixtures.NOT_FOUND_RESPONSE, code = 404)

        val result = repository.getTicket("missing-ticket")

        assertTrue(result is ApiResult.Success)
        assertEquals(null, (result as ApiResult.Success).data)
    }
}
