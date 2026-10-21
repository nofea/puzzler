package com.sheen.puzzler.ui

import androidx.compose.ui.geometry.Offset

/** Where the system splash drew its orb, in window coordinates, so the intro picks up exactly on top of it. */
data class OrbAnchor(
    val center: Offset,
    val radius: Float,
)
