/*
 * Copyright (C) 2019 CypherOS
 * Copyright (C) 2014-2020 Paranoid Android
 * Copyright (C) 2023-2024 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.device

import android.animation.Animator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.ColorDrawable
import android.media.AudioManager
import android.view.animation.OvershootInterpolator
import android.view.Gravity
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

import org.lineageos.settings.device.R

class AlertSliderDialog(private val context: Context) : Dialog(context, R.style.alert_slider_theme) {
    private val dialogView by lazy { findViewById<LinearLayout>(R.id.alert_slider_dialog)!! }
    private val frameView by lazy { findViewById<ViewGroup>(R.id.alert_slider_view)!! }
    private val iconView by lazy { findViewById<ImageView>(R.id.alert_slider_icon)!! }
    private val textView by lazy { findViewById<TextView>(R.id.alert_slider_text)!! }

    private val modeIconMap: HashMap<Int, Int> = hashMapOf(
        AudioManager.RINGER_MODE_SILENT to R.drawable.ic_volume_ringer_mute,
        AudioManager.RINGER_MODE_VIBRATE to R.drawable.ic_volume_ringer_vibrate,
        AudioManager.RINGER_MODE_NORMAL to R.drawable.ic_volume_ringer,
        KeyHandler.ZEN_PRIORITY_ONLY to R.drawable.ic_notifications_alert,
        KeyHandler.ZEN_TOTAL_SILENCE to R.drawable.ic_notifications_silence,
        KeyHandler.ZEN_ALARMS_ONLY to R.drawable.ic_alarm
    )

    private val modeTextMap: HashMap<Int, Int> = hashMapOf(
        AudioManager.RINGER_MODE_SILENT to R.string.alert_slider_mode_silent,
        AudioManager.RINGER_MODE_VIBRATE to R.string.alert_slider_mode_vibration,
        AudioManager.RINGER_MODE_NORMAL to R.string.alert_slider_mode_normal,
        KeyHandler.ZEN_PRIORITY_ONLY to R.string.alert_slider_mode_dnd_priority_only,
        KeyHandler.ZEN_TOTAL_SILENCE to R.string.alert_slider_mode_dnd_total_silence,
        KeyHandler.ZEN_ALARMS_ONLY to R.string.alert_slider_mode_dnd_alarms_only
    )

    private var isAnimating = false
    private var animator = ValueAnimator()

    private val positionScale = context.resources.getFraction(R.fraction.alert_slider_dialog_y, 1, 1)
    private val dialogPositionX = context.resources.displayMetrics.widthPixels / 100
    private val dialogPositionY = (context.resources.displayMetrics.heightPixels * positionScale).toInt()
    private val dialogHeight = context.resources.getDimension(R.dimen.alert_slider_dialog_height).toInt()

    init {
        window?.let {
            it.requestFeature(Window.FEATURE_NO_TITLE)
            it.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            it.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            it.addFlags(
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
                        or WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
            )
            it.addPrivateFlags(WindowManager.LayoutParams.PRIVATE_FLAG_TRUSTED_OVERLAY)
            it.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
            it.setType(WindowManager.LayoutParams.TYPE_VOLUME_OVERLAY)
            it.attributes = it.attributes.apply {
                format = PixelFormat.TRANSLUCENT
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                title = TAG
            }
        }

        setCanceledOnTouchOutside(false)
        setContentView(R.layout.alert_slider_dialog)
    }

    @Synchronized
    fun setState(position: Int, ringerMode: Int, flip: Boolean) {
        val viewSize = (dialogPositionY -
                (dialogHeight + dialogView.paddingTop + dialogView.paddingBottom) * 0.5).toInt()
        val viewPosition = when (position) {
            KeyHandler.POSITION_TOP -> (viewSize - (dialogHeight * 1.5).toInt())
            KeyHandler.POSITION_BOTTOM -> (viewSize + (dialogHeight * 1.5).toInt())
            else -> viewSize
        }

        if (isShowing()) {
            animatePosition(viewPosition, position, ringerMode, flip)
        } else {
            updateRingerMode(ringerMode)
            updatePosition(viewPosition, position, flip)
        }
    }

    private fun updateRingerMode(ringerMode: Int) {
        modeIconMap.get(ringerMode)?.let {
            iconView.setImageResource(it)
        } ?: {
            iconView.setImageResource(R.drawable.ic_info)
        }

        modeTextMap.get(ringerMode)?.let {
            textView.setText(it)
        } ?: {
            textView.setText(R.string.alert_slider_mode_none)
        }
    }

    private fun updatePosition(positionY: Int, position: Int, flip: Boolean) {
        frameView.setBackgroundResource(when (position) {
            KeyHandler.POSITION_TOP -> if (flip) {
                R.drawable.alert_slider_top_flip
            } else {
                R.drawable.alert_slider_top
            }
            KeyHandler.POSITION_MIDDLE -> R.drawable.alert_slider_middle
            else -> if (flip) {
                R.drawable.alert_slider_bottom_flip
            } else {
                R.drawable.alert_slider_bottom
            }
        })

        window?.let {
            it.attributes = it.attributes.apply {
                gravity = if (flip) {
                    Gravity.TOP or Gravity.LEFT
                } else {
                    Gravity.TOP or Gravity.RIGHT
                }
                x = dialogPositionX
                y = positionY
            }
        }
    }

    @Synchronized
    private fun animatePosition(positionY: Int, position: Int, ringerMode: Int, flip: Boolean) {
        if (isAnimating) animator.cancel()
        animator = ValueAnimator()
        animator.setDuration(ANIMATION_DURATION)
        animator.setInterpolator(OvershootInterpolator())
        window?.let {
            animator.setValues(
                PropertyValuesHolder.ofInt("gravity", it.attributes.gravity, if (flip) {
                    Gravity.TOP or Gravity.LEFT
                } else {
                    Gravity.TOP or Gravity.RIGHT
                }),
                PropertyValuesHolder.ofInt("x", it.attributes.x, dialogPositionX),
                PropertyValuesHolder.ofInt("y", it.attributes.y, positionY)
            )
        }
        animator.addUpdateListener(object : ValueAnimator.AnimatorUpdateListener {
            override fun onAnimationUpdate(animation: ValueAnimator) {
                window?.let {
                    it.attributes = it.attributes.apply {
                        gravity = animation.getAnimatedValue("gravity") as Int
                        x = animation.getAnimatedValue("x") as Int
                        y = animation.getAnimatedValue("y") as Int
                    }
                }
            }
        })
        animator.addListener(object : Animator.AnimatorListener {
            override fun onAnimationStart(animation: Animator) {
                isAnimating = true
                updateRingerMode(ringerMode)
            }

            override fun onAnimationEnd(animation: Animator) {
                updatePosition(positionY, position, flip)
                isAnimating = false
            }

            override fun onAnimationCancel(animation: Animator) { }
            override fun onAnimationRepeat(animation: Animator) { }
        })
        animator.start()
    }

    companion object {
        private const val TAG = "AlertSliderDialog"
        private const val ANIMATION_DURATION = 100L
    }
}
