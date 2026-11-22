"""Generate the manual's configuration tables from checked-in application inputs."""
from pathlib import Path
import json
import re
import sys
import tomllib

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / 'src/main/kotlin/org/aprserj64'
PATTERN = re.compile(r'BeaconField\("((?:\\.|[^"\\])*)",\s*"((?:\\.|[^"\\])*)",\s*"((?:\\.|[^"\\])*)"')
def fields(text):
    return [(json.loads('"'+a+'"'), json.loads('"'+b+'"'), json.loads('"'+c+'"')) for a,b,c in PATTERN.findall(text)]
def cell(value):
    return str(value).replace('|','&#124;').replace('\n','<br>')
def literal(value):
    if isinstance(value, str): return json.dumps(value,ensure_ascii=False)
    return json.dumps(value,ensure_ascii=False,separators=(', ', ': '))
labels={}
for file in SRC.glob('*Preferences.kt'):
    for key,label,default in fields(file.read_text(encoding='utf-8-sig')): labels[key]=label
config=tomllib.loads((ROOT/'settings.example.toml').read_text(encoding='utf-8-sig'))
lines=['# Settings catalog','','Generated from `settings.example.toml` and the application field schema. These are **new-install example defaults**, not a promise that an existing installation has these values. See [editing TOML](files.md) before changing a file. Exact keys are searchable.','','Tables containing records (server presets, ports and objects) are explained separately. Service addresses are configurable; a default address does not guarantee service availability. Routing and setup endpoints are omitted from this catalog; use their configured values in your local `settings.toml`.','']
def walk(table,prefix=''):
    scalar={k:v for k,v in table.items() if not isinstance(v,dict) and not (isinstance(v,list) and v and isinstance(v[0],dict))}
    if scalar:
        lines.extend(['## '+(prefix or 'Root'),'', '| Key | Example default | Meaning / UI label |','| --- | --- | --- |'])
        for key,value in scalar.items():
            full=prefix+'.'+key if prefix else key
            example = 'See local `settings.toml`' if full in {'services.routing_url', 'services.setup_url'} else f'`{cell(literal(value))}`'
            lines.append(f'| `{full}` | {example} | {cell(labels.get(full,key.replace("_"," ")))} |')
        lines.append('')
    for key,value in table.items():
        full=prefix+'.'+key if prefix else key
        if isinstance(value,dict): walk(value,full)
        elif isinstance(value,list) and value and isinstance(value[0],dict):
            if full=='port_presets': continue
            lines.extend(['## '+full+' records','',f'Each `[[{full}]]` entry is a separate record. The example contains {len(value)} entries.',''])
            for entry in value:
                lines.append('- '+ '; '.join(f'`{k} = {cell(literal(v))}`' for k,v in entry.items()))
            lines.append('')
walk(config)
outputs={'settings.md':'\n'.join(lines)}
porttext=(SRC/'PortPreferences.kt').read_text(encoding='utf-8-sig').split('val fields=listOf(',1)[1].split('val global=',1)[0]
objtext=(SRC/'BeaconPreferences.kt').read_text(encoding='utf-8-sig').split('val entityFields=listOf(',1)[1].split('fun load(',1)[0]+(SRC/'ObjectGeometry.kt').read_text(encoding='utf-8-sig')
for name,title,table,text,intro in [('ports.md','Port field reference','ports',porttext,'New-port schema defaults are shown below. Device presets can override them, particularly commands, enable state and paths. Saved unconfigured templates are disabled. See [port setup](../connect/ports.md) and [routing](../connect/routing.md).'),('objects.md','Object field reference','objects',objtext,'Fields apply to objects, items and companion stations. The default coordinates are examples: replace them before transmitting. See [object procedures](../operate/objects.md).')]:
    rows=[f'# {title}','',intro,'',f'Each `[[{table}]]` table defines one entry. The default column shows schema values; strings are shown without TOML quotation marks. An empty cell means an empty string. Arrays use TOML array syntax.','','| Key | Schema default | Meaning / UI label |','| --- | --- | --- |']
    for key,label,default in fields(text): rows.append(f'| `{key}` | {"`"+cell(default)+"`" if default else "(empty)"} | {cell(label)} |')
    rows += ['', 'For types and quoting see [configuration files](files.md). Avoid duplicate table/key declarations.','']
    outputs[name]='\n'.join(rows)
failed=[]
for name,body in outputs.items():
    file=ROOT/'docs/reference'/name
    if '--check' in sys.argv:
        if not file.exists() or file.read_text(encoding='utf-8')!=body: failed.append(name)
    else: file.write_text(body,encoding='utf-8')
if failed: raise SystemExit('Stale manual references: '+', '.join(failed))
print('Configuration references verified' if '--check' in sys.argv else 'Configuration references generated')
