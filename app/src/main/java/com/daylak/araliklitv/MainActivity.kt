package com.daylak.araliklitv

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import android.view.View
import android.graphics.Color
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.widget.Button
import android.widget.ProgressBar
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
        get() = soundEngine ?: QuizSounds(this).also { soundEngine = it }
    private var soundEnabled = true
    private val localShowAssets by lazy { LocalShowAssets(this) }
    private var originalThemeEnabled = true
    private var timerSeconds = CountdownSettings.DEFAULT_SECONDS
    private val timerHandler = Handler(Looper.getMainLooper())
    private var timerRunning = false
    private var timerDeadlineMs = 0L
    private var timerRemainingMs = 0L
    private var timerLabel: TextView? = null
    private var timerBar: ProgressBar? = null
    private var timerWarningPlayed = false
    private var autoAdvanceTask: Runnable? = null
    private val timerTick = object : Runnable {
        override fun run() {
            if (!timerRunning || screen != "study" || inFeedback) return
            val remain = CountdownSettings.remainingMillis(
                timerDeadlineMs, SystemClock.elapsedRealtime()
            )
            timerRemainingMs = remain
            if (remain == 0L) {
                timerRunning = false
                currentQuestion?.let { showTimeout(it) }
                return
            }
            timerLabel?.text = "Kalan süre: ${CountdownSettings.display(remain)}"
            timerLabel?.setTextColor(if (remain <= 10_000L)
                Color.rgb(255, 188, 96) else ink)
            timerBar?.progress = if (timerSeconds == 0) 0
                else ((remain * 1000L) / (timerSeconds * 1000L)).toInt()
            if (remain in 1L..10_000L && !timerWarningPlayed) {
                timerWarningPlayed = true
                if (soundEnabled) sounds.warning()
            }
            timerHandler.postDelayed(this, 250L)
        }
    }
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
            originalThemeEnabled = prefs.getBoolean("original_theme_enabled", true)
            // Preserve an explicit "off" setting; migrate previous 20/30/60/90s to 50s.
            timerSeconds = if (prefs.getInt("timer_seconds", CountdownSettings.DEFAULT_SECONDS) == 0)
                0 else CountdownSettings.DEFAULT_SECONDS
        } catch (e: Exception) {
            error = "Soru bankası okunamadı: ${e.message ?: "bilinmeyen hata"}"
        }
        renderHome()
        checkUpdates(false)
    }

    private val uiScale: Float by lazy {
        val metrics = resources.displayMetrics
        TvLayout.scale(metrics.widthPixels / metrics.density, metrics.heightPixels / metrics.density)
    }
    private fun px(dp: Int): Int =
        (resources.displayMetrics.density * dp * uiScale).toInt().coerceAtLeast(1)
    private fun scaledSp(sp: Float): Float = sp * uiScale
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
    private fun stopCountdown() {
        timerRunning = false
        timerHandler.removeCallbacks(timerTick)
        timerDeadlineMs = 0L
        timerRemainingMs = 0L
        timerLabel = null
        timerBar = null
    }

    private fun startCountdown(box: LinearLayout) {
        if (timerSeconds == 0) return
        timerWarningPlayed = false
        val label = TextView(this).apply {
            textSize = scaledSp(20f)
            includeFontPadding = false
            setTextColor(ink)
            setPadding(0, px(4), 0, px(8))
        }
        box.addView(label)
        val bar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 1000
            progress = 1000
            progressTintList = ColorStateList.valueOf(Color.rgb(46, 209, 255))
            progressBackgroundTintList = ColorStateList.valueOf(Color.rgb(30, 43, 103))
        }
        box.addView(bar, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, px(10)
        ).apply { bottomMargin = px(8) })
        timerLabel = label
        timerBar = bar
        timerRemainingMs = timerSeconds * 1000L
        timerDeadlineMs = SystemClock.elapsedRealtime() + timerRemainingMs
        timerRunning = true
        timerHandler.post(timerTick)
    }

    private fun optionButtons(box: LinearLayout, question: Question): Button? {
        // Five real PDF choices become A+B, C+D, and full-width E.
        // Never invent a missing option for a four-choice source.
        var first: Button? = null
        for (pair in question.options.entries.chunked(2)) {
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            box.addView(row, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ))
            for ((letter, choice) in pair) {
                val button = action(row, "$letter) $choice") {
                    showFeedback(question, letter)
                }
                if (pair.size == 2) {
                    val params = button.layoutParams as LinearLayout.LayoutParams
                    params.width = 0
                    params.weight = 1f
                    params.marginEnd = px(7)
                    button.layoutParams = params
                }
                if (first == null) first = button
            }
        }
        return first
    }

    private fun panel(): LinearLayout {
        autoAdvanceTask?.let { timerHandler.removeCallbacks(it) }
        autoAdvanceTask = null
        stopCountdown()
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val tight = screen == "study" || screen == "timeout"
            setPadding(px(32), px(if(tight) 12 else 20),
                px(32), px(if(tight) 12 else 20))
        }
        val view = ScrollView(this).apply {
            background = if (originalThemeEnabled) localShowAssets.stageBackground()
                else StageDrawable()
            isFillViewport = true
            addView(content)
        }
        setContentView(view)
        return content
    }
    private fun heading(box: LinearLayout, text: String, size: Float = 32f) {
        box.addView(TextView(this).apply {
            this.text = text; setTextColor(ink); textSize = scaledSp(size)
            includeFontPadding = false
            setShadowLayer(8f, 0f, 0f, Color.rgb(25, 75, 170))
            setPadding(0,0,0,px(if (screen=="study") 6 else 10))
        })
    }
    private fun info(box: LinearLayout, text: String, size: Float = 20f) {
        box.addView(TextView(this).apply {
            this.text = text; setTextColor(muted); textSize = scaledSp(size)
            includeFontPadding = false
            setPadding(0,0,0,px(if (screen=="study") 5 else 10))
        })
    }
    private fun action(box: LinearLayout, caption: String, click: () -> Unit): Button {
        val b = Button(this).apply {
            text = caption; isAllCaps = false
            textSize = scaledSp(if (screen=="study") 19f else 20f)
            includeFontPadding = false
            minHeight = 0
            minimumHeight = px(39)
            setTextColor(ColorStateList.valueOf(Color.WHITE))
            background = buttonBg()
            setPadding(px(14),px(if (screen=="study") 6 else 9),
                px(14),px(if (screen=="study") 6 else 9))
            setOnClickListener { click() }
        }
        val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,LinearLayout.LayoutParams.WRAP_CONTENT)
        params.bottomMargin = px(if (screen=="study") 6 else 8)
        box.addView(b, params)
        return b
    }
    private fun firstFocus(button: Button?) { button?.requestFocus() }
    private fun questions(deck: Deck?): List<Question> =
        deck?.questions ?: decks.flatMap { it.questions }

    private fun renderHome() {
        screen = "home"; currentQuestion = null; inFeedback=false; feedbackCanAdvance=false
        val box = panel()
        heading(box, "KİM KPSS KAZANMAK İSTER?")
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
        val showImageReady = localShowAssets.hasStage()
        action(box,"Yarışma arka planı: " + when {
            !showImageReady -> "Özel görsel bekleniyor"
            originalThemeEnabled -> "Özel arka plan AÇIK"
            else -> "Neon AÇIK"
        }) {
            originalThemeEnabled = !originalThemeEnabled
            prefs.edit().putBoolean("original_theme_enabled", originalThemeEnabled).apply()
            renderHome()
        }
        if (!showImageReady) {
            info(box,"Kendi sahne fotoğrafını TV'ye milyoner_stage.jpg adıyla aktar. " +
                "Görsel yalnızca bu televizyonda saklanır.", 16f)
        }
        action(box,"Geri sayım: ${CountdownSettings.label(timerSeconds)}") {
            timerSeconds = CountdownSettings.next(timerSeconds)
            prefs.edit().putInt("timer_seconds",timerSeconds).apply()
            renderHome()
        }
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
        info(box,"${if (p==null) "Yeni soru" else "Tekrar"}  •  Sayfa ${question.sourcePage}",16f)
        heading(box,question.stem,25f)
        startCountdown(box)
        val first = optionButtons(box, question)
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

    private fun showTimeout(q: Question) {
        if (inFeedback || screen != "study" || currentQuestion?.id != q.id) return
        inFeedback = true
        feedbackCanAdvance = true
        screen = "timeout"
        register(q, Rating.AGAIN, timedOut = true)
        if (soundEnabled) sounds.wrong()
        val box = panel()
        heading(box, "⏱  SÜRE DOLDU • CEVAPSIZ")
        info(box, "$timerSeconds saniye içinde cevaplanamadı. Erken tekrara alınacak.", 20f)
        heading(box, q.stem, 25f)
        info(box, "Doğru cevap: ${q.answer}) ${q.options[q.answer].orEmpty()}", 22f)
        firstFocus(action(box, "Sonraki Soru") { nextQuestion() })
        val questionId = q.id
        val advance = Runnable {
            if (screen == "timeout" && currentQuestion?.id == questionId) nextQuestion()
        }
        autoAdvanceTask = advance
        timerHandler.postDelayed(advance, 2_500L)
    }

    private fun register(q:Question, rating:Rating, timedOut: Boolean = false) {
        val now=System.currentTimeMillis()
        val old=progress[q.id] ?: Progress()
        progress[q.id] = if (timedOut) Scheduler.timeout(old, now, turn)
            else Scheduler.grade(old, rating, now, turn)
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
            val attempts = entries.sumOf { it.attempts - it.timeouts }
            val correct = entries.sumOf { it.correct }
            val timeouts = entries.sumOf { it.timeouts }
            val pct = if (attempts == 0) "-" else "${correct * 100 / attempts}%"
            info(box,"${d.title}: ${entries.size}/${d.questions.size} görüldü  •  $pct doğru  •  $timeouts süre aşımı  •  ${Scheduler.dueCount(d.questions, progress, System.currentTimeMillis(), turn)} tekrar",18f)
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

    override fun onPause() {
        autoAdvanceTask?.let { timerHandler.removeCallbacks(it) }
        autoAdvanceTask = null
        if (timerRunning) {
            timerRemainingMs = CountdownSettings.remainingMillis(
                timerDeadlineMs, SystemClock.elapsedRealtime()
            )
            timerRunning = false
            timerHandler.removeCallbacks(timerTick)
        }
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        if (screen == "study" && !inFeedback && timerSeconds > 0 &&
            timerRemainingMs > 0L && timerLabel != null && !timerRunning) {
            timerDeadlineMs = SystemClock.elapsedRealtime() + timerRemainingMs
            timerRunning = true
            timerHandler.post(timerTick)
        }
    }

    override fun onDestroy() {
        stopCountdown()
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
                learningStep=o.optInt("learningStep"),dueAtTurn=o.optInt("dueAtTurn"),
                timeouts=o.optInt("timeouts")
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
                put("timeouts",p.timeouts)
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
            "study", "timeout", "done", "unavailable" -> renderLessons()
            "lessons", "stats", "videos" -> renderHome()
            else -> super.onBackPressed()
        }
    }
}
