import unittest,ast,struct,tempfile,json
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
class AndroidConnectorTests(unittest.TestCase):
 def test_build_requires_android_pie_linker_not_linux_static_binary(self):
  text=(ROOT/'tools/fetch_cloudflared.py').read_text();tree=ast.parse(text);fn=next((x for x in tree.body if isinstance(x,ast.FunctionDef)and x.name=='android_elf'),None)
  self.assertIsNotNone(fn,'connector build must enforce Android/Bionic ELF before packaging')
  ctx={'struct':struct};exec(compile(ast.Module(body=[fn],type_ignores=[]),'real-elf-validator','exec'),ctx)
  with tempfile.TemporaryDirectory()as temp:
   p=Path(temp)/'fixture';data=bytearray(180);data[:6]=bytes([127,69,76,70,2,1]);struct.pack_into('<HH',data,16,2,183);p.write_bytes(data)
   with self.assertRaises(RuntimeError):ctx['android_elf'](p)
   struct.pack_into('<HH',data,16,3,183);struct.pack_into('<Q',data,32,64);struct.pack_into('<HH',data,54,56,1);struct.pack_into('<I',data,64,3);struct.pack_into('<Q',data,72,120)
   interp=b'/system/bin/linker64'+bytes([0]);struct.pack_into('<Q',data,96,len(interp));data[120:120+len(interp)]=interp;p.write_bytes(data);ctx['android_elf'](p)
   data[120:120+len(interp)]=b'/lib/ld-linux-x64.so'+bytes([0]);p.write_bytes(data)
   with self.assertRaises(RuntimeError):ctx['android_elf'](p)
 def test_pins_immutable_source_toolchains_and_no_linux_fallback(self):
  p=ROOT/'tools/cloudflared-android.lock.json';self.assertTrue(p.exists(),'Android build pins required')
  pin=json.loads(p.read_text());self.assertEqual(len(pin['source_commit']),40);self.assertEqual(pin['ndk'],'28.2.13676358');self.assertEqual(pin['android_api'],26)
  script=(ROOT/'tools/fetch_cloudflared.py').read_text();self.assertNotIn('cloudflared-linux-arm64',script)
  self.assertIn("'GOOS':'android'",script);self.assertIn("'CGO_ENABLED':'1'",script);self.assertIn("'-mod=readonly'",script)
