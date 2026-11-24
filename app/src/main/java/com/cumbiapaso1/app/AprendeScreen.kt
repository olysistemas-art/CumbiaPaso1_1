package com.cumbiapaso1.app

import android.content.Context
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView

/** Pestaña Aprende: ideas básicas de la cumbia y cómo usar la app. */
class AprendeScreen(private val ctx: Context, private val ui: Ui) : Screen {

    override val view: View = build()

    private fun build(): View {
        val scroll = ScrollView(ctx)
        val col = LinearLayout(ctx)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(ui.dp(18), ui.dp(20), ui.dp(18), ui.dp(24))
        scroll.addView(col, LinearLayout.LayoutParams(MATCH, WRAP))

        col.addView(ui.tv("Aprende cumbia", 24f, Pal.WHITE, true))

        add(col, "¿Qué es el tiempo 1?",
            "Cuenta 1-2-3-4 con la música. El tiempo 1 es el primer golpe grave del compás: " +
                "casi siempre lo marca el bajo o la tambora. Si aprendes a encontrarlo, el paso cae solo.")
        add(col, "Qué escuchar",
            "Güira: el \"chi-chi\" constante y agudo. Ignórala para ubicarte.\n" +
                "Bajo: el golpe grave que te dice dónde están el 1 y el 3.\n" +
                "Conga o tambora: el tambor con el tumbao.\n" +
                "Timbal o caja: marca los tiempos 2 y 4.")
        add(col, "Paso básico (guía simple)",
            "En el 1 pisa con el pie inicial hacia atrás, rebota en el 2 y junta los pies en el 3-4. " +
                "Cambia de pie en el siguiente 1. Practica primero sin música, solo con la pestaña Ritmos.")
        add(col, "Cómo usar la app",
            "Ritmos: escucha y edita el patrón de cada instrumento; sube y baja el BPM.\n" +
                "Música: carga una canción de tu teléfono; la app detecta su BPM y vibra en cada golpe grave. " +
                "Puedes ponerle la guía encima y bajar la velocidad para practicar.\n" +
                "Práctica: usa el micrófono para sentir el bajo de una fiesta o un sonido en vivo.")
        add(col, "Tus propios sonidos",
            "Los sonidos de los instrumentos son sintéticos por defecto. Para usar tus audios, " +
                "colócalos en app/src/main/res/raw/ con los nombres guira, bajo, conga y timbal " +
                "(formato wav, mp3 u ogg, cortos: 1 a 3 segundos) y vuelve a compilar.")
        return scroll
    }

    private fun add(col: LinearLayout, title: String, body: String) {
        val card = ui.card()
        card.addView(ui.tv(title, 16f, Pal.GOLD, true))
        val b = ui.tv(body, 14f, Pal.WHITE, false)
        b.setLineSpacing(ui.dp(3).toFloat(), 1f)
        card.addView(b, ui.lp(top = 6))
        col.addView(card, ui.lp(top = 12))
    }

    override fun onLeave() {}
}
