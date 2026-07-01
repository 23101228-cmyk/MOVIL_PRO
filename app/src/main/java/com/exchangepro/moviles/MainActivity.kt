package com.exchangepro.moviles

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.exchangepro.moviles.presentation.navigation.ExchangeProNavGraph
import com.exchangepro.moviles.ui.theme.ExchangeProTheme

class MainActivity : ComponentActivity() {
    /**
     * Punto de entrada Android: instala el tema Compose y entrega el control al grafo
     * de navegacion. La logica de negocio permanece fuera de la Activity.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ExchangeProTheme {
                ExchangeProNavGraph()
            }
        }
    }
}
