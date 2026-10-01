package com.tff.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/** PRD §12.3: cards 20dp, sheets 28dp top, chips 10dp, buttons 52dp-tall pills. */
object CtShapes {
    val card = RoundedCornerShape(20.dp)
    val sheet = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    val chip = RoundedCornerShape(10.dp)
    val pill = RoundedCornerShape(50)
    val field = RoundedCornerShape(14.dp)
    val buttonHeight = 52.dp
}
