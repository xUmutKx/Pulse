// Placeholder box art, drawn when a game has no cover image yet.
// Exposes window.Covers.{front, back, hash, hsl}.
(function(){
"use strict";
function hash(s){let h=2166136261;for(let i=0;i<s.length;i++){h^=s.charCodeAt(i);h=Math.imul(h,16777619)}return h>>>0}
function rng(seed){let a=seed||1;return function(){a|=0;a=a+0x6D2B79F5|0;let t=Math.imul(a^a>>>15,1|a);t=t+Math.imul(t^t>>>7,61|t)^t;return((t^t>>>14)>>>0)/4294967296}}
const hsl=(h,s,l,a)=>`hsla(${((h%360)+360)%360},${s}%,${l}%,${a==null?1:a})`;
const FONTS=["Lilita One","Bungee","Audiowide"];
const SCENE_OF={Platformer:"platform",Party:"party",Racing:"race",Arcade:"race",RPG:"rpg",Adventure:"rpg",Tactics:"rpg",Action:"action",Shooter:"action",Fighting:"action",Flight:"flight",Puzzle:"puzzle",Sports:"sports",Horror:"horror",Simulation:"platform",Streaming:"media"};

function drawScene(ctx,x,y,w,h,game,r,kind){
  const H=game.hue;
  ctx.save();ctx.beginPath();ctx.rect(x,y,w,h);ctx.clip();
  const dark=kind==="horror"||kind==="rpg"&&r()<.5;
  let g=ctx.createLinearGradient(0,y,0,y+h);
  g.addColorStop(0,dark?hsl(H+200,45,14):hsl(H,75,62));
  g.addColorStop(1,dark?hsl(H+230,50,6):hsl(H+40,70,32));
  ctx.fillStyle=g;ctx.fillRect(x,y,w,h);
  const cx=x+w/2;
  if(kind==="platform"||kind==="party"){
    ctx.fillStyle=hsl(H+60,95,80,.9);ctx.beginPath();ctx.arc(x+w*(.2+r()*.6),y+h*.3,w*.16,0,7);ctx.fill();
    for(let k=0;k<4;k++){ctx.fillStyle=hsl(H+90+k*18,55,48-k*8);ctx.beginPath();const base=y+h*(.58+k*.1);ctx.moveTo(x,base);
      for(let i=0;i<=6;i++){ctx.quadraticCurveTo(x+w*(i/6-.08),base-h*(.06+r()*.1),x+w*i/6,base)}ctx.lineTo(x+w,y+h);ctx.lineTo(x,y+h);ctx.fill()}
    for(let i=0;i<4;i++){ctx.fillStyle=hsl(H+20,40,92);const px=x+w*r()*.8,py=y+h*(.25+r()*.35);roundRect(ctx,px,py,w*.18,h*.03,6);ctx.fill();
      ctx.fillStyle=hsl(50,95,60);ctx.beginPath();ctx.arc(px+w*.09,py-h*.035,w*.022,0,7);ctx.fill()}
    if(kind==="party"){for(let i=0;i<60;i++){ctx.fillStyle=hsl(r()*360,85,62);ctx.save();ctx.translate(x+r()*w,y+r()*h*.7);ctx.rotate(r()*6);ctx.fillRect(-4,-2,8,4);ctx.restore()}}
  }else if(kind==="race"){
    const hz=y+h*.48;ctx.fillStyle=hsl(H+180,40,18);ctx.fillRect(x,hz,w,h);
    ctx.fillStyle=hsl(0,0,12);ctx.beginPath();ctx.moveTo(cx-w*.03,hz);ctx.lineTo(cx+w*.03,hz);ctx.lineTo(x+w*1.1,y+h);ctx.lineTo(x-w*.1,y+h);ctx.fill();
    ctx.strokeStyle=hsl(50,95,65);ctx.lineWidth=w*.012;ctx.setLineDash([h*.04,h*.04]);ctx.beginPath();ctx.moveTo(cx,hz);ctx.lineTo(cx,y+h);ctx.stroke();ctx.setLineDash([]);
    for(let i=0;i<26;i++){ctx.strokeStyle=hsl(H+r()*60,90,75,.55);ctx.lineWidth=1+r()*3;const sy=y+r()*h*.45,sx=x+r()*w;ctx.beginPath();ctx.moveTo(sx,sy);ctx.lineTo(sx+(sx<cx?-1:1)*w*(.15+r()*.3),sy+h*.02);ctx.stroke()}
    ctx.fillStyle=hsl(H+170,85,55);roundRect(ctx,cx-w*.14,y+h*.72,w*.28,h*.1,10);ctx.fill();ctx.fillStyle=hsl(0,0,8);ctx.fillRect(cx-w*.11,y+h*.8,w*.06,h*.03);ctx.fillRect(cx+w*.05,y+h*.8,w*.06,h*.03);
  }else if(kind==="rpg"||kind==="flight"){
    for(let i=0;i<70;i++){ctx.fillStyle=`rgba(255,255,255,${r()*.8})`;ctx.fillRect(x+r()*w,y+r()*h*.55,1.5,1.5)}
    ctx.fillStyle=hsl(H+40,60,88,.95);ctx.beginPath();ctx.arc(x+w*(.25+r()*.5),y+h*.24,w*.12,0,7);ctx.fill();
    for(let k=0;k<3;k++){ctx.fillStyle=hsl(H+200+k*10,35,30-k*9);ctx.beginPath();const base=y+h*(.62+k*.12);ctx.moveTo(x,y+h);ctx.lineTo(x,base);
      let px=x;while(px<x+w){const pw=w*(.12+r()*.18);ctx.lineTo(px+pw/2,base-h*(.08+r()*.18));ctx.lineTo(px+pw,base);px+=pw}ctx.lineTo(x+w,y+h);ctx.fill()}
    if(kind==="flight"){ctx.fillStyle=hsl(H+180,80,70);ctx.save();ctx.translate(cx,y+h*.45);ctx.rotate(-.25);ctx.beginPath();ctx.moveTo(w*.18,0);ctx.lineTo(-w*.12,-h*.03);ctx.lineTo(-w*.04,0);ctx.lineTo(-w*.12,h*.03);ctx.fill();ctx.fillRect(-w*.06,-h*.07,w*.05,h*.14);ctx.restore()}
    else{ctx.fillStyle=hsl(H+220,30,10);const bx=x+w*(.55+r()*.2),by=y+h*.62;ctx.fillRect(bx,by-h*.12,w*.16,h*.14);ctx.fillRect(bx-w*.03,by-h*.2,w*.05,h*.2);ctx.fillRect(bx+w*.14,by-h*.17,w*.05,h*.17);
      ctx.beginPath();ctx.moveTo(bx-w*.04,by-h*.2);ctx.lineTo(bx-w*.005,by-h*.26);ctx.lineTo(bx+w*.03,by-h*.2);ctx.fill();ctx.fillStyle=hsl(45,95,65);ctx.fillRect(bx+w*.06,by-h*.08,w*.02,h*.025)}
  }else if(kind==="action"){
    ctx.save();ctx.translate(cx,y+h*.55);for(let i=0;i<18;i++){ctx.rotate(Math.PI*2/18);ctx.fillStyle=i%2?hsl(H,90,70,.18):hsl(H+30,90,50,.1);ctx.beginPath();ctx.moveTo(0,0);ctx.lineTo(w,-w*.12);ctx.lineTo(w,w*.12);ctx.fill()}ctx.restore();
    ctx.fillStyle=hsl(H+180,85,60);ctx.beginPath();for(let i=0;i<6;i++){const a=i/6*Math.PI*2-Math.PI/2;ctx.lineTo(cx+Math.cos(a)*w*.22,y+h*.58+Math.sin(a)*w*.22)}ctx.fill();
    ctx.fillStyle=hsl(H+180,60,20);ctx.beginPath();for(let i=0;i<6;i++){const a=i/6*Math.PI*2-Math.PI/2;ctx.lineTo(cx+Math.cos(a)*w*.15,y+h*.58+Math.sin(a)*w*.15)}ctx.fill();
    ctx.strokeStyle="rgba(255,255,255,.85)";ctx.lineWidth=w*.02;for(let i=0;i<3;i++){ctx.beginPath();ctx.moveTo(x+w*(.1+i*.08),y+h*(.9-i*.05));ctx.lineTo(x+w*(.9-i*.05),y+h*(.35+i*.04));ctx.stroke()}
  }else if(kind==="puzzle"){
    const n=6,s=w/n;for(let i=0;i<n;i++)for(let j=0;j<Math.ceil(h/s);j++){if(r()<.55){ctx.fillStyle=hsl(H+[0,60,150,210][(r()*4)|0],80,58,.95);roundRect(ctx,x+i*s+3,y+j*s+3,s-6,s-6,8);ctx.fill();ctx.fillStyle="rgba(255,255,255,.3)";roundRect(ctx,x+i*s+7,y+j*s+7,s-14,(s-14)*.3,4);ctx.fill()}}
  }else if(kind==="sports"){
    ctx.fillStyle=hsl(130,50,38);ctx.fillRect(x,y+h*.55,w,h);ctx.strokeStyle="rgba(255,255,255,.7)";ctx.lineWidth=3;
    ctx.beginPath();ctx.ellipse(cx,y+h*.78,w*.4,h*.1,0,0,7);ctx.stroke();ctx.beginPath();ctx.moveTo(x,y+h*.78);ctx.lineTo(x+w,y+h*.78);ctx.stroke();
    for(let k=0;k<3;k++){ctx.strokeStyle=hsl(H,30,90,.25);ctx.lineWidth=h*.03;ctx.beginPath();ctx.ellipse(cx,y+h*.55,w*(.7+k*.15),h*(.25+k*.06),0,Math.PI,0);ctx.stroke()}
    ctx.fillStyle="#fff";ctx.beginPath();ctx.arc(cx+w*.1,y+h*.45,w*.09,0,7);ctx.fill();ctx.strokeStyle=hsl(H,70,40);ctx.lineWidth=3;ctx.beginPath();ctx.arc(cx+w*.1,y+h*.45,w*.09,.4,2.4);ctx.stroke();
  }else if(kind==="media"){
    ctx.fillStyle=hsl(H+200,40,8,.55);ctx.fillRect(x,y,w,h);
    for(let k=6;k>0;k--){ctx.strokeStyle=hsl(H+k*8,80,65,.08+k*.03);ctx.lineWidth=2;ctx.beginPath();ctx.arc(cx,y+h*.62,w*.08*k,0,7);ctx.stroke()}
    ctx.fillStyle=hsl(H,85,62);ctx.beginPath();ctx.arc(cx,y+h*.62,w*.17,0,7);ctx.fill();
    ctx.fillStyle="#fff";ctx.beginPath();ctx.moveTo(cx-w*.05,y+h*.62-w*.075);ctx.lineTo(cx+w*.08,y+h*.62);ctx.lineTo(cx-w*.05,y+h*.62+w*.075);ctx.closePath();ctx.fill();
    for(let i=0;i<5;i++){ctx.fillStyle=hsl(H+i*20,60,70,.18);roundRect(ctx,x+w*(.08+i*.18),y+h*.88,w*.14,h*.07,4);ctx.fill()}
  }else if(kind==="horror"){
    ctx.fillStyle=hsl(60,40,85,.9);ctx.beginPath();ctx.arc(x+w*.72,y+h*.22,w*.1,0,7);ctx.fill();
    ctx.fillStyle="#05050a";const mx=x+w*.18,my=y+h*.48;ctx.fillRect(mx,my,w*.64,h*.4);ctx.beginPath();ctx.moveTo(mx-w*.04,my);ctx.lineTo(cx,my-h*.14);ctx.lineTo(mx+w*.68,my);ctx.fill();ctx.fillRect(mx+w*.46,my-h*.16,w*.07,h*.16);
    for(let i=0;i<6;i++){ctx.fillStyle=r()<.4?hsl(40,95,60):"#14141c";ctx.fillRect(mx+w*(.06+(i%3)*.2),my+h*(.06+Math.floor(i/3)*.14),w*.08,h*.07)}
    for(let i=0;i<5;i++){ctx.fillStyle="rgba(200,210,255,.06)";ctx.beginPath();ctx.ellipse(x+r()*w,y+h*(.8+r()*.2),w*.6,h*.05,0,0,7);ctx.fill()}
  }
  ctx.restore();
}
function roundRect(ctx,x,y,w,h,r){ctx.beginPath();ctx.moveTo(x+r,y);ctx.arcTo(x+w,y,x+w,y+h,r);ctx.arcTo(x+w,y+h,x,y+h,r);ctx.arcTo(x,y+h,x,y,r);ctx.arcTo(x,y,x+w,y,r);ctx.closePath()}
function wrapLines(ctx,text,maxW){const words=text.split(" ");const lines=[];let cur="";for(const w of words){const t=cur?cur+" "+w:w;if(ctx.measureText(t).width>maxW&&cur){lines.push(cur);cur=w}else cur=t}if(cur)lines.push(cur);return lines}
const KINDS=["platform","party","race","rpg","flight","action","puzzle","sports","horror"];
const cache=new Map();
// generated covers are ~50 KB each: keep the most recent 240, not every game ever scrolled past
function capCache(k,v){cache.delete(k);cache.set(k,v);if(cache.size>240){const k0=cache.keys().next().value,u=cache.get(k0);cache.delete(k0);if(/^blob:/.test(u))setTimeout(()=>{if(!document.querySelector(`img[src="${u}"],[style*="${u}"]`))URL.revokeObjectURL(u) /* also CSS backgrounds (Details hero, Inspect disc top) */},30000)}}
// The JPEG encode (most of a generated cover's cost) runs in a worker for the shelf: the canvas is drawn here, copied
// to an ImageBitmap and encoded off the main thread into a blob: URL that goes into the same cache. front()/back() stay
// Canvases here are read back (encoded / sampled), so they live in memory (willReadFrequently): a GPU canvas made every
// read wait for the graphics chip, a long stall per cover on a phone.
// synchronous for every other caller (a cover still being encoded is finished here). blob: URLs are revoked a while after
// they fall out of the LRU, and never while an <img> shows one. No worker/OffscreenCanvas → the old toDataURL path.
let encW=null,encId=0;const encCb=new Map(),pend=new Map();
function encoder(){
  if(encW!==null)return encW;
  try{if(typeof OffscreenCanvas==="undefined")throw 0;
    const src='onmessage=async e=>{let blob=null;try{const b=e.data.bm,o=new OffscreenCanvas(b.width,b.height);o.getContext("2d").drawImage(b,0,0);b.close();blob=await o.convertToBlob({type:"image/jpeg",quality:.86})}catch(x){}postMessage({id:e.data.id,blob})}';
    encW=new Worker(URL.createObjectURL(new Blob([src],{type:"text/javascript"})));
    encW.onmessage=e=>{const cb=encCb.get(e.data.id);encCb.delete(e.data.id);if(cb)cb(e.data.blob)};
    encW.onerror=()=>{encW=false;encCb.forEach(cb=>cb(null));encCb.clear()};
  }catch(e){encW=false}
  return encW;
}
function encodeAsync(key,c){
  const w=encoder();if(!w){const u=c.toDataURL("image/jpeg",.86);capCache(key,u);return Promise.resolve(u)}
  const job={c};pend.set(key,job);
  job.p=createImageBitmap(c).then(bm=>new Promise(res=>{const id=++encId;encCb.set(id,res);w.postMessage({id,bm},[bm])})).catch(()=>null).then(blob=>{
    if(pend.get(key)===job){pend.delete(key);capCache(key,blob?URL.createObjectURL(blob):c.toDataURL("image/jpeg",.86))}
    return cache.get(key)||null; // a sync caller may have finished it first (or it was evicted meanwhile: null → caller redraws)
  });
  return job.p;
}
// sync callers asking for a cover that is still in the worker: encode the already-drawn canvas here
function pending(key){const j=pend.get(key);if(!j)return null;pend.delete(key);const u=j.c.toDataURL("image/jpeg",.86);capCache(key,u);return u}
function band(ctx,W,Hh,style,right){
  if(!style.band)return 0;const bandH=Math.round(Hh*.085);
  ctx.fillStyle=style.band.bg;ctx.fillRect(0,0,W,bandH);
  ctx.fillStyle=style.band.fg;ctx.font=`700 ${bandH*.42}px Lexend, sans-serif`;ctx.textBaseline="middle";ctx.textAlign="left";ctx.fillText(style.band.label,W*.05,bandH/2);
  if(right){ctx.textAlign="right";ctx.font=`500 ${bandH*.3}px Lexend, sans-serif`;ctx.fillText(right,W*.95,bandH/2)}
  return bandH;
}
// game: {title, seed, hue, kind?, region?}
// the region is drawn in the band, so it is part of the key: a USA and a EUR copy of one game each get their own cover
const fkey=(style,game)=>style.id+"|f|"+game.title+"|"+(game.region||"");
function front(style,game){
  const key=fkey(style,game);if(cache.has(key))return cache.get(key);
  const url=pending(key)||c2url(drawFront(style,game));capCache(key,url);return url;
}
const c2url=c=>c.toDataURL("image/jpeg",.86);
// same cover as front(), as a Promise of its URL; the encode happens in the worker
function frontAsync(style,game){
  const key=fkey(style,game);if(cache.has(key))return Promise.resolve(cache.get(key));
  if(pend.has(key))return pend.get(key).p.then(u=>u||front(style,game)); // (it resolves null if the cover was dropped from the cache meanwhile: draw it again, as the first caller does)
  return encodeAsync(key,drawFront(style,game)).then(u=>u||front(style,game));
}
function drawFront(style,game){
  const W=420,Hh=Math.round(W/style.ratio),c=document.createElement("canvas");c.width=W;c.height=Hh;const ctx=c.getContext("2d",{willReadFrequently:true});const r=rng(game.seed);
  const bandH=band(ctx,W,Hh,style,game.region?String(game.region).toUpperCase():"");
  const kind=game.scene||KINDS[game.seed%KINDS.length];
  drawScene(ctx,0,bandH,W,Hh-bandH,game,r,kind);
  const font=FONTS[game.seed%FONTS.length];let size=W*.13,lines;
  ctx.textAlign="center";ctx.textBaseline="alphabetic";
  const text=game.title.toUpperCase();
  do{ctx.font=`${size}px "${font}", Lexend, sans-serif`;lines=wrapLines(ctx,text,W*.84);size*=.92}while((lines.length>4||lines.some(l=>ctx.measureText(l).width>W*.9))&&size>12);
  size/=.92;const lh=size*1.04;const ty=bandH+Hh*.08;
  ctx.lineJoin="round";
  lines.forEach((l,i)=>{const yy=ty+i*lh+size*.8;ctx.lineWidth=size*.16;ctx.strokeStyle=hsl(game.hue+200,60,12);ctx.strokeText(l,W/2,yy);
    const tg=ctx.createLinearGradient(0,yy-size,0,yy);tg.addColorStop(0,"#ffffff");tg.addColorStop(1,hsl(game.hue+50,95,78));ctx.fillStyle=tg;ctx.fillText(l,W/2,yy)});
  return c;
}
// lines: array of [label, value] shown on the back
const bkey=(style,game)=>style.id+"|b|"+game.title;
function back(style,game,lines,blurb){
  const key=bkey(style,game);if(cache.has(key))return cache.get(key);
  const url=pending(key)||c2url(drawBack(style,game,lines,blurb));capCache(key,url);return url;
}
// (beta.9) the same back as a Promise of its URL: drawn here, the JPEG encode (most of the 10–90 ms) in the worker, so a
// back made ahead of a flip (or during one) never holds up the shelf
function backAsync(style,game,lines,blurb){
  const key=bkey(style,game);if(cache.has(key))return Promise.resolve(cache.get(key));
  if(pend.has(key))return pend.get(key).p.then(u=>u||back(style,game,lines,blurb));
  return encodeAsync(key,drawBack(style,game,lines,blurb)).then(u=>u||back(style,game,lines,blurb));
}
const hasBack=(style,game)=>cache.has(bkey(style,game));
function drawBack(style,game,lines,blurb){
  const W=420,Hh=Math.round(W/style.ratio),c=document.createElement("canvas");c.width=W;c.height=Hh;const ctx=c.getContext("2d",{willReadFrequently:true});const r=rng(game.seed^0x9e3779b9);
  const g=ctx.createLinearGradient(0,0,0,Hh);g.addColorStop(0,hsl(game.hue+200,40,16));g.addColorStop(1,hsl(game.hue+230,45,7));ctx.fillStyle=g;ctx.fillRect(0,0,W,Hh);
  const bandH=band(ctx,W,Hh,style,"");
  ctx.fillStyle="#fff";ctx.textAlign="left";ctx.textBaseline="alphabetic";ctx.font=`22px "${FONTS[game.seed%FONTS.length]}", Lexend, sans-serif`;
  wrapLines(ctx,game.title.toUpperCase(),W*.88).slice(0,2).forEach((l,i)=>ctx.fillText(l,W*.06,bandH+40+i*26));
  const shotW=W*.42,shotH=shotW*.6,top=bandH+90;
  [[W*.06,top],[W*.52,top],[W*.06,top+shotH+10],[W*.52,top+shotH+10]].forEach(([sx,sy],i)=>{drawScene(ctx,sx,sy,shotW,shotH,{hue:game.hue+i*35},r,KINDS[(game.seed>>>i)%KINDS.length]);ctx.strokeStyle="rgba(255,255,255,.7)";ctx.lineWidth=2;ctx.strokeRect(sx,sy,shotW,shotH)});
  let y=top+shotH*2+44;ctx.font="13px Lexend, sans-serif";
  if(blurb){ctx.fillStyle="rgba(255,255,255,.9)";const bl=wrapLines(ctx,blurb,W*.88).slice(0,6);bl.forEach(l=>{ctx.fillText(l,W*.06,y);y+=17});y+=10}
  (lines||[]).forEach(([k,v])=>{if(y>Hh-40)return;ctx.fillStyle="rgba(255,255,255,.55)";ctx.fillText(k,W*.06,y);ctx.fillStyle="#fff";const vv=wrapLines(ctx,String(v),W*.6)[0]||"";ctx.fillText(vv,W*.34,y);y+=22});
  return c;
}
// A cover built around a program's own icon (Stremio, Dolphin…): dark tinted sleeve, the icon large, the name below.
const iconCache=new Map();
function iconFront(style,game,iconUrl){
  const key=style.id+"|i|"+game.title+"|"+iconUrl;if(iconCache.has(key))return iconCache.get(key);
  const p=new Promise(resolve=>{
    const im=new Image();im.crossOrigin="anonymous";
    im.onload=()=>{
      const W=420,Hh=Math.round(W/style.ratio),c=document.createElement("canvas");c.width=W;c.height=Hh;const ctx=c.getContext("2d",{willReadFrequently:true});
      // average icon colour for the tint
      const t=document.createElement("canvas");t.width=t.height=8;const tx=t.getContext("2d",{willReadFrequently:true});tx.drawImage(im,0,0,8,8);const d=tx.getImageData(0,0,8,8).data;let r=0,g=0,b=0,n=0;for(let i=0;i<d.length;i+=4){if(d[i+3]<40)continue;r+=d[i];g+=d[i+1];b+=d[i+2];n++}
      if(n){r/=n;g/=n;b/=n}else{r=g=b=90}
      const bg=ctx.createLinearGradient(0,0,0,Hh);bg.addColorStop(0,`rgb(${r*.35|0},${g*.35|0},${b*.35|0})`);bg.addColorStop(1,`rgb(${r*.12|0},${g*.12|0},${b*.12|0})`);ctx.fillStyle=bg;ctx.fillRect(0,0,W,Hh);
      const glow=ctx.createRadialGradient(W/2,Hh*.42,10,W/2,Hh*.42,W*.6);glow.addColorStop(0,`rgba(${r|0},${g|0},${b|0},.45)`);glow.addColorStop(1,"rgba(0,0,0,0)");ctx.fillStyle=glow;ctx.fillRect(0,0,W,Hh);
      const bandH=band(ctx,W,Hh,style,"");
      const size=W*.56,ar=im.width/im.height,iw=ar>=1?size:size*ar,ih=ar>=1?size/ar:size;
      ctx.shadowColor="rgba(0,0,0,.55)";ctx.shadowBlur=30;ctx.shadowOffsetY=12;ctx.drawImage(im,(W-iw)/2,bandH+(Hh-bandH)*.42-ih/2,iw,ih);ctx.shadowColor="transparent";
      ctx.textAlign="center";ctx.textBaseline="alphabetic";let fs=W*.1;ctx.font=`700 ${fs}px Lexend, sans-serif`;while(ctx.measureText(game.title).width>W*.86&&fs>14){fs*=.94;ctx.font=`700 ${fs}px Lexend, sans-serif`}
      ctx.fillStyle="rgba(255,255,255,.96)";ctx.fillText(game.title,W/2,Hh*.84);
      ctx.fillStyle="rgba(255,255,255,.45)";ctx.font=`500 ${W*.04}px Lexend, sans-serif`;ctx.fillText((game.kind==="emulator"?"EMULATOR":"APP").toUpperCase(),W/2,Hh*.84+W*.07);
      // encoded off the screen's thread (a JPEG encode here took tens of ms); kept as a blob: address for the session
      const w=encoder();if(!w){resolve(c.toDataURL("image/jpeg",.9));return}
      createImageBitmap(c).then(bm=>new Promise(res=>{const id=++encId;encCb.set(id,res);w.postMessage({id,bm},[bm])})).then(blob=>{const u=blob?URL.createObjectURL(blob):c.toDataURL("image/jpeg",.9);c.width=c.height=0;resolve(u)},()=>resolve(c.toDataURL("image/jpeg",.9))); /* worker trouble: encode here instead */
    };
    im.onerror=()=>resolve(null);im.src=iconUrl;
  });
  iconCache.set(key,p);return p;
}
// "Surprise me" tile: a holographic card with a big question mark, in the shelf's case style
const randomCache=new Map();
// drawn once ever and kept (it used to be drawn for every shelf at every start: ~40 ms each, all on the first frame)
function randomFront(style){
  if(randomCache.has(style.id))return randomCache.get(style.id);
  const rk="rnd:1:"+[style.id,style.ratio,style.caseColor,style.band&&style.band.bg,style.band&&style.band.fg,style.band&&style.band.label].join("|");
  try{const kept=localStorage.getItem(rk);if(kept){randomCache.set(style.id,kept);return kept}}catch{}
  const W=420,Hh=Math.round(W/style.ratio),c=document.createElement("canvas");c.width=W;c.height=Hh;const ctx=c.getContext("2d",{willReadFrequently:true});
  const bg=ctx.createLinearGradient(0,0,0,Hh);bg.addColorStop(0,"#14121f");bg.addColorStop(1,"#07060c");ctx.fillStyle=bg;ctx.fillRect(0,0,W,Hh);
  const bandH=band(ctx,W,Hh,style,"");
  const sz=W*.58,x=(W-sz)/2,y=bandH+(Hh-bandH)*.42-sz/2,r=sz*.12;
  const foil=ctx.createConicGradient(-.6,x+sz/2,y+sz/2);["#ff4fa3","#ffd54a","#5affc8","#5a8cff","#e65aff","#ff4fa3"].forEach((col,i,a)=>foil.addColorStop(i/(a.length-1),col));
  ctx.save();ctx.shadowColor="rgba(255,255,255,.35)";ctx.shadowBlur=40;ctx.beginPath();ctx.roundRect(x,y,sz,sz,r);ctx.fillStyle=foil;ctx.fill();ctx.restore();
  const shine=ctx.createLinearGradient(x,y,x+sz,y+sz);shine.addColorStop(0,"rgba(255,255,255,.55)");shine.addColorStop(.45,"rgba(255,255,255,0)");shine.addColorStop(.55,"rgba(0,0,0,0)");shine.addColorStop(1,"rgba(0,0,0,.35)");ctx.beginPath();ctx.roundRect(x,y,sz,sz,r);ctx.fillStyle=shine;ctx.fill();
  ctx.textAlign="center";ctx.textBaseline="middle";ctx.fillStyle="rgba(10,8,16,.9)";ctx.font=`800 ${sz*.72}px Lexend, sans-serif`;ctx.fillText("?",x+sz/2,y+sz*.56);
  ctx.fillStyle="rgba(255,255,255,.96)";ctx.font=`700 ${W*.1}px Lexend, sans-serif`;ctx.textBaseline="alphabetic";ctx.fillText("Surprise me",W/2,Hh*.84);
  ctx.fillStyle="rgba(255,255,255,.45)";ctx.font=`500 ${W*.04}px Lexend, sans-serif`;ctx.fillText("SHUFFLE",W/2,Hh*.84+W*.07);
  const url=c.toDataURL("image/jpeg",.92);randomCache.set(style.id,url);
  try{if(!document.fonts||document.fonts.check(`700 ${W*.1}px Lexend`))localStorage.setItem(rk,url)}catch{} // only kept once the real font is in (not a stand-in)
  return url;
}
// the "Add games" tile on an empty console shelf: a plain dashed slot with a plus, made once per console look
const setupCache=new Map();
function setupFront(style){
  if(setupCache.has(style.id))return setupCache.get(style.id);
  const W=420,Hh=Math.round(W/style.ratio),c=document.createElement("canvas");c.width=W;c.height=Hh;const ctx=c.getContext("2d",{willReadFrequently:true});
  const bg=ctx.createLinearGradient(0,0,0,Hh);bg.addColorStop(0,"#1a1d24");bg.addColorStop(1,"#0b0c10");ctx.fillStyle=bg;ctx.fillRect(0,0,W,Hh);
  const bandH=band(ctx,W,Hh,style,"");
  const sz=W*.5,cx=W/2,cy=bandH+(Hh-bandH)*.42;
  ctx.save();ctx.setLineDash([W*.035,W*.025]);ctx.lineWidth=W*.012;ctx.strokeStyle="rgba(255,255,255,.4)";ctx.beginPath();ctx.roundRect(cx-sz/2,cy-sz/2,sz,sz,sz*.14);ctx.stroke();ctx.restore();
  ctx.strokeStyle="rgba(255,255,255,.88)";ctx.lineWidth=W*.03;ctx.lineCap="round";ctx.beginPath();ctx.moveTo(cx-sz*.2,cy);ctx.lineTo(cx+sz*.2,cy);ctx.moveTo(cx,cy-sz*.2);ctx.lineTo(cx,cy+sz*.2);ctx.stroke();
  ctx.textAlign="center";ctx.textBaseline="alphabetic";ctx.fillStyle="rgba(255,255,255,.96)";ctx.font=`700 ${W*.1}px Lexend, sans-serif`;ctx.fillText("Add games",W/2,Hh*.84);
  ctx.fillStyle="rgba(255,255,255,.45)";ctx.font=`500 ${W*.04}px Lexend, sans-serif`;ctx.fillText("OPEN SETTINGS",W/2,Hh*.84+W*.07);
  const url=c.toDataURL("image/jpeg",.92);c.width=c.height=0;if(!document.fonts||document.fonts.check(`700 ${W*.1}px Lexend`))setupCache.set(style.id,url);return url;
}
window.Covers={front,frontAsync,back,backAsync,hasBack,hash,hsl,iconFront,randomFront,setupFront};
})();
