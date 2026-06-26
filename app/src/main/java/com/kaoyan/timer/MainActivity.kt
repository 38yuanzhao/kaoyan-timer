package com.kaoyan.timer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.kaoyan.timer.ui.KaoyanApp
import com.kaoyan.timer.ui.KaoyanTheme

class MainActivity : ComponentActivity() {
    private val vm: KaoyanViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            KaoyanTheme {
                KaoyanApp(vm)
            }
        }
    }
}
