package com.example.dashpet.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.example.dashpet.R
import com.example.dashpet.ui.theme.DashPetTheme

// placeholder 화면: 이후 PetView 등으로 교체 예정.
@Composable
fun HomeScreen() {
    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentAlignment = Alignment.Center
        ) {
            Text(text = stringResource(R.string.app_name))
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenPreview() {
    DashPetTheme { HomeScreen() }
}
