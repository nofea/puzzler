package com.sheen.puzzler.fx

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

class Haptics(context: Context) {
    private val vibrator: Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        }

    var enabled = true

    private fun vibrate(effect: VibrationEffect) {
        val v = vibrator ?: return
        if (!enabled || !v.hasVibrator()) return
        runCatching { v.vibrate(effect) }
    }

    fun tick() = vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
    fun match() = vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
    fun heavy() = vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK))
    fun win() = vibrate(VibrationEffect.createWaveform(longArrayOf(0, 60, 80, 60, 80, 160), -1))
}
