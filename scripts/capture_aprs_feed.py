"""Capture a bounded receive-only APRS-IS full-feed sample; never transmits packets."""
import argparse,json,socket,time
from pathlib import Path
from datetime import datetime,timezone
p=argparse.ArgumentParser();p.add_argument('--host',default='rotate.aprs.net');p.add_argument('--port',type=int,default=10152);p.add_argument('--call',required=True);p.add_argument('--count',type=int,default=20000);p.add_argument('--seconds',type=int,default=180);p.add_argument('--output',required=True);a=p.parse_args()
assert a.call.replace('-','').isalnum() and a.count>0 and 0<a.seconds<=600
out=Path(a.output);out.parent.mkdir(parents=True,exist_ok=True)
started=datetime.now(timezone.utc).isoformat();n=0;comments=[];t=time.monotonic()
with socket.create_connection((a.host,a.port),timeout=20) as sock:
 sock.settimeout(15);remote=sock.getpeername();reader=sock.makefile('rb')
 banner=reader.readline(2048).decode('latin1').strip();comments.append(banner)
 sock.sendall(f'user {a.call} pass -1 vers APRSERJ64-QA 0.8\r\n'.encode('ascii'))
 with out.open('wb') as f:
  while n<a.count and time.monotonic()-t<a.seconds:
   try:line=reader.readline(4096)
   except (socket.timeout,TimeoutError):break
   if not line:break
   if line.startswith(b'#'):
    comments.append(line.decode('latin1').strip());continue
   f.write(line.rstrip(b'\r\n')+b'\n');n+=1
   if n%5000==0:print(f'Captured {n} packets in {time.monotonic()-t:.1f}s',flush=True)
elapsed=time.monotonic()-t
report=dict(started_utc=started,host=a.host,port=a.port,remote=remote,packets=n,seconds=elapsed,packets_per_second=n/elapsed,bytes=out.stat().st_size,server_comments=comments,transmitted_aprs_packets=0)
out.with_suffix('.json').write_text(json.dumps(report,indent=2));print(json.dumps(report,indent=2))
