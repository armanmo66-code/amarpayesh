package com.example.persiancard

import android.content.Context
import android.graphics.RectF
import android.view.View
import android.widget.FrameLayout

/**
 * یک FrameLayout که ارتفاعش را از روی نسبت تصویر زمینه حساب می‌کند
 * و فرزندانش را در موقعیت‌های نسبی (کسری) قرار می‌دهد.
 */
class CardLayout(context: Context, private val aspect: Float) : FrameLayout(context) {

    private val slots = ArrayList<Pair<View, RectF>>()

    /** بعد از تعیین اندازه نهایی صدا زده می‌شود (برای تنظیم اندازه متن‌ها) */
    var onSized: (() -> Unit)? = null

    fun addSlot(view: View, rect: RectF) {
        slots.add(view to rect)
        addView(view)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = (w * aspect).toInt()
        setMeasuredDimension(w, h)
        for ((v, r) in slots) {
            v.measure(
                MeasureSpec.makeMeasureSpec((r.width() * w).toInt(), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec((r.height() * h).toInt(), MeasureSpec.EXACTLY)
            )
        }
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val w = r - l
        val h = b - t
        for ((v, rc) in slots) {
            v.layout(
                (rc.left * w).toInt(),
                (rc.top * h).toInt(),
                (rc.right * w).toInt(),
                (rc.bottom * h).toInt()
            )
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        post { onSized?.invoke() }
    }
}
