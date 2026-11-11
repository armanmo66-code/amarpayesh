package com.example.persiancard

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.ContentValues
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.GradientDrawable
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.text.Editable
import android.text.InputFilter
import android.text.InputType
import android.text.TextWatcher
import android.text.method.DigitsKeyListener
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.NumberPicker
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs

class MainActivity : Activity() {

    private lateinit var vazir: Typeface
    private lateinit var cardBitmap: Bitmap
    private lateinit var card: CardLayout
    private lateinit var dateView: TextView
    private lateinit var exportButton: Button
    private val fields = ArrayList<EditText>()
    private val measurePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var date: Jalali.JDate = Jalali.today()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        vazir = Typeface.createFromAsset(assets, "fonts/vazirmatn_bold.ttf")
        measurePaint.typeface = vazir
        cardBitmap = loadBackground()
        savedInstanceState?.getIntArray(KEY_DATE)?.let {
            if (it.size == 3) date = Jalali.JDate(it[0], it[1], it[2])
        }
        buildUi()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putIntArray(KEY_DATE, intArrayOf(date.year, date.month, date.day))
    }

    // ───────────────────────── تصویر زمینه ─────────────────────────

    private fun loadBackground(): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeResource(resources, R.drawable.background, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > CardConfig.MAX_BITMAP_SIDE) {
            sample *= 2
        }
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inScaled = false
        }
        return BitmapFactory.decodeResource(resources, R.drawable.background, opts)
            ?: throw IllegalStateException("background image not found")
    }

    // ───────────────────────── رابط کاربری ─────────────────────────

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun highlight(): GradientDrawable = GradientDrawable().apply {
        setColor(0x201E88E5)
        setStroke(dp(1), 0x661E88E5)
        cornerRadius = dp(10).toFloat()
    }

    private fun buildUi() {
        card = CardLayout(this, cardBitmap.height.toFloat() / cardBitmap.width)
        card.background = BitmapDrawable(resources, cardBitmap)

        val lastIndex = CardConfig.FIELDS.size - 1
        CardConfig.FIELDS.forEachIndexed { i, rect ->
            val et = EditText(this)
            et.id = 5000 + i
            et.inputType = InputType.TYPE_CLASS_NUMBER
            et.keyListener = DigitsKeyListener.getInstance(ALLOWED_DIGITS)
            et.filters = arrayOf(InputFilter.LengthFilter(CardConfig.MAX_DIGITS))
            et.isSingleLine = true
            et.imeOptions = if (i == lastIndex) EditorInfo.IME_ACTION_DONE else EditorInfo.IME_ACTION_NEXT
            et.typeface = vazir
            et.setTextColor(Color.BLACK)
            et.setHintTextColor(0x55000000)
            et.hint = (i / 2 + 1).toString().toPersianDigits()
            et.gravity = Gravity.CENTER
            et.setPadding(0, 0, 0, 0)
            et.background = highlight()
            et.addTextChangedListener(object : TextWatcher {
                private var busy = false
                override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
                override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
                override fun afterTextChanged(s: Editable) {
                    if (busy) return
                    busy = true
                    for (k in 0 until s.length) {
                        val p = s[k].toPersianDigit()
                        if (p != s[k]) s.replace(k, k + 1, p.toString())
                    }
                    busy = false
                    refit(et, rect)
                }
            })
            fields.add(et)
            card.addSlot(et, rect)
        }

        dateView = TextView(this).apply {
            typeface = vazir
            setTextColor(Color.BLACK)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 0)
            background = highlight()
            isClickable = true
            setOnClickListener { showDatePicker() }
        }
        card.addSlot(dateView, CardConfig.DATE_RECT)
        updateDateView()

        card.onSized = {
            fields.forEachIndexed { i, et -> refit(et, CardConfig.FIELDS[i]) }
            refit(dateView, CardConfig.DATE_RECT)
        }

        val hint = TextView(this).apply {
            text = "روی هر کادر بزنید و عدد را وارد کنید. برای تغییر تاریخ روی کادر تاریخ بزنید."
            gravity = Gravity.CENTER
            setTextColor(0xFF555555.toInt())
            setPadding(dp(8), dp(12), dp(8), dp(4))
        }

        val dateButton = Button(this).apply {
            text = "انتخاب تاریخ گزارش"
            setOnClickListener { showDatePicker() }
        }

        exportButton = Button(this).apply {
            text = "خروجی (ذخیره تصویر در گالری)"
            setOnClickListener { exportImage() }
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(dp(8), dp(8), dp(8), dp(24))
            addView(card, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(hint)
            addView(dateButton)
            addView(exportButton)
        }

        setContentView(ScrollView(this).apply {
            isFillViewport = true
            addView(root)
        })
    }

    // ───────────────────────── اندازه متن ─────────────────────────

    private fun fitTextSize(paint: Paint, text: String, boxW: Float, boxH: Float): Float {
        var size = boxH * CardConfig.TEXT_HEIGHT_RATIO
        paint.textSize = size
        val maxW = boxW * 0.92f
        val w = paint.measureText(text)
        if (w > maxW && w > 0f) size *= maxW / w
        return size
    }

    private fun refit(tv: TextView, rect: RectF) {
        val cw = card.width.toFloat()
        val ch = card.height.toFloat()
        if (cw == 0f || ch == 0f) return
        val text = tv.text.toString().ifEmpty { "۰" }
        val size = fitTextSize(measurePaint, text, rect.width() * cw, rect.height() * ch)
        if (abs(tv.textSize - size) > 0.5f) {
            tv.setTextSize(TypedValue.COMPLEX_UNIT_PX, size)
        }
    }

    // ───────────────────────── تاریخ ─────────────────────────

    private fun dateText(): String {
        val s = if (CardConfig.DATE_AS_WORDS) {
            "${date.day} ${Jalali.MONTH_NAMES[date.month - 1]} ${date.year}"
        } else {
            "%04d/%02d/%02d".format(date.year, date.month, date.day)
        }
        return s.toPersianDigits()
    }

    private fun updateDateView() {
        dateView.text = dateText()
        refit(dateView, CardConfig.DATE_RECT)
    }

    private fun showDatePicker() {
        val yMin = CardConfig.YEAR_MIN
        val yMax = CardConfig.YEAR_MAX
        val yearLabels = Array(yMax - yMin + 1) { (yMin + it).toString().toPersianDigits() }
        val dayLabels = Array(31) { (it + 1).toString().toPersianDigits() }

        val yearPicker = NumberPicker(this).apply {
            minValue = yMin
            maxValue = yMax
            displayedValues = yearLabels
            value = date.year.coerceIn(yMin, yMax)
            wrapSelectorWheel = false
            descendantFocusability = NumberPicker.FOCUS_BLOCK_DESCENDANTS
        }
        val monthPicker = NumberPicker(this).apply {
            minValue = 1
            maxValue = 12
            displayedValues = Jalali.MONTH_NAMES
            value = date.month
            descendantFocusability = NumberPicker.FOCUS_BLOCK_DESCENDANTS
        }
        val dayPicker = NumberPicker(this).apply {
            minValue = 1
            maxValue = 31
            displayedValues = dayLabels
            value = date.day
            descendantFocusability = NumberPicker.FOCUS_BLOCK_DESCENDANTS
        }

        fun refreshDays() {
            val len = Jalali.monthLength(yearPicker.value, monthPicker.value)
            dayPicker.maxValue = len
            if (dayPicker.value > len) dayPicker.value = len
        }
        refreshDays()
        yearPicker.setOnValueChangedListener { _, _, _ -> refreshDays() }
        monthPicker.setOnValueChangedListener { _, _, _ -> refreshDays() }

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutDirection = View.LAYOUT_DIRECTION_LTR
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(16), dp(16), dp(8))
            val lp = { LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f) }
            addView(yearPicker, lp())
            addView(monthPicker, lp())
            addView(dayPicker, lp())
        }

        AlertDialog.Builder(this)
            .setTitle("انتخاب تاریخ (شمسی)")
            .setView(row)
            .setPositiveButton("تأیید") { _, _ ->
                date = Jalali.JDate(yearPicker.value, monthPicker.value, dayPicker.value)
                updateDateView()
            }
            .setNegativeButton("انصراف", null)
            .show()
    }

    // ───────────────────────── خروجی ─────────────────────────

    private fun exportImage() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
            checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), REQ_STORAGE)
            return
        }
        exportButton.isEnabled = false
        val texts = fields.map { it.text.toString().trim() }
        val dateStr = dateText()
        Thread {
            val result = runCatching {
                val out = renderCard(texts, dateStr)
                try {
                    saveToGallery(out)
                } finally {
                    out.recycle()
                }
            }
            runOnUiThread {
                exportButton.isEnabled = true
                result.onSuccess {
                    Toast.makeText(this, "تصویر در گالری ذخیره شد (پوشه PersianCard)", Toast.LENGTH_LONG).show()
                }.onFailure {
                    Toast.makeText(this, "خطا در ذخیره تصویر: ${it.message}", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_STORAGE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                exportImage()
            } else {
                Toast.makeText(this, "برای ذخیره تصویر، اجازه دسترسی لازم است", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun renderCard(texts: List<String>, dateStr: String): Bitmap {
        val out = cardBitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(out)
        val w = out.width.toFloat()
        val h = out.height.toFloat()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG)
        paint.color = Color.BLACK
        paint.typeface = vazir
        paint.textAlign = Paint.Align.CENTER

        texts.forEachIndexed { i, t ->
            if (t.isNotEmpty()) drawCentered(canvas, paint, t.toPersianDigits(), CardConfig.FIELDS[i], w, h)
        }
        drawCentered(canvas, paint, dateStr, CardConfig.DATE_RECT, w, h)
        return out
    }

    private fun drawCentered(canvas: Canvas, paint: Paint, text: String, r: RectF, w: Float, h: Float) {
        paint.textSize = fitTextSize(paint, text, r.width() * w, r.height() * h)
        val fm = paint.fontMetrics
        val x = (r.left + r.right) / 2f * w
        val y = (r.top + r.bottom) / 2f * h - (fm.ascent + fm.descent) / 2f
        canvas.drawText(text, x, y, paint)
    }

    private fun saveToGallery(bmp: Bitmap) {
        val name = "card_${System.currentTimeMillis()}.png"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, name)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/PersianCard")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: throw IllegalStateException("cannot create media entry")
            contentResolver.openOutputStream(uri)?.use { os ->
                if (!bmp.compress(Bitmap.CompressFormat.PNG, 100, os)) {
                    throw IllegalStateException("compress failed")
                }
            } ?: throw IllegalStateException("cannot open output stream")
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            contentResolver.update(uri, values, null, null)
        } else {
            @Suppress("DEPRECATION")
            val dir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                "PersianCard"
            )
            dir.mkdirs()
            val file = File(dir, name)
            FileOutputStream(file).use { os ->
                if (!bmp.compress(Bitmap.CompressFormat.PNG, 100, os)) {
                    throw IllegalStateException("compress failed")
                }
            }
            MediaScannerConnection.scanFile(this, arrayOf(file.absolutePath), arrayOf("image/png"), null)
        }
    }

    companion object {
        private const val KEY_DATE = "date"
        private const val REQ_STORAGE = 1
        private const val ALLOWED_DIGITS = "0123456789۰۱۲۳۴۵۶۷۸۹٠١٢٣٤٥٦٧٨٩"
    }
}
