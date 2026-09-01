package com.myai.offline.actions

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
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
        // Check if package exists directly
        val intent = pm.getLaunchIntentForPackage(appName)
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            return ExecutionOutcome(true, "Launched app: $appName", appName)
        }

        // Try fuzzy package match
        val installed = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        val matched = installed.firstOrNull {
            it.packageName.contains(appName, ignoreCase = true) ||
            pm.getApplicationLabel(it).toString().contains(appName, ignoreCase = true)
        }

        if (matched != null) {
            val launchIntent = pm.getLaunchIntentForPackage(matched.packageName)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
                return ExecutionOutcome(true, "Launched app: ${pm.getApplicationLabel(matched)}", matched.packageName)
            }
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
