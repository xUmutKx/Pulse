// Boxshelf shelf screen: 3D box carousel, controller input, details, boot and launch.
(function(){
"use strict";
const api=window.boxshelf;
const {hash,hsl}=window.Covers;
const $=id=>document.getElementById(id);
const safe=fn=>{try{return fn()}catch(e){return null}};
const app=$("app"),track=$("track");

/* ---------- per-shelf look ---------- */
const STYLE={
  gc:{id:"gc",rc:0.03,re:0.2,hue:258,ratio:.712,depth:.105,edge:.022,caseColor:"#16161b",caseSide:"#0b0b0e",spineInk:"#e8e8f0",band:{bg:"#26262e",fg:"#c3c3cf",label:"GAMECUBE"},spineBand:{bg:"#2c2c35",fg:"#c3c3cf",label:"GCN"},tint:["#3a2a86","#120d2c"],glow:"#9d8cff"},
  wii:{id:"wii",rc:0.04,re:0.22,hue:205,ratio:0.711,depth:0.104,edge:.03,caseColor:"#f2f3f5",caseSide:"#d9dbe0",spineInk:"#4a4f58",band:{bg:"#ffffff",fg:"#8e949e",label:"Wii"},spineBand:{bg:"#ffffff",fg:"#8e949e",label:"Wii"},tint:["#2f7bff","#0b2a7a"],glow:"#9fd0ff"},
  wiiu:{id:"wiiu",rc:0.04,re:0.22,hue:194,ratio:0.711,depth:0.104,edge:.03,caseColor:"#1d8fd1",caseSide:"#146ea6",spineInk:"#ffffff",band:{bg:"#1d8fd1",fg:"#ffffff",label:"Wii U"},spineBand:{bg:"#ffffff",fg:"#1d8fd1",label:"Wii U"},tint:["#0f6fb0","#04203a"],glow:"#6fd0ff"},
  sw:{id:"sw",rc:0.06,re:0.28,hue:355,ratio:0.612,depth:0.096,edge:.035,caseColor:"#d8101f",caseSide:"#a30a16",spineInk:"#ffffff",band:{bg:"#d8101f",fg:"#ffffff",label:"SWITCH"},spineBand:{bg:"#d8101f",fg:"#ffffff",label:"SW"},tint:["#7a2bd6","#2a0b4e"],glow:"#ff6470"},
  sw2:{id:"sw2",rc:0.06,re:0.28,hue:352,ratio:0.612,depth:0.096,edge:.035,caseColor:"#e60012",caseSide:"#ad000d",spineInk:"#ffffff",band:{bg:"#e60012",fg:"#ffffff",label:"SWITCH 2"},spineBand:{bg:"#e60012",fg:"#ffffff",label:"SW2"},tint:["#16608f","#071a2e"],glow:"#ff5a66"}, // Switch 2: the brighter red case, "SWITCH 2" band; a blue backdrop (the Joy-Con 2 accents) tells it from the Switch shelf
  ps4:{id:"ps4",rc:0.065,re:0.26,hue:216,ratio:0.787,depth:0.089,edge:.03,caseColor:"#1d4ed8",caseSide:"#153a9e",spineInk:"#ffffff",band:{bg:"#1d4ed8",fg:"#ffffff",label:"PS4"},spineBand:{bg:"#1d4ed8",fg:"#ffffff",label:"PS4"},tint:["#1e3a8a","#0b1533"],glow:"#6aa6ff"},
  ps5:{id:"ps5",rc:0.065,re:0.26,hue:220,ratio:0.787,depth:0.089,edge:.03,caseColor:"#2b62d9",caseSide:"#1f4cb3",spineInk:"#111318",band:{bg:"#ffffff",fg:"#111318",label:"PS5"},spineBand:{bg:"#ffffff",fg:"#111318",label:"PS5"},tint:["#24345e","#0a0f1f"],glow:"#9ec2ff"}, // PS5: the blue case, a white band with the black PS5 logo
  ps3:{id:"ps3",rc:0.065,re:0.26,hue:221,ratio:0.787,depth:0.1,edge:.03,caseColor:"#203553",caseSide:"#16263f",spineInk:"#ffffff",band:{bg:"#0c0d10",fg:"#ffffff",label:"PS3"},spineBand:{bg:"#0c0d10",fg:"#ffffff",label:"PS3"},tint:["#24406e","#090f1e"],glow:"#7ab4ff"},
  ps2:{id:"ps2",rc:0.035,re:0.2,hue:226,ratio:0.711,depth:0.104,edge:.03,caseColor:"#0f1d55",caseSide:"#0a1440",spineInk:"#ffffff",band:{bg:"#0f1d55",fg:"#dfe6ff",label:"PS2"},spineBand:{bg:"#0f1d55",fg:"#dfe6ff",label:"PS2"},tint:["#1b2a7a","#070b24"],glow:"#8fa6ff"},
  x360:{id:"x360",rc:0.04,re:0.22,hue:115,ratio:0.711,depth:0.104,edge:.03,caseColor:"#1a7f1a",caseSide:"#0f5a12",spineInk:"#ffffff",band:{bg:"#107c10",fg:"#ffffff",label:"XBOX 360"},spineBand:{bg:"#107c10",fg:"#ffffff",label:"X360"},tint:["#145a16","#061a07"],glow:"#7ee87e"},
  psp:{id:"psp",rc:0.055,re:0.24,hue:235,ratio:0.589,depth:0.14,edge:.03,caseColor:"#101014",caseSide:"#07070a",spineInk:"#e8e8f0",band:{bg:"#0a0a0d",fg:"#ffffff",label:"PSP"},spineBand:{bg:"#0a0a0d",fg:"#ffffff",label:"PSP"},tint:["#4a4f63","#0b0c12"],glow:"#cfd6ff"},
  n3ds:{id:"n3ds",rc:0.05,re:0.24,hue:352,ratio:1.127,depth:0.1,h:.64,edge:.03,caseColor:"#f4f4f6",caseSide:"#dcdde2",spineInk:"#3a3d45",band:{bg:"#ffffff",fg:"#d40f2a",label:"NINTENDO 3DS"},spineBand:{bg:"#ffffff",fg:"#d40f2a",label:"3DS"},tint:["#c0182e","#3a0710"],glow:"#ff7a8a"},
  pc:{id:"pc",rc:0.02,re:0.16,hue:207,ratio:.667,depth:.07,edge:.015,caseColor:"#1b1f27",caseSide:"#11141a",spineInk:"#dfe8f3",band:null,spineBand:{bg:"#11141a",fg:"#8fb4d9",label:"PC"},tint:["#1d4a6e","#0a121c"],glow:"#8fc3ff"},
  // (beta.4) Custom: PC ports, recomps and games added by hand (Quiver's too); the PC case in a warm tint
  custom:{id:"custom",rc:0.02,re:0.16,hue:28,ratio:.667,depth:.07,edge:.015,caseColor:"#211b17",caseSide:"#15110e",spineInk:"#f3e6da",band:null,spineBand:{bg:"#15110e",fg:"#e0a878",label:"PC"},tint:["#6e3f1d","#1c120a"],glow:"#ffbf8a"},
  home:{id:"home",rc:0.04,re:0.22,hue:268,ratio:.71,depth:.09,edge:.025,caseColor:"#1b1d27",caseSide:"#101218",spineInk:"#e8e8f0",band:{bg:"#1b1d27",fg:"#c9cfe0",label:"HOME"},spineBand:{bg:"#2a2d3a",fg:"#c9cfe0",label:"HOME"},tint:["#3b2f6e","#0a0b16"],glow:"#c9b8ff"},
  media:{id:"media",rc:0.03,re:0.18,hue:285,ratio:.71,depth:.06,edge:.02,caseColor:"#1a1c24",caseSide:"#0e0f14",spineInk:"#e8e8f0",band:{bg:"#1a1c24",fg:"#9aa3b8",label:"APP"},spineBand:{bg:"#2a2d38",fg:"#c8cede",label:"APP"},tint:["#2a3550","#0a0d16"],glow:"#b9a6ff"}
};
const WORDMARKS={home:["Home","Continue & favorites"],gc:["GameCube","Home console"],wii:["Wii","Motion console"],wiiu:["Wii U","Home console"],sw:["Switch","Hybrid console"],sw2:["Switch 2","Hybrid console"],psp:["PSP","Handheld"],ps3:["PS3","Home console"],ps4:["PS4","Home console"],ps5:["PS5","Home console"],x360:["Xbox 360","Home console"],n3ds:["3DS","Handheld"],ps2:["PS2","Home console"],pc:["PC","Steam, Epic & more"],custom:["Custom","PC ports & recomps"],media:["Media","Streaming"]};
if(window.PULSE_WORDMARKS)Object.assign(WORDMARKS,window.PULSE_WORDMARKS); /* Pulse: the heading words of the one shelf of games */
if(window.BS_SYSTEMS_EXTRA){Object.assign(STYLE,BS_SYSTEMS_EXTRA.style||{});Object.assign(WORDMARKS,BS_SYSTEMS_EXTRA.wordmarks||{})} // extra shelves (renderer/systems-extra.js)
const APP_HUE={stremio:265,nuvio:200};

/* ---------- state ---------- */
// Android handheld (the AYN Thor): a phone GPU and CPU. A few shelf paths below take a cheaper route there; Windows never does
const ANDROID=!!(window.bsPlatform&&bsPlatform.os==="android");
let fxOn=true;
let glowEls=null,lastGlow="",letterAnim=null;
let reduceMotion=false; // Settings → Display → Reduce motion (on top of the system's own setting)
const PADMAP={swap:false};window.BS_PAD={confirm:0,back:1}; // Settings → Controller → Confirm button (see applyConfirm)
// Scroll speed: how soon a held direction starts repeating on the shelf (and the museum wall), and how fast it goes
const SCROLL={relaxed:{f:1.4,kbd:95},normal:{f:1,kbd:0},fast:{f:.7,kbd:0}};let scrollCfg=SCROLL.normal,kbdMoveAt=0;
const reducedMQ=matchMedia("(prefers-reduced-motion: reduce)"),reduced=()=>reduceMotion||reducedMQ.matches;
let shelves=[],sysIdx=0,sel=0,flipped=false,spin=0,mode="browse",inputMode="kbd",soundOn=true,touchBack=false; /* touchBack: see BS_TOUCH.back */
let slots=[],bw=240,bh=340,bd=24,OS=1,themes={},cfgInfo=null;
const cur=()=>shelves[sysIdx];
const st=()=>STYLE[(cur()||{}).id]||STYLE.media; // no shelves yet (empty library): the neutral look, never a TypeError
const shelfFor=g=>(g&&g._shelf!=null&&allShelves[g._shelf])||cur();
const stOf=g=>STYLE[shelfFor(g).id]||STYLE.media;
let allShelves=[];
// Android (the Thor's small screen): the console bar only lists shelves that have games (plus Home and Apps), with short
// names, and glides with a transform instead of scrolling (see placePill). Windows keeps every shelf as before.
const ON_ANDROID=!!(window.bsPlatform&&bsPlatform.os==="android");
const TAB_SHORT={psx:"PS1",gba:"GBA",gbc:"GBC",gb:"GB",mastersystem:"SMS",gamegear:"Game Gear",pcengine:"TG-16",megadrive:"Genesis",neogeo:"Neo Geo"};
let tabGeo=null,tabOff=0,tabDragged=false; // the Android bar's measured tabs, how far its strip has slid, a drag just ended
const hasGames=sh=>sh.kind!=="console"||(sh.games||sh.items||[]).length>0;
const rawItem=()=>cur()&&cur().items[sel];
// while a Surprise card is showing its pick, the shelf treats it as that game (back, spine, inspect, details)
// an empty console (or Steam) shelf: A opens that console's page in Settings, to add a games folder
const emptyShelf=()=>{const s=cur();return s&&(s.kind==="console"||s.kind==="steam")&&!(s.games||[]).length&&!s._hiddenN?s:null};
const curGame=()=>{const g=rawItem();return g&&g.kind==="random"&&g.pick&&g.revealed?g.pick.game:g};
const pickShelf=()=>{const g=rawItem();return g&&g.kind==="random"&&g.pick&&g.revealed?(g.pick.sh||shelves[g.pick.shelf]):shelfFor(g)};

const saved=safe(()=>JSON.parse(localStorage.getItem("boxshelf-ui")||"{}"))||{};
const save=()=>safe(()=>localStorage.setItem("boxshelf-ui",JSON.stringify({sys:cur()&&cur().id,sel})));

/* ---------- helpers ---------- */
function fmtSize(b){if(!b)return"";const u=["B","KB","MB","GB","TB"];let i=0;while(b>=1024&&i<4){b/=1024;i++}return (i>=3?b.toFixed(1):Math.round(b))+" "+u[i]}
function fmtDate(sec){if(!sec)return"Never";const d=new Date(sec*1000),days=Math.floor((Date.now()-d)/864e5);if(days<1)return"Today";if(days<2)return"Yesterday";if(days<7)return days+" days ago";return d.toLocaleDateString(undefined,{year:"numeric",month:"short",day:"numeric"})}
const fmtDay=sec=>fmtDate(sec).replace(/^(Today|Yesterday|Never)$/,w=>w.toLowerCase()); // the same, mid-sentence ("played today")
function quote(p){return /\s/.test(p)?`"${p}"`:p}
function argsPreview(tpl,rom){return (tpl||"").replace(/\{rom\}/g,rom||"")}

// Turn scanned shelves into display items with seeds, hues and an emulator tile.
// Games you've hidden (Details → Hide from shelf; Settings → Hidden games) never reach a shelf, so Home, the museum,
// Shuffle, attract mode and letter jumps all skip them; the scan itself stays whole (lastLib) for the Settings list.
let lastLib=null;
function hiddenSet(){const h=cfgInfo&&cfgInfo.config.ui.hidden;return new Set(Array.isArray(h)?h.map(String):[])}
function prepare(list){
  const hid=hiddenSet();
  const out=list.map(s0=>{
    const all=s0.games||[],games=hid.size?all.filter(g=>!hid.has(String(g.id))):all,s={...s0,games,_hiddenN:all.length-games.length};
    const items=games.map(g=>{const seed=hash(g.title);return {...g,seed,hue:g.kind==="app"?(APP_HUE[g.appId]||seed%360):seed%360,scene:g.kind==="app"?"media":undefined}});
    if(s.kind==="console"&&s.hasEmulator){
      const t=`Open ${s.emulatorName}`;const seed=hash(t+s.id);
      items.unshift({kind:"emulator",id:"emu-"+s.id,title:t,seed,hue:seed%360,scene:"media",art:{icon:s.emulatorIcon||null}});
    }
    // no games yet: an "Add games" tile in front, which opens this console's page in Settings
    if(s.kind==="console"&&!all.length){const seed=hash("setup"+s.id);items.unshift({kind:"setup",id:"setup-"+s.id,title:"Add games",seed,hue:210,scene:"media",art:{front:Covers.setupFront(STYLE[s.id]||STYLE.media)}})}
    // (beta.7) the Custom shelf ends in an "Add games" tile: Settings → Custom games → Add games (several at once, a folder, Playnite)
    if(s.id==="custom"&&!ANDROID){const seed=hash("setup-custom");items.push({kind:"setup",addGames:true,tail:true,id:"setup-custom",title:"Add games",seed,hue:210,scene:"media",art:{front:Covers.setupFront(STYLE.custom||STYLE.media)}})}
    // "Surprise me" tile at the front of every shelf that has games on it
    if((s.kind==="console"||s.kind==="steam")&&s.games.length>1){
      const seed=hash("random"+s.id),style=STYLE[s.id]||STYLE.media;
      items.unshift({kind:"random",id:"rnd-"+s.id,title:"Surprise me",seed,hue:300,scene:"media",art:{front:Covers.randomFront(style)}});
    }
    return {...s,items};
  });
  return orderShelves(out);
}
// Your shelf order (Settings → Display → Shelf order); shelves you haven't placed keep the default order, after the rest
function orderShelves(list){
  const ord=(cfgInfo&&cfgInfo.config.ui.shelfOrder)||[];if(!ord.length)return list;
  const pos=s=>{const i=ord.indexOf(s.id);return i<0?1000+list.indexOf(s):i};
  return list.slice().sort((a,b)=>pos(a)-pos(b));
}
// Home shelf: the games you played most recently (Boxshelf's own history), then your favorites — across every shelf.
// Items are the real game objects borrowed from their shelves, so Details, Inspect and art all just work.
const HOME={id:"home",kind:"home",name:"Home",games:[],items:[],hasEmulator:false};
const COLS=new Map(); // (beta.7) the collection shelves, kept by id so a rebuild doesn't lose your place on one
function favSet(){return new Set((cfgInfo&&cfgInfo.config.ui.favorites)||[])}
let favCache=null,favCacheSrc=null;
function isFav(g){if(!g)return false;const src=cfgInfo&&cfgInfo.config.ui.favorites;if(src!==favCacheSrc||!favCache){favCacheSrc=src;favCache=new Set(src||[])}return favCache.has(g.id)}
/* ---------- sort & filter: reorganise every shelf (and the museum wall) ----------
   sh._base keeps the scanner's order; sh.items is rebuilt from it: tiles (Surprise me, the emulator) first, then the
   games that pass the filter, in the chosen order. groupOf() names the section a game sits in, for L1/R1 jumps, the
   jump flash and the museum's brass divider tabs. */
const SORTS=[["az","A–Z"],["year","Release year"],["recent","Last played"],["most","Most played"],["rating","Rating"],["genre","Genre"]];
const FILTERS=[["all","All games"],["fav","Favorites"],["played","Played"],["unplayed","Not played yet"]];
const SORT_WORD={az:"",year:"by year",recent:"by last played",most:"by play time",rating:"by rating",genre:"by genre"};
let sortBy="az",filterBy="all";
const yearOf=g=>{const i=g.info||{};const y=String(i.release||i.year||"").slice(0,4);return /^\d{4}$/.test(y)?+y:null};
const genreOf=g=>((g.info&&g.info.genres)||"").split(";")[0].trim()||null;
const ratingOf=g=>{const i=g.info||{};return i.rating?+i.rating:i.metacritic?i.metacritic/20:null};
const histOf=g=>HIST[g.id]||(g.kind==="steam"&&(g.minutes||g.lastPlayed)?{count:g.minutes?1:0,ms:(g.minutes||0)*6e4,last:(g.lastPlayed||0)*1000}:null); // Steam keeps its own play time
function groupOf(g){
  const h=histOf(g),days=h&&h.last?(Date.now()-h.last)/864e5:null,hrs=h&&h.ms?h.ms/36e5:0,r=ratingOf(g);
  switch(sortBy){
    case"az":{const c=(g.title[0]||"#").toUpperCase();return /[A-Z]/.test(c)?c:"#"}
    case"year":return yearOf(g)?String(yearOf(g)):"Undated";
    case"genre":return genreOf(g)||"Other";
    case"recent":return days==null?"Never":days<7?"This week":days<31?"This month":days<365?"This year":"Earlier";
    case"most":return !hrs?"Never":hrs>=20?"20 h +":hrs>=5?"5–20 h":hrs>=1?"1–5 h":"Under 1 h";
    case"rating":return r==null?"Unrated":r>=4.5?"★ 4.5+":r>=4?"★ 4+":r>=3?"★ 3+":"★ < 3";
  }return null;
}
// (beta.7) more filters, made from your own library: 2+ players, co-op, each decade it has, its most common genres (12)
const playersOf=g=>{const m=String((g.info&&g.info.players)||"").match(/\d+/g);return m?Math.max(...m.map(Number)):null};
const coopOf=g=>{const c=g.info&&g.info.coop;return !!c&&!/^(no|false|0)$/i.test(String(c).trim())};
const genresOf=g=>((g.info&&g.info.genres)||"").split(";").map(s=>s.trim()).filter(Boolean);
let flCache=null;
function filterList(){
  const games=allShelves.flatMap(s=>s.kind==="home"?[]:(s._base||s.items||[]).filter(isGame));
  if(flCache&&flCache.n===games.length&&flCache.a===allShelves)return flCache.list;
  const dec=new Set(),gen=new Map();let multi=false,coop=false;
  for(const g of games){const y=yearOf(g);if(y)dec.add(Math.floor(y/10)*10);for(const x of genresOf(g))gen.set(x,(gen.get(x)||0)+1);if((playersOf(g)||0)>=2)multi=true;if(coopOf(g))coop=true}
  const list=[...FILTERS];if(multi)list.push(["multi","2+ players"]);if(coop)list.push(["coop","Co-op"]);
  [...dec].sort((a,b)=>a-b).forEach(d=>list.push(["dec:"+d,d+"s"]));
  [...gen].sort((a,b)=>b[1]-a[1]).slice(0,12).map(x=>x[0]).sort((a,b)=>a.localeCompare(b)).forEach(n=>list.push(["genre:"+n,n]));
  flCache={n:games.length,a:allShelves,list};return list;
}
const filterName=v=>(filterList().find(f=>f[0]===v)||FILTERS[0])[1];
function filterFn(v){
  const base={fav:g=>isFav(g),played:g=>!!(histOf(g)&&histOf(g).count),unplayed:g=>!(histOf(g)&&histOf(g).count),multi:g=>(playersOf(g)||0)>=2,coop:coopOf}[v];if(base)return base;
  let m=/^dec:(\d{4})$/.exec(v||"");if(m){const d=+m[1];return g=>{const y=yearOf(g);return !!y&&y>=d&&y<d+10}}
  m=/^genre:(.+)$/.exec(v||"");if(m){const n=m[1];return g=>genresOf(g).includes(n)}
  return ()=>true;
}
function arrange(){
  const keep=filterFn(filterBy);
  allShelves.forEach(sh=>{if(sh.kind==="home"||!sh.items)return;if(!sh._base)sh._base=sh.items.slice();
    const idx=new Map(sh._base.map((g,i)=>[g,i])),base=(a,b)=>idx.get(a)-idx.get(b);
    const by=(f,desc)=>(a,b)=>{const x=f(a),y=f(b);if(x==null&&y==null)return base(a,b);if(x==null)return 1;if(y==null)return -1;return (desc?y-x:x-y)||base(a,b)};
    const cmp={year:by(yearOf),recent:by(g=>histOf(g)&&histOf(g).last||null,true),most:by(g=>histOf(g)&&histOf(g).ms||null,true),rating:by(ratingOf,true),
      genre:(a,b)=>{const x=genreOf(a),y=genreOf(b);if(x===y)return base(a,b);if(!x)return 1;if(!y)return -1;return x.localeCompare(y)}}[sortBy]||base;
    sh.items=[...sh._base.filter(g=>!isGame(g)&&!g.tail),...sh._base.filter(isGame).filter(keep).sort(cmp),...sh._base.filter(g=>g.tail)]; // (beta.7: a tail tile, the Custom shelf's Add games, stays last)
  });
}
// re-sort/filter and rebuild, keeping the selected game in view when it's still on the shelf
function applyArrange(){
  const keepG=curGame();arrange();buildHome();if(!cur())return;
  const i=keepG?cur().items.indexOf(keepG):-1;sel=i>=0?i:Math.max(0,cur().items.findIndex(isGame));if(sel<0)sel=0;flipped=false;spin=0;spinT=0;build();
}
/* the Sort panel: two rows of options, applied live; ←/→ picks, ↑/↓ changes row, A/B/Esc closes */
let spRow=0,spSaveT=0;
function renderSort(){
  const mk=(host,list,cur,row)=>{const h=$(host);h.innerHTML="";list.forEach(([v,l])=>{const b=document.createElement("button");b.type="button";b.className="sp-opt"+(v===cur?" on":"");b.textContent=l;b.addEventListener("click",()=>{spRow=row;pickSort(row,v)});h.append(b)})};
  mk("spSort",SORTS,sortBy,0);mk("spFilter",filterList(),filterBy,1);
  document.querySelectorAll("#sortPanel .sp-row").forEach(r=>r.classList.toggle("active",+r.dataset.row===spRow));
  const s=cur(),n=s&&s.kind!=="home"?s.items.filter(isGame).length:null;
  $("spFoot").textContent=(s&&s.kind==="home"?"Home keeps its own order. ":n!=null?`${n} game${n===1?"":"s"} on ${s.name} · `:"")+(showPad()?`${padWord("L1")} / ${padWord("R1")} jump between sections`:"Z / C jump between sections")+" · applies to every shelf and the museum";
}
function pickSort(row,v){
  if(row===0){if(sortBy===v)return;sortBy=v}else{if(filterBy===v)return;filterBy=v}
  play_("tick",{dir:1});applyArrange();renderSort();
  clearTimeout(spSaveT);spSaveT=setTimeout(()=>{spSaveT=0;if(!cfgInfo)return;cfgInfo.config.ui.sort=sortBy;cfgInfo.config.ui.filter=filterBy;api.saveConfig(cfgInfo.config).catch(()=>{})},500);
}
function sortStep(d){const list=spRow===0?SORTS:filterList(),curV=spRow===0?sortBy:filterBy;const i=list.findIndex(x=>x[0]===curV);pickSort(spRow,list[(i+d+list.length)%list.length][0])}
function openSort(){if(sysBusy()||(mode!=="browse"&&mode!=="details"))return;closeDetails(true);mode="sort";spRow=0;renderSort();$("sortPanel").classList.add("on");$("sortPanel").setAttribute("aria-hidden","false");sfx.flip()}
function closeSort(){if(mode!=="sort")return;mode="browse";$("sortPanel").classList.remove("on");$("sortPanel").setAttribute("aria-hidden","true");sfx.back()}
function sortKey(k){if(k==="ArrowLeft")sortStep(-1);else if(k==="ArrowRight")sortStep(1);else if(k==="ArrowUp"||k==="ArrowDown"){spRow=1-spRow;play_("tick",{dir:k==="ArrowDown"?1:-1});renderSort()}else if(k==="Escape"||k==="Enter"||k==="Backspace"||k==="g"||k==="G")closeSort();else if(k==="z"||k==="Z"||k==="c"||k==="C"){closeSort();letter(k.toLowerCase()==="c"?1:-1)}else return false;return true}
$("sortBtn").addEventListener("click",()=>mode==="sort"?closeSort():(setInput("pointer"),openSort()));
$("sortPanel").addEventListener("click",e=>{if(e.target===$("sortPanel"))closeSort()});

/* volume beside the clock: the icon mutes, the slider sets the level; - / = on the keyboard nudge it */
let volSaveT=0,volPrev=.7,volShowT=0;
// the museum TV follows the volume (shelf, quick menu, Settings); try: tvVol is defined further down
function musTvVol(){try{if(window.Museum&&Museum.tvVolume)Museum.tvVolume(tvVol())}catch{}}
function curVol(){return cfgInfo&&cfgInfo.config.ui.volume!=null?+cfgInfo.config.ui.volume:.7}
function setVol(v,announce){v=Math.max(0,Math.min(1,Math.round(v*20)/20));if(!cfgInfo)return;cfgInfo.config.ui.volume=v;if(v>0)volPrev=v;
  if(window.Sound)Sound.set({volume:v,enabled:soundOn});paintVol();if(announce){const el=$("vol");el.classList.add("show");clearTimeout(volShowT);volShowT=setTimeout(()=>el.classList.remove("show"),1400)}
  clearTimeout(volSaveT);volSaveT=setTimeout(()=>{volSaveT=0;api.saveConfig(cfgInfo.config).catch(()=>{})},500)}
function paintVol(){musTvVol();const v=curVol(),r=$("volRange");r.value=String(v);r.style.setProperty("--p",(v*100)+"%");const el=$("vol");el.classList.toggle("muted",v===0);el.classList.toggle("low",v>0&&v<.45);el.title=`Volume ${Math.round(v*100)}%`}
$("volRange").addEventListener("input",e=>setVol(+e.target.value));
$("volRange").addEventListener("change",()=>play_("tick",{dir:1}));
$("volBtn").addEventListener("click",()=>{const v=curVol();setVol(v>0?0:(volPrev||.7));if(curVol()>0)play_("tick",{dir:1})});

function buildHome(){
  const favs=favSet(),seen=new Set(),items=[];
  const all=[];allShelves.forEach((sh,i)=>{if(sh.kind==="home")return;(sh._base||sh.items).forEach(g=>{if(isGame(g)){g._shelf=i;all.push(g)}})});
  // (Android 0.3.2: Home shows the most played games, then the favourites; Windows keeps the most recent first)
  const hv=g=>HIST[g.id]||{},rank=ON_ANDROID?(a,b)=>(hv(b).ms||0)-(hv(a).ms||0)||(hv(b).count||0)-(hv(a).count||0)||(hv(b).last||0)-(hv(a).last||0):(a,b)=>HIST[b.id].last-HIST[a.id].last;
  all.filter(g=>HIST[g.id]&&HIST[g.id].count).sort(rank).slice(0,12).forEach(g=>{seen.add(g.id);items.push(g)});
  all.filter(g=>favs.has(g.id)&&!seen.has(g.id)).sort((a,b)=>a.title.localeCompare(b.title)).forEach(g=>items.push(g));
  HOME.items=items;HOME.games=items;
  // (beta.7) your collections (Details → Collections; cfg.ui.collections [{id,name,ids}]): shelves of your own right after
  // Home, mixing consoles the way Home does (each case keeps its own console's look); the games in the order added
  const cols=[],byId=new Map(all.map(g=>[String(g.id),g]));
  for(const c of ((cfgInfo&&cfgInfo.config&&cfgInfo.config.ui&&cfgInfo.config.ui.collections)||[])){
    if(!c||!c.id||!Array.isArray(c.ids))continue;const its=c.ids.map(id=>byId.get(String(id))).filter(Boolean);if(!its.length)continue;
    const id="col-"+c.id;STYLE[id]=STYLE[id]||STYLE.home||STYLE.media;WORDMARKS[id]=[String(c.name||"Collection"),"Your collection"];
    let sh=COLS.get(id);if(!sh){sh={id,kind:"home",col:true,name:"",games:[],items:[],hasEmulator:false};COLS.set(id,sh)}
    sh.name=String(c.name||"Collection");sh.items=its;sh.games=its;cols.push(sh)}
  const keep=cur();
  let list=allShelves.filter(s=>s!==HOME);
  // Android: a console set up without any games yet (RetroArch alone can enable a dozen) gets no tab of its own; if that
  // would leave nothing at all, they stay so the shelf can still say what to do next
  if(ON_ANDROID){const full=list.filter(hasGames);if(full.length)list=full}
  shelves=items.length?[HOME,...cols,...list]:[...cols,...list];
  const i=shelves.indexOf(keep);sysIdx=i>=0?i:Math.min(sysIdx,Math.max(0,shelves.length-1));
}
function homeLine(g){const h=HIST[g.id];const parts=[shelfFor(g).name];if(h&&h.count)parts.push("played "+fmtDay(h.last/1000));if(isFav(g))parts.push("♥ Favorite");return parts}
async function toggleFav(){
  if((mode!=="browse"&&mode!=="details")||sysBusy())return; // the Home shelf appearing mid-switch would shift the shelves
  const g=curGame();if(!g||!isGame(g)||!cfgInfo)return;
  const list=new Set(cfgInfo.config.ui.favorites||[]);const on=!list.has(g.id);if(on)list.add(g.id);else list.delete(g.id);
  cfgInfo.config.ui.favorites=[...list];try{await api.saveConfig(cfgInfo.config)}catch{}
  play_(on?"toast":"back");rumble(0,.3,70);toast(on?`♥ ${g.title} added to favorites`:`Removed ${g.title} from favorites`);
  const s=slots.find(x=>x.g===g);if(s&&s.el)s.el.classList.toggle("fav",on);
  const wasHome=cur()===HOME;buildHome();if(wasHome){if(!shelves.includes(HOME)){sysIdx=0;sel=0;build();renderTabs()}else{sel=Math.max(0,Math.min(sel,HOME.items.length-1));build()}}else renderTabs();
  updateInfo();
}

/* ---------- sound ---------- */
const play_=(n,...a)=>{if(soundOn&&!attract&&window.Sound)Sound.play(n,...a)};
// controller rumble (Xbox pads through Chromium's dual-rumble effect); quiet no-op elsewhere
function rumble(strong,weak,ms){try{if(attract||(cfgInfo&&cfgInfo.config.ui.rumble===false))return;for(const gp of navigator.getGamepads()||[]){if(gp&&gp.connected&&gp.vibrationActuator&&gp.vibrationActuator.playEffect){gp.vibrationActuator.playEffect("dual-rumble",{duration:ms,strongMagnitude:strong,weakMagnitude:weak});break}}}catch{}}
// menu music (1.75, in place of the ambient bed) follows the shelf; off while a game runs, in the museum, and while the
// window is hidden. ui.music: "calm" (default) / "lounge" / "chime" / "off"; ui.musicLevel 0-1. mmTrack / mmLevel: a
// change from Settings before it's saved; mmWant: whether the shelf wants music right now
let mmTrack=null,mmLevel=null,mmWant=false;
const MM_TRACKS=["drift","night","glow"],MM_OLD={calm:"drift",lounge:"night",chime:"glow"}; // (1.77: ambient tracks; 1.75's names still find theirs)
function mmPick(){const u=(cfgInfo&&cfgInfo.config.ui)||{},v0=mmTrack!=null?mmTrack:u.music,v=MM_OLD[v0]||v0;return v==="off"||MM_TRACKS.includes(v)?v:"drift"}
function mmVol(){const u=(cfgInfo&&cfgInfo.config.ui)||{},v=mmLevel!=null?mmLevel:u.musicLevel;return typeof v==="number"&&isFinite(v)?Math.max(0,Math.min(1,v)):.4}
document.addEventListener("visibilitychange",()=>ambient(mmWant));
// small clock in the top-right corner ("Fri 17 Oct  3:42 PM"), ticks on the minute
// Settings → Display → Clock: "full" (weekday, date and time), "time" only, or "off"; ui.clock24 picks 24-hour time
let clockT=0;
function clock(){clearTimeout(clockT);clockT=0;const el=$("clock");if(!el)return;const u=(cfgInfo&&cfgInfo.config.ui)||{},m=u.clock==="time"||u.clock==="off"?u.clock:"full";
  el.style.display=m==="off"?"none":"";if(m==="off")return;
  const d=new Date();const time=d.toLocaleTimeString(undefined,u.clock24===true?{hour:"2-digit",minute:"2-digit",hourCycle:"h23"}:{hour:"numeric",minute:"2-digit",hour12:true});el.innerHTML="";
  if(m==="full")el.append(d.toLocaleDateString(undefined,{weekday:"short",day:"numeric",month:"short"}));const b=document.createElement("b");b.textContent=time;el.append(b);el.classList.toggle("time-only",m==="time");
  clockT=setTimeout(clock,60500-(d.getSeconds()*1000+d.getMilliseconds())%60000)}
clock();
// each shelf's musical key: semitones from the voice's root, plus a pentatonic colour (major by default; a softer minor/suspended one for the darker shelves)
const KEYS={home:{key:0},gc:{key:0},wii:{key:2},wiiu:{key:4,scale:[0,2,4,7,9]},sw:{key:-3,scale:[0,2,5,7,9]},sw2:{key:-1,scale:[0,2,5,7,9]},psp:{key:-5},ps3:{key:-6,scale:[0,2,4,7,9]},ps4:{key:-7,scale:[0,3,5,7,10]},ps5:{key:-8,scale:[0,2,4,7,9]},x360:{key:-2,scale:[0,3,5,7,10]},n3ds:{key:1,scale:[0,2,4,7,9]},ps2:{key:-9},pc:{key:-4},media:{key:3,scale:[0,2,5,7,9]}};
function tuneTo(s){if(window.Sound)Sound.set(s&&KEYS[s.id]?KEYS[s.id]:{key:0})}
function ambient(on){mmWant=!!on;if(!window.Sound)return;const m=mmPick(),lv=mmVol();if(!on||!soundOn||m==="off"||lv<=0||document.hidden){Sound.ambientStop();return}Sound.ambientStart({track:m,level:lv})}
const sfx={tick:(dir)=>play_("tick",{dir}),flip:()=>play_("flip"),sys:(dir)=>play_("shelf",{dir}),go:()=>play_("go"),back:()=>play_("back")};

/* ---------- cover images ---------- */
// a Pulse app with no picture of its own gets the cover with its own icon as the picture inside (one of the six designs, by its title):
// never the generic cover with the play button
function appFrontAsync(style,g){
  if(g&&g.pulse&&g.art&&g.art.appIcon&&!g.art.front)return Covers.iconFront(style,g,g.art.appIcon).then(u=>u||Covers.frontAsync(style,g),()=>Covers.frontAsync(style,g));
  return Covers.frontAsync(style,g);
}
function frontUrl(g){return (g.art&&g.art.front)||Covers.front(stOf(g),g)}
// the console mark on a box's spine: small line pictures (Xbox's sphere, PlayStation's four buttons, Switch's two Joy-Cons), in the band's ink
const BOX_ICON={
  xbox:'<svg viewBox="0 0 24 24" aria-hidden="true"><circle cx="12" cy="12" r="9.4" fill="none" stroke="currentColor" stroke-width="1.7"/><path d="M6.6 6.8c2.3 2.1 3.6 3.6 5.4 5.7 1.8-2.1 3.1-3.6 5.4-5.7M6.6 17.2c2.3-2.1 3.6-3.6 5.4-5.7 1.8 2.1 3.1 3.6 5.4 5.7" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round"/></svg>',
  ps:'<svg viewBox="0 0 24 24" aria-hidden="true"><g fill="none" stroke="currentColor" stroke-width="1.5" stroke-linejoin="round" stroke-linecap="round"><path d="M12 2.6l2.8 4.8H9.2z"/><circle cx="19.2" cy="12" r="2.7"/><path d="M9.8 17.8l4.4 4.4M14.2 17.8l-4.4 4.4"/><rect x="2.3" y="9.4" width="5.3" height="5.3" rx=".6"/></g></svg>',
  switch:'<svg viewBox="0 0 24 24" aria-hidden="true"><g fill="none" stroke="currentColor" stroke-width="1.6"><rect x="3" y="3.4" width="6.2" height="17.2" rx="3"/><rect x="14.8" y="3.4" width="6.2" height="17.2" rx="3"/></g><circle cx="6.1" cy="8.4" r="1.3" fill="currentColor"/><circle cx="17.9" cy="15.6" r="1.3" fill="currentColor"/></svg>'
};
// the style a game's back is drawn in: a Game Hub box's own console (its colours, band and label), else the shelf's style
function backStyleOf(g){const id={ps5:"ps5",ps4:"ps4",ps3:"ps3",switch:"sw",switch2:"sw2",xbox360:"x360",xboxone:"x360",xboxseries:"x360"}[g&&g.box];return g&&g.pulse&&id&&STYLE[id]?STYLE[id]:stOf(g)}
function backUrl(g){
  if(g.art&&g.art.back)return g.art.back;
  return Covers.back(backStyleOf(g),g,infoRows(g).slice(0,5),g.info&&g.info.overview);
}
// Sample a real cover's average color so the glow matches it.
const hueCache=new Map();
// Local covers are requested at the size they're drawn (the main process scales and caches them); h in CSS px
// (Android: the adapter may offer smaller copies of its 1024 px display pictures, boxshelf.thumbFor(url, device px) → URL
// or nothing; a case on the shelf is drawn ~500 px tall, and a 1024 px cover is ~4x the decoding and upload for it)
function thumb(url,h){if(ANDROID&&url&&api&&api.thumbFor){try{const t=api.thumbFor(url,h*(devicePixelRatio||1));if(t)return t}catch{}}if(!url||!/^bsfile:/.test(url))return url;const px=h*(devicePixelRatio||1),b=[256,512,768,1024,1536,2048].find(v=>v>=px)||2048;return url+(url.includes("?")?"&":"?")+"t="+b}
// small caches that would otherwise grow with every game you scroll past
// pdf.js (320 KB) is only loaded the first time a PDF manual needs turning into pages
let pdfJsP=null;
function loadPdfJs(){if(window.pdfjsLib)return Promise.resolve();return pdfJsP||(pdfJsP=new Promise(res=>{const sc=document.createElement("script");sc.src="vendor/pdf.min.js";sc.onload=sc.onerror=()=>res();document.head.append(sc)}))}
// what the in-game Home menu shows: title, cover, the console's colour and the right button prompts
// (the Home menu is another window: a blob: cover from this one is sent as a data: URL)
function sendHomeMeta(g){try{if(!api.homeMeta)return;const u0=frontUrl(g);if(/^blob:/.test(u0)){fetch(u0).then(r=>r.blob()).then(b=>new Promise(res=>{const fr=new FileReader();fr.onload=()=>res(fr.result);fr.onerror=()=>res(null);fr.readAsDataURL(b)})).then(d=>{if(d)sendHomeMeta_(g,d)},()=>{});return}sendHomeMeta_(g,u0)}catch(e){}}
function sendHomeMeta_(g,u){try{const sh=stOf(g);api.homeMeta({title:g.title,cover:/^bsfile:/.test(u)?thumb(u,240):u,accent:sh&&sh.glow,glyphs:glyphStyle(),swap:PADMAP.swap})}catch(e){}}
function capSet(m,k,v,max){m.set(k,v);if(m.size>max)m.delete(m.keys().next().value)}
function sampleHue(g,cb){
  const url=thumb(g.art&&g.art.front,128);if(!url)return;
  if(hueCache.has(url))return cb(hueCache.get(url));
  if(tinyOk(url)){tinyBits(url,12,12).then(bm=>{const c=document.createElement("canvas");c.width=c.height=12;const x=c.getContext("2d",{willReadFrequently:true});x.drawImage(bm,0,0);bm.close&&bm.close();return hueOf(x.getImageData(0,0,12,12).data)}).then(h=>{if(h!=null){capSet(hueCache,url,h,400);cb(h)}},()=>sampleHueHere(url,cb));return}
  sampleHueHere(url,cb);
}
// the most colourful bright pixel's hue (12×12 pixels of the cover)
function hueOf(d){let best=null,bestS=-1;for(let i=0;i<d.length;i+=4){const r=d[i]/255,gg=d[i+1]/255,b=d[i+2]/255,mx=Math.max(r,gg,b),mn=Math.min(r,gg,b),s=mx-mn;if(s>bestS&&mx>.25){bestS=s;let h=0;if(s){if(mx===r)h=((gg-b)/s)%6;else if(mx===gg)h=(b-r)/s+2;else h=(r-gg)/s+4}best=Math.round(h*60+360)%360}}return best}
function sampleHueHere(url,cb){
  const img=new Image();img.crossOrigin="anonymous";
  img.onload=()=>{const go=()=>{try{const c=document.createElement("canvas");c.width=c.height=12;const x=c.getContext("2d",{willReadFrequently:true});x.drawImage(img,0,0,12,12);const best=hueOf(x.getImageData(0,0,12,12).data);
    if(best!=null){capSet(hueCache,url,best,400);cb(best)}}catch(e){}};img.decode?img.decode().then(go,go):go()}; /* decoded off the main thread first: drawImage then only scales it */
  img.src=url;
}

/* ---------- build & layout ---------- */
function measure(){
  const s=st(),vw=innerWidth,vh=innerHeight;
  bh=vw<760?Math.min(vh*.32,vw*.42/s.ratio):Math.min(vh*.4,vw*.22/s.ratio,fxOn?620:520);
  bw=bh*s.ratio;bd=bw*s.depth;
  const a=app.style;a.setProperty("--bw",bw+"px");a.setProperty("--bh",bh+"px");a.setProperty("--bd",bd+"px");a.setProperty("--edge",(bw*s.edge)+"px");
  // Cases are laid out OS× larger and scaled back down in their transform. Chrome rasterises a layer under
  // perspective at its layout size, so the selected case (pushed towards the camera) would otherwise be
  // magnified from a small raster and go soft; oversampling keeps covers crisp at the screen's real resolution.
  OS=fxOn?1.4:1.15;a.setProperty("--cw",(bw*OS)+"px");a.setProperty("--ch",(bh*OS)+"px");a.setProperty("--cd",(bd*OS)+"px");a.setProperty("--ce",(bw*s.edge*OS)+"px");a.setProperty("--crc",(bw*(s.rc||.03)*OS)+"px");a.setProperty("--cre",(bd*(s.re||.3)*OS)+"px");a.setProperty("--os",String(OS));
}
function applyCase(){const s=st(),a=app.style;a.setProperty("--case",s.caseColor);a.setProperty("--case-side",s.caseSide);a.setProperty("--spine-ink",s.spineInk)}
// Only the cases near the selection exist in the DOM (like the windowed carousels in ES-DE / Big Box): slots[] holds a
// light record per game and mount() builds a case's elements when it comes within WIN of the selection; cases that
// drift past WIN+4 are taken down again, so a 500-game shelf costs the same as a 30-game one.
const WIN=()=>(fxOn?13:9)+2;
function build(){
  track.innerHTML="";slots=[];coverQ=[];
  shelves.forEach(sh=>sh.items.forEach(g=>{if(g.kind==="random"&&(g.revealed||g.pick)){g.revealed=false;g.pick=null;clearTimeout(g.revealT)}}));
  const has=shelves.length>0;$("empty").hidden=has;$("info").hidden=!has;$("hints").hidden=!has;
  if(!has){renderTabs();return}
  applyCase();measure();
  slots=cur().items.map((g,i)=>({i,g,el:null,box:null,img:null,glare:null,back:null,drawn:false,backDrawn:false}));
  if(sel>=slots.length)sel=0;
  // no transitions on the brand-new slots for their first two frames: they must appear already in place, not slide in from the middle
  track.classList.add("fresh");renderTabs();layout(true);requestAnimationFrame(()=>requestAnimationFrame(()=>track.classList.remove("fresh")));
  qsPrebuild();
}
// The quick menu is built once, in a quiet moment after the shelf first shows, so its first open is as quick as every later
// one (built on the press, its first slide-in flickered on the Thor while the panel was created, styled and drawn)
function qsPrebuild(){if(qsPrebuild.done||!window.requestIdleCallback)return;qsPrebuild.done=true;
  requestIdleCallback(()=>{try{if(qsEl||qm.open)return;qsBuild();if(cfgInfo)qsRender();qsEl.getBoundingClientRect()}catch{}},{timeout:4000})}
function mount(s){
  const g=s.g,i=s.i,sh=st();const slot=document.createElement("div");slot.className="slot";
  slot.innerHTML=`<div class="box"><div class="face f-front"><img alt="" draggable="false" decoding="async"><div class="dim"></div><div class="glare"></div><div class="sheen"></div></div><div class="face f-back"></div>
      <div class="face f-left"><div class="spine-band"></div><div class="spine-title"></div></div><div class="face f-right spine2"><div class="spine-band"></div><div class="spine-title"></div></div><div class="face bev bev-fl"></div><div class="face bev bev-fr"></div><div class="face bev bev-bl"></div><div class="face bev bev-br"></div></div>`;
  const os=cur()===HOME?stOf(g):sh;if(os!==sh){slot.style.setProperty("--bw",`calc(var(--ch) * ${os.ratio})`);slot.style.setProperty("--bd",`calc(var(--ch) * ${os.ratio*os.depth})`);slot.style.setProperty("--edge",`calc(var(--ch) * ${os.ratio*os.edge})`);slot.style.setProperty("--rc",`calc(var(--ch) * ${os.ratio*(os.rc||.03)})`);slot.style.setProperty("--re",`calc(var(--ch) * ${os.ratio*os.depth*(os.re||.3)})`);slot.style.setProperty("--case",os.caseColor);slot.style.setProperty("--case-side",os.caseSide);slot.style.setProperty("--spine-ink",os.spineInk)}
  if(isFav(g))slot.classList.add("fav");
  // Pulse's own app icon, small under the cover, on a store picture (a cover made from the icon already has it)
  // a Game Hub box's spine is its own console: that console's colours and label (the Steam box keeps the shelf's PC spine)
  const BOXST={ps5:"ps5",ps4:"ps4",ps3:"ps3",switch:"sw",switch2:"sw2",xbox360:"x360",xboxone:"x360",xboxseries:"x360"},BOXLAB={ps5:"PS5",ps4:"PS4",ps3:"PS3",switch:"SWITCH",switch2:"SWITCH 2",xbox360:"XBOX 360",xboxone:"XBOX ONE",xboxseries:"XBOX SERIES"};
  const boxSt=g.pulse&&g.box&&BOXST[g.box]&&STYLE[BOXST[g.box]]&&STYLE[BOXST[g.box]].spineBand;
  const sb=boxSt||os.spineBand;
  const boxMark=boxSt&&g.kind!=="emulator"&&g.kind!=="setup"?BOX_ICON[/^xbox/.test(g.box)?"xbox":/^ps/.test(g.box)?"ps":"switch"]:null;
  slot.querySelectorAll(".spine-band").forEach(band=>{band.style.background=sb.bg;band.style.color=sb.fg;if(boxMark){band.innerHTML=boxMark;band.classList.add("has-mark")}else{band.classList.remove("has-mark");band.textContent=g.kind==="emulator"?"EMU":g.kind==="setup"?"ADD":(boxSt&&BOXLAB[g.box]||os.spineBand.label)}});
  // a Steam box's spine takes the colour of the game's own cover (the front), once it is read
  if(!boxSt&&g.art&&g.art.front&&!g.art.gen&&window.Covers&&Covers.coverTint&&g.kind!=="emulator"&&g.kind!=="setup")Covers.coverTint(g.art.front).then(t=>{if(!t||!slot.isConnected)return;slot.querySelectorAll(".spine-band").forEach(b=>{b.style.background=t.bg;b.style.color=t.fg})});
  slot.querySelectorAll(".spine-title").forEach(t=>t.textContent=g.title);
  // (beta.10) Android: the spine's small copy sized like the front's (thumb), not the 1024 px display copy, on every case made
  if(g.art&&g.art.spine){const su=ANDROID?thumb(g.art.spine,bh*OS):g.art.spine;slot.querySelectorAll(".f-left,.f-right").forEach(face=>{const sp=new Image();sp.className="spine-img";sp.alt="";sp.draggable=false;sp.decoding="async";sp.src=su;sp.onerror=()=>sp.remove();face.appendChild(sp)})}
  slot.style.setProperty("--glow",hsl(g.hue,90,62));
  slot.addEventListener("click",()=>{if(dragMoved||(mode!=="browse"&&mode!=="details"))return; /* (beta.8: not under a note card, Sort, Achievements or Customize) */if(i===sel){if(window.PulseHost){const n=performance.now();if(n-(slot._t||0)<340){slot._t=0;clearTimeout(slot._ft);pulseOpenCase()}else{slot._t=n;clearTimeout(slot._ft);slot._ft=setTimeout(flip,350)}}else flip()}else{setInput("pointer");go(i)}});
  // keep DOM order = shelf order so stacking stays predictable
  const next=slots.slice(i+1).find(o=>o.el);track.insertBefore(slot,next?next.el:null);
  Object.assign(s,{glareFar:false,glareSide:0,el:slot,box:slot.firstElementChild,img:slot.querySelector(".f-front img"),glare:slot.querySelector(".glare"),back:slot.querySelector(".f-back"),drawn:false,backDrawn:false,fresh:true});
  slot.style.transition="none"; // appears in place; transitions resume after its first frame
  requestAnimationFrame(()=>requestAnimationFrame(()=>{if(s.el===slot)slot.style.transition=""}));
}
function unmount(s){if(!s.el)return;s.img.removeAttribute("src");s.el.remove();Object.assign(s,{el:null,box:null,img:null,glare:null,back:null,drawn:false,backDrawn:false,coverP:null})}
const mounted=()=>slots.filter(s=>s.el);
function layout(instant){
  const fresh=track.classList.contains("fresh"),win=WIN(),inv=(1/OS).toFixed(4);let reflow=false,later=false;
  // Android: a step along the shelf builds and takes down no cases itself. The two cases just past the visible end (still
  // invisible, .far) are built a moment later, one per task after the frame that starts the slide (shelfTidy), so the
  // step's own frame is only class and transform changes. A case that is already in view (a jump, a new shelf) is still built here.
  const defer=ANDROID&&!instant&&!fresh;
  slots.forEach((s,i)=>{
    const ad=Math.abs(i-sel);
    if(ad>win+4){if(s.el){if(defer)later=true;else unmount(s)}return}
    if(ad>win&&!s.el)return;
    if(!s.el){if(defer&&ad>win-2){later=true;return}mount(s)}
    if(instant&&!fresh){s.el.style.transition="none";reflow=true}
    place(s,i,win,inv);
  });
  if(later)shelfTidySoon();
  if(reflow){void track.offsetWidth;mounted().forEach(s=>s.el.style.transition="")} // one forced reflow for the whole shelf, not one per case
  applyRotation();updateInfo();updateBg();landSel();
  $("counter").innerHTML=slots.length?`<b>${sel+1}</b> / ${slots.length}`:"";
}
// where one case sits for the current selection (layout() and shelfTidy())
function place(s,i,win,inv){
  const d=i-sel,ad=Math.abs(d),sg=Math.sign(d);
  // Selected game sits forward with clear space on each side; the rest recede in a tight angled stack.
  // The selected box turns slightly so you see its spine and thickness; side boxes turn 60° so their spines read like a shelf.
  let x=0,z=bw*1.05,ry=22;
  // Pulse, held upright: the box in front is wide on a narrow screen, so the others stand closer (a small gap, like a row of cassettes), and several show at the sides
  const tight=!!window.PulseHost&&innerWidth<innerHeight;
  if(d!==0){x=sg*(tight?bw*.9+(ad-1)*(bw*.1+bd*.45):bw*1.22+(ad-1)*(bw*.22+bd*.9));z=-bw*.7-ad*6;ry=-sg*60}
  // (Android) a case already out of sight (.far, its fade-out over, or built there) that stays out of sight takes its new
  // place at once (.gone: no transform transition). Chromium runs an animation of something invisible on the main thread,
  // not the compositor, so that one hidden case made the page restyle and draw a frame every vsync for the whole slide
  if(ANDROID){const far=ad>win-2,was=s.el.classList.contains("far"),now=performance.now();if(far&&!was)s.farAt=s.fresh?0:now+450;s.fresh=false;s.el.classList.toggle("gone",far&&was&&now>=(s.farAt||0))}
  s.el.classList.toggle("far",ad>win-2);s.el.classList.toggle("sel",d===0);s.el.classList.toggle("rside",d>0);if(ANDROID)s.el.classList.toggle("near",ad<=1);
  s.el.style.transform=`translate3d(${x}px,0,${z}px) rotateY(${ry}deg) scale(${inv})`;
  // the glare band slides with the case's angle and offset (a transform on its own layer, never a repaint); the layer is 1.2 faces wide, so % of it = faces ×83.25
  if(ANDROID)glareAndroid(s,ad,sg,x,ry);
  else if(ad<=3||!s.glareFar){s.glareFar=ad>3;s.glare.style.transform=`translateX(${((-ry/110-x/(bw*7))*83.25).toFixed(1)}%)`} // far cases keep theirs: saves ~25 transitions per move
  if(!s.drawn){s.drawn=true;queueCover(s,s.g,ad)}
  if(d!==0)s.box.style.transform="";
}
// Android: build the missing invisible cases at the ends of the window, and take down the ones left far behind, one case
// per task, nearest first, each after a frame has been drawn (rAF, then a task): never inside the step that moved the shelf
function shelfTidySoon(){if(shelfTidy.t)return;shelfTidy.t=requestAnimationFrame(()=>{shelfTidy.t=setTimeout(shelfTidy,0)})}
function shelfTidy(){
  shelfTidy.t=0;const win=WIN();let best=null,bestD=1e9;
  for(const s of slots){const ad=Math.abs(s.i-sel);if(ad>win+4&&s.el){unmount(s);shelfTidySoon();return}if(ad<=win&&!s.el&&ad<bestD){best=s;bestD=ad}}
  if(best){mount(best);place(best,best.i,win,(1/OS).toFixed(4));shelfTidySoon()}
}
// Android: only the selected case and its two neighbours (.near) keep the glare band and the dark film on layers of their
// own (android.css). A case face with a layer inside it has to be drawn off-screen first and then onto the screen (one
// extra render pass per case, every frame): with every case like that the Thor's GPU did ~6 extra passes a frame while
// the shelf slid. Further out, the band is painted into the face at the place it has two cases out on that side, and
// only changes when the case changes side (never while it just slides along the stack), so those faces aren't redrawn.
function glareAndroid(s,ad,sg,x,ry){
  if(ad<=1){s.glareSide=0;s.glare.style.transform=`translateX(${((-ry/110-x/(bw*7))*83.25).toFixed(1)}%)`;return}
  if(s.glareSide===sg)return;s.glareSide=sg;const x2=sg*(bw*1.22+bw*.22+bd*.9);s.glare.style.transform=`translateX(${((sg*60/110-x2/(bw*7))*83.25).toFixed(1)}%)`;
}
// Real covers load asynchronously; generated covers are drawn a few per frame so moving never stutters.
let coverQ=[],coverQRunning=false;
function setFront(s,g,url,made){if(url&&url.then){const img=s.img;s.coverP=url;url.then(u=>{if(s.img===img&&s.coverP===url&&u)setFront(s,g,u,made)});return}s.coverP=null;url=made?url:thumb(url,bh*OS);s.img.decoding="async";s.img.onerror=made?null:()=>{s.img.onerror=null;setFront(s,g,ANDROID&&Covers.frontAsync?appFrontAsync(stOf(g),g):Covers.front(stOf(g),g),true)};s.img.src=url} /* (a picture that won't load: the made-up cover is encoded in the worker, not on this thread mid-slide; made: never retried) */
function queueCover(s,g,dist){
  if(g.art&&g.art.front){setFront(s,g,g.art.front);return}
  if(dist===0){setFront(s,g,appFrontAsync(stOf(g),g));return}
  coverQ.push({s,g,dist});coverQ.sort((a,b)=>a.dist-b.dist);
  if(!coverQRunning){coverQRunning=true;requestAnimationFrame(drainCovers)}
}
function drainCovers(){
  const t0=performance.now();
  // one generated cover per frame at most (a single one can take most of a frame); skip ones already scrolled away
  while(coverQ.length&&performance.now()-t0<4){const {s,g}=coverQ.shift();if(s.el&&s.el.isConnected){setFront(s,g,appFrontAsync(stOf(g),g));break}}
  if(coverQ.length)requestAnimationFrame(drainCovers);else coverQRunning=false;
}
// Android: covers arriving from the background box art fetch go onto the cases one at a time, nearest the selection first.
// Each picture is decoded off the main thread before it's attached (so the face is drawn once, with the picture, instead
// of blank and then again; and never decoded in the middle of a frame), each attach gets a frame of its own, and nothing is
// attached while the shelf is sliding. Swapping ten covers in one frame was ten decodes, ten face redraws and ten texture
// uploads at once on the Thor: the "lag when the box art loads in".
const artQ=[];let artBusy=false;
function artSwapQueue(s){if(!artQ.includes(s))artQ.push(s);artSwapNext()}
function artSwapNext(){
  if(artBusy||!artQ.length)return;
  if(window.BS_BUSY&&BS_BUSY()){artBusy=true;setTimeout(()=>{artBusy=false;artSwapNext()},300);return}
  artQ.sort((a,b)=>Math.abs(a.i-sel)-Math.abs(b.i-sel));const s=artQ.shift(),g=s.g,img=s.img;
  if(!s.el||!img||!g||!g.art||!g.art.front){artSwapNext();return}
  artBusy=true;const pre=new Image();pre.decoding="async";pre.src=thumb(g.art.front,bh*OS);
  const attach=()=>{if(window.BS_BUSY&&BS_BUSY()){setTimeout(attach,300);return}
    requestAnimationFrame(()=>{if(s.img===img&&s.g===g&&s.el)setFront(s,g,g.art.front);artBusy=false;artSwapNext()})};
  // (0.4.0) decode() settles as a frame is made: on an idle page (no frame asked for: the test meter's loop used to keep
  // frames coming) it could wait for ever and hold the whole queue. One frame is asked for (not a loop), and after 1.5 s
  // the swap goes ahead anyway
  requestAnimationFrame(()=>{});
  Promise.race([pre.decode?pre.decode():Promise.resolve(),new Promise(r=>setTimeout(r,1500))]).then(attach,attach); // a picture that won't decode: setFront's own fallback (the generated cover) takes over
}
// The back of the box is only needed once you settle on a game.
function ensureBack(onlyReal){
  const s=slots[sel];if(!s||s.backDrawn)return;
  // on settle only real back art is attached; a generated back (10–90 ms of drawing) waits until you actually flip
  if(onlyReal){const g0=curGame();if(!g0||!(g0.art&&g0.art.back))return}
  s.backDrawn=true;
  const g=curGame(),img=new Image();img.alt="";img.draggable=false;img.decoding="async";img.onerror=()=>{img.onerror=null;img.src=Covers.back(backStyleOf(g),g,infoRows(g).slice(0,5),g.info&&g.info.overview)};
  // (beta.9) a made-up back not ready yet: its JPEG is encoded in the worker and the picture goes in a moment later
  // (the flip never waits for it; prepBack usually made it while you were resting on the game)
  if((g.art&&g.art.back)||!Covers.backAsync||Covers.hasBack(backStyleOf(g),g))img.src=backUrl(g);
  else Covers.backAsync(backStyleOf(g),g,infoRows(g).slice(0,5),g.info&&g.info.overview).then(u=>{if(u&&!img.src)img.src=u}).catch(()=>{img.src=backUrl(g)});
  s.back.appendChild(img);
}
// (beta.9) Details' backdrop: a screenshot is decoded off the main thread first, then put in (if that game is still open)
let heroBgWant="";
function heroBgSoon(u,g){heroBgWant=u;const im=new Image();im.decoding="async";im.src=u;
  const put=()=>{if(heroBgWant===u&&mode==="details"&&curGame()===g)$("dHeroBg").style.backgroundImage=`url("${String(u).replace(/"/g,"%22")}")`};
  (im.decode?im.decode():Promise.resolve()).then(put,put)}
// (beta.9) rest on a game with no back art of its own and its made-up back is made in idle time (requestIdleCallback
// waits for a still shelf, perf.js), so the first flip shows it at once instead of drawing it then
// Pulse: the disc has its own print, not the cover again: the game's wide picture (or a screenshot) under its logo, with the console's name, like a pressed disc
const pulseDiscCache=new Map();
function pulseDisc(g){
  if(!g||!g.pulse)return Promise.resolve(null);if(pulseDiscCache.has(g.id))return pulseDiscCache.get(g.id);
  const p=(async()=>{const a=g.art||{};
    const load=u=>new Promise(r=>{if(!u||typeof u!=="string")return r(null);const im=new Image();const t=setTimeout(()=>r(null),4000);im.onload=()=>{clearTimeout(t);r(im)};im.onerror=()=>{clearTimeout(t);r(null)};im.src=u});
    const bg=await load(a.hero)||await load(g.shots&&g.shots[0]),logo=await load(a.logo);if(!bg&&!logo)return null;
    const S=768,c=document.createElement("canvas");c.width=c.height=S;const x=c.getContext("2d"),r=S/2;
    x.beginPath();x.arc(r,r,r-1,0,Math.PI*2);x.clip();x.fillStyle="#0b1020";x.fillRect(0,0,S,S);
    if(bg){const k=Math.max(S/bg.width,S/bg.height);x.drawImage(bg,(S-bg.width*k)/2,(S-bg.height*k)/2,bg.width*k,bg.height*k)}
    const v=x.createRadialGradient(r,r,r*.25,r,r,r);v.addColorStop(0,"rgba(4,8,16,.1)");v.addColorStop(1,"rgba(4,8,16,.62)");x.fillStyle=v;x.fillRect(0,0,S,S);
    x.textAlign="center";
    if(logo){const k=Math.min(430/logo.width,150/logo.height);x.save();x.shadowColor="rgba(0,0,0,.75)";x.shadowBlur=16;x.drawImage(logo,r-logo.width*k/2,150-logo.height*k/2,logo.width*k,logo.height*k);x.restore()}
    else{x.fillStyle="#fff";x.font="800 46px Lexend, sans-serif";x.shadowColor="rgba(0,0,0,.8)";x.shadowBlur=12;x.fillText(String(g.title||"").slice(0,22),r,165);x.shadowBlur=0}
    const lab={ps5:"PS5",ps4:"PS4",ps3:"PS3",switch:"SWITCH",switch2:"SWITCH 2",xbox360:"XBOX 360",xboxone:"XBOX ONE",xboxseries:"XBOX SERIES"}[g.box]||"PC";
    x.fillStyle="rgba(255,255,255,.9)";x.font="700 26px Lexend, sans-serif";x.fillText(lab,r,S-92);
    // the clear ring and the hole in the middle
    x.beginPath();x.arc(r,r,r*.3,0,Math.PI*2);x.fillStyle="rgba(210,220,235,.55)";x.fill();x.beginPath();x.arc(r,r,r*.3,0,Math.PI*2);x.strokeStyle="rgba(255,255,255,.7)";x.lineWidth=2;x.stroke();
    x.beginPath();x.arc(r,r,r*.085,0,Math.PI*2);x.fillStyle="#05070a";x.fill();
    const u=c.toDataURL("image/jpeg",.86);c.width=c.height=0;(g.art||(g.art={})).disc=u;return u})().catch(()=>null);
  pulseDiscCache.set(g.id,p);return p}
function prepBack(s,g){if(g&&g.pulse&&!(g.art&&g.art.disc))pulseDisc(g);if(!g||(g.art&&g.art.back)||!Covers.backAsync||Covers.hasBack(backStyleOf(g),g))return;
  const run=()=>{if(slots[sel]!==s||curGame()!==g||s.backDrawn)return;Covers.backAsync(backStyleOf(g),g,infoRows(g).slice(0,5),g.info&&g.info.overview).catch(()=>{})};
  if(window.requestIdleCallback)requestIdleCallback(run,{timeout:3000});else setTimeout(run,250)}
// A tiny copy of the cover, scaled up, makes a smooth blurred background without an expensive blur filter.
const tinyCache=new Map();
function tinyBlur(url,cb){
  if(!url)return cb(null);url=thumb(url,128);if(tinyCache.has(url))return cb(tinyCache.get(url));
  if(tinyOk(url))return tinyBits(url,18,24).then(bm=>{const c=document.createElement("canvas");c.width=18;c.height=24;const x=c.getContext("2d",{willReadFrequently:true});try{x.filter="saturate(1.3) brightness(.95)"}catch(e){}x.drawImage(bm,0,0,18,24);bm.close&&bm.close();const d=c.toDataURL("image/png");capSet(tinyCache,url,d,400);return d}).then(cb,()=>tinyBlurHere(url,cb));
  tinyBlurHere(url,cb);
}
// Android: only pictures fetch may read under the page's CSP (connect-src 'self' blob:), i.e. Capacitor's own files; a
// data: cover or another site's picture takes the usual path
function tinyOk(url){if(!ANDROID||!window.createImageBitmap||!window.fetch)return false;try{const u=new URL(url,location.href);return u.protocol==="blob:"||(/^https?:$/.test(u.protocol)&&u.origin===location.origin)}catch{return false}}
// the cover shrunk to a few pixels by the browser's image decoder on a worker thread (a Blob into createImageBitmap with a
// resize), so the phone's main thread never decodes and scales a 512 px picture just to read 18×24 pixels of it
function tinyBits(url,w,h){return fetch(url).then(r=>{if(!r.ok)throw new Error(r.status);return r.blob()}).then(b=>createImageBitmap(b,{resizeWidth:w,resizeHeight:h,resizeQuality:"medium"}))}
function tinyBlurHere(url,cb){
  const img=new Image();img.crossOrigin="anonymous";img.decoding="async";
  img.onload=()=>{const go=()=>{try{const c=document.createElement("canvas");c.width=18;c.height=24;const x=c.getContext("2d",{willReadFrequently:true});try{x.filter="saturate(1.3) brightness(.95)"}catch(e){}x.drawImage(img,0,0,18,24);const d=c.toDataURL("image/png");capSet(tinyCache,url,d,400);cb(d)}catch(e){cb(url)}};img.decode?img.decode().then(go,go):go()};
  img.onerror=()=>cb(null);img.src=url;
}
// Eased free spin for the selected box (shared by the right stick and mouse drags)
let spinT=0,spinRaf=0;
// an easing factor tuned for 60 Hz frames, for a frame of dt ms: the same speed on a 60, 144 or 240 Hz screen
const easeK=(k,dt)=>1-Math.pow(1-k,Math.min(4,Math.max(.1,dt/16.667)));let spinLt=0;
function spinEase(){if(spinRaf)return;ensureBack(); /* spinning round shows the back: draw it now */const s=slots[sel];if(s)s.box.classList.add("free");const step=t=>{spinRaf=0;const d=spinT-spin;if(Math.abs(d)<.05){spin=spinT;applyRotation();if(!padSpinActive&&!(drag&&drag.onSel)){const b=slots[sel];if(b)b.box.classList.remove("free")}return}spin+=d*easeK(Math.abs(d)>60?.085:.16,t-(spinLt||t-16.667));spinLt=t;applyRotation();spinRaf=requestAnimationFrame(step)};spinLt=0;spinRaf=requestAnimationFrame(step)}
function spinSettle(){
  // nearest quarter turn: front, right spine, back or left spine
  const total=(flipped?180:0)+spinT,snapped=Math.round(total/90)*90,norm=((snapped%360)+360)%360;
  const curAngle=(flipped?180:0)+spin;
  flipped=norm===180;spinT=snapped-(flipped?180:0);spin=curAngle-(flipped?180:0);
  const k=Math.round(spinT/360)*360;spin-=k;spinT-=k; // keep both near zero, same frame of reference
  const side=norm===90||norm===270;spinEase();if(side)sfx.flip();return side; // tells a mouse drag the flip sound already played
}
function applyRotation(){const s=slots[sel];if(s){s.box.style.transform=`rotateY(${(flipped?180:0)+spin}deg)`;s.el.classList.toggle("flipped",flipped)}}
let landT;
function landSel(){
  clearTimeout(landT);if(landSel.prev&&landSel.prev.el)landSel.prev.el.classList.remove("landed");landSel.prev=slots[sel];const s=slots[sel];if(!s)return;
  landT=setTimeout(()=>{if(s.el){s.el.classList.add("landed");ensureBack(true);if(!ANDROID)prepBack(s,curGame())}},380);qsFreshSoon(); // (Android: its handheld budget keeps backs to the flip)
  const g=curGame(),gh0=themeOf().halo(shelfHue(g),shelfPos()),gh=window.bsPlatform&&bsPlatform.os==="android"?Math.round(gh0/3)*3:gh0,glow=hsl(gh,95,62,.62); /* Android: the hue in 3° steps (invisible), so most shelf steps don't repaint the halo, floor and tab bar */glowEls=glowEls||[...document.querySelectorAll(".floor,.sel-halo,#tabs")];if(glow!==lastGlow){lastGlow=glow;
    // a step along the shelf only walks the hue by a degree or so: the tab bar takes that at once instead of fading it in
    // over .6 s (a colour fade the compositor can't do, so it was a repaint every frame for as long as you kept moving);
    // a real change (another shelf, another theme) still fades
    const tb=$("tabs"),now=performance.now(),snap=tb&&Math.abs(((gh-landSel.gh)%360+540)%360-180)<6&&!(now-landSel.fadeAt<650);landSel.gh=gh;if(!snap)landSel.fadeAt=now;if(snap){tb.classList.add("glow-snap");requestAnimationFrame(()=>requestAnimationFrame(()=>tb.classList.remove("glow-snap")))} // off again once a frame has used it
    glowEls.forEach(e=>e.style.setProperty("--sel-glow",glow))} /* only its two users restyle, not the whole page */const setGlow=h=>s.el.style.setProperty("--glow",hsl(h,90,62));
  setGlow(g.hue);clearTimeout(landSel.hueT);const sample=()=>sampleHue(g,h=>{if(curGame()===g){g.hue=h;setGlow(h)}});
  // a cover not sampled yet is fetched and read back once you pause on it (~0.14 s), not for every case a held stick runs past
  if(hueCache.has(thumb(g.art&&g.art.front,128)))sample();else landSel.hueT=setTimeout(sample,ANDROID?620:140); /* Android: read once the slide has landed (a new glow colour redraws the selected case's face) */
}
function metaFor(g){
  const s=shelfFor(g);
  if(cur()===HOME)return homeLine(g);
  if(g.kind==="emulator")return ["Emulator","Open settings"];
  if(g.kind==="setup"&&g.addGames)return ["Add games",`${okWord()} adds several at once, a whole folder, or your Playnite library`];
  if(g.kind==="setup")return [`No ${s?s.name+" ":""}games yet`,`${okWord()} opens ${s?s.name:"its"} settings to add a games folder`];
  if(g.kind==="random")return g.pick?["Surprise pick",(g.pick.sh||shelves[g.pick.shelf]).name,rerollWord()]:["Shuffle","Lands on a random game"];
  if(g.kind==="app")return [g.pulse?"Game":"Streaming app"];
  if(g.kind==="steam")return [PC_SRC[g.source]||"Steam",g.minutes?`${(g.minutes/60).toFixed(1)} h played`:"Not played yet"];
  const r=raFor(g);
  return [g.region,g.ext&&g.ext.toUpperCase(),fmtSize(g.sizeBytes),g.discs&&g.discs.length>1?`${g.discs.length} discs`:"",hasManual(g)?"Manual":"",r?(r.mastered==="mastered"?"★ Mastered":`★ ${r.earned}/${r.total}`):""].filter(Boolean);
}
function updateInfo(){pvBackSchedule();
  bsSelectSoon();
  const g=curGame(),info=$("info");
  if(!g){const es=emptyShelf();$("gTitle").textContent=filterBy&&filterBy!=="all"?"No games match this filter":"No games here yet";$("gMeta").innerHTML=filterBy&&filterBy!=="all"?`<span>${showPad()?"Hold "+padWord("menu"):"Press G"} to change the filter</span>`:"";
    if(es&&!(filterBy&&filterBy!=="all")){const b=document.createElement("button");b.type="button";b.className="empty-set";b.textContent=es.kind==="steam"?`Press ${okWord()} to open Steam settings`:`Press ${okWord()} to open ${es.name} settings and add a games folder`;b.addEventListener("click",()=>play());$("gMeta").appendChild(b)}
    renderHints();return}
  $("gTitle").textContent=g.title;
  // Pulse's apps: the app's own icon beside the title (the cover is the game's own picture, never the icon)
  if(g.pulse&&g.art&&g.art.appIcon){const im=document.createElement("img");im.className="g-ico";im.alt="";im.src=g.art.appIcon;$("gTitle").prepend(im)}
  const raw=rawItem(),meta=raw&&raw!==g?["Surprise pick · "+(raw.pick.sh||shelves[raw.pick.shelf]).name,...metaFor(g).slice(0,2),rerollWord()]:metaFor(g);
  const m=$("gMeta");m.innerHTML="";meta.forEach(t=>{const sp=document.createElement("span");sp.textContent=t;m.appendChild(sp)});
  const rise=[{opacity:0,transform:"translateY(8px)"},{opacity:1,transform:"none"}],ro={duration:450,easing:"cubic-bezier(.22,.9,.25,1)"};
  // Web Animations: restarts without forcing a layout. The previous rise is cancelled first: one still running on the
  // same element kept the new one off the compositor (it then ran on the main thread, a style update every frame)
  if(updateInfo.rise)updateInfo.rise.forEach(a=>a.cancel());updateInfo.rise=reduced()?null:[$("gTitle").animate(rise,ro),m.animate(rise,ro)];
  renderHints();
  // Android (phone CPU): stepping along the shelf with Details open used to rebuild the whole panel on every step (its
  // screenshots, a big blurred hero, a layout read); the title follows at once and the rest once you pause (160 ms)
  if(mode==="details"){if(window.bsPlatform&&bsPlatform.os==="android"){$("dTitle").textContent=g.title;clearTimeout(updateInfo.fd);updateInfo.fd=setTimeout(()=>{if(mode==="details")fillDetails()},160)}else fillDetails()}
}
let bgFlip=false,bgTimer;let songsLoaded=false;
// Background hue: the system's colour (Xbox green, Switch red…) drifting ±16° along the shelf (enough to feel alive, never enough to leave the system's colour), so moving through a
// library walks the tone slowly instead of jumping to whatever each cover happens to be.
function shelfHue(g){const s=st();return (s.hue!=null?s.hue:(g?g.hue:0))+(shelfPos()*2-1)*16}
const shelfPos=()=>sel/Math.max(1,slots.length-1); // 0 at the first case, 1 at the last
/* ---------- colour themes ----------
   Each theme paints the wash behind the shelf (tint), picks the halo colour under the selected case, and may add an
   ambient layer (#themeFx, styled per theme in CSS: aurora ribbons, synthwave grid, gallery spotlight). sh = the
   system's hue walked a little along the shelf, p = position along the shelf 0..1. */
const THEMES={
  spectrum:{name:"Spectrum",halo:sh=>sh,
    tint:(sh,g)=>`radial-gradient(ellipse 75% 65% at 28% 22%,${hsl(sh,72,46,.62)},transparent 70%),radial-gradient(ellipse 60% 60% at 82% 72%,${hsl(sh+10,70,34,.5)},transparent 70%),radial-gradient(ellipse 40% 40% at 55% 55%,${hsl(g.hue,60,42,.14)},transparent 70%),linear-gradient(160deg,${hsl(sh,55,22)},${hsl(sh+4,50,7)})`},
  // Spectrum Classic: the original Spectrum, just the colour wash with no moving layer
  classic:{name:"Spectrum Classic",halo:sh=>sh,tint:(sh,g)=>THEMES.spectrum.tint(sh,g)},
  midnight:{name:"Midnight",halo:sh=>sh,
    tint:sh=>`radial-gradient(ellipse 55% 34% at 50% 74%,${hsl(sh,80,42,.28)},transparent 72%),radial-gradient(ellipse 90% 40% at 50% 0%,${hsl(sh,40,30,.12)},transparent 70%),#000`},
  aurora:{name:"Aurora",halo:sh=>sh,
    tint:(sh,g,p)=>{const h=sh;return `radial-gradient(ellipse 70% 45% at 25% 18%,${hsl(h,80,45,.45)},transparent 70%),radial-gradient(ellipse 60% 40% at 78% 26%,${hsl(h+70,75,48,.38)},transparent 70%),radial-gradient(ellipse 80% 50% at 50% 100%,${hsl(h+20,70,25,.35)},transparent 70%),linear-gradient(180deg,#04101c,#020610 70%,#01030a)`}},
  // Keys: deep night blue; the dot wave (themefx.js) takes the song's colour
  keys:{name:"Keys",halo:sh=>songHue(sh),
    tint:sh=>`radial-gradient(ellipse 70% 40% at 50% 62%,${hsl(songHue(sh),70,30,.28)},transparent 72%),radial-gradient(ellipse 80% 50% at 50% 0%,${hsl(songHue(sh)+40,50,18,.35)},transparent 70%),linear-gradient(180deg,#05040f,#0a0a22 55%,#030309)`},
  // Arcade: violet night over a neon horizon (stars and mountains are drawn by themefx.js)
  arcade:{name:"Arcade",halo:sh=>sh+300,
    tint:sh=>`radial-gradient(ellipse 80% 30% at 50% 60%,${hsl(sh+300,90,45,.32)},transparent 70%),linear-gradient(180deg,#04020c 0%,#0d0524 45%,#1c0838 58%,#07021a 62%,#030108 100%)`},
  // (1.76) the old Background setting as two themes. Artwork: the game's own background art, sharp (a game with only a
  // cover gets the poster look over Spectrum's wash; bgMode() is "art" for this theme). Blur: the selected game's cover
  // softened into the whole backdrop, under only a light shade, so every game brings its own colours
  artwork:{name:"Artwork",halo:sh=>sh,tint:(sh,g,p)=>THEMES.spectrum.tint(sh,g,p)},
  blur:{name:"Blur",halo:sh=>sh,
    tint:sh=>`radial-gradient(ellipse 55% 30% at 50% 60%,${hsl(sh,60,50,.16)},transparent 72%),linear-gradient(180deg,rgba(0,0,0,.42),rgba(0,0,0,.12) 34%,rgba(0,0,0,.18) 58%,rgba(0,0,0,.62))`}
};
// older themes from before 1.28 map onto the current five
const THEME_ALIAS={sunset:"spectrum",gallery:"midnight",synthwave:"arcade"};
// ten more themes (themes-extra.js: console-inspired, moody, animated retro), on Android since 0.3.5 and Windows since 1.75
const XTHEMES=Array.isArray(window.BS_EXTRA_THEMES)?BS_EXTRA_THEMES.filter(t=>t&&t.id&&!THEMES[t.id]&&typeof t.tint==="function"):[];
for(const t of XTHEMES)THEMES[t.id]={name:t.name,halo:typeof t.halo==="function"?t.halo:sh=>sh,tint:t.tint};
const songHue=sh=>{try{const s=window.Sound&&Sound.theme==="melody"&&Sound.song();return s?s.hue:sh}catch{return sh}};
const themeOf=()=>THEMES[themeId()]||THEMES.spectrum;let themeOverride=null;
const themeId=()=>{const t=themeOverride||(cfgInfo&&cfgInfo.config.ui.theme)||"spectrum";return THEMES[t]?t:(THEME_ALIAS[t]||"spectrum")};
// the body's theme classes: theme-<id>, plus (custom themes) theme-custom, custom-blur and the floor glow
function themeCls(){const id=themeId(),c=(THEMES[id]||{}).custom||null;Object.keys(THEMES).forEach(k=>document.body.classList.toggle("theme-"+k,k===id));
  document.body.classList.toggle("theme-custom",!!c);document.body.classList.toggle("custom-blur",!!(c&&c.blur));if(c)document.body.style.setProperty("--custom-glow",String(c.glow!=null?c.glow:.35));else document.body.style.removeProperty("--custom-glow")}
function applyTheme(){themeCls();fxTheme();updateBg();landSel()}
// (1.76, Windows) custom themes: pictures the user puts in "Custom themes\<name>\" (main/customthemes.js). Each becomes a
// theme like the built-in ones: a tile, a halo from its accent colour, and a backdrop of its picture (or the picture for
// this shelf) under a dark shade so the shelf stays readable. Pictures are decoded once when the list arrives and the
// decoded images are kept, so changing game never waits on a decode. Loaded after start-up: a saved custom theme shows
// Spectrum for that moment, then its own picture.
let CUSTOM=[];
const hexHue=h=>{const m=/^#?([0-9a-f]{2})([0-9a-f]{2})([0-9a-f]{2})$/i.exec(String(h||""));if(!m)return 225;const [r,g,b]=[1,2,3].map(i=>parseInt(m[i],16)/255),mx=Math.max(r,g,b),mn=Math.min(r,g,b),d=mx-mn;if(!d)return 225;
  const hh=mx===r?((g-b)/d)%6:mx===g?(b-r)/d+2:(r-g)/d+4;return Math.round((hh*60+360)%360)};
const cssUrl=u=>`url("${String(u).replace(/["\\\n\r]/g,"")}")`;
function setCustomThemes(list){
  for(const t of CUSTOM){document.body.classList.remove("theme-"+t.id);delete THEMES[t.id]} // (its class first: themeCls only knows the themes in the new list)
  if(typeof QM_THEMES!=="undefined")for(let i=QM_THEMES.length-1;i>=0;i--)if(/^custom-/.test(QM_THEMES[i][0]))QM_THEMES.splice(i,1);
  CUSTOM=(Array.isArray(list)?list:[]).filter(t=>t&&/^custom-[a-z0-9-]{1,48}$/.test(t.id)&&typeof t.bg==="string"&&!THEMES[t.id]).slice(0,60);
  for(const t of CUSTOM){const hue=hexHue(t.accent),sh=Math.max(0,Math.min(.9,+t.shade||0));
    THEMES[t.id]={name:String(t.name||"Custom").slice(0,40),custom:t,halo:()=>hue,
      tint:()=>{const s=cur(),u=(s&&t.sys&&t.sys[s.id])||t.bg;return `linear-gradient(180deg,rgba(0,0,0,${Math.min(.95,sh+.15).toFixed(2)}),rgba(0,0,0,${(sh*.5).toFixed(2)}) 35%,rgba(0,0,0,${(sh*.6).toFixed(2)}) 60%,rgba(0,0,0,${Math.min(.95,sh+.3).toFixed(2)})),${cssUrl(u)} center/cover no-repeat,#0b0c14`}};
    if(typeof QM_THEMES!=="undefined")QM_THEMES.push([t.id,THEMES[t.id].name]);
    t._im=[t.bg,...Object.values(t.sys||{})].map(u=>{const im=new Image();im.decoding="async";im.src=u;if(im.decode)im.decode().catch(()=>{});return im})}
  window.BS_CUSTOM_THEMES=CUSTOM.map(t=>({id:t.id,name:THEMES[t.id].name,bg:t.bg,accent:t.accent}));
  try{window.dispatchEvent(new CustomEvent("bs-custom-themes"))}catch{}
  fxTheme.t=null;applyTheme()}
async function loadCustomThemes(){if(!api.customThemes)return;try{setCustomThemes(await api.customThemes())}catch(e){console.warn("custom themes",e)}}
// the theme's moving layer follows the shelf's colour to the nearest degree, and is only told when that changes: at Low
// (a still picture) every call redrew the whole screen-sized canvas, once per move
function fxTheme(){if(!window.ThemeFx)return;const g=curGame(),t=themeId(),h=g?Math.round(shelfHue(g)):220;if(t===fxTheme.t&&h===fxTheme.h)return;fxTheme.t=t;fxTheme.h=h;ThemeFx.set(t,{hue:h})}
// (1.76: the Background setting became the Artwork and Blur themes; bgOverride / setBackground are kept as no-ops for callers)
const bgMode=()=>themeId()==="artwork"?"art":"blur";let bgOverride=null;
// the backdrop layer that fades out is hidden once its fade is over. On Android that's a class taken off by a timer
// (android.css), not a delayed visibility transition: Chromium runs that one on the main thread, a restyle and a frame
// every vsync for the whole .8 s fade, which landed on the next slide whenever you moved on within a second or so
function bgOff(b){const was=b.classList.contains("on");b.classList.remove("on");if(!ANDROID||!was)return; /* (a layer already off stays hidden: making it visible at opacity 0 kept a full-screen layer drawn for a whole scroll) */b.classList.add("bg-out");clearTimeout(b.outT);
  const hide=()=>{if(b.classList.contains("on"))b.classList.remove("bg-out");else if(window.BS_BUSY&&BS_BUSY())b.outT=setTimeout(hide,300);else b.classList.remove("bg-out")};b.outT=setTimeout(hide,850)}
function updateBg(){
  clearTimeout(bgTimer);
  bgTimer=setTimeout(()=>{
    if(!cur())return; // no shelves (theme / background changed on an empty library)
    const s=st(),g=curGame();if(!g)return;const a=$(bgFlip?"bgA":"bgB"),b=$(bgFlip?"bgB":"bgA");bgFlip=!bgFlip;
    a.querySelector(".tint").style.background=themeOf().tint(shelfHue(g),g,shelfPos());fxTheme();
    // 'art': real landscape background art (LaunchBox fanart / hero) fills the screen; a game with only a cover gets
    // a poster treatment instead (cover at full height on the right, fading into the system wash) — never a portrait
    // cover blown up to fill a 16:9 screen
    const wide=bgMode()==="art"&&((g.info&&g.info.bg)||(g.art&&g.art.hero))||null;
    const poster=!wide&&bgMode()==="art"&&frontUrl(g)||null;const artBg=wide||poster;
    app.classList.toggle("bg-art",!!artBg);a.classList.toggle("poster",!!poster);
    if(artBg){const im=a.querySelector("img");im.onerror=()=>{im.onerror=null;a.classList.remove("poster");tinyBlur(artBg,d=>{im.src=d||""})};im.src=thumb(artBg,innerHeight);
      // fade in only once the picture is decoded, or the layer shows whatever it held two moves ago
      const show=()=>{if(curGame()===g){a.classList.add("on");bgOff(b)}};(im.decode?im.decode():Promise.resolve()).then(show,show);return}
    a.classList.remove("poster");
    // (Android: a game with no cover picture blurs the made-up cover from the worker's copy, never a fresh synchronous encode)
    const src=(g.art&&g.art.hero)||(ANDROID&&!(g.art&&g.art.front)?appFrontAsync(stOf(g),g):frontUrl(g));
    const blur=u=>tinyBlur(u,d=>{if(curGame()!==g)return;const im=a.querySelector("img");im.src=d||"";a.classList.add("on");bgOff(b)});
    if(src&&src.then)src.then(blur,()=>blur(frontUrl(g)));else blur(src);
  },ANDROID?620:120); /* Android: once the slide (.55 s) has landed. Two screen-sized layers redrawn and cross-fading under the moving cases cost the Thor frames mid-slide; a held stick now changes the backdrop only when you stop */
}
function renderWordmark(el,id,name,base){const w=WORDMARKS[id]||[name,""];el.className=base+"wm wm-"+id;el.innerHTML="";const m=document.createElement("span");m.className="wm-main";m.textContent=w[0];const t=document.createElement("span");t.className="wm-tag";t.textContent=w[1];el.append(m,t)}
// Logos people drop in are often on a flat white (or black) background. Knock that background out from the edges
// inward so the logo sits on the shelf like the built-in wordmarks; the result is cached per file.
const logoCache=new Map();
function cleanLogoHere(url){ // the same clean-up on this thread: only if the worker can't run
  const p=new Promise(resolve=>{const im=new Image();im.crossOrigin="anonymous";im.onload=()=>{try{
    // work at most 1024px wide: the logo is shown small, and this keeps the fill cheap
    const sc=Math.min(1,1024/Math.max(1,im.naturalWidth)),W=Math.max(1,Math.round(im.naturalWidth*sc)),Hh=Math.max(1,Math.round(im.naturalHeight*sc));if(!im.naturalWidth||!im.naturalHeight)return resolve({url,dark:false});
    const c=document.createElement("canvas");c.width=W;c.height=Hh;const x=c.getContext("2d",{willReadFrequently:true});x.drawImage(im,0,0,W,Hh);const d=x.getImageData(0,0,W,Hh),px=d.data;
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
  }catch{resolve({url,dark:false,ar:im.naturalWidth&&im.naturalHeight?im.naturalWidth/im.naturalHeight:2.2})}};im.onerror=()=>resolve({url,dark:false});im.src=url});
  // trim empty margins (people's PNGs often carry a lot of padding, which made some logos tiny next to others),
  // measure the aspect so every logo can be shown at the same visual weight, and flag dark lettering for a halo
  function finish(c,px,W,Hh){
    let x0=W,y0=Hh,x1=0,y1=0;for(let k=0;k<W*Hh;k++){if(px[k*4+3]<20)continue;const cx=k%W,cy=(k-cx)/W;if(cx<x0)x0=cx;if(cx>x1)x1=cx;if(cy<y0)y0=cy;if(cy>y1)y1=cy}
    let lum=0,ln=0;for(let k=0;k<W*Hh;k+=3){if(px[k*4+3]<200)continue;lum+=(px[k*4]*.3+px[k*4+1]*.59+px[k*4+2]*.11);ln++}const dark=ln>0&&lum/ln<110;
    if(x1>x0&&y1>y0){const t=document.createElement("canvas");t.width=x1-x0+1;t.height=y1-y0+1;t.getContext("2d").drawImage(c,x0,y0,t.width,t.height,0,0,t.width,t.height);return {url:t.toDataURL("image/png"),dark,ar:t.width/t.height}}
    return {url:c.toDataURL("image/png"),dark,ar:W/Hh};
  }
  return p;
}
// Logo clean-up runs in logo-worker.js (off the screen's thread) and the result is kept, so a logo is only ever
// worked out once: later starts show it straight away. A replaced logo has a new address (its date is in it) and is
// done again.
const LOGO_V=3;let logoW=null,logoSeq=0;const logoWait=new Map();
function logoWorker(){if(logoW!==null)return logoW;try{logoW=new Worker("logo-worker.js");logoW.onmessage=e=>{const f=logoWait.get(e.data.id);if(f){logoWait.delete(e.data.id);f(e.data.res)}};logoW.onerror=()=>{logoW=false;logoWait.forEach(f=>f(null));logoWait.clear()}}catch{logoW=false}return logoW}
// Keep one saved result per logo file: drop older saves of the same file (a replaced logo gets a new ?v= address) and
// saves from older clean-up versions, so the store doesn't slowly fill up. A logo that needed no change is saved as a
// tiny "use as is" note, so it isn't worked out again on every start.
function logoKeep(url,key,res){try{
  const base=url.split("?")[0];
  for(let i=localStorage.length-1;i>=0;i--){const k=localStorage.key(i);if(!k||!k.startsWith("logo:")||k===key)continue;
    if(!k.startsWith("logo:"+LOGO_V+":")||k.slice(("logo:"+LOGO_V+":").length).split("?")[0]===base)localStorage.removeItem(k)}
  const v=res.url===url?{same:1,dark:!!res.dark,ar:res.ar}:res;const j=JSON.stringify(v);if(j.length<2e6)localStorage.setItem(key,j)}catch{}}
function cleanLogo(url){
  if(logoCache.has(url))return logoCache.get(url);
  const key="logo:"+LOGO_V+":"+url;let kept=null;try{kept=JSON.parse(localStorage.getItem(key)||"null")}catch{}
  if(kept&&(kept.url||kept.same)){const p=Promise.resolve(kept.same?{url,dark:!!kept.dark,ar:kept.ar}:kept);logoCache.set(url,p);return p}
  const p=(async()=>{
    const w=typeof OffscreenCanvas!=="undefined"&&typeof createImageBitmap!=="undefined"&&logoWorker();
    if(w){try{const bmp=await createImageBitmap(await(await fetch(url)).blob());const res=await new Promise(ok=>{const id=++logoSeq;logoWait.set(id,ok);w.postMessage({id,url,bmp},[bmp])});
      if(res&&res.url){logoKeep(url,key,res);return res}}catch{}}
    return cleanLogoHere(url)})();
  logoCache.set(url,p);return p}
function applyLogo(){
  const s=cur();const url=s&&themes[s.id]&&themes[s.id].logo,img=$("sysLogo");
  img.hidden=!url;$("sysWord").hidden=!!url||!s;
  if(url){img.dataset.for=url;cleanLogo(url).then(r=>{if(img.dataset.for===url){bootMark("logo");img.src=r.url;img.classList.toggle("dark-logo",!!r.dark);img.style.setProperty("--ar",String(r.ar||2.2));img.dataset.ready=url}})}
}
// The tab bar keeps its buttons between shelves; only the white pill glides to the new one.
function renderTabs(){
  const t=$("tabs");const names=shelves.map(s=>s.name).join("\u0001");
  if(t.dataset.names!==names){t.innerHTML="";t.dataset.names=names;const pill=document.createElement("span");pill.className="tab-pill";pill.innerHTML='<i class="c"></i><i class="m"></i><i class="c"></i>';
    // Android: everything sits in one strip that the bar slides with a transform (no scroll position to lay out again)
    const box=ON_ANDROID?document.createElement("div"):t;if(ON_ANDROID){box.className="tab-strip";t.classList.add("tabs-x");t.appendChild(box);tabGeo=null}box.appendChild(pill);
    shelves.forEach((s,i)=>{const b=document.createElement("button");b.type="button";b.className="tab";const short=ON_ANDROID&&TAB_SHORT[s.id];b.textContent=short||s.name;if(short){b.title=s.name;b.setAttribute("aria-label",s.name)}b.addEventListener("click",()=>{if(tabDragged)return;setInput("pointer");setSystem(i)});box.appendChild(b)})}
  t.querySelectorAll(".tab").forEach((b,i)=>b.classList.toggle("on",i===sysIdx));placePill();
  const s=cur();
  if(s){renderWordmark($("sysWord"),s.id,s.name,"sys-word ");const n=s.games.length,shown=s.kind==="home"?n:s.items.filter(isGame).length;const what=s.kind==="apps"?(n===1?"app":"apps"):s.kind==="steam"?"installed":(n===1?"game":"games");
    const extra=s.kind==="home"||s.kind==="apps"?"":[filterBy!=="all"?`${filterName(filterBy).toLowerCase()}`:"",SORT_WORD[sortBy]].filter(Boolean).join(" · ");
    $("sysCount").textContent=(filterBy!=="all"&&s.kind!=="home"&&s.kind!=="apps"?`${shown} of ${n} ${what}`:`${n} ${what}`)+(extra?" · "+extra:"")}
  else{$("sysCount").textContent="";$("counter").textContent=""}
  applyLogo();renderHints();
}

/* ---------- actions ---------- */
// moving along the shelf: idle-time jobs (perf.js) hold off until everything has settled (the slide is .55 s, the
// backdrop's cross-fade ends ~.9 s after the last move) and you've paused a moment
let shelfMovedAt=-1e9;window.BS_BUSY=()=>performance.now()-shelfMovedAt<1500;
function go(i){const n=slots.length;i=Math.max(0,Math.min(n-1,i));if(i===sel)return false;shelfMovedAt=performance.now();const dir=i>sel?1:-1;const prev=slots[sel];if(prev)prev.box.classList.remove("free");sel=i;flipped=false;spin=0;spinT=0;cancelAnimationFrame(spinRaf);spinRaf=0;layout();sfx.tick(dir);if(ANDROID){clearTimeout(go.saveT);go.saveT=setTimeout(save,700)}else save();return true} /* Android: the place is written once you stop, not on every step of a held stick */
if(ANDROID)addEventListener("visibilitychange",()=>{if(document.hidden&&go.saveT){clearTimeout(go.saveT);go.saveT=0;save()}}); // a game launched straight after a step: written as Boxshelf goes to the back
const move=d=>{if(mode!=="browse"&&mode!=="details")return;if(!go(sel+d)&&d)bump(d)};
function bump(d){const s=slots[sel];if(!s)return;s.box.style.transform=`translateX(${d*10}px) rotateY(${(flipped?180:0)+spin}deg)`;setTimeout(applyRotation,120)} /* +spin: a box turned to its spine stays turned during the nudge */
const skip=d=>{if(mode==="browse")go(sel+d*5)};
function letter(dir){
  if(mode!=="browse"||!cur()||!cur().items.length)return;const gs=cur().items;
  // sections follow the sort: letters for A–Z, years, genres, play-time bands… (Home keeps plain letters)
  const L=g=>!isGame(g)?"•":cur().kind==="home"?(/[A-Z]/i.test(g.title[0]||"")?g.title[0].toUpperCase():"#"):groupOf(g)||"#";
  const starts=[];gs.forEach((g,i)=>{if(i===0||L(g)!==L(gs[i-1]))starts.push(i)});if(starts.length<2)return;
  let k=0;starts.forEach((st,j)=>{if(st<=sel)k=j});
  let target;if(dir>0)target=starts[(k+1)%starts.length];else target=starts[sel>starts[k]?k:(k-1+starts.length)%starts.length];
  go(target);const f=$("letterFlash"),lab=L(gs[sel]);f.textContent=lab;f.classList.toggle("long",lab.length>2);
  if(letterAnim)letterAnim.cancel();if(!reduced())letterAnim=f.animate([{opacity:0,transform:"translate(-50%,-50%) scale(.8)"},{opacity:.95,transform:"translate(-50%,-50%) scale(1)",offset:.15},{opacity:0,transform:"translate(-50%,-50%) scale(1.08)"}],{duration:900,easing:"ease-out"});
}
function flip(){if(mode!=="browse"&&mode!=="details")return;ensureBack();flipped=!flipped;spin=0;spinT=0;cancelAnimationFrame(spinRaf);spinRaf=0;const b=slots[sel];if(b)b.box.classList.remove("free");applyRotation();sfx.flip();const s=slots[sel];if(s){s.el.classList.remove("landed");clearTimeout(landT);landT=setTimeout(()=>s.el.classList.add("landed"),420)}}
function placePill(remeasure){const t=$("tabs"),pill=t.querySelector(".tab-pill"),on=t.querySelector(".tab.on");if(remeasure)tabGeo=null;if(!pill||!on){if(pill)pill.style.opacity="0";return}
  if(ON_ANDROID&&t.classList.contains("tabs-x"))return placeStrip(t,pill,on);
  const x=on.offsetLeft,w=on.offsetWidth,h=on.offsetHeight,[l,m,r]=pill.children;
  pill.style.opacity="";pill.style.height=h+"px";pill.style.top=on.offsetTop+"px";pill.style.setProperty("--ph",h+"px");
  l.style.transform=`translateX(${x}px)`;r.style.transform=`translateX(${x+w-h}px)`;m.style.transform=`translateX(${x+h/2}px) scaleX(${Math.max(0,(w-h)/100)})`
  // too many shelves for one line: the bar stays put and only the pill moves; it glides only when the current console or
  // the one beside it would be cut off, and then just far enough to show them (it used to re-centre on every change)
  if(t.scrollWidth>t.clientWidth+1){const prev=on.previousElementSibling,next=on.nextElementSibling,lo=prev&&prev.classList.contains("tab")?prev.offsetLeft:0,hi=next&&next.classList.contains("tab")?next.offsetLeft+next.offsetWidth:t.scrollWidth,cw=t.clientWidth,sl=t.scrollLeft; /* (the first / last tab: all the way to that end) */
    let to=sl;if(lo<sl)to=lo;else if(hi>sl+cw)to=hi-cw;if(w>=cw||x<to||x+w>to+cw)to=x+w/2-cw/2; /* (a window too narrow for a tab and its neighbour: the tab itself, centred) */
    to=Math.max(0,Math.min(t.scrollWidth-cw,to));if(Math.abs(to-sl)>1)t.scrollTo({left:to,behavior:t.classList.contains("still")||reduced()?"auto":"smooth"})}
  tabEdges()}
/* Android's bar: the tab sizes are measured once per build / resize (tabGeo), so changing shelves reads no layout at all:
   the pill and the strip only get new transforms. The strip slides to keep the current console in the middle; the
   edges fade where more tabs wait. A finger can also drag the strip (tabDrag), which a tap on a tab never confuses. */
function measureTabs(t){const strip=t.querySelector(".tab-strip"),bs=[...strip.querySelectorAll(".tab")],cs=getComputedStyle(t);
  const cw=t.clientWidth-parseFloat(cs.paddingLeft)-parseFloat(cs.paddingRight);
  tabGeo={cw,sw:strip.scrollWidth,tabs:bs.map(b=>({x:b.offsetLeft,w:b.offsetWidth,h:b.offsetHeight,y:b.offsetTop}))};return tabGeo}
function stripTo(t,off,still){const G=tabGeo;if(!G)return;tabOff=Math.max(0,Math.min(Math.max(0,G.sw-G.cw),off));const s=t.querySelector(".tab-strip");
  if(s){s.classList.toggle("still",!!still);s.style.transform=`translate3d(${-tabOff}px,0,0)`}
  const over=G.sw>G.cw+1;t.classList.toggle("more-l",over&&tabOff>2);t.classList.toggle("more-r",over&&tabOff<G.sw-G.cw-2)}
function placeStrip(t,pill,on){const G=tabGeo||measureTabs(t),i=[...t.querySelectorAll(".tab")].indexOf(on),g=G.tabs[i];if(!g)return;
  const{x,w,h}=g,[l,m,r]=pill.children;
  pill.style.opacity="";pill.style.height=h+"px";pill.style.top=g.y+"px";pill.style.setProperty("--ph",h+"px");
  l.style.transform=`translateX(${x}px)`;r.style.transform=`translateX(${x+w-h}px)`;m.style.transform=`translateX(${x+h/2}px) scaleX(${Math.max(0,(w-h)/100)})`;
  stripTo(t,x+w/2-G.cw/2,t.classList.contains("still")||reduced())}
if(ON_ANDROID){const t=$("tabs");t.setAttribute("data-hswipe","");let x0=0,o0=0,id=-1,moved=false;
  t.addEventListener("pointerdown",e=>{if(!t.classList.contains("tabs-x"))return;id=e.pointerId;x0=e.clientX;o0=tabOff;moved=false;tabDragged=false});
  t.addEventListener("pointermove",e=>{if(e.pointerId!==id)return;const dx=e.clientX-x0;if(!moved&&Math.abs(dx)<10)return;
    if(!moved){moved=true;tabDragged=true;try{t.setPointerCapture(id)}catch{}}if(!tabGeo)measureTabs(t);stripTo(t,o0-dx,true)});
  const end=e=>{if(e.pointerId!==id)return;id=-1;if(moved)setTimeout(()=>{tabDragged=false},0)}; // the click that ends a drag picks nothing
  t.addEventListener("pointerup",end);t.addEventListener("pointercancel",end)}
function tabEdges(){if(ON_ANDROID&&$("tabs").classList.contains("tabs-x"))return;const t=$("tabs"),over=t.scrollWidth>t.clientWidth+1;t.classList.toggle("more-l",over&&t.scrollLeft>2);t.classList.toggle("more-r",over&&t.scrollLeft<t.scrollWidth-t.clientWidth-2)}
$("tabs").addEventListener("scroll",tabEdges,{passive:true});
if(window.ResizeObserver){let rw=0;new ResizeObserver(e=>{const w=Math.round(e[0].contentRect.width);if(w!==rw){rw=w;placePill(true)}}).observe($("tabs"))} // the bar's width changed (window, brand corner): keep the current console in view
addEventListener("resize",()=>{const t=$("tabs");t.classList.add("still");placePill(true);requestAnimationFrame(()=>t.classList.remove("still"))});
if(document.fonts&&document.fonts.ready)document.fonts.ready.then(()=>{const t=$("tabs");t.classList.add("still");placePill(true);requestAnimationFrame(()=>t.classList.remove("still"))});
// Brand corner (wordmark/logo + count) slides out towards the previous shelf and the new one slides in.
let brandT=0;
function brandOut(dir){const b=$("brand");if(!b)return;clearTimeout(brandT);b.classList.remove("in-l","in-r");b.classList.add(dir>0?"out-l":"out-r")}
// slide the new brand in once its logo is decoded (a first-visit logo goes through cleanLogo; don't flash the old one)
function brandIn(dir){const b=$("brand");if(!b)return;const img=$("sysLogo");const go=()=>{b.classList.remove("out-l","out-r");b.classList.add(dir>0?"in-r":"in-l");void b.offsetWidth;b.classList.remove("in-r","in-l")};
  clearTimeout(brandT);if(img.hidden||!img.dataset.for){go();return}const t0=performance.now();const wait=()=>{if(img.dataset.ready===img.dataset.for||performance.now()-t0>600)go();else brandT=setTimeout(wait,30)};wait()}
let pendingSys=-1,sysT=0;
// setSystem(i) from the current *target* shelf, so two quick presses move two shelves; while a switch is pending
// nothing can launch, inspect or open the museum on the shelf that's about to be replaced.
const sysBusy=()=>pendingSys>=0;
const sysStep=d=>setSystem((pendingSys>=0?pendingSys:sysIdx)+d);
function setSystem(i){
  if(!shelves.length)return;const from=pendingSys>=0?pendingSys:sysIdx;i=(i+shelves.length)%shelves.length;if(i===from||(mode!=="browse"&&mode!=="details"))return;
  const dir=((i-from+shelves.length)%shelves.length)<=shelves.length/2?1:-1;pendingSys=i;const target=shelves[i];
  closeDetails(true);tuneTo(shelves[i]);if(window.Sound&&Sound.theme==="melody")Sound.nextSong("shelf");sfx.sys(dir);track.classList.add("swap");brandOut(dir);$("tabs").querySelectorAll(".tab").forEach((b,k)=>b.classList.toggle("on",k===i));placePill();
  prefetchShelf(shelves[i]);const gen=++swapGen;
  // build once the old shelf has fully faded (track opacity transition is .3s), then fade the new one in only when
  // its front covers are decoded — no bare cases hanging in mid-air while the covers pop in
  clearTimeout(sysT);sysT=setTimeout(async()=>{
    // the shelf picked, found again by identity (the list can change while the old shelf fades: Home appears, a rescan)
    const t=pendingSys>=0&&shelves[pendingSys]===target?pendingSys:shelves.indexOf(target);pendingSys=-1;
    try{
      if(t<0||(mode!=="browse"&&mode!=="details")){renderTabs();brandIn(dir);return}
      sysIdx=t;shelfMovedAt=performance.now();sel=Math.max(0,shelves[t].items.findIndex(isGame));flipped=false;spin=0;spinT=0;cancelAnimationFrame(spinRaf);spinRaf=0;build();brandIn(dir);save();preloadModel();raShelfLoad();tuneTo(cur());ambient(true);rumble(0,.25,60);
      await coversReady(260);
    }catch(e){console.warn("shelf switch",e)}
    finally{if(gen===swapGen&&pendingSys<0)requestAnimationFrame(()=>track.classList.remove("swap"))} // never leave the shelf invisible
  },310);
}
let swapGen=0,prefetched=[];
// start decoding the next shelf's front covers while the old one fades out (same size bucket the cases will ask for)
function prefetchShelf(sh){
  try{const start=Math.max(0,sh.items.findIndex(isGame)),h=bh*OS;prefetched=[];
    for(let k=Math.max(0,start-5);k<Math.min(sh.items.length,start+6);k++){const g=sh.items[k];const u=g&&g.art&&g.art.front;if(!u)continue;const im=new Image();im.decoding="async";im.src=thumb(u,h);im.decode().catch(()=>{});prefetched.push(im)}
  }catch(e){}
}
// the covers around the selection: generated ones drawn now, real ones decoded (or give up after `ms`)
function coversReady(ms){
  const near=slots.filter(s=>s.el&&Math.abs(s.i-sel)<=4);
  for(let k=coverQ.length-1;k>=0;k--){const q=coverQ[k];if(Math.abs(q.s.i-sel)<=4){coverQ.splice(k,1);if(q.s.el)setFront(q.s,q.g,appFrontAsync(stOf(q.g),q.g))}}
  const decoded=()=>Promise.all(near.map(s=>s.img).filter(im=>im&&im.getAttribute("src")).map(im=>im.decode().catch(()=>{})));
  return Promise.race([Promise.all(near.map(s=>s.coverP)).then(decoded),wait(ms)]);
}

/* ---------- details ---------- */
function infoRows(g){
  const s=shelfFor(g);
  if(g.kind==="emulator")return [["Emulator",s.emulatorName],["Program",s.emulator||"Not set"]];
  if(g.kind==="setup")return [["Games found","None yet"],["Fix it in","Settings → "+s.name]];
  if(g.kind==="random")return [...(g.pick?[["Picked",g.pick.game.title],["Shelf",(g.pick.sh||shelves[g.pick.shelf]).name]]:[]),["Picks from",shuffleAll()?"Every shelf":"This shelf"],["Change","Settings → Shelf"]];
  if(g.kind==="app")return [["Type",g.pulse?"Game":"Streaming app"],["Program",g.path],["Returns","When you close the app"]];
  if(g.kind==="steam"){const si=g.info||{};return [...(si.release||si.year?[["Released",fmtRelease(si.release,si.year)]]:[]),...(si.dev?[["Developer",si.dev]]:[]),...(si.pub&&si.pub!==si.dev?[["Publisher",si.pub]]:[]),...(si.genres?[["Genre",si.genres.replace(/;/g,", ")]]:[]),...(si.metacritic?[["Metacritic",si.metacritic+" / 100"]]:[]),["Playtime",g.minutes?`${(g.minutes/60).toFixed(1)} hours`:"Not played yet"],["Last played",fmtDate(g.lastPlayed)],["Install size",fmtSize(g.sizeBytes)],["Steam ID",g.appid]];}
  const i=g.info||{};const rows=[];
  if(i.release||i.year)rows.push(["Released",fmtRelease(i.release,i.year)]);
  if(i.dev)rows.push(["Developer",i.dev]);if(i.pub&&i.pub!==i.dev)rows.push(["Publisher",i.pub]);
  if(i.genres)rows.push(["Genre",i.genres.replace(/;/g,", ")]);
  if(i.players)rows.push(["Players",i.players+(i.coop==="true"?" (co-op)":"")]);
  if(i.esrb)rows.push(["Rating",i.esrb.replace(/ - .*$/,"")]);
  if(i.rating)rows.push(["Community score",i.rating+" / 5"]);
  rows.push(["Region",g.region||"Unknown"],["File",(g.ext||"").toUpperCase()+" · "+fmtSize(g.sizeBytes)+(g.discs&&g.discs.length>1?" · "+g.discs.length+" discs":"")],["Emulator",s.emulatorName]);
  return rows;
}
function fmtRelease(r,y){if(r){const d=new Date(r);if(!isNaN(d))return d.toLocaleDateString(undefined,{year:"numeric",month:"long",day:"numeric"})}return y||""}
function descFor(g){
  const s=cur();
  if((g.kind==="rom"||g.kind==="steam")&&g.info&&g.info.overview)return g.info.overview;
  if(g.kind==="random")return `Spins through your games and lands on one at random. ${showPad()?padWord("L3")+" (press the left stick)":"R"} does the same from anywhere.`;
  if(g.kind==="setup"&&g.addGames)return "Puts more games on the Custom shelf: several programs at once, a whole folder of games, or your Playnite library.";
  if(g.kind==="setup")return `No games found for ${s.name} yet. Opens its page in Settings, where you can pick the folder your games are in.`;
  if(g.kind==="emulator")return `Opens ${s.emulatorName} on its own so you can change controllers, graphics and other settings. Close it to come back here.`;
  if(g.kind==="app")return (g.pulse&&window.__pulseGuide&&window.__pulseGuide(g))||`Opens ${g.title}. Close it to come back to the shelf.`;
  if(g.kind==="steam")return PC_HOW[g.source]||"Starts through Steam, so updates, cloud saves and the Steam overlay work as usual.";
  return g.kind==="rom"?"":`Opens ${g.title}.`;
}
function fillDetails(){
  const g=curGame(),s=shelfFor(g);if(!g)return;window.__pulseCur=g; /* Pulse: the game the details show, for its "Game settings" entry */
  {const d=$("details"),k=String(g.id!=null?g.id:g.title);if(d.dataset.gid!==k){d.dataset.gid=k;d.scrollTop=0;dMenuClose(true);dTab("overview",true)}} /* (beta.7: another game starts on Overview, its More menu closed) */ // another game starts at its top (moving along the shelf kept the last one's scroll); a refill of the same game keeps your place
  {const gs=cur().items.filter(isGame),gi=gs.indexOf(rawItem());$("dKicker").textContent=`${s.name}${cur()!==s?" · via "+cur().name:""}${gi>=0?` · ${gi+1} of ${gs.length}`:""}`} /* counts games only, as the shelf header does (not the Shuffle / emulator tiles) */$("dTitle").textContent=g.title;
  // (beta.7) the game's logo (LaunchBox's clear logo) in place of the plain title once it has loaded; the title stays for
  // screen readers and whenever there's no logo
  {const lg=$("dLogo"),head=lg.closest(".d-head"),u=g.kind==="rom"&&!ANDROID&&g.info&&g.info.logo||"";head.classList.remove("has-logo");lg.hidden=true;lg.onload=lg.onerror=null;
    if(u){lg.alt=g.title;lg.onload=()=>{if(curGame()===g&&lg.naturalWidth>8){lg.hidden=false;head.classList.add("has-logo")}};lg.onerror=()=>{lg.hidden=true};if(lg.getAttribute("src")!==u)lg.src=u;else if(lg.complete&&lg.naturalWidth)lg.onload()}else lg.removeAttribute("src")}
  const desc=descFor(g)||"";const dd0=$("dDesc");dd0.textContent=desc;dd0.hidden=!desc;dd0.classList.add("clamp");{const sa=$("dSecAbout");if(sa)sa.hidden=!desc||g.kind!=="rom"&&g.kind!=="steam"&&!g.guideOn} /* (beta.7: section labels, only over a section with something in it) */
  const more=$("dMore");more.hidden=true;more.textContent="Read more";requestAnimationFrame(()=>{more.hidden=!desc||dd0.scrollHeight<=dd0.clientHeight+4});
  // hero: cover + blurred backdrop; chips for the glanceable facts
  const front=frontUrl(g);$("dCover").src=thumb(front,420);{const hb=$("dHeroBg"),hsrc=(g.art&&(g.art.hero||g.art.front))||front;hb.style.backgroundImage=`url("${thumb(hsrc,g.art&&g.art.hero?768:256)}")`;if(window.bsPlatform&&bsPlatform.os==="android"&&hsrc)tinyBlur(hsrc,d=>{if(d&&curGame()===g&&!hb.closest(".shot"))hb.style.backgroundImage=`url("${d}")`})} /* Android: the 18×24 soft copy the shelf background already uses, so no live 28 px blur filter (android.css) */
  const i=g.info||{},chips=$("dChips");chips.innerHTML="";
  const chip=(t,cls)=>{if(!t)return;const c=document.createElement("span");c.className="chipx"+(cls?" "+cls:"");c.textContent=t;chips.append(c)};
  if(g.kind==="rom"){chip(i.release?String(i.release).slice(0,4):i.year);chip(i.genres&&i.genres.split(";")[0].trim());chip(i.players?(i.players==="1"?"1 player":`${i.players} players`):null);chip(i.esrb?i.esrb.replace(/ - .*$/,""):null,"esrb");chip(i.rating?`★ ${(+i.rating).toFixed(1)}`:null,"score");chip(i.metacritic?`Metacritic ${i.metacritic}`:null,"score");if(g.compat)chip(`Xenia: ${g.compat}`,/playable|gameplay/i.test(g.compat)?"score":"esrb")}
  else if(g.kind==="steam"){chip(PC_SRC[g.source]||"Steam");chip(yearOf(g));chip(genreOf(g));chip(i.metacritic?`Metacritic ${i.metacritic}`:null,"score");chip(g.minutes?`${(g.minutes/60).toFixed(1)} h played`:"Not played yet")}
  else if(g.kind==="app")chip(g.pulse?"Game":"Streaming app");else if(g.kind==="emulator")chip("Emulator");else if(g.kind==="random")chip("Shuffle");
  // your history with this game (launches / time / last played), kept by Boxshelf
  // (beta.7) as tiles under the header: time played, last played, launches (Steam's own hours for a Steam game)
  {const hs=HIST[g.id],st=$("dStats");st.innerHTML="";const show=g.kind==="rom"||g.kind==="steam";st.hidden=!show;
    if(show){const mins=g.kind==="steam"&&g.minutes?g.minutes:0;const tiles=[["Played",hs&&hs.ms?fmtPlay(hs.ms):mins?`${(mins/60).toFixed(1)} h`:"Not yet"],["Last played",hs&&hs.last?fmtDay(hs.last/1000):"Never"],["Launches",String(hs&&hs.count||0)]];
      tiles.forEach(([k,v])=>{const d=document.createElement("div");d.className="d-stat";const a=document.createElement("span");a.textContent=k;const b=document.createElement("b");b.textContent=v;d.append(a,b);st.append(d)})}}
  // screenshots strip (LaunchBox); the chosen one fills the hero
  const shots=(g.kind==="rom"&&i.shots)||[];shotSel=0;const ds=$("dShots");ds.innerHTML="";ds.hidden=!shots.length;$("dCover").closest(".d-hero").classList.toggle("shot",!!shots.length);
  shots.forEach((u,k)=>{const d=document.createElement("button");d.type="button";d.className="d-shot"+(k===0?" on":""); /* (a button: the controller can land on it in Media) */const im=document.createElement("img");im.loading="lazy";im.decoding="async";im.alt="";im.src=u;d.append(im);d.addEventListener("click",()=>showShot(k));ds.append(d)}); /* (beta.9: decoded off the main thread) */
  if(shots.length)heroBgSoon(shots[0],g); // (beta.9: decoded first, then swapped in: never a half-drawn or hitching backdrop)
  $("dTrailer").hidden=!(g.kind==="rom"&&i.video);
  // (beta.7) Media: says when there's nothing there yet (a preview found later fills it); the tab is marked when there's something
  {const has=!!shots.length||!$("dTrailer").hidden;$("dNoMedia").hidden=has;$("dTabMedia").classList.toggle("has",has)}
  const grid=$("dGrid");grid.innerHTML="";
  const skip=new Set(g.kind==="rom"?["Released","Genre","Players","Rating","Community score","Format","File"]:[]);
  infoRows(g).filter(([k,v])=>!skip.has(k)&&v!=null&&String(v).trim()!=="").forEach(([k,v])=>{ /* (beta.7: a fact with no value isn't shown as an empty box) */const box=document.createElement("div");if(/program|file/i.test(k))box.className="wide";const dt=document.createElement("dt"),dd=document.createElement("dd");dt.textContent=k;dd.textContent=v;box.append(dt,dd);grid.append(box)});
  // what's on file for this game: the box (front, back, spine), the disc / cartridge label, the manual
  {const box=$("dArt"),row=$("dArtRow");row.innerHTML="";const a=g.art||{},show=g.kind==="rom";box.hidden=!show;$("dTabCopy").hidden=!show;
    if(show){const media=s&&(MEDIA[s.id]||{}).kind==="cart"?"Cartridge":s&&s.id==="psp"?"UMD":"Disc",man=Array.isArray(a.manual)?a.manual.length:0;
      const items=[["Front cover",!!a.front],["Back cover",!!a.back],["Spine",!!a.spine],[media+" art",!!a.disc],["Manual",man>0,man?`${man} pages`:g.manualMiss?"none found online":""]]; /* (beta.9: manualMiss, the search looked and found none) */
      const full=a.front&&a.back&&a.spine;
      items.forEach(([l,ok,extra])=>{const c=document.createElement("span");c.className="d-art-chip"+(ok?" ok":"");c.textContent=(ok?"✓ ":"")+l+(extra?` · ${extra}`:"");row.appendChild(c)});
      const sum=document.createElement("div");sum.className="d-art-sum";sum.textContent=full&&a.disc&&man?"Complete: full box, disc and manual":full?"Full box art"+(a.disc?"":" · no disc art")+(man?"":g.manualMiss?" · no manual found online":" · no manual"):"Missing pictures can be fetched in Settings → Box art & manuals → Download missing art now";row.appendChild(sum)}}
  if(g.kind==="rom"&&i.genres&&i.genres.includes(";")){const box=document.createElement("div");box.className="wide";const dt=document.createElement("dt"),dd=document.createElement("dd");dt.textContent="Genres";dd.textContent=i.genres.replace(/;/g,", ");box.append(dt,dd);grid.append(box)}
  {const si=$("dSecInfo");if(si)si.hidden=!grid.childElementCount}
  let cmd="";
  if(g.kind==="rom")cmd=`${quote(gameEmu(g)||s.emulator||s.emulatorName)} ${argsPreview(s.gameArgs,g.file)}`;
  else if(g.kind==="emulator")cmd=quote(s.emulator||"");
  else if(g.kind==="steam")cmd=g.source&&g.source!=="steam"?(PC_EXT[g.source]||g.title):`steam://rungameid/${g.appid}`;
  else if(g.kind==="app")cmd=quote(g.path);
  $("dCmd").textContent=cmd;$("dCmd").hidden=!cmd;
  const how=$("dHow");if(how){how.textContent=g.kind==="rom"?`Plays in ${s.emulatorName}${s.bootOnLaunch&&!s.useIpl&&((themes[s.id]||{}).boot||s.insertAnim===false)?" after the boot "+((themes[s.id]||{}).boot?"video":"intro"):""}. Close the emulator to come back here.`:"";how.hidden=g.kind!=="rom"}
  raDetails(g);
  $("dPlay").textContent=g.kind==="rom"||g.kind==="steam"?"Play":"Open";
  $("dInspect").hidden=g.kind!=="rom";$("dManual").hidden=g.kind!=="rom";$("dManual").querySelector("span").textContent=hasManual(g)?"Read manual":"Read booklet"; /* (beta.7: an icon button: only its label changes) */
  $("dEmu").hidden=!(g.kind==="rom"&&s.hasEmulator);$("dEmu").textContent=`Open ${s.emulatorName||"emulator"}`;
  {const pc=g.kind==="steam"&&!!api.pcCoverOptions,b=$("dBoxArt"),p=$("dBoxArtPc");if(b)b.hidden=!(g.kind==="rom"&&api.artStudio&&g.file);if(p)p.hidden=!pc;} /* (beta.9) a PC game's Box art sits beside Play (it has no manual or Inspect), not in More */{const b=$("dRename");if(b)b.hidden=!(api.renameGame&&((g.kind==="rom"&&g.file)||g.kind==="steam"));} /* (beta.5: PC games too, through the cover picker) */
  {const b=$("dCols");if(b)b.hidden=!isGame(g)||g.kind==="app";}
  {const b=$("dChooseManual");if(b){b.hidden=!(g.kind==="rom"&&api.manualStudio&&g.file);b.textContent=hasManual(g)?"Choose manual…":"Find a manual…";b.title=!hasManual(g)&&g.manualMiss?`Boxshelf looked online on ${new Date(g.manualMiss).toLocaleDateString()} and found no manual for this game. Search or add one here.`:""}}
  {const b=$("dGameEmu");if(b){const own=g.kind==="rom"?gameEmu(g):"";b.hidden=!(g.kind==="rom"&&api.gameEmulator);b.textContent=own?(emuLabel(own).length>16?emuLabel(own).slice(0,15)+"…":emuLabel(own)):"Emulator";b.title=own?`Runs in ${own} (just this game). Press to change.`:`Runs in the shelf's ${s.emulatorName||"emulator"}. Press to pick a different build for just this game.`;b.classList.toggle("own",!!own)}}
  renderHints(); // Y's prompt follows this game's trailer / achievements row (updateInfo drew the bar before this fill)
  pvSchedule(); // (beta.7) its video preview, after a rest
}
function openDetails(){if(mode!=="browse"||!curGame()||emptyShelf())return;uiCrumb("Details: "+curGame().title);
  // (beta.7) the shelf's own preview already playing for this game carries straight on behind Details (no restart)
  {const g=curGame(),k=String(g.id!=null?g.id:g.title);if(PV&&PV.bkey===k&&app.classList.contains("pv-on")){const v=$("pvBack");clearTimeout(PV.bt);PV.bkey="";PV.key=k;PV.r={type:"file",url:v.currentSrc||v.src};app.classList.add("pv-det")}}
  PV.openAt=performance.now(); // (beta.9: opening Details looks for the trailer at once; the rest delay is for moving between games in it)
  mode="details";fillDetails();
  // (the shelf's own preview carries on in Overview's box, from where it was, with this game's fan art behind the case)
  if(!ANDROID&&PV.key&&PV.r&&dTabNow()===MEDIA_TAB){const t0=$("pvBack").currentTime||0,dv=$("dVideo"),k0=PV.key;pvBoxStart(k0);dArtOn(curGame(),k0);const u0=dv.getAttribute("src");
    if(t0>0&&u0)dv.addEventListener("loadedmetadata",()=>{try{if(PV.key===k0&&dv.getAttribute("src")===u0&&t0<dv.duration-.5)dv.currentTime=t0}catch{}},{once:true})} /* (only for that same video: not a later game's) */$("details").scrollTop=0;app.classList.add("details-open");$("details").setAttribute("aria-hidden","false");play_("panel");padSet($("details"),$("dPlay"));renderHints()}
// ---------- (beta.7) video previews (main/previews.js) ----------
// Details: rest on a game (Settings → Video previews → Start after) and its preview fades into a 16:9 box under the
// title: a video file you have or Steam's trailer clip plays right here; a YouTube trailer plays in YouTube's own player,
// laid over the same box by main (hidden while the panel scrolls, gone the moment Details is). The box only opens once
// there's a picture. Shelf (Windows, "Details and shelf"): the same clip, files only, softly behind the shelf while you
// rest on a game; it stops the moment you move. Nothing starts while the shelf slides (BS_BUSY), nothing is preloaded.
var PV={t:0,key:"",yt:"",st:0,bt:0,bkey:""}; // (var: renderHints / updateInfo can run before this line on start-up)
const pvUi=k=>cfgInfo&&cfgInfo.config&&cfgInfo.config.ui?cfgInfo.config.ui[k]:undefined;
const pvMode=()=>pvUi("previews")||"off";
const pvDelay=()=>Math.max(.5,+pvUi("previewDelay")||1.5)*1000;
const pvCan=g=>!!(g&&api.previewFind&&(g.kind==="rom"||g.kind==="steam"));
function pvAsk(g){return api.previewFind({systemId:g.kind==="rom"?(shelfFor(g)||{}).id:null,file:g.kind==="rom"?g.file:null,title:g.fileTitle||g.title,id:g.id,youtube:g.info&&g.info.video||null}).catch(()=>null)}
function pvRect(){const b=$("dMediaIn"),d=$("details");if(!b||!d)return null;const r=b.getBoundingClientRect(),p=d.getBoundingClientRect();
  if(r.width<20||r.height<20||r.top<p.top-1||r.bottom>p.bottom+1)return null;return {x:r.left,y:r.top,w:r.width,h:r.height}}
// (beta.7, Details redone) a video file or Steam clip fills the background behind the case (#pvBack, faded out toward the
// panel); the Media tab plays it in its framed box instead (the background steps back while it does). YouTube can't sit
// behind the app (its player is a view on top), so a YouTube trailer plays in the Media tab's box only.
// Before any video, the game's fan art (LaunchBox) is the backdrop (the video's poster); not on the Thor.
function pvVid(v,url,k,onPlay){uiCrumb("Video: "+String(url).split(/[\\/]/).pop().slice(0,60));v.muted=!pvUi("previewSound");v.volume=v.muted?0:Math.min(1,+(pvUi("volume")??.7));v.onplaying=()=>{if(PV.key===k)onPlay()};v.onerror=()=>{if(PV.key===k){v.onerror=null;pvBoxStop();pvBgStop()}};v.src=url;v.play().catch(()=>{})}
function pvBgStop(){app.classList.remove("pv-det");const v=$("pvBack");if(v&&v.getAttribute("src")&&!PV.bkey){v.onplaying=v.onerror=null;v.pause();v.removeAttribute("src");try{v.load()}catch{}}}
function pvBoxStop(){clearTimeout(PV.lt);const box=$("dMedia"),v=$("dVideo");if(box)box.classList.remove("on","yt","ready");if(PV.yt){PV.yt="";if(api.previewYtStop)api.previewYtStop()}
  if(v&&v.getAttribute("src")){v.onplaying=v.onerror=null;v.pause();v.removeAttribute("src");try{v.load()}catch{}}}
function pvStop(){clearTimeout(PV.t);clearTimeout(PV.st);PV.key="";PV.r=null;pvBoxStop();pvBgStop();dArtOff()}
function pvSchedule(){const g=curGame(),k=g?String(g.id!=null?g.id:g.title):"";if(k&&k===PV.key)return;pvStop();
  if(mode!=="details"||!g)return;dArtOn(g,k);if(pvMode()==="off"||!pvCan(g))return;PV.key=k;PV.t=setTimeout(()=>pvStart(g,k),!ANDROID&&performance.now()-(PV.openAt||0)<900?300:pvDelay())} // (300 ms: after the panel has slid in)
async function pvStart(g,k){
  if(PV.key!==k||mode!=="details")return;
  if(window.BS_BUSY&&BS_BUSY()){PV.t=setTimeout(()=>pvStart(g,k),300);return}
  const r=await pvAsk(g);if(PV.key!==k||mode!=="details"||!r)return;PV.r=r;
  if(r.type==="pending"){pvBoxWait(k);return} // (beta.8) a console trailer clip is being made: api.onPreviewClip plays it when it's ready
  if(r.type==="youtube"&&!api.previewYt)return; // (beta.9: Android has its own YouTube player too, android-api.js)
  $("dTabMedia").classList.add("has");$("dNoMedia").hidden=true;
  if(dTabNow()===MEDIA_TAB)pvBoxStart(k);else if(r.type==="file")pvVid($("pvBack"),r.url,k,()=>app.classList.add("pv-det"))}
// the Media tab's box: the same video framed, or YouTube's player laid over it
// (beta.9, Windows) the box opens at once with this game's art and a soft shimmer, so you see the trailer is coming (and
// nothing below jumps when it starts); the Thor's opens once the picture is there, as before
function pvBoxWait(k){clearTimeout(PV.lt);if(ANDROID||PV.key!==k||dTabNow()!==MEDIA_TAB)return;$("dMediaIn").style.backgroundImage=$("dHeroBg").style.backgroundImage;$("dMedia").classList.add("on");
  PV.lt=setTimeout(()=>{if(PV.key===k&&!$("dMedia").classList.contains("ready"))pvBoxStop()},45000)} // (a clip that never comes: the box closes again)
function pvBoxStart(k){const r=PV.r;if(!r||PV.key!==k)return;const box=$("dMedia"),tag=$("dMediaTag");$("dMediaIn").style.backgroundImage=$("dHeroBg").style.backgroundImage; // (this game's art under the picture as it fades in)
  if(r.type==="file"){pvBgStop();pvBoxWait(k);tag.textContent=r.steam?"Steam trailer":r.yt?"Trailer":"Preview";pvVid($("dVideo"),r.url,k,()=>box.classList.add("on","ready"))}
  // (beta.9, Windows) Overview never loads YouTube's own player by itself: it's a heavy web page laid over the app, and with the
  // trailer at the top of Overview it loaded on every Details open, lagged and took the controller away while it did. The
  // box waits for the console trailer clip instead (made in the background, a plain video); full screen still opens YouTube
  else if(r.type==="youtube"&&!ANDROID){if(r.pending)pvBoxWait(k)}
  else if(r.type==="pending")pvBoxWait(k); // (back on Overview while the clip is still being made: the waiting box again)
  else if(r.type==="youtube"){PV.yt=r.id;tag.textContent="Trailer";box.classList.add("on","yt");
    // the box opens first (its poster is this game's art); YouTube's player is laid over it once the box has its size
    PV.st=setTimeout(()=>{if(PV.key!==k||PV.yt!==r.id)return;const rc=pvRect();if(rc)api.previewYt(r.id,rc,!pvUi("previewSound"))},380)}}
// switching tabs: into Media the preview moves into the box; out of it, back behind the case
function pvTab(from,to){if(!PV.key||!PV.r)return;if(to===MEDIA_TAB)pvBoxStart(PV.key);else if(from===MEDIA_TAB){pvBoxStop();if(PV.r.type==="file"){const k=PV.key;pvVid($("pvBack"),PV.r.url,k,()=>app.classList.add("pv-det"))}}}
// the fan-art backdrop: decoded first, then faded in as the video's poster (no flash, no half-drawn picture)
function dArtOn(g,k){if(ANDROID||g.kind!=="rom")return;const u=g.info&&g.info.bg;if(!u)return;const im=new Image();im.src=u;im.decode().then(()=>{if(mode!=="details"||curGame()!==g)return;const v=$("pvBack");if(!v.getAttribute("src"))v.poster=u;app.classList.add("d-art-on")}).catch(()=>{})}
function dArtOff(){if(!app.classList.contains("d-art-on"))return;app.classList.remove("d-art-on");const v=$("pvBack");setTimeout(()=>{if(!app.classList.contains("d-art-on")&&!app.classList.contains("pv-on"))v.removeAttribute("poster")},700)}
// ---------- (beta.7) Details' tabs (Overview, Media, Your copy, Info) and its More menu ----------
const dTabNow=()=>{const b=document.querySelector("#details .d-body");return b?b.dataset.show:"overview"};
// (beta.9, Windows) the trailer sits at the top of Overview with the info under it (it had a Media tab of its own): the
// box, the full-screen button and the screenshots move into Overview, and the preview plays framed there. The Thor keeps
// its Media tab (MEDIA_TAB "media")
const MEDIA_TAB=ANDROID?"media":"overview";
if(!ANDROID){const ov=document.querySelector('#details .d-pane[data-pane="overview"]');if(ov){ov.prepend($("dMedia"),$("dTrailer"),$("dShots"))}$("dTabMedia").hidden=true;$("details").classList.add("d-one")}
function dTab(t,quiet){const body=document.querySelector("#details .d-body");if(!body)return;
  if(typeof t==="number"){const L=[...$("dTabs").querySelectorAll("button")].filter(b=>!b.hidden),i=L.findIndex(b=>b.dataset.tab===body.dataset.show);t=L[((i<0?0:i)+t+L.length)%L.length].dataset.tab}
  const was=body.dataset.show;if(was===t&&!quiet)return;body.dataset.show=t;
  $("dTabs").querySelectorAll("button").forEach(b=>{const on=b.dataset.tab===t;b.classList.toggle("on",on);b.setAttribute("aria-selected",String(on))});
  if(quiet)return;const d=$("details"),tb=$("dTabs");if(d.scrollTop>tb.offsetTop)d.scrollTop=Math.max(0,tb.offsetTop-8); // (back to the top of the tab, keeping the header's place)
  play_("tick",{dir:1});pvTab(was,t);if(PV.yt&&api.previewYtMove){const rc=pvRect();api.previewYtMove(rc,!!rc)}}
$("dTabs").addEventListener("click",e=>{const b=e.target.closest("button[data-tab]");if(b)dTab(b.dataset.tab)});
function dMenuOpen(){const m=$("dMenu");if(!m.hidden)return;m.hidden=false;$("dMoreBtn").setAttribute("aria-expanded","true");play_("open");const first=[...m.querySelectorAll("button")].find(b=>!b.hidden);if(first)padSet($("details"),first)}
function dMenuClose(quiet){const m=$("dMenu");if(!m||m.hidden)return;m.hidden=true;$("dMoreBtn").setAttribute("aria-expanded","false");if(!quiet){play_("back");if(mode==="details")padSet($("details"),$("dMoreBtn"))}}
$("dMoreBtn").addEventListener("click",()=>$("dMenu").hidden?dMenuOpen():dMenuClose());
$("dMenu").addEventListener("click",e=>{if(e.target.closest("button"))setTimeout(()=>dMenuClose(true),0)}); // (an item does its thing; the menu goes)
document.addEventListener("pointerdown",e=>{const m=$("dMenu");if(m&&!m.hidden&&!m.contains(e.target)&&!$("dMoreBtn").contains(e.target))dMenuClose(true)},true);
// YouTube's player says how it went: playing (the box shows it) or an error (the box closes again)
if(api.onPreviewYt)api.onPreviewYt(d=>{if(!d||d.id!==PV.yt)return;if(d.state==="playing")$("dMedia").classList.add("ready");else if(d.state==="error"){const k=PV.key;pvStop();PV.key=k}});
// (beta.8) a console trailer clip is ready (main/ytclips.js): if you're still on that game it plays like a Steam clip,
// behind the case in Details or behind the shelf. YouTube's player already playing in the Media tab is left alone (the
// clip takes over next time the tab opens).
if(api.onPreviewClip)api.onPreviewClip(d=>{if(!d||!d.id||!d.url)return;
  if(mode==="details"&&PV.key&&PV.r&&PV.r.id===d.id&&(PV.r.type==="youtube"||PV.r.type==="pending")){const k=PV.key,ytOn=!!PV.yt;PV.r={type:"file",url:d.url,yt:d.id};
    $("dTabMedia").classList.add("has");$("dNoMedia").hidden=true;
    if(dTabNow()===MEDIA_TAB){if(!ytOn)pvBoxStart(k)}else pvVid($("pvBack"),d.url,k,()=>app.classList.add("pv-det"))}
  if(PV.bwait&&PV.bwait.id===d.id&&PV.bkey===PV.bwait.k&&mode==="browse"){const k=PV.bkey;PV.bwait=null;pvBackPlay(k,d.url)}});
// the panel scrolls: YouTube's player steps aside and comes back where the box now is
$("details").addEventListener("scroll",()=>{if(!PV.yt||!api.previewYtMove)return;api.previewYtMove(null,false);clearTimeout(PV.st);PV.st=setTimeout(()=>{if(!PV.yt)return;const rc=pvRect();api.previewYtMove(rc,!!rc)},180)},{passive:true});
addEventListener("resize",()=>{if(PV.yt&&api.previewYtMove){const rc=pvRect();api.previewYtMove(rc,!!rc)}});
// the shelf's own preview, behind the cases
function pvBackStop(){clearTimeout(PV.bt);PV.bkey="";PV.bwait=null;app.classList.remove("pv-on");const v=$("pvBack");if(v&&v.getAttribute("src")){const was=v.src;setTimeout(()=>{if(v.src===was&&!PV.bkey){v.pause();v.removeAttribute("src");try{v.load()}catch{}}},650)}}
// (beta.9, Windows, previews in Details only) resting 2 s on a game on the shelf quietly asks for its preview: a console
// trailer clip or Steam's is then made in the background (main), so it's often ready by the time Details opens. Nothing
// plays; never while the shelf moves
function pvWarm(){clearTimeout(PV.wt);if(ANDROID||pvMode()!=="details"||mode!=="browse")return;const g=curGame();if(!pvCan(g)||!(g.kind==="steam"||(g.info&&g.info.video)))return;
  PV.wt=setTimeout(()=>{if(mode!=="browse"||curGame()!==g)return;if(window.BS_BUSY&&BS_BUSY()){pvWarm();return}pvAsk(g)},2000)}
function pvBackSchedule(){if(!PV)return;if(pvMode()!=="shelf"||ANDROID){if(PV.bkey)pvBackStop();pvWarm();return}
  const g=curGame(),k=g?String(g.id!=null?g.id:g.title):"";if(k&&k===PV.bkey&&mode==="browse")return;pvBackStop();
  if(mode!=="browse"||!pvCan(g))return;PV.bkey=k;PV.bt=setTimeout(()=>pvBackStart(g,k),pvDelay()+500)}
async function pvBackStart(g,k){
  if(PV.bkey!==k||mode!=="browse")return;if(window.BS_BUSY&&BS_BUSY()){PV.bt=setTimeout(()=>pvBackStart(g,k),300);return}
  const r=await pvAsk(g);if(PV.bkey!==k||mode!=="browse"||!r)return;
  if(r.type!=="file"){if(r.pending||r.type==="pending")PV.bwait={k,id:r.id};return} // (beta.8) its console trailer clip plays when ready
  pvBackPlay(k,r.url)}
function pvBackPlay(k,url){if(PV.bkey!==k||mode!=="browse")return;if(window.BS_BUSY&&BS_BUSY()){PV.bt=setTimeout(()=>pvBackPlay(k,url),300);return}
  uiCrumb("Shelf video: "+String(url).split(/[\\/]/).pop().slice(0,60));const v=$("pvBack");v.muted=true;v.onplaying=()=>{if(PV.bkey===k)app.classList.add("pv-on")};v.src=url;v.play().catch(()=>{})}
// people who set Boxshelf up before previews existed: told once, on the shelf, where to turn them on
// one UI setting saved on top of the settings as they are now (the shelf's own copy may be older than what Settings or a
// test saved since: saving that whole copy would undo them)
async function uiSet(k,v){if(cfgInfo&&cfgInfo.config)cfgInfo.config.ui[k]=v;try{const c=(await api.getConfig()).config;c.ui[k]=v;await api.saveConfig(c)}catch{}}
function pvHint(n=0){const ui=cfgInfo&&cfgInfo.config&&cfgInfo.config.ui;if(!ui||!api.previewFind||ANDROID)return;if(!cfgInfo.config.setupDone||ui.previewHint||(ui.previews||"off")!=="off")return;
  if(mode!=="browse"||sysBusy()){if(n<20)setTimeout(()=>pvHint(n+1),15000);return}
  uiSet("previewHint",true);toast("New: video previews. Turn them on in Settings → Video previews.")}
setTimeout(()=>pvHint(),9000);
// (beta.8) previews and console trailers on, but the trailer helper (main/ytclips.js) isn't there yet: say so once on the
// shelf, with the button that gets it (otherwise console games quietly play nothing on the shelf)
async function clipHint(){const ui=cfgInfo&&cfgInfo.config&&cfgInfo.config.ui;if(!ui||ANDROID||!api.clipStatus||!api.clipHelper||!cfgInfo.config.setupDone)return;
  if(ui.clipHint||(ui.previews||"off")==="off"||ui.previewClips===false)return;
  let s=null;try{s=await api.clipStatus()}catch{}if(!s||!s.supported||s.helper||s.getting)return;
  uiSet("clipHint",true);
  noteShow({kick:"Video previews",title:"Console games can play their trailers too",
    sub:"Boxshelf needs a free helper (yt-dlp, about 25 MB) to save a short clip of each console game's trailer, so it plays on the shelf and in Details like Steam games do. It's fetched once.",
    primary:["Get the helper",()=>{toast("Getting the trailer helper…");api.clipHelper().then(ok=>toast(ok?"Console trailers are ready: rest on a console game to see its trailer":"Couldn't get the trailer helper. Try again in Settings → Video previews.")).catch(()=>{})}],
    secondary:["Not now",()=>toast("You can get it any time in Settings → Video previews.")]})}
setTimeout(()=>clipHint(),14000);
// (beta.8) Steam Deck in Desktop Mode, Boxshelf not started by Steam: the Deck's controller is a keyboard and mouse there,
// so B is Esc and most emulators quit their game on it. Said once, with the way out: start Boxshelf from Steam (Game Mode)
async function deckAdd(){toast("Adding Boxshelf to Steam (Steam closes and opens again)…");let r=null;try{r=await api.addToSteam(true)}catch{}
  const why={"no-steam":"Steam hasn't been set up on this Deck yet","not-appimage":"Boxshelf isn't running from its AppImage","steam-would-not-close":"Steam didn't close: close it and try again in Settings → System","game-mode":"it's already in Steam"};
  toast(r&&r.ok?(r.updated?"Boxshelf's Steam entry is up to date. Start it from Steam (Game Mode)":"Boxshelf is in Steam now: start it from your Library in Game Mode (Non-Steam)"):"Couldn't add Boxshelf to Steam"+(r&&why[r.why]?": "+why[r.why]:"")+".");return r}
async function deckHint(){const ui=cfgInfo&&cfgInfo.config&&cfgInfo.config.ui;if(!ui||ANDROID||!api.deckInfo||!cfgInfo.config.setupDone||ui.deckHint)return;
  let d=null;try{d=await api.deckInfo()}catch{}if(!d||!d.deck||d.steam||d.gameMode)return;
  uiSet("deckHint",true);
  const steps=d.inSteam?["Switch to Game Mode and start Boxshelf from your Library (Non-Steam)","To leave a game, hold Select + Start for a moment"]
    :["Add Boxshelf to Steam (the button below does it for you)","Switch to Game Mode and start it from your Library (Non-Steam)","To leave a game, hold Select + Start for a moment"];
  noteShow({wide:true,kick:"Steam Deck",title:"Start Boxshelf from Steam for your games' controls",
    sub:"Started outside Steam, the Deck's controller works as a keyboard and mouse here, so B is the Esc key and most emulators quit their game on it. Started from Steam, B is just B.",items:steps,
    primary:d.inSteam||!d.canAdd?["Got it",null]:["Add Boxshelf to Steam",()=>deckAdd()],secondary:d.inSteam||!d.canAdd?null:["Not now",()=>toast("You can add it any time in Settings → System.")]})}
setTimeout(()=>deckHint(),11000);
// ---------- (beta.7) notes on the shelf: "a new version is out" (with its highlights) and "What's new" after updating ----------
// One card in Settings' look over the shelf, its own mode ("note"): A picks the lit button, B / Esc closes, left / right
// moves between the two buttons, the same as every other panel. Only shown on the shelf while nothing moves; a note that
// has to wait tries again a little later. The notes themselves: renderer/whatsnew.js (built in), the release's own text
// for a newer version (GitHub), shown as plain text only.
const WN=()=>Array.isArray(window.BS_WHATSNEW)?window.BS_WHATSNEW:[];
const appVer=()=>cfgInfo&&cfgInfo.version||"";
const wnEntry=v=>WN().find(e=>ANDROID?e.android===v:e.version===v)||null;
// a release's Markdown, down to its bullet points as plain text
function mdBullets(md){return String(md||"").split(/\r?\n/).map(l=>/^\s*[-*•]\s+(.+)$/.exec(l)).filter(Boolean).map(m=>m[1].replace(/\[([^\]]+)\]\([^)]*\)/g,"$1").replace(/\*\*|__|`/g,"").replace(/<[^>]*>/g,"").trim()).filter(Boolean).map(s=>s.length>140?s.slice(0,137)+"…":s)}
const NOTE={el:null,btns:[],i:0,queue:[],t:0};
function noteShow(o){
  if(NOTE.el||mode!=="browse"||(typeof qm!=="undefined"&&qm.open)/* (beta.8: never over the open quick menu) */||sysBusy()||(window.BS_BUSY&&BS_BUSY())||document.querySelector("#setup.on, #settings.on")){o.tries=(o.tries||0)+1;if(o.tries<40){NOTE.queue.push(o);clearTimeout(NOTE.t);NOTE.t=setTimeout(noteNext,6000)}return}
  const el=document.createElement("div");el.className="note-card"+(o.wide?" wide":"");el.setAttribute("role","dialog");el.setAttribute("aria-label",o.title);
  const add=(tag,cls,text)=>{const e=document.createElement(tag);e.className=cls;e.textContent=text;el.append(e);return e};
  add("div","nc-kick",o.kick);add("div","nc-title",o.title);if(o.sub)add("div","nc-sub",o.sub);
  if(o.items&&o.items.length){const ul=document.createElement("ul");ul.className="nc-list";o.items.forEach(t=>{const li=document.createElement("li");li.textContent=t;ul.append(li)});el.append(ul)}
  const row=document.createElement("div");row.className="nc-btns";el.append(row);
  NOTE.btns=[o.primary,o.secondary].filter(Boolean).map(([label,run],i)=>{const b=document.createElement("button");b.type="button";b.className="btn"+(i===0?" primary":"");b.textContent=label;b.addEventListener("click",()=>noteClose(run));row.append(b);return b});
  NOTE.el=el;NOTE.i=0;mode="note";app.classList.add("note-open");app.append(el);noteLight();play_("panel");renderHints();requestAnimationFrame(()=>el.classList.add("on"))}
function noteNext(){if(NOTE.el)return;const o=NOTE.queue.shift();if(o)noteShow(o)}
function noteLight(){NOTE.btns.forEach((b,i)=>b.classList.toggle("pad-focus",i===NOTE.i));const b=NOTE.btns[NOTE.i];if(b)try{b.focus({preventScroll:true})}catch{}}
function noteMove(d){if(NOTE.btns.length<2)return;NOTE.i=(NOTE.i+d+NOTE.btns.length)%NOTE.btns.length;noteLight();play_("tick",{dir:d})}
function notePick(){const b=NOTE.btns[NOTE.i];if(b)b.click()}
function noteClose(run){const el=NOTE.el;if(!el)return;NOTE.el=null;NOTE.btns=[];el.classList.remove("on");setTimeout(()=>el.remove(),260);mode="browse";app.classList.remove("note-open");renderHints();if(typeof run==="function")setTimeout(run,60);else sfx.back();
  if(NOTE.queue.length){clearTimeout(NOTE.t);NOTE.t=setTimeout(noteNext,900)}}
// a newer version on GitHub (main/updates.js): its highlights and the way to its notes, once per version
function noteUpdate(r){if(!r||!r.newer||!r.latest)return;let seen="";try{seen=localStorage.getItem("updateSeen")||""}catch{}if(seen===r.latest)return;
  try{localStorage.setItem("updateSeen",r.latest)}catch{}const hl=mdBullets(r.notes).slice(0,3);
  noteShow({kick:"Update available",title:`Boxshelf ${r.latest} is out`,sub:hl.length?"":"A new version is ready to download.",items:hl,
    primary:["See what's new",()=>{if(window.Setup)Setup.open({page:"whatsnew"})}],secondary:["Later",null]})}
// the first start of a new version: its highlights, once (a fresh install just remembers the version)
function wnCheck(n=0){if(!cfgInfo||!cfgInfo.config){if(n<20)setTimeout(()=>wnCheck(n+1),1500);return}const v=appVer(),ui=cfgInfo.config.ui;if(!v||ui.lastSeenVersion===v)return;
  if(!/\d/.test(v))return; /* (a build without a version number, like the Android one's "Android") */const first=!cfgInfo.config.setupDone,e=wnEntry(v);uiSet("lastSeenVersion",v);if(first||!e)return;
  noteShow({wide:true,kick:"Updated",title:`What's new in ${v}`,sub:e.title,items:(e.highlights||[]).slice(0,5),primary:["Got it",null],secondary:["All the notes",()=>{if(window.Setup)Setup.open({page:"whatsnew"})}]})}
setTimeout(()=>wnCheck(),5000);
// anything that takes over the screen (Settings, the quick menu, a game starting, the museum) ends both
function pvGuard(){if(!PV)return;if(PV.key&&(mode!=="details"||document.querySelector("#setup.on, #settings.on")))pvStop();if(PV.bkey&&mode!=="browse")pvBackStop()}
addEventListener("blur",()=>{if(PV.yt&&api.previewYtMove)api.previewYtMove(null,false)});addEventListener("focus",()=>{if(PV.yt&&api.previewYtMove){const rc=pvRect();api.previewYtMove(rc,!!rc)}});
function closeDetails(silent){if(mode!=="details")return;dMenuClose(true);pvStop();app.classList.remove("pv-on");padSet($("details"),null);mode="browse";app.classList.remove("details-open");$("details").setAttribute("aria-hidden","true");renderHints();if(!silent)sfx.back()}

/* ---------- Switch 2: which build ---------- */
// People keep several Eglantine forks (a game can run better in one than another): Play on a Switch 2 game asks which
// build to start it in, the game's last pick first. A build that can't open a .nup game is greyed out. Pad (up / down,
// A, B), keys (arrows, Enter, Esc), mouse and touch. Resolves to the exe, "" when there are no builds to choose from
// (the shelf's emulator, as before), or null for Back.
let bp=null,nextBuild=null;
// (Settings → Switch 2 → Build set to one build (askBuild false): Play starts in it, or in the game's own Details choice)
const needsBuild=(g,s)=>!!(g&&s&&g.kind==="rom"&&s.id==="sw2"&&api.sw2Builds&&!(nextBuild&&nextBuild.id===g.id)&&!(cfgInfo&&((cfgInfo.config.systems||{}).sw2||{}).askBuild===false));
async function pickBuild(g){
  let r=null;try{r=await api.sw2Builds(g.file)}catch{}
  if(!r||!r.builds||!r.builds.length)return "";
  if(bp||(mode!=="browse"&&mode!=="details"))return null;
  // (1.82) a .nup game and no build that opens .nup: say so, instead of a picker where every row is greyed out
  if(r.nup&&!r.builds.some(b=>b.nup)){play_("error");toast("None of your Switch 2 builds can open .nup games. Add a newer Eglantine build.");return null}
  return new Promise(res=>{
    const el=document.createElement("div");el.className="buildpick";
    const card=document.createElement("div");card.className="bp-card";card.setAttribute("role","dialog");card.setAttribute("aria-label","Choose a build");
    const t=document.createElement("div");t.className="bp-title";t.textContent="Start in which build?";
    const sub=document.createElement("div");sub.className="bp-game";sub.textContent=g.title;card.append(t,sub);
    const rows=r.builds.map((b,j)=>{const row=document.createElement("button");row.type="button";row.className="bp-row";const off=r.nup&&!b.nup;row.disabled=off;
      const n=document.createElement("b");n.textContent=b.label;const d=document.createElement("span");d.textContent=off?"can't open .nup games":new Date(b.date).toLocaleDateString(undefined,{month:"short",day:"numeric"});row.append(n,d);
      row.addEventListener("click",()=>{if(!off&&bp){bp.i=j;bpChoose()}});row.addEventListener("pointerenter",()=>{if(!off&&bp&&inputMode==="pointer"){bp.i=j;bpRender()}});card.append(row);return row});
    const f=document.createElement("div");f.className="bp-foot";f.textContent=`${btnWord("a")} start · ${btnWord("back")} back`;card.append(f);
    el.append(card);el.addEventListener("click",e=>{if(e.target===el)bpDone(null)});document.body.append(el);
    // (1.82) start on the suggested build, or else the first that isn't greyed out (row 0 could be)
    let i0=r.builds.findIndex(b=>b.exe===r.first);if(i0<0||rows[i0].disabled)i0=Math.max(0,rows.findIndex(x=>!x.disabled));
    bp={el,rows,builds:r.builds,i:i0,res,prev:mode};mode="build";bpRender();
    requestAnimationFrame(()=>el.classList.add("on"));sfx.flip();
  });
}
function bpRender(){if(bp)bp.rows.forEach((r,j)=>r.classList.toggle("on",j===bp.i))}
function bpMove(d){if(!bp)return;const n=bp.rows.length;let j=bp.i;for(let k=0;k<n;k++){j=(j+d+n)%n;if(!bp.rows[j].disabled)break}if(j!==bp.i){bp.i=j;play_("tick",{dir:d});bpRender()}}
function bpChoose(){if(bp&&!bp.rows[bp.i].disabled)bpDone(bp.builds[bp.i].exe)}
function bpDone(exe){if(!bp)return;const b=bp;bp=null;mode=b.prev;b.el.classList.remove("on");setTimeout(()=>b.el.remove(),260);if(exe==null||exe==="")sfx.back();b.res(exe||null)}
/* (beta.5) a PC game's cover (Steam, Epic, Quiver, added by hand; console games have the Art Studio): SteamGridDB's
   posters (with the player's key), Steam's own cover, a picture of your own, or back to the automatic one. The arrows /
   D-pad move through the grid, A uses, B (Esc) closes; a click or tap uses a tile. The main side keeps the addresses:
   the page sends a game id and an option's key only */
let cp=null,cpReturn=null;
async function pcCoverOpen(g,term,batch){
  if(!api.pcCoverOptions||cp||bp||(mode!=="browse"&&mode!=="details"))return;
  const el=document.createElement("div");el.className="buildpick coverpick";
  const card=document.createElement("div");card.className="bp-card";card.setAttribute("role","dialog");card.setAttribute("aria-label","Choose a cover");
  const t=document.createElement("div");t.className="bp-title";t.textContent=batch?`Choose a cover · ${batch.i+1} of ${batch.list.length}`:"Choose a cover";
  const sub=document.createElement("div");sub.className="bp-game";sub.textContent=g.title;
  const note=document.createElement("div");note.className="cp-note";note.textContent="Looking for covers…";
  const grid=document.createElement("div");grid.className="cp-grid";
  const f=document.createElement("div");f.className="bp-foot";f.textContent=`${btnWord("a")} use · ${btnWord("back")} back`;
  card.append(t,sub,note,grid,f);el.append(card);el.addEventListener("click",e=>{if(e.target===el){cpDone();sfx.back()}});document.body.append(el);
  if(batch)f.textContent=`${btnWord("a")} use · ${btnWord("back")} stop`; // (beta.9: going through every custom game's cover)
  cp={el,grid,note,g,term:term||"",items:[],tiles:[],i:0,prev:mode==="details"?"browse":mode,busy:false,batch:batch||null,advance:false};if(mode==="details")closeDetails(true);mode="cover";
  requestAnimationFrame(()=>el.classList.add("on"));sfx.flip();
  let r=null;try{r=await api.pcCoverOptions(g.id,term||"")}catch(e){if(cp&&cp.g===g)cp.note.textContent=String(e&&e.message||e);r={list:[],sgdb:true,current:false}}
  if(!cp||cp.g!==g)return;
  // (beta.9) Search by name works without a key too (Steam's store answers it); with no SteamGridDB key, a highlighted slot
  // after the covers says what one adds and opens its page (the picker comes back when Settings closes)
  const items=[...(batch?[{act:"skip",icon:"→",label:"Skip this game",sub:batch.i+1<batch.list.length?"On to the next":"The last one"}]:[]),...(!batch?[{act:"search",icon:"⌕",label:"Search by name…",sub:term?`Now: ${term}`:"Another name to look up"}]:[]),{act:"own",icon:"+",label:"A picture of yours",sub:"JPG, PNG or WebP"},...(r.current?[{act:"reset",icon:"↺",label:"Automatic cover",sub:"Go back to it"}]:[]),
    ...(r.list||[]).map(x=>({key:x.key,thumb:x.thumb,label:x.label,sub:x.sub||""})),
    ...(!r.sgdb&&!batch&&window.Setup&&Setup.open?[{act:"sgdb",icon:"",acct:true,label:"Add SteamGridDB",sub:"Dozens more covers · free key"}]:[])];
  const L=r.list||[],n=L.filter(x=>x.source==="steamgriddb").length,ns=L.filter(x=>x.source==="steamstore"||x.source==="steam").length;
  cp.note.textContent=n?`${n} cover${n===1?"":"s"} from SteamGridDB${term?` for "${term}"`:""}${ns?", and Steam's own":""}.`
    :term?(ns?`Steam's cover${ns===1?"":"s"} for "${term}".${r.sgdb?" SteamGridDB has none.":""}`:`Nothing found for "${term}". Try another name.`)
    :r.sgdb?(ns?"Steam's own cover. SteamGridDB has none for this game.":"SteamGridDB has no covers for this game. Search by name, or use a picture of yours.")
    :ns?"Steam's own cover. For dozens more, add a free SteamGridDB key.":"No cover under this name on Steam. Search by name, use a picture of yours, or add a free SteamGridDB key for dozens more.";
  cp.items=items;cp.tiles=items.map((it,j)=>{const b=document.createElement("button");b.type="button";b.className="cp-tile";
    if(it.acct)b.classList.add("acct");
    const im=document.createElement("div");im.className="im";if(it.thumb)im.style.backgroundImage=`url("${String(it.thumb).replace(/"/g,"%22")}")`;else if(it.acct)im.innerHTML='<svg viewBox="0 0 24 24" aria-hidden="true"><circle cx="8" cy="15" r="4"/><path d="M11 12l9-9M17 6l3 3M15 8l2 2"/></svg>';else im.textContent=it.icon||"";
    const l=document.createElement("b");l.textContent=it.label;const s=document.createElement("span");s.textContent=it.sub;b.append(im,l,s);
    b.addEventListener("click",()=>{if(cp){cp.i=j;cpRender();cpChoose()}});b.addEventListener("pointerenter",()=>{if(cp&&inputMode==="pointer"){cp.i=j;cpRender()}});grid.append(b);return b});
  const first=items.findIndex(x=>x.key&&x.key!=="current");cp.i=first>=0?first:0;cpRender();
}
function cpRender(){if(!cp)return;cp.tiles.forEach((b,j)=>b.classList.toggle("on",j===cp.i));const b=cp.tiles[cp.i];if(b)b.scrollIntoView({block:"nearest"})}
function cpCols(){const T=cp.tiles;if(T.length<2)return 1;const y=T[0].offsetTop;let n=1;while(n<T.length&&T[n].offsetTop===y)n++;return n}
function cpMove(dx,dy){if(!cp||!cp.tiles.length)return;const n=cp.tiles.length,j=Math.max(0,Math.min(n-1,cp.i+dx+dy*cpCols()));if(j!==cp.i){cp.i=j;play_("tick",{dir:dx||dy});cpRender()}}
async function cpChoose(){if(!cp||cp.busy)return;const it=cp.items[cp.i],g=cp.g,term=cp.term,B=cp.batch;if(!it)return;cp.busy=true;
  const next=()=>{if(cp)cp.advance=true}; // (in a batch: this game is settled, on to the next one)
  try{
    if(it.act==="skip"){next();cpDone();play_("tick",{dir:1});return}
    if(it.act==="own"){const r=await api.pcCoverOwn(g.id);if(r&&r.ok){if(B)B.done++;next();cpDone();play_("toast");if(!B)toast(`New cover for ${g.title}`)}else if(cp)cp.busy=false;return}
    if(it.act==="search"){cpDone();play_("panel");Setup.open({page:"coversearch",id:g.id,title:g.title,term:term||""});return}
    if(it.act==="sgdb"){cpDone();play_("panel");cpReturn={id:g.id,term:term||""};Setup.open({page:"steamgriddb"});return} // (beta.9) the key's page; the picker again when Settings closes
    if(it.act==="reset"){await api.pcCoverReset(g.id);next();cpDone();play_("toast");if(!B)toast(`${g.title} is back on its automatic cover`);return}
    if(it.key==="current"){next();cpDone();return}
    cp.note.textContent="Downloading the cover…";await api.pcCoverUse(g.id,it.key);if(B)B.done++;next();cpDone();play_("toast");if(!B)toast(`New cover for ${g.title}`);
  }catch(e){if(cp){cp.busy=false;cp.note.textContent=String(e&&e.message||e)}play_("error")}
}
/* (beta.9) covers for every custom game at once (Recomps, ports, Quiver's games, programs added by hand or from Playnite:
   they never get an automatic cover, since their names often match an unrelated game). One by one in the picker (A uses,
   Skip moves on, B stops), or the best SteamGridDB match for each in one go, with Undo for that run */
let coverUndo=[];
async function pcCoverMissing(){let L=[];try{L=await api.pcCoverMissing()}catch{}return Array.isArray(L)?L:[]}
async function pcCoverBatch(){if(!api.pcCoverMissing||cp||bp)return;const L=await pcCoverMissing();
  if(!L.length){toast("Every custom game has a cover already");return}
  const b={list:L,i:0,done:0};cpBatchOpen(b)}
function cpBatchOpen(b){const x=b.list[b.i];if(!x){toast(b.done?`New covers for ${b.done} game${b.done===1?"":"s"}`:"No covers changed");return}
  const g=allShelves.flatMap(s=>s.items||[]).find(y=>y&&y.id===x.id)||{id:x.id,title:x.title,kind:"steam"};pcCoverOpen(g,"",b)}
async function pcCoverAuto(){if(!api.pcCoverMissing||cp||bp)return {n:0};const L=await pcCoverMissing();
  if(!L.length){toast("Every custom game has a cover already");return {n:0}}
  toast(`Finding covers for ${L.length} game${L.length===1?"":"s"}…`);const done=[];
  for(const x of L){let r=null;try{r=await api.pcCoverOptions(x.id,"")}catch{continue}
    if(r&&r.sgdb===false){toast("Add a free SteamGridDB key first: Settings → Accounts & services");return {n:0,nokey:true}}
    const best=((r&&r.list)||[]).find(o=>o.source==="steamgriddb");if(!best)continue;
    try{await api.pcCoverUse(x.id,best.key);done.push(x.id)}catch{}}
  coverUndo=done;play_("toast");toast(done.length?`New covers for ${done.length} of ${L.length} game${L.length===1?"":"s"}. Don't like them? Settings → Custom games → Undo`:"SteamGridDB had no covers for these games");return {n:done.length,of:L.length}}
async function pcCoverUndo(){const L=coverUndo;coverUndo=[];for(const id of L){try{await api.pcCoverReset(id)}catch{}}if(L.length){play_("toast");toast(`${L.length} cover${L.length===1?"":"s"} put back`)}return L.length}
function cpDone(){if(!cp)return;const c=cp;cp=null;mode=c.prev;c.el.classList.remove("on");setTimeout(()=>c.el.remove(),260)
  if(c.batch){const b=c.batch;if(c.advance){b.i++;setTimeout(()=>cpBatchOpen(b),320)}else if(b.done)toast(`New covers for ${b.done} game${b.done===1?"":"s"}`)}} // (beta.9: B stops a batch)
function cpKey(k){if(k==="ArrowLeft")cpMove(-1,0);else if(k==="ArrowRight")cpMove(1,0);else if(k==="ArrowUp")cpMove(0,-1);else if(k==="ArrowDown")cpMove(0,1);
  else if(k==="Enter"||k===" ")cpChoose();else if(k==="Escape"||k==="Backspace"){cpDone();sfx.back()}else return false;return true}
function bpKey(k){if(k==="ArrowUp")bpMove(-1);else if(k==="ArrowDown")bpMove(1);else if(k==="Enter"||k===" ")bpChoose();else if(k==="Escape"||k==="Backspace")bpDone(null);else return false;return true}
/* ---------- launching ---------- */
let launchTimer,runningKind=null;
const wait=ms=>new Promise(r=>setTimeout(r,ms)); // let the screen fade to black before the emulator window appears
// (beta.9) a short note of what the screen is doing, with its memory use: main keeps the last ten and writes them to the
// log only if the screen crashes (no log noise otherwise). The Thor / the demo page have no api.crumb: nothing is sent
function uiCrumb(t){try{if(!api.crumb)return;const m=performance.memory;api.crumb(String(t).slice(0,120)+(m?` · js ${Math.round(m.usedJSHeapSize/1048576)}/${Math.round(m.jsHeapSizeLimit/1048576)} MB`:"")+` · ${document.querySelectorAll("img").length} img`)}catch{}}
function play(){
  {const g=curGame();uiCrumb("Play: "+(g?g.title:"?"))}
  const es=!rawItem()&&(mode==="browse"||mode==="details")&&!sysBusy()?emptyShelf():null;if(es){play_("panel");Setup.open({page:es.kind==="steam"?"steam":es.id});return}
  const raw=rawItem();if(!raw||sysBusy()||(mode!=="browse"&&mode!=="details"))return;closeDetails(true);
  if(raw.kind==="setup"){const s=shelfFor(raw)||cur();play_("panel");Setup.open({page:raw.addGames?"addgames":s?s.id:"consoles"});return}
  if(raw.kind==="random"){if(raw.pick)playPick(raw);else shuffle();return}
  if(cur()===HOME){playPick({pick:{shelf:shelves.indexOf(shelfFor(raw)),game:raw}});return}
  const g=raw;
  const s=cur().col?(g._sh||shelfFor(g)):cur(); // (beta.10) on a collection shelf: the game's own console (build picker, boot video and slide, its name)
  // Switch 2: which build first (before the boot slide starts); Back on the list leaves you on the shelf
  if(needsBuild(g,s)){pickBuild(g).then(exe=>{if(exe==null)return;nextBuild={id:g.id,exe};play()});return}
  const boot=g.kind==="rom"&&s.bootOnLaunch&&!s.useIpl,vid=boot&&!!(themes[s.id]||{}).boot;
  // your boot video (Customize) plays first when this console has one and its switch is on; otherwise the boot slide
  // covers the emulator starting (it stays until the game's window is up, then fades to black), and with the slide off
  // the built-in intro plays (runBoot without a video)
  if(vid){runBoot(s,()=>launchNow(g));return}
  // Pulse: the held disc goes into a console at the bottom (spins, its sound), the screen goes dark and the game starts, as a game's disc does
  if(g.pulse){runInsert(Object.assign({},s,{id:"ps5",emulatorName:"Pulse"}),g,()=>launchNow(g),false);return}
  if(g.kind==="rom"&&s.insertAnim!==false){runInsert(s,g,null,false);launchNow(g,true);return}
  if(boot){runBoot(s,()=>launchNow(g));return}
  launchNow(g);
}

/* ---------- inspect: turn the case, open it, take the disc out ---------- */
const isp={state:0,rx:-8,ry:24,tx:-8,ty:24,fl:0,tf:0,sy0:0,last:0,raf:0,drag:null,zoom:1,tz:1,px:0,py:0,tpx:0,tpy:0,tok:0,held:null,wd:0};
// Where a shelf box is on screen right now: the middle and height of its front face (client px) and how far the shelf
// turns it (the slot's angle plus any spin of the box itself, read from the live, mid-transition transforms), so the 3D
// case can leave from / land in exactly that spot. null when it isn't on screen.
function slotPose(s){if(!s||!s.el||!s.el.isConnected||!s.img)return null;const f=s.img.parentElement,r=f&&f.getBoundingClientRect();if(!r||r.width<10||r.height<10)return null;
  const ang=e=>{try{const t=getComputedStyle(e).transform;if(!t||t==="none")return 0;const m=new DOMMatrix(t);return Math.atan2(m.m31,m.m11)*180/Math.PI}catch{return 0}};
  return {x:r.left+r.width/2,y:r.top+r.height/2,h:r.height,ry:ang(s.el)+ang(s.box)}}
// show a box the 3D case had stood in for: at once when the case has landed exactly on it, or fading in (cross-fade)
function relight(e){if(!e||!e.classList.contains("lift"))return;e.classList.add("glowfade");e.classList.remove("lift");clearTimeout(e._gl);e._gl=setTimeout(()=>e.classList.remove("glowfade"),450)}
function unhold(e,fade){if(!e)return;if(!e.classList.contains("held")){relight(e);return}if(fade){e.classList.add("unhold");clearTimeout(e._uh);e._uh=setTimeout(()=>e.classList.remove("unhold"),260)}e.classList.remove("held");relight(e)}
function openInspect(){uiCrumb("Inspect");
  const g=curGame(),s=pickShelf();if(!g||(g.kind!=="rom"&&!g.pulse)||sysBusy()||(mode!=="browse"&&mode!=="details"))return;
  detailsBack=mode==="details"?g:null;closeDetails(true);mode="inspect";clearTimeout(isp.wd);const tok=++isp.tok;
  const el=$("inspect"),style=STYLE[s.id]||STYLE.media,m=MEDIA[s.id]||MEDIA.wii;
  const PT=!!window.PulseHost&&innerWidth<innerHeight,ch=PT?Math.min(innerHeight*.5,innerWidth*.62/style.ratio):Math.min(innerHeight*.58,innerWidth*.34/style.ratio),cw=ch*style.ratio;
  el.style.setProperty("--ch",ch+"px");el.style.setProperty("--cw",cw+"px");el.style.setProperty("--t",(cw*Math.max(.06,style.depth*.7))+"px");el.style.setProperty("--disc",(cw*m.size)+"px");
  el.style.setProperty("--case",style.caseColor);el.style.setProperty("--case-in",style.caseSide);el.style.setProperty("--case-side",style.caseSide);el.style.setProperty("--accent",style.glow);
  el.classList.remove("open","out","cart","umd");if(m.kind!=="disc")el.classList.add(m.kind);
  const front=frontUrl(g);
  const sp=$("ispSpine");if(g.art&&g.art.spine){sp.hidden=false;sp.src=g.art.spine}else{sp.hidden=true;sp.removeAttribute("src")}
  $("ispSpineText").textContent=g.title;
  $("ispDiscTop").style.backgroundImage=`url("${(g.art&&g.art.disc)||front}")`;
  tinyBlur((g.art&&(g.art.hero||g.art.front))||front,d=>{el.querySelector(".ins-bg img").src=d||""});
  $("ispTitle").textContent=g.title;
  isp.state=0;isp.fl=isp.tf=0;isp.rx=-8;isp.ry=-14;isp.tx=-6;isp.ty=16;isp.zoom=.88;isp.tz=.88;isp.px=isp.py=isp.tpx=isp.tpy=0;isp.last=performance.now();
  // real 3D when WebGL is available; the CSS case stays as the fallback
  let gl=false;
  try{if(window.Inspect3D&&Inspect3D.available()){
    const th=themes[s.id]||{};
    const from=slotPose(slots[sel]),hs=slots[sel];if(from&&hs&&hs.el){clearTimeout(hs.el._gl);hs.el.classList.add("glowfade","lift")}
    // the shelf box stays until the 3D case is drawn and ready to take its place; then both swap in the same frame
    const onStart=()=>{if(isp.tok!==tok||mode!=="inspect")return;if(hs&&hs.el){hs.el.classList.remove("unhold");hs.el.classList.add("held");isp.held=hs}app.classList.add("inspecting");el.classList.add("on")};
    el.classList.add("gl");gl=Inspect3D.open({from,where:from?()=>slotPose(hs):null,standIn:from&&hs.img,onStart,sys:s.id,rc:style.rc||.04,ratio:style.ratio,depth:style.depth,caseColor:style.caseColor,caseSide:style.caseSide,spineInk:style.spineInk,edge:style.edge||0,title:g.title,kind:m.kind,size:m.size,
      // sized copies (still sharp up close): full-size scans cost ~70 MB of video memory per game and half a second to upload
      front:thumb(front,1536),back:g.art&&g.art.back?thumb(g.art.back,1536):null,spine:g.art&&g.art.spine?thumb(g.art.spine,1536):null,disc:g.art&&g.art.disc?(/^data:/.test(g.art.disc)?g.art.disc:thumb(g.art.disc,1024)):null,manual:(g.art&&g.art.manual&&g.art.manual[0])?thumb(g.art.manual[0],2048):null,modelUrl:th.media||null,modelOpts:th.mediaOpts||null});
  }}catch(err){gl=false;console.warn("3D inspect failed",err);api.log&&api.log("3D inspect failed: "+(err&&err.stack||err))}
  // the CSS case is only drawn without WebGL; don't decode its scans (or generate a back) when it's hidden
  if(!gl){$("ispFront").src=front;$("ispManual").src=rdSrc((g.art&&g.art.manual&&g.art.manual[0])||front);$("ispBack").src=backUrl(g)}else["ispFront","ispManual","ispBack"].forEach(id=>$(id).removeAttribute("src"));
  if(gl&&!(g.art&&g.art.manual&&g.art.manual.length)&&(g.kind==="rom"||g.pulse)&&Inspect3D.setManual)makeBooklet(g).then(u=>{if(u&&u[0]&&curGame()===g&&isp.tok===tok&&mode==="inspect")Inspect3D.setManual(u[0])},()=>{}); // only into this same open: a late booklet from a closed Inspect stays off another game's case
  el.classList.toggle("gl",gl);
  // anything still hidden from an earlier inspect that isn't this game's box comes back (fading)
  mounted().forEach(o=>{if(o!==slots[sel]||!gl)unhold(o.el,true)});
  if(!gl)el.classList.add("on");el.setAttribute("aria-hidden","false");renderIspHints();sfx.flip();
  cancelAnimationFrame(isp.raf);isp.lt=0;isp.raf=requestAnimationFrame(ispLoop);
}
// (beta.10) the game whose Details were open when Inspect or the quick menu came over them: backing out of those (Back, Esc,
// a touch back, the menu button) opens its Details again; a launch, or anything else taking over, doesn't
let detailsBack=null;
function detailsReturn(){const g=detailsBack;detailsBack=null;if(g&&mode==="browse"&&!qm.open&&!sysBusy()&&curGame()===g)openDetails()}
function closeInspect(){
  resetEject(); // a disc out of the box goes back in when Inspect closes
  // the manual reader open on top (a phone companion's Play can close Inspect from under it): shut it too, or the paused 3D view
  // and rd.open stayed behind and every later Inspect stuck with the box lifted
  if(rd.open){rd.open=false;if(window.Inspect3D)Inspect3D.setPaused(false);$("inspect").classList.remove("reading");$("reader").classList.remove("on");$("reader").setAttribute("aria-hidden","true")}
  if(holdT){cancelAnimationFrame(holdT);holdT=null;$("ispHold").classList.remove("on");$("ispHoldBar").style.width="0%"}
  const el=$("inspect");el.classList.remove("on");el.setAttribute("aria-hidden","true");cancelAnimationFrame(isp.raf);app.classList.remove("inspecting");
  // The 3D case flies back to where its box is now (re-read every frame: the shelf may still be moving) and the box
  // shows again in the frame it lands. Move to another game / shelf, launch… mid-flight and the two cross-fade instead.
  const hs=isp.held,sl=slots,at=sel;isp.held=null;
  const tgt=()=>slots===sl&&sel===at&&(mode==="browse"||mode==="details")&&!document.hidden?slotPose(hs):null;
  const done=fade=>{if(hs&&hs.el&&!(mode==="inspect"&&slots[sel]===hs))unhold(hs.el,fade)};
  if(mode==="inspect")mode="browse"; // before leave(): it can hand the box back straight away (reduce motion)
  if(window.Inspect3D&&Inspect3D.isActive())Inspect3D.leave(tgt,done);else done(true);
  // safety net: nothing stays hidden if a flight can't finish (lost GPU, paused…); never while one is still running
  clearTimeout(isp.wd);const wd=()=>{if(mode==="inspect")return;if(window.Inspect3D&&Inspect3D.busy&&Inspect3D.busy()){isp.wd=setTimeout(wd,400);return}mounted().forEach(o=>unhold(o.el,true))};isp.wd=setTimeout(wd,1200);
  setTimeout(()=>{if(mode==="inspect")return;el.classList.remove("open","out")},320);
}
function ispLoop(t){
  const dt=t-(isp.lt||t-16.667);isp.lt=t; // the easing below is per 60 Hz frame, scaled to the real frame time (240 Hz screens)
  // Drift gently when you're not touching it.
  const idle=t-isp.last>2600,spin=ispSpins();
  // (a disc out of its case spins slowly on its own when left alone, so the side-to-side sway is only for the case)
  const ty=idle&&!spin?isp.ty+Math.sin(t/1700)*9:isp.ty, tx=idle?isp.tx+Math.sin(t/2300)*3:isp.tx;
  // Ease toward the target: slow and smooth for big turns (turning the case over), quicker for nudges.
  const big=Math.abs(ty-isp.ry)>60;const k=easeK(isp.stick&&ISP_STICK_EASE?ISP_STICK_EASE:big?ISP_EASE[0]:ISP_EASE[1],dt),k2=easeK(.1,dt); // (a held stick: follow it closely; buttons and flips keep their slow glide)
  isp.rx+=(tx-isp.rx)*k;isp.ry+=(ty-isp.ry)*k;
  isp.zoom+=(isp.tz-isp.zoom)*k2;isp.px+=(isp.tpx-isp.px)*k2;isp.py+=(isp.tpy-isp.py)*k2;isp.fl+=(isp.tf-isp.fl)*k;
  if(window.Inspect3D&&Inspect3D.isActive())Inspect3D.setPose(isp.rx,isp.ry,isp.zoom,isp.px,isp.py,t-isp.last<1200||!!isp.drag,isp.fl,ANDROID?isp.sy0:null);
  else if(spin){
    // CSS case, disc out: turn the whole stage about the disc's own centre (it sits var(--cw)*.3 in front of the case's middle),
    // so it moves in 3D about its own centre: side to side turns it over, up / down tilts it right round; the case is off-screen by then
    const z="calc(var(--cw)*.3)";
    $("ispRot").style.transform=`translate3d(${isp.px.toFixed(1)}px,${isp.py.toFixed(1)}px,0) scale(${isp.zoom.toFixed(3)}) translateZ(${z}) rotateX(${isp.rx.toFixed(2)}deg) rotateY(${(isp.fl+isp.ry-isp.sy0).toFixed(2)}deg) translateZ(calc(var(--cw)*-.3))`;
  }else $("ispRot").style.transform=`translate3d(${isp.px.toFixed(1)}px,${isp.py.toFixed(1)}px,0) scale(${isp.zoom.toFixed(3)}) rotateX(${isp.rx.toFixed(2)}deg) rotateY(${isp.ry.toFixed(2)}deg)`;
  isp.raf=requestAnimationFrame(ispLoop);
}
// Android handheld: a small thumbstick has a short throw and the screen is in your hands, so Inspect turns ~2.3x faster under
// the sticks and D-pad tilt, and the case catches up with them (and lands a D-pad quarter turn) about twice as quickly:
// at the PC values a full turn under a pushed stick took over five seconds and a quarter turn ~1.6 s to settle. Windows keeps its values.
const ISP_PAD=ANDROID?2.3:4.8,ISP_EASE=[.09,.16],ISP_STICK_EASE=ANDROID?0:.4; // (PC stick: a full push turns ~330°/s; ~190°/s was still slow) STICK_EASE: the case keeps up with a held stick (0: the Thor keeps its own glide); EASE: PC D-pad flips glide as fast as on the Thor
// Settle on the nearest side (front / spine / back / edge) when you let go, so flipping lands cleanly and the spine is a stop of its own.
// (Not a disc out of its case: that spins freely, any angle is a fine place to stop.)
function ispSnap(){if(!ispSpins())isp.ty=Math.round(isp.ty/90)*90;if(Math.abs(isp.tx)<14)isp.tx=Math.sign(isp.tx)*6;isp.last=performance.now()}
// Zoomed in, a drag turns it less (so the finger / mouse stays with the part you're looking at). raw: the same turn at every zoom.
// Android passes raw for the sticks, D-pad and arrow keys: there a held stick is a turn *speed*, and dividing it by the zoom made
// the case crawl round when zoomed in (2.4x slower at full zoom).
function ispTurn(dx,dy,raw){const z=raw?1:Math.max(1,isp.tz),lim=ispSpins()?85:50;isp.ty+=dx/z;isp.tx=Math.max(-lim,Math.min(lim,isp.tx+dy/z));isp.last=performance.now()} // (a disc out of its case tilts nearly edge on)
// d: +/-1 per step (keys, wheel, click). Analog triggers pass a smaller per-frame amount so a full pull takes about a second to zoom in.
// Zooming out past the resting size snaps back to it and recentres. (Only when zooming out: the closed case rests at .88, and an analog
// step from there lands under .9, so checking on the way in kept pinning the zoom until the case was opened.)
function ispZoom(d,analog){const f=analog?Math.pow(1+.022*Math.abs(d),typeof analog==="number"?analog:1):1.12; /* analog: a trigger read every frame (a number: padF, so the zoom speed doesn't follow the refresh rate) */isp.tz=Math.max(.88,Math.min(2.6,d>0?isp.tz*f:isp.tz/f));if(d<0&&isp.tz<=.9){isp.tz=.88;isp.tpx=0;isp.tpy=0}isp.last=performance.now()}
// The disc (or UMD) is out of its case: it moves in 3D about its own centre (side to side turns it over, up / down tilts it); "turn over" flips it.
// (Android only: Windows keeps its old disc turn, per the brief. Drop ANDROID&& here and in setPose below to give Windows the same spin.)
function ispSpins(){return ANDROID&&isp.state===2&&!$("inspect").classList.contains("cart")}
function ispFlip(){if(ispSpins()){isp.tf=isp.tf?0:180;isp.last=performance.now()}else ispTurn(180,0,true)}
function ispPan(dx,dy){if(isp.tz<=1)return;const lim=innerWidth*.35*(isp.tz-1);isp.tpx=Math.max(-lim,Math.min(lim,isp.tpx+dx));isp.tpy=Math.max(-lim,Math.min(lim,isp.tpy+dy));isp.last=performance.now()}
/* manual reader (inside inspect) */
const rd={pages:[],i:0,open:false};
function hasManual(g){return !!(g&&g.art&&g.art.manual&&g.art.manual.length)}
// Games with no manual (most Switch and PS4 games never had a printed one) get a small booklet made from what Boxshelf
// knows: the cover, the description, the details, the back cover. Clearly marked as made by Boxshelf. Made on demand,
// kept for the session.
const NO_PRINTED=new Set(["sw","sw2","ps4","ps5"]);
const bookletCache=new Map();
// Booklets and movie inserts are a few JPEG pages each (blob URLs). Only the 24 most recently used of each are kept; the
// pages of older ones are freed once you're back on the shelf (the museum and Inspect may still be showing them), and
// one that's needed again is simply made again.
const PAGES_KEEP=24;let pagesDrop=[];
function pagesTouch(cache,id){const p=cache.get(id);if(p){cache.delete(id);cache.set(id,p)}return p}
function pagesTrim(cache){while(cache.size>PAGES_KEEP){const [id,p]=cache.entries().next().value;cache.delete(id);pagesDrop.push(p)}}
function pagesFree(){if(!pagesDrop.length||mode!=="browse")return;const L=pagesDrop;pagesDrop=[];L.forEach(p=>Promise.resolve(p).then(u=>(Array.isArray(u)?u:[u]).forEach(x=>{try{if(typeof x==="string"&&x.startsWith("blob:"))URL.revokeObjectURL(x)}catch{}}),()=>{}))}
setInterval(pagesFree,15000);
// the JPEG encode of a booklet page ran on the main thread (canvas.toBlob): a stutter in the museum when you picked a case
// up. The page is still drawn here; it is copied to an ImageBitmap and encoded in a small worker (as covers.js does).
let bookW=null,bookId=0;const bookCb=new Map();
function bookletJpeg(c){
  const viaBlob=()=>new Promise(r=>c.toBlob(r,"image/jpeg",.88));
  if(bookW===null){try{if(typeof OffscreenCanvas==="undefined")throw 0;bookW=new Worker(URL.createObjectURL(new Blob(['onmessage=async e=>{let blob=null;try{const b=e.data.bm,o=new OffscreenCanvas(b.width,b.height);o.getContext("2d").drawImage(b,0,0);b.close();blob=await o.convertToBlob({type:"image/jpeg",quality:.88})}catch(x){}postMessage({id:e.data.id,blob})}'],{type:"text/javascript"})));bookW.onmessage=e=>{const f=bookCb.get(e.data.id);bookCb.delete(e.data.id);if(f)f(e.data.blob)};bookW.onerror=()=>{bookW=false;bookCb.forEach(f=>f(null));bookCb.clear()}}catch{bookW=false}}
  if(!bookW)return viaBlob();
  return createImageBitmap(c).then(bm=>new Promise(res=>{const id=++bookId;bookCb.set(id,res);bookW.postMessage({id,bm},[bm])})).catch(()=>null).then(b=>b||viaBlob());
}
function makeBooklet(g){
  if(bookletCache.has(g.id))return pagesTouch(bookletCache,g.id);
  const p=(async()=>{
    const sh=shelfFor(g)||cur(),st=STYLE[sh&&sh.id]||STYLE.media,i=g.info||{},W=900,H=1200,urls=[];
    const load=u=>new Promise(r=>{if(!u||typeof u!=="string")return r(null);const im=new Image();im.crossOrigin="anonymous";const t=setTimeout(()=>r(null),4000);im.onload=()=>{clearTimeout(t);r(im)};im.onerror=()=>{clearTimeout(t);r(null)};im.src=u});
    const page=async(draw,cover)=>{const c=document.createElement("canvas");c.width=W;c.height=H;const x=c.getContext("2d");x.fillStyle="#f4efe4";x.fillRect(0,0,W,H);await draw(x);
      if(!cover){x.fillStyle="rgba(60,50,30,.55)";x.font="500 20px Lexend, sans-serif";x.textAlign="center";x.fillText("Booklet made by Boxshelf · not an original manual",W/2,H-34);x.textAlign="left"}
      const b=await bookletJpeg(c);c.width=c.height=0;urls.push(URL.createObjectURL(b))};
    const wrap=(x,text,X,Y,maxW,lh,maxLines)=>{const words=String(text||"").split(/\s+/);let line="",y=Y,n=0;for(const w of words){const t=line?line+" "+w:w;if(x.measureText(t).width>maxW&&line){x.fillText(line,X,y);y+=lh;line=w;if(++n>=maxLines-1)break}else line=t}if(line&&n<maxLines){x.fillText(n>=maxLines-1&&words.length>0?line+"…":line,X,y);y+=lh}return y};
    const band=x=>{x.fillStyle=st.caseColor||"#222";x.fillRect(0,0,W,90);x.fillStyle=(st.band&&st.band.fg)||"#fff";x.font="700 34px Lexend, sans-serif";x.fillText((sh&&sh.name||"").toUpperCase(),48,58)};
    const front=await load((g.art&&g.art.front)||await appFrontAsync(stOf(g),g)),back=await load(g.art&&g.art.back); /* a generated cover: the worker-encoded one (the synchronous one finished its encode here, on the main thread) */
    // the front, laid out like an original instruction booklet: the box art full-bleed under a thin console band,
    // and a footer strip with "INSTRUCTION BOOKLET" and the console's name, the way the real ones are printed. A small
    // Boxshelf mark in the strip says it's ours, not a scan.
    await page(async x=>{const bg=st.caseColor||"#222",fg=(st.band&&st.band.fg)||"#fff",bb=(st.band&&st.band.bg)||bg,lab=(st.band&&st.band.label)||(sh&&sh.name||"").toUpperCase();
      x.fillStyle=bg;x.fillRect(0,0,W,H);
      const top=70,bot=H-120;
      if(front){const k=Math.max(W/front.width,(bot-top)/front.height),w=front.width*k,h=front.height*k;x.save();x.beginPath();x.rect(0,top,W,bot-top);x.clip();x.drawImage(front,(W-w)/2,top+(bot-top-h)/2,w,h);x.restore()}
      else{const g2=x.createLinearGradient(0,top,0,bot);g2.addColorStop(0,bb);g2.addColorStop(1,bg);x.fillStyle=g2;x.fillRect(0,top,W,bot-top);x.fillStyle=fg;x.font="800 72px Lexend, sans-serif";x.textAlign="center";wrap(x,g.title,W/2,top+(bot-top)/2-40,W-140,84,3);x.textAlign="left"}
      x.fillStyle=bb;x.fillRect(0,0,W,top);x.fillStyle=fg;x.font="700 30px Lexend, sans-serif";x.textAlign="left";x.fillText(lab,40,top/2+11);
      x.font="500 18px Lexend, sans-serif";x.textAlign="right";x.globalAlpha=.75;x.fillText((g.region||"").toUpperCase(),W-40,top/2+7);x.globalAlpha=1;
      x.fillStyle=bb;x.fillRect(0,bot,W,H-bot);x.fillStyle="rgba(255,255,255,.12)";x.fillRect(0,bot,W,2);
      x.fillStyle=fg;x.textAlign="left";x.font="700 26px Lexend, sans-serif";x.fillText("INSTRUCTION BOOKLET",40,bot+50);
      x.font="500 18px Lexend, sans-serif";x.globalAlpha=.7;x.fillText("Boxshelf edition · not an original manual",40,bot+82);x.globalAlpha=1;
      // the Boxshelf mark: a little stack of cases in a rounded badge, where the console's logo would sit
      {const bx=W-40-64,by=bot+22;x.strokeStyle=fg;x.lineWidth=3;x.beginPath();x.roundRect(bx,by,64,64,12);x.stroke();x.fillStyle=fg;[0,1,2].forEach(k=>{x.fillRect(bx+14+k*13,by+16,9,32)});x.fillRect(bx+12,by+50,40,3);
        x.font="600 14px Lexend, sans-serif";x.textAlign="right";x.fillText("BOXSHELF",bx-12,by+38)}
      x.textAlign="left"},true);
    await page(async x=>{band(x);x.fillStyle="#1d1810";x.font="700 40px Lexend, sans-serif";x.fillText("About this game",60,170);x.font="400 27px Lexend, sans-serif";x.fillStyle="#2b2418";
      wrap(x,i.overview||"No description yet. Download box art and info in Settings to fill this page.",60,230,W-120,40,22)});
    await page(async x=>{band(x);x.fillStyle="#1d1810";x.font="700 40px Lexend, sans-serif";x.fillText("Details",60,170);let y=240;
      const rows=[["Developer",i.dev],["Publisher",i.pub],["Released",i.release],["Genre",i.genres&&String(i.genres).replace(/;/g,", ")],["Players",i.players],["Rating",i.esrb],["Community score",i.rating?(+i.rating).toFixed(1)+" / 5":""],["Region",g.region],["System",sh&&sh.name]].filter(r=>r[1]);
      rows.forEach(([k,v])=>{x.font="600 22px Lexend, sans-serif";x.fillStyle="#8a7650";x.fillText(k.toUpperCase(),60,y);x.font="500 30px Lexend, sans-serif";x.fillStyle="#1d1810";y=wrap(x,v,60,y+40,W-120,38,2)+22});
      if(NO_PRINTED.has(sh&&sh.id)){x.font="italic 400 24px Lexend, sans-serif";x.fillStyle="#6a5a3a";wrap(x,"This game shipped without a printed manual; its controls and guide are inside the game.",60,Math.max(y+20,H-240),W-120,34,3)}});
    if(back)await page(async x=>{const k=Math.min((W-80)/back.width,(H-160)/back.height),w=back.width*k,h=back.height*k;x.drawImage(back,(W-w)/2,(H-60-h)/2,w,h)});
    return urls})();
  bookletCache.set(g.id,p);p.catch(()=>{if(bookletCache.get(g.id)===p)bookletCache.delete(g.id)});pagesTrim(bookletCache);return p}
// ---- the movie shelves: each Blu-ray's back and its insert, drawn from the title's details (Cinemeta, the catalogue
// Stremio itself uses: plot, cast, director, runtime, rating, seasons). Only real details are printed, nothing made up.
const movieDet=new Map();
function movieDetails(d){if(!movieDet.has(d.id))movieDet.set(d.id,(api.stremioDetails?api.stremioDetails(d.type,d.sid):Promise.resolve(null)).then(x=>x||null,()=>{movieDet.delete(d.id);return null}));return movieDet.get(d.id)}
const imgLoad=u=>new Promise(r=>{if(!u)return r(null);const im=new Image();im.crossOrigin="anonymous";const t=setTimeout(()=>r(null),6000);im.onload=()=>{clearTimeout(t);r(im)};im.onerror=()=>{clearTimeout(t);r(null)};im.src=u});
function wrapLines(x,text,X,Y,maxW,lh,maxLines){const words=String(text||"").split(/\s+/).filter(Boolean);let line="",y=Y,n=0;for(let i=0;i<words.length;i++){const t=line?line+" "+words[i]:words[i];if(x.measureText(t).width>maxW&&line){if(n>=maxLines-1){let l=line;while(x.measureText(l+"…").width>maxW&&l.length>1)l=l.slice(0,-1);x.fillText(l+"…",X,y);return y+lh}x.fillText(line,X,y);y+=lh;n++;line=words[i]}else line=t}if(line){x.fillText(line,X,y);y+=lh}return y}
const fitImg=(x,im,X,Y,W,H,cover)=>{const k=cover?Math.max(W/im.width,H/im.height):Math.min(W/im.width,H/im.height),w=im.width*k,h=im.height*k;x.save();x.beginPath();x.rect(X,Y,W,H);x.clip();x.drawImage(im,X+(W-w)/2,Y+(H-h)/2,w,h);x.restore();return [w,h]};
const movieLine=m=>[m.year,m.runtime,(m.genres||[]).slice(0,3).join(" · ")].filter(Boolean).join("   ·   ");
const seasonsOf=m=>Object.keys((m&&m.seasons)||{}).map(Number).filter(n=>n>0).sort((a,b)=>a-b);
const movieBackCache=new Map();
// (beta.9) the museum's made movie backs and discs: the 60 used last are kept (a big film collection used to keep every one)
const capMovie=m=>{while(m.size>60)m.delete(m.keys().next().value)};
function movieBack(d){if(movieBackCache.has(d.id))return movieBackCache.get(d.id);const p=(async()=>{
  const m=await movieDetails(d)||{},W=788,H=1000,c=document.createElement("canvas");c.width=W;c.height=H;const x=c.getContext("2d");
  const g0=x.createLinearGradient(0,0,0,H);g0.addColorStop(0,"#0d1b36");g0.addColorStop(1,"#050913");x.fillStyle=g0;x.fillRect(0,0,W,H);
  const bg=await imgLoad(m.files&&m.files.background),logo=await imgLoad(m.files&&m.files.logo);
  if(bg){fitImg(x,bg,0,0,W,430,true);const f=x.createLinearGradient(0,250,0,430);f.addColorStop(0,"rgba(13,27,54,0)");f.addColorStop(1,"rgba(13,27,54,1)");x.fillStyle=f;x.fillRect(0,250,W,180)}
  if(logo){x.save();x.shadowColor="rgba(0,0,0,.6)";x.shadowBlur=18;fitImg(x,logo,40,300,520,120,false);x.restore()}else{x.fillStyle="#fff";x.font="800 46px Lexend, sans-serif";wrapLines(x,m.name||d.title,40,380,W-80,52,2)}
  let y=470;x.fillStyle="rgba(255,255,255,.75)";x.font="600 21px Lexend, sans-serif";x.fillText(movieLine(m)||(d.type==="series"?"TV series":"Feature film"),40,y);
  if(m.rating){x.fillStyle="#f5c518";x.beginPath();x.roundRect(W-150,y-26,110,36,6);x.fill();x.fillStyle="#111";x.font="800 20px Lexend, sans-serif";x.textAlign="center";x.fillText("IMDb "+m.rating,W-95,y-2);x.textAlign="left"}
  y+=46;x.fillStyle="rgba(255,255,255,.92)";x.font="400 22px Lexend, sans-serif";y=wrapLines(x,m.description||"Details for this title aren't available yet.",40,y,W-80,31,9);
  y+=14;x.font="600 18px Lexend, sans-serif";x.fillStyle="#7fb2ff";
  if(m.cast&&m.cast.length){x.fillText("STARRING",40,y);x.fillStyle="#fff";x.font="500 20px Lexend, sans-serif";y=wrapLines(x,m.cast.slice(0,5).join(", "),40,y+28,W-80,28,2)+8}
  if(m.director&&m.director.length){x.font="600 18px Lexend, sans-serif";x.fillStyle="#7fb2ff";x.fillText("DIRECTED BY",40,y);x.fillStyle="#fff";x.font="500 20px Lexend, sans-serif";y=wrapLines(x,m.director.join(", "),40,y+28,W-80,28,1)+8}
  const ss=seasonsOf(m);if(ss.length){x.font="600 18px Lexend, sans-serif";x.fillStyle="#7fb2ff";x.fillText(`${ss.length} SEASON${ss.length===1?"":"S"} · ${ss.reduce((a,n)=>a+m.seasons[n].length,0)} EPISODES`,40,y)}
  const bandG=x.createLinearGradient(0,H-74,0,H);bandG.addColorStop(0,"#2f7fe0");bandG.addColorStop(1,"#1a4fa8");x.fillStyle=bandG;x.fillRect(0,H-74,W,74);
  x.fillStyle="#fff";x.font="800 34px Lexend, sans-serif";x.fillText("BLU-RAY",40,H-26);x.font="600 17px Lexend, sans-serif";x.textAlign="right";x.globalAlpha=.85;x.fillText("FROM YOUR STREMIO LIBRARY",W-40,H-30);x.globalAlpha=1;x.textAlign="left";
  const u=c.toDataURL("image/jpeg",.88);c.width=c.height=0;return u})();movieBackCache.set(d.id,p);capMovie(movieBackCache);p.catch(()=>movieBackCache.delete(d.id));return p}
// the disc: fanart.tv's official print when there's a key and a match (Settings → Connections), else one printed here from
// the title's own background and logo, the way real discs usually look
const movieDiscCache=new Map();
function movieDisc(d){if(movieDiscCache.has(d.id))return movieDiscCache.get(d.id);const p=(async()=>{
  const m=await movieDetails(d)||{};if(m.files&&m.files.disc)return m.files.disc;
  const S=1024,c=document.createElement("canvas");c.width=c.height=S;const x=c.getContext("2d"),r=S/2;
  x.save();x.beginPath();x.arc(r,r,r-2,0,Math.PI*2);x.clip();
  const bg=await imgLoad(m.files&&m.files.background)||await imgLoad(await libCover(d));
  x.fillStyle="#0d1b36";x.fillRect(0,0,S,S);if(bg)fitImg(x,bg,0,0,S,S,true);
  const v=x.createRadialGradient(r,r,r*.2,r,r,r);v.addColorStop(0,"rgba(5,10,20,.15)");v.addColorStop(1,"rgba(5,10,20,.6)");x.fillStyle=v;x.fillRect(0,0,S,S);
  const logo=await imgLoad(m.files&&m.files.logo);
  if(logo){x.save();x.shadowColor="rgba(0,0,0,.7)";x.shadowBlur=20;fitImg(x,logo,r-300,r-330,600,190,false);x.restore()}else{x.fillStyle="#fff";x.font="800 64px Lexend, sans-serif";x.textAlign="center";wrapLines(x,m.name||d.title,r,r-250,620,70,2);x.textAlign="left"}
  // the silver rim ring, the Blu-ray mark, the hub
  x.lineWidth=26;x.strokeStyle="rgba(210,220,235,.85)";x.beginPath();x.arc(r,r,r-14,0,Math.PI*2);x.stroke();
  x.fillStyle="rgba(255,255,255,.9)";x.font="800 40px Lexend, sans-serif";x.textAlign="center";x.fillText("BLU-RAY DISC",r,r+300);x.font="600 22px Lexend, sans-serif";x.globalAlpha=.8;x.fillText((m.year?m.year+" · ":"")+(d.type==="series"?"SERIES":"FEATURE"),r,r+340);x.globalAlpha=1;x.textAlign="left";
  x.restore();x.globalCompositeOperation="destination-out";x.beginPath();x.arc(r,r,r*.11,0,Math.PI*2);x.fill();x.globalCompositeOperation="source-over"; // the centre hole
  x.strokeStyle="rgba(200,210,225,.9)";x.lineWidth=18;x.beginPath();x.arc(r,r,r*.2,0,Math.PI*2);x.stroke();
  const u=c.toDataURL("image/png");c.width=c.height=0;return u})();movieDiscCache.set(d.id,p);capMovie(movieDiscCache);p.catch(()=>movieDiscCache.delete(d.id));return p}
// the insert: a small folded card inside the case, read like the games' manuals
const insertCache=new Map();
function movieInsert(d){if(insertCache.has(d.id))return pagesTouch(insertCache,d.id);const p=(async()=>{
  const m=await movieDetails(d)||{},W=900,H=1200,urls=[],bg=await imgLoad(m.files&&m.files.background),logo=await imgLoad(m.files&&m.files.logo),post=await libCover(d).then(imgLoad);
  const page=async draw=>{const c=document.createElement("canvas");c.width=W;c.height=H;const x=c.getContext("2d");x.fillStyle="#f4f1ea";x.fillRect(0,0,W,H);await draw(x);
    x.fillStyle="rgba(40,50,70,.5)";x.font="500 18px Lexend, sans-serif";x.textAlign="center";x.fillText("Insert made by Boxshelf from the title's catalogue details",W/2,H-30);x.textAlign="left";
    const b=await bookletJpeg(c);c.width=c.height=0;urls.push(URL.createObjectURL(b))};
  const head=(x,t)=>{const g=x.createLinearGradient(0,0,0,110);g.addColorStop(0,"#2f7fe0");g.addColorStop(1,"#1a4fa8");x.fillStyle=g;x.fillRect(0,0,W,110);x.fillStyle="#fff";x.font="800 40px Lexend, sans-serif";x.fillText(t,56,70);x.font="600 18px Lexend, sans-serif";x.textAlign="right";x.globalAlpha=.8;let n=(m.name||d.title).toUpperCase();while(x.measureText(n).width>W*.42&&n.length>3)n=n.slice(0,-2);x.fillText(n,W-56,68);x.globalAlpha=1;x.textAlign="left"};
  const label=(x,k,y)=>{x.font="700 20px Lexend, sans-serif";x.fillStyle="#2a62c0";x.fillText(k.toUpperCase(),56,y)};
  // cover: the art full-bleed, the title's logo over it
  await page(async x=>{x.fillStyle="#08101f";x.fillRect(0,0,W,H);const art=bg||post;if(art)fitImg(x,art,0,0,W,H,true);const f=x.createLinearGradient(0,H*.45,0,H);f.addColorStop(0,"rgba(8,16,31,0)");f.addColorStop(1,"rgba(8,16,31,.95)");x.fillStyle=f;x.fillRect(0,0,W,H);
    if(logo){x.save();x.shadowColor="rgba(0,0,0,.7)";x.shadowBlur=24;fitImg(x,logo,70,H-420,W-140,200,false);x.restore()}else{x.fillStyle="#fff";x.font="800 70px Lexend, sans-serif";wrapLines(x,m.name||d.title,70,H-300,W-140,78,3)}
    x.fillStyle="rgba(255,255,255,.85)";x.font="600 24px Lexend, sans-serif";x.fillText(movieLine(m),70,H-170);x.fillStyle="#7fb2ff";x.font="800 22px Lexend, sans-serif";x.fillText(d.type==="series"?"COLLECTOR'S INSERT · SERIES":"COLLECTOR'S INSERT",70,H-120)});
  // the story
  await page(async x=>{head(x,"The story");let y=190;x.fillStyle="#1b2233";x.font="400 30px Lexend, sans-serif";y=wrapLines(x,m.description||"No description is available for this title yet.",56,y,W-112,44,16)+20;
    const rows=[["Released",m.year],["Runtime",m.runtime],["Genre",(m.genres||[]).join(", ")],["IMDb rating",m.rating?m.rating+" / 10":""],["Country",m.country],["Awards",m.awards]].filter(r=>r[1]);
    rows.forEach(([k,v])=>{if(y>H-140)return;label(x,k,y);x.font="500 26px Lexend, sans-serif";x.fillStyle="#1b2233";y=wrapLines(x,v,56,y+36,W-112,34,2)+18})});
  // cast and crew
  if((m.cast&&m.cast.length)||(m.director&&m.director.length)||(m.writer&&m.writer.length))await page(async x=>{head(x,"Cast & crew");let y=190;
    if(m.director&&m.director.length){label(x,"Directed by",y);x.font="600 30px Lexend, sans-serif";x.fillStyle="#1b2233";y=wrapLines(x,m.director.join(", "),56,y+40,W-112,38,2)+26}
    if(m.writer&&m.writer.length){label(x,"Written by",y);x.font="600 30px Lexend, sans-serif";x.fillStyle="#1b2233";y=wrapLines(x,m.writer.join(", "),56,y+40,W-112,38,2)+26}
    if(m.cast&&m.cast.length){label(x,"Starring",y);y+=50;x.font="500 32px Lexend, sans-serif";x.fillStyle="#1b2233";m.cast.slice(0,8).forEach(n=>{if(y<H-120){x.fillText(n,56,y);y+=48}})}});
  // a show: its episode guide, a season or two per page
  const ss=seasonsOf(m);let pg=null,y=0;
  for(const sn of ss.slice(0,12)){const eps=m.seasons[sn];const need=60+Math.min(eps.length,24)*34;
    if(!pg||y+need>H-90){if(pg)await page(pg.draw);pg={list:[]};y=190}
    pg.list.push([sn,eps]);y+=need+20;
    const L=pg.list;pg.draw=async x=>{head(x,"Episode guide");let yy=190;L.forEach(([n,e])=>{label(x,"Season "+n+" · "+e.length+" episodes",yy);yy+=44;x.font="500 24px Lexend, sans-serif";x.fillStyle="#1b2233";e.slice(0,24).forEach(ep=>{let t=(ep.n?ep.n+". ":"")+(ep.title||"Episode "+ep.n);while(x.measureText(t).width>W-112&&t.length>4)t=t.slice(0,-2);x.fillText(t,56,yy);yy+=34});if(e.length>24){x.fillStyle="#6a7590";x.fillText("…and "+(e.length-24)+" more",56,yy);yy+=34}yy+=20})}}
  if(pg)await page(pg.draw);
  return urls})();insertCache.set(d.id,p);p.catch(()=>{if(insertCache.get(d.id)===p)insertCache.delete(d.id)});pagesTrim(insertCache);return p}
function openReader(){
  const g=curGame();if(mode!=="inspect"||!g||g.kind!=="rom"||rd.open)return;
  // the booklet kept from last time only while it's still the cached one: after 24 others its pages are freed (blob URLs
  // revoked) and the reader showed blank pages; then it's simply made again
  if(!hasManual(g)&&(rd.bookletFor!==g.id||rd.bookletP!==bookletCache.get(g.id))){if(rd.making)return;rd.making=true;toast("Making a booklet for this game…");const bp=makeBooklet(g);bp.then(u=>{rd.making=false;rd.booklet=u;rd.bookletFor=g.id;rd.bookletP=bp;if(mode==="inspect"&&curGame()===g&&!rd.open)openReader()},e=>{rd.making=false;console.warn("booklet",e);toast("Couldn't make a booklet for this game")});return}
  rd.pages=hasManual(g)?g.art.manual:rd.booklet;rd.i=0;rd.open=true;
  // a first page that's a whole spread or print sheet: just its front cover, once that's cut out
  if(hasManual(g)&&window.Inspect3D&&Inspect3D.kit&&Inspect3D.kit.coverCrop){const pg=rd.pages;Inspect3D.kit.coverCrop(pg[0]).then(u=>{if(u&&rd.open&&rd.pages===pg){rd.pages=[u].concat(pg.slice(1));showSpread(false)}},()=>{})}
  $("inspect").classList.add("reading");$("reader").classList.add("on");$("reader").setAttribute("aria-hidden","false");
  if(window.Inspect3D)Inspect3D.setPaused(true);
  showSpread(false);renderIspHints();play_("open");
}
// "this is the wrong game's manual": set it aside (kept in .manual-originals), never pick that copy again, fetch the next
// best match. Two presses, so a stray button doesn't throw a good manual away.
// (beta.9) The first press opens a small fix menu for 8 s: Wrong game (the same button again) or Repair (the right game's
// manual that looks wrong: no cover, thumbnails, two pages side by side), B / Esc to leave it
let wrongAsk=0,wrongAskId=null,wrongBusy=false;
const fixOn=()=>!!(wrongAsk&&Date.now()-wrongAsk<8000&&curGame()&&wrongAskId===curGame().id);
function fixCancel(){wrongAsk=0;wrongAskId=null;renderIspHints()}
// (beta.9) while a fix runs, the message stays up and says what it's doing (waiting for the background pass to stop, looking,
// the pages coming down); then a tick, and the reader opens again with the new pages if you're still at this game's case
let fixRun=null;
const fixSay=t=>{if(fixRun)toastWork(t)};
if(api.onManualTurn)api.onManualTurn(d=>{if(!fixRun||!d)return;if(d.wait)fixSay("Waiting for the background manual download to pause…");else if(d.go)fixSay(fixRun.kind==="repair"?"Repairing the manual…":`Looking for the right manual for ${fixRun.g.title}…`)});
if(api.onCoversProgress)api.onCoversProgress(i=>{if(!fixRun||fixRun.kind!=="wrong"||!i)return;const m=/page (\d+) of (\d+)/.exec(String(i.stage||""));if(m)fixSay(`Downloading the new manual · page ${m[1]} of ${m[2]}`)});
// this game's pages as they are now, with a version on each address (a page repaired in place isn't the cached old one).
// Main hands them back with the fix (r.pages: no whole-library scan, which set every background pass off again while you
// read); the shelf refreshes on its own once you're back on it. Only an older main without r.pages makes the scan
async function fixPages(g,r){const v="v="+Date.now().toString(36),put=L=>{g.art=g.art||{};g.art.manual=(L||[]).map(u=>typeof u==="string"&&/^bsfile:/.test(u)?u+(u.includes("?")?"&":"?")+v:u)};
  if(r&&Array.isArray(r.pages)){put(r.pages);pendingRefresh=true;return}
  try{const lib=await api.scan();if(!Array.isArray(lib))return;pendingLib=lib;for(const s of lib)for(const x of s.games||[])if(x.id===g.id){put(x.art&&x.art.manual);return}}catch{}}
function fixReopen(g){if(mode==="inspect"&&curGame()===g&&!rd.open&&hasManual(g)){rdCache.clear();openReader();return true}return false}
async function repairManual(){const g=curGame();if(!g||!hasManual(g)||wrongBusy||!api.repairManual)return;
  wrongAsk=0;wrongAskId=null;wrongBusy=true;fixRun={g,kind:"repair"};closeReader();toastWork("Repairing the manual…");
  try{const r=await api.repairManual(shelfFor(g).id,g.file);fixRun=null;
    if(r&&r.changed){toastWork("Repaired. Getting the pages…");await fixPages(g,r);const m=`✓ Manual repaired: ${r.what}`;toastDone(fixReopen(g)?m:m+". Open its case to read it")}
    else toastDone("Nothing to repair in this manual. If it's another game's, use Wrong game")}
  catch(e){fixRun=null;toast("Couldn't repair the manual: "+((e&&e.message)||e))}finally{fixRun=null;wrongBusy=false}}
async function wrongManual(){const g=curGame();if(!g||!hasManual(g)||wrongBusy)return;
  if(!fixOn()){wrongAsk=Date.now();wrongAskId=g.id;play_("tick",{dir:1});renderIspHints();setTimeout(()=>{if(wrongAsk&&Date.now()-wrongAsk>=8000){wrongAsk=0;if(rd.open)renderIspHints()}},8100);return}
  wrongAsk=0;wrongAskId=null;wrongBusy=true;fixRun={g,kind:"wrong"};closeReader();toastWork("Setting that manual aside…");
  try{const r=await api.rejectManual(shelfFor(g).id,g.file);fixRun=null;if(g.art)g.art.manual=[];
    if(r.got){toastWork("Found it. Getting the pages…");await fixPages(g,r);const m=`✓ New manual for ${g.title}`;toastDone(fixReopen(g)?m:m+". Open its case to read it")}
    else toastDone(r.offline?`Set aside. Couldn't search right now (offline?); Settings → Manuals can search later`:`Set aside. No other match for ${g.title} yet; Settings → Manuals can search by hand`)}
  catch(e){fixRun=null;toast("Couldn't replace the manual: "+((e&&e.message)||e))}finally{fixRun=null;wrongBusy=false}}
function closeReader(){if(!rd.open)return;rd.open=false;if(window.Inspect3D)Inspect3D.setPaused(false);$("inspect").classList.remove("reading");$("reader").classList.remove("on");$("reader").setAttribute("aria-hidden","true");renderIspHints();sfx.back()}
// Page 1 is the cover on its own; after that pages come in pairs like an open booklet.
// Pages are decoded off the main thread before they're shown, and the next few are warmed up in the background, so turning stays smooth.
const rdCache=new Map();
// scans sized to the screen (like the magazine reader), not full resolution; blob:/data: pages pass through unchanged
function rdSrc(u){return thumb(u,Math.min(innerHeight||1080,2048))}
function rdImage(src){if(!src)return Promise.resolve(null);src=rdSrc(src);let p=rdCache.get(src);if(!p){const im=new Image();im.decoding="async";im.src=src;p=im.decode().then(()=>im,()=>im);rdCache.set(src,p);if(rdCache.size>24){const k=rdCache.keys().next().value;rdCache.delete(k)}}return p}
function showSpread(anim){
  const L=$("rdLeft"),R=$("rdRight"),n=rd.pages.length;
  const left=rd.i===0?null:rd.pages[rd.i],right=rd.i===0?rd.pages[0]:rd.pages[rd.i+1];
  $("rdCount").textContent=rd.i===0?`Cover · ${n} pages`:`Pages ${rd.i+1}${right?"–"+(rd.i+2):""} of ${n}`;$("ispStep").textContent=$("rdCount").textContent;
  // show at once (the browser keeps the old picture until the new one is ready); pre-warm the neighbours in the background
  L.hidden=!left;const lu=rdSrc(left),ru=rdSrc(right);if(left&&L.src!==lu)L.src=lu;R.hidden=!right;if(right&&R.src!==ru)R.src=ru;
  [L,R].forEach(x=>{x.classList.remove("flip-in");if(anim){void x.offsetWidth;x.classList.add("flip-in")}});
  for(let k=rd.i+2;k<Math.min(n,rd.i+5);k++)rdImage(rd.pages[k]);
  if(rd.i>=2)rdImage(rd.pages[rd.i-2]);
}
function rdTurn(d){const n=rd.pages.length;let i=rd.i===0?(d>0?1:0):rd.i+d*2;if(i<1)i=0;if(i>=n)return void play_("error");rd.i=i;showSpread(true);play_("page")}
// Pulse: two taps on the chosen box open it: the case swings open, then the disc comes out (what Inspect does with a game's box, here for the apps of the phone)
function pulseOpenCase(){if(mode!=="browse")return;openInspect();if(mode!=="inspect")return;const tok=isp.tok;
  // the case swings open and stays so (both insides and the disc in its tray); a tap takes the disc out, holding it plays
  setTimeout(()=>{if(mode==="inspect"&&isp.tok===tok&&isp.state===0)ispAdvance()},900)}
function ispAdvance(){
  if(rd.open){closeReader();return}
  isp.tz=window.PulseHost&&innerWidth<innerHeight?.72:1;isp.tpx=isp.tpy=0; /* an upright phone: the opened case is wider than the screen, so it is drawn smaller */
  const el=$("inspect");isp.last=performance.now();
  if(isp.state===0){isp.state=1;el.classList.add("open");isp.ty=Math.round(isp.ty/360)*360+18;isp.tx=-6;play_("caseOpen")}
  else if(isp.state===1){isp.state=2;el.classList.add("out");if(ispSpins()){isp.ty=isp.sy0=isp.ry;isp.tf=0}else isp.ty=Math.round(isp.ty/360)*360;isp.tx=-4;play_("discOut")} /* a disc's spin counts from here (sy0), so it lifts out without a jump */
  else{bumpDisc();return} // a tap at "disc out" just nudges the disc; hold to play
  if(window.Inspect3D)Inspect3D.setState(isp.state);
  renderIspHints();
}
let holdT=null,holdStart=0;
function bumpDisc(){if(window.Inspect3D&&Inspect3D.isActive()){Inspect3D.bump();return}const d=$("inspect").querySelector(".isp-disc");d.style.transition="transform .25s";d.style.transform+=" translateZ(20px)";setTimeout(()=>{d.style.transform=d.style.transform.replace(" translateZ(20px)","");setTimeout(()=>{d.style.transition=""},300)},120)}
// the console under the disc, drawn for the console of the box: each as the machine looks from the front (its plates, its slot, its lights), the slot marked so the disc
// can light it up and slide into it
const CONSOLE_ART={
  ps5:'<svg viewBox="0 0 320 170" aria-hidden="true"><defs><linearGradient id="p5w" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#ffffff"/><stop offset="0.6" stop-color="#e7eaf0"/><stop offset="1" stop-color="#b4bcc9"/></linearGradient><linearGradient id="p5k" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#1c1f27"/><stop offset="0.5" stop-color="#06070a"/><stop offset="1" stop-color="#15171d"/></linearGradient></defs><ellipse cx="160" cy="156" rx="134" ry="8" fill="#000" opacity=".4"/><path d="M6 70C10 40 60 22 160 22s150 18 154 48c1 6-3 8-9 8H15c-6 0-10-2-9-8z" fill="url(#p5w)"/><path d="M6 100c-1-6 3-8 9-8h290c6 0 10 2 9 8-4 30-54 48-154 48S10 130 6 100z" fill="url(#p5w)"/><path d="M44 48c50-13 182-13 232 0" stroke="#fff" stroke-width="2.4" fill="none" opacity=".8"/><rect x="16" y="76" width="288" height="18" rx="3" fill="url(#p5k)"/><rect class="led" x="20" y="76" width="280" height="1.8" fill="#3d7dff"/><rect class="led" x="20" y="92.2" width="280" height="1.8" fill="#3d7dff"/><rect class="slot" x="58" y="82.4" width="152" height="5.2" rx="2.6" fill="#000" stroke="#2c313c" stroke-width=".8"/><rect x="228" y="82" width="14" height="6" rx="1.5" fill="#2b303b"/><rect x="248" y="83" width="9" height="4" rx="2" fill="#2b303b"/><circle cx="272" cy="85" r="2.2" fill="#3a404c"/><circle cx="282" cy="85" r="2.2" fill="#3a404c"/></svg>',
  xsx:'<svg viewBox="0 0 320 170" aria-hidden="true"><defs><linearGradient id="xsk" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#2a2e35"/><stop offset="0.08" stop-color="#17191e"/><stop offset="1" stop-color="#08090b"/></linearGradient></defs><ellipse cx="160" cy="156" rx="134" ry="8" fill="#000" opacity=".4"/><rect x="20" y="26" width="280" height="122" rx="5" fill="url(#xsk)" stroke="#353a44"/><path d="M22 30h276" stroke="#4a505c" stroke-width="1"/><circle cx="50" cy="58" r="12" fill="#0b0c0f" stroke="#e9eef5" stroke-width="1.6"/><path class="led" d="M44 51c4 3 8 8 12 15M56 51c-4 3-8 8-12 15" stroke="#f2f6fb" stroke-width="1.8" fill="none" stroke-linecap="round"/><rect class="slot" x="42" y="112" width="130" height="6" rx="3" fill="#000" stroke="#2b2f37" stroke-width=".8"/><rect x="184" y="110" width="5" height="10" rx="2" fill="#2b2f37"/><rect x="204" y="109" width="16" height="9" rx="1.5" fill="#0b0c0f" stroke="#353a44"/><circle cx="234" cy="114" r="2.4" fill="#2b2f37"/><g fill="#3fae2a"><circle cx="266" cy="40" r="2" opacity="0.25"/><circle cx="266" cy="50" r="2" opacity="0.25"/><circle cx="266" cy="60" r="2" opacity="0.25"/><circle cx="266" cy="70" r="2" opacity="0.25"/><circle cx="266" cy="80" r="2" opacity="0.25"/><circle cx="266" cy="90" r="2" opacity="0.25"/><circle cx="266" cy="100" r="2" opacity="0.25"/><circle cx="266" cy="110" r="2" opacity="0.25"/><circle cx="266" cy="120" r="2" opacity="0.25"/><circle cx="266" cy="130" r="2" opacity="0.25"/><circle cx="274" cy="40" r="2" opacity="0.50"/><circle cx="274" cy="50" r="2" opacity="0.50"/><circle cx="274" cy="60" r="2" opacity="0.50"/><circle cx="274" cy="70" r="2" opacity="0.50"/><circle cx="274" cy="80" r="2" opacity="0.50"/><circle cx="274" cy="90" r="2" opacity="0.50"/><circle cx="274" cy="100" r="2" opacity="0.50"/><circle cx="274" cy="110" r="2" opacity="0.50"/><circle cx="274" cy="120" r="2" opacity="0.50"/><circle cx="274" cy="130" r="2" opacity="0.50"/><circle cx="282" cy="40" r="2" opacity="0.75"/><circle cx="282" cy="50" r="2" opacity="0.75"/><circle cx="282" cy="60" r="2" opacity="0.75"/><circle cx="282" cy="70" r="2" opacity="0.75"/><circle cx="282" cy="80" r="2" opacity="0.75"/><circle cx="282" cy="90" r="2" opacity="0.75"/><circle cx="282" cy="100" r="2" opacity="0.75"/><circle cx="282" cy="110" r="2" opacity="0.75"/><circle cx="282" cy="120" r="2" opacity="0.75"/><circle cx="282" cy="130" r="2" opacity="0.75"/><circle cx="290" cy="40" r="2" opacity="1.00"/><circle cx="290" cy="50" r="2" opacity="1.00"/><circle cx="290" cy="60" r="2" opacity="1.00"/><circle cx="290" cy="70" r="2" opacity="1.00"/><circle cx="290" cy="80" r="2" opacity="1.00"/><circle cx="290" cy="90" r="2" opacity="1.00"/><circle cx="290" cy="100" r="2" opacity="1.00"/><circle cx="290" cy="110" r="2" opacity="1.00"/><circle cx="290" cy="120" r="2" opacity="1.00"/><circle cx="290" cy="130" r="2" opacity="1.00"/></g></svg>',
  xone:'<svg viewBox="0 0 320 170" aria-hidden="true"><defs><linearGradient id="xok" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#3a3f47"/><stop offset="0.1" stop-color="#1c1e23"/><stop offset="1" stop-color="#0c0d10"/></linearGradient><linearGradient id="xom" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#24272d"/><stop offset="1" stop-color="#121317"/></linearGradient></defs><ellipse cx="160" cy="156" rx="134" ry="8" fill="#000" opacity=".4"/><rect x="14" y="40" width="292" height="100" rx="5" fill="url(#xom)" stroke="#383d46"/><rect x="14" y="40" width="150" height="100" rx="5" fill="url(#xok)"/><g stroke="#0a0b0d" stroke-width="2"><path d="M186 46l-14 88"/><path d="M196 46l-14 88"/><path d="M206 46l-14 88"/><path d="M216 46l-14 88"/><path d="M226 46l-14 88"/><path d="M236 46l-14 88"/><path d="M246 46l-14 88"/><path d="M256 46l-14 88"/><path d="M266 46l-14 88"/><path d="M276 46l-14 88"/><path d="M286 46l-14 88"/><path d="M296 46l-14 88"/></g><rect class="slot" x="30" y="86" width="118" height="6" rx="3" fill="#000" stroke="#2e3239" stroke-width=".8"/><rect x="150" y="84" width="4" height="10" rx="2" fill="#30343b"/><circle cx="284" cy="60" r="9" fill="#0d0e11" stroke="#e9eef5" stroke-width="1.4"/><path class="led" d="M279 55c3 2 6 6 10 11M289 55c-3 2-6 6-10 11" stroke="#f2f6fb" stroke-width="1.5" fill="none" stroke-linecap="round"/></svg>',
  x360:'<svg viewBox="0 0 320 170" aria-hidden="true"><defs><linearGradient id="x3w" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#ffffff"/><stop offset="0.5" stop-color="#eceef1"/><stop offset="1" stop-color="#c4c8ce"/></linearGradient><linearGradient id="x3c" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#f4f6f8"/><stop offset="0.45" stop-color="#aab1bb"/><stop offset="0.55" stop-color="#7b838f"/><stop offset="1" stop-color="#d3d8de"/></linearGradient></defs><ellipse cx="160" cy="156" rx="134" ry="8" fill="#000" opacity=".4"/><path d="M20 34h280c-10 34-10 70 0 104H20c10-34 10-70 0-104z" fill="url(#x3w)" stroke="#b3b9c2"/><path d="M30 86h260" stroke="#c9cdd3" stroke-width="1.4"/><rect class="slot" x="58" y="56" width="160" height="15" rx="3" fill="url(#x3c)" stroke="#8a919c" stroke-width=".8"/><rect x="224" y="60" width="9" height="7" rx="2" fill="#aab1bb"/><circle cx="256" cy="106" r="16" fill="#dfe3e8" stroke="#aab1bb"/><circle class="led" cx="256" cy="106" r="11.5" fill="none" stroke="#7ac143" stroke-width="3" stroke-dasharray="15 3.06"/><circle cx="256" cy="106" r="6" fill="#f5f6f8" stroke="#9aa1ab"/><path d="M256 102v4" stroke="#7b838f" stroke-width="1.6" stroke-linecap="round"/><rect x="62" y="98" width="30" height="12" rx="2" fill="#d2d6dc" stroke="#aab1bb"/><rect x="100" y="98" width="30" height="12" rx="2" fill="#d2d6dc" stroke="#aab1bb"/><rect x="150" y="100" width="14" height="7" rx="1.5" fill="#8a919c"/><rect x="170" y="100" width="14" height="7" rx="1.5" fill="#8a919c"/></svg>',
  ps4:'<svg viewBox="0 0 320 170" aria-hidden="true"><defs><linearGradient id="p4g" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#3b404a"/><stop offset="0.12" stop-color="#1b1d23"/><stop offset="1" stop-color="#0b0c0f"/></linearGradient><linearGradient id="p4m" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#23262c"/><stop offset="1" stop-color="#101114"/></linearGradient></defs><ellipse cx="160" cy="156" rx="134" ry="8" fill="#000" opacity=".4"/><path d="M44 30h262l-12 46H32z" fill="url(#p4m)" stroke="#383d47"/><path d="M44 30h150l-12 46H32z" fill="url(#p4g)"/><path d="M30 76h264l-3 12H27z" fill="#030304"/><path d="M26 88h262l-12 46H14z" fill="url(#p4m)" stroke="#383d47"/><path class="led" d="M194 30l-12 46" stroke="#3b82ff" stroke-width="2.4"/><path class="slot" d="M40 82h132" stroke="#11141a" stroke-width="5" stroke-linecap="round"/><rect x="212" y="79" width="13" height="6" rx="1" fill="#2a2e36"/><rect x="232" y="79" width="13" height="6" rx="1" fill="#2a2e36"/></svg>',
  ps3:'<svg viewBox="0 0 320 170" aria-hidden="true"><defs><linearGradient id="p3k" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#4a4e57"/><stop offset="0.14" stop-color="#15171b"/><stop offset="1" stop-color="#050608"/></linearGradient><linearGradient id="p3c" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#eef1f5"/><stop offset="1" stop-color="#7d8693"/></linearGradient></defs><ellipse cx="160" cy="156" rx="134" ry="8" fill="#000" opacity=".4"/><path d="M16 124V74c0-24 42-40 144-40s144 16 144 40v50c0 7-4 11-11 11H27c-7 0-11-4-11-11z" fill="url(#p3k)" stroke="#2d3038"/><path d="M40 60c50-14 190-14 240 0" stroke="#fff" stroke-width="1.6" fill="none" opacity=".35"/><rect x="16" y="100" width="288" height="3" fill="url(#p3c)"/><rect class="slot" x="66" y="80" width="156" height="6" rx="3" fill="#000" stroke="#3a3e47" stroke-width=".8"/><circle class="led" cx="246" cy="116" r="2.6" fill="#35d06f"/><circle cx="258" cy="116" r="2.6" fill="#2a2d34"/><rect x="60" y="111" width="14" height="7" rx="1" fill="#22252b"/><rect x="80" y="111" width="14" height="7" rx="1" fill="#22252b"/></svg>',
  sw:'<svg viewBox="0 0 320 170" aria-hidden="true"><ellipse cx="160" cy="156" rx="134" ry="8" fill="#000" opacity=".4"/><rect x="30" y="20" width="38" height="98" rx="16" fill="#0ab9e6"/><rect x="252" y="20" width="38" height="98" rx="16" fill="#ff4554"/><circle cx="49" cy="46" r="8" fill="#075d74"/><circle cx="49" cy="46" r="5" fill="#0a3f4f"/><g fill="#075d74"><circle cx="49" cy="72" r="3"/><circle cx="42" cy="79" r="3"/><circle cx="56" cy="79" r="3"/><circle cx="49" cy="86" r="3"/></g><circle cx="271" cy="80" r="8" fill="#8a1c27"/><circle cx="271" cy="80" r="5" fill="#5e1019"/><g fill="#8a1c27"><circle cx="271" cy="40" r="3"/><circle cx="264" cy="47" r="3"/><circle cx="278" cy="47" r="3"/><circle cx="271" cy="54" r="3"/></g><rect x="66" y="16" width="188" height="104" rx="6" fill="#17191e" stroke="#2e323a"/><rect x="78" y="27" width="164" height="82" rx="3" fill="#05070a"/><rect class="slot" x="182" y="11" width="50" height="6.5" rx="2" fill="#000" stroke="#444a56" stroke-width=".9"/><path d="M54 98h212v50a7 7 0 0 1-7 7H61a7 7 0 0 1-7-7z" fill="#1e2127" stroke="#333842"/><circle class="led" cx="74" cy="140" r="2.2" fill="#35d06f"/><rect x="140" y="124" width="40" height="5" rx="2.5" fill="#2c3038"/></svg>',
  pc:'<svg viewBox="0 0 320 170" aria-hidden="true"><defs><linearGradient id="pck" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#3a3f4a"/><stop offset="0.1" stop-color="#22252c"/><stop offset="1" stop-color="#121418"/></linearGradient></defs><ellipse cx="160" cy="156" rx="134" ry="8" fill="#000" opacity=".4"/><rect x="28" y="22" width="264" height="126" rx="8" fill="url(#pck)" stroke="#4a5160"/><rect x="46" y="40" width="228" height="28" rx="3" fill="#0d0f13" stroke="#3a4050"/><rect class="slot" x="58" y="51.5" width="176" height="5" rx="2.5" fill="#000" stroke="#2c313c" stroke-width=".8"/><rect x="244" y="50" width="18" height="8" rx="2" fill="#2c313c"/><g stroke="#0b0c0f" stroke-width="3" stroke-linecap="round"><path d="M52 86h130"/><path d="M52 95h130"/><path d="M52 104h130"/><path d="M52 113h130"/><path d="M52 122h130"/><path d="M52 131h130"/></g><circle cx="250" cy="110" r="13" fill="#15171c" stroke="#4a5160"/><circle class="led" cx="250" cy="110" r="8.5" fill="none" stroke="#4b86ff" stroke-width="2"/><path d="M250 105v5" stroke="#cfd6e2" stroke-width="1.8" stroke-linecap="round"/><rect x="204" y="90" width="14" height="7" rx="1" fill="#0b0c0f" stroke="#3a4050"/><rect x="204" y="104" width="14" height="7" rx="1" fill="#0b0c0f" stroke="#3a4050"/><circle cx="211" cy="126" r="3.2" fill="#0b0c0f" stroke="#3a4050"/></svg>'
};
const consoleKey=b=>({ps5:"ps5",ps4:"ps4",ps3:"ps3",switch:"sw",switch2:"sw",xbox360:"x360",xboxone:"xone",xboxseries:"xsx"})[b]||"pc";
// the disc: a touch or mouse hold ejects it (the box shakes); dragged onto the console below it starts the game; Enter drops it in too
let holdByPtr=false;
const discRest=()=>{const d=document.querySelector("#inspect .isp-disc");return d?Math.round(d.offsetHeight*.9):0};
function ejectDisc(){
  holdByPtr=false;isp.ejected=true;const el=$("inspect"),d=document.querySelector("#inspect .isp-disc");
  el.classList.add("isp-shake","disc-out");setTimeout(()=>el.classList.remove("isp-shake"),480);
  const gl=document.querySelector("#inspect canvas.isp-gl.on");
  if(d&&!gl)d.style.translate=`0 ${-discRest()}px`;
  const cons=$("ispConsole"),cg=curGame();cons.innerHTML=CONSOLE_ART[consoleKey(cg&&cg.box)]||CONSOLE_ART.pc;cons.classList.add("art","on");rumble(.5,.3,140);play_("hold");
}
function resetEject(){
  isp.ejected=false;holdByPtr=false;const el=$("inspect");if(el)el.classList.remove("isp-shake","disc-out");
  const d=document.querySelector("#inspect .isp-disc");if(d)d.style.translate="";
  const gl2=document.querySelector("#inspect canvas.isp-gl");if(gl2){gl2.style.translate="";gl2.style.transition="";gl2.style.scale="";gl2.style.opacity=""}
  if(d){d.style.scale="";d.style.opacity=""}
  const c=$("ispConsole");if(c)c.classList.remove("on","over","in");
}
function retractDisc(){resetEject()}
// the disc is in: the game starts at once (no "opening" page in between)
let quickLaunch=false;
function insertDisc(){
  const c=$("ispConsole");if(c)c.classList.add("in");play_("boot");rumble(.6,.3,160);
  quickLaunch=true;closeInspect();setTimeout(()=>{play();setTimeout(()=>{quickLaunch=false},1500)},60);
}
function holdBegin(){
  if(mode==="inspect"&&isp.ejected){if(!holdByPtr)insertDisc();holdByPtr=false;return}
  if(mode!=="inspect"||isp.state!==2||rd.open||holdT)return;holdStart=performance.now();play_("hold");$("ispHold").classList.add("on");holdT=requestAnimationFrame(holdTick)}
function holdTick(){if(!holdT)return;if(mode!=="inspect"||isp.state!==2||rd.open){holdEnd(false);return}const p=Math.min(1,(performance.now()-holdStart)/800);$("ispHoldBar").style.width=(p*100)+"%";if(p>=1){if(holdByPtr&&!isp.ejected){cancelAnimationFrame(holdT);holdT=null;$("ispHold").classList.remove("on");$("ispHoldBar").style.width="0%";ejectDisc();if(window.__discGrab)__discGrab();return}holdEnd(true);closeInspect();setTimeout(play,120);return}holdT=requestAnimationFrame(holdTick)}
function holdEnd(fire){if(!holdT)return;cancelAnimationFrame(holdT);holdT=null;$("ispHold").classList.remove("on");$("ispHoldBar").style.width="0%";if(!fire&&isp.state===2)bumpDisc()}
function ispBack(){
  if(isp.ejected){retractDisc();return} // Back puts the disc back in first
  const el=$("inspect");isp.last=performance.now();
  if(rd.open){closeReader();return}
  if(isp.state===2){if(ispSpins()){isp.ty=isp.sy0+Math.round((isp.ty-isp.sy0)/360)*360;if(!(window.Inspect3D&&Inspect3D.isActive()))isp.ry=isp.ty} /* back the nearest whole spin: the case comes back facing as it left (the CSS case at once: its spin isn't blended out) */isp.tf=0;isp.tx=Math.max(-50,Math.min(50,isp.tx));isp.state=1;el.classList.remove("out")} /* (the disc could tilt further than the case does) */
  else if(isp.state===1){isp.state=0;el.classList.remove("open")}
  else{closeInspect();sfx.back();detailsReturn();return}
  if(window.Inspect3D)Inspect3D.setState(isp.state);
  sfx.back();renderIspHints();
}
function renderIspHints(){
  const steps=["Closed","Open","Disc out"],pad=inputMode==="pad",cart=$("inspect").classList.contains("cart");
  $("ispStep").textContent=steps[isp.state];
  const next=isp.state===0?"Open case":isp.state===1?(cart?"Take out cartridge":"Take out disc"):"Hold to play";
  let items,acts;
  if(rd.open){$("ispStep").textContent=$("rdCount").textContent||"Manual";items=[[pad?["L","R"]:["←","→"],"Turn page"],[pad?["x"]:["Space"],"Next page"],[pad?["o"]:["Esc"],"Put manual back"]];acts=[()=>rdTurn(1),()=>rdTurn(1),closeReader];
    if(hasManual(curGame())){if(fixOn()){items=[[pad?["tri"]:["W"],"Wrong game: find the right one"],[pad?["sq"]:["R"],"Repair it (cover, pages)"],[pad?["o"]:["Esc"],"Cancel"]];acts=[wrongManual,repairManual,fixCancel]}
      else{items.push([pad?["tri"]:["W"],"Fix manual…"]);acts.push(wrongManual)}}}
  else{items=[[pad?["L","R"]:["←","→"],ispSpins()?"Spin":"Next side"],[pad?["L2","R2"]:["+","−"],"Zoom"],[pad?["tri"]:["F"],"Turn over"],[pad?["x"]:["Enter"],next],[pad?["o"]:["Esc"],isp.state?"Put back":"Close"]];acts=[()=>{ispTurn(90,0,true);ispSnap()},()=>ispZoom(1),()=>ispFlip(),ispAdvance,ispBack];
    if(isp.state>=1&&curGame()&&curGame().kind==="rom"){items.splice(4,0,[pad?["sq"]:["M"],hasManual(curGame())?"Read manual":"Read booklet"]);acts.splice(4,0,openReader)}}
  const h=$("ispHints");h.innerHTML="";
  items.forEach(([keys,label],i)=>{const b=document.createElement("button");b.type="button";b.className="hint";
    b.innerHTML=`<span class="keys">${keys.map(k=>pad?glyph(k):`<span class="g">${k}</span>`).join("")}</span><span></span>`;b.lastChild.textContent=label;
    if(!rd.open&&isp.state===2&&label==="Hold to play"){b.addEventListener("pointerdown",e=>{e.stopPropagation();holdBegin()});b.addEventListener("pointerup",e=>{e.stopPropagation();holdEnd(false)});b.addEventListener("pointerleave",()=>holdEnd(false))}
    else b.addEventListener("click",e=>{e.stopPropagation();acts[i]()});h.appendChild(b)});
}
addEventListener("keyup",e=>{if(e.key==="Enter"){holdEnd(false);musHoldEnd(false)}});
function inspectKey(e){
  const k=e.key;setInput("kbd");let handled=true;
  if(rd.open){
    if(k==="ArrowRight"||k==="PageDown"||k===" ")rdTurn(1);else if(k==="ArrowLeft"||k==="PageUp")rdTurn(-1);
    else if((k==="Escape"||k==="Backspace")&&fixOn()){if(!e.repeat)fixCancel()} // (beta.9: the fix menu first)
    else if((k==="r"||k==="R")&&fixOn()){if(!e.repeat)repairManual()}
    else if(k==="Escape"||k==="Backspace"||k==="Enter"||k==="m"||k==="M"){if(!e.repeat)closeReader()}
    else if(k==="Home"){rd.i=0;showSpread(true)}else if(k==="w"||k==="W"){if(!e.repeat)wrongManual()}else handled=false;
    if(handled)e.preventDefault();return;
  }
  if(k==="+"||k==="="||k==="PageUp")ispZoom(1);else if(k==="-"||k==="_"||k==="PageDown")ispZoom(-1);
  else if(e.shiftKey&&k.startsWith("Arrow")){ispPan(k==="ArrowLeft"?30:k==="ArrowRight"?-30:0,k==="ArrowUp"?30:k==="ArrowDown"?-30:0)}
  else if(k==="m"||k==="M"){if(!e.repeat&&isp.state>=1)openReader()}
  else if(k==="ArrowLeft"){if(!e.repeat){ispTurn(-90,0,true);ispSnap()}}else if(k==="ArrowRight"){if(!e.repeat){ispTurn(90,0,true);ispSnap()}}else if(k==="ArrowUp")ispTurn(0,7,ANDROID);else if(k==="ArrowDown")ispTurn(0,-7,ANDROID);
  else if(k==="f"||k==="F"){if(!e.repeat)ispFlip()}
  else if(k==="Enter"){if(e.repeat)return;if(isp.state===2)holdBegin();else ispAdvance()}
  else if(k==="Escape"||k==="Backspace"){if(!e.repeat)ispBack()}
  else if(k==="v"||k==="V"){if(!e.repeat){closeInspect();detailsReturn()}}
  else handled=false;
  if(handled)e.preventDefault();
}
(function(){
  const el=$("inspect");
  el.addEventListener("pointerdown",e=>{if(mode!=="inspect"||e.target.closest(".isp-hints"))return;if(rd.open){isp.drag={x:e.clientX,y:e.clientY,moved:false};return}if(isp.state===2){holdByPtr=true;holdBegin()}isp.drag={x:e.clientX,y:e.clientY,moved:false};el.classList.add("dragging")});
  // the ejected disc follows the pointer; over the console it lights up, and let go there it starts the game, else it goes back.
  // With the 3D view (WebGL) the disc is drawn in the canvas, so the canvas is what moves; without it, the DOM disc moves
  let dr=null;
  const discSurface=()=>{const g=document.querySelector("#inspect canvas.isp-gl.on");return g||document.querySelector("#inspect .isp-disc")};
  const discBase=s=>s&&s.classList.contains("isp-gl")?[0,0]:[0,-discRest()];
  const discCircle=s=>{const r=s.getBoundingClientRect(),g=s.classList.contains("isp-gl");return {x:r.left+r.width/2,y:r.top+r.height/2,r:g?Math.min(r.width,r.height*.5)*.34:Math.min(r.width,r.height)/2}};
  const overConsole=s=>{const c=discCircle(s),k=$("ispConsole").getBoundingClientRect();const nx=Math.max(k.left,Math.min(c.x,k.right)),ny=Math.max(k.top,Math.min(c.y,k.bottom));return Math.hypot(c.x-nx,c.y-ny)<c.r*.7};
  el.addEventListener("pointerdown",e=>{if(!isp.ejected||mode!=="inspect")return;const s=discSurface();if(!s)return;const c=discCircle(s);
    if(Math.hypot(e.clientX-c.x,e.clientY-c.y)>c.r*1.3)return; // a touch away from the disc is not the disc
    e.stopImmediatePropagation();dr={x:e.clientX,y:e.clientY,s,base:discBase(s),cur:discBase(s),far:0};s.style.transition="none"},true);
  // the hold that brings the disc out leaves it in the hand: the same finger carries it on, without a second touch
  window.__discGrab=()=>{const s=discSurface();if(!s||!isp.drag)return;dr={x:isp.drag.x,y:isp.drag.y,s,base:discBase(s),cur:discBase(s),far:0,held:true};isp.drag=null;el.classList.remove("dragging");setTimeout(()=>{if(dr&&dr.s===s)s.style.transition="none"},420)};
  addEventListener("pointermove",e=>{if(!dr)return;const dx=e.clientX-dr.x,dy=e.clientY-dr.y;dr.far=Math.max(dr.far,Math.hypot(dx,dy));if(dr.far>6)dr.s.style.transition="none";dr.cur=[dr.base[0]+dx,dr.base[1]+dy];dr.s.style.translate=`${dr.cur[0]}px ${dr.cur[1]}px`;$("ispConsole").classList.toggle("over",overConsole(dr.s))});
  addEventListener("pointerup",e=>{if(!dr)return;const s=dr.s,base=dr.base,cur=dr.cur,still=dr.held&&dr.far<12;dr=null;const hit=overConsole(s);s.style.transition="";
    if(still){$("ispConsole").classList.remove("over");return} // let go right after it came out: it stays out, to be picked up again
    if(hit){ // into the slot: the disc slides to the console's slot and goes in, then the game starts
      const sl=$("ispConsole").querySelector(".slot"),c=discCircle(s);
      if(sl){const k=sl.getBoundingClientRect();s.style.transition="translate .26s cubic-bezier(.5,0,.8,.5),scale .26s ease-in,opacity .22s ease-in .06s";s.style.translate=`${cur[0]+(k.left+k.width/2-c.x)}px ${cur[1]+(k.top+k.height/2-c.y)}px`;s.style.scale=".18";s.style.opacity="0";setTimeout(insertDisc,270)}else insertDisc()}
    else{$("ispConsole").classList.remove("over");s.style.transition="translate .35s cubic-bezier(.2,.8,.2,1)";s.style.translate=`${base[0]}px ${base[1]}px`;setTimeout(()=>{if(isp.ejected)resetEject()},360)}});
  addEventListener("pointermove",e=>{if(!isp.drag)return;const dx=e.clientX-isp.drag.x,dy=e.clientY-isp.drag.y;if(Math.abs(dx)+Math.abs(dy)>4){isp.drag.moved=true;holdEnd(true)}if(isp.tz>1&&e.shiftKey)ispPan(dx,dy);else ispTurn(dx*.22,-dy*.16);isp.drag.x=e.clientX;isp.drag.y=e.clientY});
  addEventListener("pointerup",e=>{if(!isp.drag)return;const click=!isp.drag.moved;isp.drag=null;el.classList.remove("dragging");if(!click&&!rd.open&&isp.tz<=1)ispSnap();const wasHolding=!!holdT;holdEnd(false);if(click&&mode==="inspect"&&!wasHolding){if(rd.open)rdTurn(e.clientX<innerWidth/2?-1:1);else ispAdvance()}});
})();

// Freeze watch (shelf side): a frame that takes over a quarter second is logged with what was on screen, so a
// reported stutter can be traced to its cause
try{let lastLong=0;new PerformanceObserver(l=>{for(const e of l.getEntries()){if(e.duration<250||performance.now()-lastLong<1000)continue;lastLong=performance.now();api.log&&api.log(`freeze watch: shelf blocked ${Math.round(e.duration)} ms (mode ${mode}${document.hidden?", hidden":""})`)}}).observe({type:"longtask",buffered:false})}catch{}
addEventListener("error",e=>{try{api.log&&api.log(`error: ${e.message} @${(e.filename||"").split("/").pop()}:${e.lineno}`)}catch{}});
addEventListener("unhandledrejection",e=>{try{api.log&&api.log(`rejection: ${e.reason&&(e.reason.stack||e.reason.message)||e.reason}`)}catch{}});
/* ---------- insert animation: case opens, disc goes into the drive ---------- */
const MEDIA={gc:{kind:"disc",size:.62,drive:"top"},wii:{kind:"disc",size:.86,drive:"front"},wiiu:{kind:"disc",size:.84,drive:"front"},psp:{kind:"umd",size:.66,drive:"hatch"},sw:{kind:"cart",size:.5,drive:"slot"},sw2:{kind:"cart",size:.5,drive:"slot"},n3ds:{kind:"cart",size:.5,drive:"slot"},ps3:{kind:"disc",size:.78,drive:"front"},ps4:{kind:"disc",size:.78,drive:"front"},ps5:{kind:"disc",size:.78,drive:"front"},x360:{kind:"disc",size:.8,drive:"tray"},ps2:{kind:"disc",size:.8,drive:"tray"}};
if(window.BS_SYSTEMS_EXTRA)Object.assign(MEDIA,BS_SYSTEMS_EXTRA.media||{});
const DECK={front:"frontslot",tray:"trayload",hatch:"hatchload"};
let insertT=null,insertSeatAt=0,slideEndAt=0;const INSERT_3D=false;
// Every console boots on its own full-screen slide (see bootslide.css). The old disc-into-the-console animation (runInsert below)
// is kept, unused, for the museum's planned 3D load-into-the-console.
const SLIDE_LOOKS=new Set(["gc","wii","wiiu","sw","psp","ps3","ps2","ps4","ps5","x360","n3ds"]);
const WAVE=(amp,len,ph)=>{let d=`M0 ${50+amp*Math.sin(ph)}`;for(let x=10;x<=2000;x+=10)d+=` L${x} ${(50+amp*Math.sin(x/len*Math.PI*2+ph)+amp*.35*Math.sin(x/len*Math.PI*5.3+ph*2)).toFixed(1)}`;return d};
function runSlide(s,g,done,hideAfter){
  const el=$("insert"),sl=$("bslide"),style=STYLE[s.id]||STYLE.media,front=frontUrl(g),look=SLIDE_LOOKS.has(s.id)?s.id:s.id==="sw2"?"sw":"other"; /* (Switch 2: the Switch boot slide) */
  el.classList.remove("go","out","cart","umd","topload","slotload","has-model","has-console");Object.values(DECK).forEach(c=>el.classList.remove(c));
  el.classList.add("slide");sl.className="ins-slide bs-"+look;sl.classList.toggle("no-cover",!front);
  const [t0,t1]=style.tint||["#2a2f6e","#0b0d1f"];sl.style.setProperty("--bs-t0",t0);sl.style.setProperty("--bs-t1",t1);if(look==="other")sl.style.setProperty("--bs-acc",style.glow||"#9d8cff");else sl.style.removeProperty("--bs-acc");
  sl.style.setProperty("--bs-ratio",style.ratio||.71);
  const fx=$("bsFx");fx.innerHTML="";
  if(look==="psp"||look==="ps3")[[18,700,0],[26,950,1.7],[12,560,3.1]].forEach(([a,l,p])=>{const sv=document.createElementNS("http://www.w3.org/2000/svg","svg");sv.setAttribute("viewBox","0 0 2000 100");sv.setAttribute("preserveAspectRatio","none");const pa=document.createElementNS("http://www.w3.org/2000/svg","path");pa.setAttribute("d",WAVE(a,l,p));sv.append(pa);fx.append(sv)});
  else{const n=look==="ps2"||look==="ps4"||look==="ps5"?16:4;for(let i=0;i<n;i++){const it=document.createElement("i");if(n>4){it.style.left=(4+((i*37)%92))+"%";it.style.animationDelay=(-(i*0.83)%7)+"s";it.style.transform=`scale(${.5+((i*13)%10)/10})`}fx.append(it)}}
  $("bsCover").src=front?thumb(front,900):"";
  $("bsBoot").textContent=`Booting ${(WORDMARKS[s.id]||[s.name])[0]}`;
  const t=themes[s.id]||{};renderWordmark($("bsWord"),s.id,s.name,"bs-word ");$("bsWord").hidden=!!t.logo;$("bsLogo").hidden=!t.logo;if(t.logo)cleanLogo(t.logo).then(r=>{$("bsLogo").src=r.url});
  $("bsTitle").textContent=g.title;$("bsEmu").textContent=s.emulatorName?`Starting in ${s.emulatorName}`:"";
  const bar=$("bsBar").querySelector("i");bar.style.animation="none";void bar.offsetWidth;bar.style.animation="";
  el.classList.add("on");el.setAttribute("aria-hidden","false");void el.offsetWidth;el.classList.add("go");
  lastGoAt=performance.now();play_("go");setTimeout(()=>play_("boot"),700);rumble(.4,.25,90);
  slideEndAt=performance.now()+(reduced()?300:1000); // the line is full here: the boot sound fades out with it (about 2/5 of the old slide)
  clearTimeout(insertT);insertT=setTimeout(()=>{if(hideAfter)hideInsert();if(done)done()},reduced()?300:880);
  insertSeatAt=performance.now()+(reduced()?150:200); // the emulator starts right away under the slide; Boxshelf keeps the slide up a fixed ~4 s so the line fills
}
function runInsert(s,g,done,hideAfter){
  if(!INSERT_3D){runSlide(s,g,done,hideAfter);return}
  $("insert").classList.remove("slide");
  const el=$("insert"),style=STYLE[s.id]||STYLE.media,m=MEDIA[s.id]||MEDIA.wii;
  const front=frontUrl(g);
  const PT=!!window.PulseHost&&innerWidth<innerHeight,ch=PT?Math.min(innerHeight*.4,innerWidth*.5/style.ratio):Math.min(innerHeight*.46,innerWidth*.3/style.ratio),cw=ch*style.ratio;
  el.style.setProperty("--ch",ch+"px");el.style.setProperty("--cw",cw+"px");el.style.setProperty("--disc",(cw*m.size)+"px");
  el.style.setProperty("--case",style.caseColor);el.style.setProperty("--case-in",style.caseSide);el.style.setProperty("--accent",style.glow);
  el.classList.remove("go","out","cart","umd");if(m.kind!=="disc")el.classList.add(m.kind);
  tinyBlur((g.art&&(g.art.hero||g.art.front))||front,d=>{el.querySelector(".ins-bg img").src=d||""});
  el.querySelector(".ins-lid-front img").src=front;
  const man=el.querySelector(".ins-manual");if(man){man.style.backgroundImage=g.art&&g.art.manual&&g.art.manual[0]?`url("${g.art.manual[0]}")`:"";man.style.backgroundSize="cover";man.style.backgroundPosition="center";man.classList.toggle("real",!!(g.art&&g.art.manual&&g.art.manual.length))}
  el.querySelector(".ins-label").style.backgroundImage=`url("${front}")`;
  const drive=themes[s.id]&&themes[s.id].console;el.classList.toggle("has-console",!!drive);if(drive)el.querySelector(".ins-console-img").src=drive;
  const mdl=themes[s.id]&&themes[s.id].model;
  const useModel=!!mdl&&!drive&&window.ConsoleModel&&ConsoleModel.ready(mdl);
  if(mdl&&!useModel&&!drive&&window.ConsoleModel)ConsoleModel.load(mdl,modelOpts(s.id)).then(ok=>{if(ok===false)toast(`Couldn't use your 3D console: ${ConsoleModel.error()||"unknown error"}`)});
  el.classList.toggle("has-model",useModel);
  const top=(m.drive==="top"||useModel)&&!drive;el.classList.toggle("topload",top);
  const slot=m.drive==="slot"&&!drive&&!useModel;el.classList.toggle("slotload",slot);
  // the other consoles' devices (front slot, tray, handheld hatch): original generic shapes tinted from the shelf
  const deck=!drive&&!useModel?DECK[m.drive]||null:null;Object.values(DECK).forEach(c=>el.classList.toggle(c,c===deck));
  $("insEmu").textContent=`Starting ${s.emulatorName}`;$("insTitle").textContent=g.title;
  el.classList.add("on");el.setAttribute("aria-hidden","false");
  if(top){
    // tint the cube from the shelf's colours and measure where the well lands on screen so the disc flies exactly into it
    const [t0,t1]=style.tint||["#3a2a86","#120d2c"];
    el.style.setProperty("--cube",mix(t0,t1,.35));el.style.setProperty("--cube-dark",mix(t0,"#000",.55));el.style.setProperty("--cube-light",mix(t0,"#fff",.18));
    const cd=$("cubeDisc");cd.style.backgroundImage=`url("${front}")`;
    const mr=el.querySelector(".ins-media").getBoundingClientRect();
    let tgt=null;
    if(useModel){const t=ConsoleModel.target();if(t)tgt={cx:t.x,cy:t.y,w:t.w,h:t.h}}
    else{const r=cd.getBoundingClientRect();if(r.width>0)tgt={cx:r.left+r.width/2,cy:r.top+r.height/2,w:r.width,h:r.height}}
    if(tgt&&mr.width>0){
      el.style.setProperty("--dx",(tgt.cx-(mr.left+mr.width/2))+"px");
      el.style.setProperty("--dy",(tgt.cy-(mr.top+mr.height/2))+"px");
      el.style.setProperty("--ds",(tgt.w/mr.width).toFixed(3));
      el.style.setProperty("--da",(Math.acos(Math.max(0,Math.min(1,tgt.h/tgt.w)))*180/Math.PI).toFixed(1)+"deg");
    }
  }
  if(deck){
    const [t0,t1]=style.tint||["#3a2a86","#120d2c"];
    el.style.setProperty("--deck",mix(t0,t1,.25));el.style.setProperty("--deck-dark",mix(t0,"#000",.45));el.style.setProperty("--deck-light",mix(t0,"#fff",.28));
    $("deckDisc").style.backgroundImage=$("trayDisc").style.backgroundImage=`url("${front}")`;
    const mr=el.querySelector(".ins-media").getBoundingClientRect();
    if(deck==="trayload")el.classList.add("deck-measure"); // the drawer measured where it will be once it's out
    const t=(deck==="frontslot"?$("deckSlot"):deck==="trayload"?$("trayDisc"):$("deckDisc")).getBoundingClientRect();
    el.classList.remove("deck-measure");
    if(t.width>0&&mr.width>0){
      el.style.setProperty("--dx",(t.left+t.width/2-(mr.left+mr.width/2))+"px");
      el.style.setProperty("--dy",(t.top+t.height/2-(mr.top+mr.height/2))+"px");
      el.style.setProperty("--ds",((deck==="frontslot"?t.width*.9:t.width)/mr.width).toFixed(3));
      if(deck!=="frontslot")el.style.setProperty("--da",(Math.acos(Math.max(0,Math.min(1,t.height/t.width)))*180/Math.PI).toFixed(1)+"deg");
    }
  }
  if(slot){
    const sr=$("dockSlot").getBoundingClientRect(),mr=el.querySelector(".ins-media").getBoundingClientRect();
    if(sr.width>0&&mr.width>0){
      const ds=(sr.width*.92)/mr.width;
      el.style.setProperty("--dx",(sr.left+sr.width/2-(mr.left+mr.width/2))+"px");
      el.style.setProperty("--dy",(sr.top+sr.height/2-(mr.top+mr.height/2))+"px");
      el.style.setProperty("--ds",ds.toFixed(3));
      el.style.setProperty("--sink",(mr.height*ds).toFixed(1)+"px");
    }
  }
  void el.offsetWidth;el.classList.add("go");
  if(useModel)ConsoleModel.play(front);
  // sound beats, timed to the animation
  // musical beats only: the Play phrase, the disc coming out, a soft seat when it lands, then the drive / open phrase (no lid clacks)
  lastGoAt=performance.now();play_("go");setTimeout(()=>play_("discOut"),640);
  if(deck==="frontslot"){setTimeout(()=>play_("seat"),2700);setTimeout(()=>{play_("open");rumble(.35,.25,70)},3020);setTimeout(()=>play_("whir"),3150)}
  else if(deck){setTimeout(()=>{play_("seat");rumble(.5,.35,90)},2580);setTimeout(()=>play_("open"),deck==="trayload"?3250:3180);setTimeout(()=>play_("whir"),3300)}
  else if(slot){setTimeout(()=>play_("seat"),2450);setTimeout(()=>play_("open"),3050)}
  else if(top){setTimeout(()=>{play_("seat");rumble(.55,.4,90)},2580);setTimeout(()=>rumble(.25,.2,50),3180);setTimeout(()=>play_("whir"),3240)}
  else{setTimeout(()=>play_("seat"),1900);setTimeout(()=>{play_("open");play_("whir")},2200)}
  clearTimeout(insertT);
  insertT=setTimeout(()=>{if(hideAfter)hideInsert();if(done)done()},reduced()?300:top||slot||deck?3700:2900);
  // when the media is seated: the emulator starts only then, so its start-up can't stall the animation
  insertSeatAt=performance.now()+(reduced()?250:top||deck?3650:slot?3600:2850); // after the whole insert: the emulator's start-up (heavy) never overlaps the animation
}
function mix(a,b,t){const h=x=>{x=x.replace("#","");if(x.length===3)x=x.split("").map(c=>c+c).join("");return [0,2,4].map(i=>parseInt(x.slice(i,i+2),16))};
  const A=h(a),B=h(b);return "#"+A.map((v,i)=>Math.round(v+(B[i]-v)*t).toString(16).padStart(2,"0")).join("")}
function hideInsert(){const el=$("insert");setTimeout(()=>{const i=$("bsBar")&&$("bsBar").querySelector("i");if(i&&!el.classList.contains("on")){i.style.transition="";i.style.width=""}},700);const ib=el.querySelector(".ins-bar");if(ib)ib.classList.remove("on");if(!el.classList.contains("on"))return;el.classList.add("out");setTimeout(()=>{if(el.classList.contains("go")&&!el.classList.contains("out"))return; /* shown again meanwhile */el.classList.remove("on","go","out","slide");const fx=$("bsFx");if(fx)fx.innerHTML=""; /* the slide's looping effects stop with it */el.setAttribute("aria-hidden","true");if(window.ConsoleModel)ConsoleModel.stop()},460)}
// Customize → Test: play the insert animation for this shelf without launching anything
function testModel(id){
  const sh=shelves.find(x=>x.id===id);const g=sh&&sh.games&&sh.games.find(x=>x.kind==="rom");
  if(!sh||!g){toast("Add a game to this shelf first, then test");return}
  const t=themes[id]||{};
  const go=()=>{closeCustomize();mode="launch";runInsert(sh,g,()=>{mode="browse"},true)};
  if(t.model&&window.ConsoleModel&&!ConsoleModel.ready(t.model)){toast("Loading your 3D model…");ConsoleModel.load(t.model,modelOpts(id)).then(ok=>{if(ok)go();else if(ok===false)toast(`Couldn't use your 3D console: ${ConsoleModel.error()||"unknown error"}`)})}
  else go();
}
function openEmulator(){const s=shelfFor(curGame());if(!s||s.kind!=="console"||!s.hasEmulator)return;closeDetails(true);launchNow({kind:"emulator",title:`${s.emulatorName} settings`,_sh:s})}
// The loading screen: the game's own art, softly blurred and washed in its console's colour, its cover floating in the
// middle, the console's wordmark, the title and a slim glowing progress line (the command line only shows on an error)
let launchCtx=null;
function showLaunch(label,title,cmd){
  $("lEmu").textContent=label;$("lTitle").textContent=title;$("lCmd").textContent=cmd||"";
  const el=$("launch"),ctx=launchCtx||{},g=ctx.g,s=ctx.s,st=(s&&STYLE[s.id])||STYLE.media,[t0,t1]=st.tint||["#2a2f6e","#0b0d1f"];
  el.classList.remove("ready","err","no-card");el.style.setProperty("--l-t0",t0);el.style.setProperty("--l-t1",t1);el.style.setProperty("--l-glow",st.glow||"#9d8cff");el.style.setProperty("--l-case",st.caseColor||"#16161b");
  const front=g?(g.kind==="rom"||g.kind==="steam"?frontUrl(g):(g.art&&(g.art.front||g.art.appIcon))):null;
  el.classList.toggle("no-card",!front);$("lCover").src=front?thumb(front,768):"";
  $("lBg").removeAttribute("src");tinyBlur(g&&((g.art&&(g.art.hero||g.art.front))||front),d=>{if(d)$("lBg").src=d});
  if(s&&s.kind==="console")renderWordmark($("lWord"),s.id,s.name,"l-word ");else{$("lWord").className="l-word";$("lWord").textContent=s&&s.kind==="steam"?(s.id==="pc"?"PC":s.name):s&&s.name||""}
  const bar=el.querySelector(".l-bar i");bar.style.animation="none";void bar.offsetWidth;bar.style.animation="";
  el.style.background="";app.classList.remove("returning");returnPrep=false;
  const n=$("lNote");n.textContent="";n.classList.remove("on");app.classList.add("launching");if(performance.now()-lastGoAt>6000){lastGoAt=performance.now();sfx.go()}
}
let lastGoAt=-1e9,soundOffT=0;
// fade the cues out smoothly (no click, no half-note cut) and then let the audio device go while the game runs
function soundOff(){clearTimeout(soundOffT);if(mode!=="launch"||!window.Sound)return;if(Sound.fadeOut)Sound.fadeOut(900);else if(Sound.suspend)Sound.suspend()}
// parts: plain strings (inserted as text, never HTML: they carry error messages from the emulator / OS), {b:"…"} for a bold key, {br:1}
function note(parts,err){const n=$("lNote");n.textContent="";(Array.isArray(parts)?parts:[parts]).forEach(p=>{if(p&&p.b!=null){const b=document.createElement("b");b.textContent=p.b;n.append(b)}else if(p&&p.br)n.append(document.createElement("br"));else n.append(String(p))});n.classList.toggle("l-err",!!err);n.classList.add("on");if(err)$("launch").classList.add("err")}
// the game's window is up: the loading screen fades to black just before Boxshelf steps aside
api.onRunReady&&api.onRunReady(()=>{if(mode==="launch"){$("launch").classList.add("ready");hideInsert()}});
function insProgress(sec){const el0=$("insert");if(el0.classList.contains("slide")||!el0.classList.contains("on"))return; /* the slide's line runs on its own fixed time */const el=$("insert");let b=el.querySelector(".ins-bar");if(!b){b=document.createElement("div");b.className="l-bar ins-bar";b.innerHTML="<i></i>";el.querySelector(".ins-text").appendChild(b)}const i=b.querySelector("i");i.style.animation="none";void i.offsetWidth;i.style.animation=`load ${Math.max(2,+sec||6)}s cubic-bezier(.3,0,.2,1) forwards`;b.classList.add("on")}
async function launchNow(g,underInsert){if(PV){pvStop();pvBackStop()} /* (beta.7: no preview over a starting game) */try{api.log&&api.log(`launch: ${g&&g.title} (last input: ${typeof inputMode!=="undefined"?inputMode:"?"}, screen: ${mode}${smoothTest.on?", during the smoothness test":""})`)}catch{}
  if(smoothTest.on)return; // nothing starts while the smoothness test runs
  // (Switch 2 started from somewhere other than Play, e.g. the museum: which build first)
  if(needsBuild(g,g._sh||shelfFor(g))&&!underInsert){const exe=await pickBuild(g);if(exe==null)return;nextBuild={id:g.id,exe}}
  const emu=nextBuild&&nextBuild.id===g.id?nextBuild.exe:"";nextBuild=null;
  const s=g._sh||shelfFor(g);launchCtx={g,s};mode="launch";runningKind=g.kind;ambient(false);rumble(.6,.3,140);clearTimeout(soundOffT);soundOffT=setTimeout(soundOff,underInsert?Math.max(2500,slideEndAt-performance.now()):2500); // the boot sound plays out with the slide, then fades as the line completes
  try{
    // (beta.4) PC games that aren't Steam's: Epic's are handed to its launcher (like Steam's: Back returns to the shelf);
    // Quiver's and the ones added by hand are started by Boxshelf (like an app: the Home menu, and Back waits for them)
    // (beta.7) Ubisoft Connect, the EA app and the Xbox app too (PC_EXT): handed over the same way
    if(g.kind==="steam"&&PC_EXT[g.source]){const L=PC_EXT[g.source];showLaunch("Starting through "+L,g.title,L);await wait(650);if(mode!=="launch"||!launchCtx||launchCtx.g!==g)return;await api.launchPc(g.id);if(api.historyTouch)api.historyTouch(g.id).catch(()=>{});
      clearTimeout(launchTimer);launchTimer=setTimeout(()=>note(["Running through the "+L+". Press ",{b:btnWord("back")}," or ",{b:"Esc"}," to return to the shelf."]),1800);return}
    if(g.kind==="steam"&&g.source&&g.source!=="steam"){runningKind="app";showLaunch(g.source==="quiver"?"Starting from Quiver":g.source==="gog"?"Starting from GOG":"Starting",g.title,g.title);await wait(650);if(mode!=="launch"||!launchCtx||launchCtx.g!==g)return;const r=await api.launchPc(g.id);sendHomeMeta(g);if(api.historyTouch)api.historyTouch(g.id).catch(()=>{});
      if(r&&r.cover){const bar=$("launch").querySelector(".l-bar i");bar.style.animation="none";void bar.offsetWidth;bar.style.animation=`load ${Math.min(r.cover,8)}s cubic-bezier(.5,0,.2,1) forwards`}}
    else if(g.kind==="steam"){showLaunch("Starting through Steam",g.title,`steam://rungameid/${g.appid}`);await wait(650);if(mode!=="launch"||!launchCtx||launchCtx.g!==g)return; /* backed out during the fade */await api.launchSteam(g.appid);if(api.historyTouch)api.historyTouch(g.id).catch(()=>{});
      clearTimeout(launchTimer);launchTimer=setTimeout(()=>note(["Running through Steam. Press ",{b:btnWord("back")}," or ",{b:"Esc"}," to return to the shelf."]),1800);return}
    else if(g.kind==="app"&&quickLaunch){quickLaunch=false;await api.launchApp(g.appId,g.link);sendHomeMeta(g)}
    else if(g.kind==="app"){showLaunch("Opening app",g.title,quote(g.path));await wait(650);const r=await api.launchApp(g.appId,g.link);sendHomeMeta(g);if(r&&r.cover){const bar=$("launch").querySelector(".l-bar i");bar.style.animation="none";void bar.offsetWidth;bar.style.animation=`load ${Math.min(r.cover,8)}s cubic-bezier(.5,0,.2,1) forwards`}}
    else if(g.kind==="emulator"){showLaunch(`Opening ${s.emulatorName}`,"Emulator settings",quote(s.emulator));await wait(650);await api.launchEmulator(s.id)}
    else{showLaunch(`Starting ${s.emulatorName}`,g.title,`${quote(emu||s.emulator)} ${argsPreview(s.gameArgs,g.file)}`);await wait(underInsert?Math.max(650,insertSeatAt-performance.now()):650);const r=await (emu?api.launchGame(s.id,g.file,emu):api.launchGame(s.id,g.file));sendHomeMeta(g);if(underInsert){clearTimeout(insertT);insProgress(r&&r.cover)} /* the insert screen stays, with a progress line, until the game's window is up */if(r&&r.cover){const bar=$("launch").querySelector(".l-bar i");bar.style.animation="none";void bar.offsetWidth;bar.style.animation=`load ${r.cover}s linear forwards`}}
    if(runningKind==="error")return;
    clearTimeout(launchTimer);launchTimer=setTimeout(()=>note(cfgInfo&&cfgInfo.config.ui.homeMenu===false?"Playing · close the game to come back here":`Playing · press ${padWord("guide")} for the Home menu`),1800);
  }catch(err){clearTimeout(insertT);clearTimeout(launchTimer);hideInsert();note([String(err&&err.message||err)," ",{br:1},"Press ",{b:btnWord("back")}," or ",{b:"Esc"}," to go back."],true);runningKind="error"}
}
// a step before the game starts (a 3DS .cia being installed into Azahar) shows on the boot slide and the launch screen
if(api.onLaunchStatus)api.onLaunchStatus(t=>{if(mode!=="launch")return;const b=$("bsEmu"),l=$("lEmu");if(b)b.textContent=t;if(l)l.textContent=t});
function endLaunch(){pollResume();clearTimeout(launchTimer);clearTimeout(insertT);hideInsert();app.classList.remove("launching");mode="browse";runningKind=null;ambient(true);loadHistory();musLaunchBack()}
// a game (or Stremio) started from the museum comes back to the museum when it closes, on the shelf you were on
let musLaunch=null;
function musLaunchFrom(){const c=window.Museum&&Museum.current&&Museum.current();musLaunch={row:c&&c.row&&c.row.id||(cur()&&cur().id)||null,at:performance.now()}}
function musLaunchBack(){const m=musLaunch;musLaunch=null;if(!m||mode!=="browse")return;setTimeout(()=>{if(mode==="browse"&&!sysBusy())openMuseum(m.row||undefined)},0)}
// library updates that arrive while you're playing / in the museum / Inspect wait here and are applied back on the shelf
// (beta.10) libLater: a library change that came in mid-scroll ({msgs, rk, n0, watch}); its notes are said once its refresh is applied
let pendingLib=null,pendingRefresh=false,libLater=null;
setInterval(()=>{if((pendingRefresh||pendingLib||libLater)&&mode==="browse"&&performance.now()-returnAt>2500&&!sysBusy()&&!(window.BS_BUSY&&BS_BUSY())&&performance.now()-lastInputAt>800){const lib=pendingLib,L=libLater,need=pendingRefresh||!!lib;pendingLib=null;pendingRefresh=false;libLater=null;const r=need?refresh(true,lib||undefined):null;if(L){L.msgs.forEach(m=>toast(m));if(L.rk)toastDone(L.rk);Promise.resolve(r).then(()=>libAfter(L.n0,L.watch))}}},700);
// Back to the shelf: the shelf is put in place *under* a plain black cover (no zoom, no fade of the heavy scene), its
// covers are decoded, and only then the black cover fades away — one light layer fading, so no stutter or half-drawn frame.
// Quitting from the Home menu / quit combo starts this while the emulator is still closing (run:quitting).
let returnPrep=false,returnAt=-1e9,returnUndo=0;
function prepReturn(){
  if(mode!=="launch"||runningKind==="error"||returnPrep)return;
  returnPrep=true;const el=$("launch");el.style.background="#000";el.classList.add("ready"); // only black shows from here on
  app.classList.add("returning");app.classList.remove("launching");clearTimeout(insertT);hideInsert();
  clearTimeout(returnUndo);returnUndo=setTimeout(()=>{if(mode==="launch"&&returnPrep){returnPrep=false;app.classList.add("launching");app.classList.remove("returning")}},13000); // the game didn't close after all
}
function shelfImagesReady(ms){
  const vw=innerWidth,vh=innerHeight,imgs=[...document.querySelectorAll("#scene img")].filter(i=>{if(!i.src)return false;const r=i.getBoundingClientRect();return r.width>0&&r.right>-vw*.25&&r.left<vw*1.25&&r.bottom>0&&r.top<vh}).slice(0,40);
  return Promise.race([Promise.all(imgs.map(i=>i.decode().catch(()=>{}))),wait(ms)]);
}
const frames=n=>new Promise(r=>{const f=()=>--n<=0?r():requestAnimationFrame(f);requestAnimationFrame(f)});
api.onRunQuitting&&api.onRunQuitting(()=>{document.body.classList.remove("aside");pollResume();prepReturn()});
// while a game runs the (transparent) window stays up behind it: draw nothing but the black loading layer
api.onRunAside&&api.onRunAside(on=>{document.body.classList.toggle("aside",on);if(!on)pollResume()});
// (1.76) never left on a black screen after a game: the cover over the return waited for animation frames, which
// Chromium doesn't give a window it still thinks is hidden behind the game. The wait is now capped, a watchdog clears a
// cover still up 3 s after the game ended, and an exit outside "launch" mode still takes the black layers away
let returnWatch=0;
function clearReturnCover(why){const was=["launching","returning"].filter(c=>app.classList.contains(c));document.body.classList.remove("aside");app.classList.remove("returning","launching");returnPrep=false;$("launch").style.background="";if(was.length&&api.log)api.log(`return: cleared the black cover (${why}; was ${was.join("+")}, mode ${mode}, page ${document.visibilityState})`)}
api.onExited(async info=>{uiCrumb("Back from the game");if(window.Sound&&Sound.nextSong)Sound.nextSong("game");
  try{dimWake();dimAt=lastInputAt=performance.now()}catch{} // a game that ran a long while left the shelf idle: the screen dimmer (black until a touch) must not be on when it comes back
  clearTimeout(returnWatch);returnWatch=setTimeout(()=>{if(mode!=="launch"&&(app.classList.contains("returning")||app.classList.contains("launching")||document.body.classList.contains("aside")))clearReturnCover("watchdog, 3 s after the game ended")},3000);
  if(mode!=="launch"){if(!(info&&info.code===-1))clearReturnCover("game ended outside launch mode");return}
  if(info&&info.code===-1){clearTimeout(insertT);clearTimeout(launchTimer);hideInsert();note([`Couldn't start: ${info.error||"unknown error"}. `,{br:1},"Press ",{b:btnWord("back")}," or ",{b:"Esc"}," to go back."],true);runningKind="error";return}
  // (1.82) the emulator closed by itself within seconds: say which, instead of quietly returning to the shelf
  if(info&&info.quick){const e=info.emulatorName||"The emulator";clearTimeout(insertT);clearTimeout(launchTimer);hideInsert();
    note([`${e} closed right away${info.code?` (code ${info.code})`:""}. `,{br:1},`Open ${e} on its own to check its setup: a missing BIOS, keys or firmware is the usual cause. `,{br:1},"Press ",{b:btnWord("back")}," or ",{b:"Esc"}," to go back."],true);runningKind="error";return}
  document.body.classList.remove("aside");clearTimeout(returnUndo);prepReturn();endLaunch();returnAt=performance.now();
  // the window has just been shown again: let it paint the shelf a few times behind the cover, then reveal it
  await shelfImagesReady(700);await Promise.race([frames(3),wait(300)]);await wait(120);
  if(mode==="launch"){returnPrep=false;return} // (another game started during this return: its own launch owns the screen now)
  app.classList.remove("returning");returnPrep=false;sfx.back();
  setTimeout(()=>{if(!app.classList.contains("launching"))$("launch").style.background=""},700);
});
function back(){
  if(mode==="sort"){closeSort();return}
  if(mode==="build"){bpDone("");return}
  if(mode==="cover"){cpDone();sfx.back();return}
  if(mode==="achv"){closeAchievements();return}
  if(mode==="boot"){endBoot(true);return}
  if(mode==="customize"){if(!czClosePick())closeCustomize();return}
  if(mode==="launch"){if(runningKind==="steam"||runningKind==="error"){endLaunch();sfx.back()}else toast("Close the game or app to come back");return}
  if(mode==="details"){if(!$("dMenu").hidden){dMenuClose();return}closeDetails();return} // (beta.7: B closes the More menu first)
  if(flipped)flip();else if((mode==="browse"||mode==="details")&&(Math.abs(spin)>1||Math.abs(spinT)>1)){spinT=0;spinEase();sfx.flip()} // resting on a spine: B turns it to the front too
}
function primary(){if(mode==="boot"){endBoot();return}if(mode==="browse"||mode==="details")play()}

/* ---------- boot animation ---------- */
let bootDone=null,bootT,bootRun=0; // bootRun: tags each boot so callbacks from a cancelled one (pending play(), muted retry, error) do nothing
function chime(){play_("boot")}
// Android (0.3.5): the built-in boot screen holds its logo a little longer (Settings → Boot screen length); the PC keeps 3.1 s
function bootHold(){if(!ANDROID)return 0;const v=cfgInfo&&cfgInfo.config&&cfgInfo.config.ui&&cfgInfo.config.ui.bootLen;return v==="short"?0:v==="long"?2900:1500}
function runBoot(s,done){
  mode="boot";bootDone=done;clearTimeout(bootT);pollResume();const run=++bootRun,live=()=>run===bootRun&&mode==="boot";const b=$("boot"),v=$("bootVideo"),t=themes[s.id]||{},style=STYLE[s.id]||STYLE.media;
  b.style.setProperty("--boot-a",style.tint[0]);b.style.setProperty("--boot-c",style.glow);
  renderWordmark($("bootWord"),s.id,s.name,"");$("bootWord").hidden=!!t.logo;$("bootLogo").hidden=!t.logo;if(t.logo)cleanLogo(t.logo).then(r=>{$("bootLogo").src=r.url;$("bootLogo").classList.toggle("dark-logo",!!r.dark)});
  b.classList.remove("intro");b.classList.add("on");b.setAttribute("aria-hidden","false");
  const intro=()=>{if(!live())return;v.hidden=true;$("bootIntro").hidden=false;void b.offsetWidth;b.classList.add("intro");chime();clearTimeout(bootT);bootT=setTimeout(()=>endBoot(),3100+bootHold())};
  if(t.boot){clearTimeout(bootT);bootT=setTimeout(()=>{if(live())endBoot()},12000); /* a video that never ends (or never starts) must not keep the screen black */
    $("bootIntro").hidden=true;v.hidden=false;v.src=t.boot;v.muted=!soundOn;applySinkTo(v);v.onended=()=>{if(live())endBoot()};v.onerror=intro;
    const stale=()=>{if(!live())try{v.pause()}catch(e){}}; // a play() that resolves after the boot was skipped must not keep playing hidden
    const p=v.play();if(p&&p.then)p.then(stale,()=>{if(!live())return;v.muted=true;const q=v.play();if(q&&q.then)q.then(stale,intro)})}
  else intro();
}
function endBoot(cancel){try{if(window.PulseHost&&PulseHost.introDone)PulseHost.introDone()}catch(e){}if(mode!=="boot")return;bootRun++;clearTimeout(bootT);const b=$("boot"),v=$("bootVideo");b.classList.remove("on","intro");b.setAttribute("aria-hidden","true");v.onended=v.onerror=null;try{v.pause();v.removeAttribute("src");v.load()}catch(e){} /* drop the video: nothing left to decode or play */const d=bootDone;bootDone=null;mode=(!cancel&&d)?"launch":"browse";if(cancel)musLaunch=null;if(!cancel&&d)setTimeout(d,200)}

/* ---------- customize (logos + boot videos on disk) ---------- */
const THEME_IDS=[["gc","GameCube",true],["wii","Wii",true],["wiiu","Wii U",true],["sw","Switch",true],...(ANDROID?[]:[["sw2","Switch 2",true]]),["psp","PSP",true],["ps2","PS2",true],["ps3","PS3",true],["ps4","PS4",true],["ps5","PS5",true],["x360","Xbox 360",true],["n3ds","3DS",true],["pc","PC",false],["media","Media",false]];
async function reloadThemes(){themes=await api.themes().catch(()=>({}))||{};applyLogo();preloadModel();if(mode==="customize")renderCustomize();if(Object.values(themes).some(t=>t&&t.heavy&&t.heavy.length))optimizeModels()} /* a newly added heavy model gets its light copy without a restart */
// Program icons (apps, "Open <emulator>") become covers once the icon image has loaded
// Covers for emulators and apps made from their icons: drawn one at a time in quiet moments once the shelf is on screen
// (the shelf in view first), and encoded off the screen's thread. All at once at startup they stalled the shelf as it appeared.
let iconQ=[],iconRun=false;
function iconCovers(){
  const seen=new Set(iconQ.map(x=>x.g));const order=[cur(),...shelves.filter(s=>s!==cur())].filter(Boolean);
  order.forEach(sh=>sh.items.forEach(g=>{if(!g.art||!g.art.appIcon||g.art.front||seen.has(g))return;seen.add(g);iconQ.push({g,style:STYLE[sh.id]||STYLE.media})}));
  if(!iconRun&&iconQ.length){iconRun=true;iconNext()}
}
function iconNext(){
  const go=()=>{if(!revealedAt||performance.now()-revealedAt<2000||mode==="launch"){setTimeout(iconNext,500);return} /* not while the shelf is still coming in */
    const j=iconQ.shift();if(!j){iconRun=false;return}
    if(j.g.art.front){iconNext();return}
    Covers.iconFront(j.style,j.g,j.g.art.appIcon).then(url=>{if(url){j.g.art.front=url;j.g.art.gen=1;const i=cur()&&cur().items.indexOf(j.g);if(i>=0&&slots[i]&&slots[i].img)slots[i].img.src=url}}).finally(()=>setTimeout(iconNext,60))};
  window.requestIdleCallback?requestIdleCallback(go,{timeout:1500}):setTimeout(go,50)}
/* ---------- RetroAchievements ---------- */
const RA={on:false,systems:[],shelf:{},game:new Map(),profile:null};
async function raInit(){
  try{const e=await api.raEnabled();RA.on=!!(e&&e.enabled);RA.systems=e&&e.systems||[]}catch{RA.on=false}
  const pts=$("raPts");if(!RA.on){pts.hidden=true;return}
  try{RA.profile=await api.raProfile();if(RA.profile){pts.innerHTML="";if(RA.profile.pic){const im=new Image();im.src=RA.profile.pic;im.alt="";pts.append(im)}const b=document.createElement("b");b.textContent=RA.profile.points.toLocaleString()+" pts";pts.append(b,document.createTextNode(" · "+RA.profile.user));pts.hidden=false}}catch{}
  raShelfLoad();
}
function raHas(s){return RA.on&&s&&RA.systems.includes(s.id)}
async function raShelfLoad(){
  const busy=raShelfLoad.busy||(raShelfLoad.busy=new Set()),s=cur();if(!raHas(s)||RA.shelf[s.id]||busy.has(s.id))return; // busy: the quick menu, a shelf switch and refresh() can all ask at once; one request is enough
  busy.add(s.id);try{RA.shelf[s.id]=await api.raShelf(s.id,s.games.filter(g=>g.kind==="rom").map(g=>g.title))||{};if(cur()===s)updateInfo()}catch(e){console.warn("RA shelf",e)}finally{busy.delete(s.id)}
}
function raFor(g){const s=shelfFor(g);if(!raHas(s)||!g||g.kind!=="rom")return null;const m=RA.shelf[s.id];return m?m[g.title]||null:null}
// The row is there for every game on a shelf RetroAchievements covers: loading, the error (with Try again), no set, or progress
function raRow(state,title,sub,btn){const box=$("dAch");box.hidden=false;box.dataset.state=state;$("dAchTitle").textContent=title;$("dAchSub").textContent=sub;
  const b=$("dAchBtn");b.hidden=!btn;if(btn)b.textContent=btn;if(state!=="ok"){$("dRing").style.setProperty("--p",0);$("dRingTxt").textContent=state==="load"?"…":state==="err"?"!":"–"}if(mode==="details")renderHints()}
async function raDetails(g,force){
  const box=$("dAch");box.hidden=true;const s=shelfFor(g);if(!raHas(s)||!g||g.kind!=="rom")return;
  const key=s.id+"|"+g.title;let p=force?undefined:RA.game.get(key);
  if(p===undefined){raRow("load","Achievements","Loading from RetroAchievements…");
    try{p=await api.raProgress(s.id,g.title,!!force);RA.game.set(key,p||false)}catch(e){console.warn("RA",e);if(curGame()===g)raRow("err","Achievements didn't load",e&&e.message||"RetroAchievements didn't answer.","Try again");return}}
  if(curGame()!==g)return;
  if(!p){raRow("none","Achievements","RetroAchievements has no set for this game (or its name didn't match one).");return}
  box.dataset.state="ok";$("dAchBtn").hidden=false;$("dAchBtn").textContent="See all";
  const pct=p.total?Math.round(100*p.earned/p.total):0;
  $("dRing").style.setProperty("--p",pct);$("dRingTxt").textContent=pct+"%";
  $("dAchTitle").textContent=p.mastered==="mastered"?"Mastered":p.mastered==="completed"?"Completed":"Achievements";
  $("dAchSub").textContent=`${p.earned} of ${p.total} unlocked · ${p.pointsEarned} / ${p.points} points`;
  box.hidden=false;if(mode==="details")renderHints();
}
// what the row's button / Y does in each state: retry after an error, the full list once it's in; nothing while it loads
// or when there's no set (Y used to start a second download then, or fetch the "no set" answer again)
const achReady=()=>!$("dAch").hidden&&/^(ok|err)$/.test($("dAch").dataset.state||"");
function achAct(){if(!achReady())return false;if($("dAch").dataset.state==="err"){const g=curGame();if(g)raDetails(g,true)}else openAchievements();return true}
const ach={open:false,items:[],all:[],sel:0,filter:"all",p:null};
const ACH_TABS=[["all","All"],["got","Unlocked"],["locked","Locked"]];
async function openAchievements(){
  const g=curGame(),s=shelfFor(g);if(!raHas(s)||!g||g.kind!=="rom"||ach.open||ach.busy)return;
  let p=RA.game.get(s.id+"|"+g.title);if(p===undefined){toast("Loading achievements…");ach.busy=true;try{p=await api.raProgress(s.id,g.title,true);RA.game.set(s.id+"|"+g.title,p||false)}catch(e){toast(e&&e.message||"RetroAchievements didn't answer");if(mode==="details"&&curGame()===g)raRow("err","Achievements didn't load",e&&e.message||"RetroAchievements didn't answer.","Try again");return}finally{ach.busy=false}
    if(mode==="details"&&curGame()===g)raDetails(g)} // the Details row catches up with what just came in
  if(!p){toast("No achievement set for this game");return}
  if(curGame()!==g||(mode!=="browse"&&mode!=="details")||qm.open||sysBusy())return;
  ach.open=true;ach.p=p;ach.all=p.achievements;ach.filter="all";
  const ic=$("achIcon");if(p.icon){ic.src=p.icon;ic.hidden=false}else ic.hidden=true;
  $("achTitle").textContent=p.title;$("achBar").style.width=(p.total?100*p.earned/p.total:0)+"%";
  $("achSub").textContent=`${p.earned} of ${p.total} unlocked · ${p.total-p.earned} still locked · ${p.pointsEarned} / ${p.points} points${p.mastered?" · "+p.mastered:""}`;
  achList(true);
  const el=$("achv");el.classList.add("on");el.setAttribute("aria-hidden","false");mode="achv";achHints();sfx.flip();achSelect();
}
// the list's prompts: buttons, like the shelf's, so a mouse can switch tabs and close it (it had no way out with a mouse);
// redrawn when you switch between controller and keyboard (renderHints)
function achHints(){const pad=showPad(),h=$("achHints");h.innerHTML="";
  [[pad?["L1","R1"]:["Q","E"],"All · Unlocked · Locked",()=>achTab(ach.filter==="locked"?-2:1)],[pad?["dpad"]:["↑","↓"],"Browse",null],[pad?["o"]:["Esc"],"Close",()=>closeAchievements()]].forEach(([keys,label,act])=>{const b=document.createElement("button");b.type="button";b.className="hint";b.innerHTML=keys.map(k=>pad?glyph(k):`<span class="g">${k}</span>`).join("");b.append(document.createTextNode(label));if(act)b.addEventListener("click",act);else b.tabIndex=-1;h.append(b)})}
// the list for the chosen tab; opening lands on the first locked one (the next to go for)
function achList(first){
  const all=ach.all,f=ach.filter;ach.items=f==="got"?all.filter(a=>a.earned):f==="locked"?all.filter(a=>!a.earned):all;
  ach.sel=first?Math.max(0,ach.items.findIndex(a=>!a.earned)):0;if(ach.sel<0)ach.sel=0;
  const tabs=$("achTabs");tabs.innerHTML="";ACH_TABS.forEach(([id,label])=>{const n=id==="got"?all.filter(a=>a.earned).length:id==="locked"?all.filter(a=>!a.earned).length:all.length;
    const b=document.createElement("button");b.type="button";b.className="ach-tab"+(id===f?" on":"");b.textContent=`${label} ${n}`;b.addEventListener("click",()=>{if(ach.filter!==id){ach.filter=id;achList();achSelect();play_("tick")}});tabs.append(b)});
  const list=$("achList");list.innerHTML="";list.scrollTop=0;
  if(!ach.items.length){const e=document.createElement("div");e.className="ach-empty";e.textContent=f==="got"?"Nothing unlocked yet. Every achievement is under Locked.":"All unlocked. Nothing left to earn here.";list.append(e);return}
  ach.items.forEach((a,i)=>{const row=document.createElement("div");row.className="ach"+(a.earned?"":" locked")+(i===ach.sel?" sel":"");row.dataset.i=i;
    const im=new Image();im.src=a.badge;im.alt="";im.loading="lazy";
    const main=document.createElement("div");main.className="ach-main";const b=document.createElement("b");b.textContent=a.title;const d=document.createElement("span");d.textContent=a.desc;
    const meta=document.createElement("div");meta.className="ach-meta";meta.textContent=(a.earned?"Unlocked "+(a.date?a.date.slice(0,10):"")+(a.hardcore?" · hardcore":""):"Locked")+(a.rarity!=null?` · ${a.rarity}% of players`:"");
    main.append(b,d,meta);const pts=document.createElement("div");pts.className="ach-pts";pts.textContent=a.points+" pts";row.append(im,main,pts);row.addEventListener("click",()=>{ach.sel=i;achSelect()});list.append(row)});
}
function achTab(d){const i=ACH_TABS.findIndex(t=>t[0]===ach.filter);const n=Math.max(0,Math.min(ACH_TABS.length-1,i+d));if(n===i)return;ach.filter=ACH_TABS[n][0];achList();achSelect();play_("tick")}
function achSelect(){const rows=$("achList").children;[...rows].forEach((r,i)=>r.classList.toggle("sel",i===ach.sel));const r=rows[ach.sel];if(r)r.scrollIntoView({block:"nearest"})}
function achMove(d){if(!ach.items.length)return;const cols=Math.max(1,(getComputedStyle($("achList")).gridTemplateColumns||"").split(" ").filter(Boolean).length); /* the grid's own column count (auto-fill, 300px min + gap): width/310 rounded up a column at 1280 wide, so ↓ went diagonally */ach.sel=Math.max(0,Math.min(ach.items.length-1,ach.sel+d*cols));achSelect();play_("tick")}
function achSide(d){if(!ach.items.length)return;ach.sel=Math.max(0,Math.min(ach.items.length-1,ach.sel+d));achSelect();play_("tick")}
function closeAchievements(){if(!ach.open)return;ach.open=false;const el=$("achv");el.classList.remove("on");el.setAttribute("aria-hidden","true");mode=$("details").getAttribute("aria-hidden")==="false"?"details":"browse";renderHints();sfx.back()} // the bar may have been redrawn for "achv" meanwhile (a switch of input)
function showUnlocks(list){
  const box=$("unlocks");list.slice(0,6).forEach((a,i)=>{setTimeout(()=>{const u=document.createElement("div");u.className="unlock";const im=new Image();im.src=a.badge;im.alt="";const m=document.createElement("div");
    const k=document.createElement("div");k.className="k";k.textContent="Achievement unlocked · "+a.points+" pts";const b=document.createElement("b");b.textContent=a.title;const sp=document.createElement("span");sp.textContent=a.desc;m.append(k,b,sp);u.append(im,m);box.append(u);play_("unlock");rumble(.4,.6,220);setTimeout(()=>u.remove(),6800)},i*900)});
  // refresh cached progress for the game(s) involved
  RA.game.clear();RA.shelf={};raShelfLoad(); // all shelves: the game may not be on cur() (a Surprise pick from another shelf)
}
api.onRaUnlocks&&api.onRaUnlocks(showUnlocks);
api.onModelInstalled&&api.onModelInstalled(async r=>{toast(`Installed ${r.file} as the ${r.name} console model`);await reloadThemes();if(mode==="browse"&&INSERT_3D)testModel(r.sysId)}); // (Test shows the start-up animation, which is off: the museum shows the model)
// a note about new manuals waits for the shelf if it arrives during a game or a panel (it used to be dropped)
let pendingNote="";
function manualNote(list){if(!list||!list.length)return"";const up=list.filter(x=>x.upgraded).length,names=list.slice(0,2).map(x=>x.title).join(", ")+(list.length>2?` and ${list.length-2} more`:"");
  return up===list.length?`Swapped in sharper or English manuals for ${list.length} game${list.length===1?"":"s"}: ${names}`:up?`New and improved manuals for ${list.length} games: ${names}`:`Added manuals for ${list.length} game${list.length===1?"":"s"}: ${names}`}
setInterval(()=>{if(pendingNote&&mode==="browse"&&!sysBusy()){toast(pendingNote);pendingNote=""}},1500);
// (1.82) a newer beta on the public page (main asks once per start): said once per version, when the shelf is idle
api.onUpdate&&api.onUpdate(r=>noteUpdate(r)); // (beta.7: a card with the release's highlights, not a one-line note)
api.onLibraryChanged&&api.onLibraryChanged(info=>{const note=info&&manualNote(info.manualUpdates);
  // (beta.9) the background manual re-check set some aside and looked them up again: said plainly, once it's all done
  const rk=info&&info.rechecked&&info.rechecked.moved?`✓ Manuals checked: ${info.rechecked.moved} set aside and looked up again`:"";
  if((mode!=="browse"&&mode!=="details")||sysBusy()){pendingRefresh=true;if(rk||note)pendingNote=rk||note;return}
  const artSaid=info&&info.covers&&ARTC.said===info.covers&&performance.now()-(ARTC.saidAt||0)<20000;if(artSaid)ARTC.said=0; // (beta.10) the art card's "Box art: N added" said it already
  const msgs=[note,info&&info.covers&&!artSaid?`Added covers for ${info.covers} new game${info.covers===1?"":"s"}`:"",info&&info.manuals?`Added the built-in manual for ${info.manuals} Wii U game${info.manuals===1?"":"s"}`:"",info&&info.onlineManuals?`Added manuals for ${info.onlineManuals} game${info.onlineManuals===1?"":"s"}`:""].filter(Boolean);
  // (beta.10) mid-slide or mid-scroll: no rebuild under your thumb; the 700 ms check above applies it (and says these) once you rest
  if((window.BS_BUSY&&BS_BUSY())||performance.now()-lastInputAt<800){pendingRefresh=true;const L=libLater||(libLater={msgs:[],rk:"",n0:libGames(),watch:false});L.msgs.push(...msgs);if(rk)L.rk=rk;if(info&&info.watch)L.watch=true;return}
  msgs.forEach(m=>toast(m));if(rk)toastDone(rk);
  const n0=libGames();Promise.resolve(refresh(true)).then(()=>libAfter(n0,info&&info.watch))});
function libGames(){return allShelves.reduce((a,sh)=>a+((sh.games||[]).filter(g=>g.kind==="rom").length),0)}
function libAfter(n0,watch){if(qm.open)qsSoft();const n=libGames()-n0;if(watch&&n>0)toast(`${n} new game${n===1?"":"s"} found`+(musStyle().mail!==false?(n===1?" · it's":" · they're")+" waiting for you in the museum mail":""))}
// console.json options with per-system defaults: front-slot consoles load the disc upright through the front; GameCube is top-loading
const FRONT_SLOT=new Set(["wii","wiiu","ps2","ps3","ps4","ps5","x360"]);
// 3D models that come with Boxshelf: the PC shelf's gaming PC (an original model, made for Boxshelf), turned so its glass side shows
const BUILTIN_MODEL={pc:"models/pc.glb"},BUILTIN_OPTS={pc:{museumYaw:30}};
function modelOpts(id){const t=themes[id]||{};return Object.assign({slot:FRONT_SLOT.has(id)?"front":"top"},!t.model&&BUILTIN_OPTS[id]||{},t.modelOpts||{})}
// The shelf's 3D console (for the insert animation when a game starts) sets up its own 3D view and reads the model:
// a few hundred ms of work that used to land right as the shelf appeared (and on every change of shelf). It now waits
// for a quiet moment once the shelf is up; starting a game before then still loads it first (see play).
let pmT=0;
function preloadModel(){if(!INSERT_3D)return;clearTimeout(pmT);pmT=setTimeout(function tryPM(){
  const s=cur(),t=s&&themes[s.id];if(!(t&&t.model&&window.ConsoleModel)||ConsoleModel.ready(t.model))return;
  if(!revealedAt||performance.now()-revealedAt<2500||performance.now()-lastInputAt<1200||(mode!=="browse"&&mode!=="details")||document.hidden){pmT=setTimeout(tryPM,700);return}
  const go=()=>{if(cur()!==s){preloadModel();return}bootMark("model");ConsoleModel.load(t.model,modelOpts(s.id)).then(ok=>{bootMark("model-done");if(ok===false)toast(`Couldn't use your 3D console: ${ConsoleModel.error()||"unknown error"}`)})};
  window.requestIdleCallback?requestIdleCallback(go,{timeout:2000}):go()},400)}
// Customize: one system at a time. Tabs along the top (bumpers / Q E switch), then a row per thing you can change, each with its
// current state and its buttons; the D-pad walks the buttons, A presses, B closes.
let custSel=null,czPick=null; // czPick: the open "Find a logo" picker { id, state: load | err | ok, list, msg, code, busy }
// the picker, drawn under the Logo row: logo cards side by side (dark backing, as the shelf shows them), clearest wordmarks first,
// each with its source and licence; a small line under them when ScreenScraper could add more
const CZ_SRC={commons:"Commons"};
function czPickHtml(name){const p=czPick,B=(a,l,pr)=>`<button class="chip${pr?" primary":""}" type="button" data-a="${a}">${l}</button>`;
  if(p.state==="load")return `<div class="cz-pick"><div class="cz-pnote">Looking for ${qsEsc(name)} logos…</div></div>`;
  if(p.state==="err"||!p.list.length)return `<div class="cz-pick"><div class="cz-pnote">${qsEsc(p.state==="err"?p.msg:p.offline?"Couldn't reach Wikimedia Commons. Check the internet connection and try again.":`No logos found for ${name} right now.`)}</div><div class="cz-btns">${B("findlogo","Try again",1)}${B("closepick","Close")}</div></div>`;
  return `<div class="cz-pick"><div class="cz-pcards">${p.list.map(c=>`<button class="cz-lcard" type="button" data-a="uselogo" data-k="${qsEsc(c.key)}"${p.busy?" disabled":""}><span class="cz-lim${c.dark?" dark":""}"><img alt="" src="${qsEsc(c.preview)}"></span><span class="cz-lname">${qsEsc(p.busy===c.key?"Saving…":c.label)}</span><span class="cz-lsrc">${qsEsc([CZ_SRC[c.source]||c.source,c.licence,c.by&&c.by!==c.source?"by "+c.by:""].filter(Boolean).join(" · "))}</span></button>`).join("")}</div><div class="cz-pnote">Official logos from Wikimedia Commons, free to use.</div><div class="cz-btns">${B("closepick","Close")}</div></div>`}
// a preview that won't draw: its card goes (error events don't bubble, so this listens on the way down)
document.addEventListener("error",e=>{const t=e.target;if(t&&t.matches&&t.matches(".cz-lim img")){const c=t.closest(".cz-lcard");if(c)c.hidden=true}},true);
// a mostly dark logo (black wordmark) gets a light glow on the preview, as it does on the shelf
function czDark(src){return new Promise(ok=>{const im=new Image();im.onload=()=>{try{const W=48,H=Math.max(1,Math.round(48*im.naturalHeight/Math.max(1,im.naturalWidth))),c=document.createElement("canvas");c.width=W;c.height=H;const x=c.getContext("2d",{willReadFrequently:true});x.drawImage(im,0,0,W,H);const d=x.getImageData(0,0,W,H).data;let n=0,l=0;for(let i=0;i<d.length;i+=4){if(d[i+3]<128)continue;n++;l+=.2126*d[i]+.7152*d[i+1]+.0722*d[i+2]}ok(n>0&&l/n<70)}catch{ok(false)}};im.onerror=()=>ok(null);im.src=src})}
// focus after the picker changes: its first card, its first button, or Find a logo once it's closed
function czPickFocus(sel){if(mode!=="customize")return;const f=$("setRows").querySelector(sel||".cz-lcard:not([disabled]):not([hidden]),.cz-pick .chip")||$("setRows").querySelector('[data-a="findlogo"]');if(f){padSet($("customize"),f);revealIn(f)}}
async function czFindLogo(id,name){if(!api.findLogos)return;const p=czPick={id,state:"load",list:[]};renderCustomize();czPickFocus('[data-a="findlogo"]');
  let r;try{r=await api.findLogos(id)}catch(e){r={ok:false,error:e.message}}
  if(czPick!==p||mode!=="customize"||custSel!==id)return; /* closed, or another console's tab, while it was looking */
  if(r&&r.ok){let L=r.list||[];await Promise.all(L.map(async c=>{c.dark=await czDark(c.preview)}));L=L.filter(c=>c.dark!==null);if(czPick!==p||mode!=="customize"||custSel!==id)return;p.state="ok";p.list=L;p.offline=!!r.offline}else{p.state="err";p.msg=(r&&r.error)||"Couldn't look for logos"}
  play_(p.list.length?"open":"error");renderCustomize();czPickFocus()}
async function czUseLogo(id,name,key){const p=czPick;if(!p||p.busy||p.id!==id)return;p.busy=key;renderCustomize();czPickFocus(`.cz-lcard[data-k="${CSS.escape(key)}"]`);
  let r;try{r=await api.useLogo(id,key)}catch(e){r={ok:false,error:e.message}}
  if(czPick!==p)return;p.busy=null;
  if(r&&r.ok){czPick=null;play_("open");toast(`New logo for ${name}. Restore previous brings the old one back`);await reloadThemes();czPickFocus('[data-a="restorelogo"]')}
  else{play_("error");toast((r&&r.error)||"Couldn't use that logo");renderCustomize();czPickFocus(`.cz-lcard[data-k="${CSS.escape(key)}"]`)}}
function czClosePick(){if(!czPick)return false;czPick=null;play_("tick",{dir:-1});renderCustomize();czPickFocus('[data-a="findlogo"]');return true}
function renderCustomize(){
  const box=$("setRows");box.innerHTML="";
  if(!custSel||!THEME_IDS.some(x=>x[0]===custSel))custSel=(cur()&&THEME_IDS.some(x=>x[0]===cur().id))?cur().id:THEME_IDS[0][0];
  const tabs=document.createElement("div");tabs.className="cz-tabs";
  THEME_IDS.forEach(([tid,tname])=>{const b=document.createElement("button");b.type="button";b.className="cz-tab"+(tid===custSel?" on":"");b.textContent=tname;b.onclick=()=>{if(custSel===tid)return;custSel=tid;czPick=null;play_("tick",{dir:1});renderCustomize();const f=$("setRows").querySelector(".cz-tab.on");if(f)padSet($("customize"),f)};tabs.appendChild(b)});
  box.appendChild(tabs);
  THEME_IDS.filter(x=>x[0]===custSel).forEach(([id,name,isConsole])=>{
    const t=themes[id]||{};const sc=cfgInfo&&cfgInfo.config.systems[id];
    const row=document.createElement("div");row.className="set-row cz";
    // (beta.10) each button only where its API exists: the Thor has no setTheme / findModel / removeTheme / openThemeFolder,
    // and its api.pick of a file opens the emulator picker, so Add image / model / video stay hidden there; a row with nothing left is dropped
    const can=k=>typeof api[k]==="function",FILE=!ANDROID&&can("setTheme")&&can("pick"),
      CAN={logo:FILE,boot:FILE,console:FILE,model:FILE,ctl:FILE,media:FILE,findmodel:can("findModel"),restorelogo:can("restoreLogo"),folder:can("openThemeFolder"),rmlogo:can("removeTheme")};
    const B=(a,label,primary)=>(CAN[/^rm/.test(a)?"rmlogo":a]===false)?"":`<button class="chip${primary?" primary":""}" type="button" data-a="${a}">${label}</button>`;
    const R=(title,state,btns,note)=>!btns?"":`<div class="cz-row"><div class="cz-l"><b>${title}</b><span>${state}</span>${note?`<small>${note}</small>`:""}</div><div class="cz-btns">${btns}</div></div>`;
    const media=(MEDIA[id]||{}).kind==="cart"?"Cartridge":id==="psp"?"UMD":"Disc",autoBoot=!sc||sc.bootOnLaunch!==false;
    row.innerHTML=`<div class="set-head"><div class="set-thumb"></div><div class="set-name"></div></div>`+
      R("Logo",t.logo?"Your image":"Built-in wordmark",B("logo",t.logo?"Replace":"Add image",!t.logo)+(isConsole&&api.findLogos?B("findlogo","Find a logo"):"")+(t.logoPrev?B("restorelogo","Restore previous"):"")+(t.logo?B("rmlogo","Remove"):""),isConsole&&api.findLogos&&!(czPick&&czPick.id===id)?"Find a logo: official logos from Wikimedia Commons, free to use":"")+
      (czPick&&czPick.id===id?czPickHtml(name):"")+
      (isConsole?R("3D console",t.model?"Your 3D model":"Built-in",B("model",t.model?"Replace":"Add model",!t.model)+B("findmodel","Find a model")+(t.model?B("fit","Fit on shelf")+(INSERT_3D?B("testmodel","Test"):"")+B("rmmodel","Remove"):""),INSERT_3D?"Shown in the start-up animation and on its museum shelf":"Stands on its museum shelf (Fit on shelf takes you there)")+
        // console.png only fed the 3D start-up animation (switched off): the row stays only to remove one you added
        (INSERT_3D||t.console?R("Console picture",t.console?"Your image":"None",(INSERT_3D?B("console",t.console?"Replace":"Add image"):"")+(t.console?B("rmconsole","Remove"):""),INSERT_3D?"A flat picture, used when there's no 3D model":"Not shown anywhere at the moment (it was for the old start-up animation)"):"")+
        R("Controller",t.controller?"Your 3D model":"None added",B("ctl",t.controller?"Replace":"Add model")+(t.controller?B("rmcontroller","Remove"):""),"Lies on the museum shelf; Fit on shelf can also use one from the console model")+
        R(media,t.media?"Your 3D model":"Built-in",B("media",t.media?"Replace":"Add model")+(t.media?B("rmmedia","Remove"):""))+
        R("Boot video",t.boot?"Your video":"Built-in intro",B("boot",t.boot?"Replace":"Add video")+B("preview","Preview")+(t.boot?B("rmboot","Remove"):"")+`<button class="chip cz-toggle${autoBoot?" on":""}" type="button" data-a="bootauto" aria-pressed="${autoBoot}">${autoBoot?"Plays on launch":"Off on launch"}</button>`,sc&&sc.insertAnim!==false&&!t.boot?"No video added: with the boot screen on (Settings → this console) games start on that instead of the built-in intro":""):"")+
      // the PC shelf has no start-up animation, but its museum shelf shows a 3D PC (a built-in one, or yours)
      (id==="pc"?R("3D PC",t.model?"Your 3D model":"Built-in gaming PC",B("model",t.model?"Replace":"Add model",!t.model)+B("findmodel","Find a model")+B("fit","Fit on shelf")+(t.model?B("rmmodel","Remove"):""),"Stands on the PC shelf in the museum. Your own .glb replaces the built-in one; Remove brings it back"):"")+
      R("Files","themes\\"+id,B("folder","Open folder"));
    row.querySelector(".set-name").textContent=name;
    const th=row.querySelector(".set-thumb");if(t.logo){const im=new Image();im.src=t.logo;im.alt="";th.appendChild(im)}else{const sp=document.createElement("span");sp.textContent=name;th.appendChild(sp)}
    row.addEventListener("click",async ev=>{const a=ev.target.closest("[data-a]");if(!a)return;const act=a.dataset.a;
      try{
        if(act==="logo"||act==="boot"||act==="console"){const img=act!=="boot";const f=await api.pick({kind:"file",title:act==="logo"?`Choose a ${name} logo`:act==="console"?`Choose a ${name} console image (transparent PNG works best)`:`Choose a ${name} boot video`,filters:img?[{name:"Images",extensions:["png","svg","webp","jpg"]}]:[{name:"Videos",extensions:["mp4","webm","mov"]}]});if(f){await api.setTheme(id,act,f);toast(act==="logo"?`Logo set for ${name}`:act==="console"?`Console image set for ${name}`:`Boot video set for ${name}`);reloadThemes()}}
        else if(act==="model"){const f=await api.pick({kind:"file",title:`Choose a ${name} 3D model (.glb)`,filters:[{name:"3D models",extensions:["glb","gltf"]}]});if(f){await api.setTheme(id,"model",f);toast(`3D model set for ${name}. Use Fit on shelf to check which way it faces`);await reloadThemes();if(INSERT_3D)testModel(id)}}
        else if(act==="fit"){musFitAt(id)}
        else if(act==="findlogo"){czFindLogo(id,name)}
        else if(act==="uselogo"){czUseLogo(id,name,a.dataset.k)}
        else if(act==="closepick"){czClosePick()}
        else if(act==="restorelogo"){await api.restoreLogo(id);play_("open");toast(`Earlier logo back for ${name}`);await reloadThemes();czPickFocus('[data-a="restorelogo"],[data-a="logo"]')}
        else if(act==="ctl"){const f=await api.pick({kind:"file",title:`Choose a ${name} controller model (.glb)`,filters:[{name:"3D models",extensions:["glb","gltf"]}]});if(f){await api.setTheme(id,"controller",f);await api.saveConsoleFit(id,{controller:"file"}).catch(()=>{});toast(`Controller set for ${name}. It lies on the museum shelf in front of the name plate`);reloadThemes()}}
        else if(act==="testmodel"){testModel(id)}
        else if(act==="findmodel"){const r=await api.findModel(id,name,"sketchfab");toast(r&&r.ok?`Pick a free, downloadable ${name} model in your browser — check its licence — and save the .glb (or .zip) to Downloads. Boxshelf installs it as soon as it lands.`:(r&&r.error)||"Couldn't start")}
        else if(act==="media"){const f=await api.pick({kind:"file",title:`Choose a ${name} ${id==="sw"||id==="sw2"?"cartridge":"disc"} model (.glb)`,filters:[{name:"3D models",extensions:["glb","gltf"]}]});if(f){await api.setTheme(id,"media",f);toast(`Model set for ${name}. Inspect a game to see it.`);reloadThemes()}}
        else if(act==="rmlogo"||act==="rmboot"||act==="rmconsole"||act==="rmmodel"||act==="rmmedia"||act==="rmcontroller"){await api.removeTheme(id,act.slice(2));if(act==="rmcontroller"&&themes[id]&&themes[id].modelOpts&&themes[id].modelOpts.controller==="file")await api.saveConsoleFit(id,{controller:null}).catch(()=>{});reloadThemes()}
        else if(act==="preview"){closeCustomize();runBoot({id,name},null)}
        else if(act==="folder")api.openThemeFolder(id);
        else if(act==="bootauto"){const c=cfgInfo&&cfgInfo.config;if(!c||!c.systems||!c.systems[id]){play_("error");toast(`Set up ${name} in Settings first`);return} /* (beta.10) a console not set up yet has no systems entry (no TypeError) */c.systems[id].bootOnLaunch=!(c.systems[id].bootOnLaunch!==false);await api.saveConfig(c);const sh=shelves.find(x=>x.id===id);if(sh)sh.bootOnLaunch=c.systems[id].bootOnLaunch;play_("tick",{dir:1});renderCustomize();const f=$("setRows").querySelector('[data-a="bootauto"]');if(f)padSet($("customize"),f)}
      }catch(err){toast(err.message)}});
    box.appendChild(row);
  });
  custSwitchText();
  $("themePathNote").textContent=`Files are saved in ${cfgInfo?cfgInfo.appRoot:"Boxshelf"}\\themes\\<system>\\ as logo.png, boot.mp4, console.png, console.glb or controller.glb (console.json keeps how the console sits on the museum shelf and its lid)`;
}
/* ---------- The quick menu (☰ / Start) ----------
   A glass panel from the right, the same one Details uses, with the shelf stepping aside the same way. On top, the game
   you're on (Favorite · Details · Box art · Hide); then one list where a white pill (the console tabs' pill, turned on
   its side) glides to the row you're on. Volume changes right in its row (← →); Customize, Sort & filter, Trophies and
   Power open in place (A / →) into lines of chips like the Sort panel's. Theme and Background preview live as you move
   over them: A keeps one, B puts back what you had. Restart PC and Shut down are held to confirm. */
const qm={open:false,r:1,sub:null,sr:0,sc:0,ac:0,rows:[],pv:null,outs:null};
const ARTC={el:null,on:false,paused:false,stopping:false,done:0,total:0,t:0}; // (beta.10) the background box art run (artCardOn, under misc)
const QM_THEMES=[["spectrum","Spectrum"],["classic","Classic"],["keys","Keys"],["arcade","Arcade"],["aurora","Aurora"],["midnight","Midnight"],["artwork","Artwork"],["blur","Blur"]];
QM_THEMES.push(...XTHEMES.map(t=>[t.id,t.name])); // (the extra themes, after the original six)
const QM_SOUNDS=[["aero","Aero"],["melody","Melody"],["musicbox","Music box"]];
const QM_MUSIC=[["drift","Drift"],["night","Night"],["glow","Glow"],["off","Off"]];
const QM_MVOL=[["0.2","Quiet"],["0.4","Soft"],["0.7","Full"]];
const qsMvolNow=()=>{const v=mmVol();return QM_MVOL.reduce((a,b)=>Math.abs(+b[0]-v)<Math.abs(+a[0]-v)?b:a)[0]};
const QM_BGS=[["blur","Soft blur"],["art","Game artwork"]]; // (1.76: no longer a quick-menu line: Artwork and Blur are themes)
const QM_BOOTS=[["chime","Chime"],["crystal","Crystal"],["pulse","Pulse"],["horizon","Horizon"],["keys","Keys"]];
const QM_INTROS=[["ripple","Ripple"],["fade","Fade"],["off","Off"]];
const QS_I={
  heart:'<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M12 20s-7-4.4-7-10a4 4 0 0 1 7-2.6A4 4 0 0 1 19 10c0 5.6-7 10-7 10z"/></svg>',
  info:'<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><circle cx="12" cy="12" r="9"/><path d="M12 11v6M12 7.5h.01"/></svg>',
  image:'<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><rect x="3" y="4" width="18" height="16" rx="2"/><circle cx="9" cy="10" r="2"/><path d="M21 16l-5-5-9 9"/></svg>',
  hide:'<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M3 3l18 18M10.6 6.1A9.7 9.7 0 0 1 12 6c5 0 9 6 9 6a15 15 0 0 1-2.6 3.2M6.6 7.6C4.3 9.2 3 12 3 12s4 6 9 6a8.6 8.6 0 0 0 4.4-1.2"/></svg>',
  vol:'<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M4 9.5h3.5L12 5v14l-4.5-4.5H4z"/><path d="M15.5 9a4 4 0 0 1 0 6M18 6.5a7.5 7.5 0 0 1 0 11"/></svg>',
  mute:'<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M4 9.5h3.5L12 5v14l-4.5-4.5H4zM16 9.5l5 5M21 9.5l-5 5"/></svg>',
  brush:'<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M12 3a9 9 0 1 0 0 18c1.2 0 1.8-.9 1.4-2-.5-1.3.3-2.5 1.7-2.5H18a3 3 0 0 0 3-3C21 7 17 3 12 3z"/><circle cx="7.5" cy="11" r="1.2"/><circle cx="10" cy="7" r="1.2"/><circle cx="15" cy="7.5" r="1.2"/></svg>',
  sort:'<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M4 6h16M7 12h10M10 18h4"/></svg>',
  trophy:'<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M8 4h8v5a4 4 0 0 1-8 0zM8 6H4.5a3 3 0 0 0 3.5 4M16 6h3.5a3 3 0 0 1-3.5 4M12 13v4M8.5 20h7"/></svg>',
  museum:'<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M3 9l9-5 9 5M5 10v8M9.5 10v8M14.5 10v8M19 10v8M3 20h18"/></svg>',
  gear:'<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><circle cx="12" cy="12" r="3"/><path d="M12 2.5v3M12 18.5v3M2.5 12h3M18.5 12h3M5.3 5.3l2.1 2.1M16.6 16.6l2.1 2.1M5.3 18.7l2.1-2.1M16.6 7.4l2.1-2.1"/></svg>',
  power:'<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M12 3v8M6.3 6.3a8 8 0 1 0 11.4 0"/></svg>',
  cube:'<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linejoin="round"><path d="M12 3l8 4.5v9L12 21l-8-4.5v-9z"/><path d="M4 7.5l8 4.5 8-4.5M12 12v9"/></svg>'
};
// "PS2 · Built-in 3D · Wordmark": what this shelf has of its own (Customize's rows, in a few words)
function qsCzShelf(){const s=cur();return s&&THEME_IDS.find(x=>x[0]===s.id)||null}
function qsCzVal(){const e=qsCzShelf();if(!e)return "Logos, 3D consoles & boot videos";const t=themes[e[0]]||{};
  return `${e[1]} · ${t.model?"Own 3D":e[2]||e[0]==="pc"?"Built-in 3D":"No 3D"} · ${t.logo?"Own logo":"Wordmark"}`}
const qsEsc=s=>String(s==null?"":s).replace(/[&<>"]/g,c=>({"&":"&amp;","<":"&lt;",">":"&gt;",'"':"&quot;"})[c]);
const qsName=(L,v)=>(L.find(x=>x[0]===v)||L[0])[1];
let qsEl=null,qmSaveT=0;
function qmSave(){clearTimeout(qmSaveT);qmSaveT=setTimeout(()=>{qmSaveT=0;if(cfgInfo)api.saveConfig(cfgInfo.config).catch(()=>{})},500)}
function qsUi(){return cfgInfo?cfgInfo.config.ui:{}}
function qsBuild(){if(qsEl)return qsEl;qsEl=document.createElement("aside");qsEl.id="quickMenu";qsEl.className="qm";qsEl.setAttribute("aria-hidden","true");qsEl.setAttribute("aria-label","Quick menu");
  qsEl.innerHTML='<div class="qm-game"></div><div class="qm-scroll"><div class="qm-list" role="menu"><div class="qm-hl"></div></div></div><div class="qm-hints"></div>';
  $("app").appendChild(qsEl);return qsEl}
function qsGame(){const g=curGame();return g&&isGame(g)&&g.kind!=="random"?g:null}
function qsAch(){const g=qsGame();if(!RA.on||!g)return null;try{return raHas(shelfFor(g))?raFor(g):null}catch{return null}}
function qsThemeNow(){const t=qsUi().theme;return THEMES[t]?t:themeId()}
function qsSwatch(id){try{const g=curGame()||{hue:220};return THEMES[id].tint(shelfHue(g),g,shelfPos())}catch{return "linear-gradient(135deg,#222,#555)"}}
// the rows; `lines` opens in place into lines of chips
function qsRows(){const ui=qsUi(),r=qsAch(),g=qsGame(),out=[];
  if(g)out.push({id:"acts"});
  // Android: the Art Studio one press away (the quick menu needs a shortcut); Windows keeps its Box art button only
  if(ANDROID&&g&&g.kind==="rom"&&g.file&&api.artStudio)out.push({id:"studio",icon:QS_I.image,lbl:"Art Studio",val:()=>(shelfFor(g).name||"This console")+" · every game",go:1});
  out.push({id:"vol",icon:QS_I.vol,lbl:"Volume"});
  out.push({id:"cust",icon:QS_I.brush,lbl:"Customize",val:()=>`${qsName(QM_THEMES,qsThemeNow())} · ${qsName(QM_SOUNDS,ui.soundTheme||"aero")}`,lines:()=>[
    {k:"theme",l:"Theme",sw:1,opts:QM_THEMES,cur:qsThemeNow()},{k:"snd",l:"Sounds",opts:QM_SOUNDS,cur:ui.soundTheme||"aero"},
    ...((ui.soundTheme||"aero")==="melody"&&window.Sound&&Sound.song?[{k:"song",l:"Song",opts:[["next",`${(Sound.song()||{}).name||"This song"} · Next ›`]]}]:[]),
    {k:"music",l:"Menu music",opts:QM_MUSIC,cur:mmPick()},...(mmPick()!=="off"?[{k:"mvol",l:"Music volume",opts:QM_MVOL,cur:qsMvolNow()}]:[]),
    {k:"boot",l:"Boot sound",opts:QM_BOOTS,cur:ui.bootSound||"chime"},{k:"intro",l:"Startup",opts:QM_INTROS,cur:introStyle()}]});
  // the shelf's own look (its logo, 3D console, boot video): straight into Customize on this shelf (it replaced the link that sat at the foot of Customize's chips)
  out.push({id:"cz3d",icon:QS_I.cube,lbl:"3D & logos",val:qsCzVal,go:1});
  out.push({id:"sort",icon:QS_I.sort,lbl:"Sort & filter",val:()=>qsName(SORTS,sortBy)+(filterBy!=="all"?" · "+filterName(filterBy):""),lines:()=>[{k:"sort",l:"Sort by",opts:SORTS,cur:sortBy},{k:"filter",l:"Show",opts:filterList(),cur:filterBy}]});
  out.push({id:"ach",icon:QS_I.trophy,lbl:"Trophies",val:()=>!RA.on?"Not connected":r&&r.total?`${r.earned} / ${r.total}`+(RA.profile?` · ${RA.profile.points.toLocaleString()} pts`:""):RA.profile?`${RA.profile.points.toLocaleString()} points`:"",
    lines:()=>!RA.on?[{k:"note",note:"Sign in once and Boxshelf tracks every unlock."},{k:"go",opts:[["rasetup","Connect RetroAchievements ›"]],big:1}]:[{k:"achinfo",info:1},...(r&&r.total?[{k:"go",opts:[["achg","See this game's achievements ›"]],big:1}]:[])]});
  out.push({id:"mus",icon:QS_I.museum,lbl:"Museum",val:()=>"Walk your collection",go:1});
  if(ANDROID)qsAndroidOrder(out,ui); // (0.3.2) the Thor's order: Museum, Art Studio, Trophies first; then the looks, sounds and sorting
  // (beta.10) while box art downloads in the background: the controller's way to its Pause / Resume and Cancel (the card in
  // the corner takes the mouse and touch; the shelf keeps the D-pad)
  if(ARTC.on&&!ARTC.stopping)out.push({id:"artrun",icon:QS_I.image,lbl:"Box art download",val:artCardText,lines:()=>[{k:"artrun",opts:[...(api.pauseCovers?[ARTC.paused?["resume","Resume"]:["pause","Pause"]]:[]),...(api.cancelCovers?[["cancel","Cancel"]]:[])],big:1}]});
  out.push({sep:1});
  if(api.updateCheck&&!ANDROID)out.push({id:"upd",icon:QS_I.info,lbl:"Updates",val:qsUpdVal}); // (beta.4) check for a newer beta from here
  out.push({id:"set",icon:QS_I.gear,lbl:"Settings",val:()=>"",go:1});
  out.push({id:"pow",icon:QS_I.power,lbl:"Power",val:()=>"",lines:()=>[{k:"pow",opts:(api.powerOptions&&api.powerOptions())||[["quit","Exit Boxshelf"],["sleep","Sleep"],["restart","Restart PC",1],["shutdown","Shut down",1]],big:1}]});
  return out}
// Android (0.3.2): Museum, Art Studio and Trophies as the top three, a divider, then Customize (theme, background,
// startup), Assets (the shelf's logo, 3D console, boot video), Sounds (with the volume) and Sort & filter. Same rows and
// lines as on Windows, only regrouped: the sound choices move out of Customize into Sounds
function qsAndroidOrder(out,ui){const by=id=>out.find(x=>x.id===id),cust=by("cust"),cz=by("cz3d"),acts=out.filter(x=>x.id==="acts");
  if(cust){const all=cust.lines;cust.lines=()=>all().filter(l=>!/^(snd|song|music|mvol|boot)$/.test(l.k));cust.val=()=>qsName(QM_THEMES,qsThemeNow());
    out.push({id:"snds",icon:'<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M9 17.5V6l10-2v11.5"/><circle cx="6.5" cy="17.5" r="2.5"/><circle cx="16.5" cy="15.5" r="2.5"/></svg>',lbl:"Sounds",val:()=>qsName(QM_SOUNDS,ui.soundTheme||"aero")+" · Music: "+qsName(QM_MUSIC,mmPick()),lines:()=>all().filter(l=>/^(snd|song|music|mvol|boot)$/.test(l.k))})}
  if(cz)cz.lbl="Assets";
  const order=["mus","studio","ach",null,"cust","cz3d","snds","vol","sort"],rows=[...acts];
  for(const id of order){if(id===null){rows.push({sep:1});continue}const x=by(id);if(x)rows.push(x)}
  out.length=0;out.push(...rows)}
function qsRenderGame(){const box=qsEl.querySelector(".qm-game"),g=qsGame();box.hidden=!g;if(!g){box.innerHTML="";return}
  const h=HIST[g.id],sh=shelfFor(g)||{},cov=frontUrl(g),fav=isFav(g);
  const meta=[h&&h.ms?fmtPlay(h.ms)+" played":"",h&&h.last?"last played "+fmtDay(h.last/1000):"Not played yet"].filter(Boolean).join(" · ");
  box.innerHTML=`<div class="qm-gtop">${cov?`<img class="qm-cov" alt="" src="${qsEsc(thumb(cov,256))}">`:'<div class="qm-cov"></div>'}<div class="qm-gtx"><div class="qm-kick">${qsEsc(sh.name||"")}</div><div class="qm-gt">${qsEsc(g.title)}</div><div class="qm-gm">${qsEsc(meta)}</div></div></div>`}
function qsRender(){const L=qsEl.querySelector(".qm-list");[...L.children].forEach(e=>{if(!e.classList.contains("qm-hl"))e.remove()});qm.rows=[];qsRenderGame();const g=qsGame();
  qsRows().forEach(r=>{
    if(r.sep){L.appendChild(Object.assign(document.createElement("div"),{className:"qm-sep"}));return}
    if(r.id==="acts"){const a=document.createElement("div");a.className="qm-acts";const fav=isFav(g),canArt=g.kind==="rom"&&!!(api.artStudio||api.chooseArt);
      [["fav",fav?"Favorited":"Favorite",QS_I.heart],["det","Details",QS_I.info],...(canArt?[["art","Box art",QS_I.image]]:[]),["hide","Hide",QS_I.hide]].forEach(([k,l,ic],i)=>{
        const b=document.createElement("button");b.type="button";b.className="qm-act"+(k==="fav"&&fav?" faved":"");b.dataset.k=k;b.innerHTML=ic+`<span>${qsEsc(l)}</span>`;
        b.addEventListener("pointerenter",()=>{if(qm.sub!=null)return;qm.r=0;qm.ac=i;qsPaint()});b.addEventListener("click",()=>{setInput("pointer");if(qm.sub!=null)qsCollapse(true);qm.r=0;qm.ac=i;qsPaint();qsAct(k)});a.appendChild(b)});
      L.appendChild(a);qm.rows.push({r,el:a});return}
    const e=document.createElement("div");e.className="qm-row";e.setAttribute("role","menuitem");
    if(r.id==="vol"){const v=Math.round(curVol()*100);e.innerHTML=`${v?QS_I.vol:QS_I.mute}<span class="qm-lbl">${r.lbl}</span><span class="qm-vbar"><i style="width:${v}%"></i><b style="left:${v}%"></b></span><span class="qm-num">${v?v+"%":"Muted"}</span>`;
      e.querySelector(".qm-vbar").addEventListener("pointerdown",ev=>{const rc=ev.currentTarget.getBoundingClientRect();setVol((ev.clientX-rc.left)/rc.width);qsSoft()})}
    else e.innerHTML=`${r.icon}<span class="qm-lbl">${r.lbl}</span><span class="qm-val">${qsEsc(r.val())}</span>${r.lines||r.go?'<span class="qm-chev">›</span>':""}`;
    const k=qm.rows.length;e.addEventListener("pointerenter",()=>{if(qm.sub!=null)return;qm.r=k;qsPaint()});e.addEventListener("click",ev=>{if(ev.target.closest(".qm-vbar"))return;setInput("pointer");if(qm.sub!=null&&qm.sub!==k)qsCollapse(true);qm.r=k;qsPick()});
    L.appendChild(e);let sub=null;if(r.lines){sub=document.createElement("div");sub.className="qm-sub";L.appendChild(sub)}
    qm.rows.push({r,el:e,sub})});
  if(qm.r>=qm.rows.length)qm.r=qm.rows.length-1;if(qm.r<0)qm.r=0;
  if(qm.sub!=null){if(qm.rows[qm.sub]&&qm.rows[qm.sub].sub)qsFill(qm.rows[qm.sub],true);else qm.sub=null}
  if(ANDROID)qm.sig=qsSig();qsPaint()}
// everything the closed menu's rows show (game card, values, volume): the same string means the built rows are still right
function qsSig(){const g=qsGame(),h=g&&HIST[g.id];return [g?g.id:"",g&&g.art&&g.art.front||"",g&&isFav(g)?1:0,h?h.ms+"/"+h.last:"",Math.round(curVol()*100),RA.on?1:0,...qsRows().map(r=>r.sep?"-":r.id+":"+(r.val?r.val():""))].join("\u0001")}
// Android: once the shelf rests on a game, the closed menu's rows are rebuilt for it in an idle moment (requestIdleCallback
// waits while the shelf slides), so opening the menu builds nothing
function qsFreshSoon(){if(!ANDROID||qsFreshSoon.on||!window.requestIdleCallback)return;qsFreshSoon.on=true;
  requestIdleCallback(()=>{qsFreshSoon.on=false;try{if(qsEl&&!qm.open&&(mode==="browse"||mode==="details")&&qm.sig!==qsSig())qsRender()}catch{}},{timeout:3000})}
function qsFill(x,instant){const inn=document.createElement("div");inn.className="qm-subin";x.lines=[];
  x.r.lines().forEach(ln=>{const d=document.createElement("div");d.className="qm-line";
    if(ln.note){d.innerHTML=`<div class="qm-note">${qsEsc(ln.note)}</div>`;inn.appendChild(d);return}
    if(ln.info){const r=qsAch(),g=qsGame(),p=r&&r.total?Math.round(r.earned/r.total*100):0,pr=RA.profile;
      d.innerHTML=`<div class="qm-trow"><div class="qm-ring" style="--p:${p}"><b>${pr&&pr.pic?`<img alt="" src="${qsEsc(pr.pic)}">`:"🏆"}</b></div><div style="min-width:0"><div class="qm-tt">${r&&r.total?(r.mastered==="mastered"?"★ Mastered · ":"")+`${r.earned} of ${r.total} unlocked`:g?"No trophies for this game yet":"Your trophies"}</div><div class="qm-ts">${qsEsc(pr?`${pr.user} · ${pr.points.toLocaleString()} points`:"Signed in")}</div></div></div>`;inn.appendChild(d);return}
    if(ln.l)d.innerHTML=`<div class="qm-ll">${qsEsc(ln.l)}</div>`;const o=document.createElement("div");o.className="qm-opts";const arr=[];
    ln.opts.forEach(([v,l,hold])=>{const b=document.createElement("button");b.type="button";b.className="qm-opt"+(ln.sw?" sw":"")+(ln.big?" big":"")+(hold?" danger":"")+(ln.cur===v?" cur":"");b.dataset.k=ln.k;b.dataset.v=v;b.title=l;if(hold)b.dataset.hold="1";
      b.innerHTML=ln.sw?`<i style="background:${qsEsc(qsSwatch(v))}"></i><span class="qm-swl">${qsEsc(l)}</span>`:(hold?'<i class="qm-hb"></i>':"")+`<span>${qsEsc(l)}</span>`; // (1.81: theme tiles carry their names, as in Settings)
      b.addEventListener("pointerenter",()=>{const p=qsPos(b);if(p){qm.sr=p[0];qm.sc=p[1];qsPaint();qsPreview()}});
      b.addEventListener("click",()=>{setInput("pointer");const p=qsPos(b);if(p&&!b.dataset.hold){qm.sr=p[0];qm.sc=p[1];qsChoose()}});
      b.addEventListener("pointerdown",ev=>{if(!b.dataset.hold)return;ev.preventDefault();const p=qsPos(b);if(p){qm.sr=p[0];qm.sc=p[1];qsPaint();qsHoldStart(b)}});
      o.appendChild(b);arr.push(b)});
    d.appendChild(o);if(ln.sw){const n=document.createElement("div");n.className="qm-swname";d.appendChild(n)}inn.appendChild(d);if(arr.length)x.lines.push(arr)});
  x.sub.innerHTML="";x.sub.appendChild(inn);const h=inn.offsetHeight;
  // (beta.10) the row takes its full height at once (no layout every frame); the pills fade and slide in on the compositor (quickshelf.css)
  x.sub.style.height=h+"px";if(instant||reduced()||document.body.classList.contains("perf-handheld"))inn.classList.add("now");inn.classList.add("in")}
function qsPos(b){const x=qm.rows[qm.sub];if(!x||!x.lines)return null;for(let r=0;r<x.lines.length;r++){const c=x.lines[r].indexOf(b);if(c>=0)return [r,c]}return null}
function qsFocusEl(){if(qm.sub!=null){const x=qm.rows[qm.sub];return x&&x.lines&&x.lines[qm.sr]&&x.lines[qm.sr][qm.sc]}const x=qm.rows[qm.r];if(!x)return null;return x.r.id==="acts"?x.el.children[qm.ac]:x.el}
function qsPaint(){if(!qsEl)return;const hl=qsEl.querySelector(".qm-hl");
  qm.rows.forEach((x,i)=>{if(x.r.id==="acts")return;x.el.classList.toggle("on",qm.open&&qm.sub==null&&i===qm.r);x.el.classList.toggle("open",qm.sub===i)});
  const acts=qm.rows[0]&&qm.rows[0].r.id==="acts"?qm.rows[0].el:null;if(acts)[...acts.children].forEach((b,i)=>b.classList.toggle("on",qm.open&&qm.sub==null&&qm.r===0&&i===qm.ac));
  qsEl.querySelectorAll(".qm-opt.on").forEach(b=>b.classList.remove("on"));qsEl.querySelectorAll(".qm-swname").forEach(n=>n.textContent="");
  const f=qsFocusEl();
  if(ANDROID&&!qm.open)return; // closing (or built while closed): the highlight is placed when it opens, nothing to measure now
  if(qm.sub!=null){if(f){f.classList.add("on");const sw=f.classList.contains("sw")&&f.closest(".qm-line").querySelector(".qm-swname");if(sw)sw.textContent=f.title+(f.classList.contains("cur")?" · current":"")}
    hl.classList.add("soft");hl.style.opacity="1";hl.style.transform=`translateY(${qm.rows[qm.sub].el.offsetTop}px)`}
  else{const x=qm.rows[qm.r];if(x&&x.r.id!=="acts"){hl.classList.remove("soft");hl.style.opacity="1";hl.style.transform=`translateY(${x.el.offsetTop}px)`}else hl.style.opacity="0"}
  if(f)qsKeepInView(f);qsHints()}
function qsKeepInView(el){const sc=qsEl.querySelector(".qm-scroll");if(!sc.contains(el))return;const r=el.getBoundingClientRect(),b=sc.getBoundingClientRect(),m=36;
  if(r.top<b.top+m)sc.scrollBy({top:r.top-b.top-m,behavior:reduced()?"auto":"smooth"});else if(r.bottom>b.bottom-m)sc.scrollBy({top:r.bottom-b.bottom+m,behavior:reduced()?"auto":"smooth"})}
function qsHints(){if(!qsEl)return;const pad=showPad(),G=(p,k)=>pad?glyph(p):`<span class="g">${k}</span>`,out=[];
  if(qm.sub!=null){const f=qsFocusEl(),k=f&&f.dataset.k;out.push([G("x","Enter"),f&&f.dataset.hold?"Hold":k==="theme"||k==="bg"?"Keep":"Select"],[G("o","Esc"),"Back"])}
  else{const x=qm.rows[qm.r],id=x&&x.r.id;out.push([pad?glyph("↕"):`<span class="g">↑↓</span>`,"Move"]);if(id==="vol")out.push([pad?glyph("↔"):`<span class="g">←→</span>`,"Volume"]);
    out.push([G("x","Enter"),x&&x.r.lines?"Open":"Select"],[G("o","Esc"),"Close"])}
  qsEl.querySelector(".qm-hints").innerHTML=out.map(([k,l])=>`<span class="hint">${k}<span>${qsEsc(l)}</span></span>`).join("")}
function qmOpen(){if(qm.open||(mode!=="browse"&&mode!=="details")||sysBusy())return;uiCrumb("Quick menu");qsBuild();detailsBack=mode==="details"?curGame():null;if(mode==="details")closeDetails(true); /* (beta.10) back out of the menu: Details again */
  qm.open=true;qm.sub=null;qm.pv=null;qm.ac=0;qsEl.querySelector(".qm-scroll").scrollTop=0;if(!ANDROID||qm.sig!==qsSig())qsRender(); /* Android: rows already built for this game and these settings (qsFreshSoon) are kept: the press only moves the highlight and slides the panel in */qm.r=qm.rows.findIndex(x=>x.r.id===(ANDROID?"mus":"vol"));if(qm.r<0)qm.r=0; /* (Android 0.3.2: the highlight starts on the top three) */
  // Android: the menu's own rows are measured (qsPaint reads their offsetTop) before the page-wide qm-open class goes on.
  // The other way round, that read made the phone restyle and lay out the whole shelf, every case face included, right
  // there in the key press (~60% of the open's main-thread time); now the class change is styled once, in the next frame
  qsEl.setAttribute("aria-hidden","false");qsPaint();app.classList.add("qm-open");play_("panel"); // (beta.10) the Android order everywhere: qm-open only moves the panel (transform / visibility), so the measures are the same
  if(RA.on)raShelfLoad().then(()=>{if(qm.open&&qm.sub==null)qsSoft()}).catch(()=>{})}
function qmClose(quiet){qsHoldStop();if(!qm.open)return;qsUnpreview();if(qm.sub!=null){const x=qm.rows[qm.sub];if(x&&x.sub)x.sub.style.height="0px";qm.sub=null}
  qm.open=false;if(ANDROID){if(qsEl)qsEl.setAttribute("aria-hidden","true");qsPaint();app.classList.remove("qm-open")}else{app.classList.remove("qm-open");if(qsEl)qsEl.setAttribute("aria-hidden","true");qsPaint()}if(!quiet)play_("back");renderHints();
  if(quiet)detailsBack=null;else setTimeout(detailsReturn,0)} /* Android: measured before the page-wide class change (see qmOpen). (beta.10) Backed out: Details again if they were open; after this key's own shortcut, if any, had its go */
function qsSoft(){if(!qm.open)return;const s=[qm.r,qm.sub,qm.sr,qm.sc,qm.ac],sc=qsEl.querySelector(".qm-scroll").scrollTop,rid=qm.rows[qm.r]&&qm.rows[qm.r].r.id,sid=qm.sub!=null&&qm.rows[qm.sub]?qm.rows[qm.sub].r.id:null;
  if(sid){const k=qsRows().filter(r=>!r.sep).findIndex(r=>r.id===sid);qm.sub=k>=0?k:null} /* rows can shift (the game row comes and goes): follow them by name */
  qsRender();[qm.r,,qm.sr,qm.sc,qm.ac]=s;{const k=qm.rows.findIndex(x=>x.r.id===rid);if(k>=0)qm.r=k}if(sid){const k=qm.rows.findIndex(x=>x.r.id===sid);qm.sub=k>=0?k:null}
  if(qm.sub!=null){const x=qm.rows[qm.sub];if(x&&x.lines&&x.lines.length){qm.sr=Math.min(qm.sr,x.lines.length-1);qm.sc=Math.min(qm.sc,x.lines[qm.sr].length-1)}}qsEl.querySelector(".qm-scroll").scrollTop=sc;qsPaint();if(qm.pv)qsPreview()} // a library refresh resets themeOverride: put the preview you're on back
function qsBump(e){if(e&&e.animate&&!reduced())e.animate([{translate:"0 0"},{translate:"4px 0"},{translate:"-3px 0"},{translate:"0 0"}],{duration:170})}
function qsMoveV(d){qsHoldStop(); // moving off Restart / Shut down lets go of it
  if(qm.sub!=null){const x=qm.rows[qm.sub],cur=qsFocusEl(),line=x.lines[qm.sr];
    const cx=c=>c.offsetLeft+c.offsetWidth/2,from=cur?cx(cur):0;
    // nearest to `from` among the buttons of `L` on visual row `top` (offsetTop), or -1
    const near=(L,top)=>{let best=-1,bd=1e9;L.forEach((b,i)=>{if(b.offsetTop!==top)return;const dd=Math.abs(cx(b)-from);if(dd<bd){bd=dd;best=i}});return best};
    // (1.81) a line that wraps (the theme tiles): ↑↓ first move between its rows, as Settings' theme grid does; it's left
    // only from its top or bottom row
    if(cur&&line&&line.length>1){const tops=[...new Set(line.map(b=>b.offsetTop))].sort((p,q)=>p-q),to=tops[tops.indexOf(cur.offsetTop)+d];
      if(to!==undefined){const i=near(line,to);if(i>=0){qm.sc=i;play_("tick",{dir:d});qsPaint();qsPreview();return}}}
    const nr=qm.sr+d;if(nr<0){qsCollapse();return}if(nr>=x.lines.length){qsBump(cur);return}
    // into another line: its row nearest you (the bottom row coming up from below, the top row going down)
    const nl=x.lines[nr],nt=nl.map(b=>b.offsetTop),edge=d>0?Math.min(...nt):Math.max(...nt),i=near(nl,edge);
    qm.sr=nr;qm.sc=i>=0?i:0;play_("tick",{dir:d});qsPaint();qsPreview();return}
  const n=qm.r+d;if(n<0||n>=qm.rows.length){qsBump(qsFocusEl());return}qm.r=n;play_("tick",{dir:d});qsPaint()}
function qsMoveH(d){qsHoldStop();
  if(qm.sub!=null){const line=qm.rows[qm.sub].lines[qm.sr];if(!line){if(d<0)qsCollapse();return} /* a section with only an info line (Trophies with no set) */const nc=qm.sc+d;if(nc<0){qsCollapse();return}if(nc>=line.length){qsBump(line[qm.sc]);return}qm.sc=nc;play_("tick",{dir:d});qsPaint();qsPreview();return}
  const x=qm.rows[qm.r];if(!x)return;
  if(x.r.id==="acts"){const n=x.el.children.length,nc=qm.ac+d;if(nc<0||nc>=n){qsBump(x.el.children[qm.ac]);return}qm.ac=nc;play_("tick",{dir:d});qsPaint();return}
  if(x.r.id==="vol"){setVol(curVol()+d*.05);play_("tick",{dir:d});qsSoft();return}
  if(d>0)qsPick();else qsBump(x.el)}
// (beta.4) Quick menu → Updates: A checks the beta page now (not the 6-hour copy); once a newer beta is found, A opens its
// download page in the browser. Windows only (the update check is the Windows build's)
let qsUpd={state:"",latest:"",cur:"",url:""};
function qsUpdVal(){return qsUpd.state==="busy"?"Checking…":qsUpd.state==="new"?`${qsUpd.latest} is out · open its page`:qsUpd.state==="ok"?`Up to date · ${qsUpd.cur}`:qsUpd.state==="err"?"Couldn't check · try again":"Check for a newer beta"}
async function qsUpdCheck(){if(qsUpd.state==="busy")return;
  if(qsUpd.state==="new"&&qsUpd.url){qmClose(true);play_("open");if(api.openExternal)Promise.resolve(api.openExternal(qsUpd.url)).catch(()=>{});toast(`Opening ${qsUpd.latest}'s download page in your browser`);return}
  qsUpd.state="busy";qsSoft();play_("tick",{dir:1});
  try{const r=await api.updateCheck(true);qsUpd={state:r&&r.newer?"new":"ok",latest:(r&&r.latest)||"",cur:(r&&r.current)||"",url:(r&&r.url)||""};if(r&&r.newer)play_("open")}
  catch(e){qsUpd.state="err";play_("error")}
  qsSoft()}
function qsPick(){const x=qm.rows[qm.r];if(!x)return;const r=x.r;
  if(r.id==="upd"){qsUpdCheck();return}
  if(r.id==="acts"){const b=x.el.children[qm.ac];if(b)qsAct(b.dataset.k);return}
  if(r.id==="vol"){const v=curVol();setVol(v>0?0:(volPrev||.7));if(curVol()>0)play_("tick",{dir:1});qsSoft();return}
  if(r.id==="set"){qmClose(true);play_("open");if(window.PulseHost&&PulseHost.settings){PulseHost.settings();return}Setup.open();return}
  if(r.id==="mus"){qmClose(true);openMuseum();return}
  if(r.id==="studio"){const g=qsGame();qmClose(true);if(g&&ANDROID){play_("panel");Setup.open({page:"artstudio",systemId:shelfFor(g).id});return} /* (Android: the console's whole studio) */if(g)artStudioFor(g);return}
  if(r.id==="cz3d"){const e=qsCzShelf();qmClose(true);play_("open");if(e)custSel=e[0];czPick=null;openCustomize();return}
  if(!r.lines)return;if(qm.sub===qm.r){qsCollapse();return}
  qm.sub=qm.r;qm.sr=0;qm.sc=0;qsFill(x,false);
  // land on what's chosen now (the current theme, the current sort…)
  for(let i=0;i<x.lines.length;i++){const c=x.lines[i].findIndex(b=>b.classList.contains("cur"));if(c>=0){qm.sr=i;qm.sc=c;break}}
  if(r.id==="ach"||r.id==="pow"){qm.sr=0;qm.sc=0}
  play_("tick",{dir:1});qsPaint();qsPreview()}
function qsCollapse(quiet){if(qm.sub==null)return;qsHoldStop();qsUnpreview();const x=qm.rows[qm.sub];if(x&&x.sub)x.sub.style.height="0px";qm.r=qm.sub;qm.sub=null;if(!quiet)play_("tick",{dir:-1});qsPaint()}
// Theme and Background preview on the whole screen as you move over them; anything else puts the saved look back.
// (1.81) A theme step only recolours the backdrop's tint at once; the whole theme (body class, ThemeFx, backdrop) follows
// once you stop for 150 ms, as Settings does, so running along the tiles doesn't restyle the app on every press
let qsThemeT=0;
function qsTintNow(v){const g=curGame(),t=THEMES[v],L=document.querySelector("#bgA.on .tint,#bgB.on .tint");if(!g||!t||!L)return;try{L.style.background=t.tint(shelfHue(g),g,shelfPos())}catch{}}
function qsPreview(){const f=qm.sub!=null&&qsFocusEl(),k=f&&f.dataset.k;
  if(k==="theme"){if(qm.pv&&qm.pv.k==="bg"){bgOverride=null}qm.pv={k};const v=f.dataset.v;if(themeOverride!==v){themeOverride=v;qsTintNow(v);clearTimeout(qsThemeT);
      qsThemeT=setTimeout(()=>{qsThemeT=0;if(qm.pv&&qm.pv.k==="theme"&&themeOverride===v){applyTheme();updateBg()}},150)}}
  else if(k==="bg"){if(qm.pv&&qm.pv.k==="theme"){clearTimeout(qsThemeT);qsThemeT=0;themeOverride=null;applyTheme()}qm.pv={k};if(bgOverride!==f.dataset.v){bgOverride=f.dataset.v;updateBg()}}
  else qsUnpreview()}
function qsUnpreview(){clearTimeout(qsThemeT);qsThemeT=0;if(!qm.pv)return;const k=qm.pv.k;qm.pv=null;if(k==="theme"){themeOverride=null;applyTheme()}else bgOverride=null;updateBg()}
function qsChoose(){const f=qsFocusEl();if(!f)return;const k=f.dataset.k,v=f.dataset.v,ui=qsUi();
  if(f.dataset.hold){qsHoldStart(f);return}
  if(k==="artrun"){artCmd(v);return} // (beta.10) the background box art run
  if(k==="go"){qmClose(true);play_("open");if(v==="achg")openAchievements();else if(v==="rasetup")Setup.open({page:"ra"});return}
  if(k==="pow"){qmClose(true);if(v==="quit")api.quit("quick menu: Power → Exit");else if(api.power)api.power(v);return} // Windows: sleep (restart / shut down are held); Android: its own options
  if(k==="song"){if(window.Sound&&Sound.nextSong){Sound.nextSong("shelf");play_("tick",{dir:1})}qsSoft();return}
  if(k==="theme"){clearTimeout(qsThemeT);qsThemeT=0;ui.theme=v;themeOverride=null;qm.pv=null;applyTheme();updateBg();play_("open")}
  else if(k==="bg"){ui.background=v;bgOverride=null;qm.pv=null;updateBg();play_("open")}
  else if(k==="snd"){ui.soundTheme=v;if(window.Sound){Sound.set({theme:v});Sound.play("open")}}
  else if(k==="music"){ui.music=v;mmTrack=null;ambient(mmWant);play_(v==="off"?"back":"open")}
  else if(k==="mvol"){ui.musicLevel=+v;mmLevel=null;ambient(mmWant);play_("tick",{dir:1})}
  else if(k==="boot"){ui.bootSound=v;if(window.Sound){Sound.set({boot:v});Sound.play("boot")}}
  else if(k==="intro"){ui.bootIntro=v;try{localStorage.setItem("bootIntro",v)}catch{}play_("tick",{dir:1});toast(v==="off"?"Boxshelf will start without the logo":"Shows the next time Boxshelf starts")}
  else if(k==="sort"||k==="filter"){pickSort(k==="sort"?0:1,v);qsSoft();return}
  if(k!=="sort"&&k!=="filter")qmSave();qsSoft()}
function qsAct(k){const g=qsGame();if(!g)return;
  if(k==="fav"){toggleFav().then(()=>qsSoft());return}
  if(k==="det"){qmClose(true);openDetails();return}
  if(k==="hide"){qmClose(true);hideGame(g);return}
  if(k==="art"){qmClose(true);if(api.artStudio&&g.kind==="rom"&&g.file)artStudioFor(g);else qsChooseArt(g)}} /* Box art: straight to this game in the Art Studio picker (My picture… is there for a file of your own) */
// Box art: pick an image for this game's front cover (the old one is kept in a "replaced" folder beside the art)
// One game's Art Studio. The Android build brings its own handheld-sized studio (web-extras/android-studio.js: a row per
// part, big pictures, the bottom screen as a touch companion); everywhere else it's the Settings picker, as before.
function artStudioFor(g){const sh=shelfFor(g);play_("panel");if(window.BS_STUDIO&&BS_STUDIO.open({systemId:sh.id,file:g.file,title:g.title,id:g.id,game:g}))return;Setup.open({page:"artgame",systemId:sh.id,file:g.file,title:g.title})}
async function qsChooseArt(g){if(g&&g.kind==="steam"&&api.pcCoverOptions){pcCoverOpen(g);return} /* (beta.5) PC games: the cover picker */if(!api.chooseArt)return;try{const r=await api.chooseArt(g.id);if(r&&r.ok){play_("toast");toast(`New box art for ${g.title}`)}}catch(e){toast(String(e&&e.message||e))}}
let qsHr=0;
function qsHoldStart(b){if(qsHr)return;const t0=performance.now(),bar=b.querySelector(".qm-hb");
  const step=t=>{const k=Math.min(1,Math.max(0,(t-t0)/1000));if(bar)bar.style.width=k*100+"%";if(k>=1){qsHr=0;const kind=b.dataset.v;qmClose(true);toast(kind==="shutdown"?"Shutting down…":"Restarting…");if(api.power)api.power(kind);return}qsHr=requestAnimationFrame(step)};qsHr=requestAnimationFrame(step)}
function qsHoldStop(){if(qsHr){cancelAnimationFrame(qsHr);qsHr=0}if(qsEl)qsEl.querySelectorAll(".qm-hb").forEach(b=>b.style.width="0%")}
// (beta.8) every hold (A on the disc in Inspect, a held Restart / Shut down, the museum's hold) ends, unfinished, when the
// touch is cancelled (a system gesture, the notification shade) or Boxshelf loses focus or is hidden: the release never
// arrives then, and the hold used to finish on its own (a game started, the PC restarted)
function holdsCancel(){try{holdEnd(false)}catch{}try{if(qsHr)qsHoldStop()}catch{}try{if(musHoldT)musHoldEnd()}catch{}}
addEventListener("pointercancel",holdsCancel,true);addEventListener("blur",holdsCancel);document.addEventListener("visibilitychange",()=>{if(document.hidden)holdsCancel()});
// entry points for the pad loop and the key listener
function qmMove(v){if(qm.open)qsMoveV(v)}
function qmSide(h){if(qm.open)qsMoveH(h)}
function qmPick(){if(!qm.open)return;if(qm.sub!=null)qsChoose();else qsPick()}
function qmBack(){if(!qm.open)return;if(qm.sub!=null)qsCollapse();else qmClose()}
function paintMenuGlyph(){const g=$("menuGlyph");if(!g)return;const h=showPad()?glyph("☰"):'<svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><path d="M4 7h16M4 12h16M4 17h16"/></svg>';if(g.dataset.h!==h){g.dataset.h=h;g.innerHTML=h}} // only when it changes: this runs with the hints on every shelf step
$("menuBtn").addEventListener("click",e=>{e.stopPropagation();setInput("pointer");qm.open?qmClose():qmOpen()});
addEventListener("keydown",e=>{if(!qm.open)return;if(mode!=="browse"&&mode!=="details"){qmClose(true);return} /* something else took over (Settings, a launch): the menu goes, the key is theirs */const k=e.key;const eat=()=>{e.preventDefault();e.stopImmediatePropagation()};setInput("kbd");
  if(k==="ArrowLeft"||k==="ArrowRight"){eat();qmSide(k==="ArrowRight"?1:-1)}else if(k==="ArrowDown"||k==="ArrowUp"){eat();qmMove(k==="ArrowDown"?1:-1)}
  else if(k==="Enter"||k===" "){eat();if(!e.repeat)qmPick()}else if(k==="Escape"||k==="Backspace"){eat();qmBack()}else if(k==="Tab"){eat();qmClose()}else if(k.length===1||/^F\d/.test(k)||/^(PageUp|PageDown|Home|End)$/.test(k))qmClose()},true); // a shortcut (M, G, S…) closes the Quick Shelf and does its thing
addEventListener("keyup",e=>{if(e.key==="Enter"||e.key===" ")qsHoldStop()},true);
addEventListener("pointerup",()=>{if(qsHr)qsHoldStop()},true);addEventListener("pointercancel",()=>{if(qsHr)qsHoldStop()},true); // letting go of the mouse early cancels a held Restart / Shut down
let qmEatClick=0;addEventListener("pointerdown",e=>{if(qm.open&&!e.target.closest("#quickMenu,#menuBtn,#toast")){qmEatClick=performance.now();qmClose()}},true);
addEventListener("click",e=>{if(qmEatClick&&performance.now()-qmEatClick<600){qmEatClick=0;e.stopImmediatePropagation();e.preventDefault()}},true); // the click that closes the menu doesn't also pick a case behind it
// "LB / RB switch system" on an Xbox pad, "L1 / R1" on PlayStation, "L / R" on Nintendo, "Q / E" on the keyboard
function custSwitchText(){const el=$("czSwitch");if(el)el.textContent=(showPad()?`${padWord("L1")} / ${padWord("R1")}`:"Q / E")+" switch system."}
function custStep(d){czPick=null;const i=THEME_IDS.findIndex(x=>x[0]===custSel),n=THEME_IDS.length;custSel=THEME_IDS[((i<0?0:i)+d+n)%n][0];play_("tick",{dir:d});renderCustomize();const f=$("setRows").querySelector(".cz-tab.on");if(f){padSet($("customize"),f);revealIn(f)}}
function openCustomize(){if(mode!=="browse"&&mode!=="details")return;closeDetails(true);mode="customize";renderCustomize();app.classList.add("settings-open");$("customize").setAttribute("aria-hidden","false");sfx.flip();const c=$("setRows").querySelector(".cz-tab.on")||$("setClose");setTimeout(()=>padSet($("customize"),c),60)}
function closeCustomize(){if(mode!=="customize")return;czPick=null;padSet($("customize"),null);mode="browse";app.classList.remove("settings-open");$("customize").setAttribute("aria-hidden","true")}
const toggleCustomize=()=>mode==="customize"?closeCustomize():openCustomize();
let dragDepth=0;
addEventListener("dragenter",e=>{if(!e.dataTransfer||![...e.dataTransfer.types].includes("Files")||!cur())return;dragDepth++;app.classList.add("dragging")});
addEventListener("dragleave",()=>{dragDepth=Math.max(0,dragDepth-1);if(!dragDepth)app.classList.remove("dragging")});
addEventListener("dragover",e=>e.preventDefault());
addEventListener("drop",async e=>{e.preventDefault();dragDepth=0;app.classList.remove("dragging");const f=e.dataTransfer&&e.dataTransfer.files[0];const s=cur();if(!f||!s)return;
  const p=api.pathForFile(f);if(!p){toast("Couldn't read that file");return}
  const kind=f.type.startsWith("image/")?"logo":f.type.startsWith("video/")?"boot":null;
  if(!kind){toast("Use an image (PNG, SVG, WebP) or a video (MP4, WebM)");return}
  if(kind==="boot"&&s.kind!=="console"){toast("Boot animations are for console shelves");return}
  try{await api.setTheme(s.id,kind,p);toast(kind==="logo"?`Logo set for ${s.name}`:`Boot video set for ${s.name}`);reloadThemes()}catch(err){toast(err.message)}});

/* ---------- hints ---------- */
const SHAPES={
  tri:`<svg viewBox="0 0 16 16"><path d="M8 2.5 14 13H2z" fill="none" stroke="var(--tri)" stroke-width="2" stroke-linejoin="round"/></svg>`,
  sq:`<svg viewBox="0 0 16 16"><rect x="3" y="3" width="10" height="10" fill="none" stroke="var(--sq)" stroke-width="2" rx="1"/></svg>`,
  x:`<svg viewBox="0 0 16 16"><path d="M3.5 3.5l9 9M12.5 3.5l-9 9" stroke="var(--cross)" stroke-width="2.2" stroke-linecap="round"/></svg>`,
  o:`<svg viewBox="0 0 16 16"><circle cx="8" cy="8" r="5" fill="none" stroke="var(--circ)" stroke-width="2"/></svg>`
};
/* ---------- shuffle: a spin across the shelf that lands on a random game with a holographic flash ---------- */
let shuffling=false;
const shuffleAll=()=>!(cfgInfo&&cfgInfo.config.ui.shuffleAll===false);
const isGame=g=>g.kind==="rom"||g.kind==="steam";
// (beta.4) PC games carry their source: Steam, Epic, Quiver or added by hand (main/pcgames.js)
const PC_SRC={epic:"Epic Games",quiver:"Quiver",custom:"Custom",gog:"GOG",ubisoft:"Ubisoft",ea:"EA",xbox:"Xbox"},PC_HOW={epic:"Starts through the Epic Games Launcher, so its sign-in, cloud saves and overlay work as usual.",quiver:"Installed with Quiver Launcher. Boxshelf starts it from its own folder; updates stay in Quiver.",custom:"Added by you in Settings → Custom games. Boxshelf starts it from its own folder.",
  gog:"Installed with GOG Galaxy. GOG games have no copy protection: Boxshelf starts it itself; updates stay in GOG Galaxy.",ubisoft:"Starts through Ubisoft Connect, so its sign-in and cloud saves work as usual.",ea:"Starts through the EA app, so its sign-in and cloud saves work as usual.",xbox:"An Xbox app game (Game Pass included). It starts like any Xbox app game, signed in with your Microsoft account."};
// (beta.7) the stores whose games are handed to their own launcher (Back returns to the shelf while they run)
const PC_EXT={epic:"Epic Games Launcher",ubisoft:"Ubisoft Connect",ea:"EA app",xbox:"Xbox app"};
// a quick rainbow lick over the tile as each cover flips past
function flick(el){if(!el)return;el.classList.remove("flick");void el.offsetWidth;el.classList.add("flick");setTimeout(()=>el.classList.remove("flick"),520)}
const coverOf=(sh,g)=>(g.art&&g.art.front)||Covers.front(STYLE[sh.id]||STYLE.media,g);
const preload=url=>new Promise(r=>{const im=new Image();im.onload=()=>{if(im.decode)im.decode().then(r,r);else r()};im.onerror=()=>r();im.src=url;setTimeout(r,900)}); // (beta.10) decoded too, so the swap costs nothing
// (beta.10) a reel face's URL: real box art as is; a made-up cover encoded in the worker (never Covers.front mid-roll)
const coverAsync=(sh,g)=>(g.art&&g.art.front)?Promise.resolve(g.art.front):appFrontAsync(STYLE[sh.id]||STYLE.media,g).catch(()=>coverOf(sh,g));
// Slot machine on the "Surprise me" tile: its cover flips through random games (from every shelf, or this one),
// slowing down until it stops on the pick. The tile then shows that game; A plays it, R rolls again.
async function shuffle(){
  if(mode!=="browse"||shuffling||sysBusy()||!shelves.length)return;
  const sh=cur(),ri=sh.items.findIndex(g=>g.kind==="random");if(ri<0){toast("Nothing to shuffle on this shelf");return}
  if(sel!==ri)go(ri);
  const tileG=sh.items[ri];
  const pool=[];const srcs=shuffleAll()?shelves.map((x,i)=>i).filter(i=>shelves[i]!==HOME&&!shelves[i].col):[sysIdx]; // (beta.10) not your collections: their games are on their consoles' shelves already
  srcs.forEach(i=>shelves[i].items.forEach((g,j)=>{if(isGame(g))pool.push({shelf:i,sh:shelves[i],index:j,game:g})}));
  if(!pool.length)return;
  const rnd=n=>Math.floor(Math.random()*n);
  const target=pool[rnd(pool.length)];
  // the reel: ~22 faces, mostly distinct, ending on the pick
  // (beta.10) the faces before the pick: games with real box art when there are a few (nothing has to be drawn while it rolls)
  const realArt=pool.filter(e=>e.game.art&&e.game.art.front),fill=realArt.length>=3?realArt:pool;
  const K=Math.min(24,Math.max(10,fill.length+6));const reel=[];let last=null;
  for(let i=0;i<K-1;i++){let e=fill[rnd(fill.length)];if(fill.length>1&&e===last)e=fill[(fill.indexOf(e)+1)%fill.length];reel.push(e);last=e}
  reel.push(target);
  const U=[],face=i=>U[i]||(U[i]=coverAsync(reel[i].sh,reel[i].game).then(u=>preload(u).then(()=>u))); // each face: URL resolved and decoded once
  shuffling=true;app.classList.add("shuffling");closeDetails(true);rumble(.15,.35,120);
  clearTimeout(tileG.revealT);if(tileG.revealed&&slots[ri]&&slots[ri].el)reveal(slots[ri],tileG,tileG.pick,false);
  const alive=()=>mode==="browse"&&shuffling&&slots[ri]&&slots[ri].el&&cur()===sh;
  let rolled=null,landed=false;
  try{
    if(fill!==realArt)while(performance.now()-shelfMovedAt<600&&alive())await wait(60); // (beta.10) made-up faces: drawn once the slide to the tile has settled
    await Promise.all(reel.slice(0,8).map((e,i)=>face(i)));
    const tile=slots[ri];if(!alive())return;
    tile.el.classList.add("rolling");rolled=tile;
    for(let i=0;i<reel.length&&alive();i++){
      const t=i/(reel.length-1),u=await face(i);if(!alive())break;
      tile.img.src=u;flick(tile.el);play_("tick",{dir:1});
      if(i+6<reel.length)face(i+6);
      await wait(90+Math.pow(t,2.6)*620);
    }
    if(!alive())return;
    tile.el.classList.remove("rolling");
    // it's the pick now: the tile wears that game's cover and A plays it
    landed=true;tileG.pick=target;reveal(tile,tileG,target,true); // the card wears the real box art for a while; the pick is forgotten when it folds away
    clearTimeout(tileG.revealT);tileG.revealT=setTimeout(()=>{if(tileG.pick===target&&slots[ri]===tile&&tile.el){tile.el.classList.add("unreveal");setTimeout(()=>{if(tile.el){reveal(tile,tileG,target,false);tile.el.classList.remove("unreveal")}else tileG.revealed=false; /* scrolled out of the window meanwhile: no DOM to repaint */tileG.pick=null;updateInfo()},380)}},14000);
    tile.el.classList.remove("holo");void tile.el.offsetWidth;tile.el.classList.add("holo");setTimeout(()=>tile.el.classList.remove("holo"),3200);
    play_("unlock");rumble(.9,.6,320);setTimeout(()=>rumble(.3,.6,180),380);
    updateInfo();
  }finally{shuffling=false;app.classList.remove("shuffling");const tile=slots[ri];if(tile&&tile.el&&cur()===sh)tile.el.classList.remove("rolling");
    if(rolled&&!landed){if(rolled.el)rolled.el.classList.remove("rolling");if(rolled.img&&!tileG.revealed)rolled.img.src=tileG.art.front}} // interrupted mid-roll: the card goes back to its own face
}
// Show (or put away) the picked game on the Surprise card: front, back, spine art and title, so flipping and
// inspecting the card behaves exactly like the real box for a while
function reveal(tile,tileG,pk,on){
  const g=on?pk.game:tileG,sh=on?(pk.sh||shelves[pk.shelf]):cur(),style=STYLE[sh.id]||STYLE.media;
  tileG.revealed=on;
  tile.img.src=on?coverOf(sh,g):tileG.art.front;
  tile.el.querySelectorAll(".spine-title").forEach(t=>t.textContent=g.title);
  tile.el.querySelectorAll(".spine-band").forEach(b=>{b.style.background=style.spineBand.bg;b.style.color=style.spineBand.fg;b.textContent=style.spineBand.label});
  tile.el.querySelectorAll(".spine-img").forEach(x=>x.remove());
  if(on&&g.art&&g.art.spine)tile.el.querySelectorAll(".f-left,.f-right").forEach(face=>{const sp=new Image();sp.className="spine-img";sp.alt="";sp.draggable=false;sp.src=g.art.spine;sp.onerror=()=>sp.remove();face.appendChild(sp)});
  tile.back.innerHTML="";tile.backDrawn=false;if(rawItem()===tileG)ensureBack(); // draw the back right away so a flip is never blank
  updateInfo();
}
// Play the game a Surprise tile landed on: hop to its shelf and box, then start it
async function playPick(tileG){
  const pk=tileG.pick;if(!pk)return;
  const si=pk.sh?shelves.indexOf(pk.sh):pk.shelf;if(si<0)return;
  if(si!==sysIdx){setSystem(si);await wait(360)}
  if(mode!=="browse")return;
  const j=cur().items.indexOf(pk.game);
  if(j<0){launchNow(pk.game);return} // hidden by the shelf's filter: start it anyway
  go(j);await wait(600);if(mode==="browse"&&curGame()===pk.game)play();
}
// The phone companion's Play (main/companion.js → renderer/companion/tv-input.js): the same path as pressing A on that
// box, so the boot video / slide, history and Home menu all happen as usual. Whatever is open closes first (Settings,
// the museum, Inspect…); a game that is still closing (the phone's "Quit and play") is waited for.
let compPlay=null;
function companionPlay(p){if(!p||typeof p!=="object")return;compPlay={p,until:performance.now()+20000};companionStep()}
function companionStep(){const c=compPlay;if(!c)return;if(performance.now()>c.until){compPlay=null;toast("Couldn't start the game from the phone: Boxshelf was busy");return}
  const again=ms=>setTimeout(()=>{if(compPlay===c)companionStep()},ms);
  if(mode==="launch"&&(runningKind==="steam"||runningKind==="error")&&!returnPrep){endLaunch();return again(300)} // "Running through Steam" / an error note: nothing to wait for
  if(mode==="launch"||mode==="boot"||mode==="homemenu"||sysBusy()||returnPrep||app.classList.contains("returning"))return again(300);
  if(mode==="setup"){if(window.Settings&&Settings.isOpen&&Settings.isOpen())Settings.close();else if(window.Setup)Setup.close();return again(450)}
  if(mode==="museum"){closeMuseum();return again(500)}if(mode==="inspect"){closeInspect();return again(400)}
  if(mode==="sort"){closeSort();return again(200)}if(mode==="achv"){closeAchievements();return again(200)}if(mode==="customize"){closeCustomize();return again(300)}
  if(qm.open)qmClose(true);
  const si=shelves.findIndex(s=>s.id===c.p.shelf),sh=shelves[si],has=L=>(L||[]).find(x=>x&&x.id===c.p.id),g=sh&&(has(sh.items)||has(sh.games));compPlay=null;
  if(!g){toast(`${c.p.title||"That game"} isn't on the shelf right now`);return}
  if(mode==="details")closeDetails(true);
  setInput("pad");playPick({pick:{shelf:si,game:g}})}
/* ---------- museum: the whole collection as one shelving wall (renderer/museum.js) ---------- */
// Room options, shared by the Room menu inside the museum (Start / Tab) and Settings → Museum. Saved in ui.museumStyle
// (ui.museumLight holds the lighting mood). Every change shows up live.
// Lighting scenes: the ids are the old moods' (a saved choice carries over): Gallery is now Classic, After hours Evening,
// Neon Neon night. The sliders live under Fine-tune (folded away until you open it).
const MUS_SCENES=[["gallery","Classic"],["daylight","Daylight"],["night","Evening"],["neon","Neon night"],["spotlit","Spotlit gallery"],["lightsout","Lights out"]];
const MUS_ROOM=[
  {group:"Lighting",key:"light",label:"Scene",type:"choice",opts:MUS_SCENES,note:"Classic is the original look. Lights out leaves only the TV and the shelves' own glow."},
  {key:"timeOfDay",label:"Follow the time of day",type:"bool",note:"Daylight in the morning, Classic in the afternoon, Evening at night"},
  {key:"walkIn",label:"Lights come on as you walk in",type:"bool",note:"Off: the room opens with the lamps already on"},
  {key:"fine",label:"Fine-tune",type:"fold",note:"Brightness, warmth, the spotlight and the dust, on top of the scene"},
  {key:"bright",label:"Brightness",type:"range",min:.4,max:1.6,step:.1,fmt:v=>Math.round(v*100)+"%",fine:true},
  {key:"warmth",label:"Warmth",type:"range",min:-1,max:1,step:.1,fmt:v=>v>.05?`Warm ${Math.round(v*100)}%`:v<-.05?`Cool ${Math.round(-v*100)}%`:"Neutral",fine:true},
  {key:"spot",label:"Spotlight on the case",type:"range",min:0,max:2,step:.1,fmt:v=>Math.round(v*100)+"%",fine:true},
  {key:"dust",label:"Dust in the light",type:"range",min:0,max:2,step:.25,fmt:v=>v===0?"Off":Math.round(v*100)+"%",fine:true},
  {group:"Retro TV",key:"tv",label:"Retro TV",type:"bool",note:"A CRT that plays vintage commercials, your games' trailers or what you played lately"},
  {key:"tvguide",label:"What's on TV…",type:"tvguide",room:true,note:"Walks you over to the TV and opens its guide (switching it on if it's off)"},
  {key:"tvStand",label:"Stand",type:"choice",opts:[["cart","Wooden TV cart"],["cabinet","Low cabinet"],["floor","Floor-standing set"],["wall","Wall mount"]],tv:true,note:"The low cabinet has a couple of consoles inside"},
  {key:"tvSize",label:"Size",type:"choice",opts:[["s","Small"],["m","Medium"],["l","Large"]],tv:true},
  {key:"tvPos",label:"Position",type:"choice",opts:[["left","Left"],["centre","Centre"],["right","Right"]],tv:true,note:"Centre: between the shelves and the reading corner, which moves along to make room"},
  {key:"tvCh",label:"Channel",type:"choice",opts:[["ads","Vintage commercials"],["trailers","Game trailers"],["recent","Recently played"],["mix","Shuffle all"]],tv:true,note:"At the TV, up and down change the channel too. Shuffle all mixes the other three"},
  {key:"tvVol",label:"Volume",type:"range",min:0,max:1,step:.1,fmt:v=>v<=0?"Off":Math.round(v*100)+"%",tv:true,note:"On top of Boxshelf's own volume"},
  {key:"tvMute",label:"Mute",type:"bool",tv:true},
  {key:"tvNear",label:"Sound only when I'm looking at it",type:"bool",tv:true,note:"Fades in as you face the TV or walk up to it, and away as you leave"},
  {key:"tvCrt",label:"CRT effect",type:"range",min:0,max:1,step:.1,fmt:v=>v<=0?"Off":Math.round(v*100)+"%",tv:true,note:"Scanlines, curved glass and glow"},
  {key:"tvAnim",label:"Power on and off animation",type:"bool",tv:true},
  {group:"Room",key:"wall",label:"Walls",type:"choice",opts:[["plaster","Dark plaster"],["wood","Wood panels"],["brick","Brick"],["wallpaper","Wallpaper"],["green","Green"],["burgundy","Burgundy"],["navy","Navy"]]},
  {key:"floor",label:"Floor",type:"choice",opts:[["stone","Stone"],["wood","Wood boards"],["carpet","Carpet"],["checker","Checkerboard"]]},
  {key:"shelf",label:"Shelves",type:"choice",opts:[["walnut","Walnut"],["oak","Oak"],["black","Black"],["white","White"]]},
  {group:"Gallery pieces",key:"plinths",label:"Display plinths",type:"bool"},{key:"ropes",label:"Velvet ropes",type:"bool"},{key:"bench",label:"Museum bench",type:"bool"},{key:"posters",label:"Posters of your favourites",type:"bool"},{key:"mail",label:"New games arrive by mail",type:"bool",note:"From your next visit"},
  {group:"On the shelves",key:"fit",label:"Fit this shelf's console…",type:"fit",room:true},
  {key:"find",label:"Find magazines & guides…",type:"find",room:true},
  {key:"reseal",label:"Reseal everything you've opened",type:"reseal",room:true}];
const MUS_ROOM_DEF={walkIn:true,tv:false,mail:true,bright:1,warmth:0,spot:1,wall:"plaster",floor:"stone",shelf:"walnut",bench:false,plinths:false,ropes:false,posters:false,dust:1,timeOfDay:false,tvStand:"cart",tvSize:"m",tvPos:"right",tvCh:"ads",tvVol:1,tvMute:false,tvNear:false,tvCrt:.6,tvAnim:true};

function musStyle(){const ui=cfgInfo&&cfgInfo.config.ui||{};return Object.assign({},MUS_ROOM_DEF,ui.museumStyle||{},{light:MUS_SCENES.some(x=>x[0]===ui.museumLight)?ui.museumLight:"gallery"})}
function musLightNow(st){if(!st.timeOfDay)return st.light;const h=new Date().getHours();return h>=6&&h<11?"daylight":h>=11&&h<19?"gallery":"night"}
// what the 3D room itself takes (the rest is the shelf's: the walk-in, time of day, the TV's volume)
function musRoomKeys(st){const r={};Object.keys(MUS_ROOM_DEF).forEach(k=>{if(k!=="timeOfDay"&&k!=="walkIn"&&k!=="tvVol"&&k!=="tvMute")r[k]=st[k]});return r}
function musApplyStyle(st){if(!window.Museum||!Museum.setRoom)return;if(Museum.wake)Museum.wake(); /* (an idle handheld museum is asleep: wake it to show the change) */if(st.tv===true&&mode==="museum"&&api.tvScan)api.tvScan().catch(()=>{}); /* the TV just turned on: start finding ads */Museum.setLighting(musLightNow(st));Museum.setRoom(musRoomKeys(st));musTvVol()}
let musStyleSaveT=0;
function musSetOpt(key,val){if(!cfgInfo)return;const ui=cfgInfo.config.ui,was=musStyle();if(key==="light"){ui.museumLight=val;if(ui.museumStyle)ui.museumStyle.timeOfDay=false}else ui.museumStyle=Object.assign({},ui.museumStyle||{},{[key]:val});musApplyStyle(musStyle());
  // a TV choice shows on the TV: a look at the set for its stand / size / place (Museum.setRoom does that), the new channel
  // straight away if it's on
  if(key==="tvCh"&&val!==was.tvCh&&mode==="museum"&&Museum.tvPowered&&Museum.tvPowered())tvTune();
  if(mode==="museum"&&Museum.showTv&&/^tv(Crt|Anim|Vol|Mute|Near|Ch)$/.test(key))Museum.showTv();
  clearTimeout(musStyleSaveT);musStyleSaveT=setTimeout(()=>{musStyleSaveT=0;api.saveConfig(cfgInfo.config).catch(()=>{})},700)}
window.BoxshelfMuseumRoom={opts:MUS_ROOM,defaults:MUS_ROOM_DEF};
// the Room menu: a panel over the museum. Up/down picks a line, left/right changes it (A flips a switch), B or Start closes
let musRoom={open:false,i:0,fine:false},musResealAsk=0;
function musRoomEl(){let el=document.getElementById("musRoom");if(!el){el=document.createElement("aside");el.id="musRoom";el.className="mus-room";el.setAttribute("aria-label","Room");$("museum").appendChild(el)}return el}
// the lines shown now: Fine-tune's sliders only while it's open, the TV's own lines only with the TV in the room
function musRoomRows(st){return MUS_ROOM.filter(o=>(!o.fine||musRoom.fine)&&(!o.tv||st.tv))}
function musRoomRender(){const el=musRoomEl(),st=musStyle(),L=musRoomRows(st);musRoom.i=Math.max(0,Math.min(musRoom.i,L.length-1));el.innerHTML="";const h=document.createElement("div");h.className="mr-head";h.textContent="Room";el.appendChild(h);
  L.forEach((o,i)=>{if(o.group){const gh=document.createElement("div");gh.className="mr-group";gh.textContent=o.group;el.appendChild(gh)}const row=document.createElement("div");row.className="mr-row"+(i===musRoom.i?" on":"")+(o.type==="bool"?" bool":"")+(o.fine?" fine":"");const l=document.createElement("span");l.className="mr-lbl";l.textContent=o.label;const v=document.createElement("span");v.className="mr-val";
    // the mouse: a line is picked by clicking it; its arrows step it, a click elsewhere on it does what A does
    row.addEventListener("click",ev=>{setInput("pointer");musRoom.i=i;const a=ev.target.closest&&ev.target.closest("i");musRoomKey(a?(a.dataset.d==="-1"?"left":"right"):(o.type==="choice"||o.type==="range"?"right":"ok"))});
    if(o.type==="fold"){v.textContent=musRoom.fine?"Hide ‹":"Open ›";row.classList.add("fold");row.append(l,v);el.appendChild(row);return}
    if(o.type==="find"||o.type==="tvguide"){v.textContent="Open ›";row.append(l,v);el.appendChild(row);return}
    if(o.type==="reseal"){const n=window.Museum&&Museum.opened?Museum.opened():0;v.textContent=!n?"Nothing opened":musResealAsk&&Date.now()-musResealAsk<4000?"Press again to reseal":`${n} opened ›`;if(!n)row.classList.add("off");row.append(l,v);el.appendChild(row);return}
    if(o.type==="fit"){const f=window.Museum&&Museum.consoleFit&&Museum.consoleFit();v.textContent=f?"Open ›":"No 3D model here";if(!f)row.classList.add("off");row.append(l,v);el.appendChild(row);return}
    const val=st[o.key];v.textContent=o.type==="bool"?"":o.type==="range"?o.fmt(+val):((o.opts.find(x=>x[0]===val)||o.opts[0])[1]);
    if(o.type==="bool"){v.className="mr-val mr-sw"+(val?" on":"");v.setAttribute("aria-label",val?"On":"Off")}
    if(o.type==="range"){const bar=document.createElement("b");bar.className="mr-bar";bar.style.setProperty("--p",Math.round((+val-o.min)/(o.max-o.min)*100)+"%");v.prepend(bar)}
    if(o.type!=="bool"){const a=document.createElement("i");a.textContent="‹";a.dataset.d="-1";const b=document.createElement("i");b.textContent="›";b.dataset.d="1";v.prepend(a);v.append(b)}row.append(l,v);el.appendChild(row)});
  const dock=document.createElement("div");dock.className="mr-dock";const cur=L[musRoom.i];if(cur&&cur.note){const n=document.createElement("div");n.className="mr-note";n.textContent=cur.note;dock.appendChild(n)} // the note and the buttons stay at the bottom of the panel however far it scrolls
  // the buttons, in the pad's own pictures (Xbox, PlayStation or Nintendo, with the confirm swap) or the keys
  const f=document.createElement("div");f.className="mr-foot";const pad=inputMode==="pad",K=k=>`<span class="g">${k}</span>`;
  f.innerHTML=(pad?[[glyph("↕"),"Choose"],[glyph("↔"),"Change"],[glyph("x"),"Switch"],[glyph("o")+glyph("tri"),"Done"]]:[[K("↑")+K("↓"),"Choose"],[K("←")+K("→"),"Change"],[K("Enter"),"Switch"],[K("Esc")+K("Tab"),"Done"]]).map(([k,t])=>`<span class="mr-k">${k}<em>${t}</em></span>`).join("");dock.appendChild(f);el.appendChild(dock);
  const on=el.querySelector(".mr-row.on");if(on)on.scrollIntoView({block:"nearest"})}
function musRoomOpen(){if(mode!=="museum"||musHand().tv||tvS.guide||musHand().delivery||musHand().held||Museum.isHolding())return;musRoom.open=true;Museum.setRoomView&&Museum.setRoomView(true);musRoomEl().classList.add("on");musRoomRender();play_("panel")}
function musRoomClose(){musRoom.open=false;Museum.setRoomView&&Museum.setRoomView(false);const el=document.getElementById("musRoom");if(el)el.classList.remove("on");play_("back")}
function musRoomKey(k){const st=musStyle(),L=musRoomRows(st),o=L[Math.max(0,Math.min(musRoom.i,L.length-1))];if(!o)return;
  if(k==="up"){musRoom.i=(musRoom.i+L.length-1)%L.length;play_("tick",{dir:-1})}else if(k==="down"){musRoom.i=(musRoom.i+1)%L.length;play_("tick",{dir:1})}
  else if(k==="left"||k==="right"||k==="ok"){const d=k==="left"?-1:1;
    if(o.type==="fold"){const open=k==="left"?false:k==="right"?true:!musRoom.fine;if(open!==musRoom.fine){musRoom.fine=open;play_(open?"open":"back")}musRoomRender();return}
    if(o.type==="find"){if(k==="ok"||k==="right"){musRoomClose();museumFind("magazine")}return}
    if(o.type==="tvguide"){if(k==="ok"||k==="right")tvGuideGo();return}
    if(o.type==="reseal"){if(k!=="ok"&&k!=="right")return;const n=Museum.opened?Museum.opened():0;if(!n){play_("error");return}
      // two presses, so a stray button doesn't wrap your whole collection back up
      if(!musResealAsk||Date.now()-musResealAsk>4000){musResealAsk=Date.now();play_("tick",{dir:1});musRoomRender();setTimeout(()=>{if(musRoom.open)musRoomRender()},4100);return}
      musResealAsk=0;const done=Museum.resealAll();play_("open");rumble(.3,.5,160);toast(`Resealed ${done} item${done===1?"":"s"}: everything is back in its wrap`);musRoomRender();return}
    if(o.type==="fit"){if(k==="ok"||k==="right"){if(Museum.consoleFit&&Museum.consoleFit()){musRoomClose();Museum.toConsole();musFitOpen()}else{toast("Add a 3D model for this console in Settings → Customize");play_("error")}}return}
    else if(o.type==="bool")musSetOpt(o.key,!st[o.key]);
    else if(o.type==="range"){if(k==="ok")return;const v=Math.round(Math.max(o.min,Math.min(o.max,(+st[o.key])+d*o.step))*100)/100;if(v===+st[o.key])return;musSetOpt(o.key,v)}
    else{const ix=o.opts.findIndex(x=>x[0]===st[o.key]);musSetOpt(o.key,o.opts[(Math.max(0,ix)+d+o.opts.length)%o.opts.length][0])}play_(o.key==="light"?"lamp":"tick",{dir:d})}
  else if(k==="back")return musRoomClose();
  musRoomRender()}
/* the console fit panel: how your 3D console sits on its museum shelf. Opens from the console (X), the Room menu, or
   Settings → Customize → Fit on shelf. Every change shows live and is saved to themes\<system>\console.json */
let musFit={open:false,i:0},musFitPend={},musFitSaveT=0;
function musFitEl(){let el=document.getElementById("musFit");if(!el){el=document.createElement("aside");el.id="musFit";el.className="mus-room mus-fit";el.setAttribute("aria-label","Fit console");$("museum").appendChild(el)}return el}
function musFitLines(f){if(!f)return [];if(!f.ready)return [{k:"wait",label:"Loading the model…",val:""}];
  const L=[{k:"yaw",label:"Facing",val:`${f.yaw}°${f.yawSaved?"":" · automatic"}`,arrows:true},{k:"scale",label:"Size",val:Math.round(f.scale*100)+"%",arrows:true}];
  const opts=musFitCtlOpts(f),cur=opts.find(x=>x[0]===f.controller)||opts[0];L.push({k:"ctl",label:"Controller",val:cur[1],arrows:opts.length>1});
  if(f.controller!=="none")L.push({k:"ctlYaw",label:"Controller angle",val:f.controllerYaw==null?"automatic":f.controllerYaw+"°",arrows:true});
  f.parts.filter(p=>!p.main&&p.key!==f.controller).forEach(p=>L.push({k:"part:"+p.key,label:p.label,val:f.shown[p.key]==="show"?"Shown":"Hidden",bool:true}));
  L.push({k:"reset",label:"Back to automatic",val:""},{k:"done",label:"Done",val:""});return L}
function musFitCtlOpts(f){const o=[["none","None"]];f.parts.filter(p=>!p.main&&!/^(Disc|Cable|Stand|Memory card)/.test(p.label)).forEach(p=>o.push([p.key,p.label+(p.key===f.controllerGuess?" (from the model)":"")]));if(f.hasFile)o.push(["file","Your controller.glb"]);return o}
function musFitRender(){const el=musFitEl(),f=Museum.consoleFit&&Museum.consoleFit();el.innerHTML="";if(!f){musFitClose();return}
  const h=document.createElement("div");h.className="mr-head";h.textContent=`Fit: ${f.name} console`;el.appendChild(h);
  const L=musFitLines(f);musFit.i=Math.max(0,Math.min(musFit.i,L.length-1));
  L.forEach((o,i)=>{const row=document.createElement("div");row.className="mr-row"+(i===musFit.i?" on":"");const l=document.createElement("span");l.className="mr-lbl";l.textContent=o.label;const v=document.createElement("span");v.className="mr-val";v.textContent=o.val;
    if(o.arrows){const a=document.createElement("i");a.textContent="‹";const b=document.createElement("i");b.textContent="›";v.prepend(a);v.append(b)}row.append(l,v);el.appendChild(row)});
  const ft=document.createElement("div");ft.className="mr-foot";ft.textContent=(inputMode==="pad"?`D-pad: choose · left/right: change · ${btnWord("back")}: done`:"↑↓ choose · ←→ change · Esc: done")+` · saved in themes\\${f.id}\\console.json`;el.appendChild(ft);
  const on=el.querySelector(".mr-row.on");if(on)on.scrollIntoView({block:"nearest"})}
function musFitOpen(){if(mode!=="museum"||!Museum.consoleFit||!Museum.consoleFit())return false;if(musRoom.open)musRoomClose();musFit.open=true;musFit.i=0;musFitEl().classList.add("on");musFitRender();play_("panel");
  /* still loading the model: redraw once it's in (kept on musFit so closing / leaving the museum stops it, and a quick reopen never runs two) */clearInterval(musFit.w);let n=0;musFit.w=setInterval(()=>{let f=null;try{f=musFit.open&&Museum.consoleFit()}catch{}if(!f||f.ready||++n>40){clearInterval(musFit.w);musFit.w=0;if(f)musFitRender()}},250);return true}
function musFitClose(){clearInterval(musFit.w);musFit.w=0;if(!musFit.open)return;musFit.open=false;const el=document.getElementById("musFit");if(el)el.classList.remove("on");play_("back");renderMusHints()}
function musFitSet(patch){const f=Museum.setConsoleFit(patch);if(!f)return;const t=themes[f.id]||(themes[f.id]={});const mo=Object.assign({},t.modelOpts||{});Object.entries(patch).forEach(([k,v])=>{if(v==null)delete mo[k];else mo[k]=v});t.modelOpts=mo;
  Object.assign(musFitPend,patch);clearTimeout(musFitSaveT);const id=f.id;musFitSaveT=setTimeout(()=>{const p=musFitPend;musFitPend={};api.saveConsoleFit&&api.saveConsoleFit(id,p).catch(e=>toast("Couldn't save: "+e.message))},500);musFitRender()}
function musFitKey(k){const f=Museum.consoleFit&&Museum.consoleFit();if(!f){musFitClose();return}const L=musFitLines(f),o=L[musFit.i];
  if(k==="up"){musFit.i=(musFit.i+L.length-1)%L.length;play_("tick",{dir:-1});musFitRender();return}
  if(k==="down"){musFit.i=(musFit.i+1)%L.length;play_("tick",{dir:1});musFitRender();return}
  if(k==="back")return musFitClose();if(!o||o.k==="wait")return;
  const d=k==="left"?-1:1;
  if(o.k==="yaw")musFitSet({museumYaw:(((Math.round(f.yaw/15)*15+d*15)%360)+360)%360}); // 15° a press: straight on, a three-quarter view, a side… whatever shows it best
  else if(o.k==="scale"){if(k==="ok")return;musFitSet({museumScale:Math.round(Math.max(.5,Math.min(1.5,f.scale+d*.1))*100)/100})}
  else if(o.k==="ctl"){const op=musFitCtlOpts(f),ix=Math.max(0,op.findIndex(x=>x[0]===f.controller));musFitSet({controller:op[(ix+d+op.length)%op.length][0],controllerYaw:null})}
  else if(o.k==="ctlYaw"){const base=f.controllerYaw==null?0:f.controllerYaw;musFitSet({controllerYaw:(((base+d*45)%360)+360)%360})}
  else if(o.k.startsWith("part:")){const key=o.k.slice(5),parts=Object.assign({},f.shown);if(parts[key]==="show")delete parts[key];else parts[key]="show";musFitSet({parts})}
  else if(o.k==="reset"){if(k!=="ok")return;musFitSet({museumYaw:null,museumScale:null,controller:null,controllerYaw:null,parts:null});toast("Back to the automatic fit")}
  else if(o.k==="done"){if(k==="ok")musFitClose();return}
  play_("tick",{dir:d})}
// Settings → Customize → Fit on shelf: open the museum on that console's shelf with the panel up
function musFitAt(id){if(mode==="customize")closeCustomize();openMuseum(id);if(mode!=="museum")return;setTimeout(()=>{if(mode!=="museum")return;if(Museum.skipFill)Museum.skipFill();if(Museum.skipDelivery)Museum.skipDelivery(true);Museum.toConsole();if(!musFitOpen())toast("No 3D model on that shelf yet")},700)}
/* ---------- the reading corner: your Magazines\ and Guides\ (main/library.js) and a full-screen reader ---------- */
let libItems=[],libSig="",libCovers=new Map();
// the museum's movie shelves: your Stremio library (Boxshelf reads it from Stremio's own storage on this PC)
let movieItems=[],movieSig="";
const musLib=()=>libItems.concat(movieItems);
function movieRefresh(){if(!api.stremioLibrary)return Promise.resolve(movieItems);return api.stremioLibrary().then(l=>{const items=(Array.isArray(l)?l:[]).map(m=>({id:"stremio:"+m.id,sid:m.id,kind:"movie",type:m.type==="series"?"series":"movie",title:m.name,poster:m.poster||""})),sig=items.map(i=>i.id).join(",");if(sig!==movieSig){movieSig=sig;movieItems=items;if(mode==="museum"&&Museum.setLibrary)Museum.setLibrary(musLib())}return movieItems}).catch(()=>movieItems)}
function libRefresh(){movieRefresh();if(!api.library)return Promise.resolve(libItems);return api.library().then(l=>{const items=Array.isArray(l)?l:[],sig=items.map(i=>i.id).join(",");if(sig!==libSig){libSig=sig;libItems=items;libCovers.clear();if(mode==="museum"&&Museum.setLibrary)Museum.setLibrary(musLib())}return libItems}).catch(()=>libItems)}
function libCover(d){if(!libCovers.has(d.id))libCovers.set(d.id,(d.kind==="movie"?(api.stremioPoster?api.stremioPoster(d.sid,d.poster):Promise.resolve(null)):api.libraryCover(d.id)).then(u=>u||null,()=>{libCovers.delete(d.id);return null}));return libCovers.get(d.id)}
// the reader: one page at a time on a tall screen, the cover then two-page spreads on a wide one. Pages are drawn as
// they're needed (PDFs by pdf.js, at the size they're shown) and only a few are kept.
const rdr={open:false,item:null,src:null,doc:null,n:0,s:0,zoom:1,px:0,py:0,cache:new Map(),gen:0,dual:true};
function rdrEl(){let el=document.getElementById("libReader");if(!el){el=document.createElement("div");el.id="libReader";el.className="lib-reader";el.setAttribute("aria-hidden","true");
  el.innerHTML='<div class="lr-top"><div class="lr-title"></div><div class="lr-page"></div></div><div class="lr-stage"><div class="lr-pages"></div><div class="lr-wait">Loading…</div></div><nav class="hints lr-hints"></nav>';
  el.querySelector(".lr-stage").addEventListener("click",e=>{if(e.target.closest(".lr-hints"))return;rdrTurn(e.clientX<innerWidth/2?-1:1)});
  el.addEventListener("wheel",e=>{e.preventDefault();rdrZoom(e.deltaY<0?1:-1)},{passive:false});
  document.body.appendChild(el)}return el}
const rdrSpread=s=>!rdr.dual?[s,-1]:s===0?[-1,0]:[2*s-1,2*s<rdr.n?2*s:-1];
const rdrCount=()=>!rdr.dual?rdr.n:1+Math.ceil(Math.max(0,rdr.n-1)/2);
function rdrLabel(){const [L,R]=rdrSpread(rdr.s);if(!rdr.dual)return `Page ${rdr.s+1} of ${rdr.n}`;return L<0?`Cover · ${rdr.n} pages`:R<0?`Page ${L+1} of ${rdr.n}`:`Pages ${L+1}–${R+1} of ${rdr.n}`}
async function libOpen(d,insert){
  if(d&&d.kind==="movie"&&!insert){museumWatch(d);return}
  if(rdr.open)return;const el=rdrEl();rdr.open=true;rdr.item=d;rdr.s=0;rdr.zoom=1;rdr.px=rdr.py=0;rdr.n=0;rdr.cache.clear();const gen=++rdr.gen;
  el.querySelector(".lr-title").textContent=d.title+(d.kind==="movie"?" · Insert":d.series?` · ${d.series}`:"");el.querySelector(".lr-page").textContent="";el.querySelector(".lr-pages").innerHTML="";el.querySelector(".lr-wait").textContent="Opening…";el.classList.add("on","waiting");el.setAttribute("aria-hidden","false");
  rdrHints();play_("flip");Museum.setPaused&&Museum.setPaused(true,"reader");
  try{const src=d.kind==="movie"?{type:"images",urls:await movieInsert(d)}:await api.libraryPages(d.id);if(gen!==rdr.gen)return;rdr.src=src;
    if(src.type==="pdf"){await loadPdfJs();if(!window.pdfjsLib)throw new Error("PDF support didn't load");pdfjsLib.GlobalWorkerOptions.workerSrc="vendor/pdf.worker.min.js";const doc=await pdfjsLib.getDocument({url:src.url,isEvalSupported:false}).promise;if(gen!==rdr.gen){doc.destroy();return}rdr.doc=doc;rdr.n=doc.numPages}
    else{rdr.n=(src.urls||[]).length}
    if(!rdr.n)throw new Error("No pages found");
    rdrLayout();rdrShow()}
  catch(e){if(gen!==rdr.gen)return;el.querySelector(".lr-wait").textContent="Couldn't open this one: "+(e&&e.message||e);api.log&&api.log("reader: "+(e&&e.message))}}
function libClose(){if(!rdr.open)return;rdr.open=false;rdr.gen++;clearTimeout(rdrZoomT);if(rdr.anim)rdr.anim.forEach(x=>{try{x.finish()}catch{}});rdr.turning=false;rdr.queued=null;const el=rdrEl();el.classList.remove("on","waiting");el.setAttribute("aria-hidden","true");el.querySelector(".lr-pages").innerHTML="";rdr.cache.forEach(c=>{c.width=c.height=0});rdr.cache.clear();if(rdr.doc){try{rdr.doc.destroy()}catch{}rdr.doc=null}rdr.src=null;Museum.setPaused&&Museum.setPaused(false,"reader");play_("back");renderMusHints()}
function rdrLayout(){const was=rdrSpread(rdr.s)[0];rdr.dual=innerWidth/innerHeight>1.15;if(rdr.dual){rdr.s=was<=0?0:Math.ceil(was/2)}else rdr.s=Math.max(0,was)}
// one page drawn at height h (CSS px × devicePixelRatio, capped); kept by "index@height", the last 8 only
const rdrLoading=new Map();
function rdrPage(i,h){const key=i+"@"+h,gen=rdr.gen;if(rdr.cache.has(key)){const c=rdr.cache.get(key);rdr.cache.delete(key);rdr.cache.set(key,c);return Promise.resolve(c)}
  const lk=gen+":"+key;if(rdrLoading.has(lk))return rdrLoading.get(lk); // already on its way (riffling asked again)
  const p=rdrPage0(i,h,key,gen).finally(()=>rdrLoading.delete(lk));rdrLoading.set(lk,p);return p}
async function rdrPage0(i,h,key,gen){
  const c=document.createElement("canvas"),x=c.getContext("2d");
  if(rdr.src.type==="pdf"){const pg=await rdr.doc.getPage(i+1),v0=pg.getViewport({scale:1}),v=pg.getViewport({scale:h/v0.height});c.width=Math.round(v.width);c.height=Math.round(v.height);x.fillStyle="#fff";x.fillRect(0,0,c.width,c.height);await pg.render({canvasContext:x,viewport:v}).promise;pg.cleanup()}
  else{const im=new Image();im.decoding="async";{const u0=rdr.src.urls[i];im.src=/^(blob|data):/.test(u0)?u0:u0+(u0.includes("?")?"&":"?")+"t="+Math.min(4096,Math.max(64,Math.round(h/64)*64))} /* pages made here (a Blu-ray insert) are already at size */await im.decode();const k=h/im.naturalHeight;c.width=Math.round(im.naturalWidth*k);c.height=h;x.drawImage(im,0,0,c.width,c.height)}
  if(gen!==rdr.gen){c.width=c.height=0;throw new Error("closed")} // a page from the magazine you just closed: never into the new one's cache
  rdr.cache.set(key,c);for(const [k0,c0] of [...rdr.cache]){if(rdr.cache.size<=12)break;if(c0.isConnected||k0===key||rdr.turning&&rdr.keepNow&&rdr.keepNow.has(c0))continue;rdr.cache.delete(k0);c0.width=c0.height=0}return c} /* keeps the last 12; never empties a page that's on screen or about to be turned */
// The open magazine is a book: a left and a right page either side of the spine (one page on a tall screen). Turning a
// page is a real turn: the sheet lifts at its edge, swings over the spine and shows the next page on its back, with
// shade moving across it and onto the page underneath. Moving the pages is all transforms (the graphics card does it).
function rdrBook(){const box=rdrEl().querySelector(".lr-pages");let b=box.querySelector(".lr-book");if(!b){box.innerHTML='<div class="lr-book"><div class="lr-slot l"></div><div class="lr-slot r"></div><div class="lr-spine"></div></div>';b=box.firstChild}return b}
function rdrPut(slot,c){slot.textContent="";if(c){c.className="lr-canvas";slot.appendChild(c)}slot.classList.toggle("lr-empty",!c)}
// where the book sits: centred on the spine, or on the one page when there's only the cover / only a last left page
function rdrShift(L,R){return !rdr.dual?"0%":L<0?"-25%":R<0?"25%":"0%"}
function rdrPx(){const st=rdrEl().querySelector(".lr-stage").getBoundingClientRect();return Math.min(3600,Math.round(st.height*rdr.zoom*Math.min(2,devicePixelRatio||1)))}
async function rdrShow(){
  const el=rdrEl(),gen=rdr.gen,s=rdr.s,[L,R]=rdrSpread(s),h=rdrPx();
  el.querySelector(".lr-page").textContent=rdrLabel();
  try{const [cl,cr]=await Promise.all([L,R].map(i=>i>=0&&i<rdr.n?rdrPage(i,h).catch(()=>null):null));if(gen!==rdr.gen||s!==rdr.s||rdr.turning)return;if(!cl&&!cr)throw new Error("no page");
    const b=rdrBook(),c0=cr||cl;b.classList.toggle("lr-one",!rdr.dual);if(c0)b.style.aspectRatio=String((rdr.dual?2:1)*c0.width/c0.height);
    if(rdr.dual){rdrPut(b.querySelector(".lr-slot.l"),cl);rdrPut(b.querySelector(".lr-slot.r"),cr)}else{rdrPut(b.querySelector(".lr-slot.l"),cl);rdrPut(b.querySelector(".lr-slot.r"),null)}
    b.style.transform=`translateX(${rdrShift(L,R)})`;b.classList.toggle("lr-solo",!rdr.dual||L<0||R<0);el.classList.remove("waiting");rdrApply();rdrAhead(s,h)}
  catch(e){if(gen===rdr.gen){el.querySelector(".lr-wait").textContent="This page didn't load";el.classList.add("waiting")}}}
// the spreads either side, ready before you turn
function rdrAhead(s,h){[s+1,s-1,s+2].forEach(k=>{if(k<0||k>=rdrCount())return;rdrSpread(k).filter(i=>i>=0&&i<rdr.n&&!rdr.cache.has(i+"@"+h)).forEach(i=>rdrPage(i,h).catch(()=>{}))})}
function rdrApply(){const box=rdrEl().querySelector(".lr-pages");box.style.transform=`translate(${rdr.px}px,${rdr.py}px)`;box.style.height=(rdr.zoom*100)+"%"}
function rdrClampPan(){const st=rdrEl().querySelector(".lr-stage").getBoundingClientRect(),box=rdrEl().querySelector(".lr-pages").getBoundingClientRect(),mx=Math.max(0,(box.width-st.width)/2+40),my=Math.max(0,(st.height*rdr.zoom-st.height)/2+40);rdr.px=Math.max(-mx,Math.min(mx,rdr.px));rdr.py=Math.max(-my,Math.min(my,rdr.py))}
// d: +1 forward, -1 back. Asked again while a page is still moving: that one hurries to the end and the next follows
// quickly (hold the stick to riffle through).
function rdrTurn(d,fast){if(!rdr.n)return;if(rdr.turning){rdr.queued=d;if(rdr.anim)rdr.anim.forEach(a=>{try{a.playbackRate=4}catch{}});return}
  const ns=Math.max(0,Math.min(rdrCount()-1,rdr.s+d));if(ns===rdr.s){play_("error");return}
  rdr.px=rdr.py=0;rdrApply();
  const el=rdrEl(),b=el.querySelector(".lr-book");if(!b||!b.querySelector("canvas")){rdr.s=ns;rdrShow();play_("flip");return}
  rdrFlip(d,ns,fast).catch(()=>{rdr.turning=false;rdr.anim=null;rdr.keepNow=null;rdr.queued=null;if(rdr.open){rdr.s=ns;rdrShow()}})}
async function rdrFlip(d,ns,fast){
  rdr.turning=true;const gen=rdr.gen,h=rdrPx(),dual0=rdr.dual,el=rdrEl(),b=rdrBook(),[L0,R0]=rdrSpread(rdr.s),[L1,R1]=rdrSpread(ns);
  const pg=i=>i>=0&&i<rdr.n?rdrPage(i,h):Promise.resolve(null);
  // the turning sheet: front = the page you're turning, back = the page that lands on the other side
  const [front,back,under,keep]=await Promise.all(rdr.dual?(d>0?[pg(R0),pg(L1),pg(R1),pg(L0)]:[pg(L0),pg(R1),pg(L1),pg(R0)]):(d>0?[pg(L0),null,pg(L1),null]:[pg(L1),null,pg(L0),null]).map(p=>p||Promise.resolve(null)));
  if(gen!==rdr.gen){rdr.turning=false;return}
  if(rdr.dual!==dual0){rdr.turning=false;rdr.queued=null;rdrShow();return} // the screen changed shape meanwhile: just show the right spread
  rdr.keepNow=new Set([front,back,under,keep].filter(Boolean));
  rdr.s=ns;el.querySelector(".lr-page").textContent=rdrLabel();play_("flip");
  const dur=fast||rdr.queued!=null?240:620,ease="cubic-bezier(.36,.06,.24,1)";
  const sl=b.querySelector(".lr-slot.l"),sr=b.querySelector(".lr-slot.r");
  // what lies underneath while the sheet moves
  if(rdr.dual){if(d>0){rdrPut(sr,under);rdrPut(sl,keep)}else{rdrPut(sl,under);rdrPut(sr,keep)}}else rdrPut(sl,under);
  const sheet=document.createElement("div");sheet.className="lr-turn "+(rdr.dual?(d>0?"r":"l"):"one");
  sheet.innerHTML='<div class="lr-face f"><i class="lr-shade"></i></div><div class="lr-face b"><i class="lr-shade"></i></div>';
  const ff=sheet.querySelector(".f"),fb=sheet.querySelector(".b");if(front){front.className="lr-canvas";ff.prepend(front)}else ff.classList.add("blank");if(back){back.className="lr-canvas";fb.prepend(back)}else fb.classList.add("blank");
  const cast=document.createElement("i");cast.className="lr-cast "+(rdr.dual?(d>0?"r":"l"):"one");b.append(sheet,cast);
  // single page: back = the turning page swings in from the left over the one you were on
  const from=rdr.dual?0:(d>0?0:-180),to=rdr.dual?(d>0?-180:180):(d>0?-180:0);
  const rot=[{transform:`rotateY(${from}deg)`},{transform:`rotateY(${(from+to)/2}deg) translateZ(14px)`,offset:.5},{transform:`rotateY(${to}deg)`}];
  const anims=[sheet.animate(rot,{duration:dur,easing:ease,fill:"forwards"}),
    ff.querySelector(".lr-shade").animate([{opacity:0},{opacity:.55,offset:.48},{opacity:.55}],{duration:dur,easing:ease,fill:"forwards"}),
    fb.querySelector(".lr-shade").animate([{opacity:.6},{opacity:.6,offset:.52},{opacity:0}],{duration:dur,easing:ease,fill:"forwards"}),
    cast.animate([{opacity:0},{opacity:.85,offset:.45},{opacity:0}],{duration:dur,easing:ease,fill:"forwards"}),
    b.animate([{transform:getComputedStyle(b).transform==="none"?"translateX(0%)":b.style.transform||"translateX(0%)"},{transform:`translateX(${rdrShift(L1,R1)})`}],{duration:dur,easing:ease,fill:"forwards"})];
  rdr.anim=anims;
  await anims[0].finished.catch(()=>{});
  anims.forEach(a=>{try{a.cancel()}catch{}});sheet.remove();cast.remove();rdr.anim=null;rdr.turning=false;rdr.keepNow=null;
  if(gen!==rdr.gen)return;
  if(rdr.dual!==dual0||rdrPx()!==h){rdrShow();if(rdr.queued!=null){const q=rdr.queued;rdr.queued=null;rdrTurn(q,true)}return} // resized or zoomed during the turn: redraw for the screen as it is now
  // the book as it now lies
  const [cl,cr]=rdr.dual?(d>0?[back,under]:[under,back]):[under,null];b.style.transform=`translateX(${rdrShift(L1,R1)})`;b.classList.toggle("lr-solo",!rdr.dual||L1<0||R1<0);
  if(rdr.dual){rdrPut(sl,cl);rdrPut(sr,cr)}else rdrPut(sl,cl);
  rdrAhead(ns,h);
  if(rdr.queued!=null){const q=rdr.queued;rdr.queued=null;rdrTurn(q,true)}}
let rdrZoomT=0;function rdrZoom(d){const z=Math.max(1,Math.min(3,d>0?rdr.zoom*1.25:rdr.zoom/1.25));if(Math.abs(z-rdr.zoom)<.01)return;rdr.zoom=z<1.05?1:z;if(rdr.zoom===1)rdr.px=rdr.py=0;rdrClampPan();rdrApply();clearTimeout(rdrZoomT);rdrZoomT=setTimeout(rdrShow,180)/* sharper pages once you stop zooming */}
function rdrPan(dx,dy){if(rdr.zoom<=1&&!dx)return;rdr.px-=dx;rdr.py-=dy;rdrClampPan();rdrApply()}
function rdrHints(){const h=rdrEl().querySelector(".lr-hints"),pad=inputMode==="pad";h.innerHTML="";[[pad?["L"]:["←","→"],"Turn page"],[pad?["L2","R2"]:["+","−"],"Zoom"],[pad?["R"]:["W","A","S","D"],"Move"],[pad?["o"]:["Esc"],"Close"]].forEach(([keys,label])=>{const b=document.createElement("button");b.type="button";b.className="hint";b.innerHTML=`<span class="keys">${(pad?keys.map(glyph):keys.map(k=>`<span class="g">${k}</span>`)).join("")}</span><span>${label}</span>`;h.appendChild(b)})}
function rdrKey(e){const k=e.key;if(k==="ArrowLeft"||k==="PageUp")rdrTurn(-1);else if(k==="ArrowRight"||k==="PageDown"||k===" ")rdrTurn(1);else if(k==="+"||k==="="||k==="ArrowUp")rdrZoom(1);else if(k==="-"||k==="_"||k==="ArrowDown")rdrZoom(-1);else if(/^[wasdWASD]$/.test(k)){const q=k.toLowerCase();rdrPan(q==="a"?-80:q==="d"?80:0,q==="w"?-80:q==="s"?80:0)}else if(k==="Escape"||k==="Backspace"||k==="Enter")libClose();else return false;return true}
function rdrPad(gp,t,B,ax){const tv=b=>{const x=gp.buttons[b];return x?Math.max(x.value||0,x.pressed?1:0):0},tz=(tv(7)>.45?1:0)-(tv(6)>.45?1:0);if(tz&&(tz!==musHeld.lz||t-(musHeld.lzT||0)>200)){musHeld.lzT=t;rdrZoom(tz)}musHeld.lz=tz;
  const h=(B(15)||ax[0]>.6)?1:(B(14)||ax[0]<-.6)?-1:0;if(h&&h!==musHeld.lh){rdrTurn(h);musHeld.lhT=t}else if(h&&t-(musHeld.lhT||t)>420&&!rdr.turning){musHeld.lhT=t-180;rdrTurn(h,true)}musHeld.lh=h; // held: keep turning, quicker (riffling through)
  const [sx,sy]=stickDZ(ax[2]||0,ax[3]||0,.2);if(sx||sy)rdrPan(sx*22*padF,sy*22*padF);edge(gp,1,()=>libClose());edge(gp,0,()=>rdrTurn(1))}
addEventListener("resize",()=>{if(rdr.open&&rdr.n){rdrLayout();rdrShow()}});
setTimeout(()=>{libRefresh()},2500); /* the reading corner is ready before you first walk in */
/* find magazines & guides without leaving the museum: X in the reading corner. The Settings finder, in a panel here;
   the D-pad / stick walks the panel's buttons (left/up back, right/down on), A presses, B closes. Downloads land on the rack. */
let musFind={open:false,el:null};
addEventListener("bs-library-changed",()=>{if(mode==="museum")libRefresh()});
function museumFind(kind){if(mode!=="museum"||!window.BoxshelfIA)return;if(musRoom.open)musRoomClose();
  let el=document.getElementById("musFind");if(!el){el=document.createElement("aside");el.id="musFind";el.className="mus-find";el.innerHTML='<div class="mf-head"><div class="mf-title">Find magazines &amp; guides</div><button type="button" class="btn small mf-close">Close</button></div><div class="mf-body"></div>';el.querySelector(".mf-close").onclick=()=>musFindClose();$("museum").appendChild(el)}
  musFind.open=true;musFind.el=el;el.classList.add("on");BoxshelfIA.mount(el.querySelector(".mf-body"),kind);play_("panel");
  setTimeout(()=>{const f=el.querySelector(".ia-chips .chip")||el.querySelector("button");if(f)f.focus()},60);renderMusHints()}
function musFindClose(){if(!musFind.open)return;musFind.open=false;const el=musFind.el;if(el){el.classList.remove("on");BoxshelfIA.unmount(el.querySelector(".mf-body"))}play_("back");
  {const n=window.BoxshelfIA&&BoxshelfIA.running?BoxshelfIA.running():0;if(n)toast(`${n} download${n>1?"s":""} carry on: ${n>1?"they turn":"it turns"} up on the rack when done`)}libRefresh();renderMusHints()}
// Moving round the panel with a controller: up/down/left/right goes to the nearest thing in that direction (so down
// really goes down a row, not along it), holding repeats, the right stick scrolls, L1/R1 jump between sections.
function musFindFocusables(){const el=musFind.el;return el?[...el.querySelectorAll("button:not([disabled]),input")].filter(x=>x.offsetParent&&!x.closest(".mf-head")):[]}
function musFindSections(){const b=musFind.el&&musFind.el.querySelector(".mf-body");if(!b)return[];return [...b.children].map(c=>[...c.querySelectorAll("button:not([disabled]),input")].concat(c.matches("button:not([disabled]),input")?[c]:[]).filter(x=>x.offsetParent)).filter(l=>l.length)}
function spatialNext(list,cur,dir){const r=cur.getBoundingClientRect(),cx=r.left+r.width/2,cy=r.top+r.height/2;let best=null,bs=1e9;
  for(const x of list){if(x===cur)continue;const c=x.getBoundingClientRect();if(!c.width&&!c.height)continue;let main,side;
    if(dir==="down"||dir==="up"){main=dir==="down"?c.top-r.bottom:r.top-c.bottom;if(main<-r.height/2)continue;main=Math.max(0,main);side=Math.max(0,c.left-r.right,r.left-c.right);if(!side)side=Math.abs((c.left+c.width/2)-cx)*.05}
    else{main=dir==="right"?c.left-r.right:r.left-c.right;if(main<-r.width/2)continue;main=Math.max(0,main);if(c.bottom<=r.top+2||c.top>=r.bottom-2)continue;side=Math.abs((c.top+c.height/2)-cy)*.05} // left/right stays on the same row
    const sc=main+side*2.5;if(sc<bs){bs=sc;best=x}}
  return best}
function musFindFocus(x,d){x.focus({preventScroll:true});revealIn(x);play_("tick",{dir:d})}
function musFindStep(dir){const f=musFindFocusables();if(!f.length)return;let a=document.activeElement;
  const box=musFind.el.querySelector(".mf-body").getBoundingClientRect();
  // nothing focused, or it was scrolled out of sight with the right stick: start from what's on screen
  if(!f.includes(a)||(()=>{const r=a.getBoundingClientRect();return r.bottom<box.top||r.top>box.bottom})()){const mid=(box.top+box.bottom)/2;let b=f[0],bd=1e9;for(const x of f){const r=x.getBoundingClientRect(),dd=Math.abs((r.top+r.bottom)/2-mid);if(r.bottom>=box.top&&r.top<=box.bottom&&dd<bd){bd=dd;b=x}}musFindFocus(b,1);return}
  const n=spatialNext(f,a,dir);if(n)musFindFocus(n,dir==="left"||dir==="up"?-1:1);else play_("error")}
function musFindSection(d){const secs=musFindSections();if(!secs.length)return;const a=document.activeElement;let i=secs.findIndex(l=>l.includes(a));
  const j=i<0?0:Math.max(0,Math.min(secs.length-1,i+d));if(j===i){play_("error");return}musFindFocus(secs[j][0],d)}
function musFindScroll(dy){const b=musFind.el&&musFind.el.querySelector(".mf-body");if(b)b.scrollTop+=dy}
// B closes the finder's on-screen keyboard first; A on its search box (with a controller) opens that keyboard
function musFindKey(k){const IA=window.BoxshelfIA;if(k==="back"){if(IA&&IA.back&&IA.back()){play_("back");renderMusHints();return}return musFindClose()}
  if(k==="ok"){const a=document.activeElement;if(a&&musFind.el.contains(a)){if(a.tagName==="INPUT"){if(inputMode==="pad"&&IA&&IA.osk){IA.osk();play_("panel");renderMusHints();return}a.blur();return}a.click();if(IA&&IA.typing)setTimeout(renderMusHints,0)}return}musFindStep(k)}
let Shelf_setMusLight=null;
function museumData(){
  return allShelves.filter(sh=>(sh.kind==="console"||sh.kind==="steam")&&sh.items.some(isGame)).map(sh=>({id:sh.id,name:sh.name,style:STYLE[sh.id]||STYLE.media,
    games:sh.items.filter(isGame).map(g=>{const h=HIST[g.id],i=g.info||{};return {id:g.id,title:g.title,front:()=>{const u=g.art&&g.art.front;if(!u)return appFrontAsync(stOf(g),g);return /^bsfile:/.test(u)?u+(u.includes("?")?"&":"?")+"t=768":u} /* generated covers: a Promise, encoded off the main thread */,spine:g.art&&g.art.spine||null,year:yearOf(g)||null,
      dev:i.dev||null,pub:i.pub||null,genre:genreOf(g),rating:i.rating?+i.rating:null,metacritic:i.metacritic||null,group:groupOf(g),
      hours:h&&h.count?fmtPlay(h.ms)+" played":null,plays:h&&h.count||0,added:g.added||0,manual:!!(g.art&&g.art.manual&&g.art.manual.length),manualPage:g.art&&g.art.manual&&g.art.manual[0]||null,manualPages:g.art&&g.art.manual||[],back:g.art&&g.art.back?(/^bsfile:/.test(g.art.back)?g.art.back+(g.art.back.includes("?")?"&":"?")+"t=1024":g.art.back):null,disc:g.art&&g.art.disc||null,boxFull:!!(g.art&&g.art.front&&g.art.back&&g.art.spine),players:i.players||null,esrb:i.esrb||null,media:(MEDIA[sh.id]||{}).kind||"disc",mediaSize:(MEDIA[sh.id]||{}).size||0,last:h&&h.last?fmtDay(h.last/1000):null,fav:isFav(g),_g:g,_sh:sh}}),
    console:{model:themes[sh.id]&&themes[sh.id].model||BUILTIN_MODEL[sh.id]||null,opts:modelOpts(sh.id),controller:themes[sh.id]&&themes[sh.id].controller||null}}));
}
// a game's pictures for the museum's wall, as museumData gives them (Museum.refreshArt: new art while it's open)
function musArtOf(g){const a=g&&g.art||{};return {spine:a.spine||null,back:a.back?(/^bsfile:/.test(a.back)?a.back+(a.back.includes("?")?"&":"?")+"t=1024":a.back):null,disc:a.disc||null,boxFull:!!(a.front&&a.back&&a.spine)}}
let musOpenTok=0;
function openMuseum(at){uiCrumb("Museum");
  if(sysBusy()||(mode!=="browse"&&mode!=="details"))return;if(!window.Museum||!Museum.available()){toast("The museum needs WebGL, which isn't available here");return}
  if(!allShelves.some(sh=>(sh.kind==="console"||sh.kind==="steam")&&sh.items.some(isGame))){toast("Nothing on the shelves yet");return}
  closeDetails(true);musLaunch=null;
  // shadows follow the Performance level alone (Low turns them off). The old ui.museumShadows switch is no longer shown
  // in Settings, so it is deliberately ignored here: a hidden "off" left over from an older version can't be undone.
  Museum.setShadows(true);Museum.setLighting(musLightNow(musStyle()));musRoom.open=false; /* the scene it will settle on (time of day included), so opening doesn't cross-fade into it */
  const tOpen=performance.now();
  mode="museum";app.classList.add("museum-open");const el=$("museum");el.classList.add("on","filling");el.setAttribute("aria-hidden","false");
  // Start the fade to black first and let it get going (it then runs on the graphics side, where the work below can't
  // stall it); only then gather the shelves and build the room. Building it on the same frame as the fade began is what
  // made the very first fade into the museum hitch.
  const tok=++musOpenTok,want=at||(cur()&&cur().id);void el.offsetWidth;
  requestAnimationFrame(()=>requestAnimationFrame(()=>{
    if(tok!==musOpenTok||mode!=="museum")return;
    const data=museumData();
    const ri=Math.max(0,data.findIndex(r=>r.id===want));Museum.setRow(ri);museumSelect.first=true;
    Museum.setRoom(musRoomKeys(musStyle())); // the room as you left it, built that way (not rebuilt a moment later)
    const ok=Museum.open(Object.assign(data,{mail:musStyle().mail!==false,owner:RA.on&&RA.profile&&RA.profile.user||"",hist:HIST,favs:(cfgInfo&&cfgInfo.config.ui.favorites)||[],lib:musLib(),tv:musStyle().tv===true,walkIn:musStyle().walkIn!==false&&!reduced()}),{onTvEnded:err=>err?tvFailed():tvNext(1),onTvChannel:d=>tvNext(d),onTvBand:d=>tvBand(d),booklet:mg=>makeBooklet(mg._g),libCover,movieArt:d=>Promise.all([movieDisc(d).catch(()=>null),movieInsert(d).then(u=>u&&u[0]||null).catch(()=>null)]).then(([disc,inside])=>({disc,inside})),libBack:d=>d.kind==="movie"?movieBack(d).catch(()=>null):api.libraryCover(d.id,"back").catch(()=>null),onSelect:museumSelect,okWord:()=>okWord(),onSound:n=>play_(n),log:m=>api.log&&api.log(m),onShown:()=>{if(musBootSplash)musBootSplash();if(mode==="museum")play_("museum")},onReady:ms=>api.log&&api.log("museum open: build+shaders "+Math.round(performance.now()-tOpen)+" ms (shaders "+ms+" ms)")});
    if(!ok){el.classList.remove("on","filling");el.setAttribute("aria-hidden","true");museumTeardown();mode="browse";ambient(true);toast("Couldn't start the museum (WebGL)");return}
    renderMusHints();musApplyStyle(musStyle());libRefresh();if(api.onTvFound&&!tvS.hooked){tvS.hooked=true;api.onTvFound(()=>{if(mode==="museum"&&Museum.tvPowered()&&(tvCh()==="ads"||tvCh()==="mix")&&!tvS.list.length)tvStart()})}
    if(musGuideNext){musGuideNext=false;setTimeout(()=>{if(tok!==musOpenTok||mode!=="museum")return;if(Museum.isFilling())Museum.skipFill();tvGuideGo()},350)} // Settings → Museum → "Choose what's on"
    clearInterval(musFillT);musFillT=setInterval(()=>{if(!Museum.isFilling()){el.classList.remove("filling");clearInterval(musFillT)}},200);
  }));
  ambient(false); /* the opening chord plays when the room appears (onShown) */
  // the gallery's own quiet room tone (Settings → Sound → Museum room tone)
  if(window.Sound&&Sound.roomStart&&soundOn&&!(cfgInfo&&cfgInfo.config.ui.museumRoom===false))Sound.roomStart(1);
}
let musFillT=0,musGuideNext=false;
// the museum's screen state (shared by B and by hold-to-play from Inspect opened inside it, so neither leaves museum-open behind)
function museumTeardown(){clearInterval(musFillT);if(tvS.guide)tvGuideClose(true);tvS.tok++;tvFlush();tvS.bad.clear();tvS.list=[];clearInterval(tvS.slideT);tvS.slideT=0;try{Museum.tvOff&&Museum.tvOff()}catch{}if(rdr.open)libClose();if(musFind.open)musFindClose();if(musRoom.open)musRoomClose();if(musFit.open)musFitClose();if(window.Sound&&Sound.roomStop)Sound.roomStop();app.classList.remove("museum-open");const el=$("museum");el.classList.remove("on","filling");el.setAttribute("aria-hidden","true")}
function closeMuseum(){
  if(mode!=="museum")return;museumTeardown();
  setTimeout(()=>{if(mode!=="museum")Museum.close()},650);mode="browse";ambient(true);sfx.back();bsSelectSoon(); /* the bottom screen goes back to the shelf */
}
// the collection's catalogue: when a game arrived by mail (and its item number), written by the museum as you unbox it
function musAcquired(id){try{const m=JSON.parse(localStorage.getItem("museum-acquired")||"{}");return m&&m[String(id)]||null}catch{return null}}
function museumSelect(c){
  if(!c)return;bsSelectSoon(); /* the bottom screen follows the wall (Android only; a moment after it rests) */const r=c.row;$("musRow").textContent=r.name;
  if(c.wide&&c.wide.tv){$("musRow").textContent="Room";$("musTitle").textContent="Retro TV";$("musMeta").textContent=`${c.wide.sub} · ${okWord()} to ${c.wide.on?"watch":"turn it on"}`;if(!museumSelect.first)play_("tick",{dir:1});museumSelect.first=false;renderMusHints();return}
  if(c.wide){$("musRow").textContent=c.wide.rack?"Reading corner":"Shelf";$("musTitle").textContent=c.wide.label;$("musMeta").textContent=c.wide.sub+` · ${okWord()} to zoom in`;if(!museumSelect.first)play_("tick",{dir:1});museumSelect.first=false;renderMusHints();return}
  if(c.stack){const k=c.stack;$("musRow").textContent=k.guide?"Guide box":"Magazine box";$("musTitle").textContent=k.series;$("musMeta").textContent=[`${k.n} ${k.guide?"guides":"issues"}`,k.years,`${okWord()} to flip through`].filter(Boolean).join(" · ");if(!museumSelect.first)play_("tick",{dir:1});museumSelect.first=false;renderMusHints();return}
  if(c.lib&&c.fan){const d=c.lib,f=c.fan;$("musRow").textContent=f.series;$("musTitle").textContent=d.title;$("musMeta").textContent=[`${f.i+1} of ${f.n}`,d.type==="pdf"?"PDF":d.pages?d.pages+" pages":""].filter(Boolean).join(" · ");renderMusHints();return}
  if(c.lib&&c.lib.kind==="movie"){const d=c.lib;$("musRow").textContent=d.type==="series"?"TV show":"Movie";$("musTitle").textContent=d.title;$("musMeta").textContent=`In your Stremio library · ${okWord()} to pick it up`}
  else if(c.lib){const d=c.lib;$("musRow").textContent=d.kind==="guide"?"Strategy guide":"Magazine";$("musTitle").textContent=d.title;$("musMeta").textContent=[d.series,d.type==="pdf"?"PDF":d.pages?d.pages+" pages":""].filter(Boolean).join(" · ")}
  else if(c.tv){const m=c.tv.meta,ch=TV_BANDS.includes(c.tv.ch)?c.tv.ch:"ads",card=m?!!(m.trailer||m.recent):(ch==="trailers"||ch==="recent");$("musRow").textContent="Retro TV · "+TV_NAMES[ch];
    if(card){const tr=m?!!m.trailer:ch==="trailers";$("musTitle").textContent=m?m.game:tr?"No trailers yet":"Nothing played yet";$("musMeta").textContent=m?[c.tv.card&&c.tv.card.sub,tr?`${okWord()} to watch the trailer`:`${okWord()} to play it`].filter(Boolean).join(" · "):tr?"Trailers come with your games' LaunchBox details":"The games you play turn up here"}
    else{const none=tvS.loaded&&!tvS.list.length,m=none?null:c.tv.meta; /* (an ad that failed leaves its name behind) */$("musTitle").textContent=m?(m.game||m.title):(none?"Nothing on yet":"Tuning in…");$("musMeta").textContent=m?[m.game&&m.title!==m.game?m.title:"",m.year,"from the Internet Archive"].filter(Boolean).join(" · "):(none?"No ads found yet: Boxshelf is still looking in the background (the first time takes a while)":"")}
    museumSelect.first=false;renderMusHints();return}
  else if(c.delivery){if(musRoom.open)musRoomClose();const v=c.delivery,many=v.n>1,where=/ shelves$/.test(v.shelf)?`across ${v.shelf}`:`for your ${v.shelf} shelf`;$("musRow").textContent=many?"New arrivals":"New arrival";
    if(v.waiting){$("musTitle").textContent="Mail call";$("musMeta").textContent="Something has arrived for the collection…"}
    else if(v.open){$("musTitle").textContent="Unpacking…";$("musMeta").textContent=`${many?v.n+" new games":"A new game"} ${where} · ${okWord()} to skip`}
    else{$("musTitle").textContent=v.crate?"A crate has arrived":"A package has arrived";$("musMeta").textContent=`${many?v.n+" new games ":"A new game "}${where} · hold ${okWord()} to open it`}
    museumSelect.first=false;renderMusHints();return}
  else if(c.console){$("musTitle").textContent=`${r.name} console`;$("musMeta").textContent=`${r.games.length} games on this shelf · ${r.stats.hours}${r.stats.top?" · most played: "+r.stats.top:""}`}
  else{const g=c.game._g;const h=HIST[g.id];const aq=musAcquired(c.game.id);if(aq&&Date.now()-aq.at<7*864e5)$("musRow").textContent="New acquisition";$("musTitle").textContent=g.title;$("musMeta").textContent=[aq?`${aq.no?"Item #"+aq.no+" · ":""}acquired ${new Date(aq.at).toLocaleDateString(undefined,{day:"numeric",month:"short",year:"numeric"})}`:"",c.sealed?"Still sealed":"",g.region,h&&h.count?"played "+fmtDay(h.last/1000):"not played yet",isFav(g)?"♥ favorite":""].filter(Boolean).join(" · ")}
  // Android: at the held stick's top speed (a step every 60 ms, every 4th or 5th frame) only every other step ticks. Each tick builds a little
  // chain of sound nodes, and at 16 a second that was the biggest piece of work in a step (and they blur together anyway).
  const tNow=performance.now(),thin=ANDROID&&!c.console&&tNow-(museumSelect.tickAt||0)<85;
  if(!museumSelect.first&&!thin){play_(c.console?"pedestal":"tick",{dir:1});museumSelect.tickAt=tNow}museumSelect.first=false;renderMusHints();
}
// the buttons for each stage of a case in your hands
function musHandHints(pad){const st=musHand();
  if(st.reading)return [[pad?["L"]:["←","→"],`${st.pageLabel} · ${st.pages} pages`],[pad?["L2","R2"]:["↑","↓"],"Zoom"],[pad?["R"]:["W","S"],"Move page"],[pad?["sq"]:["X"],"Put manual back"],[pad?["o"]:["Esc"],"Done reading"]];
  if(st.discOut)return [[pad?["x"]:["Enter"],"Hold to play"],[pad?["R"]:["←","→"],"Turn"],[pad?["L2","R2"]:["↑","↓"],"Zoom"],[pad?["o"]:["Esc"],"Put disc back"]];
  if(st.open)return [[pad?["x"]:["Enter"],"Take disc out"],...(st.hasManual?[[pad?["sq"]:["X"],"Read manual"]]:[]),[pad?["R"]:["←","→"],"Turn"],[pad?["L2","R2"]:["↑","↓"],"Zoom"],[pad?["L"]:["W","A","S","D"],"Move"],[pad?["o"]:["Esc"],"Close case"]];
  return [[pad?["R"]:["←","→"],"Turn"],[pad?["L2","R2"]:["↑","↓"],"Zoom"],[pad?["L"]:["W","A","S","D"],"Move"],[pad?["x"]:["Enter"],st.wrapped?"Hold to unwrap":"Open case"],...(tvS.heldAd?[[pad?["tri"]:["T"],"Watch TV ad"]]:[]),[pad?["o"]:["Esc"],"Put back"]];}
function renderMusHints(){
  const h=$("musHints");const pad=inputMode==="pad";const held=Museum.isHolding(),c=Museum.current();
  tvRefreshHeld();
  if(tvS.guide)tvGuideChrome();
  const items=tvS.guide?[]/* the guide has its own prompts */:musHand().tv?(()=>{const ch=tvCh(),m=Museum.tvMeta();return [[pad?["↔"]:["←","→"],ch==="ads"?"Next ad":"Next"],[pad?["↕"]:["↑","↓"],"Channel"],...(m&&m.trailer?[[pad?["x"]:["Enter"],"Watch trailer"]]:tvOwned(m)?[[pad?["x"]:["Enter"],"Play this game"]]:[]),[pad?["tri"]:["T"],"TV guide"],[pad?["sq"]:["X"],"Turn off"],[pad?["o"]:["Esc"],"Back"]]})():musHand().opening?[[pad?["x","o"]:["Enter","Esc"],"Skip"]]:c&&c.delivery?(c.delivery.waiting?[[pad?["x"]:["Enter"],"Skip"]]:[[pad?["L"]:["←","→","↑","↓"],"Turn"],[pad?["x"]:["Enter"],"Hold to open"],[pad?["o"]:["Esc"],"Skip"]]):musFind.open?[[pad?["L"]:["←","→","↑","↓"],"Move"],[pad?["R"]:["PgUp","PgDn"],"Scroll"],[pad?["L1","R1"]:["Tab"],"Section"],[pad?["x"]:["Enter"],"Select"],[pad?["o"]:["Esc"],window.BoxshelfIA&&BoxshelfIA.typing&&BoxshelfIA.typing()?"Hide keyboard":"Close"]]:Museum.isWide&&Museum.isWide()?[[pad?["L"]:["←","→","↑","↓"],"Choose a section"],[pad?["x"]:["Enter"],c&&c.wide&&c.wide.empty?"Find some":"Zoom in"],...(c&&c.wide&&c.wide.rack&&c.wide.kind!=="movie"?[[pad?["sq"]:["X"],c.wide.empty?"Find some":"Find more"]]:[]),...(c&&c.wide&&c.wide.tv?[[pad?["tri"]:["T"],"TV guide"]]:[[pad?["tri"]:["L"],pad?"Room":"Lighting"]]),[pad?["o"]:["Esc"],"Back to shelf"]]:musHand().fan?[[pad?["L"]:["←","→"],"Flip"],[pad?["L1","R1"]:["Q","E"],musHand().fanYears?"Year":"Skip 10"],[pad?["x"]:["Enter"],musHand().libWrapped?"Hold to unwrap":"Read"],[pad?["o"]:["Esc"],"Put back"]]:musHand().libHeld?[[pad?["R"]:["←","→"],"Turn"],[pad?["L2","R2"]:["↑","↓"],"Zoom"],[pad?["x"]:["Enter"],musHand().libWrapped?"Hold to unwrap":musHand().libMovie?(musHand().movieDisc?"Watch in Stremio":musHand().movieOpen?"Take out the disc":"Open case"):"Read"],...(musHand().libMovie?[[pad?["sq"]:["X"],"Read insert"]]:[]),[pad?["o"]:["Esc"],musHand().movieDisc?"Put the disc back":musHand().movieOpen?"Close case":"Put back"]]:held&&Museum.isHoldingCase()?musHandHints(pad):held?[[pad?["R"]:["←","→"],"Turn"],[pad?["o"]:["Esc"],"Put down"]]:[[pad?["L"]:["←","→"],pad?"Move":"Case"],...(pad?[]:[[["↑","↓"],"Shelf"]]),[pad?["L1","R1"]:["Q","E"],"Skip 10"],...(pad?[]:[[["L"],"Lighting"]]),...(c&&c.lib&&c.lib.kind!=="movie"?[[pad?["sq"]:["X"],"Find more"]]:[]),[pad?["x"]:["Enter"],c&&c.stack?"Flip through":c&&c.parcel?"Hold to open":"Pick up"],...(c&&c.console&&Museum.consoleFit&&Museum.consoleFit()?[[pad?["sq"]:["X"],"Fit console"]]:[]),[pad?["tri"]:["Tab"],"Room"],[pad?["o"]:["Esc"],"Back to shelf"]];
  // the same prompts as last time (every case along a shelf has the same ones): left as they are. Rebuilding them on
  // each step of a fast scroll was a few ms of DOM and layout work per case.
  const sig=(pad?"p"+glyph("x"):"k")+JSON.stringify(items);/* (the glyph: a different pad's buttons draw differently) */if(sig===renderMusHints.sig&&h.childElementCount===items.length)return;renderMusHints.sig=sig;h.innerHTML="";
  items.forEach(([keys,label])=>{const b=document.createElement("button");b.type="button";b.className="hint";b.innerHTML=`<span class="keys">${(pad?keys.map(glyph):keys.map(k=>`<span class="g">${k}</span>`)).join("")}</span><span>${label}</span>`;if(label==="TV guide")b.addEventListener("click",()=>{setInput("pointer");museumY()}); /* the mouse's way in */h.appendChild(b)});
}
function museumActivate(){bsSelectSoon(); /* the bottom screen shows what's in your hands (picked up, opened…) */
  const r=Museum.activate();if(!r){museumSelect.first=true;museumSelect(Museum.current());return}
  if(r.held!=null){play_(r.held?"open":"back");rumble(.2,.4,100);renderMusHints();return}
  if(r.lifted){rumble(.1,.3,80);renderMusHints();setTimeout(renderMusHints,450);return}
  if(r.find){museumFind(r.find);return}
  if(r.libLift){rumble(.1,.3,80);renderMusHints();return}
  if(r.fan){rumble(.1,.3,80);museumSelect(Museum.current());return}
  if(r.skipped){play_("tick",{dir:1});renderMusHints();return}
  if(r.parcelTap){toast(`Hold ${okWord()} to open your package`);play_("tick",{dir:1});return}
  if(r.sealedLib){toast(`Still in its bag: hold ${okWord()} to open it`);play_("tick",{dir:1});return}
  if(r.tv){if(r.playing&&Museum.tvMeta()){museumSelect(Museum.current());return}tvTune();return} // left playing: carry on watching
  if(r.tvPlay){const tm=Museum.tvMeta();if(tm&&tm.trailer){play_("open");api.openTrailer(tm.video).then(x=>{if(x&&x.external)toast("Trailer opened in your browser")}).catch(()=>{});return}const o=tvOwned(tm);if(o){Museum.tvOff();museumPlay({_g:o.g,_sh:o.sh})}else{const m=Museum.tvMeta();toast(m&&m.console?"That's a console ad: no game to start":"That game isn't on your shelves");play_("error")}return}
  if(r.movieOpen||r.movieDisc){rumble(.1,.3,80);renderMusHints();return}
  if(r.lib){libOpen(r.lib);return}
  if(r.inHand){const s=musHand();if(s.reading||s.discOut)return;if(Museum.handX()){rumble(.08,.25,60);renderMusHints()}return}
}
// Hands-on in the museum: A picks a case up and A again opens it, then takes the disc out / puts it back; X reads the
// manual (or a Blu-ray's insert); Y is the TV guide / a held game's TV ad, else the Room menu; hold A with the disc out plays
const musHand=()=>window.Museum&&Museum.handState?Museum.handState():{};
const musReading=()=>!!musHand().reading;
function museumX(){const s=musHand();if(s.delivery)return;if(s.tv){tvS.tok++;clearInterval(tvS.slideT);tvS.slideT=0;if(tvS.guide)tvGuideClose();Museum.tvPower(false);Museum.back();play_("back");renderMusHints();return} // at the TV: switch it off and step back
  if(s.libHeld&&s.libMovie){const c=Museum.current();if(c&&c.lib&&c.lib.kind==="movie"){play_("flip");libOpen(c.lib,true)}return} // a Blu-ray in your hands: X reads its insert
  if(!s.held&&!Museum.isHolding()){const c=Museum.current();if(c&&((c.lib&&c.lib.kind==="movie")||(c.wide&&c.wide.kind==="movie"))){toast("Add movies and shows in Stremio: they turn up here");return}if(c&&(c.lib||(c.wide&&c.wide.rack))){museumFind(c.lib?c.lib.kind:c.wide.kind);return}if(c&&c.console){if(!musFitOpen()){toast("Add a 3D model for this console in Settings → Customize");play_("error")}}return}if(!s.held||!Museum.handY)return;if(!s.reading&&(!s.open||s.discOut))return;const r=Museum.handY();if(!r){if(s.open&&!s.hasManual)toast("No manual for this game yet");return}renderMusHints()}
// ---- the retro TV (Room menu → Retro TV): four channels, one playlist each; Y opens the TV guide ----
// tvS.list is the playlist of the channel that's on: ads (a video each, from the Internet Archive) and title cards (a
// game's trailer, or a game you played lately, shown for a few seconds). Shuffle all deals the three out in turn.
const tvS={list:[],i:0,tok:0,ch:"ads",heldAd:null,heldFor:null,guide:null,gi:0,loaded:false,ads:[],bad:new Set(),want:null,dirty:false,saveT:0};
// the TV's sound: Boxshelf's volume (shelf, quick menu, Settings) × the Room menu's TV volume, nothing when muted
const tvVol=()=>{const st=musStyle();return soundOn&&!st.tvMute?Math.max(0,Math.min(1,((cfgInfo&&cfgInfo.config.ui.volume)!=null?cfgInfo.config.ui.volume:.7)*.9*Math.max(0,Math.min(1,st.tvVol==null?1:+st.tvVol)))):0};
const tvOwned=a=>{if(!a||a.console||!a.rom)return null;const sh=allShelves.find(x=>x.id===a.sysId);const g=sh&&(sh.items||[]).find(x=>x.kind==="rom"&&String(x.file||"").toLowerCase()===a.rom);return g?{sh,g}:null};
async function tvLoad(){let l=[];try{l=await api.tvList()||[]}catch{}tvS.loaded=true;tvS.ads=Array.isArray(l)?l:[];return tvS.ads}
const tvMix=a=>a.map(x=>[Math.random(),x]).sort((p,q)=>p[0]-q[0]).map(x=>x[1]);
// the commercials: this shelf's games, your other games, then console ads; shuffled within each. An ad that wouldn't play
// is left out for the rest of the visit.
function tvAdList(l){const sys=((Museum.current()||{}).row||{}).id;l=l.filter(a=>a&&a.key&&!tvS.bad.has(a.key));return [].concat(...[l.filter(a=>!a.console&&a.sysId===sys),l.filter(a=>!a.console&&a.sysId!==sys),l.filter(a=>a.console&&a.sysId===sys),l.filter(a=>a.console&&a.sysId!==sys)].map(tvMix))}
async function tvProgram(ch){if(ch==="trailers")return tvTrailerCards();if(ch==="recent")return tvRecentCards();
  const ads=tvAdList(await tvLoad());if(ch==="ads")return ads;
  // Shuffle all: one from each channel in turn (in a random order each round), so it never runs long on one of them
  const src=[ads,tvTrailerCards(),tvRecentCards()].filter(x=>x.length),out=[];for(let k=0;out.length<150&&src.some(x=>k<x.length);k++)tvMix(src.filter(x=>k<x.length)).forEach(x=>out.push(x[k]));return out}
// the playlist starts on `first` (an entry's key, or an ad object that may not be listed), else where you were last time
function tvStart(first){tvTune(first)}
async function tvTune(first){clearInterval(tvS.slideT);tvS.slideT=0;const tok=++tvS.tok;if(!Museum.tvPowered())return;const ch=tvCh();
  if(first==null&&tvS.want)first=tvS.want.key;tvS.want=null;if(first==null)first=tvLastKey(ch);if(tvS.guide)tvGuideClose(true);
  if(ch==="ads"||ch==="mix"){Museum.tvStatic();museumSelect(Museum.current())} // static while the ads are listed
  const L=await tvProgram(ch);if(tok!==tvS.tok||!Museum.tvPowered()||tvCh()!==ch)return;
  let i=0;if(first!=null){const fk=typeof first==="object"?first.key:first;i=L.findIndex(e=>e.key===fk);if(i<0){const e=typeof first==="object"?first:tvEntry(fk);if(e&&!tvS.bad.has(fk))L.unshift(e);i=0}}
  tvS.list=L;tvS.i=i;tvS.ch=ch;tvPlayCur();
  // a title card stays up for a few seconds (not while the guide is open over it); a video moves on when it ends
  tvS.slideT=setInterval(()=>{if(mode!=="museum"||!Museum.tvPowered()){clearInterval(tvS.slideT);tvS.slideT=0;return}const e=tvS.list[tvS.i];if(e&&e.card&&!tvS.guide&&performance.now()-tvS.slideAt>=(e.meta.trailer?9000:6000))tvNext(1,true)},1000)}
async function tvPlayCur(){const tok=++tvS.tok;if(!Museum.tvPowered())return;const L=tvS.list;if(!L.length){Museum.tvStatic();museumSelect(Museum.current());renderMusHints();tvGuideMarks();return}
  if(!(tvS.i>=0&&tvS.i<L.length))tvS.i=0;const e=L[tvS.i];tvRemember(e.key);
  if(e.card){tvS.slideAt=performance.now();Museum.tvCard(Object.assign({},e.card,{n:`${tvS.i+1} / ${L.length}`}),e.meta);renderMusHints();tvGuideMarks();return}
  const ad=e;Museum.tvStatic();museumSelect(Museum.current());let url=null;try{url=await api.tvLocal(ad)}catch{}
  if(tok!==tvS.tok||!Museum.tvPowered())return;
  if(!url){api.log&&api.log("TV: couldn't fetch "+ad.title);tvDrop(ad);return tvPlayCur()}
  Museum.tvShow(url,{title:ad.title,year:ad.year,game:ad.game||"",sysId:ad.sysId,rom:ad.rom,console:!!ad.console,key:ad.key},tvVol());renderMusHints();tvGuideMarks()}
// an entry by its key, from any channel (the guide lists more than a channel's playlist holds)
function tvEntry(key){if(!key)return null;return tvS.ads.find(a=>a&&a.key===key)||(/^tr:/.test(key)?tvTrailerAll():/^rp:/.test(key)?tvRecentCards(60):[]).find(e=>e.key===key)||null}
function tvDrop(e){tvS.bad.add(e.key);const k=tvS.list.indexOf(e);if(k>=0){tvS.list.splice(k,1);if(k<tvS.i)tvS.i--}if(tvS.i>=tvS.list.length)tvS.i=0}
// a video that fails to play is dropped for this visit and the next one comes on (it used to go straight to "the next ad",
// which with a one-ad list was the same broken file again, in a loop)
function tvFailed(){const e=tvS.list[tvS.i],m=Museum.tvMeta();if(!e||e.card||(m&&m.key&&m.key!==e.key))return;api.log&&api.log("TV: couldn't play "+e.title);tvDrop(e);tvPlayCur()}
function tvNext(d,auto){if(!Museum.tvPowered()||!tvS.list.length)return;tvS.i=(tvS.i+d+tvS.list.length)%tvS.list.length;if(!auto){play_("tick",{dir:d});tvSaveSoon()}tvPlayCur()}
// what was on last, per channel (ui.tvLast): kept in memory as it changes, written when you pick something and when you leave
function tvLastKey(ch){const t=cfgInfo&&cfgInfo.config.ui.tvLast;return t&&typeof t[ch]==="string"?t[ch]:null}
function tvRemember(key){if(!cfgInfo||!key)return;const ui=cfgInfo.config.ui;if(ui.tvLast&&ui.tvLast[tvS.ch]===key)return;ui.tvLast=Object.assign({},ui.tvLast,{[tvS.ch]:key});tvS.dirty=true}
function tvSaveSoon(){clearTimeout(tvS.saveT);tvS.saveT=setTimeout(tvFlush,1500)}
function tvFlush(){clearTimeout(tvS.saveT);tvS.saveT=0;if(!tvS.dirty||!cfgInfo)return;tvS.dirty=false;api.saveConfig(cfgInfo.config).catch(()=>{})}
// the channels: vintage commercials (the playlist above), game trailers (your games that have one: a title card, A opens the
// trailer), recently played (a slideshow of covers from your play history) and Shuffle all. Up / down at the TV, the Room
// menu or the guide's tabs.
const TV_BANDS=["ads","trailers","recent","mix"],TV_NAMES={ads:"Vintage commercials",trailers:"Game trailers",recent:"Recently played",mix:"Shuffle all"};
function tvCh(){const c=musStyle().tvCh;return TV_BANDS.includes(c)?c:"ads"}
function tvBand(d){const i=TV_BANDS.indexOf(tvCh());musSetOpt("tvCh",TV_BANDS[(i+d+TV_BANDS.length)%TV_BANDS.length]);play_("tick",{dir:d})}

const tvCover=g=>{const u=g.art&&g.art.front;return typeof u==="string"&&u?(/^bsfile:/.test(u)?u+(u.includes("?")?"&":"?")+"t=512":u):null};
// every game with a trailer, shelf by shelf (the guide lists them all; the channel shuffles them, this shelf's first)
function tvTrailerAll(){const L=[];allShelves.forEach(sh=>{if(sh.kind!=="console")return;(sh.items||[]).forEach(g=>{if(isGame(g)&&g.info&&g.info.video){const key="tr:"+sh.id+"|"+String(g.file||g.id||"").toLowerCase(),y=yearOf(g);
  L.push({key,sh,g,card:{kind:"trailer",title:g.title,sub:[sh.name,y].filter(Boolean).join(" · "),line:"Trailer · "+okWord()+" to watch",img:tvCover(g)},meta:{key,game:g.title,title:g.title+" trailer",sysId:sh.id,rom:String(g.file||"").toLowerCase(),video:g.info.video,trailer:true,year:y}})}})});return L}
function tvTrailerCards(){const sys=((Museum.current()||{}).row||{}).id,L=tvTrailerAll();return [...tvMix(L.filter(x=>x.sh.id===sys)),...tvMix(L.filter(x=>x.sh.id!==sys))].slice(0,60)}
function tvRecentCards(n){const L=[],seen=new Set();allShelves.forEach(sh=>{if(sh.kind!=="console"&&sh.kind!=="steam")return;(sh.items||[]).forEach(g=>{const h=isGame(g)&&HIST[g.id];if(h&&h.count&&h.last&&!seen.has(g.id)){seen.add(g.id);L.push({sh,g,h})}})});
  L.sort((a,b)=>b.h.last-a.h.last);return L.slice(0,n||20).map(({sh,g,h})=>{const key="rp:"+g.id,line=["Played "+fmtDay(h.last/1000),h.ms?fmtPlay(h.ms):"",h.count>1?h.count+" sessions":""].filter(Boolean).join(" · ");return {key,sh,g,card:{kind:"recent",title:g.title,sub:sh.name,line,img:tvCover(g)},meta:{key,game:g.title,title:g.title,sysId:sh.id,rom:String(g.file||"").toLowerCase(),recent:true,line}}})}
// a game in your hands: does it have an ad? (asked once per game, shown as Y)
function tvRefreshHeld(){const g=musHand().game,key=g&&g._g&&g._sh?g._sh.id+"|"+g._g.file:null;if(key===tvS.heldFor)return;tvS.heldFor=key;tvS.heldAd=null;if(!key||!musStyle().tv||!api.tvAdFor)return;api.tvAdFor(g._sh.id,g._g.file).then(a=>{if(tvS.heldFor===key){tvS.heldAd=a||null;renderMusHints()}}).catch(()=>{})}
// ---- the TV guide: a tab per channel, each listing what's on it by console with a small cover. A puts it on, X (F) keeps
// it as a favourite (they head every tab), LB / RB (Q / E) change tab, B or Y closes. Opened by Y at the TV (or with the TV
// picked in the overview), the Room menu's "What's on TV…" and Settings → Museum → "Choose what's on".
const TV_TABS=[["ads","Commercials"],["trailers","Trailers"],["recent","Recently played"],["mix","Shuffle all"]];
const tvFavs=()=>{const f=cfgInfo&&cfgInfo.config.ui.tvFavs;return Array.isArray(f)?f:[]};
const tvSelWide=()=>{const c=window.Museum&&Museum.current&&Museum.current();return !!(c&&c.wide&&c.wide.tv)};
// the rows of one tab: [{name, items:[{key, ch, title, sub, img, ph, meta}]}], favourites first
function tvGuideData(tab){const order=id=>{const k=allShelves.findIndex(x=>x.id===id);return k<0?999:k},nameOf=id=>{const sh=allShelves.find(x=>x.id===id);return (sh&&sh.name)||String(id||"").toUpperCase()};
  const ad=a=>{const o=tvOwned(a);return {key:a.key,ch:"ads",sys:a.sysId,title:a.console?a.title:(a.game||a.title),sub:[a.console?"Console ad":"Commercial",a.year].filter(Boolean).join(" · "),img:o?tvCover(o.g):null,ph:a.console?"AD":"",ad:a}};
  const card=e=>({key:e.key,ch:e.meta.trailer?"trailers":"recent",sys:e.sh.id,title:e.g.title,sub:e.meta.trailer?["Trailer",e.meta.year].filter(Boolean).join(" · "):e.meta.line,img:e.card.img,meta:e.meta});
  const ads=()=>tvS.ads.filter(a=>a&&a.key&&!tvS.bad.has(a.key)).map(ad).sort((p,q)=>(p.ad.console?1:0)-(q.ad.console?1:0)||String(p.title).localeCompare(String(q.title)));
  const trs=()=>tvTrailerAll().map(card).sort((p,q)=>String(p.title).localeCompare(String(q.title))),rec=()=>tvRecentCards(60).map(card);
  const all=tab==="ads"?ads():tab==="trailers"?trs():tab==="recent"?rec():[...ads(),...trs(),...rec()];
  // by console: shelf order (Recently played: the console you played most recently first, as its list is)
  const by=new Map();all.forEach(it=>{if(!by.has(it.sys))by.set(it.sys,[]);by.get(it.sys).push(it)});let ids=[...by.keys()];if(tab!=="recent")ids.sort((a,b)=>order(a)-order(b));
  const out=[];if(tab==="mix")out.push({name:"",items:[{key:"__mix",ch:"mix",title:"Shuffle everything",sub:"Commercials, trailers and what you played lately, mixed",ph:"⤨"}]});
  const fv=new Set(tvFavs()),favs=all.filter(it=>fv.has(it.key));if(favs.length)out.push({name:"★ Favourites",items:favs});
  ids.forEach(id=>out.push({name:nameOf(id),items:by.get(id)}));return out}
function tvGuideOpen(tab){if(tvS.guide){tvGuideClose();return}if(mode!=="museum")return;if(musRoom.open)musRoomClose();
  const el=document.createElement("div");el.className="tv-guide";el.setAttribute("role","dialog");el.setAttribute("aria-label","TV guide");
  el.innerHTML='<div class="tg-head"><div class="tg-title"><b>TV Guide</b><span class="tg-nowline"></span></div><button type="button" class="tg-x" aria-label="Close the guide">✕</button></div><div class="tg-tabs" role="tablist"></div><div class="tg-list"></div><div class="tg-dock"></div>';
  el.querySelector(".tg-x").addEventListener("click",()=>{setInput("pointer");tvGuideClose()});
  $("museum").appendChild(el);tvS.guide={el,items:[],tab:TV_BANDS.includes(tab)?tab:tvCh(),sig:""};Museum.setGuideView&&Museum.setGuideView(true);
  tvGuideRender(true);requestAnimationFrame(()=>{if(tvS.guide&&tvS.guide.el===el)el.classList.add("on")});play_("panel");renderMusHints();
  // the ads list can grow while you're away (they're found in the background): read it again, keeping your place
  tvLoad().then(()=>{const G=tvS.guide;if(G&&G.el===el&&(G.tab==="ads"||G.tab==="mix"))tvGuideRender(false)})}
function tvGuideRender(toNow){const G=tvS.guide;if(!G)return;const el=G.el,keep=!toNow&&G.items[tvS.gi]?G.items[tvS.gi].key:null,L=el.querySelector(".tg-list"),fv=new Set(tvFavs());
  L.innerHTML="";G.items=[];G.sig="";
  tvGuideData(G.tab).forEach(gr=>{if(gr.name){const h=document.createElement("div");h.className="tg-sys";h.textContent=gr.name;L.appendChild(h)}
    gr.items.forEach(it=>{const k=G.items.length,r=document.createElement("div");r.className="tg-row"+(fv.has(it.key)?" fav":"");r.dataset.i=k;r.setAttribute("role","option");
      r.innerHTML='<span class="tg-art"></span><span class="tg-txt"><b></b><i></i></span><span class="tg-now">On now</span><button type="button" class="tg-star" aria-label="Favourite">★</button>';
      const art=r.firstChild;if(it.img){const im=document.createElement("img");im.loading="lazy";im.decoding="async";im.alt="";im.src=thumb(it.img,64);im.onerror=()=>{im.remove();art.textContent=(it.title||"?").slice(0,1)};art.appendChild(im)}else{art.textContent=it.ph||(it.title||"?").slice(0,1);art.classList.add("ph")}
      r.querySelector("b").textContent=it.title||"";r.querySelector("i").textContent=it.sub||"";if(it.key==="__mix")r.querySelector(".tg-star").remove();
      r.addEventListener("click",ev=>{setInput("pointer");tvS.gi=k;G.moved=true;if(ev.target.closest(".tg-star")){tvGuidePaint();tvGuideFav();return}tvGuidePick()});
      L.appendChild(r);G.items.push(it)})});
  if(!G.items.length){const e=document.createElement("div");e.className="tg-empty";e.textContent=G.tab==="ads"?(tvS.loaded?"No commercials found yet. Boxshelf is still looking for them in the background (the first time takes a while).":"Looking for commercials…"):G.tab==="trailers"?"No trailers yet. They come with your games' LaunchBox details.":"Nothing played yet. The games you play turn up here.";L.appendChild(e)}
  // where the guide opens: on what's on now (when it's in this tab), else the top; a refresh keeps your place
  const now=(Museum.tvMeta()||{}).key;let i=keep?G.items.findIndex(x=>x.key===keep):-1;if(i<0&&now)i=G.items.findIndex(x=>x.key===now);tvS.gi=Math.max(0,i);
  if(toNow)L.scrollTop=0;tvGuideMarks();tvGuidePaint()}
// the tabs and the button prompts: only redrawn when the pad style, the tab or the picked row's kind change (renderMusHints
// calls this often, and a button rebuilt under the mouse would lose the click)
function tvGuideChrome(){const G=tvS.guide;if(!G)return;const pad=inputMode==="pad",it=G.items[tvS.gi],what=!it?"":it.key==="__mix"?"Shuffle":it.ch==="trailers"?"Watch trailer":it.ch==="recent"?"Put it on":"Watch",sig=[pad,glyphStyle(),PADMAP.swap,G.tab,what].join("|");if(sig===G.sig)return;G.sig=sig;
  const K=k=>`<span class="g">${k}</span>`,tabs=G.el.querySelector(".tg-tabs");
  tabs.innerHTML=`<span class="tg-sh">${pad?glyph("L1"):K("Q")}</span>`+TV_TABS.map(([id,n])=>`<button type="button" role="tab" class="tg-tab${id===G.tab?" on":""}" data-t="${id}" aria-selected="${id===G.tab}">${n}</button>`).join("")+`<span class="tg-sh">${pad?glyph("R1"):K("E")}</span>`;
  tabs.querySelectorAll(".tg-tab").forEach(b=>b.addEventListener("click",()=>{setInput("pointer");tvGuideTab(b.dataset.t)}));
  const P=pad?[[glyph("↕"),"Choose"],[glyph("x"),what],[glyph("sq"),"Favourite"],[glyph("L1")+glyph("R1"),"Channel"],[glyph("o")+glyph("tri"),"Close"]]:[[K("↑")+K("↓"),"Choose"],[K("Enter"),what],[K("F"),"Favourite"],[K("Q")+K("E"),"Channel"],[K("Esc"),"Close"]];
  G.el.querySelector(".tg-dock").innerHTML=P.filter(p=>p[1]).map(([k,t])=>`<span class="mr-k">${k}<em>${t}</em></span>`).join("")}
function tvGuidePaint(){const G=tvS.guide;if(!G)return;const rows=G.el.querySelectorAll(".tg-row");rows.forEach((r,k)=>r.classList.toggle("on",k===tvS.gi));const on=rows[tvS.gi];
  if(on){const h=on.previousElementSibling;if(h&&h.classList.contains("tg-sys"))h.scrollIntoView({block:"nearest"});on.scrollIntoView({block:"nearest"})}tvGuideChrome()}
// "On now" on the row of what's playing (and the line under the title)
function tvGuideMarks(){const G=tvS.guide;if(!G)return;const m=tvS.list.length?Museum.tvMeta():null,now=m&&m.key;G.el.querySelectorAll(".tg-row").forEach((r,k)=>{const it=G.items[k];r.classList.toggle("now",!!(now&&it&&it.key===now))});
  if(now&&!G.moved){const i=G.items.findIndex(x=>x.key===now);if(i>=0&&i!==tvS.gi){tvS.gi=i;tvGuidePaint()}} // until you move, the guide follows what's on
  G.el.querySelector(".tg-nowline").textContent=m?`On now: ${m.game||m.title} · ${TV_NAMES[tvS.ch]||""}`:Museum.tvPowered()?"Tuning in…":""}
function tvGuideMove(d){const G=tvS.guide;if(!G||!G.items.length)return;const n=Math.max(0,Math.min(G.items.length-1,tvS.gi+d));if(n===tvS.gi)return;tvS.gi=n;G.moved=true;tvGuidePaint();play_("tick",{dir:d})}
function tvGuideTab(t){const G=tvS.guide;if(!G)return;const i=TV_BANDS.indexOf(G.tab),n=typeof t==="number"?TV_BANDS[(i+t+TV_BANDS.length)%TV_BANDS.length]:t;if(!TV_BANDS.includes(n)||n===G.tab)return;G.tab=n;G.moved=false;tvGuideRender(true);play_("tick",{dir:typeof t==="number"?t:1})}
function tvGuideFav(){const G=tvS.guide;if(!G||!cfgInfo)return;const it=G.items[tvS.gi];if(!it||it.key==="__mix")return;const ui=cfgInfo.config.ui,f=tvFavs().filter(k=>k!==it.key),on=f.length===tvFavs().length;
  if(on)f.unshift(it.key);ui.tvFavs=f.slice(0,500);tvS.dirty=true;tvSaveSoon();play_(on?"open":"tick",{dir:1});
  // marked in place (the Favourites group is redrawn next time the tab opens, so the list doesn't jump under you)
  G.el.querySelectorAll(".tg-row").forEach((r,k)=>{if(G.items[k]&&G.items[k].key===it.key)r.classList.toggle("fav",on)})}
// A: on it goes, straight away (a trailer opens in the trailer window too, as A does on the trailers channel)
function tvGuidePick(){const G=tvS.guide;if(!G)return;const it=G.items[tvS.gi];if(!it){play_("error");return}
  const ch=G.tab==="mix"||it.key==="__mix"?"mix":it.ch;tvGuideClose(true);play_("open");if(!musHand().tv&&!Museum.watchTv(true))return;
  tvS.want={key:it.key==="__mix"?null:it.key};if(tvCh()!==ch)musSetOpt("tvCh",ch);if(tvS.want)tvTune();tvS.dirty=true;tvSaveSoon();
  if(it.ch==="trailers"&&it.meta&&it.meta.video)api.openTrailer(it.meta.video).then(x=>{if(x&&x.external)toast("Trailer opened in your browser")}).catch(()=>{})}
function tvGuideClose(quiet){const G=tvS.guide;if(!G)return;tvS.guide=null;tvS.slideAt=performance.now();Museum.setGuideView&&Museum.setGuideView(false);G.el.classList.remove("on");G.el.style.pointerEvents="none";setTimeout(()=>G.el.remove(),220);if(!quiet)play_("back");renderMusHints()}
// the guide from anywhere in the museum: switches the TV on if needed, walks over to it, then opens
function tvGuideGo(tab){if(mode!=="museum"||musHand().delivery||rdr.open||musFind.open||musFit.open)return false;if(musRoom.open)musRoomClose();
  if(!musStyle().tv)musSetOpt("tv",true);if(!Museum.tvAvail()){toast("Turn the Retro TV on in the Room menu first");play_("error");return false}
  if(!musHand().tv){if(tvSelWide())museumActivate();else{if(!Museum.watchTv(true))return false;if(!Museum.tvMeta())tvTune()}}
  tvGuideOpen(tab);return true}
function museumY(){
  if(tvS.guide){tvGuideClose();return}
  if(musHand().tv||tvSelWide()&&!musHand().held&&!Museum.isHolding()){tvGuideGo();return} // at the TV (or with it picked in the overview): the guide
  if(musHand().held&&tvS.heldAd){const a=tvS.heldAd;if(tvCh()!=="ads")musSetOpt("tvCh","ads"); /* its ad is on the commercials channel */if(Museum.watchTv()){musHoldEnd&&musHoldEnd();tvStart(a)}return} // a game in your hands: its ad
  if(musHand().delivery||musHand().held||Museum.isHolding())return;musRoom.open?musRoomClose():musRoomOpen()}
let musHintT=0;function renderMusHintsSoon(){if(musHintT)return;musHintT=setTimeout(()=>{musHintT=0;renderMusHints()},120)} // at most every 120 ms (a pad calls this every frame; the old reset-the-timer version never got to draw)
let musHoldT=null,musHoldStart=0;
function musHoldBar(){let el=document.getElementById("musHold");if(!el){el=document.createElement("div");el.id="musHold";el.className="isp-hold mus-hold";el.innerHTML='<div class="isp-hold-bar" id="musHoldBar"></div><span>Starting…</span>';$("museum").appendChild(el)}return el}
const musHoldable=()=>{const h=musHand();return h.disc||h.wrapped};let musHoldWrap=false;
function musHoldBegin(){if(mode!=="museum"||musHoldT||!musHoldable())return;Museum.setHoldA&&Museum.setHoldA(true);musHoldWrap=!musHand().disc;musHoldStart=performance.now();play_("hold");const bar=musHoldBar();bar.querySelector("span").textContent=musHoldWrap?(musHand().parcel?"Opening…":"Unwrapping…"):"Starting…";bar.classList.toggle("wrap",musHoldWrap);bar.classList.add("on");musHoldT=requestAnimationFrame(musHoldTick)}
function musHoldTick(){if(!musHoldT)return;if(mode!=="museum"||!musHoldable()){musHoldEnd(false);return}const p=Math.min(1,(performance.now()-musHoldStart)/(musHoldWrap?1300:800));$("musHoldBar").style.width=(p*100)+"%";
  if(musHoldWrap){Museum.wrapPull&&Museum.wrapPull(p);if(p>.25)rumble(.05+p*.15,.1+p*.3,40)}
  if(p>=1){const w=musHoldWrap;musHoldEnd(true);if(w){if(Museum.unwrapHeld&&Museum.unwrapHeld()){rumble(.6,.8,260);renderMusHints()}}else museumPlay(musHand().game);return}musHoldT=requestAnimationFrame(musHoldTick)}
function musHoldEnd(){Museum.setHoldA&&Museum.setHoldA(false);if(!musHoldT)return;cancelAnimationFrame(musHoldT);musHoldT=null;if(musHoldWrap&&Museum.wrapPull)Museum.wrapPull(0);const el=document.getElementById("musHold");if(el){el.classList.remove("on");$("musHoldBar").style.width="0%"}}
// the game in your hands starts: the shelf jumps to it and plays it with the usual insert animation
// A on a movie in your hands: Stremio opens on that title (through the usual app start, home menu and all)
function museumWatch(d){const g=allShelves.flatMap(sh=>sh.items||[]).find(x=>x.kind==="app"&&x.appId==="stremio");
  if(!g){toast("Turn on Stremio under Settings → Stremio & Nuvio to watch from here");play_("error");return}
  const link=`stremio:///detail/${encodeURIComponent(d.type||"movie")}/${encodeURIComponent(d.sid)}`,gg=Object.assign({},g,{title:d.title,link,_sh:g._sh||shelfFor(g)});
  musLaunchFrom();museumTeardown();Museum.close();mode="browse";ambient(false);setTimeout(()=>launchNow(gg),120)}
function museumPlay(mg){if(!mg)return;const sh=mg._sh,si=shelves.indexOf(sh);if(si<0)return;if(mg._g.kind!=="rom"){toast(`Steam games open on the shelf: press ${btnWord("back")}, then Play`);play_("error");return}
  sysIdx=si;sel=Math.max(0,sh.items.indexOf(mg._g));flipped=false;spin=0;spinT=0;build();tuneTo(cur());raShelfLoad();preloadModel();save();
  musLaunchFrom();museumTeardown();Museum.close();mode="browse";ambient(false);setTimeout(play,120)}
// Y / L in the museum: step through the lighting presets (remembered)
let musLightT=0;
function museumLight(){if(!cfgInfo||musHand().delivery)return;const ids=Museum.lights().map(l=>l[0]);const cur0=musLightNow(musStyle()); /* from the scene on show (with Follow the time of day, not the one saved under it) */const next=ids[(ids.indexOf(cur0)+1)%ids.length];
  cfgInfo.config.ui.museumLight=next;if(cfgInfo.config.ui.museumStyle)cfgInfo.config.ui.museumStyle.timeOfDay=false;const name=Museum.setLighting(next);play_("lamp");toast(`Lighting: ${name}`);clearTimeout(musLightT);musLightT=setTimeout(()=>api.saveConfig(cfgInfo.config).catch(()=>{}),600)}
Shelf_setMusLight=v=>{if(cfgInfo)cfgInfo.config.ui.museumLight=v;if(window.Museum&&Museum.setLighting)Museum.setLighting(v)};
function museumBack(){if(Museum.back()){play_("back");renderMusHints();bsSelectSoon();return}closeMuseum()}
let musHeld={dir:0,t0:0,tl:0};
// round dead zone (a resting stick is often a little off centre), rescaled so it still reaches full tilt, eased near the middle
function stickDZ(x,y,dz=.28){const m=Math.hypot(x,y);if(m<dz)return [0,0];const k=Math.min(1,(m-dz)/(1-dz)),e=k*k;return [x/m*e,y/m*e]}
function museumPad(gp,t,B,ax){
  if(rdr.open){rdrPad(gp,t,B,ax);return}
  if(tvS.guide){const v=(B(13)||ax[1]>.55)?1:(B(12)||ax[1]<-.55)?-1:0,h=(B(15)||ax[0]>.7)?1:(B(14)||ax[0]<-.7)?-1:0;if(v){if(v!==musHeld.tg){musHeld.tg=v;musHeld.tgt=t;tvGuideMove(v)}else if(t-musHeld.tgt>(t-musHeld.tg0>900?70:140)){musHeld.tgt=t;tvGuideMove(v)}}else{musHeld.tg=0;musHeld.tg0=t}
    if(h&&h!==musHeld.tgh)tvGuideTab(h);musHeld.tgh=h; /* LB / RB, or the D-pad sideways: the tabs */edge(gp,4,()=>tvGuideTab(-1));edge(gp,5,()=>tvGuideTab(1));edge(gp,0,()=>tvGuidePick());edge(gp,2,()=>tvGuideFav());edge(gp,1,()=>tvGuideClose());edge(gp,3,()=>tvGuideClose());return}
  if(musFind.open){const h=(B(15)||ax[0]>.55)?1:(B(14)||ax[0]<-.55)?-1:0,v=(B(13)||ax[1]>.55)?1:(B(12)||ax[1]<-.55)?-1:0,k=Math.abs(ax[1])>Math.abs(ax[0])&&v||!h?(v?(v>0?"down":"up"):null):(h>0?"right":"left");
    // first press moves at once; held, it repeats after a short pause and speeds up a little
    if(k){if(k!==musHeld.mk){musHeld.mk=k;musHeld.mt0=t;musHeld.mtl=t;musFindKey(k)}else{const since=t-musHeld.mt0,gap=since>1200?70:110;if(since>340&&t-musHeld.mtl>gap){musHeld.mtl=t;musFindKey(k)}}}else musHeld.mk=null;
    const ry=gp.axes[3]||0;if(Math.abs(ry)>.18){const dt=Math.min(50,t-(musHeld.st||t));musFindScroll(Math.sign(ry)*Math.pow((Math.abs(ry)-.18)/.82,1.6)*1.6*dt)}musHeld.st=t; // right stick: scroll, faster the further you push
    edge(gp,4,()=>musFindSection(-1));edge(gp,5,()=>musFindSection(1));
    edge(gp,0,()=>musFindKey("ok"));edge(gp,1,()=>musFindKey("back"));edge(gp,2,()=>musFindKey("back"));return}
  // the Room menu takes the pad while it's open (Start opens/closes it)
  if(musRoom.open){const h=(B(15)||ax[0]>.55)?1:(B(14)||ax[0]<-.55)?-1:0,v=(B(13)||ax[1]>.6)?1:(B(12)||ax[1]<-.6)?-1:0,k=h?(h>0?"right":"left"):v?(v>0?"down":"up"):null;
    if(k&&k!==musHeld.rk)musRoomKey(k);musHeld.rk=k;edge(gp,0,()=>musRoomKey("ok"));edge(gp,1,()=>musRoomKey("back"));edge(gp,9,()=>musRoomClose());edge(gp,3,()=>musRoomClose());return}
  if(musFit.open){const h=(B(15)||ax[0]>.55)?1:(B(14)||ax[0]<-.55)?-1:0,v=(B(13)||ax[1]>.6)?1:(B(12)||ax[1]<-.6)?-1:0,k=h?(h>0?"right":"left"):v?(v>0?"down":"up"):null;
    if(k&&k!==musHeld.fk)musFitKey(k);musHeld.fk=k;edge(gp,0,()=>musFitKey("ok"));edge(gp,1,()=>musFitKey("back"));edge(gp,2,()=>musFitClose());edge(gp,9,()=>musFitClose());return}
  edge(gp,9,()=>musRoomOpen());
  const h=(B(15)||ax[0]>.55)?1:(B(14)||ax[0]<-.55)?-1:0,v=(B(13)||ax[1]>.6)?1:(B(12)||ax[1]<-.6)?-1:0;
  const tv=b=>{const x=gp.buttons[b];return x?Math.max(x.value||0,x.pressed?1:0):0},tz=(tv(7)>.45?1:0)-(tv(6)>.45?1:0);
  if(tz&&(musReading()||Museum.isHolding())){if(tz!==musHeld.tz||t-(musHeld.tzT||0)>170){musHeld.tzT=t;musReading()?Museum.readerZoom(tz):Museum.zoomHand(tz)}}
  musHeld.tz=tz;
  if(musReading()){if(h&&h!==musHeld.rh)Museum.turnPage(h);musHeld.rh=h;if(v&&v!==musHeld.rv)Museum.readerZoom(-v);musHeld.rv=v;const ry=ax[3]||0;if(Math.abs(ry)>.2)Museum.readerPan(ry*.03*padF);renderMusHintsSoon()}
  else if(Museum.isHolding()){const rx=ax[2]||0,ry=ax[3]||0;if(Math.abs(rx)>.15||Math.abs(ry)>.15){Museum.turn(rx*Math.abs(rx)*2.4*padF,-ry*Math.abs(ry)*1.4*padF);musHeld.turning=true}else if(musHeld.turning){musHeld.turning=false;Museum.release()}
    const dh=B(15)?1:B(14)?-1:0,dv=B(13)?1:B(12)?-1:0;if(dh&&!musHeld.h)Museum.turn(dh*90,0);musHeld.h=dh;if(dv&&dv!==musHeld.zv)Museum.zoomHand(-dv);musHeld.zv=dv;
    if(Museum.isHoldingCase()){const [sx,sy]=stickDZ(ax[0]||0,ax[1]||0);Museum.leanHand(sx,-sy)}}
  else{
    const d=h||v*4;if(d){if(d!==musHeld.dir){Object.assign(musHeld,{dir:d,t0:t,tl:t});Museum.move(h,v)}else{const f=scrollCfg.f,since=t-musHeld.t0,gap=(since>1400*f?60:since>700*f?90:130)*f;if(since>320*f&&t-musHeld.tl>gap&&h){musHeld.tl=t;Museum.move(h,0)}}}else musHeld.dir=0;
  }
  edge(gp,4,()=>Museum.jump(-10));edge(gp,5,()=>Museum.jump(10));edge(gp,3,()=>museumY());edge(gp,2,()=>museumX());if(B(0)&&musHoldable()){if(!musHoldT&&!musHeld.a)musHoldBegin()}else if(musHoldT&&musHeld.a&&!B(0))musHoldEnd(false);edge(gp,0,()=>{if(!musHoldable())museumActivate()});musHeld.a=B(0);edge(gp,1,()=>museumBack());
}
/* ---------- attract mode: after a few idle minutes the shelf drifts on its own with the HUD faded; any input wakes it ---------- */
let attract=false,attractT=0,attractDir=1;
function attractOn(){return !(cfgInfo&&cfgInfo.config.ui.attract===false)}
function attractCheck(){
  if(attract||dimOn||qm.open||!attractOn()||mode!=="browse"||slots.length<2||document.hidden)return;
  const mins=(cfgInfo&&+cfgInfo.config.ui.attractMinutes)||3;
  if(performance.now()-lastInputAt>mins*60000){attract=true;app.classList.add("attract");attractT=setTimeout(attractStep,2200)}
}
function attractStep(){
  if(!attract||dimOn)return; // the screen dimmer has taken over: rest (waking ends attract mode too)
  if(mode!=="browse"||document.hidden||!document.hasFocus()){attractT=setTimeout(attractStep,4000);return} // nobody can see it: don't spend a frame
  const n=slots.length;
  if(sel+attractDir>=n||sel+attractDir<0){
    attractDir=-attractDir;
    // at the end of a shelf, sometimes wander to the next shelf
    if(shelves.length>1&&Math.random()<.6){attractDir=1;sysStep(1); /* the new shelf starts at its first game: walk forwards, not back into the Shuffle / emulator tiles */attractT=setTimeout(attractStep,5200);return}
  }
  go(sel+attractDir);attractT=setTimeout(attractStep,4200);
}
function wake(){lastInputAt=performance.now();if(!attract)return;attract=false;clearTimeout(attractT);app.classList.remove("attract")}
setInterval(attractCheck,5000);
// warm the museum's shaders a few seconds after start, while nothing is happening, so its first open doesn't stutter
// warm the museum's shaders once you've been idle a few seconds (it's one long task; never mid-scroll)
const warmStart=performance.now();
(function warmLater(){setTimeout(()=>{if(!(window.Museum&&Museum.available()))return;if(mode!=="browse")return warmLater();if(app.classList.contains("covers-wait")||performance.now()-warmStart<6000)return warmLater(); /* never while the shelf is still filling in */if(performance.now()-lastInputAt<2500&&performance.now()-warmStart<30000)return warmLater(); /* while you're browsing it waits for a pause (up to 30 s), so any small hitch lands when nothing is moving */const go=()=>{if(mode!=="browse")return warmLater();bootMark("warm");let w=null;try{w=Museum.warm()}catch{}Promise.resolve(w).catch(()=>{}).then(()=>{bootMark("warm-done");return preloadModels()})};window.requestIdleCallback?requestIdleCallback(go,{timeout:3000}):go()},2500)})(); // the first quiet moment after startup (a controller that never rests used to hold this back for good) // sooner than before (was 6 s): a first visit right after startup used to pay for setting up the 3D room
// Heavy 3D models (a 50 MB console, say) are made into a light copy once, in a background worker, so the museum never
// has to chew through millions of triangles. Your file stays as it is; Boxshelf keeps the copy in themes\<system>\.optimized.
const idleNow=()=>mode==="browse"&&performance.now()-lastInputAt>1200&&!document.hidden;
const notInGame=()=>mode!=="launch"&&mode!=="boot"&&!document.hidden; // the optimizer runs in its own thread: anywhere but in a game
let modelOptBusy=false;const modelOptTried=new Set(); /* each model at most once per run, so a copy that doesn't take can't loop */
async function optimizeModels(){
  if(modelOptBusy||!api.saveOptimizedModel)return;const todo=[];for(const [id,t] of Object.entries(themes||{}))(t.heavy||[]).forEach(k=>t[k]&&!modelOptTried.has(t[k])&&todo.push([id,k,t[k]]));if(!todo.length)return;
  modelOptBusy=true;let done=0;
  try{for(const [id,k,url] of todo){modelOptTried.add(url);
    let fails={};try{fails=JSON.parse(localStorage.getItem("model-opt-fail")||"{}")}catch{}if(fails[url]&&Date.now()-fails[url]<864e5)continue; // tried and failed today: leave it on the original
    while(!notInGame())await wait(2000);api.log&&api.log(`3D model: making a light copy of ${id} ${k} in the background`);
    try{const buf=await(await fetch(url)).arrayBuffer();const w=new Worker("vendor/modelopt.worker.js");
      const r=await new Promise(ok=>{const to=setTimeout(()=>ok({ok:false,error:"took too long"}),180000);w.onmessage=e=>{clearTimeout(to);ok(e.data)};w.onerror=e=>{clearTimeout(to);ok({ok:false,error:e.message||"worker error"})};w.postMessage({id:1,buf},[buf])});w.terminate();
      if(!r.ok)throw new Error(r.error);await api.saveOptimizedModel(id,k,new Uint8Array(r.glb));done++;
      api.log&&api.log(`3D model optimized in the background: ${id} ${k} ${r.stats.trisBefore}→${r.stats.trisAfter} triangles, ${Math.round(r.stats.bytesIn/1048576)}→${Math.round(r.stats.bytesOut/1048576)} MB, ${r.stats.ms} ms`)}
    catch(e){fails[url]=Date.now();for(const u in fails)if(Date.now()-fails[u]>864e5)delete fails[u];try{localStorage.setItem("model-opt-fail",JSON.stringify(fails))}catch{}api.log&&api.log(`3D model optimize failed (${id} ${k}): ${e.message||e}; the original is used`)}}}
  finally{modelOptBusy=false}
  if(done)await reloadThemes();
}
// get the museum's console models ready while you're on the shelf (one at a time, only while you're not moving), so
// opening the museum has nothing heavy left to do
async function preloadModels(){
  await optimizeModels();
  if(!(window.Museum&&Museum.preloadModel))return;
  for(const sh of allShelves.filter(x=>x.kind==="console"||x.kind==="steam")){const t=themes[sh.id]||{},url=t.model||BUILTIN_MODEL[sh.id];if(!url)continue;while(!idleNow())await wait(1500);try{const ms=await Museum.preloadModel(url,modelOpts(sh.id));if(ms)api.log&&api.log(`museum: ${sh.id} console model ready ahead of time (${ms} ms)`)}catch{}await wait(400)}
}
addEventListener("pointermove",()=>{if(attract)wake()},{capture:true,passive:true});
// (beta.7) in Details the bar shows only what Details does (Play, Inspect, moving along, Back), not the shelf's whole list
const notDet=()=>mode!=="details",inDet=()=>mode==="details";
const HINTS=[
  {act:()=>letter(1),pad:["L1","R1"],kbd:["Z","C"],label:"Letter",when:notDet},
  {act:()=>sysStep(1),pad:["L2","R2"],kbd:["↑","↓"],label:"System",when:notDet},
  {act:()=>flip(),pad:["tri"],kbd:["F"],label:"Flip",when:notDet},
  {act:()=>toggleFav(),pad:["tri"],kbd:["H"],label:"♥ hold",when:notDet},
  {act:()=>mode==="details"?closeDetails():openDetails(),pad:["sq"],kbd:["I"],label:"Details",when:notDet},
  {act:()=>play(),pad:["x"],kbd:["Enter"],label:"Play"},
  {act:()=>openInspect(),pad:["R3"],kbd:["V"],label:"Inspect"},
  {act:()=>shuffle(),pad:["L3"],kbd:["R"],label:"Shuffle",when:notDet},
  {act:()=>openMuseum(),pad:["⧉"],kbd:["M"],label:"Museum hold",when:notDet},
  {act:()=>openSort(),pad:["☰"],kbd:["G"],label:"Sort hold",when:notDet},
  {act:()=>Setup.open(),pad:["☰"],kbd:["S"],label:"Settings",when:notDet},
  {act:()=>dTab(1),pad:["L1","R1"],kbd:["Q","E"],label:"Tabs",when:inDet}, // (beta.7)
  {act:()=>closeDetails(),pad:["o"],kbd:["Esc"],label:"Back",when:inDet}
];
// The controller version of the bar: tap / hold pairs share one entry
const PAD_HINTS=[
  {act:()=>sysStep(1),pad:["L2","R2"],label:"System"},
  {act:()=>flip(),pad:["tri"],label:"Flip",hold:"♥",when:()=>mode!=="details"},
  // in Details, Y is the trailer / achievements (the bar said "Flip · hold ♥" there, and holding Y doesn't favourite)
  {act:()=>detailsY(),pad:["tri"],label:"Flip",dy:1,when:()=>mode==="details"},
  {act:()=>mode==="details"?closeDetails():openDetails(),pad:["sq"],label:"Details",when:notDet},
  {act:()=>play(),pad:["x"],label:"Play"},
  {act:()=>openInspect(),pad:["R3"],label:"Inspect"},
  {act:()=>shuffle(),pad:["L3"],label:"Shuffle",when:notDet},
  {act:()=>Setup.open(),pad:["⧉"],label:"Settings",when:notDet},
  {act:()=>openMuseum(),pad:["⧉"],label:"",hold:"Museum",when:notDet},
  {act:()=>qmOpen(),pad:["☰"],label:"Menu",when:notDet},
  {act:()=>panelScroll($("details"),1),pad:["R"],label:"Scroll",when:()=>mode==="details"},
  {act:()=>openSort(),pad:["☰"],label:"",hold:"Sort",when:notDet},
  {act:()=>dTab(1),pad:["L1","R1"],label:"Tabs",when:inDet}, // (beta.7)
  {act:()=>closeDetails(),pad:["o"],label:"Back",when:inDet}
];
// an empty shelf (only the "Add games" tile): no prompts for keys that do nothing there
[HINTS,PAD_HINTS].forEach(L=>L.forEach(hn=>{if(!/Letter|Flip|♥|Details|Inspect|Shuffle|Sort/.test((hn.label||"")+(hn.hold||"")))return;const w=hn.when;hn.when=g=>!emptyShelf()&&(!w||w(g))}));
// Button prompts: Xbox (A/B/X/Y, LB/RB…) or PlayStation shapes. "auto" follows the connected controller.
let buttonsPref="auto",padStyle="xbox";
const XB_COL={A:"#5fbf4a",B:"#e5484d",X:"#3b82f6",Y:"#e8b81a"};
const xbFace=l=>`<svg viewBox="0 0 16 16"><circle cx="8" cy="8" r="7" fill="${XB_COL[l]}"/><text x="8" y="11.6" text-anchor="middle" font-size="10" font-weight="700" font-family="Lexend,Segoe UI,sans-serif" fill="#0b0b0f">${l}</text></svg>`;
const XB_MAP={x:"A",o:"B",sq:"X",tri:"Y",L1:"LB",R1:"RB",L2:"LT",R2:"RT",R3:"RS",L3:"LS","☰":"Menu","⧉":"View",L:"LS",R:"RS"};
const PS_WORD={"☰":"Options","⧉":"Share"};
// Nintendo layout (Switch Pro / Joy-Con): the same four positions, other letters — B bottom, A right, Y left, X top
const NT_MAP={x:"B",o:"A",sq:"Y",tri:"X",L1:"L",R1:"R",L2:"ZL",R2:"ZR",R3:"RS",L3:"LS","☰":"+","⧉":"−",L:"LS",R:"RS"};
const ntFace=l=>`<svg viewBox="0 0 16 16"><circle cx="8" cy="8" r="7" fill="#e9ebf1"/><text x="8" y="11.6" text-anchor="middle" font-size="9.5" font-weight="700" font-family="Lexend,Segoe UI,sans-serif" fill="#15161c">${l}</text></svg>`;
// Y in Details: tap = trailer if there is one, else achievements, else flip; hold = achievements when both are there
function detailsYLab(){const tr=!$("dTrailer").hidden,a=achReady();return [tr?"Trailer":a?"Achievements":"Flip",tr&&a?"Achievements":""]}
function detailsY(hold){const tr=!$("dTrailer").hidden;if(hold){achAct();return}if(tr)openTrailer();else if(!achAct())flip()}
function glyphStyle(){return buttonsPref==="auto"?padStyle:buttonsPref}
// Drawn controller icons (generic shapes: D-pad, bumpers, triggers, sticks, the two small centre buttons)
const ICO_INK="rgba(255,255,255,.95)",ICO_DIM="rgba(255,255,255,.38)";
function icoDpad(lit){ // lit: string of u d l r
  const arm=(d,x,y,w,h)=>`<rect x="${x}" y="${y}" width="${w}" height="${h}" rx="1.3" fill="${lit.includes(d)?ICO_INK:ICO_DIM}"/>`;
  const tri=(d,pts)=>lit.includes(d)?`<polygon points="${pts}" fill="#0b0d14"/>`:"";
  return `<svg viewBox="0 0 18 18" width="18" height="18">${arm("u",6.3,.6,5.4,6.4)}${arm("d",6.3,11,5.4,6.4)}${arm("l",.6,6.3,6.4,5.4)}${arm("r",11,6.3,6.4,5.4)}<rect x="6.3" y="6.3" width="5.4" height="5.4" fill="${ICO_DIM}"/>${tri("u","9,2.2 7.4,4.8 10.6,4.8")}${tri("d","9,15.8 7.4,13.2 10.6,13.2")}${tri("l","2.2,9 4.8,7.4 4.8,10.6")}${tri("r","15.8,9 13.2,7.4 13.2,10.6")}</svg>`}
const icoBumper=t=>`<svg viewBox="0 0 28 16" width="28" height="16"><path d="M3 13.5 L3 7 Q3 3 8 2.6 L20 2.6 Q25 3 25 7 L25 13.5 Z" fill="none" stroke="${ICO_INK}" stroke-width="1.4" stroke-linejoin="round"/><text x="14" y="11.6" text-anchor="middle" font-size="7.4" font-weight="700" font-family="Lexend,Segoe UI,sans-serif" fill="${ICO_INK}">${t}</text></svg>`;
const icoTrigger=t=>`<svg viewBox="0 0 22 18" width="22" height="18"><path d="M3.5 16.5 L3.5 6 Q3.5 1.4 11 1.4 Q18.5 1.4 18.5 6 L18.5 16.5 Z" fill="none" stroke="${ICO_INK}" stroke-width="1.4" stroke-linejoin="round"/><text x="11" y="12.6" text-anchor="middle" font-size="7" font-weight="700" font-family="Lexend,Segoe UI,sans-serif" fill="${ICO_INK}">${t}</text></svg>`;
const icoStick=t=>`<svg viewBox="0 0 18 18" width="18" height="18"><circle cx="9" cy="9" r="7.6" fill="none" stroke="${ICO_INK}" stroke-width="1.3"/><circle cx="9" cy="9" r="4.9" fill="${ICO_DIM}"/><text x="9" y="11.7" text-anchor="middle" font-size="7.2" font-weight="700" font-family="Lexend,Segoe UI,sans-serif" fill="${ICO_INK}">${t}</text></svg>`;
const icoMenu=`<svg viewBox="0 0 18 18" width="18" height="18"><circle cx="9" cy="9" r="8" fill="#2a2e3a" stroke="${ICO_DIM}" stroke-width=".8"/><path d="M5.4 6.4h7.2M5.4 9h7.2M5.4 11.6h7.2" stroke="${ICO_INK}" stroke-width="1.3" stroke-linecap="round"/></svg>`;
const icoView=`<svg viewBox="0 0 18 18" width="18" height="18"><circle cx="9" cy="9" r="8" fill="#2a2e3a" stroke="${ICO_DIM}" stroke-width=".8"/><rect x="5" y="5.4" width="5.6" height="4.6" rx=".8" fill="none" stroke="${ICO_INK}" stroke-width="1.1"/><rect x="7.4" y="7.8" width="5.6" height="4.6" rx=".8" fill="#2a2e3a" stroke="${ICO_INK}" stroke-width="1.1"/></svg>`;
const DPAD_KEYS={"↕":"ud","↔":"lr","↑":"u","↓":"d","←":"l","→":"r","dpad":"udlr"};
function icon(k){
  const st=glyphStyle(),xb=st==="xbox",nt=st==="nintendo",ic=h=>`<span class="g ico">${h}</span>`;
  if(DPAD_KEYS[k])return ic(icoDpad(DPAD_KEYS[k]));
  if(k==="L1"||k==="R1")return ic(icoBumper(xb?(k==="L1"?"LB":"RB"):nt?(k==="L1"?"L":"R"):k));
  if(k==="L2"||k==="R2")return ic(icoTrigger(xb?(k==="L2"?"LT":"RT"):nt?(k==="L2"?"ZL":"ZR"):k));
  if(k==="L3"||k==="R3"||k==="L"||k==="R")return ic(icoStick(k[0]));
  if(k==="☰")return xb?ic(icoMenu):null;
  if(k==="⧉")return xb?ic(icoView):null;
  return null;
}
function glyph(k){
  if(PADMAP.swap)k=k==="x"?"o":k==="o"?"x":k; // Nintendo-style confirm: "x" means "the confirm button", which is now the right one
  const i=icon(k);if(i)return i;
  if(glyphStyle()==="xbox"){const m=XB_MAP[k];if(m&&XB_COL[m])return `<span class="g shape">${xbFace(m)}</span>`;return `<span class="g">${m||k}</span>`}
  if(glyphStyle()==="nintendo"){const m=NT_MAP[k];if(m&&/^[ABXY]$/.test(m))return `<span class="g shape">${ntFace(m)}</span>`;return `<span class="g">${m||k}</span>`}
  return SHAPES[k]?`<span class="g shape">${SHAPES[k]}</span>`:`<span class="g">${PS_WORD[k]||k}</span>`;
}
// Words used in on-screen notes ("Press B or Esc").
const okWord=()=>showPad()?btnWord("ok"):"Enter"; // "Press A…" for a controller, "Press Enter…" on the keyboard
function btnWord(which){const st=glyphStyle(),right=(which==="back")!==PADMAP.swap; /* the button on the right, else the bottom one */return st==="nintendo"?(right?"A":"B"):st==="ps"?(right?"Circle":"Cross"):(right?"B":"A")}
// A pad button's printed name for running text, per pad style ("LB" / "L1" / "L"); keys: L1 R1 L3 menu guide
function padWord(k){const st=glyphStyle(),xb=st==="xbox",nt=st==="nintendo";return ({L1:xb?"LB":nt?"L":"L1",R1:xb?"RB":nt?"R":"R1",L3:xb||nt?"LS":"L3",menu:xb?"Menu":nt?"+":"Options",guide:xb?"the Xbox button":nt?"Home":"the PS button"})[k]||k}
// "R rolls again" / "LS rolls again": the Surprise tile's re-roll, worded for whatever you're holding
const rerollWord=()=>(showPad()?padWord("L3"):"R")+" rolls again";
function setButtons(pref){buttonsPref=pref||"auto";applyConfirm(confirmPref);renderHints();if(mode==="inspect")renderIspHints();updateSkipText()}
function updateSkipText(){const el=document.querySelector(".b-skip");if(el)el.textContent=`Press Enter, Esc, ${okWord()} or ${btnWord("back")} to skip`}
// controller prompts whenever a controller is connected, unless you're actually typing on the keyboard
let kbdEver=false;
const showPad=()=>inputMode==="pad"||(!!padSeen&&(inputMode!=="kbd"||!kbdEver));
function renderHints(){pvGuard();
  try{paintMenuGlyph();if(qm.open)qsHints();if(mode==="achv")achHints();if(mode==="customize")custSwitchText()}catch{} // the quick menu's own hint bar follows a switch between pad and keyboard too
  const usePad=showPad();const g=curGame();
  const es=!g&&!!emptyShelf();const sig=usePad+"|"+glyphStyle()+"|"+(g&&g.kind)+"|"+!!(g&&g.pick)+"|"+es+"|"+(mode==="details")+"|"+(mode==="details"?detailsYLab().join():"");if(sig===renderHints.sig&&$("hints").childElementCount)return;renderHints.sig=sig; // only rebuilt when something it shows changed
  const h=$("hints");h.innerHTML="";
  (usePad?PAD_HINTS:HINTS).forEach(hn=>{if(hn.when&&!hn.when(rawItem()))return;const b=document.createElement("button");b.type="button";b.className="hint"+(/Letter|Shuffle/.test(hn.label)?" hint-lo":"");
    const keys=(usePad?hn.pad.map(glyph):hn.kbd.map(k=>`<span class="g">${k}</span>`)).join("");
    const label=hn.label==="Play"&&(es||(g&&g.kind==="setup"))?"Set up":hn.label==="Play"&&g&&(g.kind==="app"||g.kind==="emulator")?"Open":hn.label==="Play"&&g&&g.kind==="random"?(g.pick?"Play":"Pick a game"):hn.label;
    const dy=hn.dy?detailsYLab():null,hold=dy?dy[1]:hn.hold,lab=dy?dy[0]:usePad?label:label==="♥ hold"?"♥ Favorite":label.replace(/ hold$/,"");
    b.innerHTML=`<span class="keys">${keys}</span>`+(lab?`<span class="lab"></span>`:"")+(usePad&&hold?`<span class="hold"><i>hold</i> </span>`:"");
    if(lab)b.querySelector(".lab").textContent=lab;if(usePad&&hold)b.querySelector(".hold").append(hold);
    b.addEventListener("click",hn.act);h.appendChild(b)});
}
let lastInputAt=0,yAt=0;
addEventListener("pointerdown",()=>{wake()},true);addEventListener("wheel",()=>{lastInputAt=performance.now()},{capture:true,passive:true});
let uiBusySent=0;
function setInput(m){if(m==="kbd"&&touchBack)m=inputMode; /* a touch Back (sent as Esc) keeps the controller / touch prompts */const was=showPad();{const t=performance.now();if(t-uiBusySent>500&&api.uiBusy){uiBusySent=t;try{api.uiBusy()}catch{}}} // background downloads hold off while you're pressing buttons
  if(m==="kbd")kbdEver=true;if(attract)wake();lastInputAt=performance.now();if(m!==inputMode){inputMode=m;renderHints();document.body.classList.toggle("pad-input",m==="pad");if(m==="pad"&&mode==="details")dGlide($("details").querySelector(".pad-focus"))}else if(showPad()!==was)renderHints()
  if(showPad()!==was&&(mode==="browse"||mode==="details")){const g=rawItem();if(!g||g.kind==="random")updateInfo()}} // the Shuffle tile's "R / LS rolls again" and the empty-filter note name a key: follow the switch
// the pointer comes back the moment the mouse moves
addEventListener("mousemove",e=>{if((Math.abs(e.movementX)+Math.abs(e.movementY))>2&&inputMode==="pad")setInput("pointer")},{passive:true});
// Controller status beside the clock: lit while a pad is connected, a toast when one connects or drops
let padSeen=null;
// What a pad is, from the id Chromium gives it ("DualSense Wireless Controller (STANDARD GAMEPAD Vendor: 054c Product: 0ce6)"):
// its maker and model first, then words in its name. style: the button prompts it gets (xbox / ps / nintendo); virtual:
// an XInput pad, which Windows reports without a maker, so Steam Input or DS4Windows can be wrapping any pad in it
// (padSee decides those from the controllers actually plugged in). Settings shows the name.
const PAD_MODELS={"054c":{"05c4":"DualShock 4","09cc":"DualShock 4","0ba0":"DualShock 4 (wireless adapter)","0ce6":"DualSense","0df2":"DualSense Edge","0e5f":"Access controller","*":"PlayStation controller"},
  "057e":{"2009":"Switch Pro Controller","2069":"Switch 2 Pro Controller","2006":"Joy-Con (L)","2007":"Joy-Con (R)","200e":"Joy-Con pair","2066":"Joy-Con 2 (R)","2067":"Joy-Con 2 (L)","2017":"SNES controller","2073":"GameCube controller","0337":"GameCube adapter","0306":"Wii remote","0330":"Wii remote","*":"Nintendo controller"},
  "045e":{"*":"Xbox controller"},"28de":{"1102":"Steam Controller","1142":"Steam Controller","1205":"Steam Deck","*":"Steam controller"},"2dc8":{"*":"8BitDo controller"}};
const PAD_STYLE={"054c":"ps","057e":"nintendo","045e":"xbox","28de":"xbox"}; // (beta.10: 8BitDo pads go by their name, Xbox by default: most are used in X-input mode; in Switch mode they report as Nintendo's own pad)
function padInfo(id){id=String(id||"");const m=/vendor:?\s*([0-9a-f]{4})[^0-9a-f]+product:?\s*([0-9a-f]{4})/i.exec(id),v=m&&m[1].toLowerCase(),p=m&&m[2].toLowerCase();
  // makers of pads for every console (Hori, PDP, PowerA, 8BitDo…): what the name says
  const style=/switch|nintendo|joy-?con|pro controller|pokken/i.test(id)?"nintendo":/dualsense|dualshock|playstation|ps[345]\b|^wireless controller/i.test(id)?"ps":"xbox";
  if(v&&PAD_MODELS[v])return {style:PAD_STYLE[v]||style,name:PAD_MODELS[v][p]||PAD_MODELS[v]["*"],vendor:v,product:p,virtual:false};
  const virtual=/xinput/i.test(id)&&!v;
  return {style,name:virtual?"Xbox-style controller (XInput)":style==="ps"?"PlayStation controller":style==="nintendo"?"Nintendo controller":/xbox/i.test(id)?"Xbox controller":"Controller",vendor:v||"",product:p||"",virtual}}
window.BS_padInfo=padInfo;
// XInput pads: the real controllers plugged in decide (main lists them: USB or Bluetooth, virtual ones and headsets left
// out). Any real Xbox pad: Xbox. Otherwise one kind of real pad (a DualSense behind Steam Input / DS4Windows): its buttons.
const padSee={list:[],ver:0};let padSeeVer=-1,padSeeT=0;
function padSeeAsk(){if(!api.padsPresent)return;clearTimeout(padSeeT);padSeeT=setTimeout(()=>api.padsPresent().then(l=>{l=Array.isArray(l)?l:[];if(JSON.stringify(l)!==JSON.stringify(padSee.list)){padSee.list=l;padSee.ver++}}).catch(()=>{}),1200)}
addEventListener("gamepadconnected",padSeeAsk);addEventListener("gamepaddisconnected",padSeeAsk);if(!ANDROID)padSeeAsk();
function padSeeReal(){const L=padSee.list;if(!L.length||L.some(x=>x.style==="xbox"))return null;const kinds=[...new Set(L.map(x=>x.style))];return kinds.length===1?L.find(x=>x.style===kinds[0]):null}
function padStyleOf(id){const i=padInfo(id);if(!i.virtual)return i.style;const r=padSeeReal();return r?r.style:"xbox"}
function padSeeName(id){if(!padInfo(id).virtual)return "";const r=padSeeReal();if(!r)return "";const n=(PAD_MODELS[r.vendor]||{})[r.product]||(PAD_MODELS[r.vendor]||{})["*"]||"Controller";return n+" (through Steam / DS4Windows)"}
// the prompts changed with the pad in use: every screen that shows buttons follows at once
function padStyleChanged(){applyConfirm(confirmPref);renderHints();updateSkipText();sendShelfInfo();try{if(mode==="inspect")renderIspHints()}catch{}try{dispatchEvent(new Event("bs-padstyle"))}catch{}}
function padName_(id){return padSeeName(id)||padInfo(id).name}
window.BS_padName=()=>padName?padName_(padName):"";window.BS_padStyleOf=padStyleOf;
function padStatus(gp){if(!document.hasFocus())return; // unfocused, Chromium hides pads: not a real disconnect
  const id=gp?gp.id:"";if(id===padSeen)return;const was=padSeen;padSeen=id;renderHints.sig="";renderHints();const el=$("padStat");if(el){el.classList.toggle("on",!!gp);el.title=gp?padName_(id):"No controller"}
  if(was===null)return; // first look at startup: no toast
  if(gp){toast(`${padName_(id)} connected`);rumble(.4,.4,120);try{api.log&&api.log(`controller: ${id} (${gp.mapping||"no mapping"}, ${gp.buttons.length} buttons) → ${padName_(id)}, ${padStyleOf(id)} prompts`)}catch{}}else{toast("Controller disconnected");if(inputMode==="pad"){setInput("kbd");renderHints.sig="";renderHints()}}} // no controller left: keyboard prompts, not A/B
function sendShelfInfo(){try{if(!api.shelfInfo)return;const n=allShelves.filter(s=>s.kind!=="home").reduce((a,s)=>a+(s.games?s.games.length:0),0);api.shelfInfo({summary:`${n} games · ${allShelves.filter(s=>s.kind!=="home").length} shelves`,glyphs:glyphStyle(),swap:PADMAP.swap})}catch(e){}}

/* ---------- keyboard ---------- */
// Home menu on the shelf (an overlay over this window): while it's open the shelf ignores its own input and passes
// keys through; when it closes it may hand back an action (Home shelf, Museum, Settings).
let preMenuMode="browse";
// Guide on the shelf: main asks first, and only these screens may open the menu (never over Inspect, the museum,
// Settings, Sort or while a game is starting)
api.onShelfMenuRequest&&api.onShelfMenuRequest(()=>{if(mode==="browse"||mode==="details"){qmClose(true);api.openShelfMenu()}});
api.onShelfMenu&&api.onShelfMenu(d=>{
  if(d&&d.open){if(mode==="homemenu")return;uiCrumb("Home menu opened (from "+mode+")");qmClose(true);closeDetails(true);preMenuMode=mode;mode="homemenu";play_("open");return}
  if(mode==="homemenu")mode=preMenuMode==="details"?"browse":preMenuMode;
  const a=d&&d.action;
  if(a==="shelfhome"){const i=shelves.indexOf(HOME);if(i>=0&&i!==sysIdx)setSystem(i);else if(i<0)toast("Play something or ♥ a game to fill your Home shelf")}
  if(a==="museum"||a==="settings")qmClose(true);
  if(a==="museum")openMuseum();else if(a==="settings")Setup.open();else play_("back");
});
addEventListener("keyup",e=>{if(mode==="homemenu"&&api.shelfMenuKey)api.shelfMenuKey(e.key,false)});
addEventListener("keydown",e=>{
  if(mode==="homemenu"){if(!e.repeat||/Arrow/.test(e.key))api.shelfMenuKey(e.key,true);e.preventDefault();return}
  if(mode==="setup"){Setup.key(e);return}
  if(mode==="sort"){setInput("kbd");if(sortKey(e.key))e.preventDefault();return}
  if(mode==="build"){setInput("kbd");if(bpKey(e.key))e.preventDefault();return}
  if(mode==="cover"){setInput("kbd");if(cpKey(e.key))e.preventDefault();return}
  if((e.key==="-"||e.key==="_"||e.key==="="||e.key==="+")&&mode!=="inspect"&&mode!=="museum"){setVol(curVol()+(e.key==="-"||e.key==="_"?-.05:.05),true);play_("tick",{dir:1});e.preventDefault();return}
  if(mode==="inspect"){inspectKey(e);return}
  if(mode==="museum"&&musFind.open){const a=document.activeElement,typing=a&&a.tagName==="INPUT"&&musFind.el.contains(a);if(e.key==="Escape"){e.preventDefault();if(typing)a.blur();else if(!(window.BoxshelfIA&&BoxshelfIA.back&&BoxshelfIA.back()))musFindClose();return}if(typing)return;if(e.key==="PageDown"||e.key==="PageUp"){e.preventDefault();musFindScroll((e.key==="PageDown"?1:-1)*innerHeight*.5);return}if(e.key==="Tab"){e.preventDefault();musFindSection(e.shiftKey?-1:1);return}const m={ArrowUp:"up",ArrowDown:"down",ArrowLeft:"left",ArrowRight:"right",Backspace:"back"}[e.key];if(m){e.preventDefault();musFindKey(m)}return}
  if(mode==="museum"&&rdr.open){setInput("kbd");if(rdrKey(e))e.preventDefault();return}
  if(mode==="museum"&&musFit.open){e.preventDefault();setInput("kbd");const m={ArrowUp:"up",ArrowDown:"down",ArrowLeft:"left",ArrowRight:"right",Enter:"ok"," ":"ok",Escape:"back",Backspace:"back",Tab:"back",x:"back",X:"back",f:"back",F:"back"}[e.key];if(m&&!e.repeat||m&&/Arrow/.test(e.key))musFitKey(m);return}
  // the TV guide: ↑↓ (PgUp / PgDn a page) choose, ←→ / Q E / Tab change tab, Enter puts it on, F (or X) favourites, Esc / T close
  if(mode==="museum"&&tvS.guide){e.preventDefault();setInput("kbd");const k=e.key;if(k==="ArrowDown")tvGuideMove(1);else if(k==="ArrowUp")tvGuideMove(-1);else if(k==="PageDown")tvGuideMove(8);else if(k==="PageUp")tvGuideMove(-8);
    else if(e.repeat)return;else if(k==="ArrowRight"||k==="e"||k==="E"||(k==="Tab"&&!e.shiftKey))tvGuideTab(1);else if(k==="ArrowLeft"||k==="q"||k==="Q"||k==="Tab")tvGuideTab(-1);else if(k==="Enter"||k===" ")tvGuidePick();else if(k==="f"||k==="F"||k==="x"||k==="X")tvGuideFav();else if(k==="Escape"||k==="Backspace"||k==="t"||k==="T")tvGuideClose();return}
  if(mode==="museum"&&(e.key==="t"||e.key==="T")&&!e.repeat&&(musHand().tv||musHand().held||tvSelWide()&&!musRoom.open)){e.preventDefault();setInput("kbd");museumY();return}
  if(mode==="museum"&&(e.key==="Tab"||musRoom.open)){e.preventDefault();setInput("kbd");if(e.key==="Tab"){musRoom.open?musRoomClose():musRoomOpen();return}const m={ArrowUp:"up",ArrowDown:"down",ArrowLeft:"left",ArrowRight:"right",Enter:"ok"," ":"ok",Escape:"back",Backspace:"back"}[e.key];if(m)musRoomKey(m);return}
  if(mode==="museum"&&musReading()&&/^Arrow/.test(e.key)){setInput("kbd");const k=e.key;if(k==="ArrowLeft")Museum.turnPage(-1);else if(k==="ArrowRight")Museum.turnPage(1);else if(k==="ArrowUp")Museum.readerZoom(1);else Museum.readerZoom(-1);renderMusHints();e.preventDefault();return}
  if(mode==="museum"){const k=e.key;setInput("kbd");if(k==="ArrowLeft")Museum.isHolding()?Museum.turn(-90,0):Museum.move(-1,0);else if(k==="ArrowRight")Museum.isHolding()?Museum.turn(90,0):Museum.move(1,0);else if(k==="ArrowUp"){if(musHand().held||musHand().libHeld)Museum.zoomHand(1); /* a magazine / Blu-ray in your hands zooms too, as on the pad */else if(!e.repeat)Museum.move(0,-1)}else if(k==="ArrowDown"){if(musHand().held||musHand().libHeld)Museum.zoomHand(-1);else if(!e.repeat)Museum.move(0,1)}else if(k==="q"||k==="Q"||k==="PageUp")Museum.jump(-10);else if(k==="e"||k==="E"||k==="PageDown")Museum.jump(10);else if(k==="l"||k==="L"){if(!e.repeat)museumLight()}else if(k==="p"||k==="P"||k===" "||k==="x"||k==="X"){if(!e.repeat)museumX()}else if(k==="r"||k==="R"){if(!e.repeat)museumX()}else if((k==="w"||k==="W")&&musReading()){Museum.readerPan(-.25)}else if((k==="s"||k==="S")&&musReading()){Museum.readerPan(.25)}else if(/^[wasdWASD]$/.test(k)&&musHand().held){const q=k.toLowerCase();Museum.panHand(q==="a"?-.08:q==="d"?.08:0,q==="w"?.08:q==="s"?-.08:0)}else if(k==="Enter"){if(!e.repeat){if(musHoldable())musHoldBegin();else museumActivate()}}else if(k==="Escape"||k==="Backspace"||k==="m"||k==="M"){if(!e.repeat)museumBack()}else return;e.preventDefault();return}
  // Customize: the arrows walk its buttons (as the D-pad does), Q / E (or PageUp / PageDown) switch system; nothing reaches the shelf behind
  if(mode==="customize"){const k=e.key,pn=$("customize"),dir={ArrowUp:"up",ArrowDown:"down",ArrowLeft:"left",ArrowRight:"right"}[k];setInput("kbd");
    if(dir){if(!padNav(pn,dir)&&(k==="ArrowUp"||k==="ArrowDown"))panelScroll(pn,k==="ArrowDown"?1:-1)}
    else if(k==="q"||k==="Q"||k==="PageUp"){if(!e.repeat)custStep(-1)}else if(k==="e"||k==="E"||k==="PageDown"){if(!e.repeat)custStep(1)}
    else if(k==="Enter"||k===" "){if(!e.repeat&&!padActivate(pn)&&document.activeElement&&pn.contains(document.activeElement))document.activeElement.click()}
    else if(k==="Escape"||k==="Backspace"||k==="t"||k==="T"){if(!e.repeat&&!((k==="Escape"||k==="Backspace")&&czClosePick()))closeCustomize()}else if(k==="F11")api.toggleFullscreen();else return;e.preventDefault();return}
  if(mode==="note"){e.preventDefault();setInput("kbd");const k=e.key;if(e.repeat)return;if(k==="ArrowLeft"||k==="ArrowUp"||(k==="Tab"&&e.shiftKey))noteMove(-1);else if(k==="ArrowRight"||k==="ArrowDown"||k==="Tab")noteMove(1);else if(k==="Enter"||k===" ")notePick();else if(k==="Escape"||k==="Backspace")noteClose();return} // (beta.7) the note card
  if(mode==="achv"){const k=e.key;setInput("kbd");if(k==="q"||k==="Q"||(k==="Tab"&&e.shiftKey)){e.preventDefault();achTab(-1)}else if(k==="e"||k==="E"||k==="Tab"){e.preventDefault();achTab(1)}else if(k==="ArrowDown")achMove(1);else if(k==="ArrowUp")achMove(-1);else if(k==="ArrowRight")achSide(1);else if(k==="ArrowLeft")achSide(-1);else if(k==="Escape"||k==="Backspace"||k==="Enter"){if(!e.repeat)closeAchievements()}else return;e.preventDefault();return}
  // (beta.7) Details' More menu: up / down walk it, Enter picks (Esc closes it, through back())
  if(mode==="details"&&!$("dMenu").hidden){const dir={ArrowUp:"up",ArrowDown:"down",ArrowLeft:"up",ArrowRight:"down"}[e.key];if(dir){e.preventDefault();setInput("kbd");padNav($("dMenu"),dir);return}if(e.key==="Enter"||e.key===" "){e.preventDefault();if(!e.repeat&&!padActivate($("dMenu"))&&document.activeElement&&$("dMenu").contains(document.activeElement))document.activeElement.click();return}}
  const k=e.key;let handled=true;setInput("kbd");
  // Scroll speed "relaxed": held arrow keys repeat no faster than this (the keyboard's own repeat rate sets the rest)
  if((k==="ArrowRight"||k==="ArrowLeft")&&e.repeat&&scrollCfg.kbd){const now=performance.now();if(now-kbdMoveAt<scrollCfg.kbd){e.preventDefault();return}kbdMoveAt=now}
  if(k==="ArrowRight")move(1);else if(k==="ArrowLeft")move(-1);
  else if(k==="ArrowUp"){if(!e.repeat)sysStep(-1)}else if(k==="ArrowDown"){if(!e.repeat)sysStep(1)}
  else if(mode==="details"&&(k==="q"||k==="Q"||k==="e"||k==="E")){if(!e.repeat){dMenuClose(true);dTab(k==="q"||k==="Q"?-1:1)}} // (beta.7) Q / E: Details' tabs
  else if(k==="q"||k==="Q"||k==="PageUp")skip(-1);else if(k==="e"||k==="E"||k==="PageDown")skip(1);
  else if(k==="z"||k==="Z")letter(-1);else if(k==="c"||k==="C")letter(1);
  else if(k==="f"||k==="F"){if(!e.repeat)flip()}
  else if(k==="i"||k==="I"){if(!e.repeat)(mode==="details"?closeDetails():openDetails())}
  else if((k==="t"||k==="T")&&mode==="details"){if(!e.repeat)openTrailer()}
  else if((k==="["||k==="]")&&mode==="details"&&!$("dShots").hidden)showShot(shotSel+(k==="]"?1:-1));
  else if(k==="v"||k==="V"){if(!e.repeat)openInspect()}
  else if(k==="r"||k==="R"){if(!e.repeat)shuffle()}
  else if(k==="h"||k==="H"){if(!e.repeat&&(mode==="browse"||mode==="details"))toggleFav()}
  else if(k==="m"||k==="M"){if(!e.repeat)openMuseum()}
  else if(k==="o"||k==="O"){if(!e.repeat&&(mode==="browse"||mode==="details"))openEmulator()}
  else if(k==="g"||k==="G"){if(!e.repeat)openSort()}
  else if(k==="Home"||k==="p"||k==="P"){if(e.repeat){}else if((mode==="browse"||mode==="details")&&api.openShelfMenu&&cfgInfo&&cfgInfo.config.ui.homeMenu!==false)api.openShelfMenu();else if(k==="Home"&&(mode==="browse"||mode==="details"))go(0)}
  else if(k==="s"||k==="S"){if(!e.repeat&&(mode==="browse"||mode==="details"))Setup.open()}
  else if(k==="t"||k==="T"){if(!e.repeat)toggleCustomize()}
  else if(k==="F11"){api.toggleFullscreen()}
  else if(k==="Enter"){
    // On the shelf, Enter always plays (even if a button still has focus from a click).
    const inPanel=e.target&&e.target.closest&&e.target.closest(".details button,.settings button,.settings input");
    if(e.repeat){}else if(inPanel&&mode!=="browse")handled=false;else{if(document.activeElement&&document.activeElement.blur)document.activeElement.blur();primary()}
  }
  else if(k==="Escape"||k==="Backspace")back();
  else if(k==="End"){if(mode==="browse"||mode==="details")go(slots.length-1)}
  else handled=false;
  if(handled)e.preventDefault();
});

/* ---------- pointer ---------- */
let wheelAcc=0,wheelT=0;
addEventListener("wheel",e=>{if(mode==="inspect"){e.preventDefault();if(rd.open)rdTurn(e.deltaY>0?1:-1);else ispZoom(e.deltaY<0?1:-1);return}
  if(mode!=="browse"&&mode!=="details")return;if(qm.open)return; /* the quick menu scrolls itself; the shelf behind stays put */if(e.target.closest&&e.target.closest(".details,.settings,.setup"))return;e.preventDefault();wheelAcc+=Math.abs(e.deltaX)>Math.abs(e.deltaY)?e.deltaX:e.deltaY;const now=performance.now();
  if(Math.abs(wheelAcc)>40&&now-wheelT>70){move(Math.sign(wheelAcc));wheelAcc=0;wheelT=now}},{passive:false});
let drag=null,dragMoved=false;
$("scene").addEventListener("pointerdown",e=>{if(mode!=="browse")return;const sl=e.target.closest(".slot");drag={x:e.clientX,onSel:!!(sl&&slots[sel]&&sl===slots[sel].el),last:e.clientX,acc:0,spin0:spin} /* spin0: a drag carries on from the angle the box already has (right stick, last drag) instead of snapping to the front */;dragMoved=false});
addEventListener("pointermove",e=>{if(!drag)return;const dx=e.clientX-drag.x;if(Math.abs(dx)>8)dragMoved=true;if(!dragMoved)return;
  if(drag.onSel){spin=drag.spin0+dx*.6;spinT=spin;slots[sel].box.classList.add("free");applyRotation()}
  else{drag.acc+=e.clientX-drag.last;drag.last=e.clientX;const step=bw*.3;while(Math.abs(drag.acc)>step){move(drag.acc>0?-1:1);drag.acc-=Math.sign(drag.acc)*step}}});
addEventListener("pointerup",()=>{if(!drag)return;if(drag.onSel&&dragMoved){spinT=spin;const wasFlipped=flipped;if(!spinSettle()&&flipped!==wasFlipped)sfx.flip()} /* one flip sound: spinSettle plays it for a spine */drag=null;setTimeout(()=>dragMoved=false,0)});

/* ---------- touch hooks ----------
   The Android build's touch layer (android-app/web-extras/android-touch.js: swipe to go back, pinch to zoom) asks these
   what's on screen; nothing on Windows calls them, so they change nothing there. Back itself is just the Esc key, the same
   as the Android back button, so every screen keeps its own idea of what Back means.
   screen(): what's in front ("shelf" is the bare game shelf, where a sideways swipe browses and must never mean Back).
   hswipe(): this screen uses sideways drags itself (turning a case, the museum, the page reader), so only a swipe that
   starts at the screen edge means Back there. mark() / cancel(): a finger that turned out to be a gesture takes back
   whatever the app started doing with it (a case half turned, a shelf drag). zoom(): what two fingers can zoom right now. */
window.BS_TOUCH=(()=>{let mk=null;
  const settingsOpen=()=>!!(window.Settings&&Settings.isOpen&&Settings.isOpen());
  const screen=()=>window.BS_STUDIO&&BS_STUDIO.isOpen()?"studio":mode==="browse"&&!qm.open&&!settingsOpen()?"shelf":mode; // the Android Art Studio over the shelf: its edge swipe is Back, not shelf browsing
  const hswipe=()=>screen()==="shelf"||mode==="inspect"||rdr.open||mode==="museum"&&!(musFind.open||tvS.guide||musRoom.open||musFit.open); // the museum's lists and menus swipe back like any other
  function mark(){mk=mode==="inspect"?{tx:isp.tx,ty:isp.ty}:null}
  function cancel(restore){
    if(isp.drag){isp.drag=null;$("inspect").classList.remove("dragging");holdEnd(true);if(restore&&mk&&mode==="inspect"){isp.tx=mk.tx;isp.ty=mk.ty;isp.last=performance.now()}}
    if(drag){if(drag.onSel&&dragMoved){spinT=drag.spin0;spinSettle()}drag=null;dragMoved=false}mk=null}
  // Each zoom target: free ones (min..max, get / set, a pan in screen pixels) follow the fingers exactly; stepped ones
  // (the museum's, which ease between fixed steps) take a step each time the pinch has grown or shrunk by `ratio`.
  function zoom(){
    if(mode==="inspect"&&!rd.open)return{min:.88,max:2.6,get:()=>isp.tz,set(z){isp.tz=Math.max(.88,Math.min(2.6,z));if(isp.tz<=.9)isp.tz=.88;isp.last=performance.now()},
      pan:()=>[isp.tpx,isp.tpy],setPan(x,y){const lim=Math.max(0,innerWidth*.35*(isp.tz-1));isp.tpx=Math.max(-lim,Math.min(lim,x));isp.tpy=Math.max(-lim,Math.min(lim,y));isp.last=performance.now()}};
    if(rdr.open)return{min:1,max:3,get:()=>rdr.zoom,set(z){z=Math.max(1,Math.min(3,z));if(Math.abs(z-rdr.zoom)<.005)return;rdr.zoom=z<1.03?1:z;if(rdr.zoom===1)rdr.px=rdr.py=0;rdrClampPan();rdrApply();clearTimeout(rdrZoomT);rdrZoomT=setTimeout(rdrShow,180)},
      panBy(dx,dy){rdrPan(-dx,-dy)}};
    if(mode==="museum"&&window.Museum&&!musFind.open&&!tvS.guide&&!musRoom.open&&!musFit.open){const h=musHand();
      if(h.reading)return{ratio:1.3,step(d){Museum.readerZoom(d);renderMusHints()},panBy(dx,dy){Museum.readerPan(-dy/innerHeight*1.6)}};
      if(h.held||h.libHeld)return{ratio:1.15,step(d){Museum.zoomHand(d)},panBy(dx,dy){Museum.panHand(-dx/innerWidth*.9,dy/innerHeight*.9)}}}
    return null}
  // back(): Back is the Esc key, the same as the Android back button, but it came from a finger: the hints keep showing
  // whatever they showed (on the Thor, its own controller's buttons) instead of switching to keyboard keys.
  function back(){const t=document.activeElement||document.body,o={key:"Escape",code:"Escape",keyCode:27,which:27,bubbles:true,cancelable:true};
    touchBack=true;try{t.dispatchEvent(new KeyboardEvent("keydown",o));t.dispatchEvent(new KeyboardEvent("keyup",o))}finally{touchBack=false}}
  // museum(): the top-screen museum by touch (Android's touch layer; nothing else calls it). Every move goes through the
  // same paths as the pad (Museum.move / turn, A = museumActivate, B = museumBack), so the wall's racing, sounds and the
  // bottom screen follow as they do for a button. null while a list / menu / the TV guide is over the wall (their own
  // touches, and a plain swipe there is Back).
  function museum(){if(mode!=="museum"||!window.Museum||musFind.open||tvS.guide||musRoom.open||musFit.open)return null;
    return{held:()=>Museum.isHolding(),
      step(h,v){if(Museum.isHolding())return false;Museum.move(h,v);return true},
      turn(dx,dy){if(Museum.isHolding())Museum.turn(dx,dy)},release(){if(Museum.isHolding())Museum.release()},
      // a tap: on a case on this row chooses it, on the chosen one picks it up (A); in your hands a tap on the case is A
      // (open it; a sealed one wants A held, as on the pad), anywhere else puts it back (B)
      tap(x,y){const p=Museum.pickAt&&Museum.pickAt(x,y);
        if(Museum.isHolding()){if(p&&p.held){if(Museum.isHoldingCase()&&!musHoldable())museumActivate()}else museumBack();bsSelectSoon();return}
        if(!p)return;const r=Museum.bottomRow&&Museum.bottomRow();if(!r)return;
        if(p.row!==r.row){Museum.move(0,p.row>r.row?1:-1);return} // a case on another row: go to that row first
        if(p.sel)museumActivate();else if(Museum.pickCol(p.col))bsSelectSoon()}}}
  return{screen,hswipe,mark,cancel,zoom,back,museum}})();

/* ---------- gamepad ---------- */
const prevBtn={};let held={dir:0,t0:0,tl:0},padSpinActive=false,padName="",quitHoldAt=0,comboCool=0;
// A short press that fires on release, so Start/Back can also be held together as the quit combo
const COMBO_BTNS={"start+back":[8,9],"lb+rb+start":[4,5,9],"l3+r3":[10,11],"back+b":[8,1]};const comboMemo={src:null,sw:null,out:null};
const tapAt={};
function tap(gp,i,t,fn){const p=!!(gp.buttons[i]&&gp.buttons[i].pressed);if(p&&!prevBtn[i])tapAt[i]=t;if(!p&&prevBtn[i]){if(tapAt[i]&&t-tapAt[i]<450&&!quitHoldAt&&t-comboCool>600){setInput("pad");fn()}tapAt[i]=0}prevBtn[i]=p}
// Triggers: standard pads report them as buttons 6/7 with an analog value; some only flag "pressed", and a few
// non-standard mappings expose them as extra axes resting at -1. Learn each axis's resting value the first time we see it.
const trigRest={};
function trig(gp){let lt=0,rt=0;const b6=gp.buttons[6],b7=gp.buttons[7];
  if(b6)lt=b6.value>0?b6.value:(b6.pressed?1:0);if(b7)rt=b7.value>0?b7.value:(b7.pressed?1:0);
  if(!lt&&!rt&&gp.mapping!=="standard"&&gp.axes.length>=6){const ax=gp.axes;for(let i=2;i<ax.length;i++){const k=gp.index+":"+i;if(trigRest[k]==null)trigRest[k]=ax[i];if(trigRest[k]<-.9){const v=(ax[i]+1)/2;if(v>.05){if(i%2===0&&!lt)lt=v;else if(!rt)rt=v}}}}
  return{lt,rt}}
function edge(gp,i,fn){const p=!!(gp.buttons[i]&&gp.buttons[i].pressed);if(p&&!prevBtn[i]){setInput("pad");fn()}prevBtn[i]=p}
// Input polling: every frame while Boxshelf is in front; ~30 Hz when it isn't (a game is running, or another window
// has focus), where a full-rate loop would only burn CPU next to the emulator.
// While a game runs and has the focus (or the window is stepped aside), polling stops entirely: the in-game combo and
// Home menu come from the main process, and focus / endLaunch / un-aside start it again.
let bgPoll=0,pollOff=false;
function pollResume(){if(!pollOff)return;pollOff=false;setTimeout(()=>poll(performance.now()),0)}
addEventListener("focus",pollResume);
// Smoothness watch (1.73.2): this loop already runs once a frame, so it also notes slow frames while the shelf is moving and
// writes one line a minute at most to the log when there were some (screen size, refresh, theme canvas size), for next time.
const fw={prev:0,hz:[],ref:0,moves:0,slow:0,worst:0,at:0};
function frameWatch(t){const gap=t-fw.prev;fw.prev=t;if(gap<=0||gap>250)return;
  if(fw.hz.length<120){fw.hz.push(gap);if(fw.hz.length===120){const a=fw.hz.slice().sort((x,y)=>x-y);fw.ref=a[60]}return}
  if(mode!=="browse"||!window.BS_BUSY())return;fw.moves++;if(gap>fw.ref*1.7){fw.slow++;fw.worst=Math.max(fw.worst,gap)}
  if(fw.slow&&t-fw.at>60000&&fw.moves>200){fw.at=t;const fx=window.ThemeFx&&ThemeFx._state();
    api.log&&api.log(`shelf smoothness: ${fw.slow} slow of ${fw.moves} frames while moving (worst ${Math.round(fw.worst)} ms, refresh ~${Math.round(1000/fw.ref)} Hz, ${innerWidth}x${innerHeight} @${devicePixelRatio}x, theme canvas ${fx?fx.W+"x"+fx.H:"-"}, ${document.body.classList.contains("fx")?"effects on":"effects off"})`);fw.slow=fw.moves=fw.worst=0}}
// padF: time since the last read, in 60 Hz frames. Anything a held stick adds every read is scaled by it, so a turn or a
// scroll runs at the same speed on a 60, 144 or 240 Hz screen (it ran 4x faster at 240 Hz before 1.73.2)
// Settings → System → Smoothness test (1.73.3): the shelf scrolls itself at a steady pace while the effects are switched off
// one at a time, and each run's frame pacing goes to the log, so the effect that costs this PC's graphics card the most shows
// up in numbers. ~45 s; Esc (or B) stops it; nothing is changed for good (the overrides are one <style> removed at the end).
const SMOOTH_OFF={halo:".sel-halo{display:none!important}",haloBlur:".sel-halo{filter:none!important}",reflect:"*{-webkit-box-reflect:none!important}",selShadow:".slot.sel .f-front,.slot.sel .f-back{box-shadow:none!important}",glare:".glare,.f-front::after,.f-back::after{display:none!important}",dim:".dim{display:none!important}",bg:".bg-layer{display:none!important}",canvas:".theme-canvas{display:none!important}",vignette:".vignette{display:none!important}",blur:"*{backdrop-filter:none!important;-webkit-backdrop-filter:none!important}"};
// the press that closed Settings (A on a row, Enter held a moment) must not land on the shelf: the pad's held buttons wait to
// be let go (dimPad's swallow), the keyboard's Enter repeats are dropped until a key goes up
function holdInput(){dimSwallowPad=true;keySwallow=true}
async function smoothTest(){
  api.log&&api.log(`smoothness test: asked (screen: ${mode}${smoothTest.on?", already running":""})`);
  if(smoothTest.on||mode!=="browse"||!cur())return false;smoothTest.on=true;
  // round 3 (1.73.5): round 2 showed nothing on the shelf is the cause (even one case alone: 35% slow; no slide at all: 50%),
  // while a small window ran a perfect 240. So now: the window itself. Full screen, a maximized window, a half-size window,
  // each standing still and scrolling. V: [name, css, per-move fn, window mode, still]
  const near=n=>()=>slots.forEach((x,k)=>{if(x&&x.el)x.el.style.visibility=Math.abs(k-sel)>n?"hidden":""}),unnear=()=>slots.forEach(x=>{if(x&&x.el)x.el.style.visibility=""});
  const wasFull=Math.abs(innerHeight-screen.height)<4&&Math.abs(innerWidth-screen.width)<4; if(api.winMode)await api.winMode("full").catch(()=>{}); // (it runs from full screen; put back how it was at the end)
  const V=[["full screen, standing still","",null,"full",true],["full screen, scrolling","",null,"full"],["full screen, flat cases",".slot,.box{transform-style:flat!important}",null,"full"],
    ["maximized window, standing still","",null,"max",true],["maximized window, scrolling","",null,"max"],["half-size window, standing still","",null,"half",true],["half-size window, scrolling","",null,"half"],
    ["full screen again, standing still","",null,"full",true],["full screen again, scrolling","",null,"full"]];
  const css=document.createElement("style");document.head.append(css);
  const ban=document.createElement("div");ban.style.cssText="position:fixed;left:50%;top:18px;transform:translateX(-50%);z-index:99;padding:6px 14px;border-radius:999px;background:rgba(0,0,0,.72);color:#fff;font:600 14px/1.3 Lexend,system-ui,sans-serif;pointer-events:none";document.body.append(ban);
  smoothTest.stop=false;
  const wait=ms=>new Promise(r=>setTimeout(r,ms)),gaps=[];let sampling=false,prev=0;
  const loop=t=>{if(sampling&&prev)gaps.push(t-prev);prev=t;if(smoothTest.on)requestAnimationFrame(loop)};requestAnimationFrame(loop);
  const sample=async(ms,scroll)=>{gaps.length=0;prev=0;sampling=true;const t0=performance.now();let dir=1,n=0;
    while(performance.now()-t0<ms&&!smoothTest.stop){if(scroll){move(dir);if(++n%8===0)dir=-dir;if(sample.fn)sample.fn()}lastInputAt=performance.now();await wait(scroll?120:100)}
    sampling=false;return gaps.slice()};
  const stat=(g,ref)=>{const a=g.slice().sort((x,y)=>x-y),avg=a.reduce((x,y)=>x+y,0)/Math.max(1,a.length);return{fps:Math.round(1000/avg),slow:Math.round(100*a.filter(x=>x>ref*1.5).length/Math.max(1,a.length)),p95:+(a[Math.floor(a.length*.95)]||0).toFixed(1),worst:Math.round(a[a.length-1]||0)}};
  const res=[];
  try{
    ban.textContent="Smoothness test: measuring the screen… (any button stops it)";await wait(500);
    const idle=(await sample(1200,false)).sort((x,y)=>x-y),ref=idle[Math.floor(idle.length/2)]||16.7;
    const fx=window.ThemeFx&&ThemeFx._state(),pf=window.Perf?Perf.get():{},gpu=(window.Perf&&Perf.detect().gpu)||"";
    api.log&&api.log(`smoothness test: start · refresh ~${Math.round(1000/ref)} Hz · window ${innerWidth}x${innerHeight} @${devicePixelRatio}x (${Math.round(innerWidth*devicePixelRatio)}x${Math.round(innerHeight*devicePixelRatio)} px) · performance ${pf.level||"?"}${pf.auto?" (auto)":""} · effects ${document.body.classList.contains("fx")?"on":"off"} · theme ${(document.body.className.match(/theme-(\w+)/)||[])[1]||"?"} · background ${bgMode()} · theme canvas ${fx?fx.W+"x"+fx.H:"-"} · ${slots.length} cases · GPU ${gpu.slice(0,90)}`);
    let wm="full";for(let i=0;i<V.length&&!smoothTest.stop;i++){const [name,rule,fn,mode,still]=V[i];css.textContent=rule;unnear();sample.fn=fn||null;if(fn)fn();
      if(mode&&mode!==wm&&api.winMode){wm=mode;ban.textContent=`Smoothness test: switching to a ${mode==="full"?"full-screen":mode==="max"?"maximized":"half-size"} window…`;await api.winMode(mode).catch(()=>{});await wait(1800)}ban.textContent=`Smoothness test ${i+1} of ${V.length}: ${name} (any button stops it)`;await wait(500);if(smoothTest.stop)break;
      const r=stat(await sample(3000,!still),ref);res.push({name,...r});api.log&&api.log(`smoothness test: ${name.padEnd(34)} ${innerWidth*devicePixelRatio|0}x${innerHeight*devicePixelRatio|0} px · ${String(r.fps).padStart(3)} fps · ${String(r.slow).padStart(2)}% slow frames · 95% within ${r.p95} ms · worst ${r.worst} ms`)}
    if(smoothTest.stop)api.log&&api.log("smoothness test: stopped early");
  }finally{smoothTest.on=false;css.remove();ban.remove();unnear();if(api.winMode)api.winMode(wasFull?"full":"max").catch(()=>{})}
  toast(smoothTest.stop?"Smoothness test stopped":"Smoothness test done: the results are in the log");return res}
let pollLast=-1e9,padF=1;
let slowPoll=false;
function anyPad(){let L=null;try{L=navigator.getGamepads?navigator.getGamepads():null}catch{}if(!L)return false;for(let i=0;i<L.length;i++)if(L[i])return true;return false}
addEventListener("gamepadconnected",()=>{if(slowPoll){clearTimeout(bgPoll);slowPoll=false;requestAnimationFrame(poll)}}); // (back to every frame at once)
function poll(t){
  if(mode==="launch"&&(!document.hasFocus()||document.body.classList.contains("aside"))){clearTimeout(bgPoll);pollOff=true;return}
  // (Android: with nothing touched for a few seconds and the shelf still, the pad is read on a 30 Hz timer instead of every
  // frame, so an idle screen doesn't wake up to draw 60 times a second; the first press switches straight back)
  if(mode==="launch"||document.hidden||!document.hasFocus()||(ANDROID&&window.BS_METER&&BS_METER.idle()&&!window.BS_BUSY())){clearTimeout(bgPoll);bgPoll=setTimeout(()=>poll(performance.now()),33)}
  // (Android, on the shelf itself: read on a ~60 Hz timer, not in an animation frame. The slide is CSS transitions the
  // compositor runs on its own; a frame callback every vsync made the page restyle every moving case, re-layerize and
  // commit 60 times a second for the whole slide. Now the page only makes a frame when a step actually changes something)
  else if(ANDROID&&mode==="browse"&&!qm.open){clearTimeout(bgPoll);bgPoll=setTimeout(()=>poll(performance.now()),16)}
  // (beta.9, PC) no controller connected and the shelf still: looked for ten times a second instead of every frame (a
  // 144 / 240 Hz screen woke the page that often for nothing); every frame again while the shelf moves (the smoothness
  // log) and the moment a controller connects
  else if(!ANDROID&&!anyPad()&&!(window.BS_BUSY&&BS_BUSY())){clearTimeout(bgPoll);slowPoll=true;bgPoll=setTimeout(()=>{slowPoll=false;poll(performance.now())},100)}
  // (beta.10, PC) a controller connected but nothing touched for 3 s and the shelf still: read ~30 times a second on a timer,
  // not every vsync; any press or input brings back every frame on the next read
  else if(!ANDROID&&!(window.BS_BUSY&&BS_BUSY())&&performance.now()-Math.max(lastInputAt,padActAt)>3000){clearTimeout(bgPoll);bgPoll=setTimeout(()=>poll(performance.now()),33)}
  else{requestAnimationFrame(poll);frameWatch(t)}
  // on a 240 Hz screen the pad is read every other frame (~120 times a second): the same feel, half the work
  if(t-pollLast<6.5)return;padF=Math.min(3,Math.max(.25,(t-pollLast)/16.667));pollLast=t;
  try{pollBody(t)}catch(e){console.warn("input",e)}
}
// Several pads (or one listed twice: a Switch Pro over Bluetooth can leave a frozen copy behind when it re-connects,
// Steam / BetterJoy add a virtual one): follow the pad that was last actually used, not simply the first in the list.
// "Used" = its buttons or sticks changed (sticks rounded, so an idle pad's tiny drift doesn't count).
// (beta.10) padActAt: the last time a pad changed or had a button held (poll stays on every frame for ~3 s after). The state
// is a number now and the list is read in place: no array or string made per read
const padUse=new Map();let padIdx=-1,padActAt=-1e9;
function activePad(){const L=safe(()=>navigator.getGamepads()),n=L?L.length:0,now=performance.now();let best=null,bt=-1,cur=null;
  for(let j=0;j<=n;j++){const p=j<n?L[j]:wmPad;if(!p||!p.connected||(p!==wmPad&&WM_RAW.test(p.id)))continue;
    let h=0;const b=p.buttons,ax=p.axes;for(let i=0;i<b.length;i++){const on=!!(b[i]&&b[i].pressed);if(on)padActAt=now;h=(Math.imul(h,31)+(on?1:2))|0}
    if(ax){const std=p.mapping==="standard";for(let i=0;i<ax.length;i++){const a=ax[i],q=Math.abs(a)<.3?0:Math.round(a*4);if(q&&std&&i<4)padActAt=now;h=(Math.imul(h,31)+q+9)|0}} // (a stick held over: in use; other axes can rest at -1)
    let o=padUse.get(p.index);if(!o||o.id!==p.id){o={id:p.id,sig:h,at:0};padUse.set(p.index,o)}else if(o.sig!==h){o.sig=h;o.at=now;padActAt=now;if(window.BS_METER)BS_METER.input()}
    if(p.index===padIdx)cur=p;if(o.at>bt){bt=o.at;best=p}}
  if(cur&&best!==cur&&padUse.get(cur.index).at>=bt)best=cur;
  if(best)padIdx=best.index;if(padUse.size>16)padUse.clear();return best}
window.BS_activePad=activePad;
/* ---------- the Wii remote (Windows, through a DolphinBar) ----------
   main reads the remote: its pointer (main also moves Chromium's mouse there, so hover, click and drag work as with a
   mouse) and its buttons, which reach every screen as one more controller: a virtual pad in the standard layout. Held
   upright: A confirms (when it isn't clicking where it points), B goes back, the D-pad moves, 1 / 2 are X / Y, - / +
   switch shelves, Home opens the Home menu. Held sideways (Settings): the D-pad turned, 2 confirms, 1 goes back. The
   DolphinBar's own HID entries in Chromium's list are left out (their reports aren't a pad's: WM_RAW). A hand cursor
   follows the pointer, and the remote gives a short rumble as it moves onto something you can click, like on a Wii. */
const WM_RAW=/057e[^0-9a-f]*(0306|0330)/i,WM_CLICK='button,a,.slot,.tab,[role="button"],.qm-opt,.bp-row,.hint,.sp-opt,select,input';
let wmPad=null;const wmHand={el:null,seenAt:0,over:null,rumbleAt:0};
function wmButtons(b,hold){const on=k=>!!(b&k),B=new Array(17).fill(false),up=on(0x800),dn=on(0x400),lf=on(0x100),rt=on(0x200);
  // (the reader's bits: Left 0x100, Right 0x200, Down 0x400, Up 0x800, Plus 0x1000; 2 0x1, 1 0x2, B 0x4, A 0x8, Minus 0x10, Home 0x80)
  if(hold==="sideways"){B[14]=up;B[15]=dn;B[13]=lf;B[12]=rt;B[1]=on(0x1);B[0]=on(0x2);B[2]=on(0x8);B[3]=on(0x4)}
  else{B[12]=up;B[13]=dn;B[14]=lf;B[15]=rt;B[1]=on(0x8);B[0]=on(0x4);B[2]=on(0x2);B[3]=on(0x1)}
  B[4]=on(0x10);B[5]=on(0x1000);B[16]=on(0x80);return B.map(p=>({pressed:p,touched:p,value:p?1:0}))}
function wmHandEl(){if(wmHand.el)return wmHand.el;const el=document.createElement("div");el.id="wmHand";el.setAttribute("aria-hidden","true");
  el.innerHTML='<svg viewBox="0 0 64 64"><path d="M22 6c3.3 0 6 2.7 6 6v17.5l2-.4V24c0-3 2.4-5.3 5.3-5.3S40.6 21 40.6 24v5.6l1.6-.2v-1.6c0-2.9 2.4-5.2 5.3-5.2s5.2 2.3 5.2 5.2v3.6l.8-.1c2.9-.4 5.5 1.9 5.5 4.8V44c0 8.8-7.2 16-16 16H34c-5.5 0-10.6-2.7-13.6-7.3L9.6 36.4c-1.6-2.4-1-5.6 1.4-7.2 2.2-1.5 5.1-1.1 6.8.9L16 30V12c0-3.3 2.7-6 6-6z"/></svg>';
  document.body.appendChild(el);wmHand.el=el;return el}
api.onWiimote&&api.onWiimote(d=>{
  if(!d||d.b==null){if(d&&d.on===false){wmPad=null;if(wmHand.el)wmHand.el.classList.remove("on")}return}
  wmPad={id:"Nintendo Wii Remote (Vendor: 057e Product: 0306)",index:90,connected:true,mapping:"standard",axes:[0,0,0,0],buttons:wmButtons(d.b,d.hold),timestamp:performance.now()};
  const el=wmHandEl();
  if(d.seen&&d.x>=0){wmHand.seenAt=performance.now();el.style.transform=`translate3d(${Math.round(d.x*innerWidth)}px,${Math.round(d.y*innerHeight)}px,0)`;el.classList.add("on")}
  else el.classList.remove("on");
});
// the short rumble as the pointer moves onto something clickable (the remote's own pointer only, not a real mouse)
addEventListener("pointerover",e=>{if(!wmHand.el||!wmHand.el.classList.contains("on")||performance.now()-wmHand.seenAt>200)return;const c=e.target&&e.target.closest?e.target.closest(WM_CLICK):null;if(c===wmHand.over)return;wmHand.over=c;
  if(c&&!c.disabled&&performance.now()-wmHand.rumbleAt>90){wmHand.rumbleAt=performance.now();api.wmRumble&&api.wmRumble(22)}},true);
addEventListener("pointerdown",()=>{if(wmHand.el&&wmHand.el.classList.contains("on"))wmHand.el.classList.add("press")},true);
addEventListener("pointerup",()=>{if(wmHand.el)wmHand.el.classList.remove("press")},true);
function pollBody(t){
  let gp=activePad();
  padStatus(gp);if(mode==="homemenu")return;
  // a screen of the Android build's own that reads the pad itself while it's up (the handheld Art Studio): the shelf's
  // edges are kept in step with what's held, so the press that closes it doesn't land on the shelf too
  if(gp&&window.BS_MODAL){const vg=viewOf(gp);if(BS_MODAL(vg,t)){for(let i=0;i<vg.buttons.length;i++)prevBtn[i]=!!(vg.buttons[i]&&vg.buttons[i].pressed);prevBtn.v=prevBtn.h=true;held.dir=0;return}}
  gp=viewOf(gp); // A/B swapped here when the confirm button is B (Settings → Controller), for every screen below
  if(gp&&document.hasFocus()&&dimPad(gp,t))return; // the press that lifted the dimmed screen does nothing else
  if(smoothTest.on){if(gp&&gp.buttons.some(b=>b&&b.pressed)){smoothTest.stop=true;dimSwallowPad=true}return} // the smoothness test: hands off (any button stops it)
  if(gp&&document.hasFocus()){
    if(gp.id!==padName||padSee.ver!==padSeeVer){padName=gp.id;padSeeVer=padSee.ver;const st=padStyleOf(gp.id);if(st!==padStyle){padStyle=st;padStyleChanged()}else sendShelfInfo()}
    const B=i=>!!(gp.buttons[i]&&gp.buttons[i].pressed),ax=gp.axes||[];
    const h=(B(15)||ax[0]>.55)?1:(B(14)||ax[0]<-.55)?-1:0;
    const v=(B(13)||ax[1]>.7)?1:(B(12)||ax[1]<-.7)?-1:0;
    // Hold the quit combo (Settings → Controller; off there = off here too) on the shelf for a moment to quit Boxshelf (a bar shows the progress)
    const combo0=COMBO_BTNS[(cfgInfo&&cfgInfo.config.ui.quitComboKeys)||"l3+r3"]||COMBO_BTNS["l3+r3"];
    // the combo names physical buttons ("back+b"): the same buttons whichever one confirms (worked out once, not per frame)
    if(comboMemo.src!==combo0||comboMemo.sw!==PADMAP.swap){comboMemo.src=combo0;comboMemo.sw=PADMAP.swap;comboMemo.out=PADMAP.swap?combo0.map(i=>i===0?1:i===1?0:i):combo0}
    const combo=comboMemo.out;
    if(cfgInfo&&cfgInfo.config.ui.quitCombo!==false&&combo.every(B)&&(mode==="browse"||mode==="details"||mode==="inspect")){
      if(!quitHoldAt)quitHoldAt=t;const p=Math.min(1,(t-quitHoldAt)/1400);$("quitHold").classList.add("on");$("quitBar").style.width=(p*100)+"%";
      if(p>=1){quitHoldAt=0;$("quitHold").classList.remove("on");sfx.back();api.quit("quit combo held on the shelf: "+((cfgInfo&&cfgInfo.config.ui.quitComboKeys)||"l3+r3"));return}
      combo.forEach(i=>prevBtn[i]=true);return;
    }else if(quitHoldAt){quitHoldAt=0;comboCool=t;$("quitHold").classList.remove("on");$("quitBar").style.width="0%"}
    // the PS button (DualSense / DualShock; Chromium's button 16) opens the Home menu, as the Home key does (Xbox's Guide
    // never reaches Chromium: the controller helper reports that one). Not on Android: the Thor's Home is the system's
    if(!ANDROID)edge(gp,16,()=>{if((mode==="browse"||mode==="details")&&api.openShelfMenu&&cfgInfo&&cfgInfo.config.ui.homeMenu!==false){qmClose(true);api.openShelfMenu()}});
    if(mode==="cover"){
      if(h&&!prevBtn.h){setInput("pad");cpMove(h,0)}if(v&&!prevBtn.v){setInput("pad");cpMove(0,v)}prevBtn.v=!!v;prevBtn.h=!!h;
      edge(gp,0,()=>cpChoose());edge(gp,1,()=>{cpDone();sfx.back()});return;
    }
    if(mode==="build"){
      if(v&&!prevBtn.v){setInput("pad");bpMove(v)}prevBtn.v=!!v;prevBtn.h=!!h;
      edge(gp,0,()=>bpChoose());edge(gp,1,()=>bpDone(""));return;
    }
    if(mode==="note"){if(h&&!prevBtn.h){setInput("pad");noteMove(h)}prevBtn.h=!!h;prevBtn.v=!!v;edge(gp,0,()=>notePick());edge(gp,1,()=>noteClose());return} // (beta.7) the shelf's note card: A picks, B closes, left / right
    if(mode==="sort"){
      if(h&&!prevBtn.h){setInput("pad");sortStep(h)}if(v&&!prevBtn.v){setInput("pad");spRow=1-spRow;play_("tick",{dir:v});renderSort()}prevBtn.v=!!v;prevBtn.h=!!h;
      edge(gp,0,()=>closeSort());edge(gp,1,()=>closeSort());edge(gp,9,()=>closeSort());edge(gp,4,()=>{closeSort();letter(-1)});edge(gp,5,()=>{closeSort();letter(1)});return;
    }
    if(mode==="achv"){
      if(v&&!prevBtn.v){setInput("pad");achMove(v)}if(h&&!prevBtn.h){setInput("pad");achSide(h)}prevBtn.v=!!v;prevBtn.h=!!h;
      const ry2=ax[3]||0;if(Math.abs(ry2)>.5&&t-(prevBtn.achT||0)>140){prevBtn.achT=t;achMove(ry2>0?1:-1)}
      edge(gp,4,()=>achTab(-1));edge(gp,5,()=>achTab(1));edge(gp,1,()=>closeAchievements());edge(gp,0,()=>closeAchievements());return;
    }
    const padActive=gp.buttons.some(b=>b&&b.pressed)||ax.some(a=>Math.abs(a)>.2);
    if(mode==="museum"){if(padActive)setInput("pad");museumPad(gp,t,B,ax);return}
    if(qm.open&&(mode==="browse"||mode==="details")){if(padActive)setInput("pad");const v=(B(13)||ax[1]>.6)?1:(B(12)||ax[1]<-.6)?-1:0,h=(B(15)||ax[0]>.6)?1:(B(14)||ax[0]<-.6)?-1:0;if(v){if(!prevBtn.qv){qmMove(v);prevBtn.qvT=t+380}else if(t>prevBtn.qvT){qmMove(v);prevBtn.qvT=t+120}}prevBtn.qv=!!v;if(h){if(!prevBtn.qh){qmSide(h);prevBtn.qhT=t+380}else if(t>prevBtn.qhT){qmSide(h);prevBtn.qhT=t+110}}prevBtn.qh=!!h;if(prevBtn[0]&&!B(0))qsHoldStop(); /* only letting go of A ends a hold A began: checking "A not down" every frame cancelled a held Enter / mouse press whenever a pad was plugged in */edge(gp,0,()=>qmPick());edge(gp,1,()=>qmBack());edge(gp,9,()=>qmClose());edge(gp,8,()=>qmClose());return}
    if(mode==="inspect"){
      if(padActive)setInput("pad");
      const lx=ax[0]||0,ly=ax[1]||0,rx2=ax[2]||0,ry2=ax[3]||0;
      const sx=Math.abs(rx2)>.15?rx2:Math.abs(lx)>.15?lx:0, sy=Math.abs(ry2)>.15?ry2:Math.abs(ly)>.15?ly:0;
      if(rd.open){edge(gp,3,()=>wrongManual());edge(gp,4,()=>rdTurn(-1));edge(gp,5,()=>rdTurn(1));edge(gp,14,()=>rdTurn(-1));edge(gp,15,()=>rdTurn(1));const hs=ax[0]>.55?1:ax[0]<-.55?-1:0;if(hs&&!prevBtn.hs)rdTurn(hs);prevBtn.hs=!!hs;/* D-pad via the edges above, stick separately: both fired for one D-pad press and skipped a spread */edge(gp,0,()=>rdTurn(1));edge(gp,1,()=>fixOn()?fixCancel():closeReader());edge(gp,2,()=>fixOn()?repairManual():closeReader()); /* (beta.9: in the fix menu, B leaves it and X repairs) */prevBtn.v=!!v;prevBtn.h=!!h;return}
      const {lt,rt}=trig(gp);if(rt>.08)ispZoom(rt,padF);if(lt>.08)ispZoom(-lt,padF);
      // zoomed in: the left stick pans the view gently, the right stick turns the case; not zoomed: either stick turns it
      if(isp.tz>1){
        if(Math.abs(lx)>.15||Math.abs(ly)>.15)ispPan(lx*Math.abs(lx)*3.2*isp.tz*padF,ly*Math.abs(ly)*3.2*isp.tz*padF);
        if(Math.abs(rx2)>.15||Math.abs(ry2)>.15)ispTurn(rx2*Math.abs(rx2)*1.15*ISP_PAD*padF,-ry2*Math.abs(ry2)*.9*ISP_PAD*padF,ANDROID);
      }else if(sx||sy)ispTurn(sx*Math.abs(sx)*1.15*ISP_PAD*padF,-sy*Math.abs(sy)*.9*ISP_PAD*padF,ANDROID);
      if((sx||sy)&&isp.tz<=1)isp.stick=true;else if(isp.stick){isp.stick=false;ispSnap()}
      // D-pad left/right: step to the next side (front → spine → back → edge); up/down tilts
      edge(gp,14,()=>{ispTurn(-90,0,true);ispSnap()});edge(gp,15,()=>{ispTurn(90,0,true);ispSnap()});if(B(12))ispTurn(0,1.1*ISP_PAD*padF,ANDROID);if(B(13))ispTurn(0,-1.1*ISP_PAD*padF,ANDROID);
      if(isp.state===2){if(B(0)&&!holdT&&!prevBtn[0])holdBegin();else if(!B(0)&&prevBtn[0]&&holdT)holdEnd(false);prevBtn[0]=B(0)} /* (only the pad's own A let go ends a hold: one held on Enter or the mouse is theirs to end) */else edge(gp,0,()=>ispAdvance());edge(gp,1,()=>ispBack());edge(gp,2,()=>{if(isp.state>=1)openReader()});edge(gp,3,()=>ispFlip());tap(gp,11,t,()=>{closeInspect();detailsReturn()});tap(gp,10,t,()=>{closeInspect();detailsReturn()});
      prevBtn.v=!!v;prevBtn.h=!!h;
    }else if(mode==="setup"){
      if(v&&!prevBtn.v)Setup.pad(v>0?"down":"up");if(h&&!prevBtn.h)Setup.pad(h>0?"right":"left");prevBtn.v=!!v;prevBtn.h=!!h;
      edge(gp,0,()=>Setup.pad(PADMAP.swap?"back":"ok"));edge(gp,1,()=>Setup.pad(PADMAP.swap?"ok":"back")); /* Confirm swap (Nintendo / "right button") as everywhere else */edge(gp,9,()=>Setup.pad("back"));
      // a Start / View press that began here (Start closes Settings) never counts as the shelf's hold-for-Sort / hold-for-Museum
      if(B(9))prevBtn.b9At=Infinity;if(B(8))prevBtn.b8At=Infinity;
      {const ry=ax[3]||0;if(Math.abs(ry)>.2&&Setup.scroll)Setup.scroll(ry*Math.abs(ry)*28*padF)} /* right stick scrolls the list you are on, or the page */
    }else{
      // Details and Customize: the D-pad moves a highlight between the panel's buttons, A presses it, B closes
      const panel=mode==="details"?($("dMenu").hidden?$("details"):$("dMenu")):mode==="customize"?$("customize"):null; // (beta.7: the More menu, while open, is all the D-pad moves through)
      if(panel){held.dir=0;if(h&&!prevBtn.h){setInput("pad");padNav(panel,h>0?"right":"left")}
        // up / down with nothing further that way: page through the text instead (the top of Details has no buttons)
        if(v&&!prevBtn.v){setInput("pad");if(!padNav(panel,v>0?"down":"up"))panelScroll(panel,v)}
        // right stick: scroll the panel freely, faster the further it's pushed
        {const ry=ax[3]||0;if(Math.abs(ry)>.22){setInput("pad");panel.scrollTop+=ry*Math.abs(ry)*26*padF}}if(mode==="customize"){edge(gp,4,()=>custStep(-1));edge(gp,5,()=>custStep(1))}}
      else{
      if(h){setInput("pad");if(h!==held.dir){held={dir:h,t0:t,tl:t};move(h)}else{const f=scrollCfg.f,since=t-held.t0,gap=(since>1400*f?45:since>700*f?75:110)*f;if(since>320*f&&t-held.tl>gap){held.tl=t;move(h)}}}else held.dir=0; // Settings → Controller → Scroll speed
      if(v&&!prevBtn.v){setInput("pad");sysStep(v)}
      }
      prevBtn.v=!!v;prevBtn.h=!!h;
      edge(gp,0,()=>{if(!(panel&&padActivate(panel)))primary()});edge(gp,1,()=>back());edge(gp,2,()=>{if(undoHide&&(mode==="browse"||mode==="details")){undoHide();return}mode==="details"?closeDetails():openDetails()}); // X right after hiding a game: Undo
      if(mode==="details"){
        // Y: trailer when there is one (hold for achievements), otherwise achievements, otherwise flip; bumpers flick screenshots
        const hasTr=!$("dTrailer").hidden,hasAch=achReady(); // the row while loading / with no set doesn't count (Y fetched again then)
        if(B(3)&&!prevBtn[3])yAt=t;
        if(B(3)&&yAt&&hasTr&&hasAch&&t-yAt>520){yAt=0;setInput("pad");detailsY(true)}
        if(!B(3)&&prevBtn[3]&&yAt){yAt=0;setInput("pad");detailsY()}
        prevBtn[3]=B(3);
        edge(gp,4,()=>{setInput("pad");dMenuClose(true);dTab(-1)});edge(gp,5,()=>{setInput("pad");dMenuClose(true);dTab(1)}); // (beta.7) bumpers: the tabs (screenshots are buttons in Media now)
      }else{
        // Y: tap flips, hold (~half a second) favorites
        if(B(3)&&!prevBtn[3])yAt=t;
        if(B(3)&&yAt&&t-yAt>520){yAt=0;setInput("pad");toggleFav()}
        if(!B(3)&&prevBtn[3]&&yAt){yAt=0;setInput("pad");flip()}
        prevBtn[3]=B(3);
      }
      tap(gp,9,t,()=>{if(mode==="browse"||mode==="details")qmOpen()});if(B(9)&&!prevBtn.b9At)prevBtn.b9At=t;if(B(9)&&prevBtn.b9At&&t-prevBtn.b9At>650){prevBtn.b9At=0;tapAt[9]=0;setInput("pad");openSort()}if(!B(9))prevBtn.b9At=0;tap(gp,11,t,()=>openInspect());tap(gp,10,t,()=>shuffle());tap(gp,8,t,()=>{if(mode==="customize")closeCustomize();else if(mode==="browse"||mode==="details")Setup.open()});if(B(8)&&!prevBtn.b8At)prevBtn.b8At=t;if(B(8)&&prevBtn.b8At&&t-prevBtn.b8At>650){prevBtn.b8At=0;tapAt[8]=0;setInput("pad");openMuseum()}if(!B(8))prevBtn.b8At=0;
      edge(gp,4,()=>letter(-1));edge(gp,5,()=>letter(1));edge(gp,6,()=>sysStep(-1));edge(gp,7,()=>sysStep(1));
      const rx=ax[2]||0;
      // right stick turns the box the way Inspect does: the stick sets a target, the box eases after it, and it settles on the nearest side when you let go
      if(Math.abs(rx)>.2&&mode==="browse"&&slots[sel]){padSpinActive=true;spinT+=rx*Math.abs(rx)*2.4*padF;spinEase()}
      else if(padSpinActive){padSpinActive=false;spinSettle()}
    }
  }
}

/* ---------- controller focus inside a panel ---------- */
function padItems(root){return [...root.querySelectorAll("button,input[type=checkbox],select,a[href],summary")].filter(e=>!e.disabled&&!e.hidden&&e.offsetParent&&e.getClientRects().length)}
// bring a control into view by scrolling only the panels that scroll (scrollIntoView also scrolled the whole app
// sideways while a panel was still sliding in, which knocked the Details panel half off its place)
// safety net: if anything still scrolls the app, put it straight back
[document.documentElement,document.body,document.getElementById("app")].forEach(n=>n&&n.addEventListener("scroll",()=>{if(n.scrollLeft||n.scrollTop){n.scrollLeft=0;n.scrollTop=0}},{passive:true}));
// (beta.9, Windows) a Details screenshot: the strip glides to centre it (a smooth scroll, the way a click already does) instead
// of jumping just far enough to show it
function shotCentre(el){const s=el.parentElement;if(!s)return;const to=Math.max(0,Math.min(s.scrollWidth-s.clientWidth,el.offsetLeft-(s.clientWidth-el.offsetWidth)/2));
  if(Math.abs(to-s.scrollLeft)>1)s.scrollTo({left:to,behavior:document.body.classList.contains("reduce-motion")?"auto":"smooth"})}
function revealIn(el){const shot=!ANDROID&&el.classList&&el.classList.contains("d-shot")&&el.closest(".details.d-one");if(shot)shotCentre(el);
  for(let p=el.parentElement;p&&p!==document.body;p=p.parentElement){const cs=getComputedStyle(p),sy=/(auto|scroll)/.test(cs.overflowY)&&p.scrollHeight>p.clientHeight,sx=!(shot&&p===el.parentElement)&&/(auto|scroll)/.test(cs.overflowX)&&p.scrollWidth>p.clientWidth;if(!sy&&!sx)continue;
  const r=el.getBoundingClientRect(),q=p.getBoundingClientRect();if(sy){if(r.top<q.top+8)p.scrollTop-=q.top+8-r.top;else if(r.bottom>q.bottom-8)p.scrollTop+=r.bottom-q.bottom+8}if(sx){if(r.left<q.left+8)p.scrollLeft-=q.left+8-r.left;else if(r.right>q.right-8)p.scrollLeft+=r.right-q.right+8}}}
// (beta.9) Details buttons (Windows): Settings' highlight glides to the button you're on: one layer behind the row, moved with a
// transform (setup.css .d-hl). Measured once per move, after the label opens; never on the Thor, which keeps its own look
function dGlide(el){if(ANDROID)return;const bar=$("details").querySelector(".d-actions");if(!bar)return;let hl=bar.querySelector(":scope>.d-hl");
  if(!el||el.id==="dPlay"||el.parentNode!==bar||innerWidth<761){ /* (under 761 px Details is the phone-style sheet: no glide) */if(hl)hl.classList.remove("on");return}
  if(!hl){hl=document.createElement("i");hl.className="d-hl";hl.setAttribute("aria-hidden","true");bar.prepend(hl)}
  const first=!hl.classList.contains("on");if(first)hl.style.transition="opacity .16s";
  hl.style.width=el.offsetWidth+"px";hl.style.height=el.offsetHeight+"px";hl.style.transform=`translate(${el.offsetLeft}px,${el.offsetTop}px)`;hl.classList.add("on");
  if(first)requestAnimationFrame(()=>{hl.style.transition=""})}
function padSet(root,el){root.querySelectorAll(".pad-focus").forEach(e=>e.classList.remove("pad-focus"));if(!el){if(root.id==="details")dGlide(null);return}el.classList.add("pad-focus");try{el.focus({preventScroll:true})}catch{}if(root.id==="details")dGlide(el); // (after the focus moves: the button left behind closes first)
  // the Details buttons sit in a bar pinned to the bottom: always in view, so moving onto them never scrolls the text
  if(el.closest(".d-actions"))return;revealIn(el);
  // scroll only the panel itself (scrollIntoView also scrolled the whole app sideways and dragged a hidden panel into view);
  // what's under the pinned button bar counts as out of view
  const sc=el.closest(".settings,.details,.d-scroll")||root,pr=sc.getBoundingClientRect(),er=el.getBoundingClientRect(),bar=sc.querySelector(":scope>.d-actions"),bot=bar?Math.min(pr.bottom,bar.getBoundingClientRect().top):pr.bottom;
  if(er.bottom>bot-12)sc.scrollTop+=er.bottom-bot+24;else if(er.top<pr.top+12)sc.scrollTop-=pr.top-er.top+24}
// moves to the nearest item in that direction (left/right fall back to the previous/next item in reading order)
function padNav(root,dir){
  const L=padItems(root);if(!L.length)return;const cur=root.querySelector(".pad-focus");
  if(!cur||!L.includes(cur)){padSet(root,L[0]);play_("tick",{dir:1});return true}
  const r=cur.getBoundingClientRect(),cx=r.left+r.width/2,cy=r.top+r.height/2;let best=null,bs=Infinity;
  for(const e of L){if(e===cur)continue;const q=e.getBoundingClientRect(),x=q.left+q.width/2,y=q.top+q.height/2,dx=x-cx,dy=y-cy;
    const ok=dir==="left"?dx<-4&&Math.abs(dy)<r.height:dir==="right"?dx>4&&Math.abs(dy)<r.height:dir==="up"?dy<-4:dy>4;if(!ok)continue;
    // up / down: side-by-side distance counts from the edges, so a full-width button right below (Play) wins over a small one nearer the middle
    const gx=Math.max(0,q.left-r.right,r.left-q.right);const sc=dir==="left"||dir==="right"?Math.abs(dx)+Math.abs(dy)*3:Math.abs(dy)+gx*.6;if(sc<bs){bs=sc;best=e}}
  if(!best&&(dir==="left"||dir==="right")){const i=L.indexOf(cur)+(dir==="right"?1:-1);best=L[i]||null}
  if(best){padSet(root,best);play_("tick",{dir:dir==="left"||dir==="up"?-1:1});return true}
  return false
}
function panelScroll(el,d){const max=el.scrollHeight-el.clientHeight;if(max<=0)return;const to=Math.max(0,Math.min(max,el.scrollTop+d*el.clientHeight*.45));if(to===el.scrollTop)return;el.scrollTo({top:to,behavior:document.body.classList.contains("reduce-motion")?"auto":"smooth"});play_("tick",{dir:d})}
function padActivate(root){const e=root.querySelector(".pad-focus");if(!e||!padItems(root).includes(e))return false;e.click();return true}
/* ---------- misc ---------- */
// (beta.10) toasts take turns: one that comes while another is up waits for it (the same text twice: shown once), each up
// ~60 ms a character (2.6 to 8 s). Your own action's answer (input in the last 0.4 s) shows at once; a working note and its
// "done" too, as before. A toast with Undo is only ended by you (Undo, or doing something that answers) or its time running out
let toastT,undoHide=null,toastCur=null;const toastQ=[];
const toastMs=m=>Math.max(2600,Math.min(8000,String(m).length*60)),toastMine=()=>performance.now()-lastInputAt<400;
function toastShow(x){const el=$("toast");clearTimeout(toastT);toastT=0;undoHide=null;toastCur=x;
  if(el.textContent!==x.msg||el.firstElementChild)el.textContent=x.msg;el.classList.remove("work","done");el.classList.add("on");if(x.kind==="work"||x.kind==="done")el.classList.add(x.kind);
  if(x.kind!=="work")toastT=setTimeout(toastEnd,x.kind==="done"?Math.max(6000,toastMs(x.msg)):toastMs(x.msg))}
function toastEnd(){clearTimeout(toastT);toastT=0;toastCur=null;undoHide=null;if(toastQ.length){toastShow(toastQ.shift());return}$("toast").classList.remove("on","done")}
function toastAdd(x){if(!x.msg)return;const c=toastCur,overUndo=!!undoHide&&!toastMine();
  if(c&&c.msg===x.msg&&c.kind===x.kind&&!undoHide){if(x.kind!=="work")toastShow(x);return} // the same text again: its time starts over
  if(toastQ.some(q=>q.msg===x.msg&&q.kind===x.kind))return;
  if(!c||(!overUndo&&(x.kind!=="plain"||c.kind==="work"||toastMine()))){toastShow(x);return}
  if(x.kind==="work"){const i=toastQ.findIndex(q=>q.kind==="work");if(i>=0){toastQ[i]=x;return}} // (a working note waiting behind an Undo: only its newest text)
  toastQ.push(x);if(toastQ.length>8)toastQ.shift()}
function toast(msg){toastAdd({msg:String(msg==null?"":msg),kind:"plain"})}
// (beta.9) a message that stays up while something works (a turning ring), changing as it goes, then a plain "done" with a
// tick (the manual fixes showed "Repairing…" for 2.6 s and then nothing until the result popped up)
function toastWork(msg){msg=String(msg);const c=toastCur;if(c&&c.kind==="work"&&!undoHide){c.msg=msg;const el=$("toast");if(el.textContent!==msg)el.textContent=msg;return}toastAdd({msg,kind:"work"})}
function toastDone(msg){toastAdd({msg:String(msg),kind:"done"})}
// (beta.10) Background box art (main.js autoCovers): a small card in the shelf's bottom-left corner, "Box art 312 of 2,000",
// with Pause / Resume and Cancel for the mouse and touch. A controller gets the same in the quick menu ("Box art download"
// row), so the card never takes the D-pad from the shelf. Only where main can pause or cancel the run (the Android build's
// API can't: it never shows there); it goes when the run ends. Its updates wait while the shelf slides
function artCardText(){const n=x=>Number(x||0).toLocaleString();return ARTC.stopping?"Stopping…":ARTC.total?`${n(Math.min(ARTC.done,ARTC.total))} of ${n(ARTC.total)}${ARTC.paused?" · paused":""}`:"Getting ready…"}
function artCardEl(){if(ARTC.el)return ARTC.el;const el=document.createElement("div");el.className="art-card";el.setAttribute("role","status");
  el.innerHTML='<div class="ac-top"><span class="ac-t">Box art</span><span class="ac-n"></span></div><div class="ac-bar"><i></i></div><div class="ac-btns"><button type="button" class="ac-b" data-k="pause" tabindex="-1"></button><button type="button" class="ac-b" data-k="cancel" tabindex="-1">Cancel</button></div><div class="ac-hint"></div>';
  el.addEventListener("click",e=>{const b=e.target.closest(".ac-b");if(!b)return;e.stopPropagation();b.blur();setInput("pointer");artCmd(b.dataset.k)});app.append(el);ARTC.el=el;return el}
function artCardPaint(){clearTimeout(ARTC.t);ARTC.t=0;if(!ARTC.on)return;if(window.BS_BUSY&&BS_BUSY()){ARTC.t=setTimeout(artCardPaint,500);return}
  const el=artCardEl(),p=el.querySelector('[data-k="pause"]');el.querySelector(".ac-n").textContent=artCardText();el.querySelector(".ac-bar i").style.transform=`scaleX(${ARTC.total?Math.min(1,ARTC.done/ARTC.total).toFixed(3):0})`;
  p.hidden=!api.pauseCovers||ARTC.stopping;p.textContent=ARTC.paused?"Resume":"Pause";el.querySelector('[data-k="cancel"]').hidden=!api.cancelCovers||ARTC.stopping;
  el.querySelector(".ac-hint").innerHTML=showPad()&&!ARTC.stopping?`${glyph("☰")}<span>Menu: pause or cancel</span>`:"";el.classList.toggle("paused",ARTC.paused);
  if(!el.classList.contains("on"))requestAnimationFrame(()=>{if(ARTC.on&&ARTC.el)ARTC.el.classList.add("on")});
  if(qm.open&&qm.sub==null){const x=qm.rows.find(x=>x.r.id==="artrun"),v=x&&x.el.querySelector(".qm-val");if(v)v.textContent=artCardText()}} // (the open menu's row: its text only)
function artCardOn(i){i=i||{};if(!api.pauseCovers&&!api.cancelCovers)return;const was=ARTC.on,wasP=ARTC.paused;
  if(i.end){const cut=ARTC.stopping;ARTC.on=false;ARTC.paused=false;ARTC.stopping=false;clearTimeout(ARTC.t);ARTC.t=0;if(ARTC.el)ARTC.el.classList.remove("on");if(was&&qm.open)qsSoft();
    // (beta.10) the card says how it went as it goes (main's got / failed; a run you cancelled with nothing yet: nothing to say).
    // Its "N added" stands in for the library's own "Added covers for N new games" that follows (ARTC.said, see onLibraryChanged)
    const got=Math.max(0,+i.got||0);ARTC.said=0;
    if(was&&i.failed)toast("Couldn't reach the art sites. Try again from Settings → Box art");
    else if(was&&typeof i.got==="number"&&(got||!cut)){if(got){ARTC.said=got;ARTC.saidAt=performance.now()}toast(got?`Box art: ${got.toLocaleString()} added`:"No new box art found")}
    return}
  ARTC.on=true;if(i.allTotal!=null){ARTC.done=+i.allDone||0;ARTC.total=+i.allTotal||0}if(typeof i.paused==="boolean"&&!ARTC.stopping)ARTC.paused=i.paused;
  artCardPaint();if(qm.open&&(!was||wasP!==ARTC.paused))qsSoft()}
// Pause / Resume / Cancel, from the card or the quick menu. Main says ok:false when no run is there any more: the card goes
async function artCmd(k){if(!ARTC.on||ARTC.stopping)return;
  if(k==="pause"||k==="resume"){const fn=k==="pause"?api.pauseCovers:api.resumeCovers;if(!fn)return;let r=null;try{r=await fn()}catch{}if(!ARTC.on)return;
    if(!r||!r.ok){artCardOn({end:true});return}ARTC.paused=k==="pause";play_("tick",{dir:k==="pause"?-1:1})}
  else if(k==="cancel"){if(!api.cancelCovers)return;ARTC.stopping=true;ARTC.paused=false;play_("back");try{await api.cancelCovers({auto:true})}catch{}if(!ARTC.on)return}
  else return;
  artCardPaint();if(qm.open)qsSoft()}
if(api&&api.onCoversAuto&&(api.pauseCovers||api.cancelCovers))api.onCoversAuto(artCardOn);
window.BS_artCard=artCardOn; // (tests: the demo page has no background run to feed it)
// (beta.10) the graphics process went away and came back (main.js): the cases go up again, as a step to another shelf puts
// them up (they could stay black until then). Never mid-slide; elsewhere (the museum, a game) it waits for the shelf
let gpuT=0,gpuTries=0;
function gpuBack(){clearTimeout(gpuT);gpuT=0;if(mode!=="browse"||(window.BS_BUSY&&BS_BUSY())||sysBusy()){if(++gpuTries<90)gpuT=setTimeout(gpuBack,2000);return}gpuTries=0;
  try{flipped=false;spin=0;spinT=0;cancelAnimationFrame(spinRaf);spinRaf=0;build();updateBg()}catch(e){try{api.log("gpu back: "+(e&&e.message||e))}catch{}}}
if(api&&api.onGpuBack)api.onGpuBack(()=>{gpuTries=0;clearTimeout(gpuT);gpuT=setTimeout(gpuBack,300)});
$("dAchBtn").addEventListener("click",()=>achAct());
let HIST={},shotSel=0;
function fmtPlay(ms){const m=Math.round((ms||0)/60000);if(m<1)return "under a minute";if(m<60)return m+" min";const h=Math.floor(m/60);return h+" h"+(m%60?" "+(m%60)+" min":"")} // no trailing space on whole hours ("2 h played")
// Restore settings / Clear play history (main process): Home, recent sorts, plaques and Details read the new history
if(api&&api.onHistoryChanged)api.onHistoryChanged(()=>loadHistory());
// the page is zoomed up on screens taller than 1080 (main.js applyScale); the 3D views keep full sharpness with it
// the 3D views cap pixels per 1080p-sized pixel: the whole page zoom counts (4K auto × the Interface size setting), else 115 / 130 % renders soft
window.BS_AUTOZOOM=1;if(api&&api.onUiScale)api.onUiScale(d=>{const a=Math.max(1,Math.min(2.6,+(d&&d.zoom)||+(d&&d.auto)||1));if(a===window.BS_AUTOZOOM)return;window.BS_AUTOZOOM=a;try{window.dispatchEvent(new Event("resize"))}catch{}});
function loadHistory(){if(api.history)api.history().then(h=>{HIST=h||{};bootMark("hist");if((/recent|most/.test(sortBy)||/played/.test(filterBy))&&cur()&&cur().kind!=="home"&&(mode==="browse"||mode==="details")){applyArrange();return}if(/recent|most/.test(sortBy)||/played/.test(filterBy))arrange();const onHome=cur()===HOME,had=shelves.includes(HOME),keep=curGame();buildHome();if(onHome||had!==shelves.includes(HOME)){if(onHome&&shelves.includes(HOME)){const i=HOME.items.indexOf(keep);sel=i>=0?i:Math.max(0,Math.min(sel,HOME.items.length-1));build()}else if(onHome&&shelves.length){/* Home went away (play history cleared, no favourites): land on the first shelf */sysIdx=0;sel=Math.max(0,cur().items.findIndex(isGame));flipped=false;closeDetails(true);build();brandIn(1);tuneTo(cur());save()}else renderTabs()}if(mode==="details")fillDetails()}).then(homeStartCheck).catch(()=>{})}
// Open into → Home: at start-up the Home shelf may only appear once the play history is in (it lists recent games)
function homeStartCheck(){if(openInto!=="home-wait")return;openInto="done";const i=shelves.indexOf(HOME);if(i<0||i===sysIdx||mode!=="browse"||sysBusy()||lastInputAt>0)return;
  if(!revealedAt){sysIdx=i;sel=0;flipped=false;spin=0;spinT=0;build();tuneTo(cur())}else setSystem(i)}
function showShot(k){const g=curGame(),shots=(g&&g.info&&g.info.shots)||[];if(!shots.length)return;shotSel=((k%shots.length)+shots.length)%shots.length;heroBgSoon(shots[shotSel],g);[...$("dShots").children].forEach((d,j)=>d.classList.toggle("on",j===shotSel));const el=$("dShots").children[shotSel];if(el)el.scrollIntoView({inline:"center",block:"nearest",behavior:"smooth"});play_("tick",{dir:1})}
function openTrailer(){const g=curGame();if(!g||!g.info||!g.info.video)return;play_("open");api.openTrailer(g.info.video).then(r=>{if(r&&r.external)toast("Trailer opened in your browser")}).catch(e=>toast(e.message))}
$("dTrailer").addEventListener("click",openTrailer);
$("dMore").addEventListener("click",()=>{const d=$("dDesc"),m=$("dMore");const c=d.classList.toggle("clamp");m.textContent=c?"Read more":"Show less"});
$("dPlay").addEventListener("click",play);$("dInspect").addEventListener("click",openInspect);$("dManual").addEventListener("click",()=>{openInspect();setTimeout(()=>{if(mode==="inspect"){ispAdvance();setTimeout(openReader,700)}},150)});$("dEmu").addEventListener("click",openEmulator);$("dClose").addEventListener("click",()=>closeDetails());
$("launch").addEventListener("click",()=>{if($("lNote").classList.contains("on"))back()});
$("themeBtn").addEventListener("click",toggleCustomize);$("setClose").addEventListener("click",closeCustomize);
$("settingsBtn").addEventListener("click",()=>Setup.open());$("museumBtn").addEventListener("click",()=>openMuseum());$("emptySetup").addEventListener("click",()=>Setup.open({wizard:true}));
let rz;addEventListener("resize",()=>{clearTimeout(rz);rz=setTimeout(()=>{if(!cur())return;measure();layout(true)},80)});

// First start: the shelf stays hidden for a moment (at most ~0.9 s) until the covers around the selected game are
// loaded and decoded, then fades in, instead of showing a row of blank cases that fill in one by one.
let coversShownOnce=false;
// The start-up reveal: the Boxshelf logo (shown from the very first paint, see splash.js) stays at least ~1 s so it never
// just flashes, then fades as the shelf comes in: the chosen case first and the rest rippling outward ("ripple"), or
// the whole shelf fading up ("fade"), or straight in with no logo ("off").
const SPLASH_T0=performance.now();let revealedAt=0;
let shelfShown=false,revealDone=false;
setTimeout(()=>{if(!revealDone){revealDone=true;shelfShown=true;revealedAt=revealedAt||performance.now();app.classList.remove("covers-wait");afterReveal()}const sp=document.getElementById("splash");if(sp&&!sp.classList.contains("out")){app.classList.remove("covers-wait");sp.classList.add("out");setTimeout(()=>{sp.remove();try{window.__splash&&window.__splash.worker.terminate()}catch{}},900)}},5000); // never stuck on the logo (and its drawing thread goes with it, as in revealShelf)
function introStyle(){const v=cfgInfo&&cfgInfo.config.ui.bootIntro||(document.documentElement.dataset.intro)||"ripple";const s=["ripple","fade","off"].includes(v)?v:"ripple";return s==="ripple"&&reduced()?"fade":s} // Reduce motion: the cases fade up instead of rippling in
// Settings → Display → Open into: "last" (where you left off), "home" (the Home shelf) or "museum" (the museum opens
// right after the start-up logo; B from it lands on the shelf as usual). Read once, at the first start-up build.
let openInto=null,musBootSplash=null;
function startIntoMuseum(){return openInto==="museum"&&!!(cfgInfo&&cfgInfo.config.setupDone)&&!!(window.Museum&&Museum.available())}
function prewarmSettings(){setTimeout(()=>{const go=()=>{if((mode==="browse")&&cfgInfo&&cfgInfo.config.setupDone&&window.Settings&&Settings.prewarm&&!document.hidden){Settings.prewarm(cfgInfo);if(window.Sound&&Sound.warm)setTimeout(()=>window.requestIdleCallback?requestIdleCallback(()=>Sound.warm(),{timeout:5000}):Sound.warm(),400) /* building the sound buffers waits for a quiet moment (not mid-scroll) */}else if(!prewarmSettings.n||prewarmSettings.n<5){prewarmSettings.n=(prewarmSettings.n||0)+1;prewarmSettings()}};
  if(window.requestIdleCallback)requestIdleCallback(go,{timeout:3000});else go()},2500)} // a few seconds after start-up, when nothing else is going on
function afterReveal(){prewarmSettings();if(!startIntoMuseum())return;openInto="done";if(mode==="browse"&&!sysBusy())openMuseum()}
function revealShelf(){
  const st=introStyle(),sp=document.getElementById("splash"),S=window.__splash;
  try{localStorage.setItem("bootIntro",st)}catch{}
  const reveal=()=>{if(revealDone)return;revealDone=true;
    const rT=performance.now();
    if(st==="ripple"){mounted().forEach(s=>{const d=Math.abs(slots.indexOf(s)-sel);s.el.style.setProperty("--rd",Math.min(9,d)*22+"ms")})}
    if(st!=="off"&&!startIntoMuseum())app.classList.add("intro-"+st); // straight into the museum: no ripple under its fade
    app.classList.remove("covers-wait");
    const splashOut=()=>{if(sp&&!sp.classList.contains("out")){sp.classList.add("out");setTimeout(()=>{sp.remove();try{S&&S.worker.terminate()}catch{}},900)}};
    revealedAt=performance.now();bootMark("reveal("+Math.round(revealedAt-rT)+"ms)");
    setTimeout(()=>{app.classList.remove("intro-ripple","intro-fade");mounted().forEach(s=>s.el.style.removeProperty("--rd"))},window.PulseHost?900:1700);
    // Open into → Museum: the logo stays up while the room is built behind it and hands over straight to the museum
    // (it used to fade out to the shelf first, then the museum faded in over it)
    if(sp&&startIntoMuseum()&&mode==="browse"&&!sysBusy()){openInto="done";const m=$("museum");m.classList.add("instant");musBootSplash=()=>{musBootSplash=null;m.classList.remove("instant");splashOut()};openMuseum();
      if(mode!=="museum"){m.classList.remove("instant");musBootSplash=null;splashOut()}else setTimeout(()=>{if(musBootSplash)musBootSplash()},4500);prewarmSettings();return} // safety: never leave the logo up
    splashOut();afterReveal()};
  if(st==="off"||!sp){reveal();return}
  // The shelf is built, its covers decoded and drawn (hidden): only now does the light sweep across the logo, with the
  // busy part of the launch over, then the shelf comes in. A short pause first lets the last of the loading settle.
  let fired=false;const fire=d=>{if(fired)return;fired=true;if(d&&d.frames)bootMark(`logo ${d.frames}f avg${d.avg}ms max${d.max}ms slow${d.slow}`);reveal()};
  setTimeout(()=>{
    if(S&&S.ready){S.onDone=fire;S.worker.postMessage({play:true});setTimeout(()=>fire(null),window.PulseHost?650:2200)}
    else fire(null)},Math.max(60,(window.PulseHost?200:600)-(performance.now()-SPLASH_T0)))}
function holdForCovers(){
  coversShownOnce=true;app.classList.add("covers-wait");const t0=performance.now();
  const show=()=>{if(shelfShown)return;shelfShown=true;bootMark(performance.now()-t0>900?"shown-timeout":"shown");revealShelf()};
  const check=()=>{
    if(performance.now()-t0>900)return show();
    const near=mounted().filter(s=>Math.abs(slots.indexOf(s)-sel)<=4);
    const ok=near.length&&near.every(s=>s.img&&!s.coverP&&(!s.img.getAttribute("src")?false:s.img.complete));
    if(ok)return (near[0].img.decode?Promise.all(near.map(s=>s.img.decode().catch(()=>{}))):Promise.resolve()).then(show);
    requestAnimationFrame(check)};
  requestAnimationFrame(check);
}
// a settings change still waiting out its short save delay: write it now, so re-reading the config doesn't undo it
async function flushSaves(){
  if(musLightT){clearTimeout(musLightT);musLightT=0;if(cfgInfo)try{await api.saveConfig(cfgInfo.config)}catch{}}
  if(!(spSaveT||volSaveT||qmSaveT||musStyleSaveT))return;
  if(spSaveT&&cfgInfo){cfgInfo.config.ui.sort=sortBy;cfgInfo.config.ui.filter=filterBy}
  [spSaveT,volSaveT,qmSaveT,musStyleSaveT].forEach(clearTimeout);spSaveT=volSaveT=qmSaveT=musStyleSaveT=0;
  if(cfgInfo)try{await api.saveConfig(cfgInfo.config)}catch{}
}
async function refresh(keepPlace,lib){
  let fresh=null;
  try{await flushSaves();const was=cfgInfo;cfgInfo=await api.getConfig();if(was&&was.config&&cfgInfo&&cfgInfo.config&&(spSaveT||volSaveT||qmSaveT||musStyleSaveT))cfgInfo.config.ui=Object.assign({},cfgInfo.config.ui,was.config.ui);
  // (1.82) the settings file was locked when Boxshelf started (antivirus, a sync tool): running on defaults, nothing saved
  if(cfgInfo&&cfgInfo.settingsBlocked&&!refresh.blockedSaid){refresh.blockedSaid=1;pendingNote="Boxshelf couldn't read your settings (another program had the file open). Restart Boxshelf to load them; changes made now aren't saved."}
  // (1.76) the old Background → Game artwork choice is now the Artwork theme (Windows' config.js does the same on load)
  if(cfgInfo&&cfgInfo.config&&cfgInfo.config.ui&&cfgInfo.config.ui.background==="art"){cfgInfo.config.ui.theme="artwork";cfgInfo.config.ui.background="blur";api.saveConfig(cfgInfo.config).catch(()=>{})} /* a setting changed while the config was being re-read: keep it (its save is still on the way) */soundOn=cfgInfo.config.ui.sound!==false;if(window.Sound)Sound.set({enabled:soundOn,boot:cfgInfo.config.ui.bootSound||"chime",theme:cfgInfo.config.ui.soundTheme||"aero",volume:cfgInfo.config.ui.volume==null?.7:cfgInfo.config.ui.volume});if(window.Sound&&Sound.loadSongs&&!songsLoaded){songsLoaded=true;Sound.loadSongs(api,cfgInfo.config.ui.builtInSongs)}(window.Perf&&Perf.set(cfgInfo.config.ui.performance||"auto"),applyEffects());paintVol();themeOverride=null;themeCls();if(api.customThemes&&!loadCustomThemes.done){loadCustomThemes.done=1;loadCustomThemes()}buttonsPref=cfgInfo.config.ui.buttons||"auto";applyUiAll();updateSkipText();fresh=lib||await api.scan()}catch(err){toast(err.message);fresh=[]}
  if(Array.isArray(fresh))lastLib=fresh;
  // a scan can take a few seconds: if you've moved on meanwhile (playing, museum, Inspect, mid-switch), apply it later
  if(keepPlace&&((mode!=="browse"&&mode!=="details")||sysBusy()||(window.BS_BUSY&&BS_BUSY()))){pendingLib=fresh;return} // (beta.10) mid-slide too: never rebuilt under a scroll
  pendingLib=null;pendingRefresh=false; // this refresh is newer than anything waiting
  const prevId=cur()&&cur().id,prevItem=curGame()&&curGame().id; // where you are *now*, not when the scan started
  try{shelves=prepare(fresh)}catch(err){toast(err.message);shelves=[]}
  allShelves=shelves;setTimeout(sendShelfInfo,0);sortBy=(cfgInfo&&cfgInfo.config.ui.sort)||"az";filterBy=(cfgInfo&&cfgInfo.config.ui.filter)||"all";arrange();buildHome();
  if(!keepPlace&&openInto===null){const o=cfgInfo&&cfgInfo.config.ui.openInto;openInto=o==="home"||o==="museum"?o:"last"}
  const homeStart=!keepPlace&&openInto==="home"; // Open into → Home shelf (it's the first shelf whenever it exists)
  const want=keepPlace?prevId:homeStart?"home":saved.sys;
  sysIdx=Math.max(0,shelves.findIndex(s=>s.id===want));
  sel=0;const items=cur()?cur().items:[];
  const wantItem=keepPlace?prevItem:null;if(wantItem){const i=items.findIndex(g=>g.id===wantItem);if(i>=0)sel=i}else if(!keepPlace&&saved.sel!=null&&saved.sel<items.length&&(cur()&&cur().id)===saved.sys)sel=saved.sel;
  if(homeStart)openInto="home-wait"; // Home may only fill once play history arrives: loadHistory finishes the job
  if(!keepPlace&&!coversShownOnce)holdForCovers();
  // the background scan came back with changes somewhere else: the shelf in view is the same, so keep its cases as
  // they are (rebuilding it made every case and cover load in again, a visible stutter right after startup)
  const slotSig=l=>l.map(g=>g.id+"|"+g.title+"|"+(g.art&&(g.art.appIcon?"i:"+g.art.appIcon:g.art.front)||"")+"|"+(g.art&&g.art.spine||"")+"|"+(isFav(g)?1:0)).join("\n");
  if(keepPlace&&slots.length&&cur()&&cur().items.length===slots.length&&slotSig(cur().items)===slotSig(slots.map(x=>x.g))&&sel===slots.findIndex(x=>x.g.id===prevItem)){cur().items.forEach((g,i)=>{const o=slots[i].g;if(o.kind==="random"&&g.kind==="random"){cur().items[i]=o;return} /* keep the Shuffle tile's own object: it holds the pick being shown (and its fold-away timer) */if(o.art&&o.art.icon&&o.art.front&&g.art&&!g.art.front)g.art.front=o.art.front;slots[i].g=g});renderTabs();updateInfo();bootMark("kept-shelf")}
  else{flipped=false;spin=0;spinT=0;cancelAnimationFrame(spinRaf);spinRaf=0;build()}preloadModel();iconCovers();raShelfLoad();tuneTo(cur());ambient(true);loadHistory();
  if(qm.open)qsSoft(); // the start-up background rescan landed under an open quick menu: its game card and rows follow (and a theme preview comes back)
}

// Effects level: "auto" turns the heavier extras off on screens above 1080p, where they cost the most.
// Since 1.55 the effects simply follow the Performance level (the separate Visual effects switch is gone from Settings,
// so a stored ui.effects "full"/"lite" is ignored on purpose): High and Balanced → on, except on screens above
// 1920×1200 where they cost the most; Low → off. `pref` is kept in the signature for older callers.
function applyEffects(){
  // effects follow the Performance level: High = everything on (reflections, glow, full-resolution Inspect) on any
  // screen; Balanced = on up to 1920x1200, off above (where they cost the most); Low = off
  const big=(screen.width*screen.height)>(1920*1200),lvl=window.Perf?Perf.get().level:"high";
  fxOn=lvl==="high"?true:lvl==="balanced"?!big:false;
  document.body.classList.toggle("fx",fxOn);
  if(cur()){measure();layout(true)}
}
/* ---------- 1.55 preferences: Fit to TV, clock, reduce motion, dimming, confirm button, scroll speed, audio output ----------
   All of them apply live (Shelf.applyUi from Settings) and at start-up (applyUiAll from refresh). */
const uiNow=()=>(cfgInfo&&cfgInfo.config.ui)||{};
// Fit to TV: the whole interface is scaled into the middle 90–100 % of the screen (for TVs that crop the edges). The
// backdrops and the museum's 3D room are scaled back out so they still fill the screen; only text and controls move in.
let safeScale=1;
function applySafeArea(p){p=+p;if(!(p>=90&&p<=100))p=100;const s=Math.round(p)/100;if(s===safeScale)return;safeScale=s;
  const r=document.documentElement.style;r.setProperty("--safe",String(s));r.setProperty("--safe-inv",(1/s).toFixed(5));document.body.classList.toggle("safe-area",s<1)}
function applyReduceMotion(on){on=!!on;if(on===reduceMotion&&document.body.classList.contains("reduce-motion")===on)return;reduceMotion=on;document.body.classList.toggle("reduce-motion",on);if(window.ThemeFx&&ThemeFx.refresh)ThemeFx.refresh()}
// Confirm button: "a" (Xbox / PlayStation style: bottom button confirms) or "b" (Nintendo style: the right button
// confirms). Swapped once, where the controller is read (padView), so every screen (shelf, Details, Inspect, reader,
// museum, Sort, quick menu, Settings) gets it; the prompts swap with it (glyph / btnWord). window.BS_PAD tells others.
// Confirm button: "a" = the bottom button, "b" = the right one, "auto" = as the controller in hand expects (the right
// one, A, on a Nintendo pad; the bottom one on Xbox / PlayStation)
let confirmPref="auto";
function applyConfirm(v){confirmPref=v||"auto";const sw=confirmPref==="b"||(confirmPref==="auto"&&glyphStyle()==="nintendo");if(sw===PADMAP.swap)return;PADMAP.swap=sw;window.BS_PAD={confirm:sw?1:0,back:sw?0:1};
  {const a=prevBtn[0];prevBtn[0]=prevBtn[1];prevBtn[1]=a} // the remembered up/down states follow the buttons, so nothing fires from the swap itself
  renderHints.sig="";renderHints();if(mode==="inspect")renderIspHints();if(mode==="museum")renderMusHints();updateSkipText();sendShelfInfo()}
const padView={buttons:[],axes:[],id:"",index:0,mapping:"",connected:true,vibrationActuator:null}; // reused every frame: no allocation
function viewOf(gp){if(!PADMAP.swap||!gp)return gp;const b=padView.buttons,src=gp.buttons,n=src.length;b.length=n;for(let i=0;i<n;i++)b[i]=src[i];if(n>1){b[0]=src[1];b[1]=src[0]}
  padView.axes=gp.axes;padView.id=gp.id;padView.index=gp.index;padView.mapping=gp.mapping;padView.connected=gp.connected;padView.vibrationActuator=gp.vibrationActuator;return padView}
// Audio output: the cue/music context and every <video> Boxshelf plays (boot videos, the museum's retro TV). A device
// that has gone (unplugged) falls back to the default output. Trailers open in their own window and use the default.
let sinkId="";
function sinkFor(){return sinkId==="default"?"":sinkId}
function applySinkTo(el){try{if(!el||typeof el.setSinkId!=="function")return;const want=sinkFor();if((el.sinkId||"")===want)return;el.setSinkId(want).catch(()=>{if(want)el.setSinkId("").catch(()=>{})})}catch{}}
function applyAudioOut(id){id=typeof id==="string"?id:"";if(id===sinkId)return;sinkId=id;window.BS_SINK=sinkFor();if(window.Sound&&Sound.setSink)Sound.setSink(sinkFor());applySinkTo($("bootVideo"))}
async function audioOutputs(){let list=[];try{list=(await navigator.mediaDevices.enumerateDevices()).filter(d=>d.kind==="audiooutput"&&d.deviceId&&d.deviceId!=="communications")}catch{}
  let n=0;const out=[{id:"default",label:"Default"}];list.forEach(d=>{if(d.deviceId==="default"||out.some(o=>o.id===d.deviceId))return;out.push({id:d.deviceId,label:d.label||("Audio output "+(++n))})});return out}
/* Dim the screen after a quiet spell (ui.dimMinutes; 0 = never): a black veil fades over everything. Not while a game,
   a boot video or the museum's TV plays, nor while Boxshelf isn't the window in front (a trailer, another app). Any
   input lifts it, and that input is used up by the waking (it doesn't also move or press anything). */
let keySwallow=false,dimOn=false,dimAt=performance.now(),dimEl=null,dimSwallowPad=false,dimClickUntil=0,dimPausedMuseum=false;
function dimTouch(){dimAt=performance.now()}
function dimAllowed(){if(mode==="launch"||mode==="boot"||document.hidden||!document.hasFocus())return false;if(mode==="museum"){try{if(Museum.tvPowered&&Museum.tvPowered()&&Museum.tvMeta())return false}catch{}}return true}
function dimCheck(){const mins=+uiNow().dimMinutes||0;if(dimOn)return;if(!mins||!dimAllowed()){dimAt=performance.now();return}if(performance.now()-Math.max(dimAt,lastInputAt)>mins*60000)dimStart()}
function dimStart(){if(dimOn)return;if(qm.open)qmClose(true);if(!dimEl){dimEl=document.createElement("div");dimEl.id="bsDim";dimEl.setAttribute("aria-hidden","true");document.body.appendChild(dimEl);void dimEl.offsetWidth}
  dimOn=true;clearTimeout(attractT); /* attract mode rests under the veil (wake() ends it) */document.body.classList.add("dimmed");dimEl.classList.add("on");
  if(mode==="museum"&&window.Museum&&Museum.setPaused){Museum.setPaused(true,"dim");dimPausedMuseum=true}}
function dimWake(){if(!dimOn)return false;dimOn=false;dimAt=performance.now();document.body.classList.remove("dimmed");if(dimEl)dimEl.classList.remove("on");
  if(dimPausedMuseum){dimPausedMuseum=false;if(mode==="museum"&&window.Museum)Museum.setPaused(false,"dim")}wake();return true}
setInterval(dimCheck,5000);
addEventListener("focus",dimTouch);document.addEventListener("visibilitychange",dimTouch);
// perf.js (the first script on the page) hands every key / click / wheel here before anyone else sees it: true = used up
window.BS_INPUT_GATE=e=>{const t=e.type;
  if(smoothTest.on){if(t==='keydown'||t==='pointerdown')smoothTest.stop=true;return true} // the smoothness test: any key or click stops it, and does nothing else
  if(keySwallow){if(t==='keyup'||t==='keydown'&&!e.repeat)keySwallow=false;else if(t==='keydown')return true} // the key that closed Settings: its auto-repeats don't reach the shelf (a fresh press does)
  if(t==="click"){if(performance.now()<dimClickUntil){dimClickUntil=0;return true}return false}
  if(t==="keyup")return dimOn;
  if(dimOn){if(t==="pointerdown")dimClickUntil=performance.now()+900;dimWake();return true}
  dimTouch();
  if(t==="keydown"&&undoHide&&(mode==="browse"||mode==="details")&&!e.repeat&&(e.key==="u"||e.key==="U"||((e.ctrlKey||e.metaKey)&&(e.key==="z"||e.key==="Z")))){undoHide();return true}
  return false};
addEventListener("mousemove",e=>{if((Math.abs(e.movementX)+Math.abs(e.movementY))>2){if(dimOn)dimWake();else dimTouch()}},{passive:true});
// the controller side, from pollBody: true while the press that woke the screen is still held (it is swallowed)
function dimPad(gp,t){let act=false;const bs=gp.buttons;for(let i=0;i<bs.length;i++)if(bs[i]&&bs[i].pressed){act=true;break}
  if(!act&&gp.mapping==="standard"){const a=gp.axes||[];for(let i=0,n=Math.min(4,a.length);i<n;i++)if(Math.abs(a[i])>.5){act=true;break}} // (beta.8: a non-standard pad's trigger can rest at -1 on one of these axes: it looked held forever)
  if(act)dimAt=t;
  if(dimOn&&act){dimWake();dimSwallowPad=true}
  if(!dimSwallowPad)return false;
  if(act){for(let i=0;i<bs.length;i++)prevBtn[i]=!!(bs[i]&&bs[i].pressed)||!!prevBtn[i];prevBtn.h=prevBtn.v=true;held.dir=0;quitHoldAt=0;return true}
  dimSwallowPad=false;for(let i=0;i<bs.length;i++)tapAt[i]=0;return false}
// Settings → applies one or more ui keys at once (the whole set at start-up)
function applyUi_(u){
  if("safeArea" in u)applySafeArea(u.safeArea);
  if("reduceMotion" in u)applyReduceMotion(u.reduceMotion===true);
  if("clock" in u||"clock24" in u)clock();
  if("confirmButton" in u)applyConfirm(u.confirmButton);
  if("scrollSpeed" in u)scrollCfg=SCROLL[u.scrollSpeed]||SCROLL.normal;
  if("audioOut" in u)applyAudioOut(u.audioOut);
  if("dimMinutes" in u){if(!(+u.dimMinutes>0))dimWake();dimAt=performance.now()}
  if("attractMinutes" in u||"attract" in u){lastInputAt=performance.now();if(attract)wake()} // a new wait starts from now
  if("effects" in u||"performance" in u){if("performance" in u&&window.Perf)Perf.set(u.performance||"auto");applyEffects();if(window.ThemeFx&&ThemeFx.refresh)ThemeFx.refresh()}
}
function applyUiAll(){const u=uiNow();applyUi_({safeArea:u.safeArea,reduceMotion:u.reduceMotion,clock:u.clock,clock24:u.clock24,confirmButton:u.confirmButton,scrollSpeed:u.scrollSpeed,audioOut:u.audioOut,dimMinutes:u.dimMinutes})}
function applyUi(partial){if(!partial||typeof partial!=="object")return;try{if(cfgInfo){const ui=cfgInfo.config.ui;for(const k of Object.keys(partial))ui[k]=partial[k]}applyUi_(partial)}catch(e){console.warn("applyUi",e)}}
// Hidden games: the list for Settings (from the whole scan, hidden ones included) and the switch itself
function hiddenList(){const hid=hiddenSet();if(!hid.size||!lastLib)return [];const out=[];lastLib.forEach(s=>(s.games||[]).forEach(g=>{if(hid.has(String(g.id)))out.push({id:String(g.id),title:g.title,system:s.name})}));return out.sort((a,b)=>a.system.localeCompare(b.system)||a.title.localeCompare(b.title))}
async function setHidden(id,on){if(!cfgInfo||id==null)return false;id=String(id);on=!!on;const ui=cfgInfo.config.ui,list=new Set((Array.isArray(ui.hidden)?ui.hidden:[]).map(String));if(on===list.has(id))return true;
  if(on)list.add(id);else list.delete(id);ui.hidden=[...list];
  // the game in view is going away: the shelf lands on its neighbour instead of jumping to the start
  const g=curGame();if(on&&g&&String(g.id)===id&&cur()&&(mode==="browse"||mode==="details")){const it=cur().items,k=it.indexOf(rawItem());const nb=it[k+1]||it[k-1];if(nb)sel=it.indexOf(nb)}
  try{await api.saveConfig(cfgInfo.config)}catch{}
  // on the shelf: rebuilt now; from Settings / the museum: once you're back on the shelf (re-reading the config under
  // an open Settings screen would swap the object it is editing)
  if(lastLib){if(mode==="browse"||mode==="details")await refresh(true,lastLib);else pendingLib=lastLib}
  return true}
function hideGame(g){if(!g||!isGame(g))return;const id=String(g.id),title=g.title;closeDetails(true);play_("back");rumble(0,.25,60);
  setHidden(id,true).then(()=>{const fn=()=>{if(undoHide!==fn)return;toastEnd();setHidden(id,false).then(()=>{toast(`${title} is back on the shelf`);play_("toast")})};
    // (beta.10) through the toast queue: it waits for nothing (your own action), and a toast from the background waits for it
    const pad=showPad(),el=$("toast");toastShow({msg:`Hidden: ${title}  `,kind:"undo"});undoHide=fn;const b=document.createElement("button");b.type="button";b.className="t-undo";b.innerHTML=pad?`${glyph("sq")}<span>Undo</span>`:`<span class="g">U</span><span>Undo</span>`;b.addEventListener("click",e=>{e.stopPropagation();fn()});el.append(b);
    clearTimeout(toastT);toastT=setTimeout(()=>{if(undoHide===fn)toastEnd()},6000)})}
// a game's own emulator build (Details → Emulator): chosen in main (a native picker, like Settings' paths), stored in
// cfg.games by game id; launching uses it instead of the shelf's emulator
const gameEmu=g=>{const o=g&&cfgInfo&&cfgInfo.config.games&&cfgInfo.config.games[g.id];return o&&o.emulator||""};
const emuLabel=p=>{const parts=String(p).split(/[\\/]/).filter(Boolean);return parts.length>1?parts[parts.length-2]:parts[0]||p};
async function chooseGameEmu(g){if(!g||g.kind!=="rom"||!api.gameEmulator)return;try{const r=await api.gameEmulator(g.id);if(r&&r.games&&cfgInfo)cfgInfo.config.games=r.games;if(r&&r.ok){play_("toast");const own=gameEmu(g);toast(own?`${g.title} now runs in ${emuLabel(own)}`:`${g.title} runs in the shelf's emulator again`)}if(mode==="details"&&curGame()===g)fillDetails()}catch(e){toast(String(e&&e.message||e))}}
(function(){const c=$("dActEnd");if(!c||$("dGameEmu"))return;const b=document.createElement("button");b.className="btn";b.type="button";b.id="dGameEmu";b.textContent="Emulator";b.title="Run this game in a different emulator build than the rest of its shelf";b.addEventListener("click",()=>chooseGameEmu(curGame()));c.before(b)})();
// the Details panel's "Box art" button (1.67): opens Settings → Art & manuals straight at this game's mix (front, back,
// spine and disc looks, Pick exact for any part); B there goes up to the console's games, then the Art page
(function(){const c=$("dActEnd");if(!c||$("dBoxArt"))return;const b=document.createElement("button");b.className="btn";b.type="button";b.id="dBoxArt";b.textContent="Box art";b.title="Choose this game's box art: front, back, spine and disc";
  b.addEventListener("click",()=>{const g=curGame();if(g&&g.kind==="steam"&&api.pcCoverOptions){closeDetails(true);pcCoverOpen(g);return}if(!g||g.kind!=="rom"||!api.artStudio)return;artStudioFor(g)});c.before(b);
  // (beta.9) PC games: the same picker from a button in the main row, beside Play (consoles keep theirs in More)
  const m=$("dMoreBtn");if(m&&!$("dBoxArtPc")){const p=document.createElement("button");p.className="btn d-ico";p.type="button";p.id="dBoxArtPc";p.hidden=true;p.title="Choose this game's cover";
    p.innerHTML='<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M4 4h16v16H4zM4 16l5-5 4 4 3-3 4 4"/><path d="M14.5 8.5h.01"/></svg><span>Box art</span>';
    p.addEventListener("click",()=>{const g=curGame();if(g&&g.kind==="steam"&&api.pcCoverOptions){closeDetails(true);pcCoverOpen(g)}});m.before(p)}})();
// (beta.5) Details → Rename: a console or PC game's own name (Settings' Rename page, so a controller gets the on-screen
// keyboard); main applies it everywhere it reads games, so box art and manuals are looked up by it too
(function(){const c=$("dActEnd");if(!c||$("dRename"))return;const b=document.createElement("button");b.className="btn";b.type="button";b.id="dRename";b.textContent="Rename";b.title="Give this game the name you want on your shelf";
  b.addEventListener("click",()=>{const g=curGame();if(!g||!api.renameGame||!(g.kind==="rom"||g.kind==="steam"))return;const s=g._sh||shelfFor(g);const ref=g.kind==="rom"?{systemId:s.id,file:g.file}:{id:g.id};
    closeDetails(true);play_("panel");Setup.open({page:"rename",ref,title:g.title,fileTitle:g.fileTitle||""})});c.before(b)})();
// the Details panel's "Choose manual…" button (Manual Studio): every manual found for this game side by side, in Settings
// → Art & manuals (the same picker as the box art's); B there goes up to the console's games, then the Art page
(function(){const m=$("dManual");if(!m||$("dChooseManual"))return;const b=document.createElement("button");b.className="btn";b.type="button";b.id="dChooseManual";b.textContent="Choose manual…";b.title="Every manual found for this game, side by side: pick another language, region or scan, or add your own PDF";
  b.addEventListener("click",()=>{const g=curGame();if(!g||g.kind!=="rom"||!g.file||!api.manualStudio)return;const s=shelfFor(g);play_("panel");Setup.open({page:"manualgame",systemId:s.id,file:g.file,title:g.title})});$("dActEnd").before(b)})(); /* (beta.7: in the More menu) */
// the Details panel's "Hide from shelf" button (made here: index.html belongs to the Settings work)
(function(){const c=$("dActEnd");if(!c||$("dHide"))return;const b=document.createElement("button");b.className="btn";b.type="button";b.id="dHide";b.textContent="Hide from shelf";b.title="Hide this game from every shelf (undo it in Settings → Hidden games)";b.addEventListener("click",()=>hideGame(curGame()));c.before(b)})();
// (beta.7) Details → Collections: this game on shelves of your own (a page in Settings: a switch per collection, or a new one)
(function(){const c=$("dActEnd");if(!c||$("dCols"))return;const b=document.createElement("button");b.className="btn";b.type="button";b.id="dCols";b.textContent="Collections";b.title="Put this game on shelves of your own";b.addEventListener("click",()=>{const g=curGame();if(!g||!isGame(g)||!window.Setup)return;closeDetails(true);Setup.open({page:"collections",id:g.id,title:g.title})});c.before(b)})();

// Called by the setup screen.
window.Shelf={
  reloadCustomThemes:()=>loadCustomThemes(), // (Settings → Display: Find themes again / Make a theme from a picture)
  companionPlay,smoothTest,holdInput,
  // (beta.5) Settings → Search for a cover: the picker again for that game, with SteamGridDB's covers for the typed name
  pcCoverSearch:(id,term)=>{const g=allShelves.flatMap(s=>s.items||[]).find(x=>x&&x.id===id);if(g)setTimeout(()=>pcCoverOpen(g,term),350)},
  // (beta.9) Settings → Custom games: covers for every custom game (one by one in the picker, or the best match for each)
  pcCoverBatch:()=>setTimeout(()=>pcCoverBatch(),350),pcCoverAuto:()=>pcCoverAuto(),pcCoverUndo:()=>pcCoverUndo(),coverUndoCount:()=>coverUndo.length,
  // (beta.7) test hooks for the library pass: the filters on offer, picking one, the shelf's games and the one in view
  _lib:{filters:()=>filterList(),filter:v=>pickSort(1,v),cur:()=>{const g=curGame();return g?{id:g.id,title:g.title,kind:g.kind}:null},
    games:()=>(cur()?cur().items.filter(isGame):[]).map(g=>({id:g.id,year:yearOf(g),genres:genresOf(g)})),shelves:()=>shelves.map(s=>({id:s.id,name:s.name,col:!!s.col,ids:(s.items||[]).filter(isGame).map(g=>g.id),last:((s.items||[])[(s.items||[]).length-1]||{}).id||null}))},
  // (beta.7) Settings changed your collections: their shelves are made again (the one you're on stays, if it still has games)
  previewStop:()=>{if(PV){pvStop();pvBackStop()}},setCollections:list=>{if(!cfgInfo||!Array.isArray(list))return;cfgInfo.config.ui.collections=list;const was=cur();buildHome();
    // (beta.10) the shelf you were on is gone (buildHome keeps the index, so another shelf slid into it): show that shelf's own cases, from its first game
    if(cur()!==was){closeDetails(true);sel=cur()?Math.max(0,cur().items.findIndex(isGame)):0;flipped=false;spin=0;spinT=0;cancelAnimationFrame(spinRaf);spinRaf=0;build();if(cur()){updateInfo();raShelfLoad();preloadModel()}tuneTo(cur());save();return}
    renderTabs();if(cur()&&cur().col)build()},
  homeButton:()=>{if(qm.open){qmClose();return "closed"}qmOpen();return qm.open?"opened":"busy"},quickMenuOpen:()=>!!qm.open, // the Thor's Home key while Boxshelf is the Home app
  _mus:{set:(k,v)=>musSetOpt(k,v),style:()=>musStyle(),hist:h=>{HIST=Object.assign({},HIST,h||{})},tv:()=>({ch:tvS.ch,n:tvS.list.length,list:tvS.list.slice(0,4).map(e=>String(e.key).slice(0,12)),i:tvS.i,ads:tvS.ads.length,bad:[...tvS.bad],guide:!!tvS.guide}),games:()=>allShelves.filter(sh=>sh.kind==="console").flatMap(sh=>sh.items.filter(isGame).map(g=>({id:g.id,title:g.title,sys:sh.id})))}, // museum test hooks (the harness can't see these names)
  refresh,flushSaves,toast,shuffle,cleanLogo,attractNow:()=>{lastInputAt=-1e9;attractCheck()},sound:(o)=>{if(o&&o.enabled!=null){soundOn=!!o.enabled;musTvVol()}if(o&&o.volume!=null&&cfgInfo){cfgInfo.config.ui.volume=+o.volume;paintVol()}if(window.Sound){Sound.set(o);if(o.preview)Sound.play(o.preview);if(o.music!=null||o.musicLevel!=null){if(o.music!=null)mmTrack=String(o.music);if(o.musicLevel!=null)mmLevel=+o.musicLevel;ambient(mmWant)}else if(o.enabled===true)ambient(mmWant)}},raReload:()=>{RA.shelf={};RA.game.clear();raInit()},
  // Settings closed without a rescan: pick up what refresh() used to re-read (sound on/off, the volume icon, prompts)
  settingsClosed:()=>{if(cpReturn){const x=cpReturn;cpReturn=null;const g=allShelves.flatMap(s=>s.items||[]).find(y=>y&&y.id===x.id);if(g)setTimeout(()=>pcCoverOpen(g,x.term),350)} /* (beta.9) back from the SteamGridDB key's page: the cover picker again */
    if(!cfgInfo)return;const u=cfgInfo.config.ui;soundOn=u.sound!==false;paintVol();buttonsPref=u.buttons||"auto";applyUiAll();renderHints.sig="";renderHints();updateSkipText();lastInputAt=performance.now()},
  // Settings reads the controller itself: its presses count as activity (attract, dimming), and none of them act while
  // the dimmed screen is up or the press that woke it is still held
  _wake:()=>{lastInputAt=dimAt=performance.now()},padBlocked:()=>dimOn||dimSwallowPad,
  setMode:m=>{if(m!=="browse"&&m!=="details")qmClose(true);mode=m},closePanels:()=>{closeDetails(true);closeCustomize()},
  setButtons,
  reloadSongs:()=>window.Sound&&Sound.loadSongs?Sound.loadSongs(api,cfgInfo&&cfgInfo.config.ui.builtInSongs):Promise.resolve(null),
  _slide:(id,title)=>{const sh=shelves.find(x=>x.id===id)||{id,name:(WORDMARKS[id]||[id])[0],emulatorName:"Emulator"};const g=(sh.items||sh.games||[]).find(x=>x.kind==="rom"&&x.hue!=null)||{kind:"rom",id:"t_"+id,title:title||"Test Game",hue:200,art:{}};mode="launch";runSlide(sh,g,()=>{},false)},
  _slideHide:()=>{hideInsert();mode="browse"},
  // the performance level (Settings → Display): shelf effects follow it at once, the museum on its next frame
  setPerformance:v=>{if(window.Perf)Perf.set(v||"auto");applyEffects();if(window.ThemeFx&&ThemeFx.refresh)ThemeFx.refresh();
    setTimeout(()=>{if(mode==='browse'&&window.Museum&&Museum.warm)try{Museum.warm()}catch{}},4000)}, // a new drawing surface: warm it up again so the next visit doesn't stutter
  setBackground:()=>updateBg(),setTheme:v=>{themeOverride=v;applyTheme()},setMuseumLight:v=>Shelf_setMusLight&&Shelf_setMusLight(v),
  // 1.55 Settings: live previews / changes for safeArea, clock, clock24, reduceMotion, attractMinutes, dimMinutes,
  // confirmButton, scrollSpeed, audioOut, openInto (read at the next start), effects / performance
  applyUi,hiddenList,setHidden,audioOutputs,
  gameCounts:()=>{const o={};(lastLib||[]).forEach(s=>{if(s&&s.id)o[s.id]=(s.games||[]).length});return o}, // Settings' console pages and tiles ("13 games")
  getMode:()=>mode,
  // Android's background box art: which shelf is in front and the game in focus (its cases come first in the fetch queue;
  // full: also the shelf's game ids in order), and how long since the last press (the museum's quiet moments)
  artFocus:full=>{const s=cur();if(!s)return null;const o={sys:s.id,sel,mode};if(full)o.ids=(s.items||[]).map(g=>g&&g.id);return o},
  idleMs:()=>performance.now()-lastInputAt,
  // pictures arrived for games already on the shelf (Android's background box art fetch; g.art is already filled in):
  // the cases in view swap their cover in place, nothing is rebuilt; cases not built yet pick it up when they are
  artUpdated:ids=>{const want=new Set((ids||[]).map(String));let n=0;for(const s of slots){if(!s.el||!s.g||!want.has(String(s.g.id)))continue;const g=s.g;
    // a spine that arrived after the case was made (Android's full box art, or one picked in Art Studio): onto its sides as mount() puts it
    if(g.art&&g.art.spine){const su=ANDROID?thumb(g.art.spine,bh*OS):g.art.spine,have=s.el.querySelectorAll(".spine-img");if(have.length)have.forEach(sp=>{if(sp.getAttribute("src")!==su)sp.src=su});
      else s.el.querySelectorAll(".f-left,.f-right").forEach(face=>{const sp=new Image();sp.className="spine-img";sp.alt="";sp.draggable=false;sp.decoding="async";sp.src=su;sp.onerror=()=>sp.remove();face.appendChild(sp)})}
    if(!s.img||!(g.art&&g.art.front))continue;if(ANDROID)artSwapQueue(s);else setFront(s,g,g.art.front);n++} /* Android: decoded first, one swap per frame, never mid-slide */
    // a case still waiting for its generated cover would otherwise paint that over the real picture a moment later
    if(coverQ.length)coverQ=coverQ.filter(q=>!(q.g&&want.has(String(q.g.id))&&q.g.art&&q.g.art.front));
    if(ANDROID&&mode==="museum"&&window.Museum&&Museum.refreshArt)Museum.refreshArt([...want],x=>musArtOf(x._g)); /* (0.3.5) and on the museum's wall */return n},
  // Settings → Museum → "Choose what's on": the museum opens at the TV with its guide up (the TV is switched on if it's off)
  openTvGuide:()=>{if(mode!=="browse"&&mode!=="details")return false;if(!musStyle().tv&&cfgInfo){const ui=cfgInfo.config.ui;ui.museumStyle=Object.assign({},ui.museumStyle||{},{tv:true});api.saveConfig(cfgInfo.config).catch(()=>{})}musGuideNext=true;openMuseum();if(mode!=="museum"){musGuideNext=false;return false}return true},
  config:()=>cfgInfo
};
// Settings → a console's "Customize" button: switch the shelf to that console (the usual slide) and open Customize
// for it once the switch has landed. Closes nothing else; false when there's no such shelf or the shelf isn't in view.
window.Shelf.openCustomize=systemId=>{if(mode!=="browse"&&mode!=="details")return false;const i=shelves.findIndex(s=>s.id===systemId);if(i<0)return false;
  const target=shelves[i];if(i===sysIdx&&pendingSys<0){openCustomize();return mode==="customize"}
  setSystem(i);const t0=performance.now();
  (function wait(){if(mode!=="browse"&&mode!=="details")return;if(pendingSys<0&&cur()===target){openCustomize();return}if(performance.now()-t0<3000)setTimeout(wait,60)})();
  return true};
window.Shelf._dim=()=>dimStart(); // test hook: dim the screen now
// A second screen (the AYN Thor's bottom screen, Android build: android-bottom.js). "bs-select" fires once the shelf has
// rested on something for a moment, never per slide frame; Shelf.bottomInfo() describes it as plain data, and
// Shelf.bottomCmd() runs the few shelf actions its touch buttons offer. Nothing listens to any of it on Windows.
function bsSelectSoon(){if(!window.bsPlatform||bsPlatform.os!=="android")return; /* only the Android build has a second screen: Windows keeps no timer */clearTimeout(bsSelectSoon.t);bsSelectSoon.t=setTimeout(()=>{const g=curGame();dispatchEvent(new CustomEvent("bs-select",{detail:{id:g?String(g.id!=null?g.id:g.title):""}}))},380)}
window.Shelf.bottomInfo=async g0=>{const g=g0||curGame();if(!g)return null; /* (the museum asks about the case it has chosen) */
  const sh=shelfFor(g)||{},st=stOf(g),i=g.info||{},h=histOf(g),a=g.art||{},game=isGame(g),r=game?raFor(g):null,md=MEDIA[sh.id]||{};
  let front=a.front||a.icon||null;if(!front){try{front=await appFrontAsync(st,g)}catch{front=null}} // the cover the shelf made up for it (encoded off the main thread, usually cached already)
  return {id:String(g.id!=null?g.id:g.title),kind:g.kind,title:g.title||"",system:{id:sh.id||"",name:sh.name||""},hue:g.hue!=null?g.hue:st.hue,
    style:{ratio:st.ratio,depth:st.depth,rc:st.rc,re:st.re,edge:st.edge,pack:st.pack||(st.rc<=.012?(st.depth<.1?"jewel":"box"):"keep") /* a moulded keep case, a CD jewel case or a printed card box (systems-extra.js presets) */,caseColor:st.caseColor,caseSide:st.caseSide,spineInk:st.spineInk,band:st.band||null,spineBand:st.spineBand||null,glow:st.glow,tint:st.tint||null},
    media:{kind:md.kind||"disc",size:md.size||.62},
    art:{front:front?thumb(front,1024):null,back:a.back?thumb(a.back,1024):null,spine:a.spine?thumb(a.spine,1024):null,disc:a.disc?thumb(a.disc,768):null,full:a.full?thumb(a.full,1024):null},
    year:game?yearOf(g):null,genre:game?genreOf(g):null,dev:i.dev||null,pub:i.pub||null,players:i.players||null,rating:i.rating?+i.rating:null,
    region:g.region||null,size:fmtSize(g.sizeBytes)||null,discs:g.discs&&g.discs.length>1?g.discs.length:0,desc:String(descFor(g)||"").slice(0,1600),
    played:h&&h.count?fmtPlay(h.ms):null,plays:h&&h.count||0,last:h&&h.last?fmtDate(h.last/1000):null,fav:game?isFav(g):false,manual:game&&g.kind==="rom"?hasManual(g):false,
    ach:r?{earned:r.earned||0,total:r.total||0,mastered:r.mastered==="mastered"}:null,achOn:!!RA.on,
    theme:(()=>{try{return {id:themeId(),bg:themeOf().tint(shelfHue(g),g,shelfPos())}}catch{return null}})()}}; // (0.3.2) the top screen's backdrop, so the bottom screen wears the same theme
// In the museum the bottom screen becomes a companion to the wall: the row you're on (its cases, which are still
// sealed, the chosen one) and that case's details. Only the cases near the chosen one travel, with real covers only
// (the bottom page draws a made-up one itself), so the message stays small however long the row is.
window.Shelf.bottomMuseum=async()=>{if(mode!=="museum"||!window.Museum||!Museum.bottomRow)return null;const r=Museum.bottomRow();if(!r)return null;
  const N=r.boxes.length,at=r.sel>=0?r.sel:0,from=Math.max(0,Math.min(N-41,at-20)),st=STYLE[r.id]||STYLE.media,c=Museum.current&&Museum.current();
  const games=r.boxes.slice(from,from+41).map((b,k)=>{const g=b.g._g||{},f=g.art&&g.art.front;return {i:from+k,id:String(b.g.id),title:b.g.title||g.title||"",cover:f?thumb(f,256):null,sealed:b.sealed}});
  const s=r.sel>=0?r.boxes[r.sel]:null,game=s&&s.g._g?await window.Shelf.bottomInfo(s.g._g):null;if(game){game.sealed=s.sealed;game.artOk=!!(s.g._g.kind==="rom"&&s.g._g.file&&api.artStudio&&window.BS_STUDIO)} /* artOk (0.3.5): the companion's Box art button */
  const h=musHand();
  return {row:{id:r.id,name:r.name,i:r.row,n:r.rows,count:N,from,sel:r.sel,held:r.held,wide:!!r.wide,style:{glow:st.glow,tint:st.tint||null,ratio:st.ratio}},games,game,
    hand:{held:!!h.held,open:!!h.open,discOut:!!h.discOut,reading:!!h.reading},room:bsRoom(),
    hud:{row:$("musRow").textContent,title:$("musTitle").textContent,meta:$("musMeta").textContent,sealed:!!(c&&c.sealed)}}};
// The Room menu's looks for the bottom screen's room buttons: the lighting scenes, walls, floor, shelves and the gallery
// pieces (the same lists the Room menu shows), with what's chosen now. Changing one goes through musSetOpt, like the menu.
const BS_ROOM_KEYS=["wall","floor","shelf","plinths","ropes","bench","posters"];
function bsRoom(){const st=musStyle(),v={light:musLightNow(st),timeOfDay:!!st.timeOfDay};BS_ROOM_KEYS.forEach(k=>{v[k]=st[k]});
  return {v,opts:[{key:"light",label:"Lighting",type:"choice",opts:MUS_SCENES}].concat(MUS_ROOM.filter(o=>BS_ROOM_KEYS.includes(o.key)).map(o=>({key:o.key,label:o.label,type:o.type,opts:o.opts||null})))}}
function bsRoomSet(a){if(!a||typeof a!=="object")return false;const k=String(a.key||""),val=a.val;
  if(k==="light"){if(!MUS_SCENES.some(x=>x[0]===val))return false}
  else{const o=MUS_ROOM.find(x=>x.key===k);if(!o||!BS_ROOM_KEYS.includes(k))return false;if(o.type==="bool"?typeof val!=="boolean":!o.opts.some(x=>x[0]===val))return false}
  musSetOpt(k,val);play_(k==="light"?"lamp":"tick",{dir:1});if(musRoom.open)musRoomRender();return true}
// (0.3.5) the bottom screen's own shortcuts (Android): an app on the device, a console's shelf, a favourite game. The
// choices for one kind (the Shortcuts tab's Add buttons), and what a press on one does
window.Shelf.shortcutOptions=async kind=>{if(!ANDROID)return [];
  if(kind==="app"){const L=api.androidApps?await api.androidApps():[];return L.map(a=>({key:"app:"+a.pkg,label:a.label,sub:"App"}))}
  if(kind==="shelf")return shelves.filter(s=>s.kind==="console"&&s.items.some(isGame)).map(s=>({key:"shelf:"+s.id,label:s.name,sub:s.items.filter(isGame).length+" games"}));
  if(kind==="game"){const out=[];for(const s of shelves){if(s.kind!=="console")continue;for(const g of s.items)if(isGame(g)&&g.kind==="rom")out.push({g,s,f:isFav(g)?1:0,p:(HIST[g.id]&&HIST[g.id].count)||0})}
    out.sort((a,b)=>b.f-a.f||b.p-a.p||String(a.g.title).localeCompare(String(b.g.title)));return out.slice(0,80).map(x=>({key:"game:"+x.g.id,label:x.g.title,sub:(x.f?"♥ ":"")+x.s.name}))}
  return []};
window.Shelf.bottomShortcut=key=>{if(!ANDROID)return false;const k=String(key||""),at=k.indexOf(":"),kind=k.slice(0,at),v=k.slice(at+1);if(at<0||!v)return false;
  if(mode==="setup"||mode==="museum"||mode==="launch"||mode==="boot"||(window.BS_STUDIO&&BS_STUDIO.isOpen()))return false;if(qm.open)qmClose(true);
  if(kind==="app"){if(!api.launchApp)return false;play_("open");toast("Opening the app…");api.launchApp(v).catch(e=>{toast(e&&e.message||"Couldn't open that app");play_("error")});return true}
  if(sysBusy()||!/^(browse|details|inspect)$/.test(mode))return false; // (beta.10) mid-switch, Settings, Customize…: no rebuild under them
  if(mode==="inspect")closeInspect();
  if(kind==="shelf"){const si=shelves.findIndex(s=>s.id===v);if(si<0)return false;if(si!==sysIdx)setSystem(si);return true}
  if(kind==="game"){let si=-1,gi=-1;shelves.forEach((s,n)=>{if(si>=0||s.kind!=="console")return;const j=s.items.findIndex(g=>g&&String(g.id)===v);if(j>=0){si=n;gi=j}});if(si<0)return false;
    if(si===sysIdx)go(gi);else{closeDetails(true);sysIdx=si;sel=gi;flipped=false;spin=0;spinT=0;build();tuneTo(cur());raShelfLoad();preloadModel();save()} /* (another shelf: straight there, as the museum's Play does) */
    bsSelectSoon();return true}
  return false};
window.Shelf.bottomCmd=(c,arg)=>{if(qm.open&&c!=="menu")qmClose(true);
  if(mode==="museum"){if(Museum.wake)Museum.wake(); /* the museum companion's touches: choose a case on this row, unwrap it, play it (an idle handheld museum wakes for them) */
    if(c==="mpick")return Museum.pickCol&&Museum.pickCol(+arg);
    if(c==="unwrap"){const ok=Museum.unwrapCol&&Museum.unwrapCol(+arg);if(ok){rumble(.6,.8,260);renderMusHints()}return ok}
    if(c==="play"){const k=Museum.current();if(k&&k.game&&!k.sealed&&(!Museum.isHolding()||Museum.isHoldingCase()))museumPlay(k.game);return}
    if(c==="prev"||c==="next"){if(!Museum.isHolding())Museum.move(c==="next"?1:-1,0);return}
    // the rest, as the pad would: a row up / down, A (pick the case up; in your hands: open it, then the disc out), B only
    // to put a held case back (B on the bare wall would step back or leave the museum), the right stick's turn, zoom
    if(c==="mrow"){if(!Museum.isHolding())Museum.move(0,+arg<0?-1:1);return}
    if(c==="mact"){const r=Museum.bottomRow&&Museum.bottomRow();if(Museum.isHoldingCase()||(r&&r.sel>=0&&!Museum.isHolding()))museumActivate();bsSelectSoon();return}
    if(c==="mback"){if(Museum.isHolding())museumBack();bsSelectSoon();return}
    if(c==="mturn"){if(Museum.isHolding()&&arg&&typeof arg==="object"){const n=v=>Math.max(-120,Math.min(120,+v||0));Museum.turn(n(arg.dx),n(arg.dy))}return}
    if(c==="mzoom"){if(Museum.isHolding()&&Museum.zoomHand)Museum.zoomHand(+arg<0?-1:1);return}
    if(c==="room"){const ok=bsRoomSet(arg);bsSelectSoon();return ok}
    // (0.3.5) Box art: the handheld Art Studio on the chosen case, over the museum (paused under it); the wall shows the
    // new pictures when it closes
    if(c==="art"){const k=Museum.current(),mg=k&&k.game,g=mg&&mg._g;if(!g||g.kind!=="rom"||!g.file||!api.artStudio||!window.BS_STUDIO||BS_STUDIO.isOpen()||Museum.isHolding())return false;
      play_("panel");if(Museum.setPaused)Museum.setPaused(true,"studio");
      const ok=BS_STUDIO.open({systemId:mg._sh.id,file:g.file,title:g.title,id:g.id,game:g,onClose:()=>{if(Museum.setPaused)Museum.setPaused(false,"studio");if(mode==="museum"&&Museum.refreshArt)Museum.refreshArt([g.id],x=>musArtOf(x._g));bsSelectSoon()}});
      if(!ok&&Museum.setPaused)Museum.setPaused(false,"studio");return ok}
    if(c!=="menu")return} /* the shelf's own buttons (favourite, details…) would act on the shelf hidden behind the museum */ /* a finger on the bottom screen means "do this": the quick menu shouldn't stay open over a shelf moving behind it */
  const browsing=mode==="browse"||mode==="details";
  if(c==="play"){if(browsing)play();return}
  if(c==="fav")return toggleFav();
  if(c==="details"){if(mode==="details")closeDetails();else openDetails();return}
  if(c==="inspect"){if(mode==="inspect"){closeInspect();detailsReturn()}else openInspect();return}
  if(c==="manual"){if(mode!=="inspect")openInspect();const t0=performance.now();(function wait(){if(mode!=="inspect")return;if(performance.now()-t0>650){openReader();return}setTimeout(wait,80)})();return} // the reader opens once the case has landed in Inspect
  if(c==="shuffle"){shuffle();return}
  if(c==="prev"||c==="next"){move(c==="next"?1:-1);return}
  if(c==="ach"){if(browsing)openAchievements();return}
  // quick ways in from the bottom screen: the Art Studio for this game (as the quick menu's Box art does), and Settings
  // at its Bottom screen line
  if(c==="art"){const g=curGame();if(!g||g.kind!=="rom"||mode==="setup")return;if(mode==="inspect")closeInspect();else if(mode==="details")closeDetails();
    if(window.BS_STUDIO&&BS_STUDIO.isOpen())return;if(api.artStudio&&g.file)artStudioFor(g);else qsChooseArt(g);return} /* (artStudioFor: the handheld studio on Android) */
  if(c==="bsettings"){if(mode==="setup")return;if(mode==="inspect")closeInspect();play_("panel");Setup.open({page:"bottom"});return}
  // (0.3.2) more of the bottom screen's shortcuts: the museum, and Settings (not under the handheld Art Studio or the
  // magazines store, which hold the controller while they're up)
  if((c==="museum"||c==="settings")&&((window.BS_STUDIO&&BS_STUDIO.isOpen())||(window.BS_READING&&BS_READING.isOpen())))return;
  if(c==="museum"){if(mode==="setup")return;if(mode==="inspect")closeInspect();else if(mode==="details")closeDetails();openMuseum();return}
  if(c==="settings"){if(mode==="setup")return;if(mode==="inspect")closeInspect();play_("panel");Setup.open({});return}
  if(c==="menu"){window.Shelf.homeButton();return}};
window.Shelf._musPlay=()=>{const c=window.Museum&&Museum.current();if(mode==="museum"&&c&&c.game)museumPlay(c.game)}; // test hook: play the case in view
window.Shelf.quickMenu=()=>qmOpen();window.Shelf._qs=()=>({g:(curGame()||{}).title,open:qm.open,r:qm.r,id:qm.rows[qm.r]&&qm.rows[qm.r].r.id,sub:qm.sub,sr:qm.sr,sc:qm.sc,ac:qm.ac,f:(()=>{const f=qsFocusEl();return f&&(f.dataset.v||f.dataset.k||f.className)})(),theme:themeId(),ov:themeOverride,bg:bgMode(),bgo:bgOverride,vol:curVol(),sort:sortBy,mode});window.Shelf._isp=()=>({rx:isp.rx,ry:isp.ry,tx:isp.tx,ty:isp.ty,tz:isp.tz,state:isp.state});window.Shelf._pickBuild=pickBuild; /* test hook: the Switch 2 build chooser */ // test hook: Inspect's pose (the device test checks the stick turn speed)
window.Shelf._booklet=()=>makeBooklet(curGame());window.Shelf._rd=()=>({open:rd.open,making:rd.making,for:rd.bookletFor,state:isp.state,g:curGame()&&curGame().id});window.Shelf.customizeStep=d=>custStep(d);

addEventListener("blur",()=>document.body.classList.add("idle-bg"));addEventListener("focus",()=>document.body.classList.remove("idle-bg"));
// Startup trace: the moments that matter (shelf built, covers shown, scan back, background jobs) and every frame that
// came in late during the first 20 s, written as one line to the Boxshelf log so a stutter can be pinned down on any PC
const BOOT={t0:performance.now(),marks:[],gaps:[],last:"start"};
function bootMark(n){if(!BOOT.done){const t=Math.round(performance.now()-BOOT.t0);BOOT.marks.push(n+"@"+t);BOOT.last=n}}
(function bootWatch(){let prev=performance.now();const f=now=>{const gap=now-prev;prev=now;if(gap>40&&!document.hidden)BOOT.gaps.push(Math.round(now-BOOT.t0)+":"+Math.round(gap)+"("+BOOT.last+")");if(now-BOOT.t0<20000)requestAnimationFrame(f);else{BOOT.done=true;api.log&&api.log(`startup: ${BOOT.marks.join(" ")} | late frames (at:ms(after)): ${BOOT.gaps.slice(0,40).join(" ")||"none"}`)}};requestAnimationFrame(f)})();
async function start(){bootMark("fonts");
  // last session's library first (instant shelves), then a real scan; rebuild only if it differs
  const cached=api.cachedLibrary?await api.cachedLibrary().catch(()=>null):null;
  if(cached&&cached.length){bootMark("cache");await refresh(false,cached);bootMark("built");const afterReveal=()=>new Promise(r=>{const w=()=>revealedAt&&performance.now()-revealedAt>1200?r():setTimeout(w,200);w()}); /* the background rescan waits until the shelf is in: it keeps the PC busy */
    afterReveal().then(()=>api.scanIfChanged?api.scanIfChanged():api.scan()).then(fresh=>{const same=!!(fresh&&fresh.unchanged)||JSON.stringify(fresh)===JSON.stringify(cached);bootMark(same?"scan-same":"scan-changed");if(!same){if((mode==="browse"||mode==="details")&&!sysBusy())refresh(true,fresh);else pendingLib=fresh}}).catch(()=>{})}
  else await refresh(false);
  requestAnimationFrame(poll); // the controller works as soon as the shelf is up, not after themes load
  await reloadThemes();bootMark("themes");renderHints();raInit();
  if(!cfgInfo||!cfgInfo.config.setupDone)Setup.open({wizard:true});
}
// the faces the generated covers use (bundled in fonts/, linked from index.html) — so covers never bake in fallback fonts
const ready=document.fonts&&document.fonts.load?Promise.all(["Lilita One","Bungee","Audiowide"].map(f=>document.fonts.load(`40px "${f}"`))):Promise.resolve();
Promise.race([ready,new Promise(r=>setTimeout(r,2500))]).then(start,start);
})();
