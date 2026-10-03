package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R

/**
 * Google Identity-compliant "Continue with Google" button.
 * Colors follow https://developers.google.com/identity/branding-guidelines
 */
@Composable
fun GoogleSignInButton(
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    val isDark = isSystemInDarkTheme()
    val bgColor = if (isDark) Color(0xFF131314) else Color(0xFFFFFFFF)
    val borderColor = if (isDark) Color(0xFF8E918F) else Color(0xFFDADCE0)
    val textColor = if (isDark) Color(0xFFE3E3E3) else Color(0xFF1F1F1F)

    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp),
        shape = RoundedCornerShape(4.dp),
        color = bgColor,
        border = BorderStroke(1.dp, borderColor),
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_google),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = Color.Unspecified
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = "Continue with Google",
                color = textColor,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp
            )
        }
    }
}
