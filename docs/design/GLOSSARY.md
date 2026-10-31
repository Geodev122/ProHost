# ProHost glossary (English · Arabic)

Use these terms for every user-facing string in `res/values/strings.xml` and `res/values-ar/strings.xml`. Keep one
word per idea: a listing is never also "workspace listing" or "ad".

| English | Arabic | Notes |
|---|---|---|
| Listing | إعلان | What a Pro Host publishes. "Create listing" = إنشاء إعلان |
| Space | مساحة | The place itself (clinic, office, studio…) |
| Division | قسم | A rentable part of a space (room, desk, hall). Room types keep their own names |
| Whole place | المكان كاملًا | A listing with one division |
| Cover photo | صورة الغلاف | The brand cover: logo or brand photo, `imageUrls[0]` |
| Category | الفئة | Space category (dropdown) |
| Facilities | المرافق | Shared by the whole space |
| Amenities | التجهيزات | Per division |
| House rules | قواعد المكان | |
| Opening hours | ساعات العمل | |
| Availability | التوفّر | |
| Booking / request | حجز / طلب | |
| Pro Host | مضيف محترف | Role name |
| Specialist | أخصائي | Role name |
| Premium | بريميوم | Product name |
| Sign in | تسجيل الدخول | Never "log in" |
| Create account | إنشاء حساب | |
| Password | كلمة المرور | |
| Email | البريد الإلكتروني | |
| WhatsApp number | رقم واتساب | Brand stays "WhatsApp" in English text, "واتساب" in Arabic |
| Draft | مسودة | |
| Publish | نشر | |

## Tone

- Sentence case for labels, titles and buttons ("Create listing", "Save changes"), never Title Case.
- One short line of helper text at most; drop it when the label already says it. No "e.g." walls.
- Arabic is Modern Standard Arabic, addressing the reader directly (أضف، اختر), with no Latin words except brand
  names, file formats and examples such as `you@example.com`.
- Keys and `%1$s` placeholders stay identical in both files (`app_name` is the only intended difference).
- Unused keys are deleted from both files (grep `R.string.key` / `@string/key` first).
