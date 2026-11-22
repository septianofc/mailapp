package com.example.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.theme.BrandBlueAccent
import com.example.ui.theme.BrandBlueDark
import com.example.ui.theme.BrandBluePrimary
import com.example.ui.theme.BrandBlueSoftContainer
import com.example.ui.theme.PureWhite

@Composable
fun SplashOverlay(modifier: Modifier = Modifier) {
  Box(
    modifier =
      modifier
        .fillMaxSize()
        .background(
          brush =
            Brush.verticalGradient(
              colors = listOf(PureWhite, BrandBlueSoftContainer.copy(alpha = 0.55f), PureWhite)
            )
        )
        .safeDrawingPadding()
        .testTag("splash_overlay"),
    contentAlignment = Alignment.Center,
  ) {
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center,
      modifier = Modifier.padding(horizontal = 32.dp),
    ) {
      Surface(
        shape = CircleShape,
        shadowElevation = 10.dp,
        color = PureWhite,
        modifier =
          Modifier.size(116.dp).border(width = 3.dp, color = BrandBluePrimary, shape = CircleShape),
      ) {
        Image(
          painter = painterResource(id = R.drawable.brand_logo),
          contentDescription = stringResource(id = R.string.cd_brand_logo),
          contentScale = ContentScale.Crop,
          modifier = Modifier.fillMaxSize().padding(4.dp).clip(CircleShape),
        )
      }

      Spacer(modifier = Modifier.height(24.dp))

      Text(
        text = stringResource(id = R.string.app_name),
        style = MaterialTheme.typography.headlineSmall,
        color = BrandBlueDark,
        textAlign = TextAlign.Center,
      )

      Spacer(modifier = Modifier.height(6.dp))

      Text(
        text = stringResource(id = R.string.brand_subtitle),
        style = MaterialTheme.typography.titleMedium,
        color = BrandBluePrimary,
        fontWeight = FontWeight.Medium,
      )

      Spacer(modifier = Modifier.height(16.dp))

      Surface(
        shape = RoundedCornerShape(50),
        color = BrandBlueSoftContainer,
        modifier =
          Modifier.border(
            width = 1.dp,
            color = BrandBlueAccent.copy(alpha = 0.35f),
            shape = RoundedCornerShape(50),
          ),
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
        ) {
          Icon(
            imageVector = Icons.Rounded.VerifiedUser,
            contentDescription = null,
            tint = BrandBluePrimary,
            modifier = Modifier.size(16.dp),
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text(
            text = stringResource(id = R.string.splash_security_badge),
            style = MaterialTheme.typography.labelMedium,
            color = BrandBlueDark,
          )
        }
      }

      Spacer(modifier = Modifier.height(28.dp))

      LinearProgressIndicator(
        color = BrandBluePrimary,
        trackColor = BrandBlueSoftContainer,
        modifier = Modifier.width(140.dp).height(4.dp).clip(RoundedCornerShape(4.dp)),
      )

      Spacer(modifier = Modifier.height(16.dp))

      Text(
        text = stringResource(id = R.string.version_label),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}
