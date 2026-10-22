import unittest,subprocess,tempfile
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1];JAVA=ROOT/'app/src/main/java/org/opencharge/teslable'
class CloudflaredCommandTests(unittest.TestCase):
 def test_fixed_argv_does_not_contain_credentials_or_shell(self):
  self.assertTrue((JAVA/'CloudflaredCommand.java').exists(),'missing safe cloudflared argv builder')
  harness='package org.opencharge.teslable;\nimport java.io.File;import java.util.List;\npublic class CommandHarness {public static void main(String[] args){\n List<String>a=CloudflaredCommand.args(new File("/native/libcloudflared.so"),new File("/private/connector.token"));\n if(!a.get(0).equals("/native/libcloudflared.so")||!a.contains("--token-file")||a.contains("--token")||a.contains("sh")||a.contains("-c")||!a.contains("127.0.0.1:27841")||!a.contains("http2"))throw new AssertionError();\n System.out.println("PASS fixed argv, token-file only, loopback metrics, no shell");\n}}'
  with tempfile.TemporaryDirectory()as tmp:
   p=Path(tmp);(p/'CommandHarness.java').write_text(harness)
   z=subprocess.run(['javac','-d',tmp,str(JAVA/'CloudflaredCommand.java'),str(p/'CommandHarness.java')],capture_output=True,text=True);self.assertEqual(z.returncode,0,z.stderr)
   z=subprocess.run(['java','-cp',tmp,'org.opencharge.teslable.CommandHarness'],capture_output=True,text=True);self.assertEqual(z.returncode,0,z.stderr);self.assertIn('PASS fixed argv',z.stdout)
 def test_separate_secret_pair_roundtrip_and_validation(self):
  self.assertTrue((JAVA/'PublicWakeSecrets.java').exists(),'missing validated secret-pair codec')
  h="""package org.opencharge.teslable;
import javax.crypto.KeyGenerator;import javax.crypto.SecretKey;
public class SecretsHarness {public static void main(String[] args)throws Exception{
 String bearer="fixture-bearer-012345678901234567890123456",tunnel="fixture-tunnel-"+"x".repeat(150);
 KeyGenerator gen=KeyGenerator.getInstance("AES");gen.init(256);SecretKey key=gen.generateKey();
 String envelope=ApiTokenCipher.encrypt(key,PublicWakeSecrets.encode(bearer,tunnel));
 if(envelope.contains(bearer)||envelope.contains(tunnel))throw new AssertionError();
 String[] out=PublicWakeSecrets.decode(ApiTokenCipher.decrypt(key,envelope));
 if(!bearer.equals(out[0])||!tunnel.equals(out[1]))throw new AssertionError();
 try{PublicWakeSecrets.encode("short",tunnel);throw new AssertionError();}catch(IllegalArgumentException expected){}
 try{PublicWakeSecrets.encode(bearer,tunnel+"\\n");throw new AssertionError();}catch(IllegalArgumentException expected){}
 try{ApiTokenCipher.decrypt(gen.generateKey(),envelope);throw new AssertionError();}catch(java.security.GeneralSecurityException expected){}
 System.out.println("PASS encrypted pair, validation, wrong-key rejection");
}}"""
  with tempfile.TemporaryDirectory()as tmp:
   p=Path(tmp);(p/'SecretsHarness.java').write_text(h)
   z=subprocess.run(['javac','-d',tmp,*[str(JAVA/x)for x in ['PublicWakeSecrets.java','ApiTokenCipher.java']],str(p/'SecretsHarness.java')],capture_output=True,text=True);self.assertEqual(z.returncode,0,z.stderr)
   z=subprocess.run(['java','-cp',tmp,'org.opencharge.teslable.SecretsHarness'],capture_output=True,text=True);self.assertEqual(z.returncode,0,z.stderr);self.assertIn('PASS encrypted pair',z.stdout)
 def test_supervisor_rejects_unverified_binary_and_stops(self):
  self.assertTrue((JAVA/'CloudflaredSupervisor.java').exists(),'missing supervised child lifecycle')
  h="""package org.opencharge.teslable;
import java.io.File;
public class SupervisorHarness {public static void main(String[] args)throws Exception{
 CloudflaredSupervisor s=new CloudflaredSupervisor(new File("/usr/bin/true"),new File(args[0]),()->"fixture-not-a-tunnel-token","0".repeat(64));
 s.start();long end=System.nanoTime()+5000000000L;
 while(!s.state().equals("connector_binary_unavailable")&&System.nanoTime()<end)Thread.sleep(10);
 if(!s.state().equals("connector_binary_unavailable"))throw new AssertionError(s.state());
 s.close();end=System.nanoTime()+5000000000L;
 while(!s.state().equals("stopped")&&System.nanoTime()<end)Thread.sleep(10);
 if(!s.state().equals("stopped"))throw new AssertionError(s.state());
 File[] files=new File(args[0]).listFiles();if(files!=null&&files.length!=0)throw new AssertionError();
 System.out.println("PASS rejects wrong digest, no token/child created, worker stops");
}}"""
  with tempfile.TemporaryDirectory()as tmp:
   p=Path(tmp);(p/'SupervisorHarness.java').write_text(h)
   z=subprocess.run(['javac','-d',tmp,*[str(JAVA/x)for x in ['CloudflaredSupervisor.java','CloudflaredCommand.java']],str(p/'SupervisorHarness.java')],capture_output=True,text=True);self.assertEqual(z.returncode,0,z.stderr)
   z=subprocess.run(['java','-cp',tmp,'org.opencharge.teslable.SupervisorHarness',str(p/'private')],capture_output=True,text=True,timeout=15);self.assertEqual(z.returncode,0,z.stderr);self.assertIn('PASS rejects wrong digest',z.stdout)
