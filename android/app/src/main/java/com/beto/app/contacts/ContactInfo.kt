package com.beto.app.contacts

import android.telephony.PhoneNumberUtils

data class ContactInfo(
    val id: Long,
    val displayName: String,
    val phoneNumbers: List<PhoneNumber>,
    val hasWhatsApp: Boolean,
    val hasEmail: Boolean,
)

data class PhoneNumber(
    val raw: String,
    val e164: String,
    val type: PhoneType,
)

enum class PhoneType {
    MOBILE,
    HOME,
    WORK,
    OTHER,
}

fun String.toE164(defaultCountry: String = "AR"): String =
    runCatching { PhoneNumberUtils.formatNumberToE164(this, defaultCountry) }
        .getOrNull()
        ?: this.filterPhoneChars()

private fun String.filterPhoneChars(): String =
    filter { it.isDigit() || it == '+' }
