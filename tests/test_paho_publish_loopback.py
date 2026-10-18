import unittest,subprocess,tempfile,socketserver,threading,socket,time,os
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
PAHO_SHA256="59914287adac506a28d5e8172eed262a22605f3df4d426b9d92f41dae2448185"
def readn(sock,n):
 out=b''
 while len(out)<n:
  part=sock.recv(n-len(out))
  if not part:raise EOFError()
  out+=part
 return out
def packet(sock):
 head=readn(sock,1)[0];size=0;mult=1
 for _ in range(4):
  byte=readn(sock,1)[0];size+=(byte&127)*mult
  if byte<128:return head,readn(sock,size)
  mult*=128
 raise ValueError('invalid fixture packet')
def publish(topic,payload,retained=False):
 topic=topic.encode();payload=payload.encode();body=len(topic).to_bytes(2,'big')+topic+payload
 assert len(body)<128
 return bytes([0x31 if retained else 0x30,len(body)])+body
class PahoPublishLoopbackTests(unittest.TestCase):
 def test_actual_paho_burst_delivery_and_retained_metadata(self):
  records=[]
  class Broker(socketserver.BaseRequestHandler):
   def handle(self):
    try:
     self.request.settimeout(15)
     while True:
      head,body=packet(self.request);kind=head>>4
      if kind==1:self.request.sendall(b'\x20\x02\x00\x00')
      elif kind==8:
       self.request.sendall(b'\x90\x03'+body[:2]+b'\x01')
       self.request.sendall(publish('fixture/retained','synthetic-retained-fixture',True))
      elif kind==3:
       size=int.from_bytes(body[:2],'big');topic=body[2:2+size].decode();position=2+size;mid=body[position:position+2];payload=body[position+2:].decode()
       records.append((topic,payload));time.sleep(.005)
       self.request.sendall(b'\x40\x02'+mid)
       if len(records)==200:self.request.sendall(publish('fixture/result','200'))
      elif kind==12:self.request.sendall(b'\xd0\x00')
      elif kind==14:return
    except (EOFError,OSError):return
  class Server(socketserver.ThreadingTCPServer):allow_reuse_address=True;daemon_threads=True
  with Server(('127.0.0.1',0),Broker) as server,tempfile.TemporaryDirectory() as out:
   thread=threading.Thread(target=server.serve_forever,daemon=True);thread.start()
   try:
    src=ROOT/'app/src/main/java/org/opencharge/teslable';names=['MqttBridgeConfig','MqttDiscoveryCatalog','MqttBridgePublisher','PahoMqttTransport']
    if (src/'MqttPublishQueue.java').exists():names.append('MqttPublishQueue')
    jar=Path.home()/'.cache/tesla-android-build/org.eclipse.paho.client.mqttv3-1.2.5.jar'
    import hashlib,urllib.request
    if not jar.is_file():
     jar=Path(out)/'paho-1.2.5.jar'
     jar.write_bytes(urllib.request.urlopen('https://repo.maven.apache.org/maven2/org/eclipse/paho/org.eclipse.paho.client.mqttv3/1.2.5/org.eclipse.paho.client.mqttv3-1.2.5.jar',timeout=30).read())
    self.assertEqual(hashlib.sha256(jar.read_bytes()).hexdigest(),PAHO_SHA256,'wrong public Paho artifact')
    cp=str(jar)+os.pathsep+out
    subprocess.run(['javac','-cp',str(jar),'-d',out,*[str(src/(n+'.java')) for n in names],str(ROOT/'tests/java/PahoPublishLoopbackHarness.java')],check=True,capture_output=True,text=True)
    r=subprocess.run(['java','-cp',cp,'org.opencharge.teslable.PahoPublishLoopbackHarness',str(server.server_address[1])],capture_output=True,text=True,timeout=25)
    self.assertEqual(r.returncode,0,r.stdout+r.stderr+' independent_loopback_received='+str(len(records)))
    self.assertEqual([(t,p) for t,p in records],[('fixture/state/'+str(i),str(i)) for i in range(200)])
   finally:server.shutdown()
