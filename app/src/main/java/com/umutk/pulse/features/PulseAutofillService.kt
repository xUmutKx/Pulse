package com.umutk.pulse.features

import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.IntentSender
import android.os.Bundle
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

/** Settings of the autofill service. */
internal object AutofillPrefs {
    private fun p(c: Context) = c.getSharedPreferences("autofill_settings", Context.MODE_PRIVATE)
    /** A card number typed in a form is offered to the vault (off at first: only the person turns it on). */
    fun saveCards(c: Context) = p(c).getBoolean("saveCards", false)
    fun setSaveCards(c: Context, v: Boolean) = p(c).edit().putBoolean("saveCards", v).apply()
}

/** A card number that passes the Luhn check (13 to 19 digits). */
internal fun cardOk(d: String): Boolean {
    if (d.length !in 13..19 || !d.all { it.isDigit() }) return false
    var sum = 0; var alt = false
    for (i in d.indices.reversed()) { var n = d[i] - '0'; if (alt) { n *= 2; if (n > 9) n -= 9 }; sum += n; alt = !alt }
    return sum % 10 == 0
}

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
        // a payment form: the boxes of the card and what is typed in them
        var cardNum: AutofillId? = null; var cardNumText = ""
        var cardExp: AutofillId? = null; var cardExpText = ""
        var cardMonth: AutofillId? = null; var cardMonthText = ""
        var cardYear: AutofillId? = null; var cardYearText = ""
        var cardCode: AutofillId? = null; var cardCodeText = ""
        var cardHolder: AutofillId? = null; var cardHolderText = ""
        // text boxes in the order they appear: the last one before the password box is the user name when nothing marks it
        val boxes = ArrayList<Pair<AutofillId, String>>()
    }

    override fun onFillRequest(request: FillRequest, cancel: CancellationSignal, callback: FillCallback) {
        val structure = request.fillContexts.lastOrNull()?.structure ?: run { callback.onSuccess(null); return }
        val form = parse(structure)
        if (form.pass == null && form.user == null && form.cardNum == null) { callback.onSuccess(null); return }
        val resp = FillResponse.Builder()
        if (form.cardNum != null) {
            // the saved cards: always behind the phone's lock (a card is shown only after it is answered)
            val cards = VaultStore.all(this).filter { it.type == 3 && cardOk(it.fields["number"].orEmpty().filter { c -> c.isDigit() }) }.take(6)
            cards.forEachIndexed { idx, m ->
                val view = RemoteViews(packageName, R.layout.autofill_item)
                view.setTextViewText(R.id.af_title, m.name.ifBlank { m.fields["brand"].orEmpty().ifBlank { "Card" } })
                view.setTextViewText(R.id.af_sub, "•••• " + m.fields["number"].orEmpty().filter { c -> c.isDigit() }.takeLast(4))
                val ds = Dataset.Builder(view).setAuthentication(authSender(idx, m, form))
                // the boxes the dataset is for (their values come after the lock is answered)
                listOfNotNull(form.cardNum, form.cardExp, form.cardMonth, form.cardYear, form.cardCode, form.cardHolder).forEach { ds.setValue(it, null) }
                resp.addDataset(ds.build())
            }
        } else {
            val matches = VaultStore.matches(this, form.pkg, form.domain).take(8)
            // the vault behind the screen lock: the login shows its secret only after the lock is answered
            val locked = AuthPrefs.biometric(this)
            matches.forEachIndexed { idx, m ->
                val view = RemoteViews(packageName, R.layout.autofill_item)
                view.setTextViewText(R.id.af_title, m.name.ifBlank { m.user })
                view.setTextViewText(R.id.af_sub, m.user)
                val ds = Dataset.Builder(view)
                if (locked) {
                    ds.setAuthentication(authSender(idx, m, form))
                    listOfNotNull(form.user, form.pass).forEach { ds.setValue(it, null) }
                } else {
                    // the keyboard's own bar shows the suggestion (Android 11+), as other password managers do
                    if (Build.VERSION.SDK_INT >= 30) inlineFor(request, idx, m.name.ifBlank { m.user }, m.user)?.let { ds.setInlinePresentation(it) }
                    form.user?.let { ds.setValue(it, AutofillValue.forText(m.user)) }
                    form.pass?.let { ds.setValue(it, AutofillValue.forText(m.pass)) }
                }
                resp.addDataset(ds.build())
            }
        }
        // a card typed in this form can be saved when it is sent, when the person turned that on
        if (form.cardNum != null && AutofillPrefs.saveCards(this)) {
            val save = SaveInfo.Builder(SaveInfo.SAVE_DATA_TYPE_CREDIT_CARD, arrayOf(form.cardNum!!))
            val opt = listOfNotNull(form.cardExp, form.cardMonth, form.cardYear, form.cardCode, form.cardHolder)
            if (opt.isNotEmpty()) save.setOptionalIds(opt.toTypedArray())
            resp.setSaveInfo(save.build())
        }
        // a login typed in this form can be saved when the form is sent
        val required = if (form.cardNum != null) null else (form.pass ?: form.user)
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

    /** The question behind a suggestion: [AutofillAuthActivity] asks for the phone's lock, then gives the real values back. */
    private fun authSender(idx: Int, item: VItem, form: Form): IntentSender {
        val i = Intent(this, AutofillAuthActivity::class.java).putExtra("item", item.id)
        form.user?.let { i.putExtra("user", it) }; form.pass?.let { i.putExtra("pass", it) }
        form.cardNum?.let { i.putExtra("cnum", it) }; form.cardExp?.let { i.putExtra("cexp", it) }; form.cardMonth?.let { i.putExtra("cmon", it) }
        form.cardYear?.let { i.putExtra("cyear", it) }; form.cardCode?.let { i.putExtra("ccode", it) }; form.cardHolder?.let { i.putExtra("chold", it) }
        // the system fills the answer into this intent, so it has to be mutable
        val flags = PendingIntent.FLAG_CANCEL_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
        return PendingIntent.getActivity(this, idx + 1, i, flags).intentSender
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
        if (form.cardNumText.isNotEmpty() && AutofillPrefs.saveCards(this)) runCatching { saveCard(form) }
        else if (form.passText.isNotEmpty()) {
            val uri = form.domain?.let { "https://$it/" } ?: form.pkg?.let { "androidapp://$it" }
            if (uri != null) {
                val label = form.domain ?: runCatching { packageManager.getApplicationLabel(packageManager.getApplicationInfo(form.pkg!!, 0)).toString() }.getOrDefault(form.pkg.orEmpty())
                runCatching { VaultStore.remember(this, label, uri, form.userText, form.passText) }
            }
        }
        callback.onSuccess()
    }

    /** A card typed in a payment form, into the vault (a number that passes the check; the same number is not saved twice). */
    private fun saveCard(f: Form) {
        val number = f.cardNumText.filter { it.isDigit() }
        if (!cardOk(number)) return
        if (VaultStore.all(this).any { it.type == 3 && it.fields["number"].orEmpty().filter { c -> c.isDigit() } == number }) return
        var month = f.cardMonthText.filter { it.isDigit() }.takeLast(2)
        var year = f.cardYearText.filter { it.isDigit() }
        if (f.cardExpText.isNotBlank()) {
            val d = f.cardExpText.split('/', '-', ' ').map { x -> x.filter { c -> c.isDigit() } }.filter { it.isNotEmpty() }
            if (d.size >= 2) { month = d[0].padStart(2, '0').takeLast(2); year = d[1] } else if (d.size == 1 && d[0].length in 4..6) { month = d[0].take(2); year = d[0].drop(2) }
        }
        val fields = LinkedHashMap<String, String>()
        fields["number"] = number
        if (f.cardHolderText.isNotBlank()) fields["holder"] = f.cardHolderText.trim()
        if (month.isNotEmpty()) fields["expMonth"] = month
        if (year.isNotEmpty()) fields["expYear"] = if (year.length == 2) "20$year" else year
        f.cardCodeText.filter { it.isDigit() }.takeIf { it.length in 3..4 }?.let { fields["code"] = it }
        VaultStore.upsert(this, VItem(type = 3, name = "Card •••• " + number.takeLast(4), fields = fields))
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
            // the boxes of a payment card (before the verification-code check: a card's security code is not one)
            val ac = attrs?.firstOrNull { it.first.equals("autocomplete", true) }?.second?.lowercase().orEmpty()
            fun hint(h: String) = hints.any { it.equals(h, true) }
            val cNum = hint(View.AUTOFILL_HINT_CREDIT_CARD_NUMBER) || ac.contains("cc-number") || listOf("cardnumber", "card number", "card_number", "kart no", "kartno", "kart numaras").any { label.contains(it) }
            val cCode = hint(View.AUTOFILL_HINT_CREDIT_CARD_SECURITY_CODE) || ac.contains("cc-csc") || listOf("cvv", "cvc", "cvv2", "cardcode", "security code", "güvenlik kodu", "guvenlik kodu").any { label.contains(it) } && !label.contains("sms")
            val cMon = hint(View.AUTOFILL_HINT_CREDIT_CARD_EXPIRATION_MONTH) || ac.contains("cc-exp-month")
            val cYear = hint(View.AUTOFILL_HINT_CREDIT_CARD_EXPIRATION_YEAR) || ac.contains("cc-exp-year")
            val cExp = hint(View.AUTOFILL_HINT_CREDIT_CARD_EXPIRATION_DATE) || ac == "cc-exp" || listOf("expiry", "expiration", "son kullanma", "exp date").any { label.contains(it) }
            val cHold = ac.contains("cc-name") || listOf("cardholder", "card holder", "name on card", "kart sahibi", "kart üzerindeki").any { label.contains(it) }
            if (cNum || cCode || cMon || cYear || cExp || cHold) {
                when {
                    cNum -> { if (f.cardNum == null) { f.cardNum = id; f.cardNumText = text } }
                    cCode -> { if (f.cardCode == null) { f.cardCode = id; f.cardCodeText = text } }
                    cMon -> { if (f.cardMonth == null) { f.cardMonth = id; f.cardMonthText = text } }
                    cYear -> { if (f.cardYear == null) { f.cardYear = id; f.cardYearText = text } }
                    cExp -> { if (f.cardExp == null) { f.cardExp = id; f.cardExpText = text } }
                    else -> { if (f.cardHolder == null) { f.cardHolder = id; f.cardHolderText = text } }
                }
                for (i in 0 until n.childCount) walk(n.getChildAt(i), f); return
            }
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

/**
 * The phone's lock before an autofill suggestion shows its secret: a saved card always, a login when the vault is behind the screen lock. The answer is the real
 * dataset, which goes back to the system. With no lock set on the phone nothing is asked.
 */
@RequiresApi(Build.VERSION_CODES.O)
class AutofillAuthActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 30) askSystemLock(this, { answer() }, { no() })
        else {
            val kg = getSystemService(android.app.KeyguardManager::class.java)
            if (kg != null && kg.isDeviceSecure) {
                @Suppress("DEPRECATION")
                runCatching { startActivityForResult(kg.createConfirmDeviceCredentialIntent(null, null), 7) }.onFailure { no() }
            } else answer()
        }
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 7 && resultCode == RESULT_OK) answer() else no()
    }

    private fun no() { setResult(RESULT_CANCELED); finish() }

    @Suppress("DEPRECATION")
    private fun fid(k: String): AutofillId? = intent.getParcelableExtra(k)

    private fun answer() {
        val item = VaultStore.all(this).firstOrNull { it.id == intent.getStringExtra("item") } ?: return no()
        val view = RemoteViews(packageName, R.layout.autofill_item)
        view.setTextViewText(R.id.af_title, item.name.ifBlank { item.user }); view.setTextViewText(R.id.af_sub, "")
        val ds = Dataset.Builder(view)
        var any = false
        fun put(k: String, v: String) { fid(k)?.let { ds.setValue(it, AutofillValue.forText(v)); any = true } }
        if (item.type == 1) { put("user", item.user); put("pass", item.pass) }
        else if (item.type == 3) {
            val f = item.fields
            val mon = f["expMonth"].orEmpty().padStart(2, '0'); val year = f["expYear"].orEmpty()
            put("cnum", f["number"].orEmpty()); put("ccode", f["code"].orEmpty()); put("chold", f["holder"].orEmpty())
            put("cmon", mon); put("cyear", year)
            put("cexp", if (mon.isNotBlank() && year.isNotBlank()) mon + "/" + year.takeLast(2) else "")
        }
        if (!any) return no()
        setResult(RESULT_OK, Intent().putExtra(android.view.autofill.AutofillManager.EXTRA_AUTHENTICATION_RESULT, ds.build()))
        finish()
    }
}
