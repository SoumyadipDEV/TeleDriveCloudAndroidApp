package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelProvider
import com.example.data.AppDatabase
import com.example.data.FileRepository
import com.example.ui.DashboardScreen
import com.example.ui.LoginScreen
import com.example.ui.MainViewModel
import com.example.ui.MainViewModelFactory
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Instantiate database, repository, and ViewModel using standard provider factory
        val db = AppDatabase.getDatabase(applicationContext)
        val repository = FileRepository(db.vFileDao())
        val factory = MainViewModelFactory(repository)
        val viewModel = ViewModelProvider(this, factory)[MainViewModel::class.java]

        // Load active Telegram cloud credentials if saved in SharedPreferences
        viewModel.loadSession(applicationContext)

        setContent {
            val isDarkMode by viewModel.isDarkMode.collectAsState()
            MyApplicationTheme(darkTheme = isDarkMode) {
                val authState by viewModel.authState.collectAsState()

                Surface(
                    modifier = Modifier.fillMaxSize()
                ) {
                    if (!authState.isLoggedIn) {
                        LoginScreen(
                            viewModel = viewModel,
                            onLoginSuccess = {
                                // Handled statefully inside VM
                            }
                        )
                    } else {
                        DashboardScreen(
                            viewModel = viewModel,
                            onLogout = {
                                viewModel.logout(applicationContext)
                            }
                        )
                    }
                }
            }
        }
    }
}
