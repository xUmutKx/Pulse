import zipfile, struct, sys, os
d = sys.argv[1]
for f in sorted(os.listdir(d)):
    try:
        z = zipfile.ZipFile(os.path.join(d, f))
    except Exception as e:
        print(f, "BADZIP"); continue
    names = {n.split("/")[-1].lower(): n for n in z.namelist()}
    info = []
    for k in ["main.bmp","cbuttons.bmp","numbers.bmp","nums_ex.bmp","text.bmp","posbar.bmp","titlebar.bmp","eqmain.bmp","pledit.bmp","volume.bmp"]:
        if k in names:
            b = z.read(names[k])
            if b[:2] != b"BM": info.append(k+":notbmp"); continue
            w, h, planes, bpp, comp = struct.unpack("<iihhI", b[18:34])
            if comp != 0 or bpp not in (8,24,32): info.append("%s:%dbpp/c%d" % (k,bpp,comp))
        else: info.append(k+":MISSING")
    print(f, info)
