/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2021-2023 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.text.Spanned
import android.util.AttributeSet
import android.view.Gravity
import android.widget.TextView
import androidx.emoji2.text.EmojiCompat
import androidx.emoji2.text.EmojiCompat.LOAD_STATE_SUCCEEDED
import androidx.emoji2.text.EmojiSpan
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

@SuppressLint("AppCompatCustomView")
class AutoScaleTextView @JvmOverloads constructor(
    context: Context?,
    attributeSet: AttributeSet? = null
) : TextView(context, attributeSet) {

    enum class Mode {
        /**
         * do not scale or ellipse text, overflow when cannot fit width
         */
        None,

        /**
         * only scale in X axis, makes text looks "condensed" or "slim"
         */
        Horizontal,

        /**
         * scale both in X and Y axis, align center vertically
         */
        Proportional
    }

    var scaleMode = Mode.None

    private lateinit var text: String

    private var needsMeasureText = true
    private val fontMetrics = Paint.FontMetrics()
    private val textBounds = Rect()

    private var needsCalculateTransform = true
    private var translateY = 0.0f
    private var translateX = 0.0f
    private var textScaleX = 1.0f
    private var textScaleY = 1.0f
    private val isCompatLoaded = EmojiCompat.get().loadState == LOAD_STATE_SUCCEEDED

    override fun setText(charSequence: CharSequence?, bufferType: BufferType) {
        // setText can be called in super constructor
        if (!::text.isInitialized || charSequence == null || !text.contentEquals(charSequence)) {
            needsMeasureText = true
            needsCalculateTransform = true
            text = charSequence?.toString() ?: ""
            requestLayout()
            invalidate()
        }
    }

    override fun getText(): CharSequence {
        return text
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val widthMode = MeasureSpec.getMode(widthMeasureSpec)
        val widthSize = MeasureSpec.getSize(widthMeasureSpec)
        val heightMode = MeasureSpec.getMode(heightMeasureSpec)
        val heightSize = MeasureSpec.getSize(heightMeasureSpec)
        val width = measureTextBounds().width() + paddingLeft + paddingRight
        val height = ceil(fontMetrics.bottom - fontMetrics.top + paddingTop + paddingBottom).toInt()
        val maxHeight = if (maxHeight >= 0) maxHeight else Int.MAX_VALUE
        val maxWidth = if (maxWidth >= 0) maxWidth else Int.MAX_VALUE
        setMeasuredDimension(
            measure(widthMode, widthSize, min(max(width, minimumWidth), maxWidth)),
            measure(heightMode, heightSize, min(max(height, minimumHeight), maxHeight))
        )
    }

    private fun measure(specMode: Int, specSize: Int, calculatedSize: Int): Int = when (specMode) {
        MeasureSpec.EXACTLY -> specSize
        MeasureSpec.AT_MOST -> min(calculatedSize, specSize)
        else -> calculatedSize
    }

    private fun measureCompatTextBounds(spanned: Spanned) {
        var measured = 0f
        val spans = spanned.getSpans(
            0, spanned.length,
            EmojiSpan::class.java
        )
        if (!spans.isNullOrEmpty()) {
            measured += paint.measureText(text.substring(0, spanned.getSpanStart(spans[0])))
            for (i in spans.indices) {
                measured += spans[i].getSize(
                    paint,
                    spanned,
                    spanned.getSpanStart(spans[i]),
                    spanned.getSpanEnd(spans[i]),
                    paint.getFontMetricsInt()
                ).toFloat()

                val afterText = spanned.subSequence(
                    spanned.getSpanEnd(spans[i]),
                    if (i + 1 < spans.size) spanned.getSpanStart(spans[i + 1]) else spanned.length
                ).toString()
                measured += paint.measureText(afterText)
            }
        }
        textBounds.set(
            /* left = */ 0,
            /* top = */ floor(fontMetrics.top).toInt(),
            /* right = */ ceil(measured).toInt(),
            /* bottom = */ ceil(fontMetrics.bottom).toInt()
        )
    }

    private fun measureTextBounds(): Rect {
        if (needsMeasureText) {
            val paint = paint
            paint.getFontMetrics(fontMetrics)

            var processed: CharSequence? = null
            if (isCompatLoaded) processed = EmojiCompat.get().process(text)

            if (processed is Spanned) {
                measureCompatTextBounds(processed)
            } else {
                val codePointCount = Character.codePointCount(text, 0, text.length)
                if (codePointCount == 1) {
                    // use actual text bounds when there is only one "character",
                    // eg. full-width punctuation
                    paint.getTextBounds(text, 0, text.length, textBounds)
                } else {
                    textBounds.set(
                        /* left = */ 0,
                        /* top = */ floor(fontMetrics.top).toInt(),
                        /* right = */ ceil(paint.measureText(text)).toInt(),
                        /* bottom = */ ceil(fontMetrics.bottom).toInt()
                    )
                }
            }
            needsMeasureText = false
        }
        return textBounds
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        if (needsCalculateTransform || changed) {
            calculateTransform(right - left, bottom - top)
            needsCalculateTransform = false
        }
    }

    private fun calculateTransform(viewWidth: Int, viewHeight: Int) {
        val contentWidth = viewWidth - paddingLeft - paddingRight
        val contentHeight = viewHeight - paddingTop - paddingBottom
        measureTextBounds()
        val textWidth = textBounds.width()
        val leftAlignOffset = (paddingLeft - textBounds.left).toFloat()
        val centerAlignOffset =
            paddingLeft.toFloat() + (contentWidth - textWidth) / 2.0f - textBounds.left.toFloat()

        @SuppressLint("RtlHardcoded")
        val shouldAlignLeft = gravity and Gravity.HORIZONTAL_GRAVITY_MASK == Gravity.LEFT
        if (textWidth >= contentWidth) {
            when (scaleMode) {
                Mode.None -> {
                    textScaleX = 1.0f
                    textScaleY = 1.0f
                    translateX = if (shouldAlignLeft) leftAlignOffset else centerAlignOffset
                }
                Mode.Horizontal -> {
                    textScaleX = contentWidth.toFloat() / textWidth.toFloat()
                    textScaleY = 1.0f
                    translateX = leftAlignOffset
                }
                Mode.Proportional -> {
                    val textScale = contentWidth.toFloat() / textWidth.toFloat()
                    textScaleX = textScale
                    textScaleY = textScale
                    translateX = leftAlignOffset
                }
            }
        } else {
            translateX = if (shouldAlignLeft) leftAlignOffset else centerAlignOffset
            textScaleX = 1.0f
            textScaleY = 1.0f
        }
        val fontHeight = (fontMetrics.bottom - fontMetrics.top) * textScaleY
        val fontOffsetY = fontMetrics.top * textScaleY
        translateY = (contentHeight.toFloat() - fontHeight) / 2.0f - fontOffsetY + paddingTop
    }

    private fun drawCompatText(canvas: Canvas, spanned: Spanned) {
        // https://stackoverflow.com/questions/67569629/canvas-drawtext-problem-with-emojicompat-android-java
        val spans = spanned.getSpans(
            0, spanned.length,
            EmojiSpan::class.java
        )
        if (!spans.isNullOrEmpty()) {
            var x = 0f
            val beforeText = text.substring(0, spanned.getSpanStart(spans[0]))
            canvas.drawText(beforeText, x, 0f, paint)
            x += paint.measureText(beforeText)

            for (i in spans.indices) {
                spans[i].draw(
                    canvas,
                    spanned,
                    spanned.getSpanStart(spans[i]),
                    spanned.getSpanEnd(spans[i]),
                    x,
                    fontMetrics.top.toInt(),
                    0,
                    fontMetrics.bottom.toInt(),
                    paint
                )

                x += spans[i].getSize(
                    paint,
                    spanned,
                    spanned.getSpanStart(spans[i]),
                    spanned.getSpanEnd(spans[i]),
                    paint.getFontMetricsInt()
                ).toFloat()

                val afterText = spanned.subSequence(
                    spanned.getSpanEnd(spans[i]),
                    if (i + 1 < spans.size) spanned.getSpanStart(spans[i + 1]) else spanned.length
                ).toString()
                canvas.drawText(afterText, x, 0f, paint)
                x += paint.measureText(afterText)
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        if (needsCalculateTransform) {
            calculateTransform(width, height)
            needsCalculateTransform = false
        }
        val paint = paint
        paint.color = currentTextColor

        canvas.translate(scrollX.toFloat(), scrollY.toFloat())
        canvas.scale(textScaleX, textScaleY, 0f, translateY)
        canvas.translate(translateX, translateY)

        var processed: CharSequence? = null
        if (isCompatLoaded) processed = EmojiCompat.get().process(text)

        if (processed is Spanned) {
            drawCompatText(canvas, processed)
        } else {
            canvas.drawText(text, 0f, 0f, paint)
        }
    }

    override fun getTextScaleX(): Float {
        return textScaleX
    }
}
