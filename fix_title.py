# -*- coding: utf-8 -*-
import io

p = r'app\src\mobile\res\values\styles.xml'
s = io.open(p, encoding='utf-8').read()

old = '''    <style name="ToolbarTextAppearance" parent="TextAppearance.Material3.TitleLarge">
        <item name="android:textSize">20sp</item>
        <item name="android:textColor">@color/white</item>
        <item name="titleTextColor">@color/white</item>
    </style>'''
new = '''    <style name="ToolbarTextAppearance" parent="TextAppearance.Material3.TitleLarge">
        <item name="android:textSize">20sp</item>
        <item name="android:textStyle">bold</item>
        <item name="android:textColor">#F7C948</item>
        <item name="titleTextColor">#F7C948</item>
    </style>'''

if old in s:
    io.open(p, 'w', encoding='utf-8', newline='').write(s.replace(old, new))
    print('title 金色加粗 OK')
else:
    print('不匹配')
    idx = s.find('ToolbarTextAppearance')
    print(repr(s[idx-20:idx+260]))
