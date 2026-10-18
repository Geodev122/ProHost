package com.example.data.auth

import org.junit.Assert.assertEquals
import org.junit.Test

class VerificationFailureTest {
    private fun kind(code: String? = null, many: Boolean = false, invalid: Boolean = false, activity: Boolean = false, msg: String = "") =
        verificationFailureKind(code, many, invalid, activity, msg)

    @Test
    fun `a blocked SMS region is told apart from a bad number even on an invalid-credentials exception`() {
        assertEquals(VerificationFailure.REGION_BLOCKED, kind(code = "ERROR_OPERATION_NOT_ALLOWED"))
        assertEquals(VerificationFailure.REGION_BLOCKED, kind(invalid = true, msg = "SMS unable to be sent until this region enabled by the app developer."))
        assertEquals(VerificationFailure.INVALID_NUMBER, kind(code = "ERROR_INVALID_PHONE_NUMBER", invalid = true))
        assertEquals(VerificationFailure.INVALID_NUMBER, kind(invalid = true, msg = "The format of the phone number provided is incorrect."))
    }

    @Test
    fun `quota, app verification, network and unknown failures`() {
        assertEquals(VerificationFailure.QUOTA, kind(code = "ERROR_QUOTA_EXCEEDED", invalid = true))
        assertEquals(VerificationFailure.QUOTA, kind(many = true))
        assertEquals(VerificationFailure.APP_NOT_VERIFIED, kind(msg = "This app is not authorized to use Firebase Authentication. [17093]"))
        assertEquals(VerificationFailure.APP_NOT_VERIFIED, kind(code = "ERROR_APP_NOT_AUTHORIZED"))
        assertEquals(VerificationFailure.RECAPTCHA_ACTIVITY, kind(activity = true))
        assertEquals(VerificationFailure.NETWORK, kind(msg = "A network error has occurred"))
        assertEquals(VerificationFailure.OTHER, kind(msg = "something else"))
    }
}
