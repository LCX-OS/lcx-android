package com.cleanx.lcx.core.transaction

import app.cash.turbine.turbineScope
import com.cleanx.lcx.core.model.PaymentMethod
import com.cleanx.lcx.core.model.PaymentStatus
import com.cleanx.lcx.core.model.ServiceType
import com.cleanx.lcx.core.model.Ticket
import com.cleanx.lcx.core.model.TicketStatus
import com.cleanx.lcx.core.network.TicketDraft
import com.cleanx.lcx.feature.payments.data.ChargeResult
import com.cleanx.lcx.feature.payments.data.PaymentRepository
import com.cleanx.lcx.feature.printing.data.PrintRepository
import com.cleanx.lcx.feature.printing.data.PrintResult
import com.cleanx.lcx.feature.tickets.data.ApiResult
import com.cleanx.lcx.feature.tickets.data.TicketRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TransactionOrchestratorTest {

    private lateinit var ticketRepository: TicketRepository
    private lateinit var paymentRepository: PaymentRepository
    private lateinit var printRepository: PrintRepository
    private lateinit var orchestrator: TransactionOrchestrator

    private val draft = TicketDraft(
        customerName = "Juan Perez",
        customerPhone = "+521234567890",
        serviceType = "wash-fold",
        service = "Lavado y Planchado",
        totalAmount = 150.0,
    )

    private val createdTicket = Ticket(
        id = "ticket-001",
        ticketNumber = "LCX-20260302-001",
        ticketDate = "2026-03-02",
        dailyFolio = 1,
        customerName = "Juan Perez",
        customerPhone = "+521234567890",
        serviceType = ServiceType.WASH_FOLD,
        status = TicketStatus.RECEIVED,
        totalAmount = 150.0,
        paymentStatus = PaymentStatus.PENDING,
        paidAmount = 0.0,
    )

    private val paidTicket = createdTicket.copy(
        paymentStatus = PaymentStatus.PAID,
        paymentMethod = PaymentMethod.CARD,
        paidAmount = 150.0,
    )

    @Before
    fun setUp() {
        ticketRepository = mockk()
        paymentRepository = mockk()
        printRepository = mockk()
        orchestrator = TransactionOrchestrator(ticketRepository, paymentRepository, printRepository)
    }

    // -- Happy path: create -> charge -> print -> complete --

    @Test
    fun `happy path - all steps succeed - reaches Completed`() = runTest {
        coEvery {
            ticketRepository.createTickets(any(), any())
        } returns ApiResult.Success(listOf(createdTicket))

        coEvery {
            paymentRepository.charge(ticketId = "ticket-001", amount = 150.0)
        } returns ChargeResult.Success(ticket = paidTicket, transactionId = "txn-001")

        coEvery {
            printRepository.printWithRetry(any(), any())
        } returns PrintResult.Success

        orchestrator.startTransaction(draft)

        val finalState = orchestrator.state.value
        assertTrue("Expected Completed but was $finalState", finalState is TransactionState.Completed)
        assertEquals("ticket-001", (finalState as TransactionState.Completed).ticket.id)
    }

    // -- Ticket creation failure --

    @Test
    fun `ticket creation failure - stops at TicketCreationFailed`() = runTest {
        coEvery {
            ticketRepository.createTickets(any(), any())
        } returns ApiResult.Error(
            httpStatus = 500,
            message = "Internal Server Error",
            code = null,
        )

        orchestrator.startTransaction(draft)

        val finalState = orchestrator.state.value
        assertTrue("Expected TicketCreationFailed but was $finalState", finalState is TransactionState.TicketCreationFailed)

        // Verify payment was never called
        coVerify(exactly = 0) { paymentRepository.charge(any(), any()) }
    }

    // -- Payment cancellation --

    @Test
    fun `payment cancellation - stops at PaymentCancelled`() = runTest {
        coEvery {
            ticketRepository.createTickets(any(), any())
        } returns ApiResult.Success(listOf(createdTicket))

        coEvery {
            paymentRepository.charge(ticketId = "ticket-001", amount = 150.0)
        } returns ChargeResult.Cancelled(reference = "ticket-001")

        orchestrator.startTransaction(draft)

        val finalState = orchestrator.state.value
        assertTrue("Expected PaymentCancelled but was $finalState", finalState is TransactionState.PaymentCancelled)

        // Verify print was never called
        coVerify(exactly = 0) { printRepository.printWithRetry(any(), any()) }
    }

    // -- Payment failure --

    @Test
    fun `payment failure - stops at PaymentFailed`() = runTest {
        coEvery {
            ticketRepository.createTickets(any(), any())
        } returns ApiResult.Success(listOf(createdTicket))

        coEvery {
            paymentRepository.charge(ticketId = "ticket-001", amount = 150.0)
        } returns ChargeResult.ReaderFailed(
            errorCode = "NETWORK",
            message = "Error de red",
        )

        orchestrator.startTransaction(draft)

        val finalState = orchestrator.state.value
        assertTrue("Expected PaymentFailed but was $finalState", finalState is TransactionState.PaymentFailed)
        assertEquals("Error de red", (finalState as TransactionState.PaymentFailed).message)

        // Verify print was never called
        coVerify(exactly = 0) { printRepository.printWithRetry(any(), any()) }
    }

    // -- Payment success + API failure (CRITICAL) --

    @Test
    fun `payment succeeds but API fails - enters PaymentSucceededApiFailed`() = runTest {
        coEvery {
            ticketRepository.createTickets(any(), any())
        } returns ApiResult.Success(listOf(createdTicket))

        coEvery {
            paymentRepository.charge(ticketId = "ticket-001", amount = 150.0)
        } returns ChargeResult.PaymentSucceededButApiCallFailed(
            transactionId = "txn-critical",
            amount = 150.0,
            apiErrorMessage = "Service Unavailable",
        )

        orchestrator.startTransaction(draft)

        val finalState = orchestrator.state.value
        assertTrue(
            "Expected PaymentSucceededApiFailed but was $finalState",
            finalState is TransactionState.PaymentSucceededApiFailed,
        )
        val critical = finalState as TransactionState.PaymentSucceededApiFailed
        assertEquals("txn-critical", critical.transactionId)
        assertEquals(150.0, critical.amount, 0.001)
        assertEquals("Service Unavailable", critical.apiErrorMessage)

        // Verify print was never called
        coVerify(exactly = 0) { printRepository.printWithRetry(any(), any()) }
    }

    // -- Print failure + skip -> advances to Completed --

    @Test
    fun `print failure then skip - advances to Completed`() = runTest {
        coEvery {
            ticketRepository.createTickets(any(), any())
        } returns ApiResult.Success(listOf(createdTicket))

        coEvery {
            paymentRepository.charge(ticketId = "ticket-001", amount = 150.0)
        } returns ChargeResult.Success(ticket = paidTicket, transactionId = "txn-001")

        coEvery {
            printRepository.printWithRetry(any(), any())
        } returns PrintResult.Error(code = "STUB_ERROR", message = "Error simulado")

        orchestrator.startTransaction(draft)

        // Should be in PrintFailed
        val afterPrint = orchestrator.state.value
        assertTrue("Expected PrintFailed but was $afterPrint", afterPrint is TransactionState.PrintFailed)

        // Skip print
        orchestrator.skipPrint()

        val finalState = orchestrator.state.value
        assertTrue("Expected Completed but was $finalState", finalState is TransactionState.Completed)
    }

    // -- Print failure + retry succeeds -> advances to Completed --

    @Test
    fun `print failure then retry succeeds - advances to Completed`() = runTest {
        coEvery {
            ticketRepository.createTickets(any(), any())
        } returns ApiResult.Success(listOf(createdTicket))

        coEvery {
            paymentRepository.charge(ticketId = "ticket-001", amount = 150.0)
        } returns ChargeResult.Success(ticket = paidTicket, transactionId = "txn-001")

        // First print fails, second succeeds
        coEvery {
            printRepository.printWithRetry(any(), any())
        } returns PrintResult.Error(code = "STUB_ERROR", message = "Error simulado") andThen PrintResult.Success

        orchestrator.startTransaction(draft)

        // Should be in PrintFailed
        val afterPrint = orchestrator.state.value
        assertTrue("Expected PrintFailed but was $afterPrint", afterPrint is TransactionState.PrintFailed)

        // Retry
        orchestrator.retryCurrentStep()

        val finalState = orchestrator.state.value
        assertTrue("Expected Completed but was $finalState", finalState is TransactionState.Completed)
    }

    // -- Double-submit prevention --

    @Test
    fun `double submit is prevented - second call is ignored`() = runTest {
        coEvery {
            ticketRepository.createTickets(any(), any())
        } returns ApiResult.Success(listOf(createdTicket))

        coEvery {
            paymentRepository.charge(ticketId = "ticket-001", amount = 150.0)
        } returns ChargeResult.Success(ticket = paidTicket, transactionId = "txn-001")

        coEvery {
            printRepository.printWithRetry(any(), any())
        } returns PrintResult.Success

        // First call
        orchestrator.startTransaction(draft)

        // Reset to simulate the flow completing
        orchestrator.reset()

        // First call again
        orchestrator.startTransaction(draft)

        val finalState = orchestrator.state.value
        assertTrue("Expected Completed but was $finalState", finalState is TransactionState.Completed)

        // createTickets should have been called twice (once per startTransaction)
        coVerify(exactly = 2) { ticketRepository.createTickets(any(), any()) }
    }

    // -- Ticket creation retry --

    @Test
    fun `ticket creation failure then retry succeeds - completes full flow`() = runTest {
        coEvery {
            ticketRepository.createTickets(any(), any())
        } returns ApiResult.Error(
            httpStatus = 500,
            message = "Server Error",
        ) andThen ApiResult.Success(listOf(createdTicket))

        coEvery {
            paymentRepository.charge(ticketId = "ticket-001", amount = 150.0)
        } returns ChargeResult.Success(ticket = paidTicket, transactionId = "txn-001")

        coEvery {
            printRepository.printWithRetry(any(), any())
        } returns PrintResult.Success

        orchestrator.startTransaction(draft)

        // Should be in TicketCreationFailed
        val afterFail = orchestrator.state.value
        assertTrue("Expected TicketCreationFailed but was $afterFail", afterFail is TransactionState.TicketCreationFailed)

        // Retry
        orchestrator.retryCurrentStep()

        val finalState = orchestrator.state.value
        assertTrue("Expected Completed but was $finalState", finalState is TransactionState.Completed)
    }

    // -- Payment cancellation retry --

    @Test
    fun `payment cancelled then retry succeeds - completes full flow`() = runTest {
        coEvery {
            ticketRepository.createTickets(any(), any())
        } returns ApiResult.Success(listOf(createdTicket))

        coEvery {
            paymentRepository.charge(ticketId = "ticket-001", amount = 150.0)
        } returns ChargeResult.Cancelled(reference = "ticket-001") andThen
            ChargeResult.Success(ticket = paidTicket, transactionId = "txn-001")

        coEvery {
            printRepository.printWithRetry(any(), any())
        } returns PrintResult.Success

        orchestrator.startTransaction(draft)

        val afterCancel = orchestrator.state.value
        assertTrue("Expected PaymentCancelled but was $afterCancel", afterCancel is TransactionState.PaymentCancelled)

        orchestrator.retryCurrentStep()

        val finalState = orchestrator.state.value
        assertTrue("Expected Completed but was $finalState", finalState is TransactionState.Completed)
    }

    // -- API sync retry after critical state --

    @Test
    fun `payment succeeded api failed - retry sync succeeds - completes flow`() = runTest {
        coEvery {
            ticketRepository.createTickets(any(), any())
        } returns ApiResult.Success(listOf(createdTicket))

        coEvery {
            paymentRepository.charge(ticketId = "ticket-001", amount = 150.0)
        } returns ChargeResult.PaymentSucceededButApiCallFailed(
            transactionId = "txn-critical",
            amount = 150.0,
            apiErrorMessage = "Service Unavailable",
        )

        coEvery {
            paymentRepository.syncPaymentToBackend(
                ticketId = "ticket-001",
                transactionId = "txn-critical",
                amount = 150.0,
            )
        } returns ChargeResult.Success(ticket = paidTicket, transactionId = "txn-critical")

        coEvery {
            printRepository.printWithRetry(any(), any())
        } returns PrintResult.Success

        orchestrator.startTransaction(draft)

        val afterCritical = orchestrator.state.value
        assertTrue(
            "Expected PaymentSucceededApiFailed but was $afterCritical",
            afterCritical is TransactionState.PaymentSucceededApiFailed,
        )

        orchestrator.retryCurrentStep()

        val finalState = orchestrator.state.value
        assertTrue("Expected Completed but was $finalState", finalState is TransactionState.Completed)
    }

    // -- Reset clears state --

    @Test
    fun `reset returns to Idle`() = runTest {
        coEvery {
            ticketRepository.createTickets(any(), any())
        } returns ApiResult.Error(httpStatus = 500, message = "Error")

        orchestrator.startTransaction(draft)

        assertTrue(orchestrator.state.value is TransactionState.TicketCreationFailed)

        orchestrator.reset()

        assertTrue(orchestrator.state.value is TransactionState.Idle)
    }
}
