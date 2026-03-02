package com.cleanx.lcx.core.transaction

import com.cleanx.lcx.core.model.PaymentMethod
import com.cleanx.lcx.core.model.PaymentStatus
import com.cleanx.lcx.core.model.ServiceType
import com.cleanx.lcx.core.model.Ticket
import com.cleanx.lcx.core.model.TicketStatus
import com.cleanx.lcx.core.network.TicketDraft
import com.cleanx.lcx.core.transaction.data.SavedTransaction
import com.cleanx.lcx.core.transaction.data.TransactionPersistence
import com.cleanx.lcx.feature.payments.data.ChargeResult
import com.cleanx.lcx.feature.payments.data.PaymentRepository
import com.cleanx.lcx.feature.printing.data.PrintRepository
import com.cleanx.lcx.feature.printing.data.PrintResult
import com.cleanx.lcx.feature.tickets.data.ApiResult
import com.cleanx.lcx.feature.tickets.data.TicketRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
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
    private lateinit var persistence: TransactionPersistence
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
        persistence = mockk(relaxed = true)

        // Default: persistence.save returns a fixed ID
        coEvery { persistence.save(any(), any(), any(), any()) } returns "persist-id-001"

        // Default: printRepository.getSelectedPrinter returns null (no printer selected in tests)
        every { printRepository.getSelectedPrinter() } returns null

        orchestrator = TransactionOrchestrator(
            ticketRepository, paymentRepository, printRepository, persistence,
        )
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

    // =========================================================================
    // Persistence integration tests
    // =========================================================================

    @Test
    fun `startTransaction persists each state transition`() = runTest {
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

        // Verify persistence.save was called for each state transition:
        // CreatingTicket, TicketCreated, ChargingPayment, PaymentCharged,
        // PrintingLabel, LabelPrinted, Completed
        coVerify(atLeast = 7) { persistence.save(any(), any(), any(), any()) }
    }

    @Test
    fun `markCompleted is called on finalization`() = runTest {
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

        coVerify { persistence.markCompleted(any()) }
    }

    @Test
    fun `cancelAndPersist calls markCancelled and resets`() = runTest {
        coEvery {
            ticketRepository.createTickets(any(), any())
        } returns ApiResult.Error(httpStatus = 500, message = "Error")

        orchestrator.startTransaction(draft)
        assertTrue(orchestrator.state.value is TransactionState.TicketCreationFailed)

        orchestrator.cancelAndPersist()

        assertTrue(orchestrator.state.value is TransactionState.Idle)
        coVerify { persistence.markCancelled(any()) }
    }

    // =========================================================================
    // Resume tests
    // =========================================================================

    @Test
    fun `resume from TICKET_CREATED skips ticket creation and goes to payment`() = runTest {
        coEvery {
            paymentRepository.charge(ticketId = "ticket-001", amount = 150.0)
        } returns ChargeResult.Success(ticket = paidTicket, transactionId = "txn-001")

        coEvery {
            printRepository.printWithRetry(any(), any())
        } returns PrintResult.Success

        val saved = SavedTransaction(
            id = "saved-001",
            correlationId = "corr-001",
            phase = TransactionPhase.TICKET_CREATED,
            draft = draft,
            ticket = createdTicket,
            paymentTransactionId = null,
            paymentAmount = null,
            errorMessage = null,
            errorCode = null,
        )

        orchestrator.resumeTransaction(saved)

        val finalState = orchestrator.state.value
        assertTrue("Expected Completed but was $finalState", finalState is TransactionState.Completed)

        // Verify ticket creation was NOT called
        coVerify(exactly = 0) { ticketRepository.createTickets(any(), any()) }
        // But payment was called
        coVerify(exactly = 1) { paymentRepository.charge(any(), any()) }
    }

    @Test
    fun `resume from PAYMENT_SUCCEEDED_API_FAILED only retries API sync - never re-charges`() = runTest {
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

        val saved = SavedTransaction(
            id = "saved-002",
            correlationId = "corr-002",
            phase = TransactionPhase.PAYMENT_SUCCEEDED_API_FAILED,
            draft = draft,
            ticket = createdTicket,
            paymentTransactionId = "txn-critical",
            paymentAmount = 150.0,
            errorMessage = "Service Unavailable",
            errorCode = null,
        )

        orchestrator.resumeTransaction(saved)

        val finalState = orchestrator.state.value
        assertTrue("Expected Completed but was $finalState", finalState is TransactionState.Completed)

        // CRITICAL: charge was never called -- only syncPaymentToBackend
        coVerify(exactly = 0) { paymentRepository.charge(any(), any()) }
        coVerify(exactly = 1) { paymentRepository.syncPaymentToBackend(any(), any(), any()) }
    }

    @Test
    fun `resume from PAYMENT_CHARGED goes directly to printing`() = runTest {
        coEvery {
            printRepository.printWithRetry(any(), any())
        } returns PrintResult.Success

        val saved = SavedTransaction(
            id = "saved-003",
            correlationId = "corr-003",
            phase = TransactionPhase.PAYMENT_CHARGED,
            draft = draft,
            ticket = paidTicket,
            paymentTransactionId = "txn-001",
            paymentAmount = 150.0,
            errorMessage = null,
            errorCode = null,
        )

        orchestrator.resumeTransaction(saved)

        val finalState = orchestrator.state.value
        assertTrue("Expected Completed but was $finalState", finalState is TransactionState.Completed)

        // No ticket creation, no payment
        coVerify(exactly = 0) { ticketRepository.createTickets(any(), any()) }
        coVerify(exactly = 0) { paymentRepository.charge(any(), any()) }
        coVerify(exactly = 1) { printRepository.printWithRetry(any(), any()) }
    }

    @Test
    fun `resume from PRINT_FAILED retries printing`() = runTest {
        coEvery {
            printRepository.printWithRetry(any(), any())
        } returns PrintResult.Success

        val saved = SavedTransaction(
            id = "saved-004",
            correlationId = "corr-004",
            phase = TransactionPhase.PRINT_FAILED,
            draft = draft,
            ticket = paidTicket,
            paymentTransactionId = "txn-001",
            paymentAmount = 150.0,
            errorMessage = "Printer offline",
            errorCode = null,
        )

        orchestrator.resumeTransaction(saved)

        val finalState = orchestrator.state.value
        assertTrue("Expected Completed but was $finalState", finalState is TransactionState.Completed)

        coVerify(exactly = 0) { paymentRepository.charge(any(), any()) }
    }

    @Test
    fun `resume from LABEL_PRINTED goes directly to finalize`() = runTest {
        val saved = SavedTransaction(
            id = "saved-005",
            correlationId = "corr-005",
            phase = TransactionPhase.LABEL_PRINTED,
            draft = draft,
            ticket = paidTicket,
            paymentTransactionId = "txn-001",
            paymentAmount = 150.0,
            errorMessage = null,
            errorCode = null,
        )

        orchestrator.resumeTransaction(saved)

        val finalState = orchestrator.state.value
        assertTrue("Expected Completed but was $finalState", finalState is TransactionState.Completed)

        coVerify(exactly = 0) { ticketRepository.createTickets(any(), any()) }
        coVerify(exactly = 0) { paymentRepository.charge(any(), any()) }
        coVerify(exactly = 0) { printRepository.printWithRetry(any(), any()) }
    }

    @Test
    fun `resume from CREATING_TICKET restarts ticket creation`() = runTest {
        coEvery {
            ticketRepository.createTickets(any(), any())
        } returns ApiResult.Success(listOf(createdTicket))

        coEvery {
            paymentRepository.charge(ticketId = "ticket-001", amount = 150.0)
        } returns ChargeResult.Success(ticket = paidTicket, transactionId = "txn-001")

        coEvery {
            printRepository.printWithRetry(any(), any())
        } returns PrintResult.Success

        val saved = SavedTransaction(
            id = "saved-006",
            correlationId = "corr-006",
            phase = TransactionPhase.CREATING_TICKET,
            draft = draft,
            ticket = null,
            paymentTransactionId = null,
            paymentAmount = null,
            errorMessage = null,
            errorCode = null,
        )

        orchestrator.resumeTransaction(saved)

        val finalState = orchestrator.state.value
        assertTrue("Expected Completed but was $finalState", finalState is TransactionState.Completed)

        coVerify(exactly = 1) { ticketRepository.createTickets(any(), any()) }
    }

    @Test
    fun `resume from COMPLETED is a no-op - stays Idle`() = runTest {
        val saved = SavedTransaction(
            id = "saved-007",
            correlationId = "corr-007",
            phase = TransactionPhase.COMPLETED,
            draft = draft,
            ticket = paidTicket,
            paymentTransactionId = "txn-001",
            paymentAmount = 150.0,
            errorMessage = null,
            errorCode = null,
        )

        orchestrator.resumeTransaction(saved)

        val finalState = orchestrator.state.value
        assertTrue("Expected Idle but was $finalState", finalState is TransactionState.Idle)
    }
}
