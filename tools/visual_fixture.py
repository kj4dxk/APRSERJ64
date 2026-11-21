# SPDX-License-Identifier: AGPL-3.0-only
# Usage: python tools/visual_fixture.py <isolated-test-folder>
# Copy the portable EXE into that folder and launch it after ready.json appears.
# All APRS and simulated RF endpoints bind to loopback; no public APRS traffic.
import socketserver, http.server, threading, time, json, pathlib, re, sys, shutil, datetime
from visual_boundaries import create
root=pathlib.Path(sys.argv[1]).resolve()
root.mkdir(parents=True,exist_ok=True)
create(root)
if not (root/'settings.toml').exists():
 shutil.copyfile(pathlib.Path(__file__).resolve().parents[1]/'settings.example.toml',root/'settings.toml')
def coords(lat,lon):
 def c(n,w):
  a=round(abs(n)*6000);return f'{a//6000:0{w}d}{a%6000/100:05.2f}'
 return c(lat,2)+('N' if lat>=0 else 'S')+'/'+c(lon,3)+('E' if lon>=0 else 'W')
def pos(call,lat,lon,symbol='>',comment='',path=''):
 return f'{call}>APRS{path}:!{coords(lat,lon)}{symbol}{comment}'
packets=[pos('DIGI1',28.552,-81.225,'#','Digipeater'),pos('IGATE1',28.56,-81.21,'&','IGate'),
 pos('N1CALL-7',28.54,-81.24,'>','090/060/A=006562 Mobile fixture',',DIGI1*,qAR,IGATE1'),
 pos('N1CALL-8',28.545,-81.26,'#','PHG5132 Coverage fixture'),
 pos('DF1',28.52,-81.255,'\\','000/000/045/949'),pos('DF2',28.55,-81.27,'\\','000/000/135/947'),
 pos('DFS1',28.525,-81.22,'\\','DFS2360'),pos('QATEST',28.537,-81.243,'-','QRU requester'),
 'N1CALL-8>APRS:_11202000c180s010g020t075r001p010h65b10132',
 'N0CALL>APRS:;TESTAREA *202000z2831.80N\\08115.80Wl410/410Area fixture']
packets += [
 'N5CALL>RXSR4P:`m*Vn v>/Mic-E fixture',
 'AMBIG>APRS:!2832.  N/08114.  W>Approximate position',
 'REPEAT>APWW01:=2832.50N/08114.30W#146.940MHz T123 -060 R25 Local repeater',
 'MOBILE>APDR01:=2832.60N/08114.40W>090/030 Messaging station',
 'INVALID>APRS:!bad-position',
 'MLBFFW>APRS::NWS_WARN :210445z,FLASH_FLOOD,FLZ041-044{12345']
packets.append(packets[-3])
expiration=(datetime.datetime.now(datetime.timezone.utc)+datetime.timedelta(hours=2)).strftime('%d%H%Mz')
packets=[re.sub(r'\d{6}z,FLASH_FLOOD',expiration+',FLASH_FLOOD',packet) for packet in packets]
lock=threading.Lock()
def log(line):
 with lock:
  with (root/'fixture.log').open('a') as f:f.write(line+'\n')
class Aprs(socketserver.StreamRequestHandler):
 def handle(self):
  self.wfile.write(b'# local visual QA server\r\n');self.wfile.flush();self.rfile.readline()
  sendlock=threading.Lock();alive=threading.Event();alive.set()
  def send(line):
   with sendlock:self.wfile.write((line+'\r\n').encode());self.wfile.flush()
  send('# logresp N0CALL-12 verified, local visual QA')
  def feed():
   control='';tick=0
   while alive.is_set():
    try:
     if tick%20==0:
      for p in packets:send(p)
     send('# visual QA heartbeat')
     new=(root/'control.txt').read_text() if (root/'control.txt').exists() else ''
     if new!=control:
      control=new
      if new.startswith('qru'):send('QATEST>APRS::N0CALL-12:HOSP 50{VIS1')
     time.sleep(1);tick+=1
    except (OSError,ValueError):break
  threading.Thread(target=feed,daemon=True).start()
  try:
   for raw in self.rfile:
    line=raw.decode().strip();log('IS '+line)
    m=re.search(r'::(.{9}):(.+)',line)
    if m:
     peer=m[1].strip();body=m[2]
     if '{' in body:send(f'{peer}>APRS::N0CALL-12:ack'+body.split('{')[-1])
     if peer=='QRU' and body.startswith('INFO'):send('QRU>APRS::N0CALL-12:HOSP(1) Max 5@50km')
     elif peer=='QRU' and body.startswith('HOSP'):send('QRU>APRS::N0CALL-12:)HOSPITAL!2832.10N/08114.60WhLocal hospital')
  finally:alive.clear()
class RF(socketserver.StreamRequestHandler):
 def handle(self):
  def feed():
   while True:
    try:
     self.wfile.write((pos('DX1',28.8,-81.24,'-','Direct RF fixture')+'\r\n'+pos('DX2',28.95,-81.25,'-','Direct RF fixture')+'\r\n').encode());self.wfile.flush();time.sleep(10)
    except (OSError,ValueError):break
  threading.Thread(target=feed,daemon=True).start()
  for raw in self.rfile:log('RF '+raw.decode().strip())
class Weather(http.server.BaseHTTPRequestHandler):
 def do_GET(self):
  data={'type':'FeatureCollection','features':[{'type':'Feature','properties':{'id':'VISUAL-WARN','event':'Test warning','severity':'Severe','headline':'Local visual test - no actual warning','description':'Check boundary rendering and readable alert details.','expires':'2100-01-01T00:00:00Z'},'geometry':{'type':'Polygon','coordinates':[[[-81.26,28.52],[-81.24,28.52],[-81.24,28.53],[-81.26,28.53],[-81.26,28.52]]]}}]}
  data=json.dumps(data).encode();self.send_response(200);self.send_header('Content-Type','application/geo+json');self.send_header('Content-Length',str(len(data)));self.end_headers();self.wfile.write(data)
 def log_message(self,*args):pass
servers=[socketserver.ThreadingTCPServer(('127.0.0.1',0),Aprs),socketserver.ThreadingTCPServer(('127.0.0.1',0),RF),http.server.ThreadingHTTPServer(('127.0.0.1',0),Weather)]
for server in servers:server.daemon_threads=True;threading.Thread(target=server.serve_forever,daemon=True).start()
path=root/'settings.toml';text=path.read_text(encoding='utf-8-sig')
def setting(section,key,value):
 global text
 pattern=r'(?ms)(^\['+re.escape(section)+r'\]\s*\n)(.*?)(?=^\[|\Z)'
 def change(m):
  body=m[2];line=key+' = '+value
  body=re.sub(r'(?m)^'+re.escape(key)+r'\s*=.*$',lambda _:line,body) if re.search(r'(?m)^'+re.escape(key)+r'\s*=',body) else body+line+'\n'
  return m[1]+body
 text=re.sub(pattern,change,text)
for section,key,value in [('aprs','callsign','"N0CALL-12"'),('aprs','passcode','"12345"'),('aprs','server','"127.0.0.1"'),('aprs','port',str(servers[0].server_address[1])),('aprs','connect_on_startup','true'),('tactical','labels','"all"'),('tactical','qru_enabled','true'),('weather','enabled','true'),('weather','alerts_url',json.dumps(f'http://127.0.0.1:{servers[2].server_address[1]}/alerts'))]:setting(section,key,value)
# Fixtures live only in this isolated test folder.
for section,key,value in [('legacyweather','enabled','true'),('legacyweather','shapefiles','["zones.shp"]'),('logging','csv','true'),('logging','gpx','true'),('telemetry','enabled','true'),('bulletins','enabled','true'),('bulletins','entries','["1|QA|600|is|Scheduled local QA bulletin"]')]:setting(section,key,value)
text=re.sub(r'(?ms)^\[\[objects\]\]\n(?:(?!^\[).)*?name\s*=\s*"HOSP"(?:(?!^\[).)*','',text)
text=re.sub(r'(?ms)^\[\[ports\]\].*?(?=^\[|\Z)','',text)
text+='\n[[ports]]\nname="Simulated VHF"\nprotocol="TEXT"\ntransport="tcp"\nenabled=true\nendpoint="127.0.0.1:'+str(servers[1].server_address[1])+'"\nrf_to_is=false\nis_to_rf=false\nbeacon=false\n'
text+='\n[[objects]]\nname="HOSP"\nlatitude=28.535\nlongitude=-81.243\nqru_group="HOSP"\nquery_only=true\nsymbol="h"\ncomment="QA hospital"\n'
path.write_text(text,encoding="utf-8")
(root/'ready.json').write_text(json.dumps({'aprs':servers[0].server_address[1],'rf':servers[1].server_address[1],'weather':servers[2].server_address[1]}))
threading.Event().wait()
