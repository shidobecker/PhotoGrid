package br.com.shido.photogrid

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import br.com.shido.photogrid.ui.components.PhotoGrid
import br.com.shido.photogrid.ui.theme.PhotoGridTheme
//https://medium.com/androiddevelopers/create-a-photo-grid-with-multiselect-behavior-using-jetpack-compose-9a8d588a9b63
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PhotoGridTheme {
                PhotoGrid()
            }
        }
    }
}
