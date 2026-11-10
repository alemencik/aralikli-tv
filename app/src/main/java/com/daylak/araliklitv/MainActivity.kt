package com.daylak.araliklitv

import android.app.Activity
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
        } catch (e: Exception) {
            error = "Soru bankası okunamadı: ${e.message ?: "bilinmeyen hata"}"
        }
        renderHome()
    }

    private fun px(dp: Int): Int = (resources.displayMetrics.density * dp).toInt()
    private fun shape(color: Int, outline: Int): GradientDrawable = GradientDrawable().apply {
        setColor(color); cornerRadius = px(12).toFloat(); setStroke(px(2), outline)
    }
    private fun buttonBg(): StateListDrawable = StateListDrawable().apply {
        addState(intArrayOf(android.R.attr.state_focused), shape(accent, Color.WHITE))
        addState(intArrayOf(), shape(Color.rgb(27,43,62), Color.rgb(65,83,105)))
    }
    private fun panel(): LinearLayout {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(px(46), px(34), px(46), px(40))
        }
        val view = ScrollView(this).apply {
            setBackgroundColor(this@MainActivity.background)
            isFillViewport = true
            addView(content)
        }
        setContentView(view)
        return content
    }
    private fun heading(box: LinearLayout, text: String, size: Float = 32f) {
        box.addView(TextView(this).apply {
            this.text = text; setTextColor(ink); textSize = size
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
        heading(box, "ARALIKLI TV")
        val all = questions(null)
        val now = System.currentTimeMillis()
        info(box,"${all.count { it.isScorable }} doğrulanmış soru  •  ${Scheduler.dueCount(all,progress,now,turn)} zamanı gelen tekrar")
        error?.let { info(box,it) }
        val primary = action(box,"Ders Seç") { renderLessons() }
        action(box,"Günlük Tekrar (Tüm Dersler)") {
            currentDeck = null; nextQuestion()
        }
        action(box,"İstatistikler") { renderStats() }
        info(box,"Cevap anahtarı bulunmayan dersler listede görünür, ancak yanlış değerlendirme yapılmaz.",16f)
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
        heading(box,if (right) "DOĞRU CEVAP" else "YANLIŞ CEVAP")
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
            "lessons", "stats" -> renderHome()
            else -> super.onBackPressed()
        }
    }
}
