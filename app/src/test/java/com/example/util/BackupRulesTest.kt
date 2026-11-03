package com.example.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The backup rules once said "exclude the Firestore cache" in a comment and <include>d it in the XML (with any <include>, only
 * that is backed up). These checks read the real files so the two can't drift again.
 */
class BackupRulesTest {
    private fun res(name: String) = File("src/main/res/xml/$name").readText().replace(Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL), "")

    @Test fun `no backup rule includes anything and every private domain is excluded`() {
        for (f in listOf("backup_rules.xml", "data_extraction_rules.xml")) {
            val xml = res(f)
            assertFalse("$f must not <include>", xml.contains("<include"))
            for (domain in listOf("database", "sharedpref", "file")) {
                assertTrue("$f must exclude $domain", xml.contains("<exclude domain=\"$domain\""))
            }
        }
        val ext = res("data_extraction_rules.xml")
        assertTrue(ext.contains("<cloud-backup>") && ext.contains("<device-transfer>"))
    }

    @Test fun `the manifest turns backup off and uses the network security config`() {
        val manifest = File("src/main/AndroidManifest.xml").readText()
        assertTrue(manifest.contains("android:allowBackup=\"false\""))
        assertTrue(manifest.contains("android:networkSecurityConfig=\"@xml/network_security_config\""))
        assertTrue(manifest.contains("android:usesCleartextTraffic=\"false\""))
    }

    @Test fun `the network security config trusts system certificates only and no cleartext`() {
        val xml = res("network_security_config.xml")
        assertTrue(xml.contains("cleartextTrafficPermitted=\"false\""))
        assertTrue(xml.contains("<certificates src=\"system\""))
        assertFalse(xml.contains("src=\"user\""))
    }
}
