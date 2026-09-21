@file:Suppress("detekt.FunctionNaming")

package uk.gov.defra.mmocatchrecord.common.design

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview

@Suppress("FunctionNaming")
@Preview(name = "Top app bar", showBackground = true)
@Composable
fun GdsTopAppBarPreview() {
    MmoTheme {
        GdsTopAppBar(
            currentLanguage = "en",
            onLanguageToggle = {},
            onBackClick = {},
        )
    }
}

@Suppress("FunctionNaming")
@Preview(name = "Bottom navigation bar", showBackground = true, backgroundColor = 0xFFFFFFFF)
@Composable
fun MmoBottomNavigationBarPreview() {
    MmoTheme {
        Column(modifier = Modifier.fillMaxWidth().background(MmoColors.White)) {
            MmoBottomNavigationBar(selectedItem = 0, onItemClick = {})
        }
    }
}
