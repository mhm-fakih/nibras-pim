# مشغّل الصوت

**مشغّل الصوت** مشغّلٌ لسطح المكتب للتسجيلات الصوتية والمرئية — المحاضرات والكلمات والمذكّرات
الصوتية والبودكاست. نقطة قوّته هي **العلامات المرجعية**: مفتاح واحد يعلّم اللحظة التي سمعتها للتوّ،
وتُحفظ العلامات في ملف نصي صغير `.tmk` بجانب التسجيل، بالصيغة نفسها التي يستخدمها
[تطبيق الجوّال](../mobile/index.md). زامِن المجلد، فتظهر هنا العلامات التي أنشأتها على الهاتف،
والعكس صحيح.

وإلى جانب ذلك يوفّر مكتبةً قائمة على المجلدات، وشريط تقديم على شكل موجة صوتية، وترجمات، ونصوصًا
مفرّغة قابلة للتحرير، وتحرير الوسوم.

## النافذة في لمحة { #the-window-at-a-glance }

```
┌───────────────┬──────────────────────────────────────────────────┐
│ FOLDERS       │  Genres │ Artists │ Albums                         │
│ Go to path…   ├─────────────────┬──────────────────┬─────────────┤
│ ⚙ root folder │  PLAYLIST       │  PLAYER          │ TRANSCRIPT  │
│               │                 │  title · waveform│ (when the   │
├───────────────┤                 │  transport       │  file has   │
│ RECENTLY      │  Tag → …        │  BOOKMARKS       │  one)       │
│ BOOKMARKED    │                 │  Speed · Vol     │             │
└───────────────┴─────────────────┴──────────────────┴─────────────┘
  status bar
```

- **FOLDERS** (المجلدات) — تصفّح مجلد تسجيلاتك؛ انقر نقرًا مزدوجًا على مجلد لتحميله. انظر
  [المكتبة](library.md).
- **Genres / Artists / Albums** (الأنواع / الفنانون / الألبومات) و**PLAYLIST** (قائمة التشغيل) — ضيّق
  الاختيار واختر مقطعًا.
- **PLAYER** (المشغّل) — الفيديو وشريط التقديم الموجي وأزرار التحكم وقائمة العلامات. انظر
  [التشغيل](playback.md) و[العلامات المرجعية](bookmarks.md).
- **TRANSCRIPT** (النص المفرّغ) — نص مفرّغ قابل للتحرير، إن كان للملف نص. انظر
  [الترجمات والنصوص المفرّغة](subtitles-and-transcripts.md).
- **RECENTLY BOOKMARKED** (عُلِّمت مؤخرًا) — الملفات التي وضعت عليها علامات مؤخرًا؛ انقر أحدها لتشغيله.

## التثبيت { #install }

المشغّل برنامج بلغة Python 3 يستخدم GTK 3 للنافذة وGStreamer للتشغيل. الحزمة `mutagen` اختيارية —
ولا تلزم إلا **لكتابة** الوسوم.

=== "Debian / Ubuntu / Mint"

    ```bash
    sudo apt install python3-gi gir1.2-gtk-3.0 gir1.2-gst-1.0 gir1.2-gstpbutils-1.0 \
         python3-mutagen gstreamer1.0-plugins-good gstreamer1.0-plugins-bad \
         gstreamer1.0-plugins-ugly gstreamer1.0-gtk3
    ```

=== "Fedora"

    ```bash
    sudo dnf install python3-gobject gtk3 gstreamer1 gstreamer1-plugins-base python3-mutagen \
         gstreamer1-plugins-good gstreamer1-plugins-bad-free gstreamer1-plugins-ugly-free
    ```

=== "Arch"

    ```bash
    sudo pacman -S python-gobject gtk3 gstreamer gst-plugins-base python-mutagen \
         gst-plugins-good gst-plugins-bad gst-plugins-ugly
    ```

تعتمد الصيغ التي تعمل فعلًا على إضافات GStreamer المثبّتة لديك؛ والحزم أعلاه تغطي كل الصيغ المذكورة
في [المكتبة](library.md#supported-formats).

!!! note "لنظام Linux فقط"
    صُمّم مشغّل الصوت لأسطح مكتب Linux. لا يوجد مُشغِّل إطلاق لنظام Windows.

## التشغيل { #run }

```bash
cd audio-player
./run.sh                    # checks the requirements, then starts
./run.sh ~/Music/talk.mp3   # open a file and start playing it
```

يخبرك `run.sh` بالحزم التي عليك تثبيتها إن كان شيء ما ناقصًا.

**مُدخل في القائمة.** شغّل `./install.sh` مرة واحدة. يضيف ذلك **Audio Player** (مشغّل الصوت) إلى
قائمة التطبيقات؛ شغّله من هناك، ثم انقر بالزر الأيمن على أيقونته في اللوحة واختر **Pin** (تثبيت).
أعد تشغيل `install.sh` إن نقلت المجلد.

## التشغيل الأول { #first-run }

1. اضغط **Ctrl+,** لفتح **Settings** (الإعدادات)، واضبط **Folder-tree root** (جذر شجرة المجلدات) على
   المجلد الذي يحوي تسجيلاتك (استخدم **Browse…** (استعراض) لاختياره). اجعله محدودًا — مجلد تسجيلاتك،
   لا مجلد المنزل كله.
2. في **FOLDERS**، انقر نقرًا مزدوجًا على مجلد. تُحمَّل ملفات الوسائط فيه (بما في ذلك المجلدات
   الفرعية) إلى قائمة التشغيل.
3. انقر مقطعًا لتشغيله.
4. أثناء التشغيل، اضغط **B** لوضع علامة على اللحظة التي سمعتها للتوّ.

يفتح المشغّل بحجم الشاشة الكامل ويتذكّر تخطيطه وسمته وآخر مجلد وآخر ملف. وفي المرة التالية يفتح ذلك
الملف متوقفًا مؤقتًا، عند الموضع الذي توقفت فيه.

## أين تُحفظ الأشياء { #where-things-are-stored }

| ماذا | أين |
|---|---|
| العلامات المرجعية | `<recording>.tmk` بجانب كل تسجيل — انظر [العلامات المرجعية](bookmarks.md) |
| الترجمات | `<recording>.<lang>.srt` بجانب التسجيل |
| النصوص المفرّغة | `<recording>.<lang>.ai.txt` بجانب التسجيل |
| الإعدادات ومواضع الاستئناف والملفات الأخيرة | `config.json` بجانب البرنامج |

لا يستخدم المشغّل الشبكة.
