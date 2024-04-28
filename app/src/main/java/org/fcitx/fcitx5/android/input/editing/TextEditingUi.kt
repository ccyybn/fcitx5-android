/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2021-2023 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.editing

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.StateListDrawable
import android.view.View
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.preference.PreferenceManager
import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.data.InputFeedbacks
import org.fcitx.fcitx5.android.data.prefs.ManagedPreference
import org.fcitx.fcitx5.android.data.theme.Theme
import org.fcitx.fcitx5.android.data.theme.ThemeManager
import org.fcitx.fcitx5.android.data.theme.ThemePrefs
import org.fcitx.fcitx5.android.input.bar.ui.ToolButton
import org.fcitx.fcitx5.android.input.keyboard.CustomGestureView
import org.fcitx.fcitx5.android.input.keyboard.ImageKeyView
import org.fcitx.fcitx5.android.input.keyboard.KeyDef
import org.fcitx.fcitx5.android.input.keyboard.TextKeyView
import org.fcitx.fcitx5.android.utils.borderDrawable
import org.fcitx.fcitx5.android.utils.pressHighlightDrawable
import org.fcitx.fcitx5.android.utils.rippleDrawable
import splitties.dimensions.dp
import splitties.resources.drawable
import splitties.views.backgroundColor
import splitties.views.dsl.constraintlayout.above
import splitties.views.dsl.constraintlayout.below
import splitties.views.dsl.constraintlayout.bottomOfParent
import splitties.views.dsl.constraintlayout.constraintLayout
import splitties.views.dsl.constraintlayout.lParams
import splitties.views.dsl.constraintlayout.leftOfParent
import splitties.views.dsl.constraintlayout.leftToRightOf
import splitties.views.dsl.constraintlayout.rightOfParent
import splitties.views.dsl.constraintlayout.rightToLeftOf
import splitties.views.dsl.constraintlayout.topOfParent
import splitties.views.dsl.core.Ui
import splitties.views.dsl.core.add
import splitties.views.dsl.core.horizontalLayout
import splitties.views.dsl.core.imageView
import splitties.views.dsl.core.lParams
import splitties.views.dsl.core.textView
import splitties.views.dsl.core.wrapContent
import splitties.views.gravityCenter
import splitties.views.imageDrawable
import splitties.views.padding

class TextEditingUi(override val ctx: Context, private val theme: Theme) : Ui {

    //if the main keyboard border is off and text editing is the border key, the background color should be applied as barColor
    private val keyBorder by ThemeManager.prefs.keyBorder

    //keyBordered should always be true, because the rounded rectangle pressed effect is needed even if borderless is selected
    private val keyBordered =
        ManagedPreference.PBool(PreferenceManager.getDefaultSharedPreferences(ctx), "", true)
    private val keyStyle by ThemeManager.prefs.keyTextEditingStyle
    private val keyRippleEffect by ThemeManager.prefs.keyTextEditingRippleEffect
    private val borderWidth = ctx.dp(1) / 2
    private fun View.applyBorderedBackground() {
        background = borderDrawable(borderWidth, theme.dividerColor)
        foreground =
            if (keyRippleEffect) rippleDrawable(theme.keyPressHighlightColor)
            else pressHighlightDrawable(theme.keyPressHighlightColor)
    }

    private fun GTextButton.applySelectBorderedBackground() {
        text.setTextColor(
            ColorStateList(
                arrayOf(
                    intArrayOf(android.R.attr.state_activated),
                    intArrayOf(android.R.attr.state_enabled)
                ),
                intArrayOf(theme.genericActiveForegroundColor, theme.keyTextColor)
            )
        )
        background = StateListDrawable().apply {
            addState(
                intArrayOf(android.R.attr.state_activated),
                borderDrawable(
                    borderWidth,
                    theme.dividerColor,
                    theme.genericActiveBackgroundColor
                )
            )
            addState(
                intArrayOf(android.R.attr.state_enabled),
                borderDrawable(borderWidth, theme.dividerColor)
            )
        }
    }

    class GTextButton(context: Context) : CustomGestureView(context) {
        val text = textView {
            isClickable = false
            isFocusable = false
            background = null
        }

        init {
            add(text, lParams(wrapContent, wrapContent, gravityCenter))
        }
    }

    class GImageButton(context: Context) : CustomGestureView(context) {
        val image = imageView {
            isClickable = false
            isFocusable = false
        }

        init {
            add(image, lParams(wrapContent, wrapContent, gravityCenter))
        }
    }

    private fun textLinearButton(@StringRes id: Int) = GTextButton(ctx).apply {
        text.setText(id)
        text.setTextColor(theme.keyTextColor)
        stateListAnimator = null
        applyBorderedBackground()
    }

    private fun iconLinearButton(@DrawableRes icon: Int) = GImageButton(ctx).apply {
        image.imageDrawable = drawable(icon)!!.apply {
            setTint(theme.altKeyTextColor)
        }
        padding = dp(10)
        applyBorderedBackground()
    }

    private fun textButton(
        @StringRes id: Int,
        variant: KeyDef.Appearance.Variant = KeyDef.Appearance.Variant.Normal,
    ): CustomGestureView {
        if (keyStyle == ThemePrefs.KeyTextEditingStyle.Linear) {
            return textLinearButton(id)
        } else {
            val prefs = ThemeManager.prefs
            val def = KeyDef.Appearance.Text(
                ctx.getString(id),
                textSize = 16f,
                variant = variant,
                border = if (keyStyle == ThemePrefs.KeyTextEditingStyle.Border) KeyDef.Appearance.Border.On else KeyDef.Appearance.Border.Off,
                prefs = KeyDef.Appearance.Prefs(
                    keyBorder = keyBordered,
                    keyRippleEffect = prefs.keyTextEditingRippleEffect,
                    keyRadius = prefs.keyTextEditingRadius,
                    keyHorizontalMargin = prefs.keyTextEditingMargin,
                    keyHorizontalMarginLandscape = prefs.keyTextEditingMarginLandscape,
                    keyVerticalMargin = prefs.keyTextEditingMargin,
                    keyVerticalMarginLandscape = prefs.keyTextEditingMarginLandscape
                )
            )
            return TextKeyView(ctx, theme, def)
        }
    }

    private fun iconButton(
        @DrawableRes icon: Int,
        variant: KeyDef.Appearance.Variant = KeyDef.Appearance.Variant.Normal
    ): CustomGestureView {
        if (keyStyle == ThemePrefs.KeyTextEditingStyle.Linear) {
            return iconLinearButton(icon)
        } else {
            val prefs = ThemeManager.prefs
            val def = KeyDef.Appearance.Image(
                icon,
                variant = variant,
                border = if (keyStyle == ThemePrefs.KeyTextEditingStyle.Border) KeyDef.Appearance.Border.On else KeyDef.Appearance.Border.Off,
                prefs = KeyDef.Appearance.Prefs(
                    keyBorder = keyBordered,
                    keyRippleEffect = prefs.keyTextEditingRippleEffect,
                    keyRadius = prefs.keyTextEditingRadius,
                    keyHorizontalMargin = prefs.keyTextEditingMargin,
                    keyHorizontalMarginLandscape = prefs.keyTextEditingMarginLandscape,
                    keyVerticalMargin = prefs.keyTextEditingMargin,
                    keyVerticalMarginLandscape = prefs.keyTextEditingMarginLandscape
                )
            )
            return ImageKeyView(ctx, theme, def)
        }
    }

    val upButton = iconButton(R.drawable.ic_baseline_keyboard_arrow_up_24)

    val rightButton = iconButton(R.drawable.ic_baseline_keyboard_arrow_right_24)

    val downButton = iconButton(R.drawable.ic_baseline_keyboard_arrow_down_24)

    val leftButton = iconButton(R.drawable.ic_baseline_keyboard_arrow_left_24)

    val selectButton =
        if (keyStyle == ThemePrefs.KeyTextEditingStyle.Linear) textLinearButton(R.string.select).apply {
            applySelectBorderedBackground()
        } else textButton(
            R.string.select,
            KeyDef.Appearance.Variant.Accent
        )

    val homeButton = iconButton(R.drawable.ic_baseline_first_page_24)

    val endButton = iconButton(R.drawable.ic_baseline_last_page_24)

    val selectAllButton =
        textButton(android.R.string.selectAll, KeyDef.Appearance.Variant.Alternative)

    val cutButton = textButton(
        android.R.string.cut, KeyDef.Appearance.Variant.Alternative
    ).apply { visibility = View.GONE }

    val copyButton =
        textButton(android.R.string.copy, KeyDef.Appearance.Variant.Alternative)

    val pasteButton =
        textButton(android.R.string.paste, KeyDef.Appearance.Variant.Alternative)

    val backspaceButton = iconButton(
        R.drawable.ic_baseline_backspace_24,
        KeyDef.Appearance.Variant.Alternative
    ).apply {
        soundEffect = InputFeedbacks.SoundEffect.Delete
    }

    override val root = constraintLayout {
        if (!keyBorder && keyStyle == ThemePrefs.KeyTextEditingStyle.Border) {
            backgroundColor = theme.barColor
        }

        add(leftButton, lParams {
            topOfParent()
            leftOfParent()
            above(homeButton)
            rightToLeftOf(selectButton)
        })
        add(upButton, lParams {
            topOfParent()
            leftToRightOf(leftButton)
            above(selectButton)
            rightToLeftOf(rightButton)
        })
        add(selectButton, lParams {
            below(upButton)
            leftToRightOf(leftButton)
            above(downButton)
            rightToLeftOf(rightButton)
        })
        add(downButton, lParams {
            below(selectButton)
            leftToRightOf(leftButton)
            above(homeButton)
            rightToLeftOf(rightButton)
        })
        add(rightButton, lParams {
            topOfParent()
            leftToRightOf(selectButton)
            above(endButton)
            rightToLeftOf(copyButton)
        })

        add(homeButton, lParams {
            below(downButton)
            leftOfParent()
            bottomOfParent()
            rightToLeftOf(endButton)
        })
        add(endButton, lParams {
            below(downButton)
            leftToRightOf(homeButton)
            bottomOfParent()
            rightToLeftOf(backspaceButton)
        })

        add(selectAllButton, lParams {
            topOfParent()
            leftToRightOf(rightButton)
            rightOfParent()
            above(cutButton)
            matchConstraintPercentWidth = 0.3f
        })
        add(cutButton, lParams {
            below(selectAllButton)
            leftToRightOf(rightButton)
            rightOfParent()
            above(copyButton)
            matchConstraintPercentWidth = 0.3f
        })
        add(copyButton, lParams {
            below(cutButton)
            leftToRightOf(rightButton)
            rightOfParent()
            above(pasteButton)
            matchConstraintPercentWidth = 0.3f
        })
        add(pasteButton, lParams {
            below(copyButton)
            leftToRightOf(rightButton)
            rightOfParent()
            above(backspaceButton)
            matchConstraintPercentWidth = 0.3f
        })
        add(backspaceButton, lParams {
            below(pasteButton)
            leftToRightOf(rightButton)
            rightOfParent()
            bottomOfParent()
            matchConstraintPercentWidth = 0.3f
        })
    }

    fun updateSelection(hasSelection: Boolean, userSelection: Boolean, hasText: Boolean) {
        if (hasText) {
            selectButton.isActivated = (hasSelection || userSelection)
            if (selectButton.isActivated) {
                (selectButton as? TextKeyView)?.onSetVariant(KeyDef.Appearance.Variant.Accent)
            } else {
                (selectButton as? TextKeyView)?.onSetVariant(KeyDef.Appearance.Variant.Normal)
            }
            (selectButton as? GTextButton)?.apply {
                applySelectBorderedBackground()
            }
            selectButton.isEnabled = true
            selectAllButton.isEnabled = true
            leftButton.isEnabled = true
            rightButton.isEnabled = true
            upButton.isEnabled = true
            downButton.isEnabled = true
            homeButton.isEnabled = true
            endButton.isEnabled = true
            backspaceButton.isEnabled = true
            if (hasSelection) {
                selectAllButton.visibility = View.GONE
                cutButton.visibility = View.VISIBLE
                copyButton.isEnabled = true
            } else {
                selectAllButton.visibility = View.VISIBLE
                cutButton.visibility = View.GONE
                copyButton.isEnabled = false
            }
        } else {
            (selectButton as? TextKeyView)?.onSetVariant(KeyDef.Appearance.Variant.Normal)
            (selectButton as? GTextButton)?.apply {
                text.setTextColor(theme.keyTextColor)
                applyBorderedBackground()
            }
            selectAllButton.visibility = View.VISIBLE
            cutButton.visibility = View.GONE

            selectButton.isEnabled = false
            selectAllButton.isEnabled = false
            copyButton.isEnabled = false
            leftButton.isEnabled = false
            rightButton.isEnabled = false
            upButton.isEnabled = false
            downButton.isEnabled = false
            homeButton.isEnabled = false
            endButton.isEnabled = false
            backspaceButton.isEnabled = false
        }
    }

    val clipboardButton = ToolButton(ctx, R.drawable.ic_clipboard, theme)

    val extension = horizontalLayout {
        add(clipboardButton, lParams(dp(40), dp(40)))
    }
}
