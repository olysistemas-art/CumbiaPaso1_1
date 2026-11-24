package com.cumbiapaso1.app

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

class Vibe(ctx: Context) {
    @Suppress("DEPRECATION")
    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= 31) {
        val vm = ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        vm.defaultVibrator
    } else {
        ctx.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    /** level: 0 suave, 1 media, 2 fuerte. Un solo pulso corto. */
    fun pulse(level: Int) {
        val ms: Long
        val amp: Int
        when (level) {
            0 -> { ms = 35L; amp = 70 }
            2 -> { ms = 110L; amp = 255 }
            else -> { ms = 65L; amp = 150 }
        }
        try {
            vibrator?.vibrate(VibrationEffect.createOneShot(ms, amp))
        } catch (e: Exception) {
            // ignorar
        }
    }
}
