package com.daylak.araliklitv

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.RelativeSizeSpan
import android.text.style.ForegroundColorSpan
import android.view.KeyEvent
import android.view.View
import android.view.ViewTreeObserver
import android.view.Gravity
import android.graphics.Typeface
import android.graphics.Color
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.StateListDrawable
import android.widget.Button
import android.widget.FrameLayout
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
    private var questionVoiceEnabled = true
    private var questionRoot: View? = null
    private var questionPromptGeneration = 0
    private var takeawayVoiceEnabled = true
    private var studySpeech: StudySpeech? = null
    private var manualFeedbackAdvance = false
    private var takeawayLine: StudyLine? = null
    private val lessonExplanations by lazy {
        assets.open("lesson_explanations.json").bufferedReader(Charsets.UTF_8).use {
            JSONObject(it.readText())
        }
    }
    private var timerSeconds = CountdownSettings.DEFAULT_SECONDS
    private val timerHandler = Handler(Looper.getMainLooper())
    private var timerRunning = false
    private var timerDeadlineMs = 0L
    private var timerRemainingMs = 0L
    private var timerLabel: TextView? = null
    private var autoAdvanceTask: Runnable? = null
    private val studyButtons = linkedMapOf<String, Button>()
    private var studyFooter: LinearLayout? = null
    private val optionOrder = mutableMapOf<String, List<String>>()
    private val answerMarkers = mutableMapOf<String, TextView>()
    private var answerCountdown: AnswerCountdown? = null
    private var answerCountdownPausedAt: Long? = null
    private var answerLocked = false
    private var selectionOutline: GradientDrawable? = null
    private val answerCountdownTick = object : Runnable {
        override fun run() {
            val countdown = answerCountdown ?: return
            val question = currentQuestion ?: return
            val now = SystemClock.elapsedRealtime()
            val remaining = countdown.remainingMs(now)
            if (remaining == 0L && !answerLocked) lockAnswerCountdown()
            val untilReveal = countdown.untilRevealMs(now)
            if (untilReveal > 0L) {
                drawHeartbeat(countdown, now)
                val nextBoundary = if (remaining > 0L) remaining else untilReveal
                timerHandler.postDelayed(this, minOf(nextBoundary, 32L))
                return
            }
            stopAnswerCountdown()
            if (countdown.choice != question.answer) register(question, Rating.AGAIN)
            revealAnswer(question, countdown.choice)
        }
    }
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
            timerLabel?.text = CountdownSettings.display(remain)
            timerLabel?.setTextColor(if (remain <= 10_000L) ink else muted)
            timerHandler.postDelayed(this, 250L)
        }
    }
    private var selectedVideoDeckId: String? = null
    private var updateChecking = false
    private var updateInstalling = false
    private var availableUpdate: UpdateRelease? = null
    private var updateMessage: String? = null

    private val paper = Color.rgb(32, 31, 30)
    private val surface = Color.rgb(43, 42, 40)
    private val ink = Color.rgb(228, 224, 216)
    private val muted = Color.rgb(178, 172, 161)
    private val hairline = Color.rgb(93, 88, 81)
    private val focusFill = Color.rgb(62, 59, 54)
    private val pulseInk = Color.rgb(214, 168, 98)
    private val correctInk = Color.rgb(143, 184, 153)
    private val wrongInk = Color.rgb(218, 153, 153)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
        try {
            val json = assets.open("questions.json").bufferedReader(Charsets.UTF_8).use { it.readText() }
            decks = QuestionLoader.load(json)
            loadProgress()
            soundEnabled = prefs.getBoolean("sound_enabled", true)
            questionVoiceEnabled = prefs.getBoolean("question_voice", true)
            takeawayVoiceEnabled = prefs.getBoolean("takeaway_voice", true)
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
    private fun shape(color: Int, outline: Int, stroke: Int = 1): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = px(12).toFloat()
            setStroke(px(stroke), outline)
        }

    private fun buttonBg(): StateListDrawable = StateListDrawable().apply {
        addState(intArrayOf(android.R.attr.state_pressed), shape(focusFill, ink, 3))
        addState(intArrayOf(android.R.attr.state_focused), shape(surface, ink, 3))
        addState(intArrayOf(), shape(surface, hairline))
    }
    private fun stopCountdown() {
        timerRunning = false
        timerHandler.removeCallbacks(timerTick)
        timerDeadlineMs = 0L
        timerRemainingMs = 0L
        timerLabel = null
    }

    private fun startCountdown(header: LinearLayout) {
        if (timerSeconds == 0) return
        val label = TextView(this).apply {
            text = CountdownSettings.display(timerSeconds * 1000L)
            textSize = scaledSp(23f)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            includeFontPadding = false
            setTextColor(ink)
            background = shape(surface, hairline)
            setPadding(px(22), px(9), px(22), px(9))
            contentDescription = "Kalan süre"
        }
        header.addView(label, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ))
        timerLabel = label
        timerRemainingMs = timerSeconds * 1000L
        timerDeadlineMs = SystemClock.elapsedRealtime() + timerRemainingMs
        timerRunning = true
        timerHandler.post(timerTick)
    }

    /**
     * Only five independent, full-width buttons; no side-by-side layout.
     * Button height follows wrapped content, so long options never overlap.
     * D-pad focus automatically scrolls the question viewport when necessary.
     */
    private fun studyOptionButtons(box: LinearLayout, question: Question): Button? {
        var first: Button? = null
        studyButtons.clear()
        answerMarkers.clear()
        val order = shuffledOptions(question.options.keys, optionOrder[question.id])
        optionOrder[question.id] = order
        val longest = question.options.values.maxOfOrNull { it.length } ?: 0
        val total = question.options.values.sumOf { it.length }
        val answerFont = StudyLayoutRules.answerSp(longest, total)
        val h = resources.displayMetrics.heightPixels
        for ((index, letter) in order.withIndex()) {
            val choice = question.options.getValue(letter)
            val button = Button(this).apply {
                text = "${'A' + index})  $choice"
                isAllCaps = false
                textSize = scaledSp(answerFont)
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(ink)
                background = buttonBg()
                gravity = Gravity.CENTER_VERTICAL or Gravity.START
                includeFontPadding = false
                isSingleLine = false
                maxLines = Int.MAX_VALUE
                ellipsize = null
                setHorizontallyScrolling(false)
                minHeight = (h * 0.095f).toInt().coerceAtLeast(px(48))
                minimumHeight = minHeight
                setPaddingRelative(px(22), px(12), px(57), px(12))
                setOnClickListener { selectAnswer(question, letter) }
                setOnFocusChangeListener { view, focused ->
                    view.animate().scaleX(if (focused) 1.008f else 1f)
                        .scaleY(if (focused) 1.008f else 1f).setDuration(120L).start()
                }
            }
            studyButtons[letter] = button
            val counter = TextView(this).apply {
                visibility = View.GONE
                setTextColor(ink)
                textSize = scaledSp(23f)
                typeface = Typeface.DEFAULT_BOLD
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }
            answerMarkers[letter] = counter
            val row = FrameLayout(this).apply {
                addView(button, FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT
                ))
                addView(counter, FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.END or Gravity.CENTER_VERTICAL
                ).apply { marginEnd = px(24) })
            }
            box.addView(row, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = px(9) })
            if (first == null) first = button
        }
        return first
    }

    /**
     * Study-only fullscreen view: timer at top, question and five buttons
     * naturally spread over available TV height. On unusually long questions
     * the whole content scrolls rather than clipping answer text.
     */
    private fun renderStudyQuestion(question: Question) {
        studySpeech?.stop()
        manualFeedbackAdvance = false
        takeawayLine = null
        autoAdvanceTask?.let { timerHandler.removeCallbacks(it) }
        autoAdvanceTask = null
        stopCountdown()
        soundEngine?.release()
        stopAnswerCountdown()
        studyFooter = null

        val outer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(paper)
            setPadding(px(24), px(12), px(24), px(12))
        }
        val timerRow = LinearLayout(this).apply {
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            orientation = LinearLayout.HORIZONTAL
        }
        outer.addView(timerRow, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = px(6) })
        val viewport = ScrollView(this).apply {
            isFillViewport = true
            isFocusable = false
            isSmoothScrollingEnabled = true
            clipToPadding = false
        }
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(px(4), px(3), px(4), px(3))
        }
        viewport.addView(body)
        outer.addView(viewport, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        ))
        val footer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            visibility = View.GONE
        }
        outer.addView(footer, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = px(10) })
        studyFooter = footer
        setContentView(outer)

        // Avoid source filenames, "Yeni soru" and page labels in the quiz.
        val questionCard = TextView(this).apply {
            text = question.stem
            textSize = scaledSp(StudyLayoutRules.questionSp(question.stem.length))
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(ink)
            gravity = Gravity.CENTER
            includeFontPadding = false
            setLineSpacing(px(3).toFloat(), 1.05f)
            setPadding(px(20), px(16), px(20), px(16))
            minHeight = (resources.displayMetrics.heightPixels * 0.18f).toInt()
            background = shape(surface, hairline)
        }
        body.addView(questionCard, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = px(18) })
        val first = studyOptionButtons(body, question)
        startCountdown(timerRow)
        first?.requestFocus()
        questionRoot = outer
        speakAfterQuestionFrame(question)
    }

    private fun speak(line: StudyLine) {
        val speech = studySpeech ?: StudySpeech(this).also { studySpeech = it }
        speech.speak(line)
    }

    private fun speakAfterQuestionFrame(question: Question) {
        if (!questionVoiceEnabled) return
        val root = questionRoot ?: return
        val generation = ++questionPromptGeneration
        val start = Runnable {
            if (generation == questionPromptGeneration && root === questionRoot &&
                root.isAttachedToWindow && screen == "study" && !inFeedback &&
                currentQuestion?.id == question.id) {
                playStudyPrompt(question)
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && root.isHardwareAccelerated) {
            root.viewTreeObserver.registerFrameCommitCallback(start)
        } else {
            root.viewTreeObserver.addOnDrawListener(object : ViewTreeObserver.OnDrawListener {
                private var posted = false

                override fun onDraw() {
                    if (posted) return
                    posted = true
                    root.postOnAnimation {
                        root.viewTreeObserver.removeOnDrawListener(this)
                        start.run()
                    }
                }
            })
        }
        root.invalidate()
    }

    private fun playStudyPrompt(question: Question) {
        if (!questionVoiceEnabled) return
        val english = isEnglish(question)
        val prompt = StudyText.question(question.stem, english)
        val options = studyButtons.keys.mapIndexed { index, key ->
            val text = question.options.getValue(key)
            StudyText.option(index, text, english)
        }
        val speech = studySpeech ?: StudySpeech(this).also { studySpeech = it }
        speech.speak(listOf(prompt) + options)
    }

    private fun isEnglish(question: Question): Boolean =
        decks.any { it.id == "ingilizce1" && question in it.questions }

    private fun panel(): LinearLayout {
        questionRoot = null
        questionPromptGeneration++
        studySpeech?.stop()
        stopAnswerCountdown()
        soundEngine?.release()
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
            setBackgroundColor(paper)
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
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
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
            setTextColor(ColorStateList.valueOf(ink))
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

    private fun renderHome(focusSettings: Boolean = false) {
        screen = "home"; currentQuestion = null; inFeedback = false; feedbackCanAdvance = false
        val box = panel()
        error?.let { info(box, it) }
        val settings = MenuTile("Ayarlar", icon = android.R.drawable.ic_menu_preferences) { renderSettings() }
        menuGrid(box, listOf(
            MenuTile("Ders Seç", icon = android.R.drawable.ic_menu_agenda) { renderLessons() },
            MenuTile("Günlük Tekrar", icon = android.R.drawable.ic_popup_sync) {
                currentDeck = null
                nextQuestion()
            },
            MenuTile("Ders Videoları", icon = android.R.drawable.ic_media_play) { renderVideos() },
            settings
        ), if (focusSettings) settings.title else null, columns = 2, fillViewport = true)
    }

    private fun tileRow(box: LinearLayout, fillViewport: Boolean): LinearLayout =
        LinearLayout(this).also { row ->
            box.addView(row, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                if (fillViewport) 0 else LinearLayout.LayoutParams.WRAP_CONTENT,
                if (fillViewport) 1f else 0f
            ).apply { if (!fillViewport) topMargin = px(12) })
        }

    private data class MenuTile(
        val title: String, val detail: String = "", val icon: Int = android.R.drawable.ic_menu_agenda,
        val select: () -> Unit
    )

    private fun roundMenuIcon(icon: Int): LayerDrawable {
        fun circle(fill: Int, edge: Int, stroke: Int) = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(fill)
            setStroke(px(stroke), edge)
        }
        val disc = StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), circle(focusFill, ink, 3))
            addState(intArrayOf(android.R.attr.state_focused), circle(surface, ink, 3))
            addState(intArrayOf(), circle(surface, hairline, 1))
        }
        val glyph = requireNotNull(getDrawable(icon)).mutate().apply { setTint(ink) }
        val layer = LayerDrawable(arrayOf(disc, InsetDrawable(glyph, px(19))))
        layer.setBounds(0, 0, px(66), px(66))
        return layer
    }

    private fun menuGrid(
        box: LinearLayout,
        tiles: List<MenuTile>,
        focusTitle: String? = null,
        columns: Int = 3,
        fillViewport: Boolean = false
    ) {
        var first: Button? = null
        var restored: Button? = null
        tiles.chunked(columns).forEach { entries ->
            val row = tileRow(box, fillViewport)
            entries.forEach { tile ->
                val button = Button(this).apply {
                    isAllCaps = false
                    textSize = scaledSp(22f)
                    typeface = Typeface.DEFAULT_BOLD
                    gravity = Gravity.CENTER
                    setTextColor(ink)
                    setPadding(px(13), px(8), px(13), px(8))
                    minimumHeight = px(130)
                    background = null
                    compoundDrawablePadding = px(8)
                    setCompoundDrawables(null, roundMenuIcon(tile.icon), null, null)
                    text = SpannableStringBuilder(tile.title).apply {
                        if (tile.detail.isNotEmpty()) {
                            append("\n")
                            val start = length
                            append(tile.detail)
                            setSpan(RelativeSizeSpan(0.78f), start, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                            setSpan(ForegroundColorSpan(muted),
                                start, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                        }
                    }
                    setOnClickListener { tile.select() }
                }
                row.addView(button, LinearLayout.LayoutParams(
                    0,
                    if (fillViewport) LinearLayout.LayoutParams.MATCH_PARENT else LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                ).apply { if (!fillViewport) marginEnd = px(10) })
                if (first == null) first = button
                if (tile.title == focusTitle) restored = button
            }
            repeat(columns - entries.size) {
                row.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f).apply { marginEnd = px(10) })
            }
        }
        firstFocus(restored ?: first)
    }

    private fun renderSettings(focusTitle: String? = null) {
        screen = "settings"
        val box = panel()
        heading(box, "Ayarlar", 36f)
        if (updateChecking) info(box, "Yeni sürüm kontrol ediliyor...", 18f)
        else if (updateInstalling) info(box, "APK indiriliyor ve doğrulanıyor...", 18f)
        else updateMessage?.let { info(box, it, 18f) }

        fun toggle(label: String, key: String, enabled: Boolean, change: (Boolean) -> Unit) =
            MenuTile(label, if (enabled) "Açık" else "Kapalı") {
                change(!enabled)
                prefs.edit().putBoolean(key, !enabled).apply()
                renderSettings(label)
            }
        val tiles = buildList {
            add(toggle("Soru seslendirmesi", "question_voice", questionVoiceEnabled) { questionVoiceEnabled = it })
            add(toggle("Cevap açıklamasını seslendir", "takeaway_voice", takeawayVoiceEnabled) { takeawayVoiceEnabled = it })
            add(toggle("Ses efektleri", "sound_enabled", soundEnabled) { soundEnabled = it })
            add(MenuTile("Geri sayım", CountdownSettings.label(timerSeconds)) {
                timerSeconds = CountdownSettings.next(timerSeconds)
                prefs.edit().putInt("timer_seconds", timerSeconds).apply()
                renderSettings("Geri sayım")
            })
            availableUpdate?.let { release ->
                if (!updateInstalling) add(MenuTile("Güncelle", "v${release.version}") { installUpdate(release) })
            }
            if (!updateChecking && !updateInstalling) {
                add(MenuTile("Güncellemeleri kontrol et") { checkUpdates(true) })
            }
            add(MenuTile("Ana Menü") { renderHome(focusSettings = true) })
        }
        menuGrid(box, tiles, focusTitle)
    }

    private fun renderLessons() {
        screen = "lessons"; currentQuestion = null; inFeedback = false; feedbackCanAdvance = false
        val box = panel()
        heading(box, "Derslerim", 36f)
        val now = System.currentTimeMillis()
        val tiles = decks.map { deck ->
            val count = deck.questions.count { it.isScorable }
            val due = Scheduler.dueCount(deck.questions, progress, now, turn)
            MenuTile(deck.title, if (count == 0) "Cevap anahtarı bekleniyor" else "$count soru · $due tekrar") {
                if (count == 0) {
                    screen = "unavailable"
                    val notice = panel()
                    heading(notice, deck.title)
                    info(notice, "Bu dersin doğrulanmış cevap anahtarı henüz eklenmedi.")
                    menuGrid(notice, listOf(MenuTile("Derslere dön") { renderLessons() }))
                } else {
                    currentDeck = deck
                    nextQuestion()
                }
            }
        }
        menuGrid(box, tiles + MenuTile("Ana Menü") { renderHome() }, currentDeck?.title)
    }

    private fun nextQuestion() {
        screen="study";inFeedback=false;feedbackCanAdvance=false
        val question=Scheduler.pick(questions(currentDeck),progress,System.currentTimeMillis(),turn)
        if (question==null) { renderDone();return }
        currentQuestion=question
        renderStudyQuestion(question)
    }

    private fun resultShape(highlight: AnswerHighlight): GradientDrawable = when (highlight) {
        AnswerHighlight.CORRECT -> shape(Color.rgb(41, 54, 45), correctInk, 2)
        AnswerHighlight.WRONG -> shape(Color.rgb(56, 42, 43), wrongInk, 2)
        AnswerHighlight.NORMAL -> shape(surface, hairline)
    }

    private fun revealAnswer(q: Question, selected: String?) {
        if (q.id != currentQuestion?.id || (screen != "feedback" && screen != "timeout")) return
        studyButtons.forEach { (letter, button) ->
            button.animate().cancel()
            button.scaleX = 1f
            button.scaleY = 1f
            val highlight = AnswerFeedback.highlight(letter, q.answer, selected)
            button.isSelected = false
            button.background = resultShape(highlight)
            answerMarkers[letter]?.apply {
                text = when (highlight) {
                    AnswerHighlight.CORRECT -> "✓"
                    AnswerHighlight.WRONG -> "×"
                    AnswerHighlight.NORMAL -> ""
                }
                setTextColor(if (highlight == AnswerHighlight.CORRECT) correctInk else wrongInk)
                visibility = if (highlight == AnswerHighlight.NORMAL) View.GONE else View.VISIBLE
            }
        }
        val correct = selected != null && selected == q.answer
        if (soundEnabled && selected != null) {
            if (correct) sounds.correct() else sounds.wrong()
        }
        val line = lessonExplanations.optString(q.id).takeIf(String::isNotBlank)?.let { StudyLine(it) }
        takeawayLine = line
        if (line != null && takeawayVoiceEnabled) speak(line)
        if (correct) {
            showSimpleRatings(q)
        } else {
            feedbackCanAdvance = true
            manualFeedbackAdvance = line != null && takeawayVoiceEnabled
            if (manualFeedbackAdvance) {
                studyFooter?.let { footer ->
                    footer.visibility = View.VISIBLE
                    firstFocus(action(footer, "Sonraki Soru") { nextQuestion() })
                }
            } else {
                val next = Runnable {
                    if (currentQuestion?.id == q.id &&
                        (screen == "feedback" || screen == "timeout")) nextQuestion()
                }
                autoAdvanceTask = next
                timerHandler.postDelayed(next, 2300L)
            }
        }
    }

    private fun showSimpleRatings(q: Question) {
        val footer = studyFooter ?: return
        footer.removeAllViews()
        footer.visibility = View.VISIBLE
        fun addRating(label: String, rating: Rating): Button {
            val button = Button(this).apply {
                text = label
                isAllCaps = false
                textSize = scaledSp(20f)
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                includeFontPadding = false
                minHeight = px(58)
                minimumHeight = px(58)
                setTextColor(ink)
                background = buttonBg()
                setPadding(px(13), px(8), px(13), px(8))
                setOnClickListener {
                    register(q, rating)
                    nextQuestion()
                }
                setOnFocusChangeListener { v, focused ->
                    v.animate().scaleX(if(focused) 1.015f else 1f)
                        .scaleY(if(focused) 1.015f else 1f)
                        .setDuration(130L).start()
                }
            }
            footer.addView(button, LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
            ).apply {
                marginStart = px(6)
                marginEnd = px(6)
            })
            return button
        }
        addRating("Salladım", Rating.GUESSED)
        // EASY places a known question into the day-based repetition schedule,
        // but never deletes it from the bank.
        addRating("Biliyordum", Rating.EASY).requestFocus()
    }

    private fun selectAnswer(question: Question, letter: String) {
        if (question.id != currentQuestion?.id) return
        val now = SystemClock.elapsedRealtime()
        val active = answerCountdown
        val countdown = if (active == null) {
            if (inFeedback || screen != "study") return
            inFeedback = true
            screen = "feedback"
            feedbackCanAdvance = false
            timerLabel?.visibility = View.INVISIBLE
            stopCountdown()
            studySpeech?.stop()
            AnswerCountdown(letter, SystemClock.elapsedRealtime())
        } else {
            if (letter == active.choice || active.remainingMs(now) == 0L) return
            studyButtons[active.choice]?.apply {
                isSelected = false
                background = buttonBg()
            }
            active.select(letter, now)
        }
        answerCountdown = countdown
        selectionOutline = shape(Color.rgb(52, 44, 33), pulseInk, 2)
        studyButtons[letter]?.apply {
            isSelected = true
            background = selectionOutline
        }
        timerHandler.removeCallbacks(answerCountdownTick)
        answerCountdownTick.run()
    }

    private fun drawHeartbeat(countdown: AnswerCountdown, now: Long) {
        val phase = (now - countdown.startedAtMs) % 960L
        val first = (1f - kotlin.math.abs(phase - 90L) / 90f).coerceAtLeast(0f)
        val second = (1f - kotlin.math.abs(phase - 260L) / 78f).coerceAtLeast(0f) * 0.7f
        val pulse = maxOf(first, second)
        val edge = Color.rgb((154 + 60 * pulse).toInt(), (113 + 55 * pulse).toInt(), (64 + 34 * pulse).toInt())
        selectionOutline?.setStroke(px(2) + (px(3) * pulse).toInt(), edge)
        selectionOutline?.setColor(Color.rgb((52 + 13 * pulse).toInt(), (44 + 8 * pulse).toInt(), (33 + 3 * pulse).toInt()))
    }

    private fun lockAnswerCountdown() {
        answerLocked = true
        studyButtons.values.forEach {
            it.animate().cancel()
            it.scaleX = 1f
            it.scaleY = 1f
            it.isEnabled = false
            it.isFocusable = false
        }
    }

    private fun stopAnswerCountdown() {
        timerHandler.removeCallbacks(answerCountdownTick)
        answerCountdown = null
        answerCountdownPausedAt = null
        selectionOutline = null
        answerLocked = false
    }

    private fun showTimeout(q: Question) {
        if (inFeedback || screen != "study" || currentQuestion?.id != q.id) return
        inFeedback = true
        screen = "timeout"
        feedbackCanAdvance = false
        timerRunning = false
        timerHandler.removeCallbacks(timerTick)
        timerLabel?.visibility = View.INVISIBLE
        studySpeech?.stop()
        studyButtons.values.forEach { it.isEnabled = false; it.isFocusable = false }
        register(q, Rating.AGAIN, timedOut = true)
        revealAnswer(q, null)
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
        val box = panel()
        heading(box, "Şimdilik tamamlandı")
        info(box, "Yeni soru veya zamanı gelen tekrar kalmadı.")
        menuGrid(box, listOf(
            MenuTile("Dersler") { renderLessons() },
            MenuTile("Ana Menü") { renderHome() }
        ))
    }

    private fun installTvBrowser() {
        val storeUrl = Uri.parse("market://details?id=com.phlox.tvwebbrowser")
        try {
            startActivity(Intent(Intent.ACTION_VIEW, storeUrl))
        } catch (_: Exception) {
            // Some Android TV boxes ship without the Play Store.
            screen = "video-help"
            val box = panel()
            heading(box, "TV Tarayıcısı Kurulumu")
            info(box, "Google Play Store uygulamasını açıp TV Bro ara ve yükle.")
            info(box, "Google Play Store yoksa bilgisayarından TV Bro'nun resmî GitHub sürümünü kurabilirsin.")
            menuGrid(box, listOf(MenuTile("Video Derslere Dön") { renderVideos() }))
        }
    }

    private fun openVideoLink(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: Exception) {
            // A stock Android TV installation may have no app handling HTTPS links.
            screen = "video-help"
            val box = panel()
            heading(box, "Bağlantı Açılamadı")
            info(box, "Bu bağlantıyı açacak uygulama bulunamadı. TV Bro, kumandayla kullanılabilen bir TV tarayıcısıdır.")
            info(box, "TV Bro'yu yükledikten sonra bu menüden ders bağlantısını yeniden aç.")
            info(box, url, 17f)
            menuGrid(box, listOf(
                MenuTile("TV Bro'yu Yükle", "Google Play") { installTvBrowser() },
                MenuTile("Video Derslere Dön") { renderVideos() }
            ))
        }
    }


    /** Open a selected YouTube video, not a generic course website.
     *  Prefer a native TV YouTube player; fall back to the exact watch URL.
     */
    private fun openYouTubeLesson(videoId: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("vnd.youtube:$videoId")))
        } catch (_: Exception) {
            openVideoLink("https://www.youtube.com/watch?v=$videoId")
        }
    }

    private fun renderVideoCourse(deck: Deck) {
        screen = "video-course"
        selectedVideoDeckId = deck.id
        val box = panel()
        heading(box, deck.title)
        val videos = VideoCatalog.forDeck(deck.id)
        val tiles = if (videos.isEmpty()) {
            info(box, "Bu ders için doğrudan açılabilen bir video doğrulanamadı.")
            listOf(MenuTile("Resmî AUZEF Öğrenci Sistemi", "Giriş gerekir") {
                openVideoLink("https://aksis.istanbul.edu.tr")
            })
        } else {
            info(box, "Bağımsız eğitim videoları", 17f)
            videos.map { video -> MenuTile(video.title, "YouTube") { openYouTubeLesson(video.youtubeId) } }
        }
        menuGrid(box, tiles + MenuTile("Ders Videolarına Dön") { renderVideos() })
    }

    private fun renderVideos() {
        screen = "videos"
        val box = panel()
        heading(box, "Ders Videoları", 36f)
        val tiles = decks.map { deck ->
            val count = VideoCatalog.forDeck(deck.id).size
            MenuTile(deck.title, if (count == 0) "Video bulunamadı" else "$count video") {
                renderVideoCourse(deck)
            }
        } + listOf(
            MenuTile("TV Bro", "Tarayıcı yükle") { installTvBrowser() },
            MenuTile("Ana Menü") { renderHome() }
        )
        menuGrid(box, tiles, decks.firstOrNull { it.id == selectedVideoDeckId }?.title)
        selectedVideoDeckId = null
    }

    private fun checkUpdates(manual: Boolean) {
        if (updateChecking) return
        updateChecking = true
        if (manual) updateMessage = "GitHub sürümleri kontrol ediliyor..."
        if (screen == "settings") renderSettings()
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
                if (screen == "settings") renderSettings()
                else if (screen == "home" && found != null) renderHome()
            }
        }.start()
    }

    private fun installUpdate(release: UpdateRelease) {
        if (updateInstalling) return
        updateInstalling = true
        updateMessage = "APK indiriliyor..."
        renderSettings()
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
                if (screen == "settings") renderSettings()
            }
        }.start()
    }

    override fun onPause() {
        questionPromptGeneration++
        studySpeech?.stop()
        soundEngine?.release()
        if (answerCountdown != null) {
            answerCountdownPausedAt = SystemClock.elapsedRealtime()
            timerHandler.removeCallbacks(answerCountdownTick)
        }
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
        if (screen == "study" && !inFeedback) {
            currentQuestion?.let(::speakAfterQuestionFrame)
        } else if (takeawayVoiceEnabled && (screen == "feedback" || screen == "timeout")) {
            takeawayLine?.let(::speak)
        }
        val pausedAt = answerCountdownPausedAt
        val countdown = answerCountdown
        if (pausedAt != null && countdown != null) {
            val pausedFor = SystemClock.elapsedRealtime() - pausedAt
            answerCountdown = countdown.copy(startedAtMs = countdown.startedAtMs + pausedFor)
            answerCountdownPausedAt = null
            answerCountdownTick.run()
        } else if ((screen == "feedback" || screen == "timeout") &&
            inFeedback && feedbackCanAdvance && !manualFeedbackAdvance) {
            currentQuestion?.let { q ->
                autoAdvanceTask?.let { timerHandler.removeCallbacks(it) }
                val next = Runnable {
                    if (currentQuestion?.id == q.id && inFeedback) nextQuestion()
                }
                autoAdvanceTask = next
                timerHandler.postDelayed(next, 1600L)
            }
        }
        if (screen == "study" && !inFeedback && timerSeconds > 0 &&
            timerRemainingMs > 0L && timerLabel != null && !timerRunning) {
            timerDeadlineMs = SystemClock.elapsedRealtime() + timerRemainingMs
            timerRunning = true
            timerHandler.post(timerTick)
        }
    }

    override fun onDestroy() {
        stopCountdown()
        stopAnswerCountdown()
        autoAdvanceTask?.let { timerHandler.removeCallbacks(it) }
        soundEngine?.release()
        studySpeech?.close()
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
            "study", "feedback", "timeout", "done", "unavailable" -> renderLessons()
            "video-help" -> {
                val deck = decks.firstOrNull { it.id == selectedVideoDeckId }
                if (deck == null) renderVideos() else renderVideoCourse(deck)
            }
            "video-course" -> renderVideos()
            "settings" -> renderHome(focusSettings = true)
            "lessons", "videos" -> renderHome()
            else -> super.onBackPressed()
        }
    }
}
