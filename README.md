# LocalVideoStudio

مشغل فيديو محلي لنظام Android باستخدام Kotlin وJetpack Compose وAndroidX Media3.

## فتح المشروع
1. افتح المجلد في Android Studio حديث.
2. استخدم JDK 17.
3. انتظر Gradle Sync، وتأكد من تثبيت Android SDK Platform 35.
4. شغّل التطبيق على جهاز Android أو محاكي.

## البناء
من Android Studio اختر **Build > Make Project**. لإنشاء APK تجريبي اختر **Build > Build APK(s)**.

## ملاحظات
- التطبيق يقرأ الفيديوهات عبر MediaStore ويطلب صلاحية الوصول إلى الفيديوهات.
- مشغل Media3 يدعم صيغًا شائعة، لكن لا يضمن كل الصيغ/الترميزات التي يدعمها VLC.
- لا تتضمن هذه النسخة ملفات Gradle Wrapper؛ يمكن لـ Android Studio استخدام Gradle المناسب أو إضافة Wrapper من بيئة Gradle موثوقة.
- يجب اختبار البناء على Android Studio قبل اعتبار المشروع جاهزًا للإصدار.
