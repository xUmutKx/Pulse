import sys,struct
sys.path.insert(0,'.')
try:
    import olefile
except Exception as e: print("noole",e); sys.exit()
o=olefile.OleFileIO('/sdcard/Download/projects/burs/63314a919abcc.doc')
wd=o.openstream('WordDocument').read()
fl=struct.unpack_from('<H',wd,10)[0]
t=o.openstream('1Table' if fl&0x200 else '0Table').read()
ccp=struct.unpack_from('<I',wd,76)[0]
print(ccp)
import re
# text simple: assume pieces
fc=struct.unpack_from('<I',wd,418)[0]; l=struct.unpack_from('<I',wd,422)[0]
i=fc
while t[i]==1: i+=3+struct.unpack_from('<H',t,i+1)[0]
n=(struct.unpack_from('<I',t,i+1)[0]-4)//12;p=i+5;pcd=p+4*(n+1)
s=''
for k in range(n):
    a,e=struct.unpack_from('<II',t,p+4*k); e=min(e,ccp)
    raw=struct.unpack_from('<I',t,pcd+k*8+2)[0]
    comp=raw&0x40000000; f=raw&0x3FFFFFFF
    if comp: s+=wd[f//2:f//2+e-a].decode('cp1252')
    else: s+=wd[f:f+2*(e-a)].decode('utf-16le')
    if a>=ccp:break
print(repr(s[:1500]))
