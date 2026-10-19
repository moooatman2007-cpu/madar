# رفع مشروع student14-main إلى GitHub من المتصفح (Private)

## 1) أنشئ مستودعًا خاصًا
1. افتح https://github.com/new وسجّل الدخول.
2. Repository name: `student14-main` (أو الاسم الذي تفضله).
3. اختر **Private**.
4. لا تضف README أو .gitignore أو License من GitHub؛ لأن المشروع يحتوي عليها بالفعل.
5. اضغط **Create repository**.

## 2) فك ضغط المشروع
1. فك ضغط `student14-main-github-ready.zip` على جهازك.
2. افتح مجلد `student14-main` الداخلي.
3. تأكد أنك ترى `README.md` و`app` و`supabase` و`gradlew` و`.gitignore`.

## 3) ارفع الملفات
1. في صفحة المستودع الجديد، اختر **Add file → Upload files**.
2. اسحب محتويات مجلد `student14-main` إلى الصفحة (المحتويات نفسها، لا ترفع ملف ZIP كملف واحد).
3. انتظر حتى تنتهي عملية الرفع.
4. اكتب رسالة مثل `Initial project upload` في Commit changes.
5. اضغط **Commit changes**.

إذا لم يقبل المتصفح سحب المجلدات دفعة واحدة، ارفع المحتويات على دفعات، مع الحفاظ على بنية المجلدات. لا ترفع ملفات `.env` أو مفاتيح التوقيع أو أي بيانات اعتماد.

## تنبيه أمني
- ملفات `.env.example` الموجودة هنا مخصصة كأمثلة، ويجب أن تحتوي على قيم وهمية فقط.
- لا تضع `SUPABASE_SERVICE_ROLE_KEY` أو `WAHA_API_KEY` أو `WAHA_WEBHOOK_SECRET` أو كلمات مرور التوقيع في المستودع.
- `SUPABASE_PUBLISHABLE_KEY` عادةً مفتاح عميل، لكنه لا يغني عن إعداد RLS وسياسات الوصول الصحيحة.
- كون المستودع Private يقلل الانكشاف لكنه لا يجعل رفع الأسرار آمنًا.
- هذا التجهيز يضيف قواعد `.gitignore` فقط؛ لا يغيّر منطق التطبيق أو قاعدة البيانات.
