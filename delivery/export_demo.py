"""Export a clean demo on an isolated database; never mutate the source database.

Builder requirements: Python 3, MySQL 8 client, JDK 17, Spring crypto/logging jars.
Passwords are read from MYSQL_PWD only. Output work files must remain private.
"""
import argparse, hashlib, json, os, re, subprocess, uuid
from pathlib import Path

p=argparse.ArgumentParser()
p.add_argument('--mysql-bin',required=True);p.add_argument('--java',required=True)
p.add_argument('--crypto-classpath',required=True);p.add_argument('--work',required=True)
p.add_argument('--database',default='study_system');p.add_argument('--user',default='root')
p.add_argument('--port',default='3306');p.add_argument('--output',required=True)
a=p.parse_args();root=Path(__file__).resolve().parent.parent
work=Path(a.work).resolve();work.mkdir(parents=True,exist_ok=True)
bin=Path(a.mysql_bin);connection=['--protocol=TCP','--host=127.0.0.1',f'--port={a.port}',f'--user={a.user}','--default-character-set=utf8mb4']
def sql(statement,db=None):
    args=[str(bin/'mysql.exe'),*connection,'--batch','--raw','--skip-column-names']
    if db:args.append(db)
    return subprocess.run(args,input=statement.encode('utf-8'),stdout=subprocess.PIPE,stderr=subprocess.PIPE,check=True).stdout
def dump(db):
    return subprocess.run([str(bin/'mysqldump.exe'),*connection,'--single-transaction','--skip-lock-tables','--no-tablespaces','--set-gtid-purged=OFF','--skip-comments','--hex-blob',db],stdout=subprocess.PIPE,stderr=subprocess.PIPE,check=True).stdout
tables=sql('SHOW TABLES',a.database).decode().splitlines()
assert set(tables)=={'subject','chapter','knowledge_point','question','question_option','user','answer_record','wrong_question','favorite'}
columns={t:sql('SHOW COLUMNS FROM `'+t+'`',a.database).decode().splitlines() for t in tables}
def fingerprints(db,content=False):
    result={}
    for table in (['subject','chapter','knowledge_point','question','question_option'] if content else tables):
        cs=[line.split('\t')[0] for line in columns[table]]
        if content and table=='question':cs.remove('created_by')
        fields=','.join('`'+c+'`' for c in cs)
        data=sql(f'SELECT JSON_ARRAY({fields}) FROM `{table}` ORDER BY id',db)
        result[table]={'rows':len(data.splitlines()),'sha256':hashlib.sha256(data).hexdigest()}
    return result
baseline=fingerprints(a.database);content=fingerprints(a.database,True)
snapshot=dump(a.database);(work/'private-source-backup.sql').write_bytes(snapshot)
scratch='study_delivery_'+uuid.uuid4().hex[:12]
sql('CREATE DATABASE `'+scratch+'` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;')
subprocess.run([str(bin/'mysql.exe'),*connection,scratch],input=snapshot,stdout=subprocess.PIPE,stderr=subprocess.PIPE,check=True)
hashes=subprocess.run([a.java,'--class-path',a.crypto_classpath,str(root/'delivery/HashDemoPasswords.java')],capture_output=True,text=True,check=True).stdout.splitlines()
assert len(hashes)==2 and all(h.startswith('$2') for h in hashes)
sql(f"""SET FOREIGN_KEY_CHECKS=0;
TRUNCATE TABLE answer_record; TRUNCATE TABLE wrong_question; TRUNCATE TABLE favorite;
DELETE FROM user;
INSERT INTO user(id,username,password,nickname,email,role,status) VALUES
(1,'admin','{hashes[0]}','演示管理员',NULL,'ADMIN',1),
(2,'student','{hashes[1]}','演示学生',NULL,'STUDENT',1);
ALTER TABLE user AUTO_INCREMENT=3;
UPDATE question SET created_by=1,updated_at=updated_at;
SET FOREIGN_KEY_CHECKS=1;""",scratch)
assert content==fingerprints(scratch,True),'Question bank changed'
assert baseline==fingerprints(a.database),'Source database changed'
header=('-- Public demo: full question bank; no original accounts or learning records.\n'
        '-- First install only. Reimporting replaces this demo database.\n'
        'CREATE DATABASE IF NOT EXISTS `study_system` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;\nUSE `study_system`;\n')
output=Path(a.output);output.parent.mkdir(parents=True,exist_ok=True)
output.write_bytes(header.encode()+dump(scratch).rstrip(b'\r\n')+b'\n')
report={'source_database':a.database,'test_database':scratch,'source_baseline':baseline,'content_fingerprints':content,'demo_counts':{t:fingerprints(scratch)[t]['rows'] for t in tables},'source_unchanged':True,'creator_remapped_to_demo_admin':True,'output_sha256':hashlib.sha256(output.read_bytes()).hexdigest()}
(work/'database-evidence.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
print(json.dumps({'test_database':scratch,'demo_counts':report['demo_counts'],'source_unchanged':True},ensure_ascii=False))
