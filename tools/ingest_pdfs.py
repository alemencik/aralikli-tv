import argparse,collections,glob,hashlib,json,os,re,unicodedata
from pathlib import Path
import fitz
ROOT=Path(__file__).parent
# Source directory supplied by operator; never assume a machine-specific /mnt/data path.
SOURCES=[('rapor','Rapor Yazma ve Mülakat Teknikleri', 'AUZEF RAPOR YAZMA VE MÜLAKAT TEKNİKLERİ.pdf'),('yerel','Yerel Yönetimler ve Sosyal Hizmet','AUZEF YEREL YÖNETİMLER VE SOSYAL HİZMET.pdf')]
QSTART=re.compile(r'^\s*(\d{1,3})\s*[.\-)]+\s*(.+)',re.M)
OPT=re.compile(r'^\s*([A-Ea-e])\s*\)\s*(.*)',re.M)
ANSWER=re.compile(r'(?im)^\s*Cevap\s*:\s*([A-Ea-e])\s*\)\s*(.*)')
HEADER=re.compile(r'(?i)^(?:RAPOR YAZMA VE M\s*|MÜLAKAT TEKNİKLERİ\s*|YEREL YÖNETİMLE\s*|ER VE SOSYAL HİZMET\s*|YEREL YÖNETİMLER VE SOSYAL HİZMET\s*|Sayfa \d+\s*|VİZEDE ÇIKAN TÜM SORULAR\s*|FİNALDE ÇIKAN TÜM SORULAR\s*|BÜTÜNLEMEDE ÇIKAN TÜM SORULAR\s*|AUZEF ÖĞRENCİ BİRLİĞİ\s*)$')

def clean(s):return ' '.join(s.split())
def normalized(s):return re.sub(r'[^a-zA-Z0-9ÇçĞğİıÖöŞşÜü]+','',unicodedata.normalize('NFC',s).lower())

def parse(srcroot,pdfname,sid):
    p=srcroot / pdfname
    d=fitz.open(p)
    chunks=[]
    for k in range(1,len(d)):
        page=d[k];mid=page.rect.width/2
        for side,clip in [('left',fitz.Rect(0,0,mid,page.rect.height)),('right',fitz.Rect(mid,0,page.rect.width,page.rect.height))]:
            raw=page.get_text(clip=clip,sort=True)
            lines=[line.strip() for line in raw.splitlines() if not HEADER.match(line.strip())]
            chunks.append((k+1,side,'\n'.join(lines)+'\n'))
    txt='\n'.join(x[2] for x in chunks)
    offsets=[];pos=0
    for page,side,part in chunks:
        offsets.append((pos,pos+len(part)+1,page,side))
        pos+=len(part)+1
    starts=list(QSTART.finditer(txt))
    found=[]; rejected=[]
    for n,st in enumerate(starts):
        end=starts[n+1].start() if n+1<len(starts) else len(txt)
        segment=txt[st.start():end]
        page=next((z[2] for z in offsets if z[0]<=st.start()<z[1]),0)
        options=list(OPT.finditer(segment)); ans=list(ANSWER.finditer(segment))
        if len(ans)!=1 or len(options)<4:continue
        stem=clean(st.group(2)+' '+segment[st.end()-st.start():options[0].start()])
        dic={}
        for i,m in enumerate(options):
            if m.start()>=ans[0].start():break
            label=m.group(1).upper()
            if label in dic:break
            nextpos=options[i+1].start() if i+1<len(options) else ans[0].start()
            dic[label]=clean(m.group(2)+' '+segment[m.end():nextpos])
        key=ans[0].group(1).upper()
        atxt=clean(ans[0].group(2)+' '+segment[ans[0].end():])
        atxt=re.split(r'(?i)\bNOT\s*:|\bAçıklama\s*:',atxt)[0]
        ok= (len(stem)>18 and len(stem)<1300 and list(dic)==list('ABCDE') and all(dic.values()) and key in dic)
        if ok:
            a,b=normalized(atxt),normalized(dic[key]);
            # quoted answer must match the selected option's text, not merely the letter.
            ok=bool(a and b) and (a.startswith(b[:min(len(b),17)]) or b.startswith(a[:min(len(a),17)]))
        if not ok:
            rejected.append({'source_page':page,'source_no':int(st.group(1)),'reason':'incomplete_options_or_answer_mismatch','excerpt':stem[:110]})
            continue
        h=hashlib.sha256((sid+'\n'+normalized(stem)+'\n'+normalized(dic[key])).encode()).hexdigest()[:14]
        found.append({'id':f'{sid}-{h}','stem':stem,'options':dic,'answer':key,'answer_text':None,'source_file':pdfname,'source_page':page,'source_no':int(st.group(1))})
    return found,rejected,len(starts)

def main(srcroot):
    dest=ROOT.parent/'app/src/main/assets/questions.json'; dest.parent.mkdir(parents=True,exist_ok=True)
    summary={'scanned_files':[],'duplicates':[],'source_decks':[],'warnings':[],'review_needed':[]}
    digests=collections.defaultdict(list)
    for p in sorted(srcroot.iterdir()):
        if p.suffix.lower()=='.pdf':
            d=fitz.open(p)
            raw=''.join(page.get_text() for page in d)
            digest=hashlib.sha256(p.read_bytes()).hexdigest()
            digests[digest].append(p.name)
            summary['scanned_files'].append({'filename':p.name,'pages':len(d),'text_chars':len(raw),'has_selectable_text':len(raw)>0})
    summary['duplicates']=[arr for arr in digests.values() if len(arr)>1]
    decks=[]
    for sid,title,file in SOURCES:
        qs,rejected,num=parse(srcroot,file,sid)
        seen={}; conflicting=set()
        grouped=collections.defaultdict(list)
        for q in qs:grouped[normalized(q['stem'])].append(q)
        for stem,g in grouped.items():
            answers={normalized(q['options'][q['answer']]) for q in g}
            if len(answers)>1:
                conflicting.add(stem)
                summary['warnings'].append({'type':'conflicting_source_answers','question':g[0]['stem'],'source_pages':[q['source_page'] for q in g]})
        selected=[];dup=0
        for q in qs:
            nstem=normalized(q['stem'])
            if nstem in conflicting:continue
            if nstem in seen:
                dup+=1;continue
            seen[nstem]=q
            selected.append(q)
        decks.append({'id':sid,'title':title,'questions':selected})
        summary['source_decks'].append({'title':title,'source_file':file,'candidate_answered':len(qs),'selected_scorable':len(selected),'duplicates_merged':dup,'conflicting_stems':len(conflicting),'rejected_fragments':len(rejected),'rejected_samples':rejected[:20]})
    # Other lessons remain visible with 0 scorable questions until the answer key is established.
    for sid,title in [('ataturk1','Atatürk İlkeleri ve İnkılap Tarihi I'),('ataturk2','Atatürk İlkeleri ve İnkılap Tarihi II'),('turkce1','Türk Dili I'),('ingilizce1','Yabancı Dil I (İngilizce)'),('sosyal','Sosyal Hizmet Kuramları')]:
        decks.append({'id':sid,'title':title,'questions':[]})
    obj={'schema':1,'decks':decks}
    assert len({q['id'] for d in decks for q in d['questions']})==sum(len(d['questions']) for d in decks)
    dest.write_text(json.dumps(obj,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    (ROOT/'import_report.json').write_text(json.dumps(summary,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    print('Total verified scoreable',sum(len(d['questions']) for d in decks),'decks',[(x['title'],len(x['questions'])) for x in decks]);print('conflicts',len(summary['warnings']))
if __name__=='__main__':
    ap=argparse.ArgumentParser(description='Extract answered PDF questions from a local PDF folder')
    ap.add_argument('--pdf-dir', type=Path, required=True, help='Path to folder with exact source PDF file names')
    args=ap.parse_args()
    if not args.pdf_dir.is_dir():
        ap.error('PDF directory not found')
    main(args.pdf_dir)
