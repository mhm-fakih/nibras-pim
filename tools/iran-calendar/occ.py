import re, json
STARTERS = ('روز','شهادت','ولادت','وفات','سالروز','آغاز','عید','تعطیل','هجوم','زادروز','بزرگداشت','هفته','رحلت','دهه','جشن','پیروزی','نیمه','اعزام','عملیات','تشکیل','تأسیس','تاسیس','فتح','قیام','تصویب','انتخاب','لیله','شب','ملی','پایان','واقعه','ارتحال','حمله','کشف','ورود','تبعید','اربعین','غدیر','مباهله','ازدواج','خروج','جنگ','تحریم','یوم','بازگشت','صدور','نزول','بعثت','عرفه','سقوط','آزادسازی','ترور','اولین','اقامه','غزوه','مبعث','میلاد','زیارت','تخریب','پذیرش','استقلال','اتحاد','تولد','خاکسپاری','شکست')

def clean(t):
    t = t.replace('ـ', ' ـ ') if False else t
    # the 1406 file stores parentheses mirrored: a cell whose first one closes is swapped back
    p = [c for c in t if c in '()']
    if p and p[0] == ')':
        t = t.translate(str.maketrans('()', ')('))
    t = re.sub(r'(?<!ه)\s*ـ\s+', ' ـ ', t)
    t = re.sub(r'هـ\s*(ق|ش)', r'هـ \1', t)
    t = re.sub(r'\(\s+', '(', t); t = re.sub(r'\s+\)', ')', t)
    t = re.sub(r'\)(?=\S)', ') ', t)
    t = re.sub(r'(\d)(هو|هـ)', r'\1 \2', t)
    t = t.replace('هو ش', 'هـ ش').replace('هو ق', 'هـ ق').replace('االله', 'الله')
    t = t.translate(str.maketrans('0123456789٠١٢٣٤٥٦٧٨٩', '۰۱۲۳۴۵۶۷۸۹۰۱۲۳۴۵۶۷۸۹'))
    t = re.sub(r'(?<=[^\s(])\(', ' (', t)
    t = t.replace('اللهعلیه', 'الله علیه').replace('علیهالسلام', 'علیه‌السلام').replace('علیهاالسلام', 'علیهاالسلام')
    return re.sub(r'\s+', ' ', t).strip()

def split(t):
    t = clean(t)
    if not t: return []
    parts = re.split(r'\s+ـ\s+', t)
    out = []
    for p in parts:
        pieces, depth, cur = [], 0, ''
        toks = re.split(r'(\s+و\s*(?=\S))', p)
        i = 0
        while i < len(toks):
            tok = toks[i]
            if i % 2 == 1:  # a " و " separator
                nxt = toks[i + 1] if i + 1 < len(toks) else ''
                if cur.count('(') == cur.count(')') and (nxt.startswith(STARTERS) or cur.endswith(')')):
                    pieces.append(cur.strip()); cur = ''
                else:
                    cur += tok
            else:
                cur += tok
            i += 1
        if cur.strip(): pieces.append(cur.strip())
        out.extend(pieces)
    return [x for x in out if x]

if __name__ == '__main__':
    import sys, collections
    allocc = collections.Counter()
    for y in ('1405', '1406'):
        for d in json.load(open(f'y{y}.json')):
            for o in split(d['text']): allocc[o] += 1
    print(len(allocc), 'distinct occasions;', sum(allocc.values()), 'in all')
    for o, n in list(allocc.items())[:40]: print(n, o)
