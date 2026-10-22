#!/usr/bin/env python3
"""Build pinned cloudflared for Android/Bionic, never silently use Linux static ELF.

Go distributions are digest-pinned, source is commit-pinned, modules use go.sum,
NDK/API are fixed. This stage requires no signing or Cloudflare credentials.
"""
from pathlib import Path
import hashlib,json,os,platform,shutil,struct,subprocess,tarfile,urllib.request
ROOT=Path(__file__).resolve().parents[1]
PIN=json.loads((ROOT/'tools/cloudflared-android.lock.json').read_text())
CACHE=Path(os.environ.get('CLOUDFLARED_BUILD_CACHE',str(Path(os.environ.get('RUNNER_TEMP',str(ROOT)))/'.cloudflared-build'))).resolve()
CACHE.mkdir(parents=True,exist_ok=True)
BINARY=CACHE/'cloudflared-android-arm64'
RECORD=CACHE/'android-connector-provenance.json'

def digest(path):
 h=hashlib.sha256()
 with path.open('rb')as f:
  for chunk in iter(lambda:f.read(1024*1024),b''):h.update(chunk)
 return h.hexdigest()

def android_elf(path):
 data=path.read_bytes()
 if len(data)<64 or data[:6]!=b'\x7fELF\x02\x01':raise RuntimeError('not a little-endian ELF64 connector')
 typ,machine=struct.unpack_from('<HH',data,16)
 if typ!=3 or machine!=183:raise RuntimeError('connector must be Android ARM64 PIE')
 offset=struct.unpack_from('<Q',data,32)[0];size,count=struct.unpack_from('<HH',data,54)
 interp=None
 for i in range(count):
  entry=offset+i*size
  if entry+56>len(data):raise RuntimeError('invalid ELF program headers')
  if struct.unpack_from('<I',data,entry)[0]==3:
   at=struct.unpack_from('<Q',data,entry+8)[0];length=struct.unpack_from('<Q',data,entry+32)[0]
   interp=data[at:at+length].rstrip(b'\0')
 if interp!=b'/system/bin/linker64':raise RuntimeError('connector must use the Android linker, not a Linux executable')

def run(args,**kwargs):
 subprocess.run(args,check=True,timeout=1800,**kwargs)

def valid_record(record):
 return (record.get('source_commit')==PIN['source_commit']and record.get('upstream_tag')==PIN['upstream_tag']and record.get('target')=='aarch64-linux-android'+str(PIN['android_api'])and record.get('ndk')==PIN['ndk']and record.get('goos')=='android'and record.get('cgo')is True and record.get('go_version','').startswith('go version '+PIN['go_version']+' ')and BINARY.is_file()and record.get('sha256')==digest(BINARY))
try:record=json.loads(RECORD.read_text())
except (OSError,ValueError):record={}
if not valid_record(record):
 host=(platform.system().lower(),{'x86_64':'amd64','aarch64':'arm64'}.get(platform.machine(),platform.machine()))
 asset=PIN['go_distributions'].get('/'.join(host))
 if asset is None:raise RuntimeError('unsupported build host; use the pinned Linux CI or Apple Silicon Mac')
 go=CACHE/'go-toolchain/go/bin/go'
 env={k:v for k,v in os.environ.items()if k in ('HOME','PATH','TMPDIR','TEMP','TMP','SYSTEMROOT','JAVA_HOME','ANDROID_HOME','ANDROID_SDK_ROOT')}
 env['GIT_TERMINAL_PROMPT']='0';env['GOTELEMETRY']='off'
 if not go.exists():
  archive=CACHE/asset['filename']
  if not archive.exists()or digest(archive)!=asset['sha256']:
   temp=archive.with_suffix('.download')
   with urllib.request.urlopen(asset['url'],timeout=120)as response,temp.open('wb')as out:shutil.copyfileobj(response,out)
   if digest(temp)!=asset['sha256']:temp.unlink();raise RuntimeError('Go distribution digest mismatch')
   os.replace(temp,archive)
  tools=go.parents[2];tools.mkdir(parents=True,exist_ok=True)
  with tarfile.open(archive)as f:
   for member in f.getmembers():
    dest=(tools/member.name).resolve()
    if not dest.is_relative_to(tools.resolve()):raise RuntimeError('unsafe distribution path')
    if member.issym()and not(dest.parent/member.linkname).resolve().is_relative_to(tools.resolve()):raise RuntimeError('unsafe distribution symlink')
    if member.islnk()and not(tools/member.linkname).resolve().is_relative_to(tools.resolve()):raise RuntimeError('unsafe distribution hardlink')
   f.extractall(tools)
 version=subprocess.check_output([str(go),'version'],text=True,env=env).strip()
 if not version.startswith('go version '+PIN['go_version']+' '):raise RuntimeError('wrong cached Go version')
 source=CACHE/'cloudflared-android-source'
 if not(source/'.git').is_dir():
  run(['git','-c','credential.helper=','clone','--depth','1','--branch',PIN['upstream_tag'],'https://github.com/cloudflare/cloudflared.git',str(source)],env=env)
 commit=subprocess.check_output(['git','rev-parse','HEAD'],cwd=source,text=True,env=env).strip()
 if commit!=PIN['source_commit']:raise RuntimeError('upstream source revision mismatch')
 status=subprocess.check_output(['git','status','--porcelain=v1'],cwd=source,text=True,env=env)
 if status.strip():raise RuntimeError('refusing modified connector source')
 sdk=Path(env.get('ANDROID_HOME')or env.get('ANDROID_SDK_ROOT')or'')
 ndk=sdk/'ndk'/PIN['ndk'];prebuilt={'darwin':'darwin-x86_64','linux':'linux-x86_64'}[host[0]]
 cc=ndk/'toolchains/llvm/prebuilt'/prebuilt/'bin'/('aarch64-linux-android'+str(PIN['android_api'])+'-clang')
 if not cc.is_file():raise RuntimeError('pinned Android NDK compiler unavailable')
 env.update({'GOOS':'android','GOARCH':'arm64','CGO_ENABLED':'1','CC':str(cc),'GOMAXPROCS':'2','GOCACHE':str(CACHE/'go-cache'),'GOMODCACHE':str(CACHE/'go-mod-cache'),'PATH':str(go.parent)+os.pathsep+env.get('PATH','')})
 flags='-s -w -buildid= -X main.Version='+PIN['version']+' -X main.BuildTime='+PIN['build_time']
 run([str(go),'build','-mod=readonly','-buildvcs=false','-trimpath','-p=2','-buildmode=pie','-ldflags',flags,'-o',str(BINARY),'./cmd/cloudflared'],cwd=source,env=env)
 android_elf(BINARY)
 record={'version':PIN['version'],'upstream_tag':PIN['upstream_tag'],'source_commit':commit,'go_version':version,'ndk':PIN['ndk'],'target':'aarch64-linux-android'+str(PIN['android_api']),'goos':'android','cgo':True,'sha256':digest(BINARY),'bytes':BINARY.stat().st_size}
 RECORD.write_text(json.dumps(record,indent=2)+'\n')
android_elf(BINARY)
# Carry the upstream, Go-standard-library and pinned-module license notices in
# the APK. These inputs are public source/toolchain caches, never app data.
notices=[]
inputs=[(CACHE/'cloudflared-android-source'/'LICENSE','cloudflared '+PIN['source_commit']),(CACHE/'go-toolchain/go/LICENSE','Go '+PIN['go_version'])]
modules=CACHE/'go-mod-cache'
if not modules.is_dir():raise RuntimeError('module license cache missing; rebuild the connector before distribution')
for path in sorted(modules.rglob('*')):
 if path.is_file()and path.name.upper().startswith(('LICENSE','LICENCE','COPYING','COPYRIGHT','NOTICE')):
  inputs.append((path,str(path.relative_to(modules))))
for path,label in inputs:
 if path.is_file():notices.append('===== '+label+' =====\n'+path.read_text(errors='replace')+'\n')
if len(notices)<3:raise RuntimeError('connector distribution license notices unavailable')
notice=ROOT/'app/src/main/assets/cloudflared-THIRD-PARTY-NOTICES.txt';notice.parent.mkdir(parents=True,exist_ok=True);notice.write_text('\n'.join(notices))
# APK signing is the trust boundary for the binary and its expected digest. The
# manifest is generated from the verified build, never supplied through the UI.
dest=ROOT/'app/src/main/jniLibs/arm64-v8a/libcloudflared.so';dest.parent.mkdir(parents=True,exist_ok=True)
if not dest.exists()or digest(dest)!=record['sha256']:
 temp=dest.with_suffix('.build');shutil.copyfile(BINARY,temp);temp.chmod(0o755);os.replace(temp,dest)
manifest=ROOT/'app/src/main/assets/cloudflared-build.json';manifest.parent.mkdir(parents=True,exist_ok=True)
allowed=('version','upstream_tag','source_commit','go_version','ndk','target','goos','cgo','sha256','bytes')
manifest.write_text(json.dumps({k:record[k]for k in allowed},indent=2)+'\n')
print('Pinned Android/Bionic cloudflared verified: '+record['sha256'])
