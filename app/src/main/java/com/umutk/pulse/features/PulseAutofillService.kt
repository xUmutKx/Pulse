package com.umutk.pulse.features

import android.app.PendingIntent
import android.app.assist.AssistStructure
import android.content.Intent
import android.service.autofill.InlinePresentation
import android.os.Build
import android.os.CancellationSignal
import android.service.autofill.AutofillService
import android.service.autofill.Dataset
import android.service.autofill.FillCallback
import android.service.autofill.FillRequest
import android.service.autofill.FillResponse
import android.service.autofill.SaveCallback
import android.service.autofill.SaveInfo
import android.service.autofill.SaveRequest
import android.text.InputType
import android.view.View
import android.view.autofill.AutofillId
import android.view.autofill.AutofillValue
import android.widget.RemoteViews
import androidx.annotation.RequiresApi
import com.umutk.pulse.R

/**
 * Pulse as the phone's autofill service (Settings > Passwords & autofill): logins of the vault are offered in other apps and in browsers that
 * support third-party autofill, and a login typed there can be saved into the vault.
 */
@RequiresApi(Build.VERSION_CODES.O)
class PulseAutofillService : AutofillService() {

    /** What the screen's form holds: the ids of the user and password boxes, what is typed in them, and where the form is from. */
    private class Form {
        var user: AutofillId? = null
        var pass: AutofillId? = null
        var userText = ""
        var passText = ""
        var pkg: String? = null
        var domain: String? = null
        // text boxes in the order they appear: the last one before the password box is the user name when nothing marks it
        val boxes = ArrayList<Pair<AutofillId, String>>()
    }

    override fun onFillRequest(request: FillRequest, cancel: CancellationSignal, callback: FillCallback) {
        val structure = request.fillContexts.lastOrNull()?.structure ?: run { callback.onSuccess(null); return }
        val form = parse(structure)
        if (form.pass == null && form.user == null) { callback.onSuccess(null); return }
        val matches = VaultStore.matches(this, form.pkg, form.domain).take(8)
        val resp = FillResponse.Builder()
        matches.forEachIndexed { idx, m ->
            val view = RemoteViews(packageName, R.layout.autofill_item)
            view.setTextViewText(R.id.af_title, m.name.ifBlank { m.user })
            view.setTextViewText(R.id.af_sub, m.user)
            val ds = Dataset.Builder(view)
            // the keyboard's own bar shows the suggestion (Android 11+), as other password managers do
            if (Build.VERSION.SDK_INT >= 30) inlineFor(request, idx, m.name.ifBlank { m.user }, m.user)?.let { ds.setInlinePresentation(it) }
            form.user?.let { ds.setValue(it, AutofillValue.forText(m.user)) }
            form.pass?.let { ds.setValue(it, AutofillValue.forText(m.pass)) }
            resp.addDataset(ds.build())
        }
        // a login typed in this form can be saved when the form is sent
        val required = form.pass ?: form.user
        if (required != null) {
            val both = form.pass != null && form.user != null
            val type = if (both) SaveInfo.SAVE_DATA_TYPE_PASSWORD or SaveInfo.SAVE_DATA_TYPE_USERNAME else SaveInfo.SAVE_DATA_TYPE_PASSWORD
            val save = SaveInfo.Builder(type, arrayOf(required))
            if (both) save.setOptionalIds(arrayOf(form.user!!))
            // pages and apps that swap the form for the next screen (a single-page site, e-Devlet) after the login still ask to save
            save.setFlags(SaveInfo.FLAG_SAVE_ON_ALL_VIEWS_INVISIBLE)
            resp.setSaveInfo(save.build())
        }
        runCatching { callback.onSuccess(resp.build()) }.onFailure { callback.onSuccess(null) }
    }

    @RequiresApi(30)
    private fun inlineFor(req: FillRequest, i: Int, title: String, sub: String): InlinePresentation? = runCatching {
        val specs = req.inlineSuggestionsRequest?.inlinePresentationSpecs ?: return null
        if (specs.isEmpty()) return null
        val spec = specs[minOf(i, specs.lastIndex)]
        if (!androidx.autofill.inline.UiVersions.getVersions(spec.style).contains(androidx.autofill.inline.UiVersions.INLINE_UI_VERSION_1)) return null
        val pi = PendingIntent.getActivity(this, 0, Intent(), PendingIntent.FLAG_IMMUTABLE)
        val content = androidx.autofill.inline.v1.InlineSuggestionUi.newContentBuilder(pi).setTitle(title).also { if (sub.isNotBlank() && sub != title) it.setSubtitle(sub) }.build()
        InlinePresentation(content.slice, spec, false)
    }.getOrNull()

    override fun onSaveRequest(request: SaveRequest, callback: SaveCallback) {
        val structure = request.fillContexts.lastOrNull()?.structure ?: run { callback.onSuccess(); return }
        val form = parse(structure)
        if (form.passText.isNotEmpty()) {
            val uri = form.domain?.let { "https://$it/" } ?: form.pkg?.let { "androidapp://$it" }
            if (uri != null) {
                val label = form.domain ?: runCatching { packageManager.getApplicationLabel(packageManager.getApplicationInfo(form.pkg!!, 0)).toString() }.getOrDefault(form.pkg.orEmpty())
                runCatching { VaultStore.remember(this, label, uri, form.userText, form.passText) }
            }
        }
        callback.onSuccess()
    }

    private fun parse(structure: AssistStructure): Form {
        val f = Form()
        f.pkg = structure.activityComponent?.packageName
        for (i in 0 until structure.windowNodeCount) walk(structure.getWindowNodeAt(i).rootViewNode, f)
        if (f.user == null && f.pass != null) {
            val p = f.pass
            val before = f.boxes.takeWhile { it.first != p }.lastOrNull()
            if (before != null) { f.user = before.first; f.userText = before.second }
        }
        return f
    }

    private fun walk(n: AssistStructure.ViewNode, f: Form) {
        if (f.domain == null) f.domain = n.webDomain?.takeIf { it.isNotBlank() }
        val id = n.autofillId
        if (id != null && n.autofillType == View.AUTOFILL_TYPE_TEXT) {
            val hints = n.autofillHints?.toList().orEmpty()
            val text = n.autofillValue?.takeIf { it.isText }?.textValue?.toString().orEmpty()
            val inputType = n.inputType
            val variation = inputType and InputType.TYPE_MASK_VARIATION
            val textClass = inputType and InputType.TYPE_MASK_CLASS
            val attrs = n.htmlInfo?.attributes
            val htmlType = attrs?.firstOrNull { it.first.equals("type", true) }?.second?.lowercase()
            val label = ((n.idEntry ?: "") + " " + (n.hint ?: "") + " " + (attrs?.firstOrNull { it.first.equals("name", true) }?.second ?: "")).lowercase()
            // a verification code (SMS, e-mail, authenticator, captcha) is neither a user name nor a password: it is never filled and never saved
            val otp = hints.any { h -> h.contains("otp", true) || h.contains("one-time", true) || h.contains("smsOTP", true) } ||
                attrs?.firstOrNull { it.first.equals("autocomplete", true) }?.second?.contains("one-time-code", true) == true ||
                listOf("otp", "one-time", "onetime", "verification", "verify", "doğrulama", "dogrulama", "sms", "captcha", "güvenlik kodu", "guvenlik kodu", "tek kullanımlık", "tek kullanimlik", "2fa", "mfa", "pin code", "kod").any { label.contains(it) } && !label.contains("password") ||
                label.contains("tek kullanımlık") || label.contains("tek kullanimlik")
            if (otp) { for (i in 0 until n.childCount) walk(n.getChildAt(i), f); return }
            val isPass = View.AUTOFILL_HINT_PASSWORD in hints || htmlType == "password" ||
                (textClass == InputType.TYPE_CLASS_TEXT && variation in setOf(InputType.TYPE_TEXT_VARIATION_PASSWORD, InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD, InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD)) ||
                (textClass == InputType.TYPE_CLASS_NUMBER && variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD) ||
                label.contains("password") || label.contains("passwd") || label.contains("şifre") || label.contains("parola")
            val isUser = View.AUTOFILL_HINT_USERNAME in hints || View.AUTOFILL_HINT_EMAIL_ADDRESS in hints || htmlType == "email" ||
                (textClass == InputType.TYPE_CLASS_TEXT && variation == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS) ||
                listOf("user", "email", "login", "e-posta", "eposta", "kullanıcı", "kullanici", "phone", "mail").any { label.contains(it) }
            if (isPass) { if (f.pass == null) { f.pass = id; f.passText = text } }
            else {
                if (isUser && f.user == null) { f.user = id; f.userText = text }
                if (textClass == InputType.TYPE_CLASS_TEXT || textClass == 0) f.boxes += id to text
            }
        }
        for (i in 0 until n.childCount) walk(n.getChildAt(i), f)
    }
}
