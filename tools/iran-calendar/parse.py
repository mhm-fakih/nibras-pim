import sys, re, html, json, unicodedata, subprocess, datetime

WEEKDAYS = {'شنبه':5,'یکشنبه':6,'دوشنبه':0,'سهشنبه':1,'چهارشنبه':2,'پنجشنبه':3,'جمعه':4}  # python weekday()
HMONTHS = ['محرم','صفر','ربیعالاول','ربیعالثانی','جمادیالاولی','جمادیالثانیه','رجب','شعبان','رمضان','شوال','ذیالقعده','ذیالحجه']
FA_DIGITS = str.maketrans('۰۱۲۳۴۵۶۷۸۹٠١٢٣٤٥٦٧٨٩', '01234567890123456789')

def norm(s):
    s = unicodedata.normalize('NFKC', s)
    return s.replace('ي','ی').replace('ك','ک').replace('ۀ','ه').replace('ة','ه')

def key(s):  # for matching words: no spaces / ZWNJ / tatweel
    return re.sub(r'[\s‌‏‎ـ]', '', norm(s))

LTR = re.compile(r'[0-9A-Za-z٠-٩۰-۹](?:[0-9A-Za-z٠-٩۰-۹./:\-]*[0-9A-Za-z٠-٩۰-۹])?')
MIRROR = str.maketrans('()[]«»', ')(][»«')

def line_text(chars):
    # chars: list of (x0, x1, c) in content order. Zero-width chars join the glyph before them.
    # A space drawn inside a letter's box is the PDF's stand-in for a zero-width non-joiner
    # (امام‌زادگان); left in, it would split the word.
    glyphs = [(x0, x1) for x0, x1, c in chars if c.strip()]
    chars = [(x0, x1, c) for x0, x1, c in chars
             if c.strip() or not any(g0 + 0.3 < (x0 + x1) / 2 < g1 - 0.3 for g0, g1 in glyphs)]
    clusters = []
    for x0, x1, c in chars:
        if clusters and abs(x1 - x0) < 0.01 and c.strip():
            clusters[-1][2] += c
        else:
            clusters.append([x0, x1, c])
    clusters.sort(key=lambda k: k[0])
    visual = ''.join(k[2] if len(k[2]) == 1 else k[2] for k in clusters)
    # visual (left to right) → logical for a right-to-left line: reverse clusters, keep each
    # cluster's own letters as given (a ligature's), then put left-to-right runs back in order.
    logical = ''.join(k[2] for k in reversed(clusters))
    logical = LTR.sub(lambda m: m.group(0)[::-1], logical)
    return re.sub(r'\s+', ' ', logical).strip()

def page_blocks(pdf, page):
    """Every printed line split into cells at horizontal gaps; each cell with its box and text."""
    out = subprocess.run(['mutool', 'draw', '-F', 'stext', '-o', '-', pdf, str(page)], capture_output=True, text=True).stdout
    cells = []
    for l in re.finditer(r'<line bbox="([^"]+)"[^>]*>(.*?)</line>', out, re.S):
        ly0, ly1 = float(l.group(1).split()[1]), float(l.group(1).split()[3])
        chars = [(float(q.split()[0]), float(q.split()[2]), html.unescape(c))
                 for q, c in re.findall(r'<char quad="([^"]+)"[^>]*c="([^"]*)"', l.group(2))]
        # split at gaps: sort by position, a new cell where the next glyph starts well after the last ends
        order = sorted(range(len(chars)), key=lambda i: chars[i][0])
        groups, cur, right = [], [], None
        for i in order:
            x0, x1, c = chars[i]
            if cur and x0 - right > 2.5:
                groups.append(cur); cur = []
            cur.append(i)
            right = max(right or x1, x1) if cur[:-1] else x1
        if cur: groups.append(cur)
        for g in groups:
            g = sorted(g)  # content order within the cell
            sub = [chars[i] for i in g]
            text = norm(line_text(sub)).strip()
            if text:
                xs = [c[0] for c in sub] + [c[1] for c in sub]
                cells.append(dict(x0=min(xs), x1=max(xs), y0=ly0, y1=ly1, yc=(ly0 + ly1) / 2, text=text))
    return cells

WD_KEYS = sorted(WEEKDAYS, key=len, reverse=True)
HM_KEYS = {key(h): i + 1 for i, h in enumerate(HMONTHS)}
HM_KEYS.update({'ربیالاول': 3, 'ربیالثانی': 4, 'ربیعالآخر': 4, 'جمادیالاول': 5, 'جمادیالآخر': 6, 'جمادیالآخره': 6, 'ذیقعده': 11, 'ذیحجه': 12})
ROW = re.compile(r'^(\d+)\s+(\S+)\s+(\d+)\s+(.+?)\s+(\d{4}|")\s+(.*)$')

def parse(pdf, year, nowruz):
    n = int(subprocess.run(['pdfinfo', pdf], capture_output=True, text=True).stdout.split('Pages:')[1].split()[0])
    days, k = [], 0
    hmonth = hyear = None
    for page in range(1, n + 1):
        cells = page_blocks(pdf, page)
        heads = [b for b in cells if key(b['text']) == 'مناسبتها']
        if not heads: continue
        greg = [b for b in cells if key(b['text']) == 'میلادی']
        ev_right = (greg[0]['x0'] if greg else heads[0]['x1'] + 120) - 10
        # rows: the date cells (right edge past the occasions column), one printed line each
        dates = sorted((b for b in cells if b['x1'] > ev_right and b['yc'] > heads[0]['y1']), key=lambda b: b['yc'])
        lines = []
        for b in dates:
            if lines and abs(b['yc'] - lines[-1][0]['yc']) < 3: lines[-1].append(b)
            else: lines.append([b])
        rows = []
        for ln in lines:
            ln.sort(key=lambda b: -b['x1'])
            text = ' '.join(b['text'] for b in ln).translate(FA_DIGITS)
            text = re.sub(r'(\d)(?=[^\d\s"])', r'\1 ', text)      # "21مارس" -> "21 مارس"
            text = re.sub(r'([^\d\s"])(?=\d)', r'\1 ', text)
            compact = key(text)
            wd = next((w for w in WD_KEYS if compact.startswith(w)), None)
            if not wd: continue
            # drop the weekday's words from the front, whatever spacing they had
            rest, need = text.split(), len(wd)
            while need > 0 and rest:
                need -= len(key(rest[0])); rest = rest[1:]
            rows.append(dict(yc=sum(b['yc'] for b in ln) / len(ln), wd=wd, rest=' '.join(rest), ev=[]))
        ev = sorted((b for b in cells if b['x1'] <= ev_right and b['yc'] > heads[0]['y1'] + 2), key=lambda b: b['yc'])
        # each wrapped line to the row it is nearest: a cell's lines sit centred on its row
        for b in ev:
            if not rows: break
            r = min(rows, key=lambda r: abs(r['yc'] - b['yc']))
            if abs(r['yc'] - b['yc']) <= 40: r['ev'].append(b)
        for r in rows:
            date = nowruz + datetime.timedelta(days=k)
            m = ROW.match(r['rest'])
            sday = hday = None
            if m:
                sday, hday = int(m.group(1)), int(m.group(3))
                hm = key(m.group(4))
                if hm != '"':
                    if hm in HM_KEYS: hmonth = HM_KEYS[hm]
                    else: print('unknown hijri month', repr(m.group(4)), date, file=sys.stderr)
                if m.group(5) != '"': hyear = int(m.group(5))
            else:
                print('unparsed row', date, repr(r['rest']), file=sys.stderr)
            if WEEKDAYS[r['wd']] != date.weekday():
                print('weekday mismatch', date, r['wd'], file=sys.stderr)
            # top to bottom, and within a printed line right to left (cells split at digit gaps)
            evs = sorted(r['ev'], key=lambda b: (round(b['yc']), -b['x1']))
            text = re.sub(r'\s+', ' ', ' '.join(b['text'] for b in evs)).strip()
            days.append(dict(date=date.isoformat(), sday=sday, hijri=[hyear, hmonth, hday], text=text,
                             holiday='تعطیل' in text))
            k += 1
    return days

if __name__ == '__main__':
    pdf, year, nowruz = sys.argv[1], int(sys.argv[2]), datetime.date.fromisoformat(sys.argv[3])
    days = parse(pdf, year, nowruz)
    json.dump(days, open(sys.argv[4], 'w', encoding='utf-8'), ensure_ascii=False, indent=1)
    print(len(days), 'days;', sum(1 for d in days if d['text']), 'with occasions;', sum(d['holiday'] for d in days), 'holidays', file=sys.stderr)
