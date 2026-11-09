/* Boxshelf logo clean-up, in a worker so it never holds up the shelf (it took a quarter to half a second per logo on the
   screen's own thread at startup). Same steps as before: knock out a flat background from the edges, clear the holes
   inside letters, crisp anti-aliased edges, light lettering for dark logos, trim the margins. */
const png=c=>c.convertToBlob({type:"image/png"}).then(b=>new FileReaderSync().readAsDataURL(b));
function processLogo(url,im,resolve){try{
    // work at most 1024px wide: the logo is shown small, and this keeps the fill cheap
    const sc=Math.min(1,1024/Math.max(1,im.width)),W=Math.max(1,Math.round(im.width*sc)),Hh=Math.max(1,Math.round(im.height*sc));if(!im.width||!im.height)return resolve({url,dark:false});
    const c=new OffscreenCanvas(W,Hh);const x=c.getContext("2d",{willReadFrequently:true});x.drawImage(im,0,0,W,Hh);const d=x.getImageData(0,0,W,Hh),px=d.data;
    // already transparent somewhere at the edges? leave it alone
    let edgeAlpha=0;const corners=[[0,0],[W-1,0],[0,Hh-1],[W-1,Hh-1]];for(const [cx,cy] of corners)edgeAlpha+=px[(cy*W+cx)*4+3];if(edgeAlpha<4*250)return resolve(finish(c,px,W,Hh));
    // background = average of the corners; flood from every edge pixel, clearing near-matching pixels
    let r=0,g=0,b=0;for(const [cx,cy] of corners){const i=(cy*W+cx)*4;r+=px[i];g+=px[i+1];b+=px[i+2]}r/=4;g/=4;b/=4;
    const near=i=>Math.abs(px[i]-r)+Math.abs(px[i+1]-g)+Math.abs(px[i+2]-b)<72;
    const seen=new Uint8Array(W*Hh),stack=[];const push=k=>{if(!seen[k]){seen[k]=1;stack.push(k)}};for(let i=0;i<W;i++){push(i);push((Hh-1)*W+i)}for(let j=0;j<Hh;j++){push(j*W);push(j*W+W-1)}
    let cleared=0;while(stack.length){const k=stack.pop();if(!near(k*4))continue;px[k*4+3]=0;cleared++;const cx=k%W,cy=(k-cx)/W;if(cx>0)push(k-1);if(cx<W-1)push(k+1);if(cy>0)push(k-W);if(cy<Hh-1)push(k+W)}
    if(cleared<W*Hh*.02)return resolve({url,dark:false,ar:W/Hh}); // nothing much to remove: it was a real picture, not a flat backdrop
    // the lettering's colour: the commonest colour well away from the backdrop
    let INK=null;{const bucket=new Map();let best=null,bestN=0;
      for(let k=0;k<W*Hh;k+=2){const i=k*4;if(px[i+3]===0)continue;const dd=Math.abs(px[i]-r)+Math.abs(px[i+1]-g)+Math.abs(px[i+2]-b);if(dd<150)continue;const key=(px[i]>>4)<<8|(px[i+1]>>4)<<4|(px[i+2]>>4),v=(bucket.get(key)||0)+1;bucket.set(key,v);if(v>bestN){bestN=v;best=key}}
      if(best!=null){let sr=0,sg=0,sb=0,sn=0;for(let k=0;k<W*Hh;k+=2){const i=k*4;if(px[i+3]===0)continue;if(((px[i]>>4)<<8|(px[i+1]>>4)<<4|(px[i+2]>>4))!==best)continue;sr+=px[i];sg+=px[i+1];sb+=px[i+2];sn++}
        const ink=[sr/sn,sg/sn,sb/sn],v=[ink[0]-r,ink[1]-g,ink[2]-b];INK={ink,v,vv:v[0]*v[0]+v[1]*v[1]+v[2]*v[2]||1}}}
    // how far a colour is from being a blend of backdrop and lettering
    const offLine=i=>{if(!INK)return 999;const {v,vv}=INK,c0=px[i]-r,c1=px[i+1]-g,c2=px[i+2]-b,t=(c0*v[0]+c1*v[1]+c2*v[2])/vv,e0=c0-t*v[0],e1=c1-t*v[1],e2=c2-t*v[2];return Math.sqrt(e0*e0+e1*e1+e2*e2)};
    // the backdrop showing through the letters (the insides of A, B, O, e…): enclosed patches of the backdrop colour
    // whose surroundings are the lettering (its colour, or blends of it into the backdrop) are holes and go too. Other surroundings mean
    // it's part of the artwork (the GameCube cube's pale faces), so those stay.
    {const q=[];const lim=W*Hh*.15;const hole=[];
      for(let k0=0;k0<W*Hh;k0++){if(seen[k0]||px[k0*4+3]===0||!near(k0*4))continue;
        const pts=[];q.length=0;q.push(k0);seen[k0]=1;let satSum=0,satN=0;
        while(q.length){const k=q.pop();pts.push(k);const cx=k%W,cy=(k-cx)/W;
          for(const [n,dx,dy] of [[cx>0?k-1:-1,-1,0],[cx<W-1?k+1:-1,1,0],[cy>0?k-W:-1,0,-1],[cy<Hh-1?k+W:-1,0,1]]){if(n<0)continue;
            if(near(n*4)&&px[n*4+3]!==0){if(!seen[n]){seen[n]=1;q.push(n)}}
            else if(px[n*4+3]!==0){const fx=cx+dx*4,fy=cy+dy*4;const f=fx>=0&&fy>=0&&fx<W&&fy<Hh?fy*W+fx:n;if(px[f*4+3]!==0){satSum+=offLine(f*4);satN++}}}} // judged a few pixels further in, past the blurred rim
        if(pts.length<lim&&satN&&satSum/satN<30)for(const k of pts){px[k*4+3]=0;hole.push(k)}}
    }
    // Crisp edges: every pixel along a cut (2 px either side) that is a blend of the backdrop and the lettering's
    // colour becomes that colour with the right see-through amount, the way a properly cut-out logo is anti-aliased.
    // (The old "soften" painted the backdrop's own colour there, which left a pale rim round dark letters.) Pixels
    // that aren't such a blend (the GameCube cube's purple, say) are left exactly as they are.
    if(INK){const {ink,v,vv}=INK;{
        const A=new Uint8Array(W*Hh);for(let k=0;k<W*Hh;k++)A[k]=px[k*4+3]===0?1:0;
        const band=new Uint8Array(W*Hh);for(let k=0;k<W*Hh;k++){if(!A[k])continue;const cx=k%W,cy=(k-cx)/W;for(let dy=-2;dy<=2;dy++)for(let dx=-2;dx<=2;dx++){const xx=cx+dx,yy=cy+dy;if(xx>=0&&yy>=0&&xx<W&&yy<Hh)band[yy*W+xx]=1}}
        for(let k=0;k<W*Hh;k++){if(!band[k])continue;const i=k*4,c=[px[i]-r,px[i+1]-g,px[i+2]-b],t=(c[0]*v[0]+c[1]*v[1]+c[2]*v[2])/vv;
          const e0=c[0]-t*v[0],e1=c[1]-t*v[1],e2=c[2]-t*v[2];if(e0*e0+e1*e1+e2*e2>40*40)continue;
          const al=Math.max(0,Math.min(1,t));px[i]=ink[0];px[i+1]=ink[1];px[i+2]=ink[2];px[i+3]=Math.round((A[k]?255:px[i+3])*al)}}} /* cut pixels on the rim get their share back too, so the edge is smooth, not stepped */
    // Black or dark-grey lettering would vanish on Boxshelf's dark screens, and the white glow it used to get round it
    // looked fuzzy. Use the logo's reversed (light) version instead, the way consoles print it on dark packaging: the
    // dark lettering turns near-white, coloured parts (the GameCube cube) stay as they are.
    if(INK){const [ir,ig,ib]=INK.ink,lum=ir*.3+ig*.59+ib*.11,sat=Math.max(ir,ig,ib)-Math.min(ir,ig,ib);
      if(lum<90&&sat<30)for(let k=0;k<W*Hh;k++){const i=k*4;if(px[i+3]===0)continue;if(Math.abs(px[i]-ir)+Math.abs(px[i+1]-ig)+Math.abs(px[i+2]-ib)<70){px[i]=242;px[i+1]=242;px[i+2]=246}}}
    x.putImageData(d,0,0);resolve(finish(c,px,W,Hh));
  }catch{resolve({url,dark:false,ar:im.width&&im.height?im.width/im.height:2.2})}
  // trim empty margins (people's PNGs often carry a lot of padding, which made some logos tiny next to others),
  // measure the aspect so every logo can be shown at the same visual weight, and flag dark lettering for a halo
  function finish(c,px,W,Hh){
    let x0=W,y0=Hh,x1=0,y1=0;for(let k=0;k<W*Hh;k++){if(px[k*4+3]<20)continue;const cx=k%W,cy=(k-cx)/W;if(cx<x0)x0=cx;if(cx>x1)x1=cx;if(cy<y0)y0=cy;if(cy>y1)y1=cy}
    let lum=0,ln=0;for(let k=0;k<W*Hh;k+=3){if(px[k*4+3]<200)continue;lum+=(px[k*4]*.3+px[k*4+1]*.59+px[k*4+2]*.11);ln++}const dark=ln>0&&lum/ln<110;
    if(x1>x0&&y1>y0){const t=new OffscreenCanvas(x1-x0+1,y1-y0+1);t.getContext("2d").drawImage(c,x0,y0,t.width,t.height,0,0,t.width,t.height);return png(t).then(u=>({url:u,dark,ar:t.width/t.height}))}
    return png(c).then(u=>({url:u,dark,ar:W/Hh}));
  }
}

self.onmessage=e=>{const {id,url,bmp}=e.data;processLogo(url,bmp,v=>Promise.resolve(v).then(res=>{try{bmp.close()}catch{}self.postMessage({id,res})},err=>{try{bmp.close()}catch{}self.postMessage({id,res:{url,dark:false}})}))};
