package com.sdm.videoplayer.ui.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sdm.videoplayer.BuildConfig
import com.sdm.videoplayer.R
import com.sdm.videoplayer.data.prefs.AccentChoice
import com.sdm.videoplayer.data.prefs.AppSettings
import com.sdm.videoplayer.data.prefs.ThemeMode
import com.sdm.videoplayer.ui.theme.SdmColors
import com.sdm.videoplayer.ui.theme.accentColor

@Composable
fun SettingsScreen(
    settings: AppSettings,
    onTheme: (ThemeMode) -> Unit,
    onAccent: (AccentChoice) -> Unit,
    onDefaultSpeed: (Float) -> Unit,
    onSeekStep: (Int) -> Unit,
    onAutoResume: (Boolean) -> Unit,
    onGestureBrightness: (Boolean) -> Unit,
    onGestureVolume: (Boolean) -> Unit,
    onGestureSeek: (Boolean) -> Unit,
    onDoubleTapSeek: (Boolean) -> Unit,
    onLockTouch: (Boolean) -> Unit,
    onPip: (Boolean) -> Unit,
    onBackground: (Boolean) -> Unit,
    onSubtitleSize: (Float) -> Unit,
    onRemoveFolder: (String) -> Unit,
    onAddFolder: () -> Unit,
    onClearHistory: () -> Unit,
    onClearThumbnails: () -> Unit
) {
    var showPrivacy by remember { mutableStateOf(false) }

    LazyColumn(contentPadding = PaddingValues(12.dp, 12.dp, 12.dp, 100.dp)) {
        item {
            // هوية التطبيق
            Card(shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(Brush.linearGradient(listOf(SdmColors.Violet, SdmColors.ElectricBlue, SdmColors.NeonCyan))),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(R.drawable.ic_launcher_foreground),
                            contentDescription = "SDM VP",
                            modifier = Modifier.size(64.dp)
                        )
                    }
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("SDM", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.headlineMedium, color = SdmColors.Silver)
                            Text(" VP", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.headlineMedium, color = SdmColors.Violet)
                        }
                        Text("Saddam Video Player", style = MaterialTheme.typography.bodyMedium, color = SdmColors.TextMuted)
                        Text("مشغل فيديو صدام", style = MaterialTheme.typography.labelSmall, color = SdmColors.TextMuted)
                    }
                }
            }
        }

        item { GroupTitle("المظهر والهوية") }
        item {
            Card(shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("وضع الواجهة", style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ThemeMode.entries.forEach { mode ->
                            FilterChip(
                                selected = settings.themeMode == mode,
                                onClick = { onTheme(mode) },
                                label = {
                                    Text(
                                        when (mode) {
                                            ThemeMode.SYSTEM -> "حسب النظام"
                                            ThemeMode.DARK -> "داكن"
                                            ThemeMode.LIGHT -> "فاتح"
                                        }
                                    )
                                }
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text("لون الواجهة", style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AccentChoice.entries.forEach { accent ->
                            Box(
                                Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(accentColor(accent))
                                    .clickable { onAccent(accent) },
                                contentAlignment = Alignment.Center
                            ) {
                                if (settings.accent == accent) {
                                    Text("✓", color = Color.White, fontWeight = FontWeight.Bold)
                                } else {
                                    Box(
                                        Modifier
                                            .size(34.dp)
                                            .clip(CircleShape)
                                    ) {}
                                }
                            }
                            Spacer(Modifier.width(0.dp))
                        }
                    }
                }
            }
        }

        item { GroupTitle("التشغيل") }
        item {
            Card(shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("السرعة الافتراضية: ${settings.defaultSpeed}×", style = MaterialTheme.typography.bodyLarge)
                    Slider(
                        value = settings.defaultSpeed,
                        onValueChange = onDefaultSpeed,
                        valueRange = 0.25f..3f,
                        steps = 10,
                        colors = androidx.compose.material3.SliderDefaults.colors(thumbColor = SdmColors.Violet)
                    )
                    Text("مدة التقديم والترجيع: ${settings.seekStepMs / 1000} ثانية", style = MaterialTheme.typography.bodyLarge)
                    Slider(
                        value = (settings.seekStepMs / 1000).toFloat(),
                        onValueChange = { onSeekStep(it.toInt()) },
                        valueRange = 5f..60f,
                        steps = 10,
                        colors = androidx.compose.material3.SliderDefaults.colors(thumbColor = SdmColors.Violet)
                    )
                    SwitchRow("الاستئناف التلقائي من آخر موضع", settings.autoResume, onAutoResume)
                    SwitchRow("صورة داخل صورة عند مغادرة التطبيق", settings.pipOnLeave, onPip)
                    SwitchRow("الاستمرار بالصوت في الخلفية", settings.backgroundPlayback, onBackground)
                }
            }
        }

        item { GroupTitle("الإيماءات") }
        item {
            Card(shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    SwitchRow("السحب على يسار الشاشة لتغيير السطوع", settings.gestureBrightness, onGestureBrightness)
                    SwitchRow("السحب على يمين الشاشة لتغيير الصوت", settings.gestureVolume, onGestureVolume)
                    SwitchRow("السحب الأفقي لتغيير موضع التشغيل", settings.gestureSeek, onGestureSeek)
                    SwitchRow("النقر المزدوج للتقديم والترجيع", settings.doubleTapSeek, onDoubleTapSeek)
                    SwitchRow("بدء المشاهدة مع قفل اللمس", settings.lockTouchOnStart, onLockTouch)
                }
            }
        }

        item { GroupTitle("الترجمة") }
        item {
            Card(shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("حجم خط الترجمة: ${settings.subtitleSize.toInt()}", style = MaterialTheme.typography.bodyLarge)
                    Slider(
                        value = settings.subtitleSize,
                        onValueChange = onSubtitleSize,
                        valueRange = 10f..30f,
                        steps = 19,
                        colors = androidx.compose.material3.SliderDefaults.colors(thumbColor = SdmColors.Violet)
                    )
                    Text(
                        "الترجمة المدمجة في الملف تُعرض تلقائيًا. يمكن إضافة ملف SRT/VTT خارجي من داخل المشغل عبر زر المسارات.",
                        style = MaterialTheme.typography.labelSmall,
                        color = SdmColors.TextMuted
                    )
                }
            }
        }

        item { GroupTitle("مجلدات المكتبة") }
        item {
            Card(shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    if (settings.customFolders.isEmpty()) {
                        Text(
                            "لم تُضف مجلدات. المكتبة تعرض فيديوهات الجهاز عبر MediaStore.",
                            style = MaterialTheme.typography.labelSmall,
                            color = SdmColors.TextMuted
                        )
                    }
                    settings.customFolders.forEach { uri ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                settings.folderNames[uri] ?: uri,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                                maxLines = 1
                            )
                            TextButton(onClick = { onRemoveFolder(uri) }) { Text("إزالة", color = SdmColors.Magenta) }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    TextButton(onClick = onAddFolder) { Text("＋ إضافة مجلد") }
                }
            }
        }

        item { GroupTitle("البيانات") }
        item {
            Card(shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    TextButton(onClick = onClearHistory) { Text("مسح سجل المشاهدة") }
                    TextButton(onClick = onClearThumbnails) { Text("مسح الصور المصغرة المؤقتة") }
                }
            }
        }

        item { GroupTitle("عن التطبيق") }
        item {
            Card(shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("SDM VP — Saddam Video Player", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "الإصدار ${BuildConfig.VERSION_NAME}",
                        style = MaterialTheme.typography.labelSmall,
                        color = SdmColors.TextMuted
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "مشغل فيديو محلي يعمل بالكامل على جهازك، بدون إنترنت أو حساب أو إعلانات.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = { showPrivacy = true }) { Text("سياسة الخصوصية") }
                }
            }
        }
    }

    if (showPrivacy) {
        AlertDialog(
            onDismissRequest = { showPrivacy = false },
            title = { Text("سياسة الخصوصية") },
            text = {
                Text(
                    "كل ملفات الفيديو تُقرأ من جهازك فقط. لا يرفع التطبيق أي فيديو أو بيانات إلى أي خادم، " +
                        "ولا يستخدم أي خدمة مدفوعة أو مفاتيح API خارجية.\n\n" +
                        "الصلاحيات المطلوبة:\n" +
                        "• قراءة الفيديوهات (READ_MEDIA_VIDEO) لعرض المكتبة.\n" +
                        "• الظهور فوق التطبيقات (SYSTEM_ALERT_WINDOW) للنافذة العائمة.\n" +
                        "• الإشعارات لعناصر التحكم أثناء التشغيل في الخلفية.\n\n" +
                        "المفضلة وسجل المشاهدة وقوائم التشغيل والإعدادات تُخزَّن محليًا على جهازك فقط، " +
                        "ويمكنك مسحها في أي وقت من هذه الشاشة.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = { TextButton(onClick = { showPrivacy = false }) { Text("موافق") } }
        )
    }
}

@Composable
private fun GroupTitle(text: String) {
    Row(Modifier.padding(top = 18.dp, bottom = 6.dp, start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(width = 3.dp, height = 14.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Brush.verticalGradient(listOf(SdmColors.Violet, SdmColors.NeonCyan)))
        )
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
