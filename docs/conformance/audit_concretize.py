#!/usr/bin/env python3
"""Regenerate neoskript-plugin/src/test/resources/audit_lines.json from inventory.json.
For each flipped (done/parse-verified) expression/condition/effect, concretize its canonical first
Skript pattern into a script line. FlipAuditTest then parses each and reports which don't parse
(the alias-completeness backlog). Run from repo root: python3 docs/conformance/audit_concretize.py"""
import json, re, os
HERE=os.path.dirname(os.path.abspath(__file__))
inv=json.load(open(os.path.join(HERE,'inventory.json')))
def concretize(p):
    p=re.sub(r'([(|\[])\s*\d*:', r'\1', p)
    p=re.sub(r'\b[a-z]+:(?=[a-z])','',p)
    p=re.sub(r'\d+¦','',p); p=p.replace('¦','')
    p=p.replace("'[s]","'s").replace(r'\%','')
    for _ in range(80):
        n=re.sub(r'\[[^\[\]]*\]','',p)
        n=re.sub(r'\(([^()|]*)\|[^()]*\)',r'\1',n)
        n=re.sub(r'\(([^()|]*)\)',r'\1',n)
        if n==p: break
        p=n
    def slot(m):
        t=m.group(1).lower().lstrip('-').lstrip('~').lstrip('*')
        if any(k in t for k in ('number','integer','float','double','long','short')): return '1'
        if 'string' in t or 'text' in t: return '"x"'
        if 'boolean' in t: return 'true'
        if 'world' in t: return 'world of player'
        if 'location' in t or 'position' in t: return 'location of player'
        if 'vector' in t: return 'vector(1, 1, 1)'
        if 'item' in t: return 'item("stone")'
        if 'block' in t: return 'event-block'
        if 'date' in t: return 'now'
        if 'timespan' in t: return '1 second'
        return 'player'
    p=re.sub(r'%([^%]*)%',slot,p); p=re.sub(r'<[^>]*>','x',p)
    return re.sub(r'\s+',' ',p).strip()
out=[{'id':e['id'],'cat':e['category'],'line':concretize(e['patterns'][0])}
     for e in inv if e['category'] in ('expression','condition','effect') and e['status'] in ('done','parse-verified')]
dest=os.path.join(HERE,'..','..','neoskript-plugin','src','test','resources','audit_lines.json')
json.dump(out, open(dest,'w'))
print(f"wrote {len(out)} audit lines")
