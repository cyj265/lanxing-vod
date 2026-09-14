# -*- coding: utf-8 -*-
"""揽星影视 UI 特色化：首页品牌区 + 胶囊分类 + 渐变卡片 + 导航胶囊 + 金色点缀"""
import io, os

BASE = r'D:\TVBox\app\src'
MOB = os.path.join(BASE, 'mobile', 'res')
MAIN = os.path.join(BASE, 'main', 'res')

def w(path, content):
    full = os.path.join(MOB if path.startswith('mobile') else MAIN, path.split('/', 1)[1] if '/' in path else path)
    os.makedirs(os.path.dirname(full), exist_ok=True)
    io.open(full, 'w', encoding='utf-8', newline='').write(content)
    print('写入', full)

def edit(path, old, new):
    full = os.path.join(MOB if path.startswith('mobile') else MAIN, path.split('/', 1)[1] if '/' in path else path)
    s = io.open(full, encoding='utf-8').read()
    if old not in s:
        print('!! 未匹配:', path, '->', old[:50])
        return
    io.open(full, 'w', encoding='utf-8', newline='').write(s.replace(old, new, 1))
    print('修改', full)

# ---------- 1. 分类 tab：渐变蓝胶囊选中态 ----------
w('mobile/drawable/shape_item_round_activated.xml', '''<?xml version="1.0" encoding="utf-8"?>
<ripple xmlns:android="http://schemas.android.com/apk/res/android"
    android:color="#33FFFFFF">
    <item>
        <shape android:shape="rectangle">
            <gradient
                android:angle="0"
                android:endColor="#1F5CC8"
                android:startColor="#3D8BFF"
                android:type="linear" />
            <corners android:radius="28dp" />
            <padding
                android:bottom="7dp"
                android:left="18dp"
                android:right="18dp"
                android:top="7dp" />
        </shape>
    </item>
</ripple>
''')

w('mobile/drawable/shape_item_round_normal.xml', '''<?xml version="1.0" encoding="utf-8"?>
<ripple xmlns:android="http://schemas.android.com/apk/res/android"
    android:color="?attr/colorControlHighlight">
    <item>
        <shape android:shape="rectangle">
            <solid android:color="#1A2436" />
            <corners android:radius="28dp" />
            <padding
                android:bottom="7dp"
                android:left="18dp"
                android:right="18dp"
                android:top="7dp" />
        </shape>
    </item>
</ripple>
''')

# 分类文字色 selector
w('mobile/color/selector_type_text.xml', '''<?xml version="1.0" encoding="utf-8"?>
<selector xmlns:android="http://schemas.android.com/apk/res/android">
    <item android:color="@color/white" android:state_activated="true" />
    <item android:color="#9AA3B0" />
</selector>
''')

edit('mobile/layout/adapter_type.xml',
     'android:textColor="@color/white"',
     'android:textColor="@color/selector_type_text"')

# ---------- 2. 首页 Toolbar 品牌区 ----------
w('mobile/drawable/shape_toolbar_bg.xml', '''<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="rectangle">
    <gradient
        android:angle="0"
        android:endColor="#0E1116"
        android:startColor="#152338"
        android:type="linear" />
</shape>
''')

edit('mobile/layout/fragment_vod.xml',
     '''        android:background="@color/transparent"
        android:elevation="0dp"
        app:elevation="0dp"''',
     '''        android:background="@drawable/shape_toolbar_bg"
        android:elevation="0dp"
        app:elevation="0dp"''')

# FAB 改揽星蓝
for fid in ('filter', 'link', 'top'):
    edit('mobile/layout/fragment_vod.xml',
         'app:backgroundTint="@color/blue_500"',
         'app:backgroundTint="@color/accent"')

# ---------- 3. 新 logo：渐变三角 + 金星 ----------
w('mobile/drawable/ic_logo.xml', '''<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:aapt="http://schemas.android.com/aapt"
    android:width="36dp"
    android:height="36dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
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
    <path
        android:fillColor="#F7C948"
        android:pathData="M66,18 L69,27 L78,30 L69,33 L66,42 L63,33 L54,30 L63,27 Z" />
</vector>
''')

# ---------- 4. 卡片：大圆角 + 渐变占位 + 渐变名称遮罩 ----------
w('mobile/drawable/shape_vod_placeholder.xml', '''<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="rectangle">
    <gradient
        android:angle="135"
        android:endColor="#16263F"
        android:startColor="#0E1116"
        android:type="linear" />
    <corners android:radius="16dp" />
</shape>
''')

edit('mobile/drawable/shape_vod.xml',
     '<corners android:radius="8dp" />',
     '<corners android:radius="16dp" />')

w('mobile/drawable/shape_vod_name.xml', '''<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="rectangle">
    <gradient
        android:angle="90"
        android:endColor="#66000000"
        android:startColor="#CC000000"
        android:type="linear" />
    <corners
        android:bottomLeftRadius="16dp"
        android:bottomRightRadius="16dp" />
    <padding
        android:bottom="6dp"
        android:left="6dp"
        android:right="6dp"
        android:top="6dp" />
</shape>
''')

# 卡片布局：占位渐变 + 海报感高度
for layout in ('adapter_vod_rect.xml', 'adapter_vod.xml'):
    edit('mobile/layout/' + layout,
         'android:background="@color/black_20"',
         'android:background="@drawable/shape_vod_placeholder"')
    edit('mobile/layout/' + layout,
         'android:layout_height="80dp"',
         'android:layout_height="96dp"')

# ---------- 5. 底部导航：选中渐变胶囊指示器 ----------
w('mobile/drawable/shape_indicator.xml', '''<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="rectangle">
    <gradient
        android:angle="0"
        android:endColor="#1F5CC8"
        android:startColor="#3D8BFF"
        android:type="linear" />
    <corners android:radius="24dp" />
</shape>
''')

edit('mobile/values/styles.xml',
     '<item name="android:color">@color/indicator</item>',
     '''<item name="android:color">@color/accent</item>
        <item name="android:shapeAppearance">@style/IndicatorShape</item>''')

edit('mobile/values/styles.xml',
     '<style name="ToolbarTextAppearance" parent="TextAppearance.Material3.TitleLarge">',
     '<style name="IndicatorShape" parent="ShapeAppearance.Material3.Corner.Full"/>\n\n    <style name="ToolbarTextAppearance" parent="TextAppearance.Material3.TitleLarge">')
edit('mobile/values/styles.xml',
     '''<item name="android:textSize">20sp</item>
          <item name="android:textColor">@color/white</item>
          <item name="titleTextColor">@color/white</item>''',
     '''<item name="android:textSize">20sp</item>
          <item name="android:textStyle">bold</item>
          <item name="android:textColor">#F7C948</item>
          <item name="titleTextColor">#F7C948</item>''')

print('全部完成')
