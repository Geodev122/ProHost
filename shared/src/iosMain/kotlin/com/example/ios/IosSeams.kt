package com.example.ios

import com.example.shared.resources.Res
import com.example.shared.resources.allPluralStringResources
import com.example.shared.resources.allStringResources
import com.example.ui.util.AppLanguage
import com.example.ui.util.LocaleNames
import com.example.ui.util.PlatformActions
import com.example.ui.util.SharedText
import com.example.util.FileAccess
import com.example.util.PickedFile
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.getPluralString
import org.jetbrains.compose.resources.getString
import platform.Foundation.*
import platform.UIKit.*

/**
 * iOS implementations of the shared seams (links, share sheet, clipboard, picked files, app language, country names, strings).
 * Installed once by [IosPlatform.installSeams] when the Compose root starts.
 */
object IosPlatformActions : PlatformActions {
    private fun open(url: String) {
        val target = NSURL.URLWithString(url) ?: throw IllegalArgumentException("Not a URL")
        UIApplication.sharedApplication.openURL(target, options = emptyMap<Any?, Any?>(), completionHandler = null)
    }

    override fun openUrl(url: String, newTask: Boolean) = open(url)

    override fun sendMail(mailtoUri: String) = open(mailtoUri)

    override fun openFile(file: PickedFile, mimeType: String) {
        val url = NSURL.URLWithString(file.handle) ?: throw IllegalArgumentException("Not a file URL")
        present(UIActivityViewController(activityItems = listOf(url), applicationActivities = null))
    }

    override fun openAppSettings() = open(UIApplicationOpenSettingsURLString)

    /** iOS keeps per-app language in the app's own Settings page. */
    override fun openAppLanguageSettings() = open(UIApplicationOpenSettingsURLString)

    /** Apple's new-event sheet (EventKitUI); throws when the bridge is missing, like Android without a calendar app. */
    override fun addCalendarEvent(
        chooserTitle: String, title: String, description: String,
        allDayStartUtcMillis: Long, allDayEndUtcMillis: Long, rrule: String?
    ) {
        val bridge = IosPlatform.calendar ?: throw UnsupportedOperationException("No calendar on this device")
        val count = rrule?.substringAfter("COUNT=", "")?.substringBefore(';')?.toIntOrNull() ?: 1
        bridge.presentAllDayEvent(title, description, allDayStartUtcMillis, if (rrule?.contains("FREQ=MONTHLY") == true) count else 1)
    }

    override fun shareText(chooserTitle: String, subject: String, text: String) =
        present(UIActivityViewController(activityItems = listOf(text), applicationActivities = null))

    override fun copyText(label: String, text: String) {
        UIPasteboard.generalPasteboard.string = text
    }

    private fun present(controller: UIViewController) {
        var top = UIApplication.sharedApplication.keyWindow?.rootViewController ?: return
        while (true) top = top.presentedViewController ?: break
        controller.popoverPresentationController?.sourceView = top.view // iPad: the sheet needs an anchor
        top.presentViewController(controller, animated = true, completion = null)
    }
}

/** A [PickedFile] on iOS is a `file://` URL string (from the photo or document picker, or a file the app wrote). */
@OptIn(ExperimentalForeignApi::class)
object IosFileAccess : FileAccess {
    override fun extension(file: PickedFile, fallback: String): String =
        NSURL.URLWithString(file.handle)?.pathExtension?.lowercase()?.takeIf { it.isNotBlank() } ?: fallback

    override suspend fun readBytes(file: PickedFile): ByteArray? =
        NSURL.URLWithString(file.handle)?.let { NSData.dataWithContentsOfURL(it) }?.toByteArray()

    override suspend fun sizeBytes(file: PickedFile): Long? {
        val path = NSURL.URLWithString(file.handle)?.path ?: return null
        val attributes = NSFileManager.defaultManager.attributesOfItemAtPath(path, error = null) ?: return null
        return (attributes[NSFileSize] as? NSNumber)?.longLongValue
    }
}

internal object IosSeams {
    /** The app language like Android's `locales[0].language`: "ar" or "en" (region and script dropped). */
    fun language(): String =
        (NSLocale.preferredLanguages.firstOrNull() as? String)?.substringBefore('-')?.substringBefore('_') ?: ""

    @OptIn(ExperimentalResourceApi::class)
    fun install() {
        IosPlatform.installSeamsOnce {
            PlatformActions.current = IosPlatformActions
            com.example.util.SoundPlayer.current = IosSoundPlayer
            com.example.util.Geocoding.current = IosGeocoding
            com.example.legal.LegalPdfs.current = IosLegalPdfs
            com.example.util.KeyValueStores.open = { name -> IosKeyValueStore(name) }
            FileAccess.current = IosFileAccess
            // App Review 2.3.10: shared texts that name Google Play read as the App Store on iPhone.
            com.example.data.billing.StoreBrand.current = com.example.data.billing.AppStoreKind.APP_STORE
            AppLanguage.current = { language() }
            LocaleNames.country = { iso ->
                NSLocale(localeIdentifier = language()).displayNameForKey(NSLocaleCountryCode, iso) ?: ""
            }
            // Same contract as Android's Resources lookup by name: "" when the key is unknown. Compose resources read the
            // bundled XML through a suspend API; the reads are local and cached, so they run synchronously here.
            SharedText.lookup = { name, args ->
                val res = Res.allStringResources[name]
                if (res == null) "" else com.example.data.billing.StoreBrand.text(runBlocking { if (args.isEmpty()) getString(res) else getString(res, *args.map { it ?: "" }.toTypedArray()) })
            }
            SharedText.plural = { name, count, args ->
                val res = Res.allPluralStringResources[name]
                if (res == null) "" else com.example.data.billing.StoreBrand.text(runBlocking { getPluralString(res, count, *args.map { it ?: "" }.toTypedArray()) })
            }
        }
    }
}
