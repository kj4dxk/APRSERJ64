"""Replay a local APRS capture over loopback only; no upstream network or RF output."""
import socket,threading,time,json,argparse
from pathlib import Path
p=argparse.ArgumentParser();p.add_argument('--input',required=True);p.add_argument('--port',type=int,default=18765);a=p.parse_args();folder=Path(a.input).parent
lines=Path(a.input).read_bytes().splitlines();stats={'connections':0,'sent':0,'client_packets':0,'cycles':0,'started':time.time()};lock=threading.Lock()
def publish():
 (folder/'relay-stats.json').write_text(json.dumps(stats))
def client(conn):
 with conn:
  f=conn.makefile('rb');conn.sendall(b'# APRSERJ64 bounded loopback replay\r\n');login=f.readline();call=login.split()[1].decode();conn.sendall(f'# logresp {call} unverified, server LOCAL-REPLAY\r\n'.encode())
  with lock:stats['connections']+=1
  def reader():
   try:
    for line in f:
     if not line.startswith(b'#'):
      with lock:stats['client_packets']+=1
   except OSError:pass
  threading.Thread(target=reader,daemon=True).start()
  i=0
  try:
   while True:
    control=json.loads((folder/'replay-control.json').read_text());rate=control.get('rate',0)
    if control.get('stop'):break
    if rate<=0:time.sleep(.1);continue
    start=time.monotonic();batch=max(1,int(rate/10))
    for _ in range(batch):
     conn.sendall(lines[i%len(lines)]+b'\r\n');i+=1
    with lock:stats['sent']+=batch;stats['cycles']=i//len(lines);publish()
    time.sleep(max(0,.1-(time.monotonic()-start)))
  except (OSError,ConnectionError):pass
with socket.socket() as server:
 server.bind(('127.0.0.1',a.port));server.listen();print(f'Loopback replay ready: {len(lines)} packets, port {a.port}',flush=True)
 while True:
  conn,_=server.accept();threading.Thread(target=client,args=(conn,),daemon=True).start()
