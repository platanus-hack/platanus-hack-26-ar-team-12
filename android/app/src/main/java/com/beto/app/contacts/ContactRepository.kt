package com.beto.app.contacts

import android.Manifest
import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import com.beto.app.action.DemoContacts

class ContactRepository(
    private val dataSource: ContactDataSource,
    private val permissionChecker: () -> Boolean,
) {
    constructor(context: Context) : this(
        dataSource = AndroidContactDataSource(context.contentResolver),
        permissionChecker = {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_CONTACTS,
            ) == PackageManager.PERMISSION_GRANTED
        },
    )

    fun hasPermission(): Boolean = permissionChecker()

    fun resolve(name: String): List<ContactInfo> {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return emptyList()
        if (!hasPermission()) return demoFallback(trimmed)

        return dataSource.searchByName(trimmed).map { it.toContactInfo(dataSource) }
    }

    fun findByPhone(phone: String): ContactInfo? {
        val normalizedQuery = phone.toE164().digitsOnly()
        if (normalizedQuery.isEmpty()) return null
        if (!hasPermission()) {
            return DemoContacts.all.firstOrNull { it.e164.digitsOnly() == normalizedQuery }
                ?.toContactInfo()
        }

        return dataSource.findByPhone(phone)
            ?.toContactInfo(dataSource)
            ?.takeIf { contact ->
                contact.phoneNumbers.any { number ->
                    number.e164.digitsOnly() == normalizedQuery ||
                        number.raw.digitsOnly() == normalizedQuery
                }
            }
    }

    fun knownContactNames(limit: Int = 100): List<String> {
        if (!hasPermission()) return DemoContacts.all.map { it.canonicalName }
        return dataSource.listContacts(limit).map { it.displayName }
    }

    private fun demoFallback(name: String): List<ContactInfo> =
        DemoContacts.resolve(name)?.let { listOf(it.toContactInfo()) }.orEmpty()

    private fun ContactRow.toContactInfo(dataSource: ContactDataSource): ContactInfo =
        ContactInfo(
            id = id,
            displayName = displayName,
            phoneNumbers = dataSource.loadPhoneNumbers(id),
            hasWhatsApp = dataSource.hasWhatsApp(id),
            hasEmail = dataSource.hasEmail(id),
        )

    private fun com.beto.app.action.DemoContact.toContactInfo(): ContactInfo =
        ContactInfo(
            id = -canonicalName.hashCode().toLong(),
            displayName = canonicalName,
            phoneNumbers = listOf(
                PhoneNumber(
                    raw = e164,
                    e164 = e164.toE164(),
                    type = PhoneType.MOBILE,
                ),
            ),
            hasWhatsApp = true,
            hasEmail = false,
        )
}

data class ContactRow(
    val id: Long,
    val displayName: String,
)

interface ContactDataSource {
    fun searchByName(name: String): List<ContactRow>
    fun listContacts(limit: Int): List<ContactRow>
    fun findByPhone(phone: String): ContactRow?
    fun loadPhoneNumbers(contactId: Long): List<PhoneNumber>
    fun hasWhatsApp(contactId: Long): Boolean
    fun hasEmail(contactId: Long): Boolean
}

private class AndroidContactDataSource(
    private val contentResolver: ContentResolver,
) : ContactDataSource {

    override fun searchByName(name: String): List<ContactRow> {
        val uri = Uri.withAppendedPath(
            ContactsContract.Contacts.CONTENT_FILTER_URI,
            Uri.encode(name),
        )
        return contentResolver.query(
            uri,
            arrayOf(
                ContactsContract.Contacts._ID,
                ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
            ),
            null,
            null,
            ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
        ).useRows { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(ContactsContract.Contacts._ID)
                    val displayName = cursor.getString(
                        ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
                    ).orEmpty()
                    if (displayName.isNotBlank()) add(ContactRow(id, displayName))
                }
            }
        }
    }

    override fun findByPhone(phone: String): ContactRow? {
        val uri = Uri.withAppendedPath(
            ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
            Uri.encode(phone),
        )
        return contentResolver.query(
            uri,
            arrayOf(
                ContactsContract.PhoneLookup.CONTACT_ID,
                ContactsContract.PhoneLookup.DISPLAY_NAME,
            ),
            null,
            null,
            null,
        ).useRows { cursor ->
            if (!cursor.moveToFirst()) return@useRows null
            ContactRow(
                id = cursor.getLong(ContactsContract.PhoneLookup.CONTACT_ID),
                displayName = cursor.getString(ContactsContract.PhoneLookup.DISPLAY_NAME).orEmpty(),
            )
        }
    }

    override fun listContacts(limit: Int): List<ContactRow> =
        contentResolver.query(
            ContactsContract.Contacts.CONTENT_URI,
            arrayOf(
                ContactsContract.Contacts._ID,
                ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
            ),
            null,
            null,
            ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
        ).useRows { cursor ->
            buildList {
                while (cursor.moveToNext() && size < limit) {
                    val displayName = cursor.getString(
                        ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
                    ).orEmpty()
                    if (displayName.isNotBlank()) {
                        add(ContactRow(cursor.getLong(ContactsContract.Contacts._ID), displayName))
                    }
                }
            }
        }

    override fun loadPhoneNumbers(contactId: Long): List<PhoneNumber> =
        contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.TYPE,
            ),
            "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
            arrayOf(contactId.toString()),
            null,
        ).useRows { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    val raw = cursor.getString(ContactsContract.CommonDataKinds.Phone.NUMBER)
                        .orEmpty()
                    if (raw.isBlank()) continue
                    add(
                        PhoneNumber(
                            raw = raw,
                            e164 = raw.toE164(),
                            type = cursor.getInt(
                                ContactsContract.CommonDataKinds.Phone.TYPE,
                            ).toPhoneType(),
                        ),
                    )
                }
            }
        }

    override fun hasWhatsApp(contactId: Long): Boolean =
        hasDataMimeType(contactId, WHATSAPP_MIMETYPE)

    override fun hasEmail(contactId: Long): Boolean =
        hasDataMimeType(contactId, ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE)

    private fun hasDataMimeType(contactId: Long, mimeType: String): Boolean =
        contentResolver.query(
            ContactsContract.Data.CONTENT_URI,
            arrayOf(ContactsContract.Data._ID),
            "${ContactsContract.Data.CONTACT_ID} = ? AND ${ContactsContract.Data.MIMETYPE} = ?",
            arrayOf(contactId.toString(), mimeType),
            null,
        ).useRows { cursor -> cursor.moveToFirst() }

    private fun Int.toPhoneType(): PhoneType =
        when (this) {
            ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE -> PhoneType.MOBILE
            ContactsContract.CommonDataKinds.Phone.TYPE_HOME -> PhoneType.HOME
            ContactsContract.CommonDataKinds.Phone.TYPE_WORK -> PhoneType.WORK
            else -> PhoneType.OTHER
        }

    companion object {
        private const val WHATSAPP_MIMETYPE = "vnd.android.cursor.item/vnd.com.whatsapp.profile"
    }
}

private inline fun <T> Cursor?.useRows(block: (Cursor) -> T): T {
    if (this == null) return block(EmptyCursor)
    return use(block)
}

private fun Cursor.getLong(columnName: String): Long =
    getLong(getColumnIndexOrThrow(columnName))

private fun Cursor.getInt(columnName: String): Int =
    getInt(getColumnIndexOrThrow(columnName))

private fun Cursor.getString(columnName: String): String? =
    getString(getColumnIndexOrThrow(columnName))

private fun String.digitsOnly(): String = filter(Char::isDigit)

private object EmptyCursor : Cursor by android.database.MatrixCursor(emptyArray<String>())
