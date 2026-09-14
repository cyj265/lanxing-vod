import io
p = r'app\src\main\res\values\strings.xml'
s = io.open(p, encoding='utf-8').read()
s = s.replace('<string name="app_name">影视</string>', '<string name="app_name">揽星影视</string>')
io.open(p, 'w', encoding='utf-8', newline='').write(s)
print('app_name -> 揽星影视 OK')
