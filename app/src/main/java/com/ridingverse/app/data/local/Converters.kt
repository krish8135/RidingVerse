package com.ridingverse.app.data.local

import androidx.room.TypeConverter

/** Minimal converters for values Room can't persist natively. */
class Converters {
    @TypeConverter
    fun doublesToString(values: List<Double>?): String? =
        values?.joinToString(",")

    @TypeConverter
    fun stringToDoubles(raw: String?): List<Double>? =
        raw?.takeIf { it.isNotBlank() }
            ?.split(",")
            ?.mapNotNull { it.toDoubleOrNull() }
}
