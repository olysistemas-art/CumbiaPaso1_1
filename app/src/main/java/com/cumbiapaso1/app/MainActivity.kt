package com.cumbiapaso1.app

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {

    private lateinit var store: Store
    private lateinit var vibe: Vibe
    private lateinit var samples: Samples
    private lateinit var model: RhythmModel
    private lateinit var ui: Ui

    private lateinit var ritmos: RitmosScreen
    private lateinit var musica: MusicaScreen
    private lateinit var practica: PracticaScreen
    private lateinit var aprende: AprendeScreen
    private lateinit var screens: List<Screen>
    private val navItems = ArrayList<TextView>()
    private var current = 0
    private lateinit var splash: View
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ui = Ui(this)
        store = Store(this)
        vibe = Vibe(this)
        samples = Samples(this)
        model = RhythmModel(store)

        ritmos = RitmosScreen(this, ui, store, model, samples)
        musica = MusicaScreen(this, ui, store, model, samples, vibe) { pickAudio() }
        practica = PracticaScreen(this, ui, store, vibe, { hasMic() }, { requestMic() })
        aprende = AprendeScreen(this, ui)
        screens = listOf(ritmos, musica, practica, aprende)

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(Pal.BG)

        val content = FrameLayout(this)
        for (s in screens) content.addView(s.view, FrameLayout.LayoutParams(MATCH, MATCH))
        root.addView(content, LinearLayout.LayoutParams(MATCH, 0, 1f))
        root.addView(buildNav(), LinearLayout.LayoutParams(MATCH, WRAP))

        val stack = FrameLayout(this)
        stack.addView(root, FrameLayout.LayoutParams(MATCH, MATCH))
        splash = buildSplash()
        stack.addView(splash, FrameLayout.LayoutParams(MATCH, MATCH))
        setContentView(stack)

        select(0)
        handler.postDelayed({ splash.visibility = View.GONE }, 1200)
    }

    private fun buildSplash(): View {
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.gravity = Gravity.CENTER
        box.setBackgroundColor(Pal.BG)
        box.setPadding(ui.dp(32), ui.dp(32), ui.dp(32), ui.dp(32))
        box.addView(ui.tv("CUMBIA", 48f, Pal.RED, true))
        box.addView(ui.tv("PASO 1", 48f, Pal.WHITE, true))
        val sub = ui.tv("Siente el golpe grave del bajo y vibra en el tiempo 1 de la cumbia.", 15f, Pal.GRAY, false)
        sub.gravity = Gravity.CENTER
        box.addView(sub, ui.lp(top = 14))
        return box
    }

    private fun buildNav(): View {
        val bar = LinearLayout(this)
        bar.orientation = LinearLayout.HORIZONTAL
        bar.setBackgroundColor(android.graphics.Color.parseColor("#12121F"))
        val labels = listOf("🥁\nRitmos", "🎵\nMúsica", "🎯\nPráctica", "📖\nAprende")
        for (i in 0 until labels.size) {
            val t = ui.tv(labels[i], 12f, Pal.GRAY, true)
            t.gravity = Gravity.CENTER
            t.setPadding(0, ui.dp(10), 0, ui.dp(10))
            t.setOnClickListener { select(i) }
            bar.addView(t, LinearLayout.LayoutParams(0, WRAP, 1f))
            navItems.add(t)
        }
        return bar
    }

    private fun select(i: Int) {
        if (i != current) screens[current].onLeave()
        current = i
        for (k in 0 until screens.size) {
            screens[k].view.visibility = if (k == i) View.VISIBLE else View.GONE
            navItems[k].setTextColor(if (k == i) Pal.RED else Pal.GRAY)
        }
    }

    // ------------------------------------------------------------ permisos y archivos

    private fun hasMic(): Boolean =
        checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun requestMic() {
        requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 100)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100) {
            val ok = grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
            practica.onMicResult(ok)
        }
    }

    private fun pickAudio() {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT)
        i.addCategory(Intent.CATEGORY_OPENABLE)
        i.type = "audio/*"
        @Suppress("DEPRECATION")
        startActivityForResult(i, 200)
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 200 && resultCode == RESULT_OK) {
            val uri = data?.data
            if (uri != null) {
                try {
                    contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } catch (e: Exception) {
                    // no es necesario
                }
                select(1)
                musica.onPicked(uri)
            }
        }
    }

    // ------------------------------------------------------------ ciclo de vida

    override fun onPause() {
        super.onPause()
        // Android no permite audio ni micrófono en segundo plano sin servicio.
        for (s in screens) s.onLeave()
    }

    override fun onDestroy() {
        musica.destroy()
        samples.release()
        super.onDestroy()
    }
}
