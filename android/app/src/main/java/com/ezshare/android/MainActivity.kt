package com.ezshare.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.ezshare.android.server.ShareManager
import com.ezshare.android.ui.AppRoot
import com.ezshare.android.ui.EZShareTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ShareManager.init(applicationContext)
        setContent {
            EZShareTheme {
                AppRoot()
            }
        }
    }
}
