package com.habitergy.link.ui.theme

import androidx.compose.ui.unit.dp

/**
 * Layout tokens aligned with Manager onboarding (`RegisterStep1`, `theme.ts`).
 */
object HabitergyDimens {
    /** Base hero shape size — Manager `HERO_SHAPE_SIZE`. */
    val HeroShapeSize = 192.dp

    /** Onboarding hero display size — `HERO_SHAPE_SIZE * 1.3` (RegisterStep1). */
    val HeroShapeDisplaySize = 250.dp

    /** RegisterStep1 hero `margin-top: -12px`. */
    val HeroShapeTopOverlap = 12.dp

    /** RegisterStep1 hero `margin-bottom: 12px`. */
    val HeroShapeBottomGap = 12.dp

    /** RegisterStep1 title `mb: 1.5` (12dp). */
    val HeroTitleBottomGap = 12.dp

    /** RegisterStep1 hero block `mb: 5` (40dp) before form fields. */
    val HeroSectionBottomGap = 40.dp

    /** RegisterStep1 subtitle `maxWidth: 480`. */
    val HeroSubtitleMaxWidth = 480.dp
}
