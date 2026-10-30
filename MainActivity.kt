package com.custom.iptvtv

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.custom.iptvtv.ui.IptvTheme
import com.custom.iptvtv.ui.Root

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val state = appState
        setContent { IptvTheme { Root(state) } }
    }

    // Tarayıcıdayken Menü / Yer imi tuşu: sayfayı favorilere ekler (WebView tuşu yutmadan önce yakalanır)
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0 &&
            (event.keyCode == KeyEvent.KEYCODE_MENU || event.keyCode == KeyEvent.KEYCODE_BOOKMARK) &&
            appState.overlay is Overlay.Web
        ) {
            appState.webFavTick++
            return true
        }
        return super.dispatchKeyEvent(event)
    }
}
