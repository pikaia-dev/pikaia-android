package dev.pikaia.android.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Typography.bold32: TextStyle
    get() = TextStyle(
        fontSize = 32.sp,
        fontWeight = FontWeight.W700
    )

val Typography.bold20: TextStyle
    get() = TextStyle(
        fontSize = 20.sp,
        fontWeight = FontWeight.W700
    )

val Typography.bold22: TextStyle
    get() = TextStyle(
        fontSize = 22.sp,
        fontWeight = FontWeight.W700
    )

val Typography.bold18: TextStyle
    get() = TextStyle(
        fontSize = 18.sp,
        fontWeight = FontWeight.W700
    )

val Typography.bold16: TextStyle
    get() = TextStyle(
        fontSize = 16.sp,
        fontWeight = FontWeight.W700
    )

val Typography.bold28: TextStyle
    get() = TextStyle(
        fontSize = 28.sp,
        fontWeight = FontWeight.W700
    )

val Typography.semiBold28: TextStyle
    get() = TextStyle(
        fontSize = 28.sp,
        fontWeight = FontWeight.W600
    )

// titles
val Typography.semiBold24: TextStyle
    get() = TextStyle(
        fontSize = 24.sp,
        fontWeight = FontWeight.W600
    )

val Typography.semiBold22: TextStyle
    get() = TextStyle(
        fontSize = 22.sp,
        fontWeight = FontWeight.W600
    )

// button label, toolbar title
val Typography.semiBold16: TextStyle
    get() = TextStyle(
        fontSize = 16.sp,
        fontWeight = FontWeight.W600
    )

val Typography.semiBold12: TextStyle
    get() = TextStyle(
        fontSize = 12.sp,
        fontWeight = FontWeight.W600
    )

val Typography.medium18: TextStyle
    @Composable
    get() = TextStyle(
        fontSize = 18.sp,
        fontWeight = FontWeight.W500
    )

// regular text, input field hints
val Typography.normal18: TextStyle
    get() = TextStyle(
        fontSize = 18.sp,
        fontWeight = FontWeight.W400
    )

val Typography.normal16: TextStyle
    get() = TextStyle(
        fontSize = 16.sp,
        fontWeight = FontWeight.W400
    )

// smaller text
val Typography.normal14: TextStyle
    get() = TextStyle(
        fontSize = 14.sp,
        fontWeight = FontWeight.W400
    )

val Typography.semiBold14: TextStyle
    get() = TextStyle(
        fontSize = 14.sp,
        fontWeight = FontWeight.W600
    )

val Typography.bold14: TextStyle
    get() = TextStyle(
        fontSize = 14.sp,
        fontWeight = FontWeight.W700
    )

// tiny text
val Typography.normal12: TextStyle
    get() = TextStyle(
        fontSize = 12.sp,
        fontWeight = FontWeight.W400
    )

// bottom menu
val Typography.normal10: TextStyle
    get() = TextStyle(
        fontSize = 10.sp,
        fontWeight = FontWeight.W400
    )