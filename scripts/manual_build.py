"""Validate repository docs; build MkDocs and a self-contained offline manual."""
from pathlib import Path
from html import escape, unescape
from html.parser import HTMLParser
import json
import posixpath
import re
import subprocess
import sys
import tomllib
from urllib.parse import unquote, urlsplit
import markdown
import yaml

ROOT=Path(__file__).resolve().parents[1]
DOCS=ROOT/'docs'
config=yaml.safe_load((ROOT/'mkdocs.yml').read_text(encoding='utf-8-sig'))
def flatten(nav):
    for item in nav:
        for title,value in item.items():
            if isinstance(value,list): yield from flatten(value)
            else: yield value
pages=list(flatten(config['nav']))
assert set(pages)=={p.relative_to(DOCS).as_posix() for p in DOCS.rglob('*.md')}, 'Navigation must cover all chapters'
class Inspect(HTMLParser):
    def __init__(self): super().__init__(); self.ids=set();self.links=[];self.text=[]
    def handle_starttag(self,tag,attrs):
        a=dict(attrs)
        if 'id' in a:self.ids.add(a['id'])
        if tag=='a' and 'href' in a:self.links.append(a['href'])
    def handle_data(self,data):self.text.append(data)
rendered={}; inspected={}
for page in pages:
    source=(DOCS/page).read_text(encoding='utf-8')
    for lang,body in re.findall(r'^```(toml|json)\s*\n(.*?)^```',source,re.M|re.S):
        try: (tomllib.loads if lang=='toml' else json.loads)(body)
        except Exception as ex:raise ValueError(f'{page}: invalid {lang} example: {ex}') from ex
    rendered[page]=markdown.markdown(source,extensions=['tables','fenced_code','toc'])
    obj=Inspect();obj.feed(rendered[page]);inspected[page]=obj
for page,obj in inspected.items():
    for link in obj.links:
        url=urlsplit(unescape(link))
        if url.scheme or url.netloc:continue
        target=posixpath.normpath(posixpath.join(posixpath.dirname(page),unquote(url.path))) if url.path else page
        assert (DOCS/target).exists(), f'{page}: missing {link}'
        if url.fragment and target in inspected:
            assert unquote(url.fragment) in inspected[target].ids,f'{page}: missing anchor {link}'
subprocess.run([sys.executable,str(ROOT/'scripts/manual_reference.py'),'--check'],cwd=ROOT,check=True)
subprocess.run([sys.executable,'-m','mkdocs','build','--strict'],cwd=ROOT,check=True)
def prefix(page):return 'chapter-'+page.removesuffix('.md').replace('/','-')
articles=[];nav=[]
for page in pages:
    pid=prefix(page); title=re.search(r'<h1[^>]*>(.*?)</h1>',rendered[page],re.S).group(1)
    nav.append(f'<a href="#{pid}" data-target="{pid}">{title}</a>')
    body=re.sub(r' id="([^"]+)"',lambda m:f' id="{pid}--{m[1]}"',rendered[page])
    def link(m):
        raw=m[1]; url=urlsplit(unescape(raw))
        if url.scheme or url.netloc:return m[0]
        target=posixpath.normpath(posixpath.join(posixpath.dirname(page),unquote(url.path))) if url.path else page
        if target not in rendered:return m[0]
        return 'href="#'+prefix(target)+('--'+escape(unquote(url.fragment),quote=True) if url.fragment else '')+'"'
    body=re.sub(r'href="([^"]*)"',link,body)
    articles.append(f'<article id="{pid}">{body}</article>')
html='''<!doctype html><html lang="en"><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1"><title>APRSERJ64 user manual</title><style>
*{box-sizing:border-box}body{margin:0;color:#172b3a;background:#fff;font:16px/1.6 system-ui,sans-serif}aside{position:fixed;inset:0 auto 0 0;width:280px;overflow:auto;padding:22px;background:#edf3f7;border-right:1px solid #ccd7df}aside a{display:block;margin:8px 0;line-height:1.35}input{width:100%;padding:10px;font:inherit}button{margin:8px 0;padding:5px 10px}main{margin-left:280px;padding:28px 5vw;max-width:1500px}article{padding-bottom:40px;margin-bottom:36px;border-bottom:2px solid #dde5eb;scroll-margin-top:20px}a{color:#075e94}h1,h2,h3{line-height:1.2}h1{font-size:2rem}h2{margin-top:2rem}table{border-collapse:collapse;width:100%;display:block;overflow:auto}td,th{border:1px solid #cbd5df;padding:9px;text-align:left;vertical-align:top}th{background:#edf3f7}code{overflow-wrap:anywhere;font-size:.9em}pre{overflow:auto;background:#f1f4f7;padding:16px}p,li{max-width:90ch}[hidden]{display:none!important}.hint{font-size:.85rem;color:#415969}@media(max-width:800px){aside{position:relative;width:auto;max-height:40vh}main{margin:0;padding:20px}}@media print{aside{display:none}main{margin:0;padding:0;max-width:none}article[hidden]{display:block!important}article{break-before:page}pre,table{overflow:visible}a{color:inherit}}
</style><aside><strong>APRSERJ64 manual · '''+escape((ROOT/'version.txt').read_text().strip())+'''</strong><p class="hint">Search filters chapters. Use Ctrl+F for individual matches. Clear search to read everything.</p><label for="search">Search manual</label><input id="search" type="search" placeholder="KISS, passcode, map…"><button id="clear">Clear</button><p id="count" role="status"></p><nav>'''+''.join(nav)+'''</nav></aside><main>'''+''.join(articles)+'''</main><script>
const input=document.querySelector('#search'),articles=[...document.querySelectorAll('article')],links=[...document.querySelectorAll('nav a')];
const text=articles.map(a=>a.textContent.toLowerCase());
function filter(){const terms=input.value.toLowerCase().trim().split(/\\s+/).filter(Boolean);let count=0;articles.forEach((a,i)=>{const visible=terms.every(t=>text[i].includes(t));a.hidden=!visible;links[i].hidden=!visible;if(visible)count++});document.querySelector('#count').textContent=count+' of '+articles.length+' chapters';}
input.addEventListener('input',filter);document.querySelector('#clear').onclick=()=>{input.value='';filter();input.focus()};
document.addEventListener('click',e=>{const a=e.target.closest('a[href^="#"]');if(!a)return;const el=document.getElementById(decodeURIComponent(a.hash.slice(1)));if(el&&el.closest('article')?.hidden){input.value='';filter()}});filter();
</script></html>'''
out=ROOT/'build/APRSERJ64-manual.html';out.write_text(html,encoding='utf-8')
check=Inspect();check.feed(html)
assert len(check.ids)==len(re.findall(r' id="',html)), 'Duplicate offline IDs'
for link in check.links:
    if link.startswith('#'):assert unquote(link[1:]) in check.ids, f'Broken offline link: {link}'
index=json.loads((ROOT/'build/manual-site/search/search_index.json').read_text(encoding='utf-8'))
assert len(index['docs'])>=len(pages),'Incomplete web search index'
print(f'Validated {len(pages)} chapters, examples, local links, anchors and offline links; {len(index["docs"])} search entries. Offline manual: {out}')
