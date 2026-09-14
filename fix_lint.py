# -*- coding: utf-8 -*-
import io

p = r'app\build.gradle'
s = io.open(p, encoding='utf-8').read()

anchor = """    packagingOptions {
        resources {"""
addition = """    lint {
        checkReleaseBuilds false
        abortOnError false
    }

    packagingOptions {
        resources {"""
if anchor in s:
    s = s.replace(anchor, addition, 1)
    io.open(p, 'w', encoding='utf-8', newline='').write(s)
    print('lint 禁用 OK')
else:
    print('锚点未匹配')
