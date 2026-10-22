import unittest,subprocess,tempfile,socket,select,os,time
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
JAVA=ROOT/'app/src/main/java/org/opencharge/teslable'
TOKEN='fixture-not-a-live-credential-01234567890123456789'
HARNESS='package org.opencharge.teslable;\npublic class WakeHarness {\n public static void main(String[] args)throws Exception {\n  PublicWakeHttpServer s=new PublicWakeHttpServer(()->args.length==0?"fixture-not-a-live-credential-01234567890123456789":null,\n   ()->{System.out.println("WAKE_REQUEST");System.out.flush();return 202;},0);\n  s.start();System.out.println(s.getPort());System.out.flush();System.in.read();s.close();\n }\n}'
class PublicWakeHttpTests(unittest.TestCase):
 def setUp(self):
  self.assertTrue((JAVA/'PublicWakeHttpServer.java').is_file(),'missing wake-only HTTP implementation')
  self.tmp=tempfile.TemporaryDirectory();self.addCleanup(self.tmp.cleanup)
  p=Path(self.tmp.name);(p/'WakeHarness.java').write_text(HARNESS)
  out=subprocess.run(['javac','-d',str(p),str(JAVA/'PublicWakeHttpServer.java'),str(p/'WakeHarness.java')],capture_output=True,text=True)
  self.assertEqual(out.returncode,0,out.stderr)
  self.server=subprocess.Popen(['java','-cp',str(p),'org.opencharge.teslable.WakeHarness'],stdin=subprocess.PIPE,stdout=subprocess.PIPE,stderr=subprocess.PIPE,text=True)
  self.addCleanup(self.close);self.port=int(self.server.stdout.readline())
 def close(self):
  if self.server.poll() is None:self.server.communicate('x',timeout=8)
 def request(self,raw):
  with socket.create_connection(('127.0.0.1',self.port),timeout=4)as s:
   s.settimeout(7);s.sendall(raw.encode('ascii'));s.shutdown(socket.SHUT_WR);out=b''
   while True:
    x=s.recv(8192)
    if not x:break
    out+=x
  return out.decode('ascii')
 def post(self,body='',extra='',token=TOKEN):
  return self.request('POST /v1/wake HTTP/1.1\r\nHost: localhost\r\n'+('Authorization: Bearer '+token+'\r\n'if token is not None else '')+'Content-Length: '+str(len(body))+'\r\n'+extra+'\r\n'+body)
 def assert_no_command(self):
  self.assertFalse(select.select([self.server.stdout],[],[],0.1)[0],'unauthorized/invalid request dispatched wake')
 def test_unauthenticated_rejected_without_dispatch(self):
  self.assertIn(' 401 ',self.post(token=None));self.assert_no_command()
 def test_authenticated_empty_json_accepts_fixed_wake(self):
  response=self.post('{}');self.assertIn(' 202 ',response);self.assertIn('"accepted":true',response)
  self.assertEqual(self.server.stdout.readline().strip(),'WAKE_REQUEST')
 def test_repeat_request_is_rate_limited(self):
  self.assertIn(' 202 ',self.post());self.assertEqual(self.server.stdout.readline().strip(),'WAKE_REQUEST')
  self.assertIn(' 429 ',self.post());self.assert_no_command()
 def test_wrong_token_rejected(self):
  self.assertIn(' 401 ',self.post(token='wrong-token'));self.assert_no_command()
 def test_arbitrary_control_body_rejected(self):
  self.assertIn(' 400 ',self.post('{"command":"unlock"}'));self.assert_no_command()
 def test_other_routes_never_expose_telemetry_or_controls(self):
  for route in ['/','/v1/status','/v1/telemetry','/v1/unlock','/v1/wake?cmd=unlock']:
   self.assertIn(' 404 ',self.request('GET '+route+' HTTP/1.1\r\nHost: localhost\r\n\r\n'))
  self.assert_no_command()
 def test_duplicate_authorization_rejected(self):
  self.assertIn(' 400 ',self.post(extra='Authorization: Bearer '+TOKEN+'\r\n'));self.assert_no_command()
 def test_oversized_body_rejected(self):
  self.assertIn(' 400 ',self.post('x'*65));self.assert_no_command()
 def test_transfer_encoding_rejected(self):
  self.assertIn(' 400 ',self.post(extra='Transfer-Encoding: chunked\r\n'));self.assert_no_command()
 def test_get_wake_never_dispatches(self):
  response=self.request('GET /v1/wake HTTP/1.1\r\nHost: localhost\r\nAuthorization: Bearer '+TOKEN+'\r\n\r\n')
  self.assertIn(' 405 ',response);self.assert_no_command()
 def test_slow_header_has_absolute_deadline(self):
  with socket.create_connection(('127.0.0.1',self.port),timeout=3)as client:
   client.settimeout(3);client.sendall(b'POST /v1/wake HTTP/1.1\r\nX-Slow: ')
   readable=False
   for _ in range(24):
    time.sleep(0.25)
    if select.select([client],[],[],0)[0]:readable=True;break
    try:client.sendall(b'x')
    except BrokenPipeError:readable=True;break
   self.assertTrue(readable,'slow header kept request alive beyond absolute 5-second budget')
   self.assertIn(b' 408 ',client.recv(4096))
  self.assert_no_command()
