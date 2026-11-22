"""Compare captured APRS positions against aprslib (independent implementation)."""
import argparse,collections,json
from pathlib import Path
import aprslib
p=argparse.ArgumentParser();p.add_argument('folder');a=p.parse_args();folder=Path(a.folder)
lines=(folder/'packets.txt').read_bytes().splitlines();ours=[json.loads(s) for s in (folder/'decoded.jsonl').read_text().splitlines()];stats=collections.Counter();bad=[]
for i,(raw,o) in enumerate(zip(lines,ours)):
 try:r=aprslib.parse(raw.decode('latin1'))
 except Exception:stats['reference_rejected']+=1;continue
 stats['reference_parsed']+=1
 if 'latitude' not in r or 'longitude' not in r:continue
 stats['reference_positions']+=1
 if 'lat' not in o:stats['missing_our_position']+=1;bad.append({'index':i,'reason':'missing','format':r.get('format')});continue
 stats['compared_positions']+=1
 if abs(o['lat']-r['latitude'])>0.0002 or abs(o['lon']-r['longitude'])>0.0002:
  stats['coordinate_mismatch']+=1;bad.append({'index':i,'reason':'coordinates','ours':o,'reference':r})
 else:stats['coordinates_match']+=1
 if o['symbol']!=r.get('symbol',o['symbol']) or o['table']!=r.get('symbol_table',o['table']):stats['symbol_mismatch']+=1
(folder/'comparison.json').write_text(json.dumps(dict(stats),indent=2));(folder/'mismatch-indices.json').write_text(json.dumps(bad,indent=2,default=str));print(json.dumps(dict(stats),indent=2))
