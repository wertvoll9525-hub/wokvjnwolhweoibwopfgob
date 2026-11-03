"""Store screenshots built from real emulator captures (graphro/dist/ci/screens)."""
import os

HERE = os.path.dirname(os.path.abspath(__file__))
SCREENS = os.path.abspath(os.path.join(HERE, '..', '..', 'dist', 'ci', 'screens'))

SHOTS = [
    ('01', '#F4F4F1', '#121212', '복사만 하면<br>차트가 돼요', '엑셀·한글·웹에서 복사한 표를 바로 읽어요', '01-home.png'),
    ('02', '#121212', '#FFFFFF', '섞인 단위도<br>알아서 맞춰요', '“280만원”, “4,380,000원”, 합계 행까지 정리', '02-data.png'),
    ('03', '#FFFFFF', '#121212', '어떤 차트가 맞는지<br>이유까지 알려줘요', '데이터 모양을 보고 추천하고, 안 맞는 건 말려요', '03-editor.png'),
    ('04', '#F04E23', '#FFFFFF', '눌러서 강조,<br>해석은 자동으로', '증감률과 순위를 계산해 한 줄로 적어요', '04-highlight.png'),
    ('05', '#F4F4F1', '#121212', '과장된 차트를<br>잡아내요', '0이 아닌 축, 맞지 않는 원 그래프를 검사해요', '06-check.png'),
    ('06', '#121212', '#FFFFFF', '카톡 이미지·PPT·엑셀로<br>바로 보내요', 'PPT엔 직접 고칠 수 있는 차트로 들어가요', '07-export-image.png'),
]


def shot(num, bg, fg, head, sub, img):
    sub_color = '#CFCFD2' if fg == '#FFFFFF' else '#4A4A50'
    w = 588
    h = int(w * 2400 / 1080)
    left = (1080 - w) // 2 - 14
    top = 540
    body = f'''<div style="width:1080px;height:1920px;background:{bg};position:relative;overflow:hidden">
<div style="position:absolute;left:96px;top:118px;right:96px">
<div class="mono" style="font-size:26px;font-weight:500;letter-spacing:.12em;color:{'#F04E23' if bg != '#F04E23' else '#FFFFFF'}">{num} / 06</div>
<div style="font-size:84px;font-weight:700;letter-spacing:-.05em;line-height:1.14;color:{fg};margin-top:26px">{head}</div>
<div style="font-size:36px;color:{sub_color};margin-top:26px;letter-spacing:-.02em">{sub}</div></div>
<div style="position:absolute;left:{left}px;top:{top}px;width:{w+28}px;height:{h+28}px;border-radius:84px;background:#121212;padding:14px;box-shadow:0 40px 90px rgba(0,0,0,.28)">
<img src="file://{SCREENS}/{img}" style="width:{w}px;height:{h}px;border-radius:70px;display:block"></div></div>'''
    return f'<!doctype html><html lang="ko"><head><meta charset="utf-8"><link rel="stylesheet" href="base.css"><style>html,body{{width:1080px;height:1920px;overflow:hidden}}</style></head><body>{body}</body></html>'


if __name__ == '__main__':
    for num, bg, fg, head, sub, img in SHOTS:
        open(os.path.join(HERE, f'shot{num}.html'), 'w').write(shot(num, bg, fg, head, sub, img))
    print('ok')
