"""Build two separate deliveries from explicit, verified runtime directories.

Run tests/builds first. Output must be a new directory. No credentials are needed.
"""
from pathlib import Path
import argparse, hashlib, json, shutil, subprocess

p=argparse.ArgumentParser()
p.add_argument('--output',required=True);p.add_argument('--java-runtime',required=True)
p.add_argument('--mysql-runtime',required=True);p.add_argument('--demo-sql',required=True)
a=p.parse_args();root=Path(__file__).resolve().parent.parent
out=Path(a.output).resolve();out.mkdir(parents=True,exist_ok=False)
source=out/'GitHub源码版';portable=out/'便携运行版'
source.mkdir();portable.mkdir()
java=Path(a.java_runtime).resolve();mysql=Path(a.mysql_runtime).resolve()
demo=Path(a.demo_sql).resolve()
assert demo.read_bytes().startswith(b'-- Public demo:'),'Only use a sanitized public export'
assert (java/'bin/java.exe').is_file() and (mysql/'bin/mysqld.exe').is_file()
exclusions={'.git','.idea','.vscode','.codex_tmp','target','node_modules','dist','__pycache__'}
def ignore(directory,names):
    return [n for n in names if n in exclusions or n.endswith(('.log','.pyc','.iml')) or n.startswith('.env')]
for name in ['study-server','study-web']:
    shutil.copytree(root/name,source/name,ignore=ignore)
shutil.copytree(root/'delivery',source/'delivery',ignore=lambda d,n:[x for x in n if x=='demo.sql' or x=='__pycache__'])
(source/'sql').mkdir();shutil.copy2(demo,source/'sql/study_system.sql')
shutil.copy2(root/'delivery/README-source.md',source/'README.md')
(source/'docs').mkdir();shutil.copy2(root/'docs/DEPLOYMENT.md',source/'docs/DEPLOYMENT.md')
(source/'.gitignore').write_text('study-web/node_modules/\nstudy-web/dist/\nstudy-server/target/\n.idea/\n.vscode/\n*.iml\n.env*\n*.log\ndata/\nruntime/\nrelease/\n__pycache__/\n',encoding='utf-8')
for name in ['app','web','sql','runtime/mysql/bin','logs']:(portable/name).mkdir(parents=True,exist_ok=True)
shutil.copy2(root/'study-server/target/study-server-0.0.1-SNAPSHOT.jar',portable/'app/study-server.jar')
shutil.copytree(root/'study-web/dist',portable/'web',dirs_exist_ok=True)
shutil.copytree(java,portable/'runtime/java')
for item in (mysql/'bin').iterdir():
    if item.name in ['mysqld.exe','mysql.exe','mysqladmin.exe'] or (item.suffix.lower()=='.dll' and 'debug' not in item.name and not item.name.startswith('mysqlrouter')):
        shutil.copy2(item,portable/'runtime/mysql/bin'/item.name)
for name in ['share','lib/plugin','lib/private']:
    shutil.copytree(mysql/name,portable/'runtime/mysql'/name,ignore=lambda d,n:[x for x in n if x.endswith('.lib') or 'debug' in x.lower()])
for name in ['LICENSE','README','docs/INFO_BIN','docs/INFO_SRC']:
    (portable/'runtime/mysql'/name).parent.mkdir(parents=True,exist_ok=True)
    shutil.copy2(mysql/name,portable/'runtime/mysql'/name)
# Use the redistributable C/C++ runtime files supplied with Temurin, with its notices.
for name in ['msvcp140.dll','vcruntime140.dll','vcruntime140_1.dll']:
    shutil.copy2(java/'bin'/name,portable/'runtime/mysql/bin'/name)
shutil.copy2(demo,portable/'sql/study_system.sql')
shutil.copy2(root/'delivery/start.bat',portable/'启动.bat')
(portable/'launcher.ps1').write_text((root/'delivery/launcher.ps1').read_text(encoding='utf-8-sig'),encoding='utf-8-sig')
shutil.copy2(root/'delivery/README-portable.md',portable/'使用说明.md')
shutil.copy2(root/'delivery/THIRD-PARTY.md',portable/'THIRD-PARTY.md')
# Preserve installed frontend production packages' own license and notice files.
lock=json.loads((root/'study-web/package-lock.json').read_text(encoding='utf-8'))
licenses={}
for package,metadata in lock.get('packages',{}).items():
    if not package or metadata.get('dev'):continue
    location=root/'study-web'/package
    if not location.is_dir():continue
    licenses[package]={'version':metadata.get('version'),'license':metadata.get('license')}
    for file in location.iterdir():
        if file.is_file() and file.name.upper().startswith(('LICENSE','LICENCE','COPYING','NOTICE')):
            destination=portable/'licenses/frontend'/package/file.name
            destination.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(file,destination)
(portable/'licenses/frontend').mkdir(parents=True,exist_ok=True)
(portable/'licenses/frontend/packages.json').write_text(json.dumps(licenses,ensure_ascii=False,indent=2),encoding='utf-8')
for path in source.rglob('*.ps1'):
    path.write_text(path.read_text(encoding='utf-8-sig'),encoding='utf-8-sig')
manifest={'question_bank':{'subject':4,'chapter':15,'knowledge_point':70,'question':703,'question_option':2236},'personal_learning_records':0,'demo_users':2,'demo_sql_sha256':hashlib.sha256(demo.read_bytes()).hexdigest(),'runtime_java':(java/'release').read_text(encoding='utf-8'),'mysql_version':'8.0.41','files':{}}
for folder in [source,portable]:
    files={str(f.relative_to(folder)).replace('\\','/'):hashlib.sha256(f.read_bytes()).hexdigest() for f in sorted(folder.rglob('*')) if f.is_file()}
    manifest['files'][folder.name]=files
    (folder/'文件校验.json').write_text(json.dumps(files,ensure_ascii=False,indent=2),encoding='utf-8')
(out/'交付清单.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2),encoding='utf-8')
print(json.dumps({'source':str(source),'portable':str(portable),'files':{k:len(v) for k,v in manifest['files'].items()}},ensure_ascii=False))
