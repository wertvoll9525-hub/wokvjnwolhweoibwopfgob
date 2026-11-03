"""Store promo images in Korean, English and Japanese, built from real emulator captures.
Usage: python3 promo.py  (writes shot-<lang>-NN.html and feature-<lang>.html; render-all.js renders them)"""
import os
HERE = os.path.dirname(os.path.abspath(__file__))
SCREENS = os.path.abspath(os.path.join(HERE, '..', '..', 'dist', 'ci', 'screens'))
JP_FONT = '/tmp/claude-0/-home-user-wokvjnwolhweoibwopfgob/3f7e91d5-db9a-501f-9109-dbcfe8bbcce9/scratchpad/fonts/NotoSansJP.ttf'

FILES = ['01-home.png', '02-data.png', '03-editor.png', '04-highlight.png', '06-check.png', '07-export-image.png']
COLORS = [('#F4F4F1', '#121212'), ('#121212', '#FFFFFF'), ('#FFFFFF', '#121212'), ('#F04E23', '#FFFFFF'), ('#F4F4F1', '#121212'), ('#121212', '#FFFFFF')]

TEXT = {
    'ko': [
        ('복사만 하면<br>차트가 돼요', '엑셀·한글·웹에서 복사한 표를 바로 읽어요'),
        ('섞인 단위도<br>알아서 맞춰요', '“280만원”, “4,380,000원”, 합계 행까지 정리'),
        ('어떤 차트가 맞는지<br>이유까지 알려줘요', '데이터 모양을 보고 추천하고, 안 맞는 건 말려요'),
        ('눌러서 강조,<br>해석은 자동으로', '증감률과 순위를 계산해 한 줄로 적어요'),
        ('과장된 차트를<br>잡아내요', '0이 아닌 축, 맞지 않는 원 그래프를 검사해요'),
        ('카톡 이미지·PPT·엑셀로<br>바로 보내요', 'PPT엔 직접 고칠 수 있는 차트로 들어가요'),
    ],
    'en': [
        ('Copy it.<br>It’s a chart.', 'Reads tables copied from spreadsheets and the web'),
        ('Messy units,<br>cleaned up', '$320K, $280,000 and total rows, sorted out'),
        ('The right chart,<br>and why', 'Recommends by data shape, warns when it doesn’t fit'),
        ('Tap to highlight.<br>Auto captions.', 'Change and rank, calculated into one line'),
        ('Catches<br>misleading charts', 'Axes that skip zero, donuts with too many slices'),
        ('Image, PowerPoint<br>or Excel', 'PowerPoint gets a real, editable chart'),
    ],
    'ja': [
        ('コピーするだけで<br>グラフに', 'Excel・Webからコピーした表をそのまま読み取り'),
        ('混在した単位も<br>自動でそろえる', '「280万円」「4,380,000円」、合計行まで整理'),
        ('合うグラフを<br>理由つきで提案', 'データの形から推薦し、合わないものは止めます'),
        ('タップで強調、<br>説明文は自動', '増減率や順位を計算して1行に'),
        ('誤解を招くグラフを<br>見抜く', '0から始まらない軸、合わない円グラフをチェック'),
        ('画像・PPT・Excelで<br>すぐ共有', 'PPTには編集できる本物のグラフで'),
    ],
}
FEATURE = {
    'ko': ('그래프로', '아무 표나 넣으면, 틀리지 않는 차트'),
    'en': ('Graphro', 'Any table in. An honest chart out.'),
    'ja': ('グラフロ', 'どんな表でも、まちがえないグラフに'),
}


def head(lang):
    jp = f"@font-face{{font-family:J;src:url(file://{JP_FONT})}}" if lang == 'ja' else ''
    fam = "J,P,sans-serif" if lang == 'ja' else "P,sans-serif"
    return f'<!doctype html><html lang="{lang}"><head><meta charset="utf-8"><link rel="stylesheet" href="base.css"><style>{jp} body{{font-family:{fam}}} .h{{font-weight:700}}</style>'


def shot(lang, i):
    bg, fg = COLORS[i]
    headline, sub = TEXT[lang][i]
    sub_color = '#CFCFD2' if fg == '#FFFFFF' else '#4A4A50'
    w = 588; h = int(w * 2400 / 1080); left = (1080 - w) // 2 - 14; top = 540
    size = 84 if lang != 'en' else 80
    body = f'''<div style="width:1080px;height:1920px;background:{bg};position:relative;overflow:hidden">
<div style="position:absolute;left:96px;top:118px;right:96px">
<div class="mono" style="font-size:26px;font-weight:500;letter-spacing:.12em;color:{'#F04E23' if bg != '#F04E23' else '#FFFFFF'}">0{i+1} / 06</div>
<div class="h" style="font-size:{size}px;letter-spacing:{'-.05em' if lang != 'en' else '-.035em'};line-height:1.14;color:{fg};margin-top:26px">{headline}</div>
<div style="font-size:36px;color:{sub_color};margin-top:26px;letter-spacing:-.02em">{sub}</div></div>
<div style="position:absolute;left:{left}px;top:{top}px;width:{w+28}px;height:{h+28}px;border-radius:84px;background:#121212;padding:14px;box-shadow:0 40px 90px rgba(0,0,0,.28)">
<img src="file://{SCREENS}/{lang}/{FILES[i]}" style="width:{w}px;height:{h}px;border-radius:70px;display:block"></div></div>'''
    return head(lang) + f'<style>html,body{{width:1080px;height:1920px;overflow:hidden}}</style></head><body>{body}</body></html>'


def feature(lang):
    name, tag = FEATURE[lang]
    vals = [320, 280, 410, 395, 452, 438]
    months = {'ko': [f'{m}월' for m in range(1, 7)], 'en': ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun'], 'ja': [f'{m}月' for m in range(1, 7)]}[lang]
    bars = ''.join(f'<rect x="{606+i*62}" y="{360-v*0.62:.0f}" width="30" height="{v*0.62:.0f}" rx="4" fill="{"#F04E23" if i==2 else "#DADADC"}"/>' for i, v in enumerate(vals))
    labels = ''.join(f'<text x="{621+i*62}" y="392" font-family="M,J,P" font-size="16" fill="#6E6E75" text-anchor="middle">{m}</text>' for i, m in enumerate(months))
    body = f'''<div style="width:1024px;height:500px;background:#fff;position:relative;overflow:hidden;display:flex;align-items:center">
<div style="padding-left:80px;width:540px;position:relative;z-index:1">
<div style="display:flex;align-items:flex-end;gap:10px;height:64px"><i style="width:16px;height:32px;border-radius:3px;background:#121212"></i><i style="width:16px;height:50px;border-radius:3px;background:#121212"></i><i style="width:16px;height:64px;border-radius:3px;background:#F04E23"></i></div>
<div class="h" style="font-size:64px;letter-spacing:-.05em;margin-top:28px">{name}</div>
<div style="font-size:{28 if lang != 'ja' else 26}px;color:#4A4A50;letter-spacing:-.02em;margin-top:8px">{tag}</div>
<div class="mono" style="font-size:16px;letter-spacing:.12em;color:#6E6E75;margin-top:28px">OFFLINE · ON-DEVICE · NO SIGN-UP</div></div>
<div style="position:absolute;right:0;top:0;width:470px;height:500px;background:#F4F4F1"></div>
<svg style="position:absolute;left:0;top:0" width="1024" height="500"><line x1="600" y1="360" x2="960" y2="360" stroke="#121212" stroke-width="1.5"/>{bars}
<text x="745" y="{360-410*0.62-14:.0f}" font-family="M" font-size="22" font-weight="600" fill="#121212" text-anchor="middle">410 <tspan fill="#C33A15">+46%</tspan></text>{labels}</svg></div>'''
    return head(lang) + f'<style>html,body{{width:1024px;height:500px;overflow:hidden}}</style></head><body>{body}</body></html>'


if __name__ == '__main__':
    for lang in TEXT:
        for i in range(6):
            open(os.path.join(HERE, f'shot-{lang}-0{i+1}.html'), 'w').write(shot(lang, i))
        open(os.path.join(HERE, f'feature-{lang}.html'), 'w').write(feature(lang))
    print('ok')
