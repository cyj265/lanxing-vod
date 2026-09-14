import io

p = r'app\proguard-rules.pro'
s = io.open(p, encoding='utf-8').read()

# 在 CatVod 区块前追加 media3 与 catvod 全包 keep
addition = """# Media3 反射加载类（DefaultMediaSourceFactory 按类名反射）
-keep class androidx.media3.exoplayer.hls.** { *; }
-keep class androidx.media3.exoplayer.dash.** { *; }
-keep class androidx.media3.exoplayer.rtsp.** { *; }
-keep class androidx.media3.exoplayer.smoothstreaming.** { *; }
-keep class androidx.media3.exoplayer.source.DefaultMediaSourceFactory { *; }

# CatVod 全包（DEX 爬虫宿主反射调用）
-keep class com.github.catvod.** { *; }

# EventBus 订阅方法
-keepclassmembers class * {
    @org.greenrobot.eventbus.Subscribe <methods>;
}

# CatVod
"""
if '# CatVod' in s:
    s = s.replace('# CatVod', addition, 1)
    io.open(p, 'w', encoding='utf-8', newline='').write(s)
    print('proguard 规则已补充')
else:
    print('未找到 CatVod 锚点')
