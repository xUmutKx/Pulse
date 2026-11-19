import math, random, sys
import numpy as np
random.seed(7); np.random.seed(7)
A='/home/user/repo/app/src/main/assets/kbd/'
ROWS={'tr':["qwertyuıopğü","asdfghjklşi","zxcvbnmöç"],'en':["qwertyuiop","asdfghjkl","zxcvbnm"]}
def adj(lang):
    pos={}
    for r,row in enumerate(ROWS[lang]):
        for c,ch in enumerate(row): pos[ch]=(r,c+0.5*r)
    m={}
    for a,(r1,c1) in pos.items():
        m[a]=[b for b,(r2,c2) in pos.items() if b!=a and abs(r1-r2)<=1 and abs(c1-c2)<=1.1]
    return m
def load(lang,n):
    ws=[];sc={}
    for line in open(A+lang+'.txt',encoding='utf8'):
        p=line.split()
        if len(p)!=2 or not p[0].isalpha(): continue
        ws.append(p[0]); sc[p[0]]=math.log(float(p[1])+1)
        if len(ws)>=n: break
    return ws,sc
def deletes(w,d):
    out={w}; cur={w}
    for _ in range(d):
        nxt=set()
        for x in cur:
            for i in range(len(x)): nxt.add(x[:i]+x[i+1:])
        out|=nxt; cur=nxt
    return out
def ed(a,b):
    d=list(range(len(b)+1))
    for i in range(1,len(a)+1):
        prev=d[0]; d[0]=i
        for j in range(1,len(b)+1):
            t=d[j]; d[j]=min(d[j]+1,d[j-1]+1,prev+(a[i-1]!=b[j-1]))
            if i>1 and j>1 and a[i-1]==b[j-2] and a[i-2]==b[j-1]: d[j]=min(d[j],prev)  # rough swap
            prev=t
    return d[len(b)]
def isswap(a,b):
    if len(a)!=len(b) or a==b: return False
    i=next(k for k in range(len(a)) if a[k]!=b[k])
    return i+1<len(a) and a[i]==b[i+1] and a[i+1]==b[i] and a[i+2:]==b[i+2:]
def typo(w,A_):
    k=random.random(); i=random.randrange(len(w))
    if k<.45 and A_.get(w[i]): return w[:i]+random.choice(A_[w[i]])+w[i+1:]
    if k<.6 and len(w)>3: return w[:i]+w[i+1:]
    if k<.72 and A_.get(w[i]): return w[:i]+random.choice(A_[w[i]])+w[i:]
    if k<.85 and i+1<len(w): return w[:i]+w[i+1]+w[i]+w[i+2:]
    if k<.92: return w[:i]+w[i]+w[i:]
    j=random.randrange(len(w))
    w2=w[:i]+(random.choice(A_[w[i]]) if A_.get(w[i]) else w[i])+w[i+1:]
    return w2[:j]+(random.choice(A_[w2[j]]) if A_.get(w2[j]) else w2[j])+w2[j+1:]
def feats(typed,cand,run,sc):
    fc=sc.get(cand,0.0); e=ed(typed,cand)
    fr=sc.get(run,0.0) if run else 0.0; er=ed(typed,run) if run else 3
    return [fc, fr, (fc-2.6*e)-(fr-2.6*er if run else 0.0), float(e), float(er), float(run is not None), float(isswap(typed,cand)), float(len(typed)), float(len(cand)-len(typed))]
rows=[];ys=[]
for lang,n in (('tr',40000),('en',30000)):
    ws,sc=load(lang,n); A_=adj(lang)
    # 8% of the rarer words are held out: typed right, they are real words the list does not know (what a wrong correction looks like)
    rare=ws[5000:]; held=set(random.sample(rare,int(len(rare)*.08)))
    known={w:s for w,s in sc.items() if w not in held}
    idx={}
    for w in known:
        for d in deletes(w,2 if len(w)>=5 else 1): idx.setdefault(d,[]).append(w)
    def best(t):
        cs=set()
        for d in deletes(t,2 if len(t)>=5 else 1): cs.update(idx.get(d,()))
        cs.discard(t)
        sc2=sorted([(known[c]-2.6*ed(t,c),c) for c in cs if ed(t,c)<=2],reverse=True)
        return (sc2[0][1], sc2[1][1] if len(sc2)>1 else None) if sc2 else None
    freqw=[w for w in ws if w in known and len(w)>=3]
    wts=np.array([known[w] for w in freqw]); wts=np.exp(wts-wts.max()); wts/=wts.sum()
    pos=neg=0
    for w in np.random.choice(freqw,6000,p=wts**.5/ (wts**.5).sum()):
        t=typo(w,A_)
        if t==w or t in known: continue
        r=best(t)
        if r is None: continue
        rows.append(feats(t,r[0],r[1],known)); ys.append(1 if r[0]==w else 0)
    for w in random.sample(sorted(held),min(3000,len(held))):
        if len(w)<3: continue
        r=best(w)
        if r is None: continue
        rows.append(feats(w,r[0],r[1],known)); ys.append(0)
    print(lang,len(rows),sum(ys),file=sys.stderr)
X=np.array(rows,dtype=float); y=np.array(ys,dtype=float)
mu=X.mean(0); sd=X.std(0); sd[sd==0]=1
Z=(X-mu)/sd
H=12
perm=np.random.permutation(len(y)); cut=int(len(y)*.8); tr_,te=perm[:cut],perm[cut:]
W1=np.random.randn(H,Z.shape[1])*.3; B1=np.zeros(H); W2=np.random.randn(H)*.3; B2=0.0
lr=.05
for it in range(6000):
    Zt=Z[tr_]; yt=y[tr_]
    a=np.tanh(Zt@W1.T+B1); p=1/(1+np.exp(-(a@W2+B2))); g=(p-yt)/len(yt)
    gW2=a.T@g+.001*W2; gB2=g.sum(); da=np.outer(g,W2)*(1-a*a); gW1=da.T@Zt+.001*W1; gB1=da.sum(0)
    W1-=lr*gW1*10; B1-=lr*gB1*10; W2-=lr*gW2*10; B2-=lr*gB2*10
Z=Z[te]; y=y[te]
p=1/(1+np.exp(-(np.tanh(Z@W1.T+B1)@W2+B2)))
for th in (.5,.6,.7,.8):
    tp=((p>=th)&(y==1)).sum(); fp=((p>=th)&(y==0)).sum(); fn=((p<th)&(y==1)).sum()
    print(f"th {th}: kept right {tp}, wrong fixes {fp}, missed {fn}, precision {tp/max(1,tp+fp):.3f}")
base_fp=(y==0).sum(); print("without gate wrong fixes", base_fp, "right", (y==1).sum())
print("MEAN", ", ".join(f"{v:.5f}f" for v in mu))
print("SD", ", ".join(f"{v:.5f}f" for v in sd))
print("W1", "; ".join(", ".join(f"{v:.5f}f" for v in row) for row in W1)); print("B1", ", ".join(f"{v:.5f}f" for v in B1)); print("W2", ", ".join(f"{v:.5f}f" for v in W2)); print("B2", f"{B2:.5f}f")
