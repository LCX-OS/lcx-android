package com.cleanx.lcx.core.transaction.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.cleanx.lcx.core.model.PaymentMethod
import com.cleanx.lcx.core.model.PaymentStatus
import com.cleanx.lcx.core.model.ServiceType
import com.cleanx.lcx.core.model.Ticket
import com.cleanx.lcx.core.model.TicketStatus
import com.cleanx.lcx.core.network.TicketDraft
import com.cleanx.lcx.core.transaction.TransactionPhase
import com.cleanx.lcx.core.transaction.TransactionState
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TransactionPersistenceTest {

    private lateinit var database: LcxDatabase
    private lateinit var dao: TransactionDao
    private lateinit var persistence: TransactionPersistence
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        encodeDefaults = true
    }

    private val testDraft = TicketDraft(
        customerName = "Juan Perez",
        customerPhone = "+521234567890",
        serviceType = "wash-fold",
        service = "Lavado y Planchado",
        totalAmount = 150.0,
    )

    private val testTicket = Ticket(
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

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, LcxDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.transactionDao()
        persistence = TransactionPersistence(dao, json)
    }

    @After
    fun tearDown() {
        database.close()
    }

    // -- Save and Load ---

    @Test
    fun `save CreatingTicket and load returns correct phase and draft`() = runTest {
        val state = TransactionState.CreatingTicket
        val id = persistence.save(
            state = state,
            correlationId = "corr-001",
            draft = testDraft,
        )

        val loaded = persistence.loadActiveTransaction()
        assertNotNull(loaded)
        assertEquals(id, loaded!!.id)
        assertEquals("corr-001", loaded.correlationId)
        assertEquals(TransactionPhase.CREATING_TICKET, loaded.phase)
        assertNotNull(loaded.draft)
        assertEquals("Juan Perez", loaded.draft!!.customerName)
    }

    @Test
    fun `save TicketCreated persists ticket data`() = runTest {
        val state = TransactionState.TicketCreated(testTicket)
        persistence.save(
            state = state,
            correlationId = "corr-002",
            draft = testDraft,
        )

        val loaded = persistence.loadActiveTransaction()
        assertNotNull(loaded)
        assertEquals(TransactionPhase.TICKET_CREATED, loaded!!.phase)
        assertNotNull(loaded.ticket)
        assertEquals("ticket-001", loaded.ticket!!.id)
        assertEquals("LCX-20260302-001", loaded.ticket!!.ticketNumber)
    }

    @Test
    fun `save PaymentSucceededApiFailed persists payment context`() = runTest {
        val state = TransactionState.PaymentSucceededApiFailed(
            ticket = testTicket,
            transactionId = "txn-critical",
            amount = 150.0,
            apiErrorMessage = "Service Unavailable",
        )
        persistence.save(
            state = state,
            correlationId = "corr-003",
            draft = testDraft,
        )

        val loaded = persistence.loadActiveTransaction()
        assertNotNull(loaded)
        assertEquals(TransactionPhase.PAYMENT_SUCCEEDED_API_FAILED, loaded!!.phase)
        assertEquals("txn-critical", loaded.paymentTransactionId)
        assertEquals(150.0, loaded.paymentAmount!!, 0.001)
        assertEquals("Service Unavailable", loaded.errorMessage)
    }

    @Test
    fun `save updates existing record when same id is used`() = runTest {
        val id = persistence.save(
            state = TransactionState.CreatingTicket,
            correlationId = "corr-004",
            draft = testDraft,
        )

        // Update the same record
        persistence.save(
            state = TransactionState.TicketCreated(testTicket),
            correlationId = "corr-004",
            draft = testDraft,
            transactionId = id,
        )

        val loaded = persistence.loadActiveTransaction()
        assertNotNull(loaded)
        assertEquals(id, loaded!!.id)
        assertEquals(TransactionPhase.TICKET_CREATED, loaded.phase)
    }

    // -- Mark Completed ---

    @Test
    fun `markCompleted hides record from loadActiveTransaction`() = runTest {
        val id = persistence.save(
            state = TransactionState.CreatingTicket,
            correlationId = "corr-005",
            draft = testDraft,
        )

        persistence.markCompleted(id)

        val loaded = persistence.loadActiveTransaction()
        assertNull(loaded)

        // But it should still be loadable by ID
        val byId = persistence.loadById(id)
        assertNotNull(byId)
        assertEquals(TransactionPhase.COMPLETED, byId!!.phase)
    }

    // -- Mark Cancelled ---

    @Test
    fun `markCancelled hides record from loadActiveTransaction`() = runTest {
        val id = persistence.save(
            state = TransactionState.ChargingPayment(testTicket),
            correlationId = "corr-006",
            draft = testDraft,
        )

        persistence.markCancelled(id)

        val loaded = persistence.loadActiveTransaction()
        assertNull(loaded)

        val byId = persistence.loadById(id)
        assertNotNull(byId)
        assertEquals(TransactionPhase.CANCELLED, byId!!.phase)
    }

    // -- Cleanup ---

    @Test
    fun `cleanup removes old completed records but keeps active ones`() = runTest {
        // Create a completed record
        val completedId = persistence.save(
            state = TransactionState.CreatingTicket,
            correlationId = "corr-old",
            draft = testDraft,
        )
        persistence.markCompleted(completedId)

        // Create an active record
        persistence.save(
            state = TransactionState.ChargingPayment(testTicket),
            correlationId = "corr-active",
            draft = testDraft,
        )

        // Cleanup with 0 maxAge to remove everything completed
        persistence.cleanup(maxAge = 0)

        // Active should still be there
        val active = persistence.loadActiveTransaction()
        assertNotNull(active)
        assertEquals("corr-active", active!!.correlationId)

        // Completed should be gone
        val completed = persistence.loadById(completedId)
        assertNull(completed)
    }

    // -- Load returns most recent active ---

    @Test
    fun `loadActiveTransaction returns most recently updated active record`() = runTest {
        // Save two active transactions
        persistence.save(
            state = TransactionState.CreatingTicket,
            correlationId = "corr-first",
            draft = testDraft,
        )

        // A small pause to ensure different updatedAt
        persistence.save(
            state = TransactionState.ChargingPayment(testTicket),
            correlationId = "corr-second",
            draft = testDraft,
        )

        val loaded = persistence.loadActiveTransaction()
        assertNotNull(loaded)
        assertEquals("corr-second", loaded!!.correlationId)
    }

    // -- All phases can be saved and loaded ---

    @Test
    fun `all non-terminal phases round-trip correctly`() = runTest {
        val states = listOf(
            TransactionState.CreatingTicket to TransactionPhase.CREATING_TICKET,
            TransactionState.TicketCreated(testTicket) to TransactionPhase.TICKET_CREATED,
            TransactionState.TicketCreationFailed("Error", "E001") to TransactionPhase.TICKET_CREATION_FAILED,
            TransactionState.ChargingPayment(testTicket) to TransactionPhase.CHARGING_PAYMENT,
            TransactionState.PaymentCharged(testTicket, "txn-1") to TransactionPhase.PAYMENT_CHARGED,
            TransactionState.PaymentFailed(testTicket, "Fail") to TransactionPhase.PAYMENT_FAILED,
            TransactionState.PaymentCancelled(testTicket) to TransactionPhase.PAYMENT_CANCELLED,
            TransactionState.PrintingLabel(testTicket) to TransactionPhase.PRINTING_LABEL,
            TransactionState.LabelPrinted(testTicket) to TransactionPhase.LABEL_PRINTED,
            TransactionState.PrintFailed(testTicket, "Print error") to TransactionPhase.PRINT_FAILED,
        )

        for ((state, expectedPhase) in states) {
            val id = persistence.save(
                state = state,
                correlationId = "corr-phase-${expectedPhase.name}",
                draft = testDraft,
            )
            val loaded = persistence.loadById(id)
            assertNotNull("Failed to load for phase $expectedPhase", loaded)
            assertEquals(expectedPhase, loaded!!.phase)
        }
    }

    // -- Error data preserved ---

    @Test
    fun `TicketCreationFailed preserves error message and code`() = runTest {
        val state = TransactionState.TicketCreationFailed(
            message = "Duplicate ticket",
            code = "DUPLICATE",
        )
        val id = persistence.save(
            state = state,
            correlationId = "corr-err",
            draft = testDraft,
        )

        val loaded = persistence.loadById(id)
        assertNotNull(loaded)
        assertEquals("Duplicate ticket", loaded!!.errorMessage)
        assertEquals("DUPLICATE", loaded.errorCode)
    }

    @Test
    fun `save with null draft stores empty string`() = runTest {
        val id = persistence.save(
            state = TransactionState.CreatingTicket,
            correlationId = "corr-nodraft",
            draft = null,
        )

        val loaded = persistence.loadById(id)
        assertNotNull(loaded)
        assertNull(loaded!!.draft)
    }
}
