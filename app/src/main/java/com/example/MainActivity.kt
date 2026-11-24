package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import com.example.ui.CrusherErpApp
import com.example.ui.auth.AuthScreen
import com.example.ui.i18n.AppLanguage
import com.example.ui.i18n.LanguageStore
import com.example.ui.i18n.LocalAppLanguage
import com.example.ui.i18n.LocalAppStrings
import com.example.ui.i18n.stringsFor
import com.example.ui.theme.CrusherErpTheme
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CrusherErpTheme {
                AppContent()
            }
        }
    }
}

@Composable
fun AppContent() {
    val context = LocalContext.current
    var currentUser by remember { mutableStateOf(Firebase.auth.currentUser) }
    var language by remember { mutableStateOf(LanguageStore.getLanguage(context)) }
    val strings = remember(language) { stringsFor(language) }
    val onLanguageChange: (AppLanguage) -> Unit = { nextLang ->
        language = nextLang
        LanguageStore.saveLanguage(context, nextLang)
    }

    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            currentUser = auth.currentUser
        }
        Firebase.auth.addAuthStateListener(listener)
        onDispose {
            Firebase.auth.removeAuthStateListener(listener)
        }
    }

    CompositionLocalProvider(
        LocalAppLanguage provides language,
        LocalAppStrings provides strings,
        LocalLayoutDirection provides language.layoutDirection
    ) {
        val user = currentUser
        if (user == null) {
            AuthScreen(
                onAuthSuccess = {
                    currentUser = Firebase.auth.currentUser
                },
                onLanguageChange = onLanguageChange
            )
        } else {
            key(user.uid) {
                CrusherErpApp(
                    currentUserId = user.uid,
                    onLanguageChange = onLanguageChange
                )
            }
        }
    }
}
