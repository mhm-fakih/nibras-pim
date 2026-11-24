"""Builds backend/src/main/resources/calendar/iran-calendar-<years>.json from the official PDFs.

    python3 build.py Calendar-1405.pdf:1405:2026-03-21 Calendar-1406.pdf:1406:2027-03-21

Each argument is PDF:SHAMSI_YEAR:NOWRUZ (the Gregorian date of 1 Farvardin). Occasions are looked up
in fa2ar.json for their Arabic; new ones are listed as untranslated (add them to fa2ar.json and run
again). Needs mutool and pdfinfo.
"""
import sys, json, datetime, os
import parse, occ

here = os.path.dirname(os.path.abspath(__file__))
fa2ar = json.load(open(os.path.join(here, 'fa2ar.json'), encoding='utf-8'))
days, years, missing = [], [], set()
for arg in sys.argv[1:]:
    pdf, year, nowruz = arg.rsplit(':', 2)
    year, nowruz = int(year), datetime.date.fromisoformat(nowruz)
    years.append(year)
    for n, d in enumerate(parse.parse(pdf, year, nowruz)):
        m = n // 31 + 1 if n < 186 else 7 + (n - 186) // 30
        dd = n % 31 + 1 if n < 186 else (n - 186) % 30 + 1
        if d['sday'] is not None and d['sday'] != dd:
            print('Shamsi day mismatch', d['date'], d['sday'], dd, file=sys.stderr)
        occs = []
        for o in occ.split(d['text']):
            if o not in fa2ar: missing.add(o)
            occs.append({'fa': o, 'ar': fa2ar.get(o, ''), 'holiday': 'تعطیل' in o})
        days.append({'date': d['date'], 'shamsi': [year, m, dd], 'hijri': d['hijri'], 'holiday': d['holiday'], 'occasions': occs})
name = 'iran-calendar-' + '-'.join(map(str, years)) + '.json'
out = os.path.join(here, '..', '..', 'backend', 'src', 'main', 'resources', 'calendar', name)
json.dump({'source': 'Official calendar of Iran (University of Tehran Institute of Geophysics), years '
           + ' and '.join(map(str, years)) + ' SH', 'days': days}, open(out, 'w', encoding='utf-8'), ensure_ascii=False, indent=0)
print(len(days), 'days written to', os.path.normpath(out))
if missing:
    print(len(missing), 'occasions without Arabic:', file=sys.stderr)
    for o in sorted(missing): print('  ', o, file=sys.stderr)
