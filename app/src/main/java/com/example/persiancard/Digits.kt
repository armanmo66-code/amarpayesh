package com.example.persiancard

/** تبدیل ارقام انگلیسی و عربی به ارقام فارسی */
fun Char.toPersianDigit(): Char = when (this) {
    in '0'..'9' -> '۰' + (this - '0')
    in '٠'..'٩' -> '۰' + (this - '٠')
    else -> this
}

fun String.toPersianDigits(): String {
    val sb = StringBuilder(length)
    for (c in this) sb.append(c.toPersianDigit())
    return sb.toString()
}
