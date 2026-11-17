package com.qqmusictv.data.updates

import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.X509TrustManager
import org.junit.Assert.*
import org.junit.Test

class LegacyUpdateTlsTest {
    @Test fun fallbackIsLimitedToAndroid6AndExactOfficialUpdateEndpoint() {
        assertTrue(needsLegacyUpdateTls("https://update.invalid/update.json", 23))
        for (url in listOf("http://update.invalid/update.json", "https://update.invalid.evil.test/update.json", "https://update-test.update.invalid/update.json", "https://update.invalid:444/update.json", "https://update.invalid/update.json?x=1")) assertFalse(url, needsLegacyUpdateTls(url, 23))
        assertFalse(needsLegacyUpdateTls("https://update.invalid/update.json", 24))
    }
    @Test fun systemTrustPassesAndAbsentPinnedCaFailsClosed() {
        val allow = object : X509TrustManager {
            override fun getAcceptedIssuers() = emptyArray<X509Certificate>()
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
        }
        LegacyUpdateTrustManager(allow).checkServerTrusted(emptyArray(), "RSA")
        val reject = object : X509TrustManager {
            override fun getAcceptedIssuers() = emptyArray<X509Certificate>()
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) { throw CertificateException("reject") }
            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) { throw CertificateException("reject") }
        }
        try { LegacyUpdateTrustManager(reject).checkServerTrusted(emptyArray(), "RSA"); fail("missing anchor must fail") } catch (_: CertificateException) {}
    }
}
