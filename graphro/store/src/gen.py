"""Builds the Play Store images as HTML pages; render.js turns them into PNGs."""
import os

HERE = os.path.dirname(os.path.abspath(__file__))
VALS = [320, 280, 410, 395, 452, 438]
BACK = '<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="#121212" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"><path d="M15 5l-7 7 7 7"/></svg>'
UNDO = '<svg width="19" height="19" viewBox="0 0 24 24" fill="none" stroke="{c}" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"><path d="{d}"/></svg>'
LOGO = '<svg width="{s}" height="{s}" viewBox="0 0 18 18"><rect x=".5" y="9" width="4.5" height="9" rx="1" fill="#121212"/><rect x="6.75" y="4" width="4.5" height="14" rx="1" fill="#121212"/><rect x="13" y=".5" width="4.5" height="17.5" rx="1" fill="#F04E23"/></svg>'


def axis():
    return ('<g font-family="M" font-size="10" fill="#6E6E75"><text x="0" y="20">500</text><text x="0" y="89">300</text><text x="0" y="158">100</text></g>'
            '<g stroke="#ECECE9"><line x1="30" y1="24" x2="350" y2="24"/><line x1="30" y1="92.8" x2="350" y2="92.8"/><line x1="30" y1="161.6" x2="350" y2="161.6"/></g>'
            '<line x1="0" y1="196" x2="350" y2="196" stroke="#121212"/>')


def xlabels(hl):
    return ''.join(f'<text x="{56+i*56}" y="214" text-anchor="middle" font-family="M" font-size="11" fill="{"#121212" if i==hl else "#6E6E75"}" font-weight="{600 if i==hl else 400}">{i+1}월</text>' for i in range(6))


def line_chart():
    pts = ' '.join(f'{56+i*56},{196-v*0.344:.1f}' for i, v in enumerate(VALS))
    dots = ''.join(f'<circle cx="{56+i*56}" cy="{196-v*0.344:.1f}" r="3.2" fill="#fff" stroke="#2E2E33" stroke-width="1.6"/>' for i, v in enumerate(VALS) if i != 2)
    return (f'<svg width="350" height="222" viewBox="0 0 350 222">{axis()}'
            f'<polyline points="{pts}" fill="none" stroke="#2E2E33" stroke-width="2" stroke-linejoin="round" stroke-linecap="round"/>{dots}'
            '<line x1="168" y1="62" x2="168" y2="196" stroke="#F04E23" stroke-dasharray="2 3"/>'
            '<circle cx="168" cy="55" r="7.5" fill="#fff"/><circle cx="168" cy="55" r="5.5" fill="#F04E23"/>'
            '<text x="168" y="40" text-anchor="middle" font-family="M" font-size="12" font-weight="600" fill="#121212">410 <tspan fill="#C33A15">+46%</tspan></text>'
            f'{xlabels(2)}</svg>')


def bar_chart(hl=4, target=True):
    bars = ''.join(f'<rect x="{44+i*56}" y="{196-v*0.344:.1f}" width="24" height="{v*0.344:.1f}" rx="3" fill="{"#F04E23" if i==hl else "#DADADC"}"/>' for i, v in enumerate(VALS))
    t = ('<line x1="30" y1="58.4" x2="350" y2="58.4" stroke="#121212" stroke-width="1.2" stroke-dasharray="4 3"/>'
         '<rect x="286" y="49" width="64" height="18" rx="9" fill="#121212"/><text x="292" y="61.5" font-family="M" font-size="10" font-weight="600" fill="#fff">목표 400</text>') if target else ''
    v = VALS[hl]
    return (f'<svg width="350" height="222" viewBox="0 0 350 222">{axis()}{bars}{t}'
            f'<text x="{56+hl*56}" y="{196-v*0.344-8:.1f}" text-anchor="middle" font-family="M" font-size="12" font-weight="600">{v}</text>{xlabels(hl)}</svg>')


def topbar_editor():
    return (f'<div class="bar"><div class="ib">{BACK}</div><div style="flex:1"></div>'
            '<span style="font-size:13px;font-weight:600;padding:0 12px">표</span>'
            f'<div class="ib">{UNDO.format(c="#121212", d="M9 14L4 9l5-5M4 9h10.5a5.5 5.5 0 0 1 0 11H11")}</div>'
            f'<div class="ib">{UNDO.format(c="#BDBDC2", d="M15 14l5-5-5-5M20 9H9.5a5.5 5.5 0 0 0 0 11H13")}</div>'
            '<span class="pill ink" style="height:40px;padding:0 18px;font-size:14px;margin-left:6px">내보내기</span></div>')


def screen_home():
    rec = [('상반기 월별 매출', 'LINE · 오늘', '2,17 14,20 26,8 38,10 50,3 62,5', (26, 8)),
           ('읍면별 인구', 'BAR · 어제', '2,6 14,10 26,14 38,17 50,21 62,24', (2, 6)),
           ('주민 설문 응답', 'DONUT · 10.28', '2,20 14,14 26,16 38,9 50,12 62,7', (38, 9)),
           ('예산 집행률', 'BAR · 10.21', '2,22 14,19 26,17 38,12 50,9 62,4', (62, 4))]
    rows = ''.join(
        f'<div style="display:grid;grid-template-columns:32px 1fr 64px 36px;align-items:center;height:64px;border-top:1px solid #ECECE9">'
        f'<span class="mono" style="font-size:13px;color:#6E6E75">0{i+1}</span>'
        f'<div><div style="font-size:15px;font-weight:600;letter-spacing:-.015em">{t}</div><div class="meta" style="margin-top:3px">{m}</div></div>'
        f'<svg width="64" height="28"><polyline points="{p}" fill="none" stroke="#2E2E33" stroke-width="1.5" stroke-linejoin="round" stroke-linecap="round"/><circle cx="{d[0]}" cy="{d[1]}" r="2.5" fill="#F04E23"/></svg>'
        '<span style="text-align:center;color:#6E6E75;letter-spacing:2px">···</span></div>' for i, (t, m, p, d) in enumerate(rec))
    return f'''<div class="screen">
<div class="bar" style="padding:0 10px 0 24px"><div style="display:flex;align-items:center;gap:8px">{LOGO.format(s=18)}<b style="font-size:17px;letter-spacing:-.02em">그래프로</b></div><div style="flex:1"></div><div class="ib"><svg width="20" height="20" viewBox="0 0 24 24" stroke="#121212" stroke-width="1.6" stroke-linecap="round"><path d="M4 8h16M4 16h16"/></svg></div></div>
<div style="padding:24px 24px 0"><div class="meta">TUE · 11.03</div><div style="font-size:32px;font-weight:700;letter-spacing:-.045em;line-height:1.18;margin-top:10px">무엇을<br>그려볼까요</div></div>
<div style="margin:28px 16px 0;border-radius:24px;background:#F4F4F1;padding:20px 20px 18px">
<div style="display:flex;justify-content:space-between"><span class="meta" style="color:#C33A15">CLIPBOARD</span><span class="meta">TEXT</span></div>
<svg width="318" height="88" style="margin-top:16px"><line x1="0" y1="87" x2="318" y2="87" stroke="#CFCFD2"/><polyline points="6,48 66,58 126,20 186,25 246,6 306,10" fill="none" stroke="#121212" stroke-width="1.75" stroke-linejoin="round" stroke-linecap="round"/><circle cx="126" cy="20" r="4.5" fill="#F04E23"/></svg>
<div style="display:flex;align-items:center;margin-top:16px"><div style="flex:1"><div style="font-size:17px;font-weight:700;letter-spacing:-.025em">방금 복사한 내용</div><div style="font-size:13px;color:#4A4A50;margin-top:3px">표·숫자를 읽어서 차트로 만들어요</div></div>
<span class="pill ink" style="height:44px;padding:0 18px;font-size:15px">만들기 →</span></div></div>
<div style="display:flex;gap:8px;margin:12px 16px 0">{''.join(f'<span class="pill out" style="flex:1;height:48px;font-size:13px">{t}</span>' for t in ["붙여넣기","파일 열기","예제"])}</div>
<div style="padding:28px 24px 4px"><span class="meta">RECENT</span></div>
<div style="padding:0 24px">{rows}</div></div>'''


def screen_data():
    data = [('1월', '320', '1,240', 0), ('2월', '280', '1,105', 1), ('3월', '410', '1,630', 0), ('4월', '395', '1,580', 2), ('5월', '452', '1,712', 0), ('6월', '438', '1,690', 1)]
    rows = ''
    for m, a, b, f in data:
        cell = (f'<span style="justify-self:end;height:32px;padding:0 10px;margin-right:-10px;border-radius:99px;display:flex;align-items:center;gap:6px;font-family:M;font-size:14px;'
                + ('background:#F04E23;color:#fff;font-weight:600"><i style="width:5px;height:5px;border-radius:9px;background:#fff"></i>' if f == 2 else
                   'color:#C33A15;font-weight:600"><i style="width:5px;height:5px;border-radius:9px;background:#F04E23"></i>' if f == 1 else '">')
                + f'{a}</span>')
        rows += (f'<div style="display:grid;grid-template-columns:72px 1fr 1fr;height:44px;align-items:center;border-bottom:1px solid #ECECE9">'
                 f'<b style="font-size:15px;font-weight:600">{m}</b>{cell}<span class="mono" style="justify-self:end;font-size:14px">{b}</span></div>')
    rows += ('<div style="display:grid;grid-template-columns:72px 1fr 1fr;height:44px;align-items:center;color:#9A9AA0;text-decoration:line-through">'
             '<b style="font-size:15px;font-weight:600">합계</b><span class="mono" style="justify-self:end">2,295</span><span class="mono" style="justify-self:end">8,957</span></div>'
             '<div style="font-size:13px;color:#6E6E75;display:flex;justify-content:space-between"><span>합계 행이라 그래프에서 뺐어요</span><u style="color:#121212;font-weight:600">포함하기</u></div>')
    return f'''<div class="screen">
<div class="bar"><div class="ib"><svg width="20" height="20" viewBox="0 0 24 24" stroke="#121212" stroke-width="1.6" stroke-linecap="round"><path d="M6 6l12 12M18 6L6 18"/></svg></div></div>
<div style="padding:8px 24px 0"><div class="meta">STEP 1 / 2 · DATA</div><div class="title" style="margin-top:8px">표를 이렇게<br>읽었어요</div></div>
<div style="display:flex;align-items:center;padding:18px 24px 0"><div style="flex:1;display:flex;gap:14px;font-family:M;font-size:12px;color:#4A4A50"><span>7 × 3</span><span style="color:#C33A15">● 고침 3</span><span>— 제외 1</span></div><div class="seg"><span class="on">표</span><span>원문</span></div></div>
<div style="padding:14px 24px 0"><div style="display:grid;grid-template-columns:72px 1fr 1fr;padding-bottom:8px;border-bottom:1px solid #121212">
<div><b style="font-size:14px">월</b><div class="meta">기간</div></div><div style="text-align:right"><b style="font-size:14px">매출</b><div class="meta">숫자 · 만 원</div></div><div style="text-align:right"><b style="font-size:14px">방문자</b><div class="meta">숫자 · 명</div></div></div>{rows}</div>
<div style="margin:auto 16px 0;background:#121212;border-radius:22px;padding:16px 16px 16px 20px;color:#fff">
<div style="display:flex;justify-content:space-between"><span class="meta" style="color:#CFCFD2">4월 · 매출</span><span class="meta" style="color:#CFCFD2">2 / 3</span></div>
<div style="font-size:17px;font-weight:700;letter-spacing:-.025em;margin-top:10px">“395” → 395</div>
<div style="font-size:13px;color:#CFCFD2;margin-top:6px;line-height:1.5">단위가 빠져 있어서, 다른 칸처럼 만 단위로 읽었어요</div>
<div style="display:flex;gap:8px;margin-top:12px"><span class="pill" style="height:40px;padding:0 16px;border:1px solid #4A4A50;font-size:13px">원래대로</span><span class="pill" style="height:40px;padding:0 16px;border:1px solid #4A4A50;font-size:13px">직접 고치기</span><span class="pill" style="height:40px;padding:0 16px;background:#fff;color:#121212;font-size:13px">확인, 다음</span></div></div>
<div style="padding:12px 16px 24px"><span class="pill sig" style="height:54px;font-size:16px">차트 만들기 →</span></div></div>'''


def glyphs(sel):
    g = {'선': '<polyline points="2,14 9,16 14,6 20,8 26,3" fill="none" stroke="#121212" stroke-width="1.75" stroke-linejoin="round" stroke-linecap="round"/>',
         '막대': '<rect x="3" y="9" width="5" height="11" rx="1"/><rect x="11.5" y="3" width="5" height="17" rx="1"/><rect x="20" y="6" width="5" height="14" rx="1"/>',
         '영역': '<path d="M2 14 L9 16 L14 6 L20 8 L26 3 L26 20 L2 20 Z"/>',
         '가로': '<rect x="2" y="1" width="17" height="4.5" rx="1"/><rect x="2" y="8" width="24" height="4.5" rx="1"/><rect x="2" y="15" width="11" height="4.5" rx="1"/>',
         '도넛': '<circle cx="14" cy="10" r="7.5" fill="none" stroke="#121212" stroke-width="3.5"/>'}
    out = ''
    for k, v in g.items():
        on = k == sel
        out += (f'<div style="width:62px;height:64px;border-radius:16px;{"background:#fff;box-shadow:0 1px 3px rgba(0,0,0,.1);" if on else ""}display:flex;flex-direction:column;align-items:center;justify-content:center;gap:5px;{"opacity:.45;" if k=="도넛" else ""}">'
                f'<svg width="28" height="20" viewBox="0 0 28 20" fill="#121212">{v}</svg><b style="font-size:11px">{k}</b></div>')
    return out


def screen_editor_type():
    return f'''<div class="screen">{topbar_editor()}
<div style="padding:16px 24px 0"><div class="meta">LINE · 6 POINTS · 만 원</div><div class="title" style="margin-top:8px">상반기 월별 매출 <span style="color:#9A9AA0;font-size:16px">✎</span></div></div>
<div style="padding:12px 20px 0">{line_chart()}</div>
<div style="padding:6px 24px 0;font-size:15px;line-height:1.55;color:#4A4A50">3월 매출이 전월보다 46% 늘었어요. 280만 원에서 410만 원으로.</div>
<div style="padding:14px 24px"><span class="pill" style="display:inline-flex;height:36px;padding:0 14px;border:1px solid #ECECE9;font-size:13px;gap:8px"><i style="width:7px;height:7px;border-radius:9px;background:#0F7A57"></i>검사 통과 · 참고 1 ›</span></div>
<div style="margin-top:auto;padding:0 16px 20px;display:flex;flex-direction:column;gap:12px">
<div class="panel"><div style="display:flex;gap:6px">{glyphs("선")}</div>
<div style="padding:14px 4px 0"><b style="font-size:15px">선 그래프</b> <span class="meta" style="color:#C33A15;margin-left:6px">BEST</span><div style="font-size:13px;line-height:1.55;color:#4A4A50;margin-top:4px">기간에 따른 흐름을 보여주기에 가장 좋아요. 3월의 큰 증가가 잘 드러나요.</div></div></div>
<div class="dock"><span class="on">종류</span><span>강조</span><span>축</span><span>라벨</span><span>값</span></div></div></div>'''


def screen_editor_tune():
    return f'''<div class="screen">{topbar_editor()}
<div style="padding:16px 24px 0"><div class="meta">BAR · 6 POINTS · 만 원</div><div class="title" style="margin-top:8px">상반기 월별 매출</div></div>
<div style="padding:12px 20px 0">{bar_chart()}</div>
<div style="display:flex;justify-content:center;margin-top:6px"><div style="display:flex;background:#121212;border-radius:99px;padding:4px;box-shadow:0 8px 24px rgba(0,0,0,.22)"><span style="height:36px;padding:0 14px;color:#fff;font-size:13px;font-weight:600;display:flex;align-items:center">강조 해제</span><span style="height:36px;padding:0 14px;border-radius:99px;background:#fff;font-size:13px;font-weight:600;display:flex;align-items:center">메모 달기</span></div></div>
<div style="padding:12px 24px 0;font-size:15px;line-height:1.55;color:#4A4A50">5월 매출이 452만 원으로 가장 많아요. 목표 400만 원에 닿은 달은 3개예요.</div>
<div style="margin-top:auto;padding:0 16px 20px;display:flex;flex-direction:column;gap:12px">
<div class="panel"><div class="meta" style="padding:0 4px 8px">AUTO HIGHLIGHT</div>
<div style="display:flex;background:#E6E6E2;border-radius:99px;padding:3px">{''.join(f'<span style="flex:1;height:40px;border-radius:99px;display:flex;align-items:center;justify-content:center;font-size:13px;font-weight:600;{"background:#fff;box-shadow:0 1px 3px rgba(0,0,0,.1)" if t=="최댓값" else ""}">{t}</span>' for t in ["최댓값","가장 큰 변화","직접","없음"])}</div>
<div style="display:flex;align-items:center;min-height:52px;padding:0 4px;border-top:1px solid #E6E6E2;margin-top:8px"><div style="flex:1"><b style="font-size:13px">목표선</b><div style="font-size:13px;color:#6E6E75">400 만 원 · 눌러서 바꾸기</div></div><div class="sw"><i></i></div></div>
<div style="display:flex;align-items:center;min-height:52px;padding:0 4px;border-top:1px solid #E6E6E2"><b style="font-size:13px;flex:1">강조 색</b><i style="width:22px;height:22px;border-radius:99px;background:#F04E23;box-shadow:0 0 0 2px #F4F4F1,0 0 0 3.5px #121212;margin:0 9px"></i><i style="width:22px;height:22px;border-radius:99px;background:#2C3E9E;margin:0 9px"></i><i style="width:22px;height:22px;border-radius:99px;background:#121212;margin:0 9px"></i></div></div>
<div class="dock"><span>종류</span><span class="on">강조</span><span>축</span><span>라벨</span><span>값</span></div></div></div>'''


def mini_bars(axis_min):
    out = ''
    for i, v in enumerate(VALS):
        h = (v - axis_min) / (500 - axis_min) * 92
        cx = 52 + i * 48
        c = '#F04E23' if i == 2 else '#2E2E33' if i == 1 else '#DADADC'
        out += f'<rect x="{cx-12}" y="{100-h:.1f}" width="24" height="{h:.1f}" rx="3" fill="{c}"/><text x="{cx}" y="122" text-anchor="middle" font-family="M" font-size="10" fill="#6E6E75">{i+1}월</text>'
    return f'<svg width="310" height="128" viewBox="0 0 310 128"><text x="0" y="11" font-family="M" font-size="10" fill="#6E6E75">500</text><text x="0" y="104" font-family="M" font-size="10" fill="#6E6E75">{axis_min}</text>{out}<line x1="30" y1="100" x2="310" y2="100" stroke="#121212"/></svg>'


def screen_check():
    return f'''<div class="screen" style="background:#5E5E62"><div style="height:56px;flex:none"></div>
<div style="flex:1;background:#fff;border-radius:28px 28px 0 0;display:flex;flex-direction:column">
<div style="align-self:center;width:36px;height:4px;border-radius:4px;background:#DADADC;margin-top:8px"></div>
<div style="padding:20px 24px 0"><div class="meta">HONESTY CHECK</div><div class="title" style="margin-top:8px">정직성 검사</div></div>
<div style="display:flex;gap:28px;padding:18px 24px 0">{''.join(f'<div><div class="mono" style="font-size:34px;font-weight:500;line-height:1;color:{c}">{n}</div><div style="font-size:12px;color:#4A4A50;margin-top:4px">{l}</div></div>' for n,l,c in [(1,"고칠 것","#F04E23"),(1,"참고","#121212"),(5,"통과","#0F7A57")])}</div>
<div style="margin:20px 16px 0;background:#F4F4F1;border-radius:24px;padding:20px">
<div class="meta" style="color:#C33A15">FIX · AXIS-ZERO</div><div style="font-size:17px;font-weight:700;letter-spacing:-.025em;margin-top:6px">세로축이 250부터 시작해요</div>
<div style="font-size:13px;line-height:1.55;color:#4A4A50;margin-top:6px">가장 큰 값은 가장 작은 값의 <b style="color:#121212">1.6배</b>인데, 막대로는 <b style="color:#C33A15">6.7배</b>로 보여요.</div>
<div style="display:flex;gap:10px;margin-top:14px"><div style="flex:1;background:#fff;border-radius:16px;padding:10px">{mini_bars(250).replace('width="310"','width="100%"')}<div style="font-size:12px;font-weight:600;color:#C33A15;text-align:center">지금</div></div></div>
<div style="display:flex;gap:8px;align-items:center;margin-top:14px"><div class="seg" style="background:#fff"><span class="on">지금</span><span>고친 뒤</span></div><span class="pill sig" style="flex:1;height:42px;font-size:14px">0부터 시작</span></div></div>
<div style="margin:0 24px;padding:18px 0;border-bottom:1px solid #ECECE9"><div class="meta">NOTE</div><div style="font-size:16px;font-weight:700;margin-top:6px">6개 시점은 추세라 하기엔 짧아요</div><div style="font-size:13px;color:#4A4A50;margin-top:6px">해석 문구에 ‘추세’ 대신 ‘늘었어요/줄었어요’라고 썼어요.</div></div>
<div style="margin:0 24px;padding:16px 0;font-size:13px;color:#4A4A50"><i style="display:inline-block;width:7px;height:7px;border-radius:9px;background:#0F7A57;margin-right:8px"></i><b style="color:#121212">통과 5</b> · 기간 순서, 단위 표시, 색 구분, 3D 효과 없음, 이중 축 없음</div>
</div></div>'''


def screen_export():
    pts = ' '.join(f'{10+i*32},{124-v*0.2:.0f}' for i, v in enumerate(VALS))
    return f'''<div class="screen" style="background:#5E5E62"><div style="height:56px;flex:none"></div>
<div style="flex:1;background:#fff;border-radius:28px 28px 0 0;display:flex;flex-direction:column">
<div style="align-self:center;width:36px;height:4px;border-radius:4px;background:#DADADC;margin-top:8px"></div>
<div style="display:flex;align-items:flex-end;padding:20px 24px 0"><div style="flex:1"><div class="meta">EXPORT</div><div class="title" style="margin-top:8px">내보내기</div></div><div class="seg"><span class="on">이미지</span><span>PPT</span><span>엑셀</span></div></div>
<div style="margin:20px 16px 0;height:300px;border-radius:24px;background:#F4F4F1;display:flex;align-items:center;justify-content:center">
<div style="width:212px;height:265px;background:#fff;border-radius:4px;box-shadow:0 12px 32px rgba(0,0,0,.12);padding:18px 16px;display:flex;flex-direction:column;gap:6px">
<span class="mono" style="font-size:7px;letter-spacing:.08em;color:#6E6E75">1월 – 6월 · 만 원</span><b style="font-size:15px;letter-spacing:-.04em;line-height:1.25">3월 매출이 전월보다<br>46% 늘었어요</b><span style="font-size:8px;color:#4A4A50">상반기 월별 매출 · 단위: 만 원</span>
<svg width="180" height="130" viewBox="0 0 180 130"><line x1="0" y1="124" x2="180" y2="124" stroke="#121212" stroke-width=".75"/><polyline points="{pts}" fill="none" stroke="#2E2E33" stroke-width="1.75" stroke-linejoin="round"/><circle cx="74" cy="42" r="4" fill="#F04E23" stroke="#fff" stroke-width="1.5"/><text x="74" y="32" text-anchor="middle" font-family="M" font-size="8" font-weight="600">410 +46%</text></svg>
<span class="mono" style="margin-top:auto;font-size:6px;color:#9A9AA0">출처: 매장 판매 기록</span></div></div>
<div style="padding:8px 24px 0">{''.join(f'<div style="display:flex;align-items:center;min-height:54px;border-bottom:1px solid #ECECE9"><div style="flex:1"><b style="font-size:14px">{t}</b><div style="font-size:12px;color:#6E6E75">{s}</div></div><div class="sw"><i></i></div></div>' for t,s in [("해석을 제목으로","“3월 매출, 전월보다 46% 늘었어요”처럼"),("세로형 4:5","카톡·인스타에 꽉 차게"),("출처 표기","아래에 작게")])}</div>
<div style="margin-top:auto;display:flex;gap:8px;padding:12px 16px 24px"><span class="pill out" style="height:54px;padding:0 22px;font-size:15px">저장</span><span class="pill ink" style="flex:1;height:54px;font-size:15px;font-weight:700">카톡 등으로 공유</span></div>
</div></div>'''


SHOTS = [
    ('01', '#F4F4F1', '#121212', '복사만 하면<br>차트가 돼요', '엑셀·한글·웹에서 복사한 표를 바로 읽어요', screen_home),
    ('02', '#121212', '#FFFFFF', '섞인 단위도<br>알아서 맞춰요', '“280만원”, “4,380,000원”, 합계 행까지 정리', screen_data),
    ('03', '#FFFFFF', '#121212', '어떤 차트가 맞는지<br>이유까지 알려줘요', '데이터 모양을 보고 추천하고, 안 맞는 건 말려요', screen_editor_type),
    ('04', '#F04E23', '#FFFFFF', '눌러서 강조,<br>해석은 자동으로', '증감률과 순위를 계산해 한 줄로 적어요', screen_editor_tune),
    ('05', '#F4F4F1', '#121212', '과장된 차트를<br>잡아내요', '0이 아닌 축, 맞지 않는 원 그래프를 검사해요', screen_check),
    ('06', '#121212', '#FFFFFF', '카톡 이미지·PPT·엑셀로<br>바로 보내요', 'PPT엔 직접 고칠 수 있는 차트로 들어가요', screen_export),
]


def page(body, w, h):
    return f'<!doctype html><html lang="ko"><head><meta charset="utf-8"><link rel="stylesheet" href="base.css"><style>html,body{{width:{w}px;height:{h}px;overflow:hidden}}</style></head><body>{body}</body></html>'


def shot(num, bg, fg, head, sub, fn):
    sub_color = '#CFCFD2' if fg == '#FFFFFF' else '#4A4A50'
    scale = 1.62
    body = f'''<div style="width:1080px;height:1920px;background:{bg};position:relative;overflow:hidden">
<div style="position:absolute;left:96px;top:118px;right:96px">
<div class="mono" style="font-size:26px;font-weight:500;letter-spacing:.12em;color:{'#F04E23' if bg != '#F04E23' else '#FFFFFF'};opacity:.95">{num} / 06</div>
<div style="font-size:84px;font-weight:700;letter-spacing:-.05em;line-height:1.14;color:{fg};margin-top:26px">{head}</div>
<div style="font-size:36px;color:{sub_color};margin-top:26px;letter-spacing:-.02em">{sub}</div></div>
<div style="position:absolute;left:{(1080-390*scale)/2-14:.0f}px;top:{1920-844*scale+40:.0f}px;width:{390*scale+28:.0f}px;height:{844*scale+28:.0f}px;border-radius:78px;background:#121212;padding:14px;box-shadow:0 40px 90px rgba(0,0,0,.28)">
<div style="width:{390*scale:.0f}px;height:{844*scale:.0f}px;border-radius:64px;overflow:hidden;background:#fff">
<div style="transform:scale({scale});transform-origin:0 0">{fn()}</div></div></div></div>'''
    return page(body, 1080, 1920)


def feature():
    pts = ' '.join(f'{620+i*62},{330-v*0.5:.0f}' for i, v in enumerate(VALS))
    body = f'''<div style="width:1024px;height:500px;background:#FFFFFF;position:relative;overflow:hidden;display:flex;align-items:center">
<div style="padding-left:80px;width:560px">
<div style="display:flex;align-items:flex-end;gap:10px;height:64px">
<i style="width:16px;height:32px;border-radius:3px;background:#121212"></i><i style="width:16px;height:50px;border-radius:3px;background:#121212"></i><i style="width:16px;height:64px;border-radius:3px;background:#F04E23"></i></div>
<div style="font-size:64px;font-weight:700;letter-spacing:-.05em;margin-top:28px">그래프로</div>
<div style="font-size:30px;color:#4A4A50;letter-spacing:-.02em;margin-top:8px">아무 표나 넣으면, 틀리지 않는 차트</div>
<div class="mono" style="font-size:16px;letter-spacing:.12em;color:#6E6E75;margin-top:28px">OFFLINE · ON-DEVICE · NO SIGN-UP</div></div>
<div style="position:absolute;right:0;top:0;width:470px;height:500px;background:#F4F4F1"></div>
<svg style="position:absolute;left:0;top:0" width="1024" height="500"><line x1="600" y1="360" x2="960" y2="360" stroke="#121212" stroke-width="1.5"/>
{''.join(f'<rect x="{606+i*62}" y="{360-v*0.62:.0f}" width="30" height="{v*0.62:.0f}" rx="4" fill="{"#F04E23" if i==2 else "#DADADC"}"/>' for i,v in enumerate(VALS))}
<text x="621" y="{360-410*0.62-14+124:.0f}" font-family="M" font-size="20" font-weight="600" fill="#121212" text-anchor="middle"></text>
<text x="745" y="{360-410*0.62-14:.0f}" font-family="M" font-size="22" font-weight="600" fill="#121212" text-anchor="middle">410 <tspan fill="#C33A15">+46%</tspan></text>
{''.join(f'<text x="{621+i*62}" y="392" font-family="M" font-size="16" fill="#6E6E75" text-anchor="middle">{i+1}월</text>' for i in range(6))}</svg></div>'''
    return page(body, 1024, 500)


def icon():
    body = '''<div style="width:512px;height:512px;background:#FFFFFF;display:flex;align-items:flex-end;justify-content:center;gap:28px;padding-bottom:132px">
<i style="width:66px;height:180px;border-radius:12px;background:#121212"></i><i style="width:66px;height:282px;border-radius:12px;background:#121212"></i><i style="width:66px;height:396px;border-radius:12px;background:#F04E23"></i></div>'''
    # match the adaptive icon proportions: bars sit in the central safe area
    body = body.replace('padding-bottom:132px', 'padding-bottom:118px').replace('height:396px', 'height:370px').replace('height:282px', 'height:266px').replace('height:180px', 'height:170px')
    return page(body, 512, 512)


if __name__ == '__main__':
    for num, bg, fg, head, sub, fn in SHOTS:
        open(os.path.join(HERE, f'shot{num}.html'), 'w').write(shot(num, bg, fg, head, sub, fn))
    open(os.path.join(HERE, 'feature.html'), 'w').write(feature())
    open(os.path.join(HERE, 'icon.html'), 'w').write(icon())
    print('ok')
