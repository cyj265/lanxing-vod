# -*- coding: utf-8 -*-
import io

p = r'app\src\mobile\res\values\styles.xml'
s = io.open(p, encoding='utf-8').read()
s = s.replace('android:shapeAppearance', 'shapeAppearance')
io.open(p, 'w', encoding='utf-8', newline='').write(s)
print('shapeAppearance 前缀已修')
