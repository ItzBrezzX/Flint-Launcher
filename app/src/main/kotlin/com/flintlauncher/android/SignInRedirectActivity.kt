package com.flintlauncher.android

import android.app.Activity
import android.content.Intent
import android.os.Bundle

/**
 * Receives the OAuth custom-scheme redirect (flintlauncher://oauth) after the user signs
 * in on Microsoft's site. It does not parse or log the URI (it contains the one-time
 * authorization code); it just hands it to [RedirectHolder] and finishes, returning to
 * the task that started sign-in.
 */
class SignInRedirectActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        intent?.data?.toString()?.let(RedirectHolder::deliver)
        // Bring the launcher task back to foreground so the sign-in screen can react.
        val main = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        startActivity(main)
        finish()
    }
}
