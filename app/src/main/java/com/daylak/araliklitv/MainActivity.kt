package com.daylak.araliklitv

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.graphics.Color
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.json.JSONObject
import java.text.DateFormat
import java.util.Date

class MainActivity : Activity() {
    private var decks: List<Deck> = emptyList()
    private val progress = mutableMapOf<String, Progress>()
    private val prefs by lazy { getSharedPreferences("aralikli_progress_v1", MODE_PRIVATE) }
    private var turn = 0
    private var currentDeck: Deck? = null
    private var currentQuestion: Question? = null
    private var inFeedback = false
    private var feedbackCanAdvance = false
    private var screen = "home"
    private var error: String? = null
    private var soundEngine: QuizSounds? = null
    private val sounds: QuizSounds
        get() = soundEngine ?: QuizSounds().also { soundEngine = it }
    private var soundEnabled = true
    private var updateChecking = false
    private var updateInstalling = false
    private var availableUpdate: UpdateRelease? = null
    private var updateMessage: String? = null

    private val background = Color.rgb(13, 22, 35)
    private val ink = Color.rgb(241, 245, 249)
    private val muted = Color.rgb(172, 184, 198)
    private val accent = Color.rgb(47, 105, 155)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
        try {
            val json = assets.open("questions.json").bufferedReader(Charsets.UTF_8).use { it.readText() }
            decks = QuestionLoader.load(json)
            loadProgress()
            soundEnabled = prefs.getBoolean("sound_enabled", true)
        } catch (e: Exception) {
            error = "Soru bankası okunamadı: ${e.message ?: "bilinmeyen hata"}"
        }
        renderHome()
        checkUpdates(false)
    }

    private fun px(dp: Int): Int = (resources.displayMetrics.density * dp).toInt()
    private fun shape(color: Int, outline: Int): GradientDrawable =
        GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
            intArrayOf(color, Color.rgb(9, 17, 61))).apply {
            cornerRadius = px(18).toFloat()
            setStroke(px(3), outline)
        }
    private fun buttonBg(): StateListDrawable = StateListDrawable().apply {
        addState(intArrayOf(android.R.attr.state_focused),
            shape(Color.rgb(33,83,165), Color.rgb(255,211,103)))
        addState(intArrayOf(), shape(Color.rgb(13,30,94), Color.rgb(53,147,230)))
    }
    private fun panel(): LinearLayout {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(px(46), px(34), px(46), px(40))
        }
        val view = ScrollView(this).apply {
            background = StageDrawable()
            isFillViewport = true
            addView(content)
        }
        setContentView(view)
        return content
    }
    private fun heading(box: LinearLayout, text: String, size: Float = 32f) {
        box.addView(TextView(this).apply {
            this.text = text; setTextColor(ink); textSize = size
            setShadowLayer(8f, 0f, 0f, Color.rgb(25, 75, 170))
            setPadding(0,0,0,px(15))
        })
    }
    private fun info(box: LinearLayout, text: String, size: Float = 20f) {
        box.addView(TextView(this).apply {
            this.text = text; setTextColor(muted); textSize = size
            setPadding(0,0,0,px(20))
        })
    }
    private fun action(box: LinearLayout, caption: String, click: () -> Unit): Button {
        val b = Button(this).apply {
            text = caption; isAllCaps = false; textSize = 21f
            setTextColor(ColorStateList.valueOf(Color.WHITE))
            background = buttonBg()
            setPadding(px(22),px(11),px(22),px(11))
            setOnClickListener { click() }
        }
        val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,LinearLayout.LayoutParams.WRAP_CONTENT)
        params.bottomMargin = px(12)
        box.addView(b, params)
        return b
    }
    private fun firstFocus(button: Button?) { button?.requestFocus() }
    private fun questions(deck: Deck?): List<Question> =
        deck?.questions ?: decks.flatMap { it.questions }

    private fun renderHome() {
        screen = "home"; currentQuestion = null; inFeedback=false; feedbackCanAdvance=false
        val box = panel()
        heading(box, "✦  ARALIKLI TV  ✦")
        val all = questions(null)
        val now = System.currentTimeMillis()
        info(box,"${all.count { it.isScorable }} doğrulanmış soru  •  ${Scheduler.dueCount(all,progress,now,turn)} zamanı gelen tekrar")
        error?.let { info(box,it) }
        val primary = action(box,"Ders Seç") { renderLessons() }
        action(box,"Günlük Tekrar (Tüm Dersler)") {
            currentDeck = null; nextQuestion()
        }
        action(box,"İstatistikler") { renderStats() }
        action(box,"Ders Videoları") { renderVideos() }
        action(box,if(soundEnabled) "Ses efektleri: AÇIK" else "Ses efektleri: KAPALI") {
            soundEnabled = !soundEnabled
            prefs.edit().putBoolean("sound_enabled", soundEnabled).apply()
            renderHome()
        }
        if (updateChecking) info(box, "Yeni sürüm kontrol ediliyor...", 18f)
        updateMessage?.let { info(box, it, 18f) }
        availableUpdate?.let { release ->
            action(box, "Güncelle: v${release.version}") { installUpdate(release) }
        }
        if (!updateChecking && !updateInstalling) {
            action(box,"Güncellemeleri Kontrol Et") { checkUpdates(true) }
        }
        if (updateInstalling) info(box, "APK indiriliyor ve doğrulanıyor...", 18f)
        info(box,"Doğrulanmamış cevap anahtarı olan sorular puanlanmaz.",16f)
        firstFocus(primary)
    }

    private fun renderLessons() {
        screen = "lessons"; currentQuestion = null; inFeedback=false;feedbackCanAdvance=false
        val box = panel(); heading(box,"DERSLERİM")
        info(box,"Öğrenmeye başlamak için kumandanın OK tuşunu kullan.")
        var first: Button? = null
        decks.forEach { deck ->
            val count = deck.questions.count { it.isScorable }
            val due = Scheduler.dueCount(deck.questions,progress,System.currentTimeMillis(),turn)
            val title = if (count==0) "${deck.title}  •  Cevap anahtarı bekleniyor"
                        else "${deck.title}  •  $count soru / $due tekrar"
            val b = action(box,title) {
                if (count==0) {
                    val notice=panel();screen="unavailable"
                    heading(notice,deck.title)
                    info(notice,"Bu dersin soruları alındı, ancak otomatik değerlendirmeye uygun doğrulanmış cevap anahtarı henüz eklenmedi.")
                    firstFocus(action(notice,"Derslere Dön") { renderLessons() })
                } else { currentDeck=deck; nextQuestion() }
            }
            if (first==null) first=b
        }
        action(box,"Ana Menü") { renderHome() }
        firstFocus(first)
    }

    private fun nextQuestion() {
        screen="study";inFeedback=false;feedbackCanAdvance=false
        val question=Scheduler.pick(questions(currentDeck),progress,System.currentTimeMillis(),turn)
        if (question==null) { renderDone();return }
        currentQuestion=question
        val box=panel()
        heading(box,"${currentDeck?.title ?: "Günlük Tekrar"}",26f)
        val p=progress[question.id]
        info(box,"${if (p==null) "Yeni soru" else "Tekrar"}  •  ${question.sourceFile.orEmpty()} / s.${question.sourcePage}",16f)
        heading(box,question.stem,27f)
        var first:Button?=null
        question.options.forEach { (letter,option) ->
            val b=action(box,"$letter) $option") { showFeedback(question,letter) }
            if (first==null) first=b
        }
        action(box,"Derslere Dön") { renderLessons() }
        firstFocus(first)
    }

    private fun showFeedback(q: Question,selected: String) {
        if (inFeedback || q.id!=currentQuestion?.id)return
        inFeedback=true
        val right=(selected==q.answer)
        val box=panel()
        if (soundEnabled) {
            if (right) sounds.correct() else sounds.wrong()
        }
        val banner = TextView(this).apply {
            text = if(right) "✓  DOĞRU CEVAP!" else "✕  YANLIŞ CEVAP"
            textSize = 32f
            setTextColor(if(right) Color.rgb(116,255,155) else Color.rgb(255,119,133))
            setBackground(shape(
                if (right) Color.rgb(6,73,42) else Color.rgb(105,27,56),
                if (right) Color.rgb(64,255,148) else Color.rgb(255,98,120)
            ))
            setPadding(px(26),px(22),px(26),px(22))
        }
        box.addView(banner)
        banner.animate().scaleX(1.035f).scaleY(1.035f).setDuration(170L)
            .withEndAction { banner.animate().scaleX(1f).scaleY(1f).setDuration(170L).start() }
            .start()
        info(box, if (right) "Başardın! Hatırlama seviyeni seç." else
            "Bu soru kısa süre sonra tekrar karşına gelecek.", 19f)
        heading(box,q.stem,25f)
        info(box,"İşaretlediğin: $selected) ${q.options[selected] ?: ""}",21f)
        info(box,"Doğru: ${q.answer}) ${q.options[q.answer] ?: ""}",22f)
        if (!right) {
            register(q, Rating.AGAIN)
            feedbackCanAdvance=true
            firstFocus(action(box,"Sonraki Soru") { nextQuestion() })
        } else {
            info(box,"Cevabı nasıl buldun?",23f)
            val guessed=action(box,"Tahmin ettim") { register(q,Rating.GUESSED);nextQuestion() }
            action(box,"Zorlandım") { register(q,Rating.HARD);nextQuestion() }
            action(box,"Biliyordum") { register(q,Rating.GOOD);nextQuestion() }
            action(box,"Çok kolaydı") { register(q,Rating.EASY);nextQuestion() }
            firstFocus(guessed)
        }
    }

    private fun register(q:Question, rating:Rating) {
        val now=System.currentTimeMillis()
        val old=progress[q.id] ?: Progress()
        progress[q.id] = Scheduler.grade(old,rating,now,turn)
        turn++
        saveProgress()
    }

    private fun renderDone() {
        screen="done"; currentQuestion=null
        val box=panel()
        heading(box,"ŞİMDİLİK TAMAMLANDI")
        info(box,"Yeni soru veya zamanı gelen tekrar kalmadı. Zamanı gelmemiş soruları erkenden tekrarlamıyoruz.")
        firstFocus(action(box,"Dersler") { renderLessons() })
        action(box,"Ana Menü") { renderHome() }
    }

    private fun openVideoLink(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: Exception) {
            val box = panel()
            screen = "videos"
            heading(box, "Bağlantı açılamadı")
            info(box, "Televizyonda tarayıcı veya YouTube uygulaması bulunmayabilir.")
            info(box, url, 17f)
            firstFocus(action(box, "Video Dersler") { renderVideos() })
        }
    }

    private fun renderVideos() {
        screen = "videos"
        val box = panel()
        heading(box, "VİDEO DERSLER")
        info(box, "AUZEF ders anlatımları, üniversitenin kendi materyalleri üzerinden takip edilebilir.")
        val first = action(box,"AUZEF Ders Materyalleri (Resmi)") {
            openVideoLink("https://ikinciuniversite.istanbul.edu.tr/tr/content/ders-materyalleri/ders-materyalleri")
        }
        action(box,"AUZEF Öğrenci Sistemi (AKSİS)") {
            openVideoLink("https://aksis.istanbul.edu.tr")
        }
        action(box,"Atatürk İlkeleri II - Anadolu Üniversitesi Video Ders") {
            openVideoLink("https://www.youtube.com/watch?v=Qou2hEGopUA")
        }
        info(box,"Video izlemek internet bağlantısı ve uygun TV uygulaması gerektirir.",16f)
        action(box,"Ana Menü") { renderHome() }
        firstFocus(first)
    }

    private fun renderStats() {
        screen="stats";val box=panel();heading(box,"İSTATİSTİKLER")
        decks.forEach { d ->
            val entries=d.questions.mapNotNull { progress[it.id] }
            val attempts=entries.sumOf { it.attempts }
            val correct=entries.sumOf { it.correct }
            val pct= if(attempts==0) "-" else "${correct*100/attempts}%"
            info(box,"${d.title}: ${entries.size}/${d.questions.size} görüldü  •  $pct doğru  •  ${Scheduler.dueCount(d.questions,progress,System.currentTimeMillis(),turn)} tekrar",18f)
        }
        firstFocus(action(box,"Ana Menü") { renderHome() })
    }


    private fun checkUpdates(manual: Boolean) {
        if (updateChecking) return
        updateChecking = true
        if (manual) updateMessage = "GitHub sürümleri kontrol ediliyor..."
        if (screen == "home") renderHome()
        Thread {
            var found: UpdateRelease? = null
            var problem: String? = null
            try {
                val version = packageManager.getPackageInfo(packageName,0).versionName ?: "0.0.0"
                found = AppUpdater.findUpdate(version)
            } catch (e: Exception) { problem = e.message ?: "Bağlantı hatası" }
            runOnUiThread {
                updateChecking = false
                availableUpdate = found
                updateMessage = when {
                    problem != null -> "Güncelleme kontrol edilemedi: $problem"
                    found != null -> "Yeni sürüm bulundu: v${found.version}"
                    manual -> "Uygulama güncel."
                    else -> null
                }
                if (screen == "home") renderHome()
            }
        }.start()
    }

    private fun installUpdate(release: UpdateRelease) {
        if (updateInstalling) return
        updateInstalling = true
        updateMessage = "APK indiriliyor..."
        renderHome()
        Thread {
            var apk: java.io.File? = null
            var problem: String? = null
            try { apk = AppUpdater.download(this, release) }
            catch (e: Exception) { problem = e.message ?: "İndirme başarısız" }
            runOnUiThread {
                updateInstalling = false
                if (apk != null) {
                    try {
                        val opened = AppUpdater.install(this, apk)
                        updateMessage = if (opened)
                            "Android kurulum ekranında güncellemeyi onayla."
                        else "Yükleme iznini verip Güncelle seçeneğine yeniden bas."
                    } catch (e: Exception) {
                        updateMessage = "Kurulum başlatılamadı: ${e.message}"
                    }
                } else updateMessage = "Güncelleme başarısız: $problem"
                if (screen == "home") renderHome()
            }
        }.start()
    }

    override fun onDestroy() {
        soundEngine?.release()
        super.onDestroy()
    }

    private fun loadProgress() {
        turn = prefs.getInt("turn",0)
        val source = JSONObject(prefs.getString("progress", "{}") ?: "{}")
        val keys = source.keys()
        while(keys.hasNext()) {
            val id=keys.next();val o=source.getJSONObject(id)
            progress[id] = Progress(
                repetitions=o.optInt("repetitions"),intervalDays=o.optInt("intervalDays"),
                ease=o.optDouble("ease",2.5),dueAt=o.optLong("dueAt"),
                attempts=o.optInt("attempts"),correct=o.optInt("correct"),
                learningStep=o.optInt("learningStep"),dueAtTurn=o.optInt("dueAtTurn")
            )
        }
    }
    private fun saveProgress() {
        val o=JSONObject()
        progress.forEach { (key,p) ->
            o.put(key,JSONObject().apply {
                put("repetitions",p.repetitions);put("intervalDays",p.intervalDays)
                put("ease",p.ease);put("dueAt",p.dueAt)
                put("attempts",p.attempts);put("correct",p.correct)
                put("learningStep",p.learningStep);put("dueAtTurn",p.dueAtTurn)
            })
        }
        prefs.edit().putString("progress",o.toString()).putInt("turn",turn).apply()
    }

    override fun onKeyDown(keyCode:Int,event:KeyEvent?):Boolean {
        if (keyCode==KeyEvent.KEYCODE_DPAD_RIGHT && inFeedback && feedbackCanAdvance) {
            nextQuestion();return true
        }
        return super.onKeyDown(keyCode,event)
    }
    @Deprecated("Handle system back button for the Android TV remote")
    override fun onBackPressed() {
        when(screen) {
            "study", "done", "unavailable" -> renderLessons()
            "lessons", "stats", "videos" -> renderHome()
            else -> super.onBackPressed()
        }
    }
}
