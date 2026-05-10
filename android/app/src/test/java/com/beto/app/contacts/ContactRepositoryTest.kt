package com.beto.app.contacts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ContactRepositoryTest {

    @Test
    fun resolvesSingleMatch() {
        val repository = repository(
            contacts = listOf(contact(1, "Carlos Perez")),
            phones = mapOf(1L to listOf(mobile("+54 9 11 1111-2222"))),
        )

        val result = repository.resolve("Carlos")

        assertEquals(1, result.size)
        assertEquals("Carlos Perez", result.single().displayName)
        assertEquals(PhoneType.MOBILE, result.single().phoneNumbers.single().type)
    }

    @Test
    fun resolvesMultipleMatchesForHomonyms() {
        val repository = repository(
            contacts = listOf(contact(1, "Pedro Gomez"), contact(2, "Pedro Suarez")),
        )

        val result = repository.resolve("Pedro")

        assertEquals(listOf("Pedro Gomez", "Pedro Suarez"), result.map { it.displayName })
    }

    @Test
    fun returnsEmptyListWhenNoMatch() {
        val repository = repository(contacts = emptyList())

        assertTrue(repository.resolve("Carlos").isEmpty())
    }

    @Test
    fun fallsBackToDemoContactsWhenPermissionDenied() {
        val repository = repository(permissionGranted = false)

        val result = repository.resolve("mi nieto")

        assertEquals(1, result.size)
        assertEquals("Mi nieto", result.single().displayName)
        assertTrue(result.single().hasWhatsApp)
    }

    @Test
    fun marksHasWhatsAppTrueWhenWhatsAppMimetypePresent() {
        val repository = repository(
            contacts = listOf(contact(1, "Juan")),
            whatsAppIds = setOf(1),
        )

        assertTrue(repository.resolve("Juan").single().hasWhatsApp)
    }

    @Test
    fun marksHasWhatsAppFalseWhenNoWhatsAppMimetype() {
        val repository = repository(
            contacts = listOf(contact(1, "Juan")),
            whatsAppIds = emptySet(),
        )

        assertFalse(repository.resolve("Juan").single().hasWhatsApp)
    }

    @Test
    fun findByPhoneResolvesByExactMatch() {
        val repository = repository(
            contacts = listOf(contact(1, "Dra Lopez")),
            phones = mapOf(1L to listOf(mobile("+54 9 11 3333-4444"))),
            phoneLookup = mapOf("+54 9 11 3333-4444" to contact(1, "Dra Lopez")),
        )

        val result = repository.findByPhone("+54 9 11 3333-4444")

        assertEquals("Dra Lopez", result?.displayName)
    }

    @Test
    fun findByPhoneReturnsNullWhenLookupIsNotExact() {
        val repository = repository(
            contacts = listOf(contact(1, "Dra Lopez")),
            phones = mapOf(1L to listOf(mobile("+54 9 11 3333-4444"))),
            phoneLookup = mapOf("3333" to contact(1, "Dra Lopez")),
        )

        assertNull(repository.findByPhone("3333"))
    }

    @Test
    fun knownContactNamesReturnsContactsWhenPermissionGranted() {
        val repository = repository(
            contacts = listOf(contact(1, "Juan"), contact(2, "Pedro")),
        )

        assertEquals(listOf("Juan", "Pedro"), repository.knownContactNames())
    }

    private fun repository(
        permissionGranted: Boolean = true,
        contacts: List<ContactRow> = emptyList(),
        phones: Map<Long, List<PhoneNumber>> = emptyMap(),
        whatsAppIds: Set<Long> = emptySet(),
        emailIds: Set<Long> = emptySet(),
        phoneLookup: Map<String, ContactRow> = emptyMap(),
    ): ContactRepository =
        ContactRepository(
            dataSource = FakeContactDataSource(
                contacts = contacts,
                phones = phones,
                whatsAppIds = whatsAppIds,
                emailIds = emailIds,
                phoneLookup = phoneLookup,
            ),
            permissionChecker = { permissionGranted },
        )

    private fun contact(id: Long, displayName: String) = ContactRow(id, displayName)

    private fun mobile(raw: String) = PhoneNumber(raw = raw, e164 = raw.toE164(), type = PhoneType.MOBILE)
}

private class FakeContactDataSource(
    private val contacts: List<ContactRow>,
    private val phones: Map<Long, List<PhoneNumber>>,
    private val whatsAppIds: Set<Long>,
    private val emailIds: Set<Long>,
    private val phoneLookup: Map<String, ContactRow>,
) : ContactDataSource {

    override fun searchByName(name: String): List<ContactRow> =
        contacts.filter { it.displayName.contains(name, ignoreCase = true) }

    override fun listContacts(limit: Int): List<ContactRow> = contacts.take(limit)

    override fun findByPhone(phone: String): ContactRow? = phoneLookup[phone]

    override fun loadPhoneNumbers(contactId: Long): List<PhoneNumber> =
        phones[contactId].orEmpty()

    override fun hasWhatsApp(contactId: Long): Boolean = contactId in whatsAppIds

    override fun hasEmail(contactId: Long): Boolean = contactId in emailIds
}
