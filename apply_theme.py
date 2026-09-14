import io

# 1. mobile colors.xml
p1 = r'app\src\mobile\res\values\colors.xml'
s = io.open(p1, encoding='utf-8').read()
s = s.replace('<color name="primary">@color/blue_500</color>', '<color name="primary">#3D8BFF</color>')
s = s.replace('<color name="primaryDark">@color/blue_700</color>', '<color name="primaryDark">#1F5CC8</color>')
s = s.replace('<color name="accent">@color/blue_500</color>', '<color name="accent">#3D8BFF</color>')
io.open(p1, 'w', encoding='utf-8', newline='').write(s)
print('mobile colors OK')

# 2. night colors
p2 = r'app\src\mobile\res\values-night\colors.xml'
s = io.open(p2, encoding='utf-8').read()
s = s.replace('<color name="primary">@color/light_blue_500</color>', '<color name="primary">#3D8BFF</color>')
s = s.replace('<color name="primaryDark">@color/light_blue_700</color>', '<color name="primaryDark">#1F5CC8</color>')
s = s.replace('<color name="accent">@color/light_blue_500</color>', '<color name="accent">#4FC3F7</color>')
io.open(p2, 'w', encoding='utf-8', newline='').write(s)
print('night colors OK')

# 3. app_name
p3 = r'app\src\main\res\values\strings.xml'
s = io.open(p3, encoding='utf-8').read()
s = s.replace('<string name="app_name">揽星影视</string>', '<string name="app_name">影视</string>')
io.open(p3, 'w', encoding='utf-8', newline='').write(s)
print('app_name OK')

# 4. icon: 蓝色渐变三角 + 金色星（一脉相承但区分）
p4 = r'app\src\main\res\drawable\ic_launcher_foreground.xml'
s = io.open(p4, encoding='utf-8').read()
new_icon = '''<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:aapt="http://schemas.android.com/aapt"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">

    <!-- 渐变蓝播放三角 -->
    <path
        android:pathData="M34,28 L82,54 L34,80 Z">
        <aapt:attr name="android:fillColor">
            <gradient
                android:startColor="#4FC3F7"
                android:endColor="#1F5CC8"
                android:startX="34"
                android:endX="82"
                android:startY="28"
                android:endY="80"
                android:type="linear" />
        </aapt:attr>
    </path>

    <!-- 金色四角星 -->
    <path
        android:fillColor="#FFD36B"
        android:pathData="M78,20 L81,29 L90,32 L81,35 L78,44 L75,35 L66,32 L75,29 Z" />

    <!-- 星点 -->
    <path
        android:fillColor="#66FFFFFF"
        android:pathData="M28,74 a3,3 0 1 0 6,0 a3,3 0 1 0 -6,0" />
    <path
        android:fillColor="#66FFFFFF"
        android:pathData="M24,26 a2.2,2.2 0 1 0 4.4,0 a2.2,2.2 0 1 0 -4.4,0" />
</vector>
'''
io.open(p4, 'w', encoding='utf-8', newline='').write(new_icon)
print('icon OK')
