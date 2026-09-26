package com.example.llama

import android.view.ViewGroup

/** Compatibility extension for legacy margin assignments in the UI code. */
var ViewGroup.LayoutParams.bottomMargin: Int
    get() = (this as? ViewGroup.MarginLayoutParams)?.bottomMargin ?: 0
    set(value) {
        if (this is ViewGroup.MarginLayoutParams) {
            bottomMargin = value
        }
    }
