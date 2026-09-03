package com.myai.offline.actions

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.AlarmClock
import android.provider.ContactsContract
import android.provider.MediaStore
import android.provider.Settings
import com.myai.offline.data.model.AssistantAction
import com.myai.offline.data.model.AssistantActionType
import java.net.URLEncoder

class AndroidActionHandler(private val context: Context) {

    data class ExecutionOutcome(
        val success: Boolean,
        val message: String,
        val appLaunched: String? = null
    )

    /**
     * Executes the validated action safely using explicit/implicit Android Intents.
     */
    fun execute(action: AssistantAction): ExecutionOutcome {
        val validation = ActionValidator.validate(action)
        if (validation is ActionValidator.ValidationResult.Invalid) {
            return ExecutionOutcome(
                success = false,
                message = "Action validation failed: ${validation.reason}"
            )
        }

        return try {
            when (action.type) {
                AssistantActionType.OPEN_YOUTUBE -> openYouTube()
                AssistantActionType.SEARCH_YOUTUBE -> searchYouTube(action.query ?: "")
                AssistantActionType.OPEN_CHROME -> openChrome()
                AssistantActionType.OPEN_SETTINGS -> openSettings()
                AssistantActionType.OPEN_URL -> openUrl(action.url ?: "")
                AssistantActionType.OPEN_APP -> openSpecificApp(action.appName ?: "")
                AssistantActionType.MAKE_CALL -> makePhoneCall(action.phoneNumber ?: "")
                AssistantActionType.SEND_SMS -> sendSmsMessage(action.phoneNumber ?: "", action.messageText ?: "")
            }
        } catch (e: Exception) {
            ExecutionOutcome(
                success = false,
                message = "Failed to dispatch Android Intent: ${e.localizedMessage ?: e.message}"
            )
        }
    }

    private fun openYouTube(): ExecutionOutcome {
        val pm = context.packageManager
        val youtubeIntent = pm.getLaunchIntentForPackage("com.google.android.youtube")
        return if (youtubeIntent != null) {
            youtubeIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(youtubeIntent)
            ExecutionOutcome(true, "Opening YouTube app.", "YouTube")
        } else {
            // Fallback to web URL in browser
            val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com"))
            webIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(webIntent)
            ExecutionOutcome(true, "YouTube app not found. Opened YouTube in web browser.", "Browser")
        }
    }

    private fun searchYouTube(query: String): ExecutionOutcome {
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val appUri = Uri.parse("vnd.youtube://results?search_query=$encodedQuery")
        val webUri = Uri.parse("https://www.youtube.com/results?search_query=$encodedQuery")

        val appIntent = Intent(Intent.ACTION_VIEW, appUri).apply {
            setPackage("com.google.android.youtube")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        return if (isIntentAvailable(appIntent)) {
            context.startActivity(appIntent)
            ExecutionOutcome(true, "Searching for \"$query\" on YouTube app.", "YouTube")
        } else {
            val webIntent = Intent(Intent.ACTION_VIEW, webUri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(webIntent)
            ExecutionOutcome(true, "Searching for \"$query\" on YouTube via web browser.", "Browser")
        }
    }

    private fun openChrome(): ExecutionOutcome {
        val pm = context.packageManager
        val chromeIntent = pm.getLaunchIntentForPackage("com.android.chrome")
        return if (chromeIntent != null) {
            chromeIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chromeIntent)
            ExecutionOutcome(true, "Opening Google Chrome.", "Chrome")
        } else {
            val defaultBrowserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(defaultBrowserIntent)
            ExecutionOutcome(true, "Chrome not installed. Opened default browser.", "Browser")
        }
    }

    private fun openSettings(): ExecutionOutcome {
        val intent = Intent(Settings.ACTION_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        return ExecutionOutcome(true, "Opening Android system settings.", "Settings")
    }

    private fun openUrl(urlStr: String): ExecutionOutcome {
        val validUrl = if (!urlStr.startsWith("http://") && !urlStr.startsWith("https://")) {
            "https://$urlStr"
        } else urlStr

        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(validUrl)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        return ExecutionOutcome(true, "Opened link: $validUrl", "Browser")
    }

    private fun openSpecificApp(appName: String): ExecutionOutcome {
        val pm = context.packageManager
        val cleanName = appName.trim().lowercase()
            .removePrefix("the ")
            .removeSuffix(" app")
            .trim()

        // 1. Check direct common aliases
        when (cleanName) {
            "youtube", "yt" -> return openYouTube()
            "chrome", "google chrome", "browser", "internet" -> return openChrome()
            "settings", "phone settings", "system settings" -> return openSettings()
            "camera" -> {
                val cameraIntent = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                if (isIntentAvailable(cameraIntent)) {
                    context.startActivity(cameraIntent)
                    return ExecutionOutcome(true, "Opening Camera.", "Camera")
                }
            }
            "photos", "gallery" -> {
                val galleryIntent = Intent(Intent.ACTION_VIEW, MediaStore.Images.Media.EXTERNAL_CONTENT_URI).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                if (isIntentAvailable(galleryIntent)) {
                    context.startActivity(galleryIntent)
                    return ExecutionOutcome(true, "Opening Gallery.", "Gallery")
                }
            }
            "clock", "alarm", "timer" -> {
                val clockIntent = Intent(AlarmClock.ACTION_SHOW_ALARMS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                if (isIntentAvailable(clockIntent)) {
                    context.startActivity(clockIntent)
                    return ExecutionOutcome(true, "Opening Clock.", "Clock")
                }
            }
            "contacts", "address book" -> {
                val contactsIntent = Intent(Intent.ACTION_VIEW, ContactsContract.Contacts.CONTENT_URI).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                if (isIntentAvailable(contactsIntent)) {
                    context.startActivity(contactsIntent)
                    return ExecutionOutcome(true, "Opening Contacts.", "Contacts")
                }
            }
            "phone", "dialer" -> {
                val dialerIntent = Intent(Intent.ACTION_DIAL).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(dialerIntent)
                return ExecutionOutcome(true, "Opening Phone Dialer.", "Phone")
            }
            "maps", "google maps" -> {
                val mapsIntent = pm.getLaunchIntentForPackage("com.google.android.apps.maps") ?: Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("geo:0,0?q=")
                ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                mapsIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(mapsIntent)
                return ExecutionOutcome(true, "Opening Google Maps.", "Maps")
            }
            "messages", "sms", "messaging" -> {
                val messagingIntent = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_APP_MESSAGING)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                if (isIntentAvailable(messagingIntent)) {
                    context.startActivity(messagingIntent)
                    return ExecutionOutcome(true, "Opening Messages.", "Messages")
                }
            }
            "email", "gmail", "mail" -> {
                val emailIntent = pm.getLaunchIntentForPackage("com.google.android.gm") ?: Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_APP_EMAIL)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                emailIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(emailIntent)
                return ExecutionOutcome(true, "Opening Email.", "Email")
            }
        }

        // 2. Map known third-party app packages
        val wellKnownPackages = mapOf(
            "whatsapp" to "com.whatsapp",
            "instagram" to "com.instagram.android",
            "facebook" to "com.facebook.katana",
            "twitter" to "com.twitter.android",
            "x" to "com.twitter.android",
            "spotify" to "com.spotify.music",
            "telegram" to "org.telegram.messenger",
            "snapchat" to "com.snapchat.android",
            "tiktok" to "com.zhiliaoapp.musically",
            "netflix" to "com.netflix.mediaclient",
            "calculator" to "com.google.android.calculator",
            "play store" to "com.android.vending",
            "google play" to "com.android.vending",
            "files" to "com.google.android.apps.nbu.files",
            "drive" to "com.google.android.apps.docs",
            "google drive" to "com.google.android.apps.docs"
        )

        val knownPkg = wellKnownPackages[cleanName]
        if (knownPkg != null) {
            val intent = pm.getLaunchIntentForPackage(knownPkg)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                return ExecutionOutcome(true, "Opening $appName.", appName)
            }
        }

        // 3. Direct package check if full package passed
        val directIntent = pm.getLaunchIntentForPackage(appName)
        if (directIntent != null) {
            directIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(directIntent)
            return ExecutionOutcome(true, "Opening $appName.", appName)
        }

        // 4. Query all launchable activities on device for exact or partial label match
        try {
            val launcherIntent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val resolveInfos = pm.queryIntentActivities(launcherIntent, 0)

            // Try exact label match first
            val exactMatch = resolveInfos.firstOrNull {
                pm.getApplicationLabel(it.activityInfo.applicationInfo).toString().trim().equals(cleanName, ignoreCase = true)
            }
            if (exactMatch != null) {
                val targetPkg = exactMatch.activityInfo.packageName
                val launchIntent = pm.getLaunchIntentForPackage(targetPkg)
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(launchIntent)
                    val label = pm.getApplicationLabel(exactMatch.activityInfo.applicationInfo).toString()
                    return ExecutionOutcome(true, "Opening $label.", label)
                }
            }

            // Try fuzzy label or package match
            val fuzzyMatch = resolveInfos.firstOrNull {
                val label = pm.getApplicationLabel(it.activityInfo.applicationInfo).toString().lowercase()
                val pkg = it.activityInfo.packageName.lowercase()
                label.contains(cleanName) || pkg.contains(cleanName) || cleanName.contains(label)
            }
            if (fuzzyMatch != null) {
                val targetPkg = fuzzyMatch.activityInfo.packageName
                val launchIntent = pm.getLaunchIntentForPackage(targetPkg)
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(launchIntent)
                    val label = pm.getApplicationLabel(fuzzyMatch.activityInfo.applicationInfo).toString()
                    return ExecutionOutcome(true, "Opening $label.", label)
                }
            }
        } catch (e: Exception) {
            // Fall through
        }

        // 5. Fallback: Search on Google Play Store
        val playStoreSearch = Intent(Intent.ACTION_VIEW, Uri.parse("market://search?q=${Uri.encode(appName)}")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (isIntentAvailable(playStoreSearch)) {
            context.startActivity(playStoreSearch)
            return ExecutionOutcome(true, "$appName not found. Searching on Google Play Store.", appName)
        }

        return ExecutionOutcome(false, "Could not find an installed application matching \"$appName\".")
    }

    private fun makePhoneCall(phoneNumber: String): ExecutionOutcome {
        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phoneNumber")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        return ExecutionOutcome(true, "Opened dialer for $phoneNumber", "Phone")
    }

    private fun sendSmsMessage(phoneNumber: String, text: String): ExecutionOutcome {
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$phoneNumber")).apply {
            putExtra("sms_body", text)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        return ExecutionOutcome(true, "Prepared SMS message for $phoneNumber", "SMS")
    }

    private fun isIntentAvailable(intent: Intent): Boolean {
        val pm = context.packageManager
        val list = pm.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
        return list.isNotEmpty()
    }
}
