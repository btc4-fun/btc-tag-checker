#!/usr/bin/env python3
"""Render only trusted, whitelisted public templates; never import app.py or fetch URLs."""
import argparse
from datetime import datetime, timezone
import hashlib
from html.parser import HTMLParser
import json
from pathlib import Path
import re


ALLOWED = ('base.html', 'guide.html', 'terms.html', 'security.html', '_wallet_safety.html', '_purchase_warning.html', '_payment_details.html')
PAGES = ('guide', 'terms', 'payment', 'security', 'costs')

def check_snapshot(target):
    manifest = json.loads((target/'manifest.json').read_text(encoding='utf-8'))
    if set(manifest['pages']) != set(PAGES):
        raise ValueError('Snapshot page inventory mismatch')
    for page in PAGES:
        if hashlib.sha256((target/(page+'.txt')).read_bytes()).hexdigest() != manifest['pages'][page]:
            raise ValueError('Snapshot hash mismatch: '+page)
    print('Bundled snapshot verified: '+manifest['fetched_at'])

class PlainText(HTMLParser):
    def __init__(self):
        super().__init__(convert_charrefs=True)
        self.main = False
        self.hidden = []
        self.parts = []
    def handle_starttag(self, tag, attrs):
        if tag == 'main': self.main = True
        if not self.main: return
        if self.hidden:
            if tag not in ('img','input','br','meta','link','hr'): self.hidden.append(tag)
            return
        if tag in ('script','style','figure','nav','aside','noscript') or any(key == 'hidden' for key, value in attrs):
            self.hidden.append(tag)
            return
        if tag in ('h1','h2','h3','p','li','section','br','code','button'): self.parts.append('\n')
    def handle_endtag(self, tag):
        if self.hidden:
            if tag == self.hidden[-1]: self.hidden.pop()
            return
        if tag == 'main': self.main = False
        if self.main and tag in ('h1','h2','h3','p','li','section','code','button'): self.parts.append('\n')
    def handle_data(self, data):
        if self.main and not self.hidden: self.parts.append(data)
def extract(html):
    parser=PlainText();parser.feed(html)
    return '\n\n'.join(re.sub(r'\s+', ' ', line).strip() for line in ''.join(parser.parts).splitlines() if line.strip())+'\n'
def main():
    parser=argparse.ArgumentParser()
    parser.add_argument('--source-root',type=Path,help='Explicit trusted btc-envelope-maker checkout; only public template allowlist is read')
    parser.add_argument('--check',action='store_true',help='Verify bundled content without any source checkout or Jinja2')
    args=parser.parse_args()
    target=Path(__file__).resolve().parents[1]/'app/src/main/assets/public'
    if args.check or args.source_root is None:
        check_snapshot(target)
        return
    from jinja2 import DictLoader, Environment, StrictUndefined
    templates=args.source_root/'bitcoin4funsite/templates'
    target.mkdir(parents=True,exist_ok=True)
    sources={name:(templates/name).read_text(encoding='utf-8') for name in ALLOWED if (templates/name).is_file()}
    # A loader dictionary disallows includes outside this public whitelist.
    env=Environment(loader=DictLoader(sources),undefined=StrictUndefined,autoescape=True)
    pages={name:extract(env.get_template(name+'.html').render()) for name in ('guide','terms')}
    pages['payment']=extract('<main>'+env.get_template('_payment_details.html').render(page='payment')+'</main>')
    if 'security.html' in sources:
        pages['security']=extract(env.get_template('security.html').render())
        security_source='security.html'
    else:
        pages['security']=extract('<main>'+env.get_template('_wallet_safety.html').render()+'</main>')
        security_source='_wallet_safety.html (security page not yet present)'
    pages['costs']='قیمت و کارمزد ثابت در این برنامه ذخیره نشده است. برای نرخ و برآورد تازه، صفحهٔ آنلاین هزینه‌ها را به انتخاب خود باز کنید.\n\nhttps://btc4.fun/costs\n'
    stamp=datetime.now(timezone.utc).isoformat(timespec='seconds')
    digest=hashlib.sha256(json.dumps(sources,sort_keys=True,ensure_ascii=False).encode()).hexdigest()
    disclaimer='نسخهٔ آفلاینِ متن عمومی وب‌سایت؛ ممکن است تغییر کرده باشد. پیش از هر پرداخت، اطلاعات فعلی سایت، شمارهٔ مقصد و نام صاحب حساب را دوباره بررسی کنید. قیمت‌ها فقط در صفحهٔ آنلاین هزینه‌ها به‌روز می‌شوند. اشاره‌های متن به «سایت» دربارهٔ وب‌سایت است؛ برنامه پس از رضایت شما استعلام شبکه می‌فرستد. تصاویر و قابلیت‌های تعاملی وب در این نسخهٔ متنی موجود نیستند.'
    hashes={}
    for page,text in pages.items():
        content=disclaimer+'\n\nزمان برداشت متن\n\n'+stamp+'\n\nنسخهٔ محتوا\n\n'+digest[:12]+'\n\n'+text
        (target/(page+'.txt')).write_text(content)
        hashes[page]=hashlib.sha256(content.encode()).hexdigest()
    manifest={'version':1,'fetched_at':stamp,'source_sha256':digest,'security_source':security_source,'sources':{name:hashlib.sha256(text.encode()).hexdigest() for name,text in sources.items()},'pages':hashes,'images_bundled':False,'prices_bundled':False}
    (target/'manifest.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2)+'\n')
    print(json.dumps({'pages':list(pages),'snapshot':stamp,'version':digest[:12],'security_source':security_source},ensure_ascii=False))
if __name__=='__main__':main()
