# بريد مؤقت — Temp Email

تطبيق Android أصلي بـ Jetpack Compose لإنشاء صندوق بريد مؤقت حقيقي عبر Mail.tm واستقبال الرسائل بدون تسجيل مستخدم أو Google Sign-In.

## Installation compatibility

- Debug builds use `com.tempinbox.privateinbox.debug` so they cannot conflict with the published Release package.
- Release APK signing enables V1, V2 and V3 schemes for broader installer compatibility.
- There are no ABI splits; the standard APK is a single installable APK.
- Keep the same Release keystore for every public update.

## بيانات الإصدار الحالية
- الاسم الظاهر: بريد مؤقت
- applicationId: `com.tempinbox.privateinbox`
- versionName: `1.0.0`
- versionCode: `1`
- minSdk: `26`
- targetSdk / compileSdk: `37`
- الإعلانات: لا توجد في 1.0.0
- المشتريات/الاشتراك: لا توجد

## الوظائف
- إنشاء عنوان بريد مؤقت فعلي عبر Mail.tm.
- تحديث تلقائي لصندوق الوارد أثناء استخدام التطبيق.
- قراءة الرسائل وحذفها.
- نسخ ومشاركة العنوان.
- جلسة محلية مشفّرة عبر Android Keystore.
- انتهاء محلي بعد 60 دقيقة مع محاولة حذف حساب Mail.tm.
- استعادة جلسة تلقائية عند انتهاء صلاحية الـtoken.
- دعم pagination لصندوق الوارد بدل الاكتفاء بأول صفحة.

## تحديثات ما بعد النشر
لإصدار تحديث يبقي التطبيق نفسه على المتاجر:
1. زد `APP_VERSION_CODE` إلى رقم أكبر من الإصدار السابق.
2. حدّث `APP_VERSION_NAME` مثل `1.0.1`.
3. **لا تغيّر** `applicationId`.
4. **لا تغيّر** مفتاح Release Keystore.
5. حدّث changelog ولقطات الشاشة إذا تغيّرت الواجهة.
6. ابنِ APK موقّع بالمفتاح نفسه ثم ارفع الإصدار على صفحة التطبيق الموجودة.

## التوقيع
مفتاح الإصدار موجود خارج ملف المشروع. إعداد Gradle يعرف أن المفتاح بصيغة PKCS12. لا تضع ملف keystore أو كلمات المرور في GitHub.

## البناء
- AGP: `9.4.0`
- Gradle: `9.6.0`
- Kotlin: `2.4.20`
- Compose BOM: `2026.09.00`
- JDK: `17`

CI يستخدم Gradle Action الرسمي لتثبيت Gradle 9.6.0، ويبني الإصدار النهائي موقّعًا فقط عند توفر أسرار التوقيع. البناء المحلي يحتاج Gradle 9.6.0 أو Android Studio/بيئة Android مناسبة؛ ملفات `gradlew` و`gradlew.bat` موجودة كمدخلات محلية واضحة، بينما CI لا يعتمد على Wrapper JAR.

## النشر
المجلد `store/` يحتوي بيانات المتجر وقائمة التحقق وبيانات الإصدار. المجلد `docs/` يحتوي سياسة الخصوصية وشروط الاستخدام الجاهزة للنشر على GitHub Pages.

قبل الإطلاق يجب استبدال بيانات التواصل الخاصة بالمطور ونشر `docs/` والحصول على URL حقيقي للسياسة والموقع.
