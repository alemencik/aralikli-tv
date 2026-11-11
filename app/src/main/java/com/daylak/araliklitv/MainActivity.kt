package com.daylak.araliklitv

import android.app.Activity
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Intent
import android.net.Uri
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
import android.view.Gravity
import android.graphics.Typeface
import android.graphics.Color
import android.content.res.ColorStateList
import android.graphics.drawable.ClipDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.StateListDrawable
import android.widget.Button
import android.widget.FrameLayout
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
    private var questionVoiceEnabled = true
    private var takeawayEnabled = true
    private var takeawayVoiceEnabled = true
    private var studySpeech: StudySpeech? = null
    private var takeawayCard: TextView? = null
    private var studyViewport: ScrollView? = null
    private var manualFeedbackAdvance = false
    private var takeawayLine: StudyLine? = null
    private val englishExplanations by lazy {
        assets.open("english_explanations.json").bufferedReader(Charsets.UTF_8).use {
            JSONObject(it.readText())
        }
    }
    private val localShowAssets by lazy { LocalShowAssets(this) }
    private var stageMode = StageBackgroundMode.ATHENS
    private var timerSeconds = CountdownSettings.DEFAULT_SECONDS
    private val timerHandler = Handler(Looper.getMainLooper())
    private var timerRunning = false
    private var timerDeadlineMs = 0L
    private var timerRemainingMs = 0L
    private var timerLabel: TextView? = null
    private var timerBar: ProgressBar? = null
    private var autoAdvanceTask: Runnable? = null
    private val studyButtons = linkedMapOf<String, Button>()
    private var studyFooter: LinearLayout? = null
    private val optionOrder = mutableMapOf<String, List<String>>()
    private val answerCounters = mutableMapOf<String, TextView>()
    private var answerCountdown: AnswerCountdown? = null
    private var answerCountdownPausedAt: Long? = null
    private var countdownFill: ClipDrawable? = null
    private val answerCountdownTick = object : Runnable {
        override fun run() {
            val countdown = answerCountdown ?: return
            val question = currentQuestion ?: return
            val now = SystemClock.elapsedRealtime()
            val remaining = countdown.remainingMs(now)
            if (remaining > 0L) {
                drawAnswerCountdown(countdown, remaining)
                timerHandler.postDelayed(this, minOf(remaining, 32L))
                return
            }
            lockAnswerCountdown(countdown)
            val untilReveal = countdown.untilRevealMs(now)
            if (untilReveal > 0L) {
                timerHandler.postDelayed(this, untilReveal)
                return
            }
            stopAnswerCountdown()
            if (countdown.choice != question.answer) register(question, Rating.AGAIN)
            revealAnswer(question, countdown.choice)
        }
    }
    private val answerFlashes = mutableListOf<AnimatorSet>()
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
            timerLabel?.setTextColor(if (remain <= 10_000L)
                Color.rgb(255, 188, 96) else ink)
            timerBar?.progress = if (timerSeconds == 0) 0
                else ((remain * 1000L) / (timerSeconds * 1000L)).toInt()
            timerHandler.postDelayed(this, 250L)
        }
    }
    private var selectedVideoDeckId: String? = null
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
            questionVoiceEnabled = prefs.getBoolean("question_voice", true)
            takeawayEnabled = prefs.getBoolean("takeaway_visible", true)
            takeawayVoiceEnabled = prefs.getBoolean("takeaway_voice", true)
            val previousStage = if (prefs.getBoolean("original_theme_enabled", true))
                StageBackgroundMode.ATHENS else StageBackgroundMode.NEON
            stageMode = StageBackgroundMode.entries.firstOrNull {
                it.name == prefs.getString("stage_background", null)
            } ?: previousStage
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
    /** Violet question card distinguishes the prompt from navy-blue options. */
    private fun questionBg(): GradientDrawable =
        GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
            intArrayOf(Color.rgb(43, 25, 81), Color.rgb(25, 15, 60))).apply {
            cornerRadius = px(16).toFloat()
            setStroke(px(3), Color.rgb(221, 177, 85))
        }
    private fun buttonBg(): StateListDrawable = StateListDrawable().apply {
        addState(intArrayOf(android.R.attr.state_pressed),
            shape(Color.rgb(105,72,19), Color.rgb(255,226,116)))
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

    private fun startCountdown(header: LinearLayout) {
        if (timerSeconds == 0) return
        val label = TextView(this).apply {
            text = CountdownSettings.display(timerSeconds * 1000L)
            textSize = scaledSp(23f)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            includeFontPadding = false
            setTextColor(ink)
            background = shape(Color.rgb(10, 28, 76), Color.rgb(44, 191, 238))
            setPadding(px(22), px(9), px(22), px(9))
            contentDescription = "Kalan süre"
        }
        header.addView(label, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ))
        timerLabel = label
        timerBar = null
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
        answerCounters.clear()
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
                setTextColor(Color.WHITE)
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
                setTextColor(Color.WHITE)
                textSize = scaledSp(23f)
                typeface = Typeface.DEFAULT_BOLD
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }
            answerCounters[letter] = counter
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
        clearOptionAnimations()
        autoAdvanceTask?.let { timerHandler.removeCallbacks(it) }
        autoAdvanceTask = null
        stopCountdown()
        soundEngine?.release()
        stopAnswerCountdown()
        studyFooter = null

        val outer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = localShowAssets.stageBackground(stageMode)
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
        studyViewport = viewport
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
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            includeFontPadding = false
            setLineSpacing(px(3).toFloat(), 1.05f)
            setPadding(px(20), px(16), px(20), px(16))
            minHeight = (resources.displayMetrics.heightPixels * 0.18f).toInt()
            background = questionBg()
        }
        body.addView(questionCard, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = px(18) })
        val first = studyOptionButtons(body, question)
        val card = TextView(this).apply {
            visibility = View.GONE
            setTextColor(Color.WHITE)
            textSize = scaledSp(23f)
            setPadding(px(22), px(16), px(22), px(16))
            background = shape(Color.rgb(9, 70, 82), Color.rgb(87, 215, 203))
        }
        body.addView(card, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = px(6) })
        takeawayCard = card
        startCountdown(timerRow)
        first?.requestFocus()
        playStudyPrompt(question)
    }

    private fun speak(line: StudyLine) {
        val speech = studySpeech ?: StudySpeech(this).also { studySpeech = it }
        speech.speak(line)
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

    private fun panel(useStage: Boolean = true): LinearLayout {
        studySpeech?.stop()
        studyViewport = null
        takeawayCard = null
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
            background = if (useStage) {
                localShowAssets.stageBackground(stageMode)
            } else {
                GradientDrawable(GradientDrawable.Orientation.TL_BR,
                    intArrayOf(Color.rgb(14, 24, 43), Color.rgb(6, 11, 24)))
            }
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

    private fun renderHome(focusSettings: Boolean = false) {
        screen = "home"; currentQuestion = null; inFeedback = false; feedbackCanAdvance = false
        val box = panel(useStage = false)
        heading(box, "Kim KPSS Kazanmak İster?", 40f)
        val all = questions(null)
        val due = Scheduler.dueCount(all, progress, System.currentTimeMillis(), turn)
        info(box, "${all.count { it.isScorable }} soru  •  $due zamanı gelen tekrar")
        error?.let { info(box, it) }
        val top = tileRow(box)
        val primary = homeTile(top, "Ders Seç", android.R.drawable.ic_menu_agenda, 0xff184d9e.toInt()) {
            renderLessons()
        }
        val daily = homeTile(top, "Günlük Tekrar", android.R.drawable.ic_popup_sync, 0xff10645c.toInt()) {
            currentDeck = null
            nextQuestion()
        }
        val videos = homeTile(top, "Ders Videoları", android.R.drawable.ic_media_play, 0xff62408e.toInt()) {
            renderVideos()
        }
        val bottom = tileRow(box)
        val stats = homeTile(bottom, "İstatistikler", android.R.drawable.ic_menu_sort_by_size, 0xff79512f.toInt()) {
            renderStats()
        }
        val settingsLabel = if (availableUpdate != null) "Ayarlar · Güncelleme var" else "Ayarlar"
        val settings = homeTile(bottom, settingsLabel, android.R.drawable.ic_menu_preferences, 0xff344964.toInt()) {
            renderSettings()
        }
        primary.nextFocusDownId = stats.id
        daily.nextFocusDownId = stats.id
        videos.nextFocusDownId = settings.id
        stats.nextFocusUpId = primary.id
        settings.nextFocusUpId = videos.id
        firstFocus(if (focusSettings) settings else primary)
    }

    private fun tileRow(box: LinearLayout): LinearLayout = LinearLayout(this).also { row ->
        box.addView(row, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = px(12) })
    }

    private fun homeTile(
        row: LinearLayout, caption: String, icon: Int, color: Int, click: () -> Unit
    ): Button = Button(this).apply {
        id = View.generateViewId()
        text = caption
        isAllCaps = false
        textSize = scaledSp(24f)
        typeface = Typeface.DEFAULT_BOLD
        gravity = Gravity.CENTER
        setTextColor(Color.WHITE)
        setPadding(px(12), px(24), px(12), px(20))
        compoundDrawablePadding = px(14)
        val image = getDrawable(icon)?.mutate()?.apply {
            setTint(Color.WHITE)
            setBounds(0, 0, px(41), px(41))
        }
        setCompoundDrawables(null, image, null, null)
        background = StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), shape(color, Color.WHITE))
            addState(intArrayOf(android.R.attr.state_focused), shape(color, Color.rgb(123, 234, 255)))
            addState(intArrayOf(), shape(color, Color.rgb(57, 77, 112)))
        }
        setOnClickListener { click() }
        row.addView(this, LinearLayout.LayoutParams(0, px(130), 1f).apply {
            marginEnd = px(10)
        })
    }

    private data class MenuTile(val title: String, val detail: String = "", val select: () -> Unit)

    private fun menuGrid(box: LinearLayout, tiles: List<MenuTile>, focusTitle: String? = null) {
        val columns = 3
        val palette = intArrayOf(0xff184d9e.toInt(), 0xff10645c.toInt(), 0xff62408e.toInt())
        var first: Button? = null
        var restored: Button? = null
        tiles.chunked(columns).forEachIndexed { rowIndex, entries ->
            val row = tileRow(box)
            entries.forEachIndexed { column, tile ->
                val color = palette[(column + rowIndex) % palette.size]
                val button = Button(this).apply {
                    isAllCaps = false
                    textSize = scaledSp(22f)
                    typeface = Typeface.DEFAULT_BOLD
                    gravity = Gravity.CENTER
                    setTextColor(Color.WHITE)
                    setPadding(px(13), px(12), px(13), px(12))
                    background = StateListDrawable().apply {
                        addState(intArrayOf(android.R.attr.state_pressed), shape(color, Color.WHITE))
                        addState(intArrayOf(android.R.attr.state_focused), shape(color, Color.rgb(123, 234, 255)))
                        addState(intArrayOf(), shape(color, Color.rgb(65, 96, 132)))
                    }
                    text = SpannableStringBuilder(tile.title).apply {
                        if (tile.detail.isNotEmpty()) {
                            append("\n")
                            val start = length
                            append(tile.detail)
                            setSpan(RelativeSizeSpan(0.78f), start, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                            setSpan(ForegroundColorSpan(Color.rgb(192, 218, 247)),
                                start, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                        }
                    }
                    setOnClickListener { tile.select() }
                }
                row.addView(button, LinearLayout.LayoutParams(0, px(112), 1f).apply { marginEnd = px(10) })
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
        val box = panel(useStage = false)
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
        val showImageReady = localShowAssets.hasStage()
        val stageChoices = StageBackgroundMode.entries.filter { it != StageBackgroundMode.LOCAL || showImageReady }
        val activeStage = stageMode.takeIf { it in stageChoices } ?: StageBackgroundMode.ATHENS
        val tiles = buildList {
            add(toggle("Soru seslendirmesi", "question_voice", questionVoiceEnabled) { questionVoiceEnabled = it })
            add(toggle("Hap bilgiyi göster", "takeaway_visible", takeawayEnabled) { takeawayEnabled = it })
            add(toggle("Hap bilgiyi seslendir", "takeaway_voice", takeawayVoiceEnabled) { takeawayVoiceEnabled = it })
            add(toggle("Ses efektleri", "sound_enabled", soundEnabled) { soundEnabled = it })
            add(MenuTile("Yarışma arka planı", activeStage.label) {
                stageMode = stageChoices[(stageChoices.indexOf(activeStage) + 1) % stageChoices.size]
                prefs.edit().putString("stage_background", stageMode.name).apply()
                renderSettings("Yarışma arka planı")
            })
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
        val box = panel(useStage = false)
        heading(box, "Derslerim", 36f)
        val now = System.currentTimeMillis()
        val tiles = decks.map { deck ->
            val count = deck.questions.count { it.isScorable }
            val due = Scheduler.dueCount(deck.questions, progress, now, turn)
            MenuTile(deck.title, if (count == 0) "Cevap anahtarı bekleniyor" else "$count soru · $due tekrar") {
                if (count == 0) {
                    screen = "unavailable"
                    val notice = panel(useStage = false)
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

    /**
     * Same five answers as on the question screen. A wrong selection turns
     * red while the correct choice becomes green, with no duplicate text labels.
     */
    private fun resultShape(highlight: AnswerHighlight): GradientDrawable {
        val colors = when (highlight) {
            AnswerHighlight.CORRECT -> intArrayOf(Color.rgb(10, 117, 51), Color.rgb(3, 55, 34))
            AnswerHighlight.WRONG -> intArrayOf(Color.rgb(157, 25, 40), Color.rgb(87, 14, 34))
            AnswerHighlight.NORMAL -> intArrayOf(Color.rgb(13, 38, 102), Color.rgb(8, 20, 62))
        }
        val edge = when (highlight) {
            AnswerHighlight.CORRECT -> Color.rgb(62, 247, 126)
            AnswerHighlight.WRONG -> Color.rgb(255, 104, 109)
            AnswerHighlight.NORMAL -> Color.rgb(47, 146, 232)
        }
        return GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, colors).apply {
            cornerRadius = px(13).toFloat()
            setStroke(px(3), edge)
        }
    }

    private fun clearOptionAnimations() {
        answerFlashes.forEach { it.cancel() }
        answerFlashes.clear()
    }

    /** Two bright color pulses, ending on the correct final color. */
    private fun flashRevealedOption(button: Button) {
        val flash = ObjectAnimator.ofFloat(button, View.ALPHA, 1f, 0.52f, 1f).apply {
            duration = 520L
            repeatCount = 1
            repeatMode = ValueAnimator.RESTART
        }
        val growX = ObjectAnimator.ofFloat(button, View.SCALE_X, 1f, 1.014f, 1f).apply {
            duration = 520L
            repeatCount = 1
            repeatMode = ValueAnimator.RESTART
        }
        val growY = ObjectAnimator.ofFloat(button, View.SCALE_Y, 1f, 1.014f, 1f).apply {
            duration = 520L
            repeatCount = 1
            repeatMode = ValueAnimator.RESTART
        }
        val animation = AnimatorSet().apply {
            playTogether(flash, growX, growY)
            start()
        }
        answerFlashes.add(animation)
    }

    /**
     * User selection stays gold before the correct option turns green.
     * Wrong selections turn red, while all other options keep their blue style.
     * No second result page and no redundant answer labels.
     */
    private fun revealAnswer(q: Question, selected: String?) {
        if (q.id != currentQuestion?.id || (screen != "feedback" && screen != "timeout")) return
        studyButtons.forEach { (letter, button) ->
            button.animate().cancel()
            button.scaleX = 1f
            button.scaleY = 1f
            val highlight = AnswerFeedback.highlight(letter, q.answer, selected)
            button.background = resultShape(highlight)
            button.alpha = if (highlight == AnswerHighlight.NORMAL) 0.68f else 1f
            if (highlight != AnswerHighlight.NORMAL) flashRevealedOption(button)
        }
        val correct = selected != null && selected == q.answer
        if (soundEnabled && selected != null) {
            if (correct) sounds.correct() else sounds.wrong()
        }
        val answer = q.answer?.let(q.options::get)
        val english = isEnglish(q)
        val line = if (english) {
            englishExplanations.optString(q.id).takeIf(String::isNotBlank)?.let { StudyLine(it) }
        } else {
            answer?.let { StudyText.takeaway(q.stem, it, false) }
        }
        val showTakeaway = takeawayEnabled && !english && line != null
        takeawayLine = line
        if (showTakeaway && line != null) {
            takeawayCard?.apply {
                text = "Hap bilgi\n${line.text}"
                visibility = View.VISIBLE
            }
        }
        if (line != null && takeawayVoiceEnabled) speak(line)
        if (correct) {
            showSimpleRatings(q)
        } else {
            feedbackCanAdvance = true
            manualFeedbackAdvance = line != null && (showTakeaway || takeawayVoiceEnabled)
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
        if (showTakeaway) {
            studyViewport?.let { viewport ->
                viewport.post {
                    if (currentQuestion?.id == q.id) viewport.smoothScrollTo(0, viewport.getChildAt(0).height)
                }
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
                setTextColor(Color.WHITE)
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
        val first = addRating("Salladım", Rating.GUESSED)
        // EASY places a known question into the day-based repetition schedule,
        // but never deletes it from the bank.
        addRating("Biliyordum", Rating.EASY)
        first.requestFocus()
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
            studyButtons[active.choice]?.background = buttonBg()
            answerCounters[active.choice]?.visibility = View.GONE
            active.select(letter, now)
        }
        answerCountdown = countdown
        val fill = ClipDrawable(
            shape(Color.rgb(161, 94, 11), Color.rgb(255, 221, 78)),
            Gravity.LEFT, ClipDrawable.HORIZONTAL
        )
        countdownFill = fill
        studyButtons[letter]?.background = LayerDrawable(arrayOf(
            shape(Color.rgb(69, 43, 19), Color.rgb(255, 221, 78)),
            fill
        ))
        timerHandler.removeCallbacks(answerCountdownTick)
        answerCountdownTick.run()
    }

    private fun drawAnswerCountdown(countdown: AnswerCountdown, remaining: Long) {
        countdownFill?.level = (remaining * 10_000L / AnswerCountdown.DURATION_MS).toInt()
        answerCounters[countdown.choice]?.apply {
            text = ((remaining + 999L) / 1000L).toString()
            visibility = View.VISIBLE
        }
    }

    private fun lockAnswerCountdown(countdown: AnswerCountdown) {
        countdownFill?.level = 0
        answerCounters[countdown.choice]?.visibility = View.GONE
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
        countdownFill = null
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
        val box = panel(useStage = false)
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
            val box = panel(useStage = false)
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
            val box = panel(useStage = false)
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
        val box = panel(useStage = false)
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
        val box = panel(useStage = false)
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

    private fun renderStats() {
        screen = "stats"
        val box = panel(useStage = false)
        heading(box, "İstatistikler", 36f)
        val tiles = decks.map { deck ->
            val seen = deck.questions.count { progress.containsKey(it.id) }
            MenuTile(deck.title, "$seen / ${deck.questions.size} soru görüldü") { renderDeckStats(deck) }
        }
        menuGrid(box, tiles + MenuTile("Ana Menü") { renderHome() })
    }

    private fun renderDeckStats(deck: Deck) {
        screen = "deck-stats"
        val box = panel(useStage = false)
        heading(box, deck.title)
        val entries = deck.questions.mapNotNull { progress[it.id] }
        val attempts = entries.sumOf { it.attempts - it.timeouts }
        val correct = entries.sumOf { it.correct }
        val timeouts = entries.sumOf { it.timeouts }
        val pct = if (attempts == 0) "—" else "%${correct * 100 / attempts}"
        val due = Scheduler.dueCount(deck.questions, progress, System.currentTimeMillis(), turn)
        info(box, "${entries.size} / ${deck.questions.size} soru görüldü   ·   $pct doğru   ·   $timeouts süre aşımı   ·   $due tekrar")
        menuGrid(box, listOf(
            MenuTile("İstatistiklere dön") { renderStats() },
            MenuTile("Ana Menü") { renderHome() }
        ))
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
        studySpeech?.stop()
        answerFlashes.forEach { it.cancel() }
        answerFlashes.clear()
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
            currentQuestion?.let(::playStudyPrompt)
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
        clearOptionAnimations()
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
            "deck-stats" -> renderStats()
            "lessons", "stats", "videos" -> renderHome()
            else -> super.onBackPressed()
        }
    }
}
