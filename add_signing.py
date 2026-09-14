import io

p = r'app\build.gradle'
s = io.open(p, encoding='utf-8').read()

old = """    buildTypes {
        release {
            minifyEnabled true
            shrinkResources true
            proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro', 'proguard-rules-media.pro'
        }
    }
"""
new = """    def keystoreProperties = new Properties()
    def keystorePropertiesFile = rootProject.file("local.properties")
    if (keystorePropertiesFile.exists()) {
        keystoreProperties.load(new FileInputStream(keystorePropertiesFile))
    }
    signingConfigs {
        release {
            storeFile file(keystoreProperties['storeFile'])
            storePassword keystoreProperties['storePassword']
            keyAlias keystoreProperties['keyAlias']
            keyPassword keystoreProperties['keyPassword']
        }
    }
    buildTypes {
        release {
            signingConfig signingConfigs.release
            minifyEnabled true
            shrinkResources true
            proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro', 'proguard-rules-media.pro'
        }
    }
"""
if old in s:
    s = s.replace(old, new)
    io.open(p, 'w', encoding='utf-8', newline='').write(s)
    print('签名配置已加')
else:
    print('未匹配')
    # 打印实际内容帮助调试
    i = s.find('buildTypes')
    print(repr(s[i:i+260]))
