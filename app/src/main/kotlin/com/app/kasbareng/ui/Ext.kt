package com.app.kasbareng.ui

import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText

/** Pengganti doAfterTextChanged (fungsi biasa, bukan inline). */
fun EditText.onTextChanged(action: () -> Unit) {
    addTextChangedListener(object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        override fun afterTextChanged(s: Editable?) {
            action()
        }
    })
}