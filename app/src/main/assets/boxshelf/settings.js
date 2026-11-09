// Settings: the full-screen settings app (sidebar of sections, a page of rows, an info panel and a hint bar), built for
// the couch: controller, keyboard or mouse, search with an on-screen keyboard, live previews. Opened through
// Setup.open() (setup.js keeps the first-run wizard and the big tools: box art / manual downloads, the Internet Archive
// finder, which open over this screen via Setup.openLegacy and come back to the same row when closed).
(function(){
"use strict";
const api=window.boxshelf||{};
const esc=s=>String(s==null?"":s).replace(/[&<>"]/g,c=>({"&":"&amp;","<":"&lt;",">":"&gt;",'"':"&quot;"}[c]));
const clone=o=>JSON.parse(JSON.stringify(o));
const eq=(a,b)=>a===b||(a!=null&&b!=null&&String(a)===String(b))||(typeof a==="number"&&typeof b==="number"&&Math.abs(a-b)<1e-6);
const shelf=(name,...a)=>{const S=window.Shelf;if(!S||typeof S[name]!=="function")return undefined;try{return S[name](...a)}catch(e){console.warn("settings: Shelf."+name,e)}};
const applyUi=o=>shelf("applyUi",o);
const errMsg=e=>String(e&&e.message||e||"").replace(/^Error invoking remote method '[^']+': (Error: )?/,"");

const IC={
  games:'<rect x="3" y="7" width="18" height="11" rx="4"/><path d="M8 11v3M6.5 12.5h3"/><circle cx="16" cy="12" r=".6"/><circle cx="18" cy="14" r=".6"/>',
  pc:'<rect x="3" y="4" width="18" height="12" rx="2"/><path d="M8 20h8M12 16v4"/>',
  apps:'<rect x="4" y="5" width="16" height="12" rx="2"/><path d="M10 9l4 2-4 2z"/>',
  display:'<rect x="3" y="4" width="18" height="12" rx="2"/><path d="M7 20h10"/>',
  perf:'<path d="M4 15a8 8 0 1 1 16 0"/><path d="M12 15l4-5"/>',
  sound:'<path d="M5 9v6h4l5 4V5L9 9z"/><path d="M17 9a4 4 0 0 1 0 6"/>',
  shelf:'<rect x="3" y="5" width="4" height="14" rx="1"/><rect x="9" y="5" width="4" height="14" rx="1"/><path d="M15 6l4 1-3 12-4-1z"/>',
  museum:'<path d="M3 9l9-5 9 5M5 10v8M9 10v8M15 10v8M19 10v8M3 20h18"/>',
  pad:'<path d="M7 8h10a4 4 0 0 1 4 4v1a3 3 0 0 1-5 2l-1-1H9l-1 1a3 3 0 0 1-5-2v-1a4 4 0 0 1 4-4z"/>',
  art:'<rect x="4" y="4" width="16" height="16" rx="2"/><circle cx="9" cy="9" r="2"/><path d="M20 15l-5-5-9 10"/>',
  pv:'<rect x="3" y="5" width="18" height="14" rx="3"/><path d="M10 9.5v5l4.5-2.5z"/>',
  reading:'<path d="M12 6c-2-1.5-5-2-8-1.5V19c3-.5 6 0 8 1.5 2-1.5 5-2 8-1.5V4.5C17 4 14 4.5 12 6z"/><path d="M12 6v14.5"/>',
  acct:'<circle cx="12" cy="9" r="4"/><path d="M4 20a8 8 0 0 1 16 0"/>',
  sys:'<circle cx="12" cy="12" r="3"/><path d="M12 2v3M12 19v3M2 12h3M19 12h3M5 5l2 2M17 17l2 2M5 19l2-2M17 7l2-2"/>',
  search:'<circle cx="11" cy="11" r="7"/><path d="M20 20l-4-4"/>',
  phone:'<rect x="7" y="2.5" width="10" height="19" rx="2.2"/><path d="M11 18.5h2"/>'
};
const svg=k=>`<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round">${IC[k]||""}</svg>`;
// the themes (same swatches as the classic page)
const THEMES=[["spectrum","Spectrum","linear-gradient(120deg,#3b2fb8,#1f7ad6 30%,#d8202f 55%,#1a8f2a 80%,#5a2bd0)"],
  ["classic","Spectrum Classic","linear-gradient(160deg,#3b2fb8,#5a2bd0 40%,#1b1450)"],
  ["keys","Keys","radial-gradient(circle,rgba(140,190,255,.95) 0 1.3px,transparent 1.8px) 0 55%/9px 7px,radial-gradient(ellipse 80% 40% at 50% 60%,rgba(80,120,255,.45),transparent 70%),linear-gradient(180deg,#05040f,#0a0a22 55%,#030309)"],
  ["arcade","Arcade","repeating-linear-gradient(0deg,rgba(0,0,0,.25) 0 1px,transparent 1px 3px),linear-gradient(180deg,transparent 62%,#ff3fc8 62.5%,rgba(255,63,200,.25) 66%,transparent 80%),radial-gradient(circle,#fff 0 1px,transparent 1.5px) 0 0/23px 17px,linear-gradient(180deg,#04020c,#1c0838 60%,#07021a)"],
  ["aurora","Aurora","radial-gradient(ellipse 80% 50% at 30% 20%,#25d6a0,transparent 70%),radial-gradient(ellipse 70% 50% at 75% 30%,#7a4cff,transparent 70%),#04101c"],
  ["midnight","Midnight","linear-gradient(90deg,transparent 10%,rgba(150,170,255,.9) 50%,transparent 90%) 0 70%/100% 2px no-repeat,radial-gradient(ellipse 60% 12% at 50% 70%,rgba(90,120,255,.45),transparent 72%),#000"],
  ["artwork","Artwork","linear-gradient(180deg,rgba(0,0,0,.1),rgba(0,0,0,.5)),radial-gradient(circle at 72% 30%,#ffe7a8 0 9%,transparent 10%),linear-gradient(172deg,#7fb6e8 0 46%,#4e8f4a 46.5% 64%,#2f5e30 64.5%)"],
  ["blur","Blur","radial-gradient(circle at 28% 34%,#ff9a5a,transparent 52%),radial-gradient(circle at 74% 58%,#5a7cff,transparent 54%),radial-gradient(circle at 42% 84%,#3ad0a0,transparent 50%),#2a2440"]];
// the extra themes (themes-extra.js), each with its own tile, on both platforms
const XTHEMES=Array.isArray(window.BS_EXTRA_THEMES);
if(XTHEMES)for(const t of window.BS_EXTRA_THEMES)if(t&&t.id&&!THEMES.some(x=>x[0]===t.id))THEMES.push([t.id,t.name,t.swatch||"#111"]);
// (1.76, Windows) custom themes (app.js loads them: window.BS_CUSTOM_THEMES, event bs-custom-themes): a tile each, after
// the rest, its swatch the picture itself under a light shade
const syncCustomThemes=()=>{for(let i=THEMES.length-1;i>=0;i--)if(/^custom-/.test(THEMES[i][0]))THEMES.splice(i,1);
  for(const t of (window.BS_CUSTOM_THEMES||[]))if(t&&/^custom-[a-z0-9-]{1,48}$/.test(t.id))THEMES.push([t.id,String(t.name||"Custom"),`linear-gradient(rgba(0,0,0,.12),rgba(0,0,0,.12)),url("${String(t.bg||"").replace(/["\\\n\r]/g,"")}") center/cover no-repeat,#0b0c14`])};
syncCustomThemes();
const OFFICIAL={gc:"https://dolphin-emu.org/download/",wii:"https://dolphin-emu.org/download/",wiiu:"https://cemu.info/",sw:"https://eden-emu.dev/downloads",sw2:"https://github.com/Djihads80/Eglantine/releases",psp:"https://www.ppsspp.org/download/",ps2:"https://pcsx2.net/downloads/",ps3:"https://rpcs3.net/download",ps4:"https://shadps4.net/downloads",ps5:"https://github.com/Jetsku/KytyPS5/releases",n3ds:"https://azahar-emu.org/pages/download/",x360:"https://xenia.jp/download/",psx:"https://github.com/stenzek/duckstation/releases",nds:"https://melonds.kuribo64.net/downloads.php",nes:"https://www.retroarch.com/?page=platforms",snes:"https://www.retroarch.com/?page=platforms",n64:"https://www.retroarch.com/?page=platforms",gb:"https://www.retroarch.com/?page=platforms",gbc:"https://www.retroarch.com/?page=platforms",gba:"https://www.retroarch.com/?page=platforms",megadrive:"https://www.retroarch.com/?page=platforms",mastersystem:"https://www.retroarch.com/?page=platforms",gamegear:"https://www.retroarch.com/?page=platforms",dreamcast:"https://www.retroarch.com/?page=platforms",retroarch:"https://www.retroarch.com/?page=platforms",steam:"https://store.steampowered.com/about/",stremio:"https://www.stremio.com/downloads",nuvio:"https://github.com/NuvioMedia/NuvioDesktop"};
const HELP={gc:"Dolphin runs GameCube games. Point to Dolphin.exe.",wii:"Dolphin also runs Wii games. It can be the same Dolphin.exe as GameCube.",
  wiiu:"Cemu runs Wii U games: .wua, .wud / .wux or extracted game folders. Disc images need your keys.txt set up in Cemu.",sw:"Eden runs Switch games. Point to eden.exe.",sw2:"Eglantine (an Eden fork) runs Switch 2 games. Point to eglantine.exe (forks like Orchid or Lichii are found too; Build picks which one plays). Unpacked game folders work (exefs + romfs, or base + updates: the newest update is started), and .nsp / .xci.",
  psp:"PPSSPP runs PSP games. Point to PPSSPPWindows64.exe.",ps2:"PCSX2 runs PS2 games (.iso, .chd, .cso, .zso). It needs a PS2 BIOS of its own.",
  ps3:"RPCS3 runs PS3 games. Install the PS3 system software in RPCS3 first.",ps4:"shadPS4 runs PS4 games: folders with an eboot.bin inside.",ps5:"Kyty (KytyJets) runs PS5 games: folders with an eboot.bin inside. Boxshelf starts them with your Kyty launcher's settings (resolution, controller, add-ons folder) and the build's own preset.",
  x360:"Xenia runs Xbox 360 games: .iso discs, extracted folders, XBLA .xex files or .zar archives.",n3ds:"Azahar runs 3DS games: decrypted .3ds / .cci / .cxi, or decrypted .cia files (installed into Azahar the first time you play)."};
const MUS_DEF={tv:false,mail:true,bright:1,warmth:0,spot:1,wall:"plaster",floor:"stone",shelf:"walnut",bench:false,plinths:false,ropes:false,posters:false,dust:1,timeOfDay:false,walkIn:true,tvStand:"cart",tvSize:"m",tvPos:"right",tvCh:"ads",tvVol:1,tvMute:false,tvNear:false,tvCrt:.6,tvAnim:true};
const MUS_OPTS={light:[["gallery","Classic"],["daylight","Daylight"],["night","Evening"],["neon","Neon night"],["spotlit","Spotlit gallery"],["lightsout","Lights out"]],
  tvStand:[["cart","Wooden TV cart"],["cabinet","Low cabinet"],["floor","Floor-standing set"],["wall","Wall mount"]],tvSize:[["s","Small"],["m","Medium"],["l","Large"]],tvPos:[["left","Left"],["centre","Centre"],["right","Right"]],tvCh:[["ads","Vintage commercials"],["trailers","Game trailers"],["recent","Recently played"],["mix","Shuffle all"]],
  wall:[["plaster","Dark plaster"],["wood","Wood panels"],["brick","Brick"],["wallpaper","Wallpaper"],["green","Green"],["burgundy","Burgundy"],["navy","Navy"]],
  floor:[["stone","Stone"],["wood","Wood boards"],["carpet","Carpet"],["checker","Checkerboard"]],shelf:[["walnut","Walnut"],["oak","Oak"],["black","Black"],["white","White"]]};
function musOpts(key){const R=window.BoxshelfMuseumRoom,o=R&&R.opts&&R.opts.find(x=>x.key===key);return o&&o.opts?o.opts:MUS_OPTS[key]}
const musDef=k=>{const R=window.BoxshelfMuseumRoom;return R&&R.defaults&&R.defaults[k]!==undefined?R.defaults[k]:MUS_DEF[k]};

/* ---------- state ---------- */
let root=null,stage=null,E={};
let isOpen=false,active=false,closing=false,info=null,cfg=null,SECTIONS=[];
let saveT=0,pending=false,needRescan=false,raChanged=false;
let secI=0,rowI=0,focus="side",sub=null,subRet=0,query="",results=null,resultTotal=0,preSearch=null;
let oskI=0,oskLayer=0,edit=null,grab=null,infoOpen=false,device="key",padId="",ci=0;
let det=null,detecting=false,status={},svcBusy={},displays=null,outputs=null,autostart=null,homeApp=null,hidden=null,ipl=null,sw2b=null,pathOk={},pathAsk=new Set();
let askT={},toastT=0,peekT=0,themeT=0,rafId=0,primed=false,openId=0;const held={};

/* ---------- config access ---------- */
function gp(path){let o=cfg;for(const k of path.split(".")){if(o==null||typeof o!=="object")return undefined;o=o[k]}return o}
function sp(path,v){secDrop();const ks=path.split(".");let o=cfg;for(let i=0;i<ks.length-1;i++){if(o[ks[i]]==null||typeof o[ks[i]]!=="object")o[ks[i]]={};o=o[ks[i]]}o[ks[ks.length-1]]=v}
const bindv=(path,def)=>({path,d:def,get:()=>{const v=gp(path);return v===undefined||v===null?def:v},set:v=>sp(path,v)});
const bindb=(path,def)=>({path,d:def,get:()=>{const v=gp(path);return v===undefined||v===null?def:!!v},set:v=>sp(path,!!v)});
// row makers: choice, toggle, slider, action, path (file / folder picker), text (typed), heading
const C=(label,path,opts,def,info,x={})=>({t:"choice",label,opts,info,...bindv(path,def),...x});
const T=(label,path,def,info,x={})=>({t:"toggle",label,info,...bindb(path,def),...x});
const S=(label,path,def,min,max,step,fmt,info,x={})=>({t:"slider",label,min,max,step,fmt,info,...bindv(path,def),...x});
const A=(label,btn,info,run,x={})=>({t:"action",label,btn,info,run,...x});
const F=(label,path,kind,info,x={})=>({t:"path",label,kind,info,...bindv(path,""),d:undefined,...x});
const X=(label,path,info,x={})=>({t:"text",label,info,...bindv(path,""),d:undefined,...x});
const H=(text,x={})=>({t:"head",text,...x});
// a sub-page inside a section (the rarely-used rows, so each page stays short): the row opens {kind:"more",id}, B comes
// back to it. MORE is rebuilt with SECTIONS (build()); search and the deep links look inside these pages too.
let MORE={};
const M=(label,id,info,lead,rows,x={})=>{MORE[id]={id,name:label,lead,rows,extra:x.extra};return {t:"action",label,btn:"",open:true,pg:id,info,ns:true,run:()=>openSub({kind:"more",id}),...x}};

const optsOf=r=>typeof r.opts==="function"?r.opts()||[]:r.opts;
function idxOf(r,o){const v=r.get();let i=o.findIndex(x=>eq(x[0],v));if(i<0)i=o.findIndex(x=>eq(x[0],r.d));return Math.max(0,i)}
const isChanged=r=>r.d!==undefined&&r.get&&!eq(r.get(),r.d);
const shown=r=>!r.when||r.when();

/* ---------- the sections ---------- */
function consoleList(){return info&&info.consoles||[]}
// the Consoles grid's order: your shelves first, hidden ones after (dimmed); every use of the grid's position goes
// through this, so a tile and its index always agree
function tileList(){const L=consoleList();return [...L.filter(c=>sysCfg(c.id).enabled!==false),...L.filter(c=>sysCfg(c.id).enabled===false)]}
function sysCfg(id){cfg.systems=cfg.systems||{};const c=cfg.systems[id]||(cfg.systems[id]={enabled:true,emulator:"",gameArgs:"",emulatorArgs:"",romDirs:[],artDir:"",bootOnLaunch:true});if(!Array.isArray(c.romDirs))c.romDirs=[];return c}
function conState(c){const s=sysCfg(c.id);if(s.enabled===false)return "off";return s.emulator&&s.romDirs.length?"ok":s.emulator||s.romDirs.length?"warn":""}
// (games counted on the last scan: "13 games"; until a scan has run, the folders)
const gameCount=id=>{const m=shelf("gameCounts");return m&&typeof m[id]==="number"?m[id]:null};
function conStatus(c){const s=sysCfg(c.id),d=conState(c),n=s.romDirs.length,g=gameCount(c.id);
  return d==="off"?"Shelf hidden":d==="ok"?(g!=null?`Ready · ${g} game${g===1?"":"s"}`:`Ready · ${n} folder${n===1?"":"s"}`):d==="warn"?(s.emulator?"Add a games folder":`Needs ${c.emulatorName||"its emulator"}`):"Not set up"}
// a program shown by its name ("Dolphin.exe" → "Dolphin"); the whole path stays in the row's explanation
const progName=p=>String(p||"").split(/[\\/]/).pop().replace(/\.exe$/i,"");
// (beta.10) a console tile names the emulator actually picked when it's another known one (Switch: Citron, Ryujinx), else its usual one
const emuShownName=c=>{const n=progName(((cfg&&cfg.systems&&cfg.systems[c.id])||{}).emulator).toLowerCase();return ({citron:"Citron",ryujinx:"Ryujinx"})[n]||c.emulatorName||""};
const folderName=p=>{const L=String(p||"").split(/[\\/]/).filter(Boolean);return L.slice(-2).join(" › ")||String(p||"")};
function consolesReady(){const on=consoleList().filter(c=>sysCfg(c.id).enabled!==false);return `${on.filter(c=>conState(c)==="ok").length}/${on.length}`}
const melody=()=>voiceOf(gp("ui.soundTheme"))==="melody";
const voiceOf=v=>({arcade:"musicbox",pulse:"musicbox",glass:"aero",wood:"aero",xylo:"melody",jazz:"melody"})[v]||v||"aero";
const swapAB=()=>{const v=gp("ui.confirmButton")||"auto";return v==="b"||(v==="auto"&&padStyle()==="nintendo")};
function padStyle(){const p=gp("ui.buttons")||"auto";if(p==="xbox"||p==="ps"||p==="nintendo")return p;if(window.BS_padStyleOf)return BS_padStyleOf(padId); /* the shelf's detection: maker and model, and what's behind an XInput pad */return /054c|dualsense|dualshock|playstation|^wireless controller/i.test(padId)?"ps":/057e|nintendo|pro controller|joy-con/i.test(padId)?"nintendo":"xbox"}
// the shelf's prompts changed with the pad in use (another pad, or what's behind an XInput one): pictures follow
addEventListener("bs-custom-themes",()=>{try{syncCustomThemes();if(typeof render==="function"&&window.Settings&&Settings.isOpen&&Settings.isOpen())render()}catch{}});
addEventListener("bs-padstyle",()=>{try{oskSig="";if(device==="pad"&&typeof render==="function")render()}catch{}});
// the quit combo's buttons in the names printed on your pad (padwatch reads them by position: "B" is the right face button,
// which a Switch pad calls A; Start / Back are + / − there and Options / Share on a PlayStation pad)
function comboOpts(){const st=padStyle(),N=st==="ps"?{lb:"L1",rb:"R1",start:"Options",back:"Share",b:"Circle"}:st==="nintendo"?{lb:"L",rb:"R",start:"Plus",back:"Minus",b:"A"}:{lb:"LB",rb:"RB",start:"Start",back:"Back",b:"B"};
  return [["l3+r3","L3 + R3"],["lb+rb+start",`${N.lb} + ${N.rb} + ${N.start}`],["back+b",`${N.back} + ${N.b}`],["start+back",`${N.start} + ${N.back}`]]}
// Accounts & services (1.71): one row per service, A opens its own small page (sub kind "svc") with its fields, Test and the sign-up link
const SVC_N={ra:"RetroAchievements",fanart:"fanart.tv",steamgriddb:"SteamGridDB",screenscraper:"ScreenScraper"},ART_SVC=["fanart","steamgriddb","screenscraper"];
const svcSet=s=>s==="ra"?!!(gp("ra.user")&&gp("ra.key")):s==="screenscraper"?!!(gp("keys.ssUser")&&gp("keys.ssPass")):!!gp("keys."+s);
function svcState(s){const st=status[s]||{};if(!svcSet(s))return {state:"none"};return st.state&&st.state!=="none"?st:{state:"unknown"}} // set up but never checked: main says "none" (Not checked yet)
function acctDot(){const L=["ra",...ART_SVC].map(svcState).filter(x=>x.state!=="none"&&x.state!=="unknown");if(!L.length)return null;return L.some(x=>x.state==="fail")?"warn":"ok"}
function pathSub(p,want){if(!p)return null;const ok=pathOk[p];if(ok==null){if(ok===undefined)pathAsk.add(p);return null}const good=want==="file"?ok.file:ok.dir;return good?{text:want==="file"?"Found":"Folder found",cls:"ok"}:{text:"Can't find it",cls:"bad"}}
function openLink(url){if(api.openExternal)Promise.resolve(api.openExternal(url)).then(()=>toast("Opened in your browser")).catch(e=>toast(errMsg(e)));else toast("Opening links needs a newer Boxshelf")}
function twice(key,run,msg){const t=Date.now();if(askT[key]&&t-askT[key]<4000){askT[key]=0;run()}else{askT[key]=t;toast(msg||"Press again to confirm");setTimeout(()=>{if(askT[key]===t){askT[key]=0;bump()}},4100)}bump()}
const asking=key=>askT[key]&&Date.now()-askT[key]<4000;

function build(){
  const perfWhy=window.Perf&&Perf.detect?(()=>{try{const d=Perf.detect();return `Automatic picks ${({high:"High",balanced:"Balanced",low:"Low",handheld:"Handheld"})[d.tier]||d.tier} for this PC${d.why?" ("+d.why+")":""}`}catch{return "Automatic picks for this PC"}})():"Automatic picks for this PC";
  // (1.78) each app on a page of its own (the Apps page was ten rows); its row says how it stands
  // (MORE is emptied for this build first: these pages register in it, and the reset further down would drop them)
  MORE={};const appRows=[];(info.apps||[]).forEach(a=>{const p=`apps.${a.id}.`;appRows.push(M(a.name,"app-"+a.id,`${a.name}'s settings: whether it shows on the Media shelf, how it starts, and where it's installed.`,`Launched from the Media shelf.`,[
    T(`Show ${a.name}`,p+"enabled",true,a.id==="stremio"?"Puts Stremio on the Media shelf, and your Stremio library on the museum's movie shelves.":`Puts ${a.name} on the Media shelf.`,{rescan:true}),
    T("Go fullscreen after start",p+"fullscreen",true,"Presses the app's fullscreen key once its window is up."),
    T("Controller as mouse",p+"padMouse",false,"The left stick moves a pointer and A clicks, for apps with no controller support. Leave it off when the app has its own (Stremio: Settings → Enable gamepad support)."),
    F("Program",p+"path","file",`Found automatically. Point to ${(a.exeNames||[a.name+".exe"])[0]} if it's installed somewhere unusual.`,{rescan:true,ph:"Not found",filters:[{name:a.name,extensions:["exe"]}],want:"file",keys:a.name+" exe"}),
    A(`Get ${a.name}`,"Open site",`Opens ${a.name}'s download page in your browser.`,()=>openLink(OFFICIAL[a.id]||""),{when:()=>!!OFFICIAL[a.id]&&!gp(p+"path"),ns:true})],
    {btn:()=>!gp(p+"path")?"Not installed":gp(p+"enabled")===false?"Hidden":"On",keys:a.name+" app media"}))});
  const musS=(label,key,min,max,step,fmt,info,x={})=>S(label,"ui.museumStyle."+key,musDef(key),min,max,step,fmt,info,{preview:"mus",...x});
  const musC=(label,key,info,x={})=>C(label,"ui.museumStyle."+key,()=>musOpts(key),musDef(key),info,{preview:"mus",...x});
  const musT=(label,key,info,x={})=>T(label,"ui.museumStyle."+key,musDef(key)!==undefined?!!musDef(key):true,info,{...x});
  SECTIONS=[
  {g:"Games",id:"consoles",name:"Consoles",ic:"games",badge:consolesReady,lead:"Every console Boxshelf can run. Pick one to set its emulator, games folders and how it starts.",rows:[
    {t:"consoles",label:"Consoles",info:""},
    H("For every console"),
    A("Find emulators again",()=>detecting?"Searching…":"Search","Looks in the usual install places and your Boxshelf folder for emulators and games folders you haven't set up yet, and fills in any console that has nothing set.",runDetect,{keys:"detect scan auto find"}),
  ]},
  {g:"Games",id:"pc",name:"PC games",ic:"pc",lead:"Your installed Steam games, on a shelf of their own.",rows:[
    T("Show installed Steam games","steam.enabled",true,"Adds a PC shelf with every game Steam has installed. Uninstalled games are left out.",{rescan:true,keys:"steam"}),
    T("Hide Steam tools","steam.hideTools",true,"Leaves Proton, Steamworks Common Redistributables and other tools off the shelf.",{rescan:true,keys:"redistributables proton steam"}),
    F("Steam folder","steam.path","folder","The folder that contains steam.exe. Found automatically; change it only if Steam lives somewhere unusual.",{ph:"C:\\Program Files (x86)\\Steam",rescan:true,want:"dir",pickTitle:"Choose your Steam folder"}),
    A("Get Steam","Open site","Opens Steam's download page in your browser.",()=>openLink(OFFICIAL.steam)),
    // (beta.4) Epic's installs join the PC shelf (main/pcgames.js reads its install list; Play goes through its launcher)
    T("Show installed Epic Games","pc.epic",true,"Adds the games the Epic Games Launcher has installed to the PC shelf. They start through the Epic launcher, so its sign-in and cloud saves work as usual.",{rescan:true,keys:"epic games store fortnite launcher",sub:()=>pcCount("epic")}),
    // (beta.7) more stores: each one's installs join the PC shelf (main/pcgames.js storeGames)
    T("Show installed GOG games","pc.gog",true,"Adds the games GOG Galaxy has installed to the PC shelf. GOG games have no copy protection, so Boxshelf starts them itself, with the Home menu and quit combo like any game.",{rescan:true,keys:"gog galaxy store",sub:()=>pcCount("gog")}),
    T("Show installed Ubisoft games","pc.ubisoft",true,"Adds the games Ubisoft Connect has installed to the PC shelf. They start through Ubisoft Connect, so its sign-in and cloud saves work as usual.",{rescan:true,keys:"ubisoft connect uplay store",sub:()=>pcCount("ubisoft")}),
    T("Show installed EA games","pc.ea",true,"Adds the games the EA app has installed to the PC shelf. They start through the EA app, so its sign-in and cloud saves work as usual.",{rescan:true,keys:"ea app origin electronic arts store",sub:()=>pcCount("ea")}),
    T("Show installed Xbox games","pc.xbox",true,"Adds the games the Xbox app has installed (Game Pass included) to the PC shelf. They start like any Xbox app game, signed in with your Microsoft account.",{rescan:true,keys:"xbox game pass microsoft store",sub:()=>pcCount("xbox")}),
  ]},
  // (beta.4) the Custom shelf: Quiver Launcher's apps (recomps, ports) and any program added by hand
  {g:"Games",id:"custom",name:"Custom games",ic:"pc",lead:"Recomps, PC ports and any other program, on a Custom shelf of their own.",rows:[{t:"dyn",fn:customRows}]},
  {g:"Games",id:"apps",name:"Apps",ic:"apps",dot:()=>Object.values(cfg.apps||{}).some(a=>a&&a.enabled!==false&&a.path)?"ok":null,lead:"Stremio and Nuvio, launched from the Media shelf.",rows:appRows},
  {g:"Look & sound",id:"display",name:"Display",ic:"display",lead:"How the shelf looks from the couch.",rows:[
    C("Interface size","ui.uiScale",[[1,"Standard"],[1.15,"Large"],[1.3,"Extra large"]],1,"Bigger text and buttons for a TV across the room.",{more:"Large suits most 55\" and bigger TVs at 2–3 m. Extra large for very far seating.",live:()=>fit(),keys:"zoom text big small scale"}),
    // (1.78) no peek: the page stays put and takes on the theme's colour at once; the shelf behind switches once you stop on a
    // theme (stepping through them quickly used to fade the page out and in and cross-fade the shelf on every step)
    {t:"theme",label:"Theme",info:"The colour and mood behind your shelves. This page takes on each theme's colour as you move through them; close Settings to see it on your shelf.",opts:()=>THEMES.map(t=>[t[0],t[1]]),...bindv("ui.theme","spectrum"),live:v=>{applyTint();clearTimeout(themeT);themeT=setTimeout(()=>shelf("setTheme",v),150)},keys:"spectrum classic keys arcade aurora midnight artwork blur picture wallpaper game art cube blue shift swirl pocket oled walnut forest crt vhs synthwave background colour color"},
    // (1.76, Windows) custom themes: a picture becomes a theme (a folder in Custom themes\ with the picture and a theme.json); (1.78) on a page of their own
    M("Custom themes","custom-themes","Make a theme from any picture, or open the folder your themes live in.","A picture becomes a theme behind your shelves.",[
    A("Make a theme from a picture","Choose","Pick any picture (JPG, PNG, WebP or SVG). Boxshelf makes a theme from it and shows it behind your shelves. Change its name, shade or glow later in its theme.json.",async()=>{
      const f=await pick({kind:"file",title:"Choose a picture for your theme",filters:[{name:"Pictures",extensions:["jpg","jpeg","png","webp","svg"]}]}),file=typeof f==="string"?f:f&&f.path;if(!file)return;
      try{const id=await api.customThemeCreate(file);await shelf("reloadCustomThemes");if(id&&(window.BS_CUSTOM_THEMES||[]).some(t=>t.id===id)){sp("ui.theme",id);shelf("setTheme",id);applyTint();changed(null);const t=window.BS_CUSTOM_THEMES.find(x=>x.id===id);toast(`New theme: ${t?t.name:"your picture"}`)}else toast("Theme made. Find themes again to see it")}catch(e){toast(errMsg(e))}},
      {when:()=>!!api.customThemeCreate,keys:"custom own theme picture wallpaper photo image background make create",ns:true}),
    A("Open custom themes folder","Open","Each folder in it is a theme: a picture named background, an optional picture per console (gc.jpg, ps2.png…) and theme.json for its name, shade, blur and glow. A text file there explains it.",async()=>{try{await api.customThemesOpen()}catch(e){toast(errMsg(e))}},
      {when:()=>!!api.customThemesOpen,keys:"custom themes folder",ns:true}),
    A("Find themes again","Find","Looks in the custom themes folder again after you add, change or remove a theme.",async()=>{await shelf("reloadCustomThemes");const n=(window.BS_CUSTOM_THEMES||[]).length;toast(n?`${n} custom theme${n===1?"":"s"}`:"No custom themes yet")},
      {when:()=>!!api.customThemes,keys:"custom themes reload refresh",ns:true})],
      {when:()=>!!api.customThemeCreate,keys:"custom own themes picture wallpaper photo image background make create folder"}),
    C("Clock","ui.clock",[["full","Day, date and time"],["time","Time only"],["off","Hidden"]],"full","What shows in the top corner of the shelf.",{keys:"time date",live:v=>applyUi({clock:v})}),
    T("24-hour clock","ui.clock24",false,"Shows 18:30 instead of 6:30 PM.",{keys:"time 24 hour",live:v=>applyUi({clock24:v})}),
    // a second screen (the AYN Thor's bottom screen): only offered when the device has one (Android: android-bottom.js)
    C("Bottom screen","ui.bottomScreen",[["on","On"],["off","Off"]],"on","The small screen under the shelf shows the game you're on: its case to turn and open with a finger, its details, trophies and big touch buttons. While a game is playing the bottom screen is always handed to the game, so DS and 3DS emulators and bottom-screen mods get all of it.",{when:()=>!!(api.bottomScreen&&api.bottomScreen.available()),keys:"bottom screen second display dual thor touch ds 3ds",live:v=>api.bottomScreen.set(v!=="off"),
      // (0.4.0) what the native side really does (off by default on devices that aren't AYN's: a TV or a cast isn't a bottom screen)
      get:()=>{const s=api.bottomScreen&&api.bottomScreen.status();return s&&typeof s.enabled==="boolean"?(s.enabled?"on":"off"):(gp("ui.bottomScreen")||"on")},
      sub:()=>{const s=api.bottomScreen&&api.bottomScreen.status();return s&&s.builtIn===false?{text:`A second display was found${s.name?` (${s.name})`:""}. On this device (${s.device||"not a Thor"}) it stays off unless you turn it on.`}:null}}),
    H("Starting up"),
    C("Startup animation","ui.bootIntro",[["ripple","Logo + ripple"],["fade","Logo + fade"],["off","Off"]],"ripple","The Boxshelf logo shows while your shelf loads, then the games come in: the one you were on first, rippling out to each side, or the whole shelf fading up together. Off starts straight on the shelf.",{keys:"boot intro",live:v=>{try{localStorage.setItem("bootIntro",v)}catch{}}}),
    C("Open into","ui.openInto",[["last","Last shelf"],["home","Home shelf"],["museum","Museum"]],"last","Where Boxshelf goes when it starts: the console shelf you were on last time, Home (continue playing and favourites), or straight into the museum.",{more:"Museum: after the Boxshelf logo the lights come up in the museum overview (a little slower to start than the shelf). B still takes you to the shelf any time.",keys:"start startup boot launch open first home museum shelf",live:v=>applyUi({openInto:v})}),
    H("More"),
    M("Screen & motion","display-more","Which screen, fullscreen, fitting the picture to your TV and reduce motion.","Set these once for your TV.",[
      T("Start in fullscreen","ui.startFullscreen",true,"Opens Boxshelf fullscreen. Press F11 any time to switch.",{keys:"full screen window"}),
      T("Hide the taskbar during games","ui.hideTaskbar",true,"Nothing but Boxshelf and your game on screen: the Windows taskbar is hidden while a game starts, runs and closes, and comes back on the shelf.",{keys:"taskbar windows bar immersion flash desktop start menu"}),
      C("Screen","ui.display",()=>displays,"","Which screen Boxshelf opens on, for a PC with both a monitor and a TV. Games open on the same screen.",{when:()=>!!(displays&&displays.length),keys:"monitor tv hdmi output display second",live:v=>{if(api.moveToDisplay){const d=(displays||[]).find(x=>x[0]===v);Promise.resolve(api.moveToDisplay(v||(d&&d[2])||"")).catch(e=>toast(errMsg(e)))}}}),
      S("Fit to TV","ui.safeArea",100,90,100,1,v=>v+"%","Shrinks everything slightly if your TV cuts off the edges of the picture (overscan). Watch the corners of the screen while you change it.",{keys:"overscan safe area edges cut off zoom",live:v=>{applyUi({safeArea:v});fit()}}),
      T("Reduce motion","ui.reduceMotion",false,"Tones down the startup ripple, the museum walk-in and the moving theme backgrounds.",{keys:"animation accessibility motion sickness",live:v=>{applyUi({reduceMotion:v});document.body.classList.toggle("bss-reduce",!!v)}}),
    ]),
  ]},
  {g:"Look & sound",id:"perf",name:"Performance",ic:"perf",lead:"One setting for how much Boxshelf asks of your graphics card.",rows:[
    C("Performance","ui.performance",[["auto","Automatic"],["high","High"],["balanced","Balanced"],...(window.bsPlatform&&bsPlatform.os==="android"?[["handheld","Handheld"]]:[]),["low","Low"]],"auto",perfWhy+", and lets the museum render a touch softer whenever frames run slow.",{more:"High: everything on (reflections, glow, shadows, full resolution).\nBalanced: lighter shadows, background at 30 fps.\nLow: still background, no blur or shadows, museum at 0.8×.\nHandheld (Android): no reflections or glow pulse, no museum shadows, background at 30 fps.\nVisual effects and museum shadows follow this level.",keys:"fps lag slow graphics smooth effects reflections glow shadows",live:v=>shelf("setPerformance",v)}),
  ]},
  {g:"Look & sound",id:"sound",name:"Sound",ic:"sound",lead:"Interface sounds, menu music, songs and the museum's room tone.",rows:[
    T("Interface sounds","ui.sound",true,"Ticks, whooshes and chimes as you move around.",{live:v=>shelf("sound",{enabled:v,preview:v?"open":null})}),
    S("Volume","ui.volume",.7,0,1,.05,v=>Math.round(v*100)+"%","How loud Boxshelf's own sounds are. Games and apps aren't affected.",{keys:"loud quiet",live:v=>{shelf("sound",{volume:v});soundSoon("open")}}),
    {t:"choice",label:"Voice",opts:[["aero","Aero"],["melody","Melody"],["musicbox","Music box"]],path:"ui.soundTheme",d:"aero",get:()=>voiceOf(gp("ui.soundTheme")),set:v=>sp("ui.soundTheme",v),info:"Aero: soft, pure and airy; each console's shelf plays in its own key.\nMelody: a soft piano — every move plays the next note of a song, and the song changes every few minutes.\nMusic box: small steel tines, warm and gentle.",keys:"sound theme piano",live:v=>shelf("sound",{theme:v,preview:"open"})},
    C("Menu music","ui.music",[["drift","Drift"],["night","Night"],["glow","Glow"],["off","Off"]],"off","Quiet ambient music under the shelf, like a console's home screen. Drift: airy and warm. Night: darker and slower. Glow: a slow music-box lullaby. It fades out when a game starts.",{get:()=>{const v=gp("ui.music");return ({calm:"drift",lounge:"night",chime:"glow"})[v]||v||"drift"} /* (1.75's names) */,keys:"ambient background music bgm soundtrack",live:v=>shelf("sound",{music:v})}),
    S("Music volume","ui.musicLevel",.4,0,1,.05,v=>Math.round(v*100)+"%","How loud the menu music plays, under Boxshelf's other sounds.",{keys:"ambient background music bgm quiet loud",when:()=>gp("ui.music")!=="off",live:v=>shelf("sound",{musicLevel:v})}),
    C("Boot sound","ui.bootSound",[["chime","Chime"],["crystal","Crystal"],["pulse","Pulse"],["horizon","Horizon"],["keys","Keys"]],"chime","The sting that plays with a console's boot animation.",{live:v=>{shelf("sound",{boot:v});soundSoon("boot")}}),
    C("Boot screen length","ui.bootLen",[["short","Short"],["normal","Normal"],["long","Long"]],"normal","How long a console's boot screen stays up before the game starts (the built-in one; a console's own boot video plays to its end).",{keys:"boot animation intro launch time longer",when:()=>!!(window.bsPlatform&&bsPlatform.os==="android")}), // (Android only: the PC keeps its short one)
    C("Output device","ui.audioOut",()=>outputs,"","Where Boxshelf's sounds play. Games use their own emulator's audio setting.",{when:()=>!!(outputs&&outputs.length),keys:"audio hdmi speakers headphones",live:v=>applyUi({audioOut:v})}),
    A("Next song","Skip","Moves on to the next song.",()=>{if(window.Sound&&Sound.nextSong){Sound.nextSong("pick");if(Sound.play)Sound.play("shelf");const s=Sound.song&&Sound.song();if(s)toast("Now playing: "+s.name)}},{when:melody,sub:()=>{const s=window.Sound&&Sound.song&&Sound.song();return s?{text:"Now playing: "+s.name}:null}}),
    H("More"),
    M("Room tone & songs","sound-more","The museum's room tone, and (with Melody) your own songs.","The quiet room sound in the museum, and the songs Melody plays.",[
      H("Museum"),
      T("Room tone in the museum","ui.museumRoom",true,"A quiet room sound while you walk the museum."),
      H("Songs",{when:melody}),
      T("Include the built-in songs","ui.builtInSongs",true,"With Melody, Boxshelf's own songs play along with any MIDI files in your Songs folder.",{when:melody,live:v=>{if(window.Sound&&Sound.set)Sound.set({builtInSongs:v})}}),
      A("Songs folder","Open","Drop .mid files here (piano versions of game themes work great), then press Reload.",()=>api.openSongsFolder&&api.openSongsFolder(),{when:melody,keys:"midi"}),
      A("Reload songs","Reload","Reads your Songs folder again.",async()=>{const r=await shelf("reloadSongs");if(r)toast(`${r.songs} song${r.songs===1?"":"s"} loaded`+(r.skipped&&r.skipped.length?` · ${r.skipped.length} couldn't be read`:""))},{when:melody}),
    ]),
  ]},
  {g:"Boxshelf",id:"shelf",name:"Shelf",ic:"shelf",lead:"How the shelf behaves: console order, shuffle and the idle show.",rows:[
    A("Console order","Reorder","The order of the consoles along the top. Home always comes first.",()=>openSub({kind:"order"}),{keys:"shelf order arrange tabs move"}),
    // (beta.7) your own shelves (Details → Collections adds games to them): renamed and deleted here
    M("Collections","shelf-cols","Shelves of your own, mixing games from any console (Details → Collections adds a game). Rename or delete them here.","Each one is a shelf beside Home.",[{t:"dyn",fn:colManageRows}],{keys:"collections lists playlists own shelves"}),
    T("Shuffle from every shelf","ui.shuffleAll",true,"Surprise me picks from all consoles instead of just the shelf you're on.",{keys:"random surprise"}),
    T("Attract mode","ui.attract",true,"After a few idle minutes the shelf drifts through your boxes on its own, with the HUD faded, like a store display.",{keys:"idle screensaver demo"}),
    C("Attract mode starts after","ui.attractMinutes",[[2,"2 min"],[3,"3 min"],[5,"5 min"],[10,"10 min"],[20,"20 min"]],3,"How long without input before the tour begins.",{keys:"idle",live:v=>applyUi({attractMinutes:v})}),
    C("Dim the screen after","ui.dimMinutes",[[0,"Never"],[10,"10 min"],[20,"20 min"],[30,"30 min"]],0,"Fades the screen to black after a long idle spell, to protect OLED TVs. Any button wakes it.",{keys:"oled burn in screensaver blank sleep",live:v=>applyUi({dimMinutes:v})}),
  ]},
  {g:"Boxshelf",id:"museum",name:"Museum",ic:"museum",lead:"Your collection's room. Changes preview live behind this page; all of them can also be changed inside the museum (Y).",rows:[
    H("Lighting"),
    {t:"choice",label:"Scene",opts:()=>musOpts("light"),...bindv("ui.museumLight","gallery"),set:v=>{sp("ui.museumLight",v);if(cfg.ui.museumStyle)cfg.ui.museumStyle.timeOfDay=false},info:"The whole room's light at once, cross-faded as you change it. Classic is the original look; Neon night lights each shelf in its console's colour; Lights out leaves only the TV and the shelves' own glow.",keys:"lighting mood daylight evening neon spotlit gallery dark",preview:"mus",live:v=>shelf("setMuseumLight",v)},
    musT("Follow the time of day","timeOfDay","Daylight in the morning, Classic in the afternoon, Evening at night."),
    M("Fine-tune the light","mus-light","Brightness, warmth, the spotlight on the case and the dust in the light.","On top of the scene you picked.",[
      musS("Brightness","bright",.4,1.6,.1,v=>Math.round(v*100)+"%","Overall brightness of the room, on top of the scene."),
      musS("Warmth","warmth",-1,1,.1,v=>v>.05?`Warm ${Math.round(v*100)}%`:v<-.05?`Cool ${Math.round(-v*100)}%`:"Neutral","Cooler or warmer light."),
      musS("Spotlight on the case","spot",0,2,.1,v=>Math.round(v*100)+"%","How strongly the selected case is lit.",{preview:null}),
      musS("Dust in the light","dust",0,2,.25,v=>v===0?"Off":Math.round(v*100)+"%","Specks drifting in the lamp light. (The Low performance level turns them off.)",{preview:null}),
    ]),
    H("Retro TV"),
    musT("Retro TV","tv","A CRT in the room that plays vintage commercials for your games, their trailers, or what you played lately.",{keys:"commercials ads crt television trailers",preview:"mus"}),
    // the TV guide lives in the museum (it needs the 3D room): this goes there, at the TV, with the guide open
    A("Choose what's on","Open guide","Opens the museum at the TV with its guide: commercials, trailers, recently played, or all three shuffled. Switches the TV on if it's off.",async()=>{await close();if(shelf("openTvGuide")===false)shelf("toast","The museum opens from the shelf");},{keys:"tv guide channel whats on commercials trailers shuffle favourites"}),
    M("TV set & sound","mus-tv","The TV's stand, size, place in the room, channel, sound and CRT look.","How the retro TV looks and sounds.",[
      ...(()=>{const on=()=>!!gp("ui.museumStyle.tv"),pct=v=>v<=0?"Off":Math.round(v*100)+"%";return [
        musC("Stand","tvStand","A wooden cart with a VCR, a low cabinet with a couple of consoles inside, a floor-standing tube set, or a bracket on the wall.",{when:on,keys:"cart cabinet wall mount floor"}),
        musC("Size","tvSize","Small, medium or large.",{when:on}),
        musC("Position","tvPos","Left or right of the room, or in the middle between the shelves and the reading corner (which moves along to make room).",{when:on,keys:"left right centre center"}),
        musC("Channel","tvCh","What's on when you switch it on. At the TV, up and down change the channel too; Shuffle all mixes the other three.",{when:on,keys:"commercials trailers recently played",preview:null}),
        musS("TV volume","tvVol",0,1,.1,pct,"On top of Boxshelf's own volume.",{when:on,preview:null,keys:"sound loud"}),
        musT("Mute the TV","tvMute","Pictures only.",{when:on,keys:"sound silent"}),
        musT("Sound only when I'm looking at it","tvNear","The sound fades in as you face the TV or walk up to it, and away as you leave.",{when:on,keys:"sound fade near"}),
        musS("CRT effect","tvCrt",0,1,.1,pct,"Scanlines, curved glass and glow on the TV's picture.",{when:on,keys:"scanlines curvature glow"}),
        musT("Power on and off animation","tvAnim","The picture opens out of a bright line when it switches on, and folds back to a dot when it goes off.",{when:on})]})(),
    ],{when:()=>!!gp("ui.museumStyle.tv")}),
    H("Room"),
    M("Walls, gallery & visits","mus-room","Walls, floor and shelves, the gallery pieces, how a visit starts, and resetting the room.","The room itself, the pieces in it and how a visit starts.",[
      H("Room"),
      musC("Walls","wall","The walls of the room."),musC("Floor","floor","What the floor is made of."),musC("Shelves","shelf","The wood (or paint) of the shelves."),
      H("Gallery pieces"),
      musT("Display plinths","plinths","Your favourite (or most played) games on lit plinths."),musT("Velvet ropes","ropes","Brass posts and a velvet rope."),musT("Museum bench","bench","A bench in front of the shelves."),musT("Posters of your favourites","posters","Framed box-art posters on the walls."),
      H("Visits"),
      musT("Deliveries for new games","mail","New games arrive in a parcel you unpack, instead of already being on the shelf. From your next visit.",{keys:"mail parcel crate package"}),
      musT("Walk-in when you enter","walkIn","The lights come on shelf by shelf. Off goes straight to the overview.",{keys:"intro lights skip"}),
      A("Reset the room",()=>asking("musreset")?"Press again":"Reset","Puts every museum setting back to how it started.",()=>twice("musreset",()=>{delete cfg.ui.museumStyle;cfg.ui.museumLight="gallery";shelf("setMuseumLight","gallery");changed(null);toast("The room is back to how it started")},"Press again to reset the room"),{warnBtn:()=>asking("musreset")}),
    ]),
  ]},
  {g:"Boxshelf",id:"pad",name:"Controller",ic:"pad",lead:"Pads, button prompts and the in-game Home menu.",rows:[
    {t:"dyn",fn:()=>{const n=window.BS_padName?BS_padName():"";return [A(n?`In use: ${n}`:"No controller in use yet","Refresh",n?"The controller Boxshelf last saw you press, and the buttons it shows for it. Press a button on another one to switch.":"Press a button on a controller and Boxshelf shows what it is here.",()=>{try{listSig="";render()}catch{}},{keys:"detected controller which pad model dualsense xbox switch",ns:true})]}},
    C("Button prompts","ui.buttons",[["auto","Match my controller"],["xbox","Xbox (A B X Y)"],["ps","PlayStation (shapes)"],["nintendo","Nintendo (B A Y X)"]],"auto","Changes the button pictures on screen.",{keys:"glyphs icons playstation xbox",live:v=>shelf("setButtons",v)}),
    C("Confirm button","ui.confirmButton",()=>{const st=padStyle(),L=st==="ps"?["Cross","Circle"]:st==="nintendo"?["B","A"]:["A","B"];return [["auto","Match my controller"],["a",`${L[0]} (bottom)`],["b",`${L[1]} (right)`]]},"auto","Which button selects. Match my controller: A on the right with a Switch pad, as on a Switch; the bottom button with Xbox and PlayStation pads.",{keys:"swap a b nintendo",live:v=>applyUi({confirmButton:v})}),
    C("Scroll speed","ui.scrollSpeed",[["relaxed","Relaxed"],["normal","Normal"],["fast","Fast"]],"normal","How quickly the shelf moves when you hold the stick or d-pad.",{keys:"repeat hold speed",live:v=>applyUi({scrollSpeed:v})}),
    T("Controller rumble","ui.rumble",true,"Short pulses as you play: launching a game, the shuffle landing, favouriting or hiding a game, changing shelf, and picking things up in the museum.",{keys:"vibration"}),
    ...(window.bsPlatform&&bsPlatform.os==="android"?[]:[
      // (1.78) the Wii remote on a page of its own (only DolphinBar owners need it); its row shows On / Off
      M("Wii remote","pad-wiimote","A Wii remote on a Mayflash DolphinBar: point to move the hand, A to click. Turn it on or off, and how you hold it.","A Wii remote on a Mayflash DolphinBar, as a pointer and a controller.",[
      T("Wii remote","ui.wiimote",true,"A Wii remote on a Mayflash DolphinBar, left in its Dolphin mode (4): point at the screen to move the hand, press A to click, hold A and move to drag the shelf. The D-pad and buttons work like a controller, and Home opens the Home menu. Dolphin gets the remote for its games.",{keys:"wiimote wii remote dolphinbar mayflash pointer ir motion"}),
      C("Hold the remote","ui.wmHold",[["upright","Upright (pointing)"],["sideways","Sideways"]],"upright","Upright: A selects, B goes back. Sideways, like a classic pad: the D-pad turned, 2 selects, 1 goes back.",{keys:"wiimote sideways horizontal",when:()=>gp("ui.wiimote")!==false}),
      S("Pointer speed","ui.wmSpeed",1.5,.8,3,.1,v=>(+v).toFixed(1)+"×","How far the hand moves for a turn of the remote: higher covers the screen with a smaller turn.",{keys:"wiimote sensitivity pointer",when:()=>gp("ui.wiimote")!==false})],
      {btn:()=>gp("ui.wiimote")===false?"Off":"On",keys:"wiimote wii remote dolphinbar mayflash pointer"})]),
    // (1.78) a row that only matters while its switch is on hides with it
    H("Home menu"),
    T("Home menu","ui.homeMenu",true,"In a game: press Guide (or hold View / Back for a second) to pause, save, load, take a screenshot or quit. On the shelf: the same button opens Home shelf, Museum, Settings and Power. Keyboard: Home or P.",{more:"If Guide opens the Xbox Game Bar instead, turn off 'Open Game Bar using this button on a controller' in Windows Settings → Gaming."}),
    C("Home menu layout","ui.homeLayout",[["side","Side guide"],["center","Centred card"]],"side","Where the Home menu appears.",{when:()=>gp("ui.homeMenu")!==false}),
    H("Quit combo"),
    T("Quit combo","ui.quitCombo",true,"Hold these buttons about a second while a game runs to close the emulator and come back; hold them on the shelf to quit Boxshelf."),
    {t:"choice",label:"Buttons to hold",when:()=>gp("ui.quitCombo")!==false,opts:comboOpts,...bindv("ui.quitComboKeys","l3+r3"),set:v=>{sp("ui.quitComboKeys",v);sp("ui.quitComboChosen",true)},info:"The buttons for the quit combo. They're the same buttons by position on every pad.",keys:"quit combo exit close game hold l3 r3 lb rb start back select plus minus options share"},
  ]},
  // (1.80) Box art first: the summary in the heading, Art Studio as the page's first row (with the games that have no cover)
  {g:"Library",id:"art",name:"Box art & manuals",ic:"art",lead:"How your box art looks, what's still missing, and your manuals.",dot:()=>artNoCover()>0?"warn":null,rows:[
    H("Box art",{em:artSummary}),
    M("Art Studio","art-studio","Each console's shelf style (authentic scans, sharpest HD, one region or special editions), then any game's own mix.","Pick a console to choose how its box art looks, then fine-tune any game.",[
      H("Consoles",{em:()=>{const L=Object.values(sJobs).filter(j=>j.state==="run");return L.length?`${L.length} re-fetch${L.length===1?"":"es"} running`:"Box art per console and per game"}}),
      {t:"dyn",fn:studioRows},
      H(""),
      A("Refresh art database",()=>lbRef?"Cancel":"Refresh","Downloads the LaunchBox game database again (about 110 MB) and rebuilds Boxshelf's index of it, so newer picture types (reconstructed fronts and backs, new scans) show up in Art Studio. Your box art isn't touched; the old index keeps working until the new one is ready.",refreshIndex,{when:()=>!!api.artRefreshIndex,warnBtn:()=>!!lbRef,keys:"launchbox index database rebuild update metadata refresh reconstructed",sub:()=>({text:refText(),cls:lbRef?"warn":""})}),
    ],{when:()=>!!api.artStudio,keys:"art studio box art covers style look region hd special edition missing no cover",btn:()=>{const n=artNoCover();return n?`${n} without a cover`:""},warnBtn:()=>artNoCover()>0,
      sub:()=>{const L=Object.values(sJobs).filter(j=>j.state==="run"&&!j.man);return L.length?{text:`${L.length} re-fetch${L.length===1?"":"es"} running`,cls:"warn"}:{text:"Choose how each console's box art looks, and fix any game"}}}),
    T("Fetch box art for new games","ui.autoCovers",false,api.artStudio?"Covers, backs, spines and discs for new games, in the background, the way each console's shelf style asks. Searching sends game titles to LaunchBox, libretro, GameTDB and GamesDatabase.org; no accounts needed.":"Covers, backs, spines and discs for new games, in the background. Searching sends game titles to LaunchBox, libretro, GameTDB and GamesDatabase.org; no accounts needed.",{keys:"covers scrape download"}),
    ...(window.bsPlatform&&bsPlatform.os==="android"?[]:[T("Make missing backs and spines","ui.makeBacks",true,"A game with a cover but no scanned back or spine anywhere (most Switch 2 games, for now) gets them made: the back from its artwork, screenshots, description and rating, the spine in its console's style with the game's logo. A real scan replaces either as soon as one is found.",{keys:"back spine cover generate make missing switch 2"})]),
    // (Android swaps these two rows for its own by their old labels: android-api.js BS_SETTINGS_PATCH)
    M(window.bsPlatform&&bsPlatform.os==="android"?"Download box art now":"Download missing art now","art-get","Pick the consoles and what to get (covers, backs, spines, discs), then download with progress and Cancel.","Pick the consoles and what to get. Pictures you already have are kept, and each console follows its Art Studio style.",[{t:"dyn",fn:artGetRows}],{when:()=>!!api.artCoverage&&!!api.downloadCovers,keys:"download box art now covers scrape",sub:()=>AG.busy?{text:"Downloading…",cls:"warn"}:null}),
    H("Manuals"),
    T("Fetch manuals for new games","ui.autoManuals",false,"Finds the game's manual (archive.org and GamesDatabase.org) and turns it into pages you can read in Inspect.",{keys:"booklet"}),
    T("Only sure English manuals","ui.manualSure",true,"Skips manuals Boxshelf isn't sure about when you download them below. Off takes the best match for every game.",{keys:"language unsure"}),
    M(window.bsPlatform&&bsPlatform.os==="android"?"Download manuals now":"Get manuals","man-get","Pick the consoles, then download their manuals with progress and Cancel.","Pick the consoles to look for manuals on. Manuals you already have are kept.",[{t:"dyn",fn:manGetRows}],{when:()=>!!api.manualCoverage&&!!api.findManuals,keys:"download manuals now booklet",sub:()=>MG.busy?{text:"Downloading…",cls:"warn"}:null}),
    M("Manuals by console","art-manuals","Every manual found for each game, per console: another language, region or sharper scan, or a PDF of your own.","Pick a console, then any game to see every manual found for it.",[
      {t:"dyn",fn:manualRows}, // Manual Studio: one row per console → its games → every manual for a game
    ],{when:()=>!!api.manualStudio,keys:"manual studio manuals booklet choose language region"}),
    H("Library"),
    F("Art library folder","ui.artLibrary","folder","One folder for all box art and manuals, so a fresh setup finds them instead of downloading again. Art already beside your games is copied there in the background (nothing is moved or deleted).",{ph:"Beside each console's games (a covers folder)",d:"",rescan:true,want:"dir",pickTitle:"Choose a folder for your box art and manuals",keys:"covers folder path"}),
    M("Hidden games","art-hidden","Games you hid from their Details panel. Bring any of them back here.","Hidden from every shelf, the museum, shuffle and the idle show.",[],{when:()=>!!(window.Shelf&&Shelf.hiddenList),extra:hiddenRows,sub:()=>hidden&&hidden.length?{text:`${hidden.length} hidden`}:null}),
  ]},
  // (beta.8) video previews (app.js PV, main/previews.js, main/ytclips.js): a section of its own, easy to find (it was a
  // page inside Box art & manuals). Left out where previews don't exist yet (Android: see below SECTIONS).
  {g:"Library",id:"previews",name:"Video previews",ic:"pv",dot:()=>clipNeed()?"warn":null,lead:"Rest on a game and its preview plays. Videos you already have come first, then Steam's trailers for Steam games, then console trailers from YouTube.",rows:[
    C("Play previews","ui.previews",()=>window.bsPlatform&&bsPlatform.os==="android"?[["off","Off"],["details","In Details"]]:[["off","Off"],["details","In Details"],["shelf","Details and shelf"]],"off",
      "In Details: under the game's art, or in its Media tab. Details and shelf: softly behind the cases too while you rest on a game.",{keys:"video preview trailer snap movie playnite"}),
    // (beta.8) console trailers (main/ytclips.js): the switch, then the helper (fetched once) or what's kept
    T("Console trailers","ui.previewClips",true,"Saves the first 20 seconds of a console game's YouTube trailer as a small clip, so it plays behind the shelf and in Details just like a Steam trailer. Made the first time you rest on a game, never in bulk, and kept in Boxshelf's data folder (the Steam and console clips share 400 MB; the oldest go first).",{when:()=>gp("ui.previews")!=="off"&&clipOk(),keys:"console trailers youtube clips download playnite video preview shelf"}),
    {t:"dyn",fn:clipRows},
    T("Play previews with sound","ui.previewSound",false,"Off: previews play silently (Steam's trailer clips are always silent).",{when:()=>gp("ui.previews")!=="off",keys:"video preview sound audio mute"}),
    C("Start a preview after","ui.previewDelay",[[0.5,"Half a second"],[1,"1 second"],[1.5,"1.5 seconds"],[2,"2 seconds"],[3,"3 seconds"],[5,"5 seconds"],[8,"8 seconds"],[10,"10 seconds"],[15,"15 seconds"]],1.5,"How long you rest on a game before its preview starts (on the shelf, half a second more, so browsing quickly stays calm).",{when:()=>gp("ui.previews")!=="off",keys:"video preview delay wait"}),
    // (beta.9: the Thor only. On Windows, Overview's box never loads YouTube's player by itself, it lagged and took the
    // controller away; full screen opens the trailer either way)
    T("YouTube's player in Details","ui.previewYoutube",true,"A game's YouTube trailer plays in YouTube's own player in Details → Media (streamed: it needs the internet, and YouTube can now and then show an ad). Also covers the wait while a console trailer clip is made.",{when:()=>!!api.previewYt&&gp("ui.previews")!=="off"&&!!(window.bsPlatform&&bsPlatform.os==="android"),keys:"video preview youtube trailer online"}),
    {t:"dyn",fn:()=>gp("ui.previews")!=="off"&&api.previewCount?pvRows():[]}
  ]},
  // the museum's reading corner (1.68): its own page, so a deep link lands on its rows instead of below Art Studio's list
  {g:"Library",id:"reading",name:"Magazines & guides",ic:"reading",lead:"The museum's reading corner: where your magazines and strategy guides live, and finding more on the Internet Archive.",rows:[
    H("Find more"),
    A("Find on the Internet Archive","Browse","Search and download classic magazines and strategy guides. Only scans anyone may download are shown.",()=>legacy("reading"),{keys:"nintendo power game informer magazines guides archive.org download"}),
    H("Folders"),
    F("Magazines & guides folder","ui.readingLibrary","folder","Magazines\\ and Guides\\ are kept in this folder. Changing it doesn't move anything already downloaded: copy the two folders across yourself.",{ph:"Beside Boxshelf",d:"",want:"dir",pickTitle:"Choose a folder for your magazines and guides",keys:"reading corner folder path location",sub:()=>({text:readFolder("Magazines").replace(/[\\/]Magazines$/,"")})}),
    A("Magazines folder","Open","PDF, CBZ / CBR, or a folder of page images per issue. Sub-folders become series (Magazines\\Nintendo Power\\Issue 12.pdf).",()=>api.openLibraryFolder&&api.openLibraryFolder("magazine"),{keys:"reading corner",sub:()=>({text:readFolder("Magazines")})}),
    A("Guides folder","Open","Strategy guides for the reading corner, in the same formats.",()=>api.openLibraryFolder&&api.openLibraryFolder("guide"),{keys:"reading corner strategy",sub:()=>({text:readFolder("Guides")})}),
    {t:"dyn",fn:readingOldRows}, // last: rows that turn up later must not move the ones a deep link counted
  ]},
  {g:"Library",id:"acct",name:"Accounts & services",ic:"acct",dot:acctDot,lead:"Sign-ins and keys that add extra art and features. Keys are stored encrypted on this PC.",rows:[
    H("RetroAchievements"),
    svcRow("ra","Achievements on each game's details, and a summary after you play. Opens your account, its key, and the sign-in inside each emulator.","retroachievements achievements trophies account"),
    H("Art sources"),
    svcRow("fanart","Official disc art and logos for the museum's movie shelves.","api key movies fanart"),
    svcRow("steamgriddb","Community-made posters, offered in Art Studio next to the real box scans.","api key steamgriddb steam grid posters art studio"),
    svcRow("screenscraper","Box fronts, backs, full wraps (for spines) and disc / cartridge art by region, offered in Art Studio.","screenscraper screen scraper account art studio box back spine disc"),
  ]},
  {g:"System",id:"phone",name:"Phone companion",ic:"phone",dot:()=>phone&&phone.on?"ok":phone&&phone.enabled&&phone.error?"warn":null,lead:"Browse your games on your phone, start one on the TV, read its manual, and use the phone as a remote and keyboard. The phone has to be on the same Wi-Fi as this PC.",rows:[
    {t:"toggle",label:"Phone companion",info:"Runs a small web page on your home network for paired phones. Only phones that scanned the code (or typed the PIN) can use it; nothing is reachable from the internet. Windows may ask the first time whether to allow Boxshelf on your network: choose Private networks.",get:()=>!!(phone&&phone.enabled),set:setPhone,nosave:true,keys:"phone mobile remote companion qr pair wifi app",when:()=>!!api.companionSet},
    H("",{pair:true,when:()=>!!(phone&&phone.enabled)}),
    A("New PIN","New PIN","Makes a new PIN and code. Phones already paired stay paired.",newPhonePin,{when:()=>!!(phone&&phone.on),keys:"phone pin code"}),
    {t:"choice",label:"Port",opts:[8730,8731,8732,8733,8734,8080,8888,9000,9730].map(p=>[p,String(p)]),d:8730,get:()=>phone&&phone.wantPort||8730,set:setPhonePort,nosave:true,info:"The network port the phone page uses. Change it only if another program already uses this one (Boxshelf moves to the next free one by itself and says so above).",when:()=>!!(api.companionSet&&phone&&phone.enabled),keys:"phone port network"},
    H("Paired phones",{em:()=>{const n=phone&&phone.phones?phone.phones.length:0;return n?`${n} phone${n===1?"":"s"}`:""},when:()=>!!api.companionSet}),
    {t:"dyn",fn:phoneRows},
    A("Forget all phones",()=>asking("phones")?"Press again":"Forget all","Unpairs every phone. Each one has to scan the code again to use Boxshelf.",()=>twice("phones",()=>forgetPhone("all"),"Press again to forget every paired phone"),{when:()=>!!(phone&&phone.phones&&phone.phones.length),warnBtn:()=>asking("phones"),keys:"phone unpair forget"}),
  ]},
  {g:"System",id:"sys",name:"System",ic:"sys",lead:"Startup, folders and housekeeping.",rows:[
    {t:"dyn",fn:deckRows}, // (beta.8) Linux / Steam Deck: Add Boxshelf to Steam
    {t:"toggle",label:"Start Boxshelf with Windows",info:"Boots straight into the shelf when you sign in. Pair it with Start in fullscreen for a console-style setup.",get:()=>!!(autostart&&autostart.enabled),set:setAutostart,nosave:true,keys:"autostart boot login startup",when:()=>!!api.setAutostart},
    A("Use Boxshelf as the Home screen",()=>homeApp&&homeApp.isDefault?"On · change":"Off · set up","Makes the Home button open Boxshelf, like a console. Android asks you to pick the Home app on its own screen; pick Boxshelf there (or your old launcher to undo it).",async()=>{try{await api.openHomeSettings()}catch(e){toast(errMsg(e))}},{when:()=>!!api.openHomeSettings,keys:"home launcher default cocoon"}),
    A("Exit to the device's home screen",()=>homeApp&&homeApp.previousLabel?"Open "+homeApp.previousLabel:"Exit","Opens the launcher you used before Boxshelf. Boxshelf stays ready in the background.",async()=>{try{await api.power("exitHome")}catch(e){toast(errMsg(e))}},{when:()=>!!api.openHomeSettings,keys:"exit launcher home cocoon stock"}),
    A("Rescan games","Rescan","Looks for new games in every folder now.",async()=>{await save();needRescan=false;toast("Rescanning…");try{await shelf("refresh",true);toast("Library rescanned")}catch(e){toast(errMsg(e))}},{keys:"refresh library scan"}),
    H("Backup & help"),
    A("Back up settings","Save","Saves your settings, favourites and play history to a file you can restore later or on a new PC. Keys and passwords aren't included.",backup,{when:()=>!!api.backupSettings,keys:"backup export"}),
    A("Restore settings","Open","Loads a backup file. Your current keys and passwords are kept.",restoreBackup,{when:()=>!!api.restoreSettings,keys:"backup import restore"}),
    A("Smoothness test","Run","Scrolls the shelf by itself for about 45 seconds, switching effects off one at a time, and writes how smooth each was to the log. Leave the controller alone while it runs; Esc stops it.",async()=>{await close();setTimeout(()=>{if(window.Shelf&&Shelf.smoothTest)Shelf.smoothTest()},900)},{when:()=>!!(window.Shelf&&Shelf.smoothTest),keys:"performance fps frame rate lag stutter smooth benchmark"}),
    A("Report a problem","Create","Bundles the log and settings (keys removed) into one file on your Desktop, ready to send.",report,{when:()=>!!api.reportProblem,keys:"bug log help support"}),
    A("Report a bug online","Open","Opens the beta's bug form on GitHub in your browser. Attach the file Report a problem makes, and say what you were doing.",reportOnline,{when:()=>!!api.updateCheck,keys:"bug issue github feedback beta tester"}),
    A("About",()=>"Boxshelf "+(info.version||""),"Boxshelf "+(info.version||"")+". A console-style front end for your emulators, Steam and media apps.",()=>{infoOpen=!infoOpen;renderInfo()},{keys:"version",keepInfo:true,more:"Box art: LaunchBox Games Database, libretro thumbnails, GameTDB, GamesDatabase.org. Manuals, magazines and commercials: the Internet Archive. Achievements: RetroAchievements."}),
    M("What's new","whatsnew","Each version's changes: the newest one out, and the one you have.","What changed in each version of Boxshelf. Select an item to read more about it.",[{t:"dyn",fn:wnRows}],{keys:"what's new whats new release notes patch notes changelog changes version update",sub:()=>{const e=wnFor(info.version);return {text:upd&&upd.newer?`${upd.latest} is out`:e?e.title:"Release notes"}}}),
    A("Beta updates",updBtn,"Looks on the beta page for a newer Boxshelf. When there is one, this opens its download page: unzip it over this folder and your settings are kept.",checkUpdates,{when:()=>!!api.updateCheck,keys:"update new version beta download upgrade"}),
    T("Check for new betas","ui.updateCheck",true,"Once each time Boxshelf starts, it asks the beta page on GitHub whether a newer beta is out, and says so on the shelf. Nothing downloads by itself.",{when:()=>!!api.updateCheck,keys:"update check online automatic"}),
    H("More"),
    M("Advanced","sys-more","Run setup again, the app folder, clearing the play history and restoring emulator settings.","Setup, folders and resets you rarely need.",[
      A("Run setup again","Start","Walks through the first-time setup.",()=>legacy("welcome",{wizard:true}),{keys:"wizard first"}),
      A("App folder","Open","Settings, covers and the log that records launches, downloads and errors.",()=>api.openAppFolder&&api.openAppFolder(),{keys:"logs data"}),
      A("Clear play history",()=>asking("hist")?"Press again":"Clear","Forgets last played and play time for every game (Recently played and Most played start over).",()=>twice("hist",clearHistory,"Press again to clear the play history"),{when:()=>!!api.clearHistory,warnBtn:()=>asking("hist"),keys:"privacy history reset recently played"}),
      A("Restore emulator settings","Restore","Puts back Eden's, PCSX2's and RPCS3's own settings from before Boxshelf first changed them.",async()=>{try{const r=await api.restoreEmuSettings();toast(r&&r.length?`Restored ${r.length} emulator settings file${r.length>1?"s":""}`:"Nothing to restore")}catch(e){toast(errMsg(e))}},{when:()=>!!api.restoreEmuSettings,keys:"backup eden pcsx2 rpcs3"}),
    ]),
    A("Quit Boxshelf","Quit","Closes Boxshelf completely. Sleep, Restart and Shut down are in the Home menu.",async()=>{await save();api.quit&&api.quit()},{keys:"exit close"}),
  ]},
  ];
  if(!api.previewFind)SECTIONS=SECTIONS.filter(s=>s.id!=="previews"); // (beta.9: on Android too; its Settings puts them under Shelf & museum, android-settings.js)
if(window.bsPlatform&&bsPlatform.filterSections)SECTIONS=bsPlatform.filterSections(SECTIONS,{bind:bindb,M,H,T,A}); // Android: drop what this platform can't do (renderer/platform.js); M…A: the row makers, for a platform's own rows and sub-pages
}
/* Accounts & services: each service's own page (sub {kind:"svc",id}) */
function svcRow(id,info,keys){return {t:"action",label:SVC_N[id],status:id,open:true,info,keys,run:()=>openSub({kind:"svc",id}),...(id==="ra"?{sub:()=>{const t=raSummary();return t?{text:"Emulators: "+t}:null}}:{})}}
// built once per service: the text editor finds its row by identity (edit.row===r), so the rows must stay the same objects
const svcMemo={};function svcPage(id){return expand(svcMemo[id]||(svcMemo[id]=svcPage0(id)))}
function svcPage0(id){const n=SVC_N[id]||id,P=(lead,rows)=>({g:"Library",id:"acct",name:n,lead,rows});
  const test=(label,info)=>A(label,()=>svcBusy[id]?"Checking…":"Test",info,()=>testSvc(id),{status:id,keys:id});
  if(id==="ra")return P("Your RetroAchievements account, and the sign-in inside each emulator (achievements only unlock where the emulator is signed in too).",[
    T("Show achievements","ra.enabled",true,"Progress on each game's details, and a summary after you play.",{keys:"retroachievements trophies",ra:true}),
    X("Username","ra.user","Your RetroAchievements username.",{ph:"Not set",ra:true,keys:"retroachievements account"}),
    X("Web API key","ra.key","On retroachievements.org → Settings → Keys. It only reads your public profile and progress. Stored encrypted.",{secret:true,ph:"Not set",ra:true,keys:"retroachievements"}),
    test("Test connection","Checks the account and shows your points."),
    A("Get your key","Open site","Opens your RetroAchievements settings page in the browser (Keys → Web API Key).",()=>openLink("https://retroachievements.org/settings"),{keys:"retroachievements api key"}),
    H("Signed in to your emulators",{em:raSummary,when:()=>!!api.raEmulators}),
    {t:"dyn",fn:emuRows},
    A("Check again",()=>raEmuBusy?"Checking…":"Check","Looks at each emulator's achievements sign-in again.",()=>loadRaEmu(true),{when:()=>!!api.raEmulators,keys:"retroachievements emulator sign in"}),
  ]);
  if(id==="fanart")return P("Official disc art and logos for the movies on the museum's movie shelves. Without a key, discs are printed from each title's own logo and art.",[
    X("Key","keys.fanart","Your personal fanart.tv API key. Stored encrypted.",{secret:true,ph:"Not set",fan:true,keys:"api key movies fanart"}),
    test("Test the key","Checks the key."),
    A("Get a key","Open site","Opens fanart.tv's page for a free personal key.",()=>openLink("https://fanart.tv/get-an-api-key/"),{keys:"fanart"})]);
  if(id==="steamgriddb")return P("Community-made posters for your games, offered in Art Studio next to the real box scans (Pick exact on the Front; the HD shelf style may use them). Optional.",[
    X("Key","keys.steamgriddb","Your SteamGridDB API key. Stored encrypted.",{secret:true,ph:"Not set",fan:true,keys:"api key steamgriddb steam grid posters"}),
    test("Test the key","Checks the key."),
    A("Get a free key","Open site","Sign in at steamgriddb.com (a free Steam sign-in), then Preferences → API → copy the key and paste it above.",()=>openLink("https://www.steamgriddb.com/profile/preferences/api"),{keys:"steamgriddb"})]);
  return P("Box fronts, backs, full wraps (Boxshelf cuts the spine from them) and disc or cartridge art, by region, in Art Studio. Optional, and needs a free ScreenScraper account. ScreenScraper only answers apps registered with it: until Boxshelf's registration is done, the test says \"Not available yet\" and no account can sign in (unless you have your own developer login, below). Box art still comes from libretro, LaunchBox and SteamGridDB meanwhile.",[
    X("Username","keys.ssUser","Your screenscraper.fr username. Stored encrypted.",{ph:"Not set",fan:true,keys:"screenscraper account user login"}),
    X("Password","keys.ssPass","Your screenscraper.fr password. Stored encrypted, sent only to ScreenScraper.",{secret:true,ph:"Not set",fan:true,keys:"screenscraper password"}),
    X("Developer ID","keys.ssDevId","Only if ScreenScraper gave you a developer login of your own (devid). Boxshelf uses its own registration otherwise, once it has one.",{ph:"Only if you have one",fan:true,keys:"screenscraper devid developer"}),
    X("Developer password","keys.ssDevPass","The developer password (devpassword) that goes with your developer ID. Stored encrypted.",{secret:true,ph:"Only if you have one",fan:true,keys:"screenscraper devpassword developer"}),
    test("Test the account","Signs in and shows how many lookups the account has left today."),
    A("Get a free account","Open site","Opens ScreenScraper's sign-up page. A free account allows a set number of lookups a day; a donation raises it.",()=>openLink("https://www.screenscraper.fr/membreinscription.php"),{keys:"screenscraper sign up register"}),
    A("Ask for developer access","Open site","Opens ScreenScraper's forum, where developers ask for a developer ID and password. Only for using a developer login of your own.",()=>openLink("https://www.screenscraper.fr/forumsujets.php?frub=12&numpage=0"),{keys:"screenscraper devid developer"})])}

/* Phone companion (main/companion.js): on / off, the pairing card (QR code, address, PIN), paired phones */
let phone=null,phonePortT=0;
if(api.onCompanionState)api.onCompanionState(s=>{if(s&&typeof s==="object"){phone=s;if(isOpen)bump()}});
async function setPhone(v){if(!api.companionSet)return;phone=Object.assign({},phone||{},{enabled:!!v,starting:!!v,error:""});bump();
  try{phone=(await api.companionSet({enabled:!!v}))||phone;toast(!v?"Phone companion is off":phone.on?"Phone companion is on: scan the code with your phone":phone.error||"Couldn't start the phone companion")}
  // failed: read main's real state back, or the pairing card would say "Starting…" for ever
  catch(e){toast(errMsg(e));phone=Object.assign({},phone,{starting:false,error:errMsg(e)});if(api.companionState)try{const s=await api.companionState();if(s&&typeof s==="object")phone=s}catch{}}bump()}
// each step of the Port choice would restart the server: only the one you stop on counts
function setPhonePort(v){phone=Object.assign({},phone||{},{wantPort:+v});clearTimeout(phonePortT);phonePortT=setTimeout(async()=>{try{phone=await api.companionSet({port:+v});if(phone.on)toast(`Phone page now at ${phone.url}`);else if(phone.error)toast(phone.error)}catch(e){toast(errMsg(e))}bump()},900)}
async function newPhonePin(){try{phone=await api.companionPin();toast("New PIN made")}catch(e){toast(errMsg(e))}bump()}
async function forgetPhone(id){try{phone=await api.companionForget(id);toast(id==="all"?"Every phone forgotten":"Phone forgotten")}catch(e){toast(errMsg(e))}bump()}
function phoneRows(){if(!api.companionSet)return [];const L=phone&&phone.phones||[];
  if(!L.length)return [{t:"action",label:"No phones paired yet",btn:"—",info:"Switch the phone companion on and scan the code with your phone's camera.",run:()=>{},ns:true,dim:true}];
  return L.map(p=>({t:"action",label:p.name||"Phone",btn:()=>asking("ph"+p.id)?"Press again":"Forget",warnBtn:()=>asking("ph"+p.id),ns:true,keys:"phone paired",
    info:"Forget unpairs this phone: it has to scan the code again to use Boxshelf.",sub:()=>({text:`Paired ${when(p.added)}${p.seen?` · last used ${when(p.seen)}`:""}`}),
    run:()=>twice("ph"+p.id,()=>forgetPhone(p.id),`Press again to forget ${p.name||"this phone"}`)}))}
function pairHtml(){const p=phone;
  if(!p||(!p.on&&!p.error))return `<div class="bss-pairmsg">Starting…</div>`;
  if(!p.on)return `<div class="bss-pairmsg bad">${esc(p.error)}</div>`;
  return `<div class="bss-pair"><div class="qr">${p.qr||""}</div><div class="pt"><div class="lbl">Scan with your phone's camera, or open</div><div class="url">${esc(p.url)}</div><div class="lbl">and enter the PIN</div><div class="pin">${esc(String(p.pin).slice(0,3))} ${esc(String(p.pin).slice(3))}</div>`+
    (p.locked?`<div class="warn">Pairing is locked for ${p.locked} min after 5 wrong PINs</div>`:"")+(p.note?`<div class="note">${esc(p.note)}</div>`:"")+
    `<div class="note">The first time, Windows may ask whether to allow Boxshelf on your network: choose Private networks, then Allow.</div></div></div>`}
/* RetroAchievements in each emulator: signed in, as whom, switched on */
let raEmu=null,raEmuBusy=false,emuAsk=null;
const RA_SYS={gc:"GameCube",wii:"Wii",psp:"PSP",ps2:"PS2"};
const RA_STEPS={Dolphin:"1. Open Dolphin.\n2. Config → Achievements.\n3. Log in with your RetroAchievements username and password, then tick Enable.\n4. Close Dolphin. Boxshelf checks again when you come back.",
  PPSSPP:"1. Open PPSSPP.\n2. Settings → Tools → RetroAchievements.\n3. Log in with your RetroAchievements account.\n4. Close PPSSPP. Boxshelf checks again when you come back.",
  PCSX2:"1. Open PCSX2.\n2. Settings → Achievements.\n3. Tick Enable Achievements, then press Login.\n4. Close PCSX2. Boxshelf checks again when you come back."};
function loadRaEmu(manual){if(!api.raEmulators||raEmuBusy)return;raEmuBusy=true;if(isOpen)bump();
  Promise.resolve(api.raEmulators()).then(l=>{raEmu=Array.isArray(l)?l:[];if(manual)toast(raSummary()||"Checked")}).catch(e=>{raEmu=raEmu||[];if(manual)toast(errMsg(e))}).finally(()=>{raEmuBusy=false;if(isOpen)bump()})}
const emuOf=sys=>(raEmu||[]).find(x=>x.system===sys);
const conName=sys=>(consoleList().find(c=>c.id===sys)||{}).name||RA_SYS[sys]||sys;
function emuText(e){const em=e.emulator||"the emulator";switch(e.state){case "ok":return ["ok",`Signed in as ${e.user||gp("ra.user")||"you"}`];case "other-user":return ["warn","Signed in as someone else"];
  case "off":return ["warn",`Turned off in ${em}`];case "signed-out":return ["warn","Not signed in yet"];case "no-emulator":return ["none","No emulator set"];case "unsupported":return ["none","No achievement sets yet"];default:return ["none","Open the emulator once first"]}}
function emuChip(sys){const e=emuOf(sys);if(!e)return stHtml(raEmuBusy?"busy":"none",raEmuBusy?"Checking…":"Not checked yet");const [c,t]=emuText(e);return stHtml(c,t)}
function raSummary(){const L=(raEmu||[]).filter(e=>e.state!=="unsupported");if(!L.length)return raEmuBusy&&!raEmu?"Checking…":"";return `${L.filter(e=>e.state==="ok").length} of ${L.length} consoles ready`}
function emuSteps(sys){const e=emuOf(sys)||{},em=e.emulator||"the emulator",me=gp("ra.user");
  if(e.state==="unsupported")return `There are no RetroAchievements sets for ${conName(sys)} yet.`;
  if(e.state==="no-emulator")return `Set ${conName(sys)}'s emulator first: Consoles → ${conName(sys)} → Emulator.`;
  if(e.state==="ok")return `All set: achievements you earn in ${em} show up on each game's details.`;
  const pre=e.state==="other-user"?`${em} is signed in as ${e.user||"another account"}${me?`, but Boxshelf uses ${me}. Log out there first, then log in as ${me}.`:"."}\n\n`:e.state==="unknown"||!e.state?`${em} hasn't saved its settings yet: open it once, then come back.\n\n`:"";
  return pre+(RA_STEPS[e.emulator]||`Sign in to RetroAchievements in ${em}'s settings.`)+(e.detail?`\n\n${e.detail}`:"")}
function emuRows(){if(!api.raEmulators)return [];const list=raEmu&&raEmu.length?raEmu:Object.keys(RA_SYS).map(system=>({system}));
  return list.map(e=>{const sys=e.system,emu=e.emulator||(consoleList().find(c=>c.id===sys)||{}).emulatorName||"";
    return {t:"action",label:emu?`${conName(sys)} · ${emu}`:conName(sys),emu:sys,ns:true,keepInfo:true,keys:"retroachievements achievements emulator sign in",
      get info(){const x=emuOf(sys);return !x?"Checking this emulator's achievements sign-in.":x.state==="ok"?"Achievements unlock in this emulator and show up in Boxshelf.":x.state==="unsupported"?"This console has no achievement sets yet.":"Achievements won't unlock here until this is fixed."},
      more:()=>emuSteps(sys),run:()=>emuAct(sys)}})}
async function emuAct(sys){const e=emuOf(sys);if(!e||e.state==="unsupported"){infoOpen=true;renderInfo();return}
  if(e.state==="no-emulator"){openConsole(sys);return}
  if(emuAsk===sys&&api.launchEmulator){emuAsk=null;await save();toast(`Opening ${e.emulator||"the emulator"}…`);try{await api.launchEmulator(sys)}catch(err){toast(errMsg(err))}render();return}
  emuAsk=sys;infoOpen=true;play("open");render()}
function hiddenRows(){if(!(window.Shelf&&Shelf.hiddenList))return [];const L=hidden||[];
  if(!L.length)return [{t:"action",label:"No hidden games",btn:"—",info:"Hide a game from its Details panel (Hide). It shows up here so you can bring it back.",run:()=>{},ns:true,dim:true}];
  const names=Object.fromEntries(consoleList().map(c=>[c.id,c.name]));
  return L.map(g=>({t:"action",label:g.title||g.id,btn:"Unhide",info:"Hidden from every shelf, the museum, shuffle and the idle show. Unhide puts it back.",sub:()=>({text:names[g.system]||g.system||""}),ns:true,keys:"hidden hide",
    run:async()=>{await save();shelf("setHidden",g.id,false);if(Array.isArray(cfg.ui.hidden))cfg.ui.hidden=cfg.ui.hidden.filter(x=>x!==g.id);hidden=(hidden||[]).filter(x=>x.id!==g.id);toast(`${g.title||"Game"} is back on its shelf`);bump()}}))}

// one console's page
function conPages(id){
  const def=consoleList().find(c=>c.id===id)||{id,name:id,emulatorName:"Emulator",exeNames:["emulator.exe"]},c=sysCfg(id),p=`systems.${id}.`,emu=def.emulatorName||"Emulator",exe=(def.exeNames||["emulator.exe"])[0];
  // the shelf on / off first, with where it stands ("Ready · 13 games", "Needs Dolphin"); then the emulator and games
  // folders by name (the whole path is in the explanation on the right)
  const rows=[T("Show this shelf",p+"enabled",true,"Turn a console off without losing its settings.",{rescan:true,keys:"hide shelf console off",sub:()=>{const st=conState(def);return {text:conStatus(def),cls:st==="ok"?"ok":st==="warn"?"warn":""}}}),
    F("Emulator",p+"emulator","file",id==="sw"?"The Switch emulator this shelf uses: Eden, Citron or Ryujinx. One game can use a different one: Details → Emulator.":`The ${emu} program this shelf uses.`,{rescan:true,want:"file",short:v=>{const b=progName(v).toLowerCase();return ({citron:"Citron",ryujinx:"Ryujinx"})[b]||((def.exeNames||[]).some(x=>progName(x).toLowerCase()===b)?emu:progName(v))} /* the usual program: its proper name (PPSSPP, not PPSSPPWindows64); (beta.10) the Switch's other emulators by their own names */,filters:[{name:emu,extensions:["exe"]}],pickTitle:`Find ${exe}`,ph:`Point to ${exe}`,more:()=>c.emulator?c.emulator:"Found automatically when it's in your Boxshelf folder or a usual install place. Find emulators again (Consoles) looks once more."})];
  // Switch 2 (Windows): which Eglantine build Play starts. "Ask each time" (the default) shows the list at Play; a build
  // picked here becomes the shelf's emulator and Play starts in it straight away. A game's own Details → Emulator wins.
  // (beta.3) a page of its own: every build (the list Play shows), Ask each time, and builds or folders kept elsewhere
  if(id==="sw2"&&sw2Row())rows.splice(2,0,M("Builds","sw2-builds","Which Eglantine build starts your Switch 2 games: every build Boxshelf found (the same list Play shows), Ask each time, and builds kept in other folders.",
    "The same list Play shows. Ask each time shows it at Play; choose a build to always start in it. A game's own choice (Details → Emulator) still comes first.",[{t:"dyn",fn:sw2BuildRows}],
    {keys:"build builds fork forks eglantine lichii orchid selevet version branch which emulator choose",btn:()=>{const b=sw2Fixed();return b?b.label||progName(b.exe):"Ask each time"},
     sub:()=>(sw2Want(),{text:sw2b==null?"Looking for builds…":sw2b.length?`${sw2b.length} build${sw2b.length===1?"":"s"} found`:"No builds found yet · add one here",cls:sw2b&&!sw2b.length?"warn":""})}));
  const d=det&&det.systems&&det.systems[id];
  if(d&&d.emulator&&d.emulator!==c.emulator)rows.push(A("Use the emulator found","Use","Boxshelf found this copy. Use it for this shelf.",()=>{c.emulator=d.emulator;changed({rescan:true})},{sub:()=>({text:d.emulator}),ns:true}));
  const twin=id==="wii"?"gc":id==="gc"?"wii":null;
  if(twin&&!c.emulator&&sysCfg(twin).emulator)rows.push(A(`Use ${twin==="gc"?"GameCube":"Wii"}'s Dolphin`,"Use","The same Dolphin.exe runs both.",()=>{c.emulator=sysCfg(twin).emulator;changed({rescan:true})},{ns:true}));
  c.romDirs.forEach((dir,i)=>rows.push({t:"folder",label:c.romDirs.length>1?`Games folder ${i+1}`:"Games folder",info:"Where this console's games are. Subfolders are searched too. Select it twice to remove it from this shelf (nothing is deleted).",more:dir,short:folderName,get:()=>dir,ns:true,
    // (beta.10) the confirmation is keyed by the folder itself, not its place in the list (removing another one moved it onto this one)
    run:()=>twice("rd:"+id+":"+dir,()=>{const k=c.romDirs.indexOf(dir);if(k>=0)c.romDirs.splice(k,1);if(window.bsPlatform&&bsPlatform.os==="android")rdGone().add(dir);changed({rescan:true});toast("Folder removed from this shelf (nothing was deleted)")},"Press again to remove this folder"),ask:"rd:"+id+":"+dir,sub:()=>pathSub(dir,"dir")}));
  (d&&d.romDirs||[]).filter(x=>!c.romDirs.includes(x)&&!rdGone().has(x)).forEach(x=>rows.push(A("Add the folder found","Add","Boxshelf found games for this console here.",()=>{if(!c.romDirs.includes(x))c.romDirs.push(x);changed({rescan:true})},{sub:()=>({text:x}),ns:true})));
  // (beta.7) Set up for me: the emulator set up the way the shelf expects, on a page of its own
  if(esCan(c))rows.push(M(`Set up ${emu} for me`,"emusetup-"+id,`Controller, the Home menu's hotkeys, one BIOS folder and a graphics preset for ${emu}: backed up first, and Undo puts it back.`,`Boxshelf sets ${emu} up the way the shelf works best. Each switch is one kind of change; your files are backed up first, and Undo puts them back.`,[{t:"dyn",fn:()=>emuSetupRows(id)}],{keys:"setup set up configure configuration automatic easy controller pad hotkeys bios graphics resolution preset",sub:()=>(esGet(id),esSub(id)),ns:true}));
  rows.push(A(c.romDirs.length?"Add another games folder":"Add a games folder","Add",`Another folder to look in for this console's games${def.hint?` (${def.hint})`:def.extensions?` (${def.extensions.slice(0,4).map(e=>"."+e).join(", ")}…)`:""}.` /* def.hint: a console whose games aren't plain files (PS3 / PS4 game folders) says so in systems.js */,async()=>{const r=await pick({kind:"folder",title:`Choose a ${def.name} games folder`});if(r)rdGone().delete(r);if(r&&!c.romDirs.includes(r)){c.romDirs.push(r);changed({rescan:true})}},{keys:"roms games folder"}),
    H("When you press Play"),
    // what the two switches do (app.js play()): your boot video, when this console has one, plays first; otherwise the boot
    // screen (the console's look with the game's cover and a progress line) covers the emulator starting. With the boot
    // screen off and no video, the built-in intro plays instead.
    T("Boot screen with the game's cover",p+"insertAnim",true,"The console's start-up look with the game's cover and a progress line while the emulator opens.",{keys:"insert animation disc cartridge launch"}),
    T("Your boot video before games",p+"bootOnLaunch",true,"Plays the boot video you added for this console (Customize) before the game, instead of the boot screen. Without a video, the built-in intro plays only when the boot screen is off.",{keys:"boot animation intro launch"}));
  // everyday rows above; the emulator's own tools, launch options and this console's startup oddities on one sub-page
  // (Emulator & advanced), so the page fits a TV screen. Built here so both pages share this call's row objects.
  const adv=[H(emu),
    A("Open the emulator on its own","Open","Starts the emulator without a game so you can change its graphics or controls.",async()=>{await save();try{await api.launchEmulator(id)}catch(e){toast(errMsg(e))}},{when:()=>!!c.emulator&&!!api.launchEmulator,ns:true}),
    A("Get "+emu,"Open site",`Opens ${emu}'s official download page.`,()=>openLink(OFFICIAL[id]),{when:()=>!!OFFICIAL[id],ns:true}),
    X("Emulator options",p+"emulatorArgs","What Boxshelf passes when it opens the emulator on its own.",{ph:"(none)",d:def.emulatorArgs||"",mono:true})];
  // the real GameCube startup as a short checklist: the switch says whether your IPL.bin is there; "Add your IPL.bin"
  // shows only while it isn't; its two fine-tunings only while the startup is on
  const iplOk=()=>!!(ipl&&ipl.found&&ipl.found.length);
  if(id==="gc"&&iplCap())adv.push(H("GameCube startup"),
    T("Play the real GameCube startup",p+"useIpl",false,"Boots through the console's own startup animation. Needs your GameCube's system file (IPL.bin) in Dolphin's GC\\USA folder (EUR or JAP for those regions). When it's on, your boot video is skipped.",{keys:"ipl bios",sub:()=>ipl?iplOk()?{text:"✓ Your IPL.bin is there ("+ipl.found.map(x=>x.region).join(", ")+")",cls:"ok"}:{text:"Needs your IPL.bin",cls:"warn"}:null}),
    A("Add your IPL.bin","Open folder","Opens Dolphin's GC\\USA folder: put the IPL.bin dumped from your GameCube there (EUR or JAP folders for those regions).",async()=>{await save();try{await api.openIplFolder("gc","USA")}catch(e){toast(errMsg(e))}},{when:()=>!!api.openIplFolder&&!iplOk(),ns:true}),
    T("Skip Dolphin's warning pop-ups",p+"hideDolphinWarnings",false,"Hides Dolphin's warnings (like an unrecognised IPL.bin) that would stop the game from starting.",{when:()=>!!c.useIpl}),
    S("Startup animation time",p+"iplCoverSeconds",2.5,0,15,.5,v=>v===0?"Off":v+" s","How long the loading screen stays over the real GameCube startup. Lower shows more of it but may flash Dolphin.",{when:()=>!!c.useIpl}));
  if(id==="ps5")adv.push(H("Kyty"),
    T("Start games through Kyty's launcher",p+"viaLauncher",false,"Off: Play starts the game straight away, with the settings you chose in Kyty's launcher (and the KytyJets preset). On: Play opens Kyty's launcher and you start the game there (it can't be told which game to open)."));
  adv.push(H("Launching"),
    S("Longest loading screen",p+"coverSeconds",def.coverSeconds!=null?def.coverSeconds:4,0,30,.5,v=>v===0?"Off":v+" s","The black loading screen hides the emulator's windows while it starts. It lifts as soon as the game is fullscreen; this is only the longest it waits. 0 turns it off."),
    X("Game launch options",p+"gameArgs","What Boxshelf passes to the emulator when you play. {rom} is the game file.",{d:def.gameArgs,mono:true,more:"Default: "+(def.gameArgs||"(none)")}),
    A("Restore default launch options","Restore","Puts the launch options back to Boxshelf's defaults.",()=>{c.gameArgs=def.gameArgs||"";c.emulatorArgs=def.emulatorArgs||"";changed(null);toast("Launch options back to the defaults")},{ns:true}),
    H("More"),
    F("Own art folder",p+"artDir","folder","A folder with this console's covers, if you keep them separately. Name images after the game file, like Pikmin (USA).png; add -back for the back cover.",{ph:"(uses the art library)",d:"",want:"dir",short:folderName,rescan:true,pickTitle:"Choose a cover art folder",when:()=>!(window.bsPlatform&&bsPlatform.os==="android")})); // Android: art lives in "Boxshelf Art" beside the games, there is no art library
  rows.push(H("More"),
    A("Box art (Art Studio)","Open",`Jumps to ${def.name}'s games in the Art Studio: its shelf style, and every version of any game's art (manuals are a row away).`,()=>{route("artstudio",{systemId:id});render()},{ns:true}),
    A("Customize (3D, logo, boot video)","Open","Its logo, 3D console, controller and disc models and boot video (the Customize screen).",()=>customize(id),{ns:true}),
    {t:"action",label:"Emulator & advanced",btn:"",open:true,ns:true,info:`Open ${emu} on its own, launch options, the longest loading screen${id==="gc"&&iplCap()?" and the real GameCube startup":""}.`,run:()=>openSub({kind:"conadv",id})});
  return {main:{g:"Games",id:"console",sys:id,name:def.name,ic:"games",lead:HELP[id]||`${emu} runs ${def.name} games.`,rows},
    adv:{g:"Games",id:"console",sys:id,name:"Emulator & advanced",lead:`${emu}'s own tools, how Boxshelf starts it, and the rarely-needed settings for ${def.name}.`,rows:adv}};
}
const consolePage=id=>conPages(id).main,conAdvPage=id=>conPages(id).adv;
// the console order: A picks a shelf up, up / down moves it, A (or B) puts it down
function orderList(){const known=[...consoleList().filter(c=>sysCfg(c.id).enabled!==false).map(c=>[c.id,c.name]),...(cfg.steam&&cfg.steam.enabled!==false?[["pc","PC"]]:[]),["media","Media"]];
  const saved=(cfg.ui.shelfOrder||[]).filter(id=>known.some(k=>k[0]===id));return {known,order:[...saved,...known.map(k=>k[0]).filter(id=>!saved.includes(id))]}}
function orderPage(){const {known,order}=orderList(),nm=id=>(known.find(k=>k[0]===id)||[id,id])[1];
  return {g:"Boxshelf",id:"order",name:"Console order",ic:"shelf",lead:"The order of the shelves along the top. Home always comes first. Pick one up, move it, put it down.",
    rows:[...order.map((id,i)=>({t:"order",label:nm(id),id,n:i+1,info:"Pick it up, then move it up or down the list.",ns:true})),H(""),A("Reset to default order",()=>asking("ordreset")?"Press again":"Reset","Puts the shelves back in Boxshelf's own order.",()=>twice("ordreset",()=>{delete cfg.ui.shelfOrder;changed({rescan:true});toast("Default order")},"Press again to reset the shelf order"),{ns:true,warnBtn:()=>asking("ordreset"),ask:"ordreset"})]}} // (beta.10) pressed twice, like the other resets
function moveOrder(d){const {order}=orderList(),i=rowI,j=i+d;if(!grab||j<0||j>=order.length)return;[order[i],order[j]]=[order[j],order[i]];cfg.ui.shelfOrder=order.slice();rowI=j;changed({rescan:true})}

/* ---------- Get box art / Get manuals (1.75): Settings pages, replacing the classic panel's grids ----------
   Each console is a switch (in this download or not) with what it has; the parts to get are four switches for every
   ticked console; Start shows each console's progress as rows, then what was found. The plan is ui.artPlan, as before. */
const AG={cov:null,loading:false,busy:false,prog:{},stage:"",rep:null,err:null,started:0,ids:[],parts:null};
const MG={cov:null,loading:false,busy:false,prog:{},stage:"",phase:"",rep:null,err:null,started:0,ids:[],pick:null};
// (beta.9) Get manuals → Check my manuals: every manual you have looked at again (main manuals:check), progress and a result
const MC={busy:false,stage:"",done:0,total:0,checked:0,moved:0,started:0,rep:null,err:null,stopping:false};let mcTick=0,mcHooked=false;
const PARTS4=[["front","Covers"],["back","Backs"],["spine","Spines"],["disc","Discs & carts"]];
const took=t=>{const s=Math.max(0,Math.round((Date.now()-t)/1000));return s<60?s+"s":Math.floor(s/60)+"m "+String(s%60).padStart(2,"0")+"s"};
const infoRow=(label,btn,sub,x={})=>({t:"action",label,btn:btn||"",ns:true,keepInfo:true,run:()=>{},sub:()=>sub,...x}); // a line of information (selectable, so a controller can scroll through)
let dlHooked=false,dlTick=0;
function dlHook(){if(dlHooked||!api.onCoversProgress)return;dlHooked=true;
  api.onCoversProgress(p=>{p=p||{};const S=AG.busy?AG:MG.busy?MG:null;if(!S)return;if(p.systemId)S.prog[p.systemId]=p;if(p.stage)S.stage=p.stage;if(isOpen)bump()})}
function dlClock(){clearTimeout(dlTick);if(!(AG.busy||MG.busy))return;dlTick=setTimeout(()=>{if(isOpen)bump();dlClock()},1000)} // the elapsed time ticks
function agPlan(){const pl=gp("ui.artPlan");if(pl&&typeof pl==="object")return {...pl};const o={};consoleList().forEach(c=>{o[c.id]=PARTS4.map(x=>x[0])});return o}
// the parts every ticked console gets (none ticked: the last ones chosen here, else all four)
function agParts(sys,pl){const on=sys.filter(c=>(pl[c.id]||[]).length);if(!on.length)return AG.parts?[...AG.parts]:PARTS4.map(x=>x[0]);return PARTS4.map(x=>x[0]).filter(k=>on.every(c=>(pl[c.id]||[]).includes(k)))}
function agLoad(){if(AG.loading||!api.artCoverage)return;AG.loading=true;api.artCoverage().then(c=>{AG.cov=Array.isArray(c)?c:[]}).catch(()=>{AG.cov=[]}).finally(()=>{AG.loading=false;if(isOpen)bump()})}
function artGetRows(){dlHook();
  if(AG.busy){const L=[H("Getting box art",{em:()=>took(AG.started)})];
    if(!Object.keys(AG.prog).length)L.push(infoRow(AG.stage||"Starting…",""));
    AG.ids.forEach(id=>{const c=consoleList().find(x=>x.id===id)||{name:id},p=AG.prog[id],tot=p&&p.total||0,dn=p&&p.done||0,fin=!!tot&&dn>=tot;
      L.push(infoRow(c.name,p&&tot?`${dn} of ${tot}`:"",{text:p?(fin?"Finished":p.game?"Now: "+p.game:p.stage||""):"Waiting",cls:fin?"ok":""}))});
    L.push(A("Cancel","Cancel","Stops after the games in progress. Pictures already downloaded are kept.",()=>{if(api.cancelCovers)api.cancelCovers();AG.stage="Stopping after the games in progress…";toast("Stopping after the games in progress")},{ns:true}));return L}
  if(AG.rep||AG.err){const L=[];
    if(AG.err)L.push(H("Box art didn't finish"),infoRow(AG.err,""));
    else{const r=AG.rep,t=r.systems.reduce((a,x)=>({got:a.got+(x.front||0)+(x.back||0)+(x.spine||0)+(x.disc||0),up:a.up+(x.upgraded||0),miss:a.miss+(x.missed||[]).length}),{got:0,up:0,miss:0});
      L.push(H(r.cancelled?"Download cancelled":"All done",{em:()=>"took "+took(AG.started)}),
        infoRow(`${t.got} new picture${t.got===1?"":"s"}`+(t.up?`, ${t.up} sharper`:""),"",{text:t.miss?`${t.miss} game${t.miss===1?"":"s"} still missing something`:"Everything you picked was found",cls:t.miss?"warn":"ok"},{info:"New art shows on the shelf when you close Settings."}));
      r.systems.forEach(x=>{const parts=[["front","covers"],["back","backs"],["spine","spines"],["disc","discs"]].filter(([k])=>x[k]).map(([k,l])=>`${x[k]} ${l}`),miss=(x.missed||[]).length,kinds=Object.entries(x.missedKinds||{}).map(([k,n])=>`${n} ${k}${n>1?"s":""}`).join(", ");
        // (1.80) a console with games still missing something opens them in Art Studio (Missing a part), instead of a dead end
        const fix=miss&&api.artStudio?{run:()=>{AG.rep=null;route("artstudio",{systemId:x.id});if(studio){studio.filter="missing";studio.zone="grid"}play("open");render()},btn:(parts.length?parts.join(", ")+" · ":"")+"Fix in Art Studio"}:{};
        L.push(infoRow(x.name||x.id,parts.length?parts.join(", "):"Nothing new",miss?{text:`${miss} not found${kinds?` (${kinds})`:""}`,cls:"warn"}:{text:x.upgraded?`${x.upgraded} made sharper`:"Complete",cls:"ok"},{info:miss?"Not found anywhere: "+(x.missed||[]).slice(0,12).join(", ")+(miss>12?"…":"")+(fix.btn?". Open them in Art Studio to choose another version or use a picture of your own.":""):"",...fix}))});
      if(r.launchboxError)L.push(infoRow("LaunchBox couldn't be reached","",{text:"Only libretro covers were tried this time",cls:"warn"}));}
    L.push(H(""),A("Back to the list","Back","Your consoles and what they still need.",()=>{AG.rep=null;AG.err=null;AG.cov=null;agLoad()},{ns:true}));return L}
  if(!AG.cov){agLoad();return [infoRow("Checking what you have…","")]}
  const sys=AG.cov.filter(c=>c.total>0);
  if(!sys.length)return [infoRow("No games yet","",{text:"Add a games folder for a console first (Consoles)"})];
  const hd=gp("ui.artHD")!==false,pl=agPlan(),parts=agParts(sys,pl),chosen=sys.filter(c=>(pl[c.id]||[]).length);
  const L=[H("Consoles",{em:()=>{const p=agPlan();return `${sys.filter(c=>(p[c.id]||[]).length).length} of ${sys.length} picked`}})];
  sys.forEach(c=>{const miss=c.total-c.front,lack=PARTS4.slice(1).some(([k])=>c[k]<c.total),low=hd?PARTS4.reduce((a,[k])=>a+(c[k+"Low"]||0),0):0;
    const st=(miss?`${c.front} of ${c.total} covers · ${miss} missing`:lack?`All ${c.total} covers · some backs, spines or discs missing`:`Complete · ${c.total} games`)+(low?` · ${low} low-res`:"");
    L.push({t:"toggle",label:c.name,ns:true,info:"On: this console is in the download. It gets the parts switched on below.",get:()=>(agPlan()[c.id]||[]).length>0,
      set:v=>{const p=agPlan();p[c.id]=v?agParts(sys,p):[];if(v&&!p[c.id].length)p[c.id]=PARTS4.map(x=>x[0]);cfg.ui.artPlan=p},sub:()=>({text:st,cls:!miss&&!lack?"ok":""})})});
  L.push(A("Select all","All","Every console in this download.",()=>{const p=agPlan(),use=agParts(sys,p);sys.forEach(c=>{p[c.id]=use.length?[...use]:PARTS4.map(x=>x[0])});cfg.ui.artPlan=p;scheduleSave()},{ns:true}),
    A("Select none","None","No console in this download.",()=>{const p=agPlan();sys.forEach(c=>{p[c.id]=[]});cfg.ui.artPlan=p;scheduleSave()},{ns:true}),
    H("What to get"));
  PARTS4.forEach(([k,l])=>L.push({t:"toggle",label:l,ns:true,info:k==="disc"?"Disc art, or the cartridge for Switch, Switch 2 and 3DS games.":`The ${l.toLowerCase()} of each case.`,get:()=>agParts(sys,agPlan()).includes(k),
    set:v=>{const p=agPlan(),cur=agParts(sys,p),next=PARTS4.map(x=>x[0]).filter(x=>x===k?v:cur.includes(x));AG.parts=next;sys.filter(c=>(p[c.id]||[]).length).forEach(c=>{const s=(p[c.id]||[]).filter(x=>x!==k);if(v)s.push(k);p[c.id]=s});cfg.ui.artPlan=p}}));
  // (worked out live: Settings keeps a page's row objects between redraws, so a value fixed here would go stale)
  const sum=()=>{const p=agPlan(),on=sys.filter(c=>(p[c.id]||[]).length),hd2=gp("ui.artHD")!==false;let missing=0,lowN=0;on.forEach(c=>(p[c.id]||[]).forEach(k=>{missing+=c.total-c[k];if(hd2)lowN+=c[k+"Low"]||0}));return {n:on.length,missing,lowN}};
  const startRow=A("Start download",()=>{if(AG.busy)return "Running…";const s=sum();return s.missing+s.lowN===0&&s.n?"Check again":"Start"},`Pictures you already have are kept. Searching sends your game titles to LaunchBox, libretro and GameTDB. The first time also fetches the LaunchBox database (about 110 MB, once).`,agStart,
    {ns:true,sub:()=>{const s=sum();return {text:!s.n?"Switch on at least one console":`${s.n} console${s.n>1?"s":""} · up to ${s.missing} missing picture${s.missing===1?"":"s"}`+(s.lowN?` and ${s.lowN} low-res`:""),cls:s.n?"":"warn"}}});
  Object.defineProperty(startRow,"dim",{get:()=>sum().n===0,enumerable:true});
  L.push(H("Options"),
    T("Sharpest scans first","ui.artHD",true,"Takes the sharpest version there is and swaps out low-res pictures you already have. A game with only small scans keeps the best one found, and gets upgraded when a sharper one turns up.",{keys:"hd sharp"}),
    H(""),startRow);
  return L}
// (beta.10) a download that ends puts the cursor back on the first row only when its own page is the one you're on (not
// editing, not searching): it used to land on whatever page was open, so the next A pressed the wrong row
function dlShown(id){return isOpen&&!!sub&&sub.id===id&&focus==="page"&&!edit&&!results}
async function agStart(){
  if(AG.busy||MG.busy||MC.busy){toast("A download is already running");return}
  const pl=agPlan(),ids=(AG.cov||[]).filter(c=>c.total&&(pl[c.id]||[]).length).map(c=>c.id);if(!ids.length)return;
  // (beta.10) busy at once, before the settings save: a quick second press used to start a second run
  Object.assign(AG,{busy:true,prog:{},stage:"Starting…",rep:null,err:null,started:Date.now(),ids});rowI=0;play("open");bump();dlClock();
  try{await save()}catch(e){AG.busy=false;toast("Box art: "+errMsg(e));if(isOpen)bump();return}
  try{const p={};ids.forEach(id=>{p[id]=pl[id]});AG.rep=await api.downloadCovers(ids,p,gp("ui.artHD")!==false)}catch(e){AG.err=errMsg(e)}
  AG.busy=false;AG.cov=null;needRescan=true;if(dlShown("art-get"))rowI=0;toast(AG.err?"Box art: "+AG.err:AG.rep&&AG.rep.cancelled?"Box art download cancelled":"Box art download finished");if(isOpen)bump()}
// Get manuals: the same, one switch per console
function mgLoad(){if(MG.loading||!api.manualCoverage)return;MG.loading=true;api.manualCoverage().then(c=>{MG.cov=Array.isArray(c)?c:[]}).catch(()=>{MG.cov=[]}).finally(()=>{MG.loading=false;if(isOpen)bump()})}
function manGetRows(){dlHook();
  if(MC.busy)return [H("Checking your manuals",{em:()=>took(MC.started)}),infoRow(MC.stage||"Starting…",MC.total?`${MC.done} of ${MC.total} consoles`:""),
    infoRow("So far","",{text:`${MC.checked} looked at · ${MC.moved} set aside`,cls:MC.moved?"warn":""}),
    A("Cancel",()=>MC.stopping?"Stopping…":"Cancel","Stops at the next step. What's been set aside so far is looked up the next time the background pass runs.",()=>{if(MC.stopping)return;MC.stopping=true;if(api.cancelCheckManuals)api.cancelCheckManuals();toast("Stopping")},{ns:true})];
  if(MC.rep||MC.err){const r=MC.rep||{},L=[];
    if(MC.err)L.push(H("The check didn't finish"),infoRow(MC.err,""));
    else{const bits=[`${r.checked||0} looked at`];if(r.moved)bits.push(`${r.got||0} replaced`);if(r.moved&&r.moved>(r.got||0))bits.push(`${r.moved-(r.got||0)} set aside without a match yet`);if(r.shrunk)bits.push(`${r.shrunk} made smaller`);
      L.push(H(r.cancelled?"Check stopped":"Manuals checked",{em:()=>"took "+took(MC.started)}),
        infoRow(r.cancelled?"Stopped · "+bits.join(" · "):r.moved||r.shrunk?"✓ "+bits.join(" · "):`✓ All ${r.checked||0} manuals look right`,"",{text:r.cancelled?"Stopped part way":r.moved||r.shrunk?"Changes show when you close Settings":"Nothing needed fixing",cls:r.cancelled?"warn":"ok"},
          {info:"Manuals set aside are kept in the game's .manual-originals folder, never deleted."}))}
    L.push(H(""),A("Back to the list","Back","Your consoles and what they still need.",()=>{MC.rep=null;MC.err=null;MG.cov=null;mgLoad()},{ns:true}));return L}
  if(MG.busy){const L=[H(MG.phase==="download"?"Downloading manuals":"Looking for manuals",{em:()=>took(MG.started)})];
    if(MG.phase==="download")L.push(infoRow(MG.stage||"",""));
    else MG.ids.forEach(id=>{const c=consoleList().find(x=>x.id===id)||{name:id},p=MG.prog[id],tot=p&&p.sysTotal||0,dn=p&&p.sysDone||0,fin=!!tot&&dn>=tot;
      L.push(infoRow(c.name,p&&tot?`${dn} of ${tot}`:"",{text:p?(fin?"Searched":p.game?"Now: "+p.game:""):"Waiting",cls:fin?"ok":""}))});
    if(MG.phase!=="download")L.push(A("Cancel","Cancel","Stops looking. Nothing is downloaded.",()=>{if(api.cancelManuals)api.cancelManuals();toast("Stopping")},{ns:true}));return L}
  if(MG.rep||MG.err){const L=[];
    if(MG.err)L.push(H("Manuals didn't finish"),infoRow(MG.err,""));
    else{const added=MG.rep.systems.reduce((a,x)=>a+x.added.length,0),miss=MG.rep.systems.reduce((a,x)=>a+x.none.length+x.unsure.length+x.failed.length,0);
      L.push(H("All done",{em:()=>"took "+took(MG.started)}),infoRow(`${added} manual${added===1?"":"s"} added`,"",{text:miss?`${miss} game${miss===1?"":"s"} still without one`:"Every game you picked has one",cls:miss?"warn":"ok"},{info:"They show up when you close Settings."}));
      MG.rep.systems.forEach(x=>{const noPrint=["sw","sw2","ps4","ps5"].includes(x.id);L.push(infoRow(x.name,x.added.length?`${x.added.length} added`:"Nothing new",
        {text:[x.had?`${x.had} already had one`:"",x.unsure.length?`${x.unsure.length} unsure skipped`:"",x.none.length?`${x.none.length} ${noPrint?"with no printed manual":"not found"}`:"",x.failed.length?`${x.failed.length} failed`:""].filter(Boolean).join(" · ")||"Complete",cls:x.none.length||x.unsure.length||x.failed.length?"warn":"ok"},
        {info:noPrint&&x.none.length?"These games keep their guide inside the game; Boxshelf makes a booklet instead.":""}))})}
    L.push(H(""),A("Back to the list","Back","Your consoles and what they still need.",()=>{MG.rep=null;MG.err=null;MG.cov=null;mgLoad()},{ns:true}));return L}
  if(!MG.cov){mgLoad();return [infoRow("Checking what you have…","")]}
  const sys=MG.cov.filter(c=>c.total>0);if(!sys.length)return [infoRow("No games yet","",{text:"Add a games folder for a console first (Consoles)"})];
  if(!MG.pick)MG.pick=new Set(sys.map(c=>c.id));
  const L=[H("Consoles",{em:()=>`${sys.filter(c=>MG.pick.has(c.id)).length} of ${sys.length} picked`})];
  sys.forEach(c=>{const miss=c.total-c.have;L.push({t:"toggle",label:c.name,ns:true,nosave:true,info:"On: look for this console's manuals.",get:()=>MG.pick.has(c.id),set:v=>{v?MG.pick.add(c.id):MG.pick.delete(c.id)},
    sub:()=>({text:(miss?`${c.have} of ${c.total} manuals · ${miss} missing`:`All ${c.total} manuals`)+(c.low?` · ${c.low} small scan${c.low===1?"":"s"}`:""),cls:miss?"":"ok"})})});
  const sum=()=>{const on=sys.filter(c=>MG.pick.has(c.id));return {n:on.length,missing:on.reduce((a,c)=>a+c.total-c.have,0)}}; // (live, as above)
  const startRow=A("Start download",()=>MG.busy?"Running…":"Start","Manuals you already have are kept. Searching sends your game titles to the Internet Archive and GamesDatabase.org.",mgStart,
    {ns:true,sub:()=>{const s=sum();return {text:!s.n?"Switch on at least one console":s.missing?`${s.n} console${s.n>1?"s":""} · up to ${s.missing} missing manual${s.missing===1?"":"s"}`:"Every game you picked already has one",cls:s.n&&s.missing?"":"warn"}}});
  Object.defineProperty(startRow,"dim",{get:()=>{const s=sum();return !s.n||!s.missing},enumerable:true});
  L.push(A("Select all","All","Every console.",()=>{MG.pick=new Set(sys.map(c=>c.id))},{ns:true}),A("Select none","None","No console.",()=>{MG.pick=new Set()},{ns:true}),
    H("Options"),T("Only sure English manuals","ui.manualSure",true,"Off takes the best match for every game, even unsure ones.",{keys:"language unsure"}),
    H(""),startRow);
  if(api.checkManuals)L.push(H("Your manuals"),A("Check my manuals",()=>MC.busy?"Running…":"Check","Looks again at every manual you have: another game's, or one with thumbnail pages, is set aside (kept in .manual-originals) and looked up again; huge pages are made smaller. Manuals you picked yourself are left alone.",mcStart,
    {ns:true,keys:"check recheck wrong manuals fix",when:()=>!!api.checkManuals,sub:()=>({text:"Every console · the manuals already on your shelves",cls:""})}));
  return L}
// Check my manuals: one at a time with the other downloads here; the elapsed time ticks each second while it runs
function mcClock(){clearTimeout(mcTick);if(!MC.busy)return;mcTick=setTimeout(()=>{if(isOpen)bump();mcClock()},1000)}
async function mcStart(){
  if(AG.busy||MG.busy||MC.busy){toast("A download is already running");return}if(!api.checkManuals)return;
  // (at once: a second quick press must not start a second check while the settings save, and the page shows this run)
  Object.assign(MC,{busy:true,stage:"Starting…",done:0,total:0,checked:0,moved:0,rep:null,err:null,stopping:false,started:Date.now()});
  if(!mcHooked&&api.onManualCheck){mcHooked=true;api.onManualCheck(d=>{if(!MC.busy||!d)return;if(d.stage)MC.stage=d.stage;if(d.total)MC.total=d.total;if(d.done!=null)MC.done=d.done;if(d.checked!=null)MC.checked=d.checked;if(d.moved!=null)MC.moved=d.moved;if(isOpen)bump()});
    // (it first waits for the background manual pass to pause at its next step: say so instead of "Starting…")
    if(api.onManualTurn)api.onManualTurn(d=>{if(MC.busy&&d&&d.wait&&MC.stage==="Starting…"){MC.stage="Waiting for the background manual download to pause…";if(isOpen)bump()}})}
  rowI=0;play("open");bump();mcClock();try{await save()}catch{}
  if(MC.stopping)MC.rep={checked:0,moved:0,shrunk:0,got:0,cancelled:true}; // (Cancel while the settings saved: it never starts)
  else{try{MC.rep=await api.checkManuals()||{}}catch(e){MC.err=errMsg(e)}}
  MC.busy=false;MC.stopping=false;clearTimeout(mcTick);if(dlShown("man-get"))rowI=0;MG.cov=null;const r=MC.rep||{};if(r.moved||r.shrunk)needRescan=true;
  toast(MC.err?"Manuals: "+MC.err:r.cancelled?"Manual check stopped":"Manual check finished");if(isOpen)bump()}
async function mgStart(){
  if(AG.busy||MG.busy||MC.busy){toast("A download is already running");return}const ids=[...(MG.pick||[])];if(!ids.length||!api.findManuals)return;
  Object.assign(MG,{busy:true,prog:{},stage:"Starting…",phase:"search",rep:null,err:null,started:Date.now(),ids});rowI=0;play("open");bump();dlClock(); // (beta.10) busy before the save, like the manual check
  try{await save()}catch(e){MG.busy=false;toast("Manuals: "+errMsg(e));if(isOpen)bump();return}
  try{const found=await api.findManuals(ids)||[];
    const min=window.BoxshelfManMin||7,pick=[],unsure=[],none=[];
    found.forEach(r=>{if(r.have)return;const c=(r.candidates||[])[0];if(!c){none.push(r);return}if(gp("ui.manualSure")!==false&&!(c.score>=min&&/^(eng|en|english|)$/i.test(c.language||""))){unsure.push(r);return}pick.push({r,c})});
    MG.phase="download";MG.stage=pick.length?`Downloading ${pick.length} manual${pick.length===1?"":"s"}…`:"Nothing to download";bump();
    const rep=pick.length?await api.downloadManuals(pick.map(({r,c})=>({systemId:r.systemId,file:r.file,id:c.id}))):[];
    const byFile=new Map((rep||[]).map(x=>[x.file,x]));
    MG.rep={systems:ids.map(id=>{const cc=(MG.cov||[]).find(c=>c.id===id),mine=found.filter(r=>r.systemId===id),ok=p=>p.r.systemId===id&&(byFile.get(p.r.file)||{}).pages;
      return {id,name:cc?cc.name:id,had:mine.filter(r=>r.have).length,added:pick.filter(ok).map(p=>p.r.title),failed:pick.filter(p=>p.r.systemId===id&&!ok(p)).map(p=>p.r.title),unsure:unsure.filter(r=>r.systemId===id).map(r=>r.title),none:none.filter(r=>r.systemId===id).map(r=>r.title)}})};
  }catch(e){MG.err=errMsg(e)}
  MG.busy=false;MG.cov=null;needRescan=true;if(dlShown("man-get"))rowI=0;toast(MG.err?"Manuals: "+MG.err:"Manual download finished");if(isOpen)bump()}

/* ---------- pages & navigation ---------- */
// (beta.9) one build of the page's rows per moment: the input, the info card, the hints and the page itself all asked for
// it (4-5 full builds, every dyn row run each time, per press). Dropped at the next microtask, at every redraw and on
// every change or move (sp, changed, goSec, openSub, leaveSub, search), so it's never stale
let secMemo=null;const secDrop=()=>{secMemo=null};
function sec(){if(secMemo)return secMemo;secMemo=secBuild();Promise.resolve().then(secDrop);return secMemo}
function secBuild(){
  if(results)return {g:"Search",id:"search",name:query?`Results for “${query}”`:"Search",lead:query?`${resultTotal>results.length?`Showing ${results.length} of ${resultTotal}`:`${results.length} setting${results.length===1?"":"s"} found`}. ${word("A","Enter","Click")} opens one on its own page.`:"Type a word, like “shadows”, “volume” or “overscan”.",rows:results};
  if(sub)return subPage(sub);
  return expand(SECTIONS[secI])}
function subPage(s){if(s.kind==="console")return consolePage(s.id);if(s.kind==="conadv")return conAdvPage(s.id);if(s.kind==="svc")return svcPage(s.id);
  if(s.kind==="more"){const m=MORE[s.id],o=SECTIONS[secI]||{};return expand({g:o.g,id:o.id,ic:o.ic,name:m?m.name:"",lead:m?m.lead:"",rows:m?m.rows:[],extra:m&&m.extra})}return orderPage()}
// a sub-page's own name, and where B goes from it (the hint and the crumb name it): the page it was opened from
function subName(s){const c=()=>(consoleList().find(x=>x.id===s.id)||{name:s.id}).name;return s.kind==="console"?c():s.kind==="conadv"?"Emulator & advanced":s.kind==="svc"?SVC_N[s.id]||s.id:s.kind==="more"?(MORE[s.id]||{}).name||"":"Console order"}
function subUpName(s){if(!s)return "";if(artAcct&&s===artAcct.page)return "Art Studio";if(s.up)return subName(s.up);if(s.kind==="console"||s.kind==="conadv")return "Consoles";if(s.kind==="svc")return "Accounts";if(s.kind==="order")return "Shelf";return (SECTIONS[secI]||{}).name||""}
function crumbOf(s){const L=[];let x=s;for(;x.up;x=x.up)L.unshift(subName(x.up));L.unshift(x.kind==="svc"?"Accounts & services":subUpName(x));L.push(subName(s));return L.join("  ›  ")}
function expand(s){const rows=[];s.rows.forEach(r=>{if(r.t==="dyn")rows.push(...r.fn());else rows.push(r)});if(s.extra)rows.push(...s.extra());return {...s,rows}}
const rowsOf=s=>s.rows.filter(r=>r.t!=="head"&&shown(r));
const curRow=()=>focus==="page"||focus==="edit"||(focus==="osk"&&edit)?rowsOf(sec())[rowI]:null;
const word=(pad,key,mouse)=>device==="pad"?pad:device==="mouse"?mouse:key;
function goSec(i){secDrop();secI=(i+SECTIONS.length)%SECTIONS.length;sub=null;rowI=0;grab=null;studioClear()}
function goSecId(id){let i=SECTIONS.findIndex(s=>s.id===id);if(i<0)i=SECTIONS.findIndex(s=>s.alias&&s.alias.includes(id));if(i>=0)goSec(i)} // (alias: a platform that merges sections keeps the old names working)
// sub-pages nest (a console → its Emulator & advanced): each one keeps the page under it (up) and that page's own return row
function openSub(s){secDrop();s.up=sub;s.upRet=subRet;subRet=rowI;sub=s;rowI=0;focus="page";grab=null;play("open");render()}
function leaveSub(){if(artAcct&&sub&&sub===artAcct.page){artBackTo();return}secDrop();const was=sub;sub=was&&was.up||null;grab=null;rowI=subRet||0;subRet=was&&was.upRet||0;if(was&&was.kind==="console"){const k=tileList().findIndex(c=>c.id===was.id);if(k>=0)ci=k}}
function openConsole(id){if(sub){openSub({kind:"console",id})}else{subRet=rowI;sub={kind:"console",id};rowI=0;focus="page";play("open");render()} /* from inside a sub-page (RetroAchievements' emulator rows): nested, so B comes back to it */if(id==="gc"&&!ipl&&api.findIpl&&iplCap())Promise.resolve(save()).then(()=>api.findIpl("gc")).then(r=>{ipl=r;bump()}).catch(()=>{});if(id==="sw2"&&sw2Row())Promise.resolve(save()).then(()=>api.sw2Builds("")).then(r=>{sw2b=r&&r.builds||[];bump()}).catch(()=>{sw2b=[];bump()})} /* Switch 2: the Eglantine builds for its Build row, looked for again each time the page opens */
const sw2Row=()=>!!api.sw2Builds&&!(window.bsPlatform&&bsPlatform.os==="android");
// (beta.3) Settings → Switch 2 → Builds. One choice among "Ask each time" and every build (the shelf's emulator is the
// build Play starts in, or offers first when it asks); builds and folders of builds kept elsewhere can be added and removed
// (beta.4) Settings → Custom games: Quiver Launcher (found where it usually is, or found / chosen once for a portable
// copy) and the programs added by hand, each named here. pcSt: what main sees ({ quiver: folder, epic: count }), asked
// for when the page is drawn
let pcSt=null,pcAsking=false;const customNameRows=new WeakMap();
// (beta.7) a store row's line: "3 installed" / "None installed" once main has counted (null while asking)
function pcCount(k){if(!pcSt)setTimeout(pcStatus,0);return pcSt&&pcSt[k]!=null?{text:pcSt[k]?`${pcSt[k]} installed`:"None installed"}:null}
function pcStatus(){if(pcAsking||!api.pcStatus)return;pcAsking=true;Promise.resolve(api.pcStatus()).then(r=>{pcSt=r||{};pcAsking=false;bump()}).catch(()=>{pcSt={};pcAsking=false;bump()})}
// (beta.9) how many custom games have no cover yet (the Covers rows)
const COV={n:null,loading:false,busy:false};
function covLoad(force){if(!api.pcCoverMissing||COV.loading||(COV.n!=null&&!force))return;COV.loading=true;
  Promise.resolve(api.pcCoverMissing()).then(L=>{COV.n=Array.isArray(L)?L.length:0}).catch(()=>{COV.n=0}).finally(()=>{COV.loading=false;bump()})}
function customRows(){if(!pcSt)setTimeout(pcStatus,0);cfg.pc=cfg.pc||{epic:true,quiver:true,quiverPath:"",custom:[]};const pc=cfg.pc;if(!Array.isArray(pc.custom))pc.custom=[];const rows=[];
  const again=()=>{pcSt=null;changed({rescan:true})};
  rows.push({t:"head",text:"Quiver Launcher"});
  rows.push(T("Show Quiver's games","pc.quiver",true,"Every app Quiver Launcher has installed (recomps, PC ports) gets a case on the Custom shelf. Boxshelf starts them from their own folders; updates stay in Quiver.",{rescan:true,keys:"quiver recomp recomps ports launcher"}));
  rows.push(A("Quiver Launcher",()=>pcSt&&pcSt.quiver?"Found":"Find","Boxshelf looks where Quiver usually keeps its library. A portable copy (unzipped anywhere) is found with Find, or choose its folder.",async()=>{
    toast("Looking for Quiver Launcher…");const d=await Promise.resolve(api.findQuiver&&api.findQuiver()).catch(()=>"");
    if(d){pc.quiverPath=d;again();toast("Found Quiver Launcher in "+folderName(d))}
    else{const p=await pick({kind:"folder",title:"Choose Quiver Launcher's folder (the one with QuiverLauncher.exe)"}),f=typeof p==="string"?p:p&&p.path;if(f){pc.quiverPath=f;again();toast("Quiver Launcher's folder set")}}},
    {when:()=>pc.quiver!==false,ns:true,keys:"quiver folder find",sub:()=>pcSt==null?{text:"Looking…"}:pcSt.quiver?{text:folderName(pcSt.quiver)}:{text:"Not found yet · press to find it",cls:"warn"}}));
  rows.push({t:"head",text:pc.custom.length?`Your games (${pc.custom.length})`:"Your games"});
  // (beta.7) every way of adding games on one page: several programs, a folder of games, Playnite, one program
  rows.push(M("Add games","addgames","Several programs at once, a whole folder of games, your Playnite library, or one program.","Every way to put games on the Custom shelf. You see what was found before anything is added.",[{t:"dyn",fn:addRows}],
    {keys:"add game games custom several many folder scan playnite import program exe recomp port",sub:()=>({text:"Several at once, a folder, Playnite or one program"})}));
  // (beta.9) covers for every custom game at once (app.js pcCoverBatch / pcCoverAuto): they never get one automatically
  if(api.pcCoverMissing&&window.Shelf&&Shelf.pcCoverBatch){covLoad();const n=COV.n,S=window.Shelf;
    rows.push({t:"head",text:"Covers"});
    rows.push(A("Choose covers one by one","Start","Goes through every custom game without a cover of its own: SteamGridDB's covers for each (with a free SteamGridDB key, Settings → Accounts & services) or a picture of yours. A uses one, Skip moves on, B stops.",
      ()=>{if(!n){toast("Every custom game has a cover already");return}close();S.pcCoverBatch()},{ns:true,keys:"covers cover art all custom games batch steamgriddb recomp port decomp",sub:()=>({text:n==null?"Looking…":n?`${n} game${n===1?"":"s"} without a cover`:"Every game has one"})}));
    rows.push(A("Use the best cover for each","Find","Picks SteamGridDB's top cover for every custom game without one, in one go. A recomp can share its name with another game: look them over, and Undo puts them all back.",
      async()=>{if(!n){toast("Every custom game has a cover already");return}if(COV.busy)return;COV.busy=true;bump();try{await S.pcCoverAuto()}finally{COV.busy=false;covLoad(true)}},{ns:true,keys:"covers best match all automatic steamgriddb",sub:()=>({text:COV.busy?"Finding covers…":"Needs a SteamGridDB key"})}));
    if(S.coverUndoCount&&S.coverUndoCount())rows.push(A(`Undo the ${S.coverUndoCount()} covers just found`,"Undo","Puts back the made-up cases those games had before.",async()=>{await S.pcCoverUndo();covLoad(true)},{ns:true,keys:"undo covers"}));}
  pc.custom.forEach((c,i)=>{
    // (beta.5) the name row is kept per game (the text editor finds its row by identity: a new object each draw never edited)
    let nr=customNameRows.get(c);if(!nr){nr=X(c.title||"Game","",`Its name on the shelf. Program: ${c.exe}`,{get:()=>c.title||"",set:v=>{c.title=String(v||"").slice(0,120);nr.label=c.title||"Game"},rescan:true,ns:true,keys:"rename custom game",sub:()=>({text:folderName(c.exe)})});customNameRows.set(c,nr)}
    rows.push(nr);
    // (beta.10) which shelf it sits on: Custom, or the PC shelf beside the store games
    rows.push(A("Shelf",()=>c.shelf==="pc"?"PC":"Custom","Which shelf this game is on: Custom (games you added) or PC (with your Steam and store games). Press to move it.",()=>{if(c.shelf==="pc")delete c.shelf;else c.shelf="pc";changed({rescan:true});toast(`${c.title||"The game"} is on the ${c.shelf==="pc"?"PC":"Custom"} shelf now`)},{ns:true,keys:"shelf move pc custom shelf"}));
    rows.push(A("Remove "+(c.title||"this game"),"Remove","Takes it off its shelf. Nothing is deleted.",()=>twice("pcx:"+c.exe+"|"+(c.title||""),()=>{const k=pc.custom.indexOf(c);if(k>=0)pc.custom.splice(k,1);changed({rescan:true});toast("Removed from the Custom shelf (nothing was deleted)")},"Press again to remove it"),{ns:true,ask:"pcx:"+c.exe+"|"+(c.title||"")}))}); // (beta.10) keyed by the game, not its place in the list
  return rows}
const sw2Low=x=>String(x||"").toLowerCase();
function sw2Fixed(){const c=sysCfg("sw2");if(c.askBuild!==false||!c.emulator)return null;return (sw2b||[]).find(b=>sw2Low(b.exe)===sw2Low(c.emulator))||{exe:c.emulator,label:progName(c.emulator)}}
let sw2Asking=0,sw2In=false; // (the newest ask wins; sw2In: one is on its way)
function sw2Reload(){sw2b=null;sw2In=true;bump();const me=++sw2Asking;Promise.resolve(save()).then(()=>api.sw2Builds("")).then(r=>{if(me===sw2Asking){sw2b=r&&r.builds||[];sw2In=false;bump()}}).catch(()=>{if(me===sw2Asking){sw2b=[];sw2In=false;bump()}})}
// the list is asked for when it's missing, however the page was reached (a link straight to Switch 2 skipped the console
// tile's own ask, and the row said "Looking for builds…" for ever)
function sw2Want(){if(sw2b==null&&!sw2In)setTimeout(()=>{if(sw2b==null&&!sw2In)sw2Reload()},0)}
function sw2BuildRows(){sw2Want();const c=sysCfg("sw2"),fx=sw2Fixed(),L=sw2b||[],day=t=>new Date(t).toLocaleDateString(undefined,{month:"short",day:"numeric",year:"numeric"}),rows=[];
  const choose=exe=>{if(exe){c.askBuild=false;c.emulator=exe}else c.askBuild=true;play("tick",1);changed(null)};
  rows.push(A("Ask each time",()=>sw2Fixed()?"Choose":"✓ Chosen","Play shows the list of builds, starting on the one you last used for that game.",()=>choose(null),{sub:()=>({text:"Play shows the list"})}));
  rows.push({t:"head",text:sw2b==null?"Looking for builds…":L.length?`${L.length} build${L.length===1?"":"s"} found`:"No builds found yet: add a build or a folder of builds below"});
  L.forEach(b=>{const on=()=>{const f=sw2Fixed();return !!f&&sw2Low(f.exe)===sw2Low(b.exe)},first=()=>!sw2Fixed()&&sw2Low(sysCfg("sw2").emulator)===sw2Low(b.exe);
    rows.push(A(b.label,()=>on()?"✓ Always":"Always use",`Always start Switch 2 games in this build.\n${b.exe}`,()=>choose(b.exe),
      {ns:true,keys:"build fork "+b.label,sub:()=>({text:`${day(b.date)}${b.nup?" · opens .nup":""}${first()?" · Play offers it first":""}`})}))});
  rows.push({t:"head",text:"Builds kept somewhere else"});
  rows.push(A("Add a folder of builds","Browse…","A folder holding several builds (your forks). Boxshelf looks four folders deep inside it.",async()=>{const f=await pick({kind:"folder",title:"Choose a folder of Eglantine builds"}),d=typeof f==="string"?f:f&&f.path;if(!d)return;
    const a=Array.isArray(c.buildDirs)?c.buildDirs:[];if(a.some(x=>sw2Low(x)===sw2Low(d)))return toast("That folder is already in the list");c.buildDirs=[...a,d].slice(0,20);changed(null);sw2Reload();toast("Looking for builds in "+folderName(d))}));
  rows.push(A("Add a build","Browse…","One build's program (eglantine.exe, lichii.exe, orchid.exe…), wherever it is.",async()=>{const f=await pick({kind:"file",title:"Choose an Eglantine build",filters:[{name:"Programs",extensions:["exe"]}]}),x=typeof f==="string"?f:f&&f.path;if(!x)return;
    const a=Array.isArray(c.buildExes)?c.buildExes:[];if(a.some(y=>sw2Low(y)===sw2Low(x))||L.some(b=>sw2Low(b.exe)===sw2Low(x)))return toast("That build is already in the list");c.buildExes=[...a,x].slice(0,20);changed(null);sw2Reload();toast("Build added")}));
  // (beta.10) by path, not by place in the list: removing one shifted an armed confirmation onto the next
  const drop=(k,v,what)=>{const a=(c[k]||[]).slice(),j=a.indexOf(v);if(j>=0)a.splice(j,1);c[k]=a;changed(null);sw2Reload();toast(what+" removed from the list (nothing was deleted)")};
  (Array.isArray(c.buildDirs)?c.buildDirs:[]).forEach(d=>rows.push(A("Folder · "+folderName(d),"Remove",`Builds in this folder are listed. Remove takes it off the list; nothing is deleted.\n${d}`,()=>twice("sw2d:"+d,()=>drop("buildDirs",d,"Folder"),"Press again to remove this folder"),{ns:true,ask:"sw2d:"+d})));
  (Array.isArray(c.buildExes)?c.buildExes:[]).forEach(x=>rows.push(A("Build · "+progName(x),"Remove",`Remove takes it off the list; nothing is deleted.\n${x}`,()=>twice("sw2x:"+x,()=>drop("buildExes",x,"Build"),"Press again to remove this build"),{ns:true,ask:"sw2x:"+x})));
  return rows}

/* ---------- search ---------- */
function allSettings(){const out=[];
  SECTIONS.forEach((s,si)=>{let k=0,grp="";const rs=expand(s).rows;rs.forEach(r=>{if(r.t==="head"){grp=r.text;return}if(!shown(r))return;const i=k++;
    if(r.t==="consoles"){consoleList().forEach(c=>out.push({sec:si,console:c.id,label:c.name,where:"Games › Consoles",text:`${c.name} ${c.emulatorName||""} emulator console`}));return}
    if(r.ns&&!r.keys)return;out.push({sec:si,i,row:r,label:r.label,where:`${s.g?s.g+" › ":""}${s.name}${grp?" › "+grp:""}`,text:[r.label,r.info,r.keys,s.name,grp,r.opts&&typeof r.opts!=="function"?r.opts.map(o=>o[1]).join(" "):""].join(" ")})})});
  // the sections' own sub-pages (Screen & motion, Art Studio, Advanced…): found here, opened on the row inside
  SECTIONS.forEach((s,si)=>{const top=rowsOf(expand(s));top.forEach((m,at)=>{const pg=m.pg&&MORE[m.pg];if(!pg)return;let k=0,grp="";expand(pg).rows.forEach(r=>{if(r.t==="head"){grp=r.text;return}if(!shown(r))return;const i=k++;if(r.ns&&!r.keys)return;
    out.push({sec:si,more:pg.id,at,i,row:r,label:r.label,where:`${s.name} › ${pg.name}${grp?" › "+grp:""}`,text:[r.label,r.info,r.keys,s.name,pg.name,grp,r.opts&&typeof r.opts!=="function"?r.opts.map(o=>o[1]).join(" "):""].join(" ")})})})});
  const ai0=SECTIONS.findIndex(s=>s.id==="acct");if(ai0>=0)Object.keys(SVC_N).forEach(id=>{const p=svcPage(id);let k=0,grp="";p.rows.forEach(r=>{if(r.t==="head"){grp=r.text;return}if(r.t==="dyn"||!shown(r))return;const i=k++;if(r.ns&&!r.keys)return;
    out.push({sec:ai0,svc:id,i,row:r,label:r.label,where:`Accounts & services › ${p.name}${grp?" › "+grp:""}`,text:[r.label,r.info,r.keys,p.name,grp].join(" ")})})});
  const ci0=SECTIONS.findIndex(s=>s.id==="consoles");
  consoleList().forEach(c=>{const P=conPages(c.id);[P.main,P.adv].forEach(p=>{const adv=p===P.adv;let k=0,grp="";p.rows.forEach(r=>{if(r.t==="head"){grp=r.text;return}if(!shown(r))return;const i=k++;if(r.ns)return;
    out.push({sec:ci0,console:c.id,adv,i,row:r,label:r.label,where:`Consoles › ${c.name}${adv?" › "+p.name:""}${grp&&grp!==c.emulatorName?" › "+grp:""}`,text:[r.label,r.info,r.keys,c.name,c.emulatorName,adv?"advanced":""].join(" ")})})})});
  return out}
// (beta.9) the search index is built once per search (every section, sub-page and console page), not on every key
let searchIdx=null;
function runSearch(){secDrop();const q=query.trim().toLowerCase();if(!q){results=[];resultTotal=0;rowI=0;return}
  const words=q.split(/\s+/);
  const scored=(searchIdx||(searchIdx=allSettings())).map(e=>{const t=e.text.toLowerCase(),l=e.label.toLowerCase();if(!words.every(w=>t.includes(w)))return null;
    return {...e,score:(l.startsWith(q)?3:0)+(l.includes(q)?2:0)+(words.every(w=>l.includes(w))?1:0)}}).filter(Boolean).sort((a,b)=>b.score-a.score);
  resultTotal=scored.length;results=scored.slice(0,40).map(e=>({t:"result",label:e.label,where:e.where,go:e,info:e.row?e.row.info:"Opens this console's page.",row:e.row}));rowI=0}
function hi(label){const ws=query.trim().split(/\s+/).filter(Boolean).map(w=>w.replace(/[.*+?^${}()|[\]\\]/g,"\\$&"));if(!ws.length)return esc(label);
  const re=new RegExp("("+ws.sort((a,b)=>b.length-a.length).join("|")+")","ig");return String(label).split(re).map((p,j)=>j%2?`<mark>${esc(p)}</mark>`:esc(p)).join("")}
// (beta.10) the real GameCube startup (IPL.bin) needs Dolphin's Windows folders: Linux and Android say dolphinIpl:false
function iplCap(){return !window.bsPlatform||bsPlatform.has("dolphinIpl")}
// (beta.10) Android: games folders removed this session. Android's permission for one is given back at once, so the
// folders found earlier (det) never offer it again: added back that way it couldn't be read. Picking it again works
function rdGone(){return rdGone.s||(rdGone.s=new Set())}
function openSearch(viaPad){searchIdx=null;if(edit)cancelEdit();grab=null;if(!results)preSearch={secI,rowI,sub,subRet,focus,studio,stack,sRet};studio=null;stack=[];sSig="";runSearch();focus="search";if(viaPad)openOsk("search");render()}
function closeSearch(){searchIdx=null;secDrop();results=null;query="";closeOsk();if(preSearch){({secI,rowI,sub,subRet}=preSearch);if(preSearch.studio){studio=preSearch.studio;stack=preSearch.stack||[];sRet=preSearch.sRet||0;sSig=""}focus=preSearch.focus==="search"||preSearch.focus==="osk"?"side":preSearch.focus}else focus="side";preSearch=null;render()}
function typeChar(c){if(edit){edit.value+=c;paintEdit();return}query+=c;runSearch();if(focus!=="osk")focus="search";render()}
function backspace(){if(edit){edit.value=edit.value.slice(0,-1);paintEdit();return}query=query.slice(0,-1);runSearch();render()}
function goResult(r){const e=r.go;results=null;closeOsk();preSearch=null;query="";focus="page";
  secI=e.sec;sub=null;
  if(e.console){const k=tileList().findIndex(c=>c.id===e.console);if(k>=0)ci=k;subRet=0;
    if(e.i===undefined){rowI=0;openConsole(e.console);return}
    sub={kind:"console",id:e.console};if(e.adv){subRet=Math.max(0,rowsOf(consolePage(e.console)).findIndex(r=>r.label==="Emulator & advanced"));sub={kind:"conadv",id:e.console,up:sub,upRet:0}}if(e.console==="gc"&&!ipl&&api.findIpl&&iplCap())Promise.resolve(save()).then(()=>api.findIpl("gc")).then(r=>{ipl=r;bump()}).catch(()=>{})} // the IPL row's Found / No IPL.bin line, as openConsole loads it
  if(e.svc){subRet=Math.max(0,rowsOf(expand(SECTIONS[e.sec])).findIndex(r=>r.status===e.svc));sub={kind:"svc",id:e.svc}}
  if(e.more){subRet=e.at||0;sub={kind:"more",id:e.more}}
  rowI=e.i||0;render();flashCur()}
function flashCur(){const el=E.list.querySelector(".bss-row.cur");if(el){el.classList.remove("flash");void el.offsetWidth;el.classList.add("flash")}}

/* ---------- on-screen keyboard (search, and typing into a setting) ---------- */
const L0=[["1","2","3","4","5","6","7","8","9","0"],["q","w","e","r","t","y","u","i","o","p"],["a","s","d","f","g","h","j","k","l","'"],["z","x","c","v","b","n","m","-",".","⌫"]];
const L1=[["1","2","3","4","5","6","7","8","9","0"],["Q","W","E","R","T","Y","U","I","O","P"],["A","S","D","F","G","H","J","K","L","\""],["Z","X","C","V","B","N","M","_",":","⌫"]];
const L2=[["!","@","#","$","%","^","&","*","(",")"],["\\","/",":",";","_","=","+","{","}","~"],["[","]","<",">","|","?",",","`","'","\""],["€","£","¥","§","°","·","…","-",".","⌫"]];
let oskMode="search",oskSig="";
function keysNow(){if(oskMode==="search")return [...L0,["Space","Clear","Done"]];return [...[L0,L1,L2][oskLayer],["Shift","#+=","Space","Clear","Cancel","Done"]]}
function openOsk(mode){oskMode=mode||"search";oskLayer=0;E.osk.classList.add("on");focus="osk";const n=keysNow().flat().length;if(oskI>=n)oskI=0;oskSig=""}
function closeOsk(){E.osk.classList.remove("on");if(focus==="osk")focus=edit?"page":"search"}
function renderOsk(){if(!E.osk.classList.contains("on"))return;const K=keysNow(),sig=oskMode+oskLayer+(device==="pad")+swapAB()+padStyle();
  if(sig!==oskSig){oskSig=sig;let i=0;
    E.osk.innerHTML=K.map(row=>`<div class="bss-krow">${row.map(k=>{const j=i++,cls=k==="Space"?"wide":k.length>1&&k!=="⌫"?"mid":"";return `<div class="bss-key ${cls} ${k==="Done"?"go":""} ${(k==="Shift"&&oskLayer===1)||(k==="#+="&&oskLayer===2)?"lit":""}" data-j="${j}">${esc(k)}</div>`}).join("")}</div>`).join("");
    E.osk.querySelectorAll(".bss-key").forEach(k=>k.onclick=e=>{e.stopPropagation();oskI=+k.dataset.j;focus="osk";pressKey(keysNow().flat()[oskI]);render()})}
  E.osk.querySelectorAll(".bss-key").forEach(k=>k.classList.toggle("cur",focus==="osk"&&+k.dataset.j===oskI))}
function pressKey(k){if(k==="⌫")backspace();else if(k==="Space")typeChar(" ");else if(k==="Clear"){if(edit){edit.value="";paintEdit()}else{query="";runSearch()}}
  else if(k==="Done")oskDone();else if(k==="Cancel")cancelEdit();else if(k==="Shift"){oskLayer=oskLayer===1?0:1}else if(k==="#+="){oskLayer=oskLayer===2?0:2}
  else{typeChar(k);if(oskLayer===1)oskLayer=0}}
function oskDone(){if(edit){commitEdit();return}closeOsk();focus=results&&results.length?"page":"search";rowI=0}
function oskMove(a){const K=keysNow(),pos=[];K.forEach((row,ri)=>row.forEach((k,c)=>pos.push([ri,c])));if(!pos[oskI])oskI=0;const [ri,c]=pos[oskI];
  if(a==="left"||a==="right"){const n=K[ri].length,nc=(c+(a==="right"?1:-1)+n)%n;oskI=pos.findIndex(p=>p[0]===ri&&p[1]===nc)}
  else{const nr=ri+(a==="down"?1:-1);if(nr<0){if(!edit){closeOsk()}return}if(nr>=K.length)return;
    const frac=(c+.5)/K[ri].length,nc=Math.min(K[nr].length-1,Math.floor(frac*K[nr].length));oskI=pos.findIndex(p=>p[0]===nr&&p[1]===nc)}}

/* ---------- editing a text setting ---------- */
function startEdit(r,first){edit={row:r,value:(first!=null?String(r.get()||"")+first:String(r.get()||"")),idx:rowI};
  if(device==="pad"){openOsk("edit");render()}else{focus="edit";render();const inp=E.list.querySelector(".bss-edit");if(inp){inp.focus();try{inp.setSelectionRange(inp.value.length,inp.value.length)}catch{}}}}
function paintEdit(){const el=E.list.querySelector(".bss-editv");if(el){el.firstChild.textContent=edit.row.secret?"•".repeat(edit.value.length):edit.value}}
function commitEdit(){if(!edit)return;const r=edit.row,v=edit.value.trim().replace(/^"(.*)"$/,"$1");edit=null;closeOsk();focus="page";
  if(!eq(v,r.get()||"")){r.set(v);changed(r);if(r.ra||r.fan)setTimeout(loadStatus,2500)}else{render()}}
function cancelEdit(){if(!edit)return;edit=null;closeOsk();focus="page";render()}

/* ---------- changing things ---------- */
// (beta.9) a burst of answers (the ~10 that come back as Settings opens, a download's progress) is drawn once, next frame
// (the next frame or 50 ms, whichever comes first: a page drawn slowly, or in software, never waits a whole frame for it)
let bumpT=0,bumpT2=0;function bump(){if(bumpT||!isOpen)return;const go=()=>{if(!bumpT)return;cancelAnimationFrame(bumpT);clearTimeout(bumpT2);bumpT=0;bumpT2=0;render()};bumpT=requestAnimationFrame(go);bumpT2=setTimeout(go,50)}
function changed(r){secDrop();
  if(r&&r.rescan)needRescan=true;if(r&&r.ra)raChanged=true;
  if(!(r&&r.nosave))scheduleSave();
  if(r&&r.live)try{r.live(r.get())}catch(e){console.warn("settings live",e)}
  if(r&&r.preview)peek(r.preview);
  render();
}
function scheduleSave(){pending=true;clearTimeout(saveT);saveT=setTimeout(()=>{save()},400)}
async function save(){clearTimeout(saveT);saveT=0;if(!pending||!cfg)return;pending=false;
  try{const r=await api.saveConfig(cfg);if(info)info.config=r||cfg;const ci2=shelf("config");if(ci2&&typeof ci2==="object")ci2.config=clone(r||cfg)}catch(e){pending=true;toast(errMsg(e))}} // (beta.8: a failed save is tried again with the next change, not dropped)
function change(d){const r=curRow();if(!r)return;let n;
  if(r.t==="choice"||r.t==="theme"){const o=optsOf(r),i=idxOf(r,o),j=Math.max(0,Math.min(o.length-1,i+d));if(j===i&&eq(o[i][0],r.get()))return;n=o[j][0]}
  else if(r.t==="slider"){const v=+r.get();n=Math.round(Math.max(r.min,Math.min(r.max,v+d*r.step))/r.step)*r.step;n=+n.toFixed(4);if(eq(n,v))return}
  else if(r.t==="toggle"){n=d>0;if(n===!!r.get())return}
  else return;
  lastChange={r,d};r.set(n);play(r.t==="toggle"?"flip":"tick",d);changed(r)}
async function act(){const r=curRow();if(!r)return;
  const el=rowEls[rowI];if(el&&el!==gridEl){el.classList.remove("press");void el.offsetWidth;el.classList.add("press")}
  if(r.t==="toggle"){lastChange={r,d:1};r.set(!r.get());play("flip",1);changed(r)}
  // (beta.3) A on the theme tiles keeps the highlighted theme and puts it on the shelf now (◀ ▶ already choose; A used to
  // step on to the next theme, like a choice row)
  else if(r.t==="theme"){const o=optsOf(r),i=idxOf(r,o);if(!o.length)return;clearTimeout(themeT);shelf("setTheme",o[i][0]);play("open",1);toast("Theme: "+o[i][1])}
  else if(r.t==="choice"){const o=optsOf(r);if(!o.length)return;lastChange={r,d:1};r.set(o[(idxOf(r,o)+1)%o.length][0]);play("tick",1);changed(r)}
  else if(r.t==="slider")toast(device==="pad"?"Use ◀ ▶ to adjust":"Use ← → to adjust");
  else if(r.t==="consoles"){const c=tileList()[ci];if(c)openConsole(c.id)}
  else if(r.t==="result")goResult(r);
  else if(r.t==="text")startEdit(r);
  else if(r.t==="path"){const p=await pick({kind:r.kind==="file"?"file":"folder",title:r.pickTitle||r.label,filters:r.filters,defaultPath:r.get()||undefined});if(p){r.set(p);changed(r)}}
  else if(r.t==="order"){grab=grab?null:r.id;play("tick",1);render()}
  else if(r.t==="action"||r.t==="folder"){if(r.dim)return;try{const p=r.run&&r.run();if(p&&p.then)await p}catch(e){toast(errMsg(e))}if(!r.keepInfo)render()}}
function reset(){const c=curRow(),r=c&&(c.t==="result"?c.row:c);if(!r||r.d===undefined||!r.get||eq(r.get(),r.d))return;r.set(r.d);toast(`${r.label} back to default`);changed(r)}
async function pick(o){if(!api.pick){toast("Not available here");return null}try{return await api.pick(o)}catch(e){toast(errMsg(e));return null}}
function play(k,d){if(window.Sound&&Sound.play&&gp("ui.sound")!==false)try{Sound.play(k,{dir:d||1})}catch{}}
let sndT=0;function soundSoon(k){clearTimeout(sndT);sndT=setTimeout(()=>shelf("sound",{preview:k}),260)}

/* ---------- async bits: detection, services, system actions ---------- */
async function runDetect(){if(detecting||!api.detect)return;detecting=true;bump();const my=openId;
  try{const d0=await api.detect();
    // Settings closed (or reopened) while it searched: its config was already saved, so filling it in now would save a stale copy over newer changes
    if(my!==openId||!isOpen){detecting=false;return}
    det=d0;let n=0;
    for(const[id,d]of Object.entries(det.systems||{})){const c=cfg.systems&&cfg.systems[id];if(!c)continue;if(!c.emulator&&d.emulator){c.emulator=d.emulator;n++}if(!(c.romDirs||[]).length&&(d.romDirs||[]).length){c.romDirs=[...d.romDirs];n++}}
    if(cfg.steam&&!cfg.steam.path&&det.steam&&det.steam.path){cfg.steam.path=det.steam.path;n++}
    for(const[id,a]of Object.entries(det.apps||{}))if(cfg.apps&&cfg.apps[id]&&!cfg.apps[id].path&&a.path){cfg.apps[id].path=a.path;n++}
    toast(n?`Found ${n} new thing${n===1?"":"s"} and filled them in`:"Nothing new found");if(n)changed({rescan:true})}
  catch(e){toast(errMsg(e))}detecting=false;bump()}
async function loadStatus(){if(!api.apiStatus)return;try{const s=await api.apiStatus();if(s&&typeof s==="object")status=s}catch{}if(isOpen)bump()}
async function testSvc(s){await save();svcBusy[s]=true;bump();
  try{if(api.testApi)status[s]=await api.testApi(s);
    else status[s]={state:"fail",msg:"Testing isn't available here",at:Date.now()}}
  catch(e){status[s]={state:"fail",msg:errMsg(e),at:Date.now()}}
  svcBusy[s]=false;const st=svcState(s);toast(st.state==="ok"?(s==="ra"?"RetroAchievements connected":`${SVC_N[s]||s} works`+(st.left!=null?` · ${st.left} lookups left today`:"")):st.state==="none"?"Fill in the account first":"That didn't work: "+(st.msg||""));if(s==="ra")raChanged=true;bump()}
async function setAutostart(v){try{const r=await api.setAutostart(v);autostart={enabled:!!(r&&r.enabled!==undefined?r.enabled:v),how:r&&r.how};toast(v?"Boxshelf starts with Windows":"Removed from startup")}catch(e){toast(errMsg(e))}bump()}
// (1.82) the public beta page: newest release (asked once when the row first shows, if checks are on) and the bug form
let upd=null,updAsked=false;
function updBtn(){if(!upd&&!updAsked&&gp("ui.updateCheck")!==false&&api.updateCheck){updAsked=true;Promise.resolve(api.updateCheck(false)).then(r=>{upd=r;if(isOpen)renderPage()}).catch(()=>{})}
  return upd&&upd.newer?"Get "+upd.latest:upd&&!upd.error&&upd.latest?"Up to date":"Check now"}
async function checkUpdates(){if(upd&&upd.newer){openLink(upd.url);return}toast("Checking for a newer beta…");
  try{upd=await api.updateCheck(true);toast(upd.error?"Couldn't reach the beta page. Try again later":upd.newer?`Boxshelf ${upd.latest} is out: press again to get it`:upd.latest?"You have the newest beta":"No betas published yet");if(isOpen)renderPage()}catch(e){toast(errMsg(e))}}
async function reportOnline(){try{const r=await api.updateCheck(false);openLink(r&&r.links&&r.links.issues)}catch(e){toast(errMsg(e))}}
async function backup(){await save();try{const r=await api.backupSettings();if(r&&r.ok)toast("Saved: "+String(r.file||"").split(/[\\/]/).pop());else if(r&&r.error)toast(r.error)}catch(e){toast(errMsg(e))}}
async function restoreBackup(){await save();try{const r=await api.restoreSettings();if(r&&r.ok){const fresh=await api.getConfig();info=fresh;cfg=clone(fresh.config);pending=false;colRowsBy.clear();ADD.rows=null;clearTimeout(saveT);{const ci2=shelf("config");if(ci2&&typeof ci2==="object")ci2.config=clone(fresh.config)}listSig="";ensureCfg();build();needRescan=true;raChanged=true;applyTint();fit();document.body.classList.toggle("bss-reduce",gp("ui.reduceMotion")===true);{const u=cfg.ui;applyUi({safeArea:u.safeArea,clock:u.clock,clock24:u.clock24,reduceMotion:u.reduceMotion,confirmButton:u.confirmButton,scrollSpeed:u.scrollSpeed,audioOut:u.audioOut,dimMinutes:u.dimMinutes,attractMinutes:u.attractMinutes});
      // the theme / background previews set an override on the shelf that outlives the config: point them at the restored values
      shelf("setTheme",u.theme||"spectrum");shelf("setBackground",u.background||"blur");shelf("setButtons",u.buttons||"auto");shelf("setPerformance",u.performance||"auto")}toast("Settings restored");bump()}else if(r&&r.error)toast(r.error)}catch(e){toast(errMsg(e))}}
async function report(){await save();toast("Bundling the log…");try{const r=await api.reportProblem();toast(r&&r.ok?"Saved to your Desktop: "+String(r.file||"").split(/[\\/]/).pop():"Couldn't create it"+(r&&r.error?": "+r.error:""))}catch(e){toast(errMsg(e))}}
async function clearHistory(){try{const r=await api.clearHistory();toast(r&&r.ok===false?"Couldn't clear it":"Play history cleared");needRescan=true}catch(e){toast(errMsg(e))}}
async function customize(id){await close();const S=window.Shelf;
  if(S&&S.openCustomize){let ok=false;try{ok=S.openCustomize(id)}catch{}if(ok===false&&S.toast)S.toast("Customize opens from that console's shelf");return}
  try{dispatchEvent(new KeyboardEvent("keydown",{key:"t",bubbles:true}))}catch{}}
let readWhere=null,readOld=null,readCopy=null;
// the full path shown under each folder row: the folder you picked (saved or not yet), else where Boxshelf keeps them
function readFolder(k){const v=gp("ui.readingLibrary");if(v)return String(v).replace(/[\\/]+$/,"")+"\\"+k;if(readWhere)return k==="Magazines"?readWhere.magazines:readWhere.guides;return info&&info.appRoot?info.appRoot+"\\"+k:k}
// magazines left in an earlier Boxshelf folder (the owner moved his install): one row per folder, copy only
function readingOldRows(){if(!readOld||!readOld.length)return [];return [H("From an earlier Boxshelf folder"),...readOld.slice(0,3).map(o=>A(`Bring them over from ${o.root}`,readCopy&&readCopy.root===o.root?(readCopy.total?`${readCopy.done} / ${readCopy.total} · Stop`:"Copying… · Stop"):"Copy",`${o.magazines.files} magazine file${o.magazines.files===1?"":"s"} and ${o.guides.files} guide file${o.guides.files===1?"":"s"} (${Math.round(o.bytes/1048576)} MB) are in that folder. They're copied into your current Magazines and Guides folders; the old ones stay where they are, and nothing you already have is overwritten.`,()=>readingImport(o),{keys:"old install move migrate copy previous bring over"}))]}
// pressed again while it copies: stop (main finishes the file in hand; what was copied stays)
async function readingImport(o){if(readCopy){if(readCopy.root===o.root&&api.readingImportStop&&!readCopy.stop){readCopy.stop=true;toast("Stopping the copy…");try{await api.readingImportStop()}catch{}}return}if(!api.readingImport)return;readCopy={root:o.root,done:0,total:0};bump();
  try{const r=await api.readingImport(o.root)||{};toast(`${r.cancelled?"Stopped · c":"C"}opied ${r.copied||0} file${r.copied===1?"":"s"}${r.skipped?`, ${r.skipped} already there`:""}${r.failed?`, ${r.failed} couldn't be copied`:""}`);try{readOld=await api.readingOld()}catch{}}
  catch(e){toast(errMsg(e))}finally{readCopy=null;bump()}}
if(api.onReadingImport)api.onReadingImport(p=>{if(readCopy&&p){readCopy.done=p.done;readCopy.total=p.total;if(isOpen)bump()}});
function loadAsync(){
  if(api.readingWhere)Promise.resolve(api.readingWhere()).then(w=>{readWhere=w;bump()}).catch(()=>{});
  if(api.readingOld)Promise.resolve(api.readingOld()).then(l=>{readOld=Array.isArray(l)?l:[];bump()}).catch(()=>{});
  loadStatus();loadRaEmu();if(api.artStudio){loadStudio();if(studio)studioLoad()}if(api.manualStudio){msLoadInfo();if(studio&&MV[studio.v])studioLoad()}
  if(api.listDisplays)Promise.resolve(api.listDisplays()).then(l=>{if(!Array.isArray(l)||!l.length)return;const cur=l.find(d=>d.current)||l.find(d=>d.primary)||l[0];
    const prim=l.find(d=>d.primary)||l[0];displays=[["",`Automatic (${prim.label||"main display"})`,String(prim.id)],...l.map((d,i)=>[String(d.id),d.label||`Display ${i+1}${d.primary?" (main)":""}${d.width?` · ${d.width}×${d.height}`:""}`,String(d.id)])];bump()}).catch(()=>{});
  const ao=shelf("audioOutputs");if(ao&&ao.then)ao.then(l=>{outputs=[["","Windows default"],...(l||[]).filter(o=>o&&o.id&&o.id!=="default").map(o=>[o.id,o.label||"Output device"])];bump()}).catch(()=>{});
  if(api.getAutostart)Promise.resolve(api.getAutostart()).then(r=>{autostart=r||{enabled:false};bump()}).catch(()=>{});
  if(api.homeStatus)Promise.resolve(api.homeStatus()).then(r=>{homeApp=r||null;bump()}).catch(()=>{});
  if(api.companionState)Promise.resolve(api.companionState()).then(s=>{phone=s;bump()}).catch(()=>{});
  const h=shelf("hiddenList");Promise.resolve(h).then(l=>{hidden=Array.isArray(l)?l:[];bump()}).catch(()=>{});
}
function checkPathsSoon(){if(!pathAsk.size||!api.checkPaths)return;const L=[...pathAsk];pathAsk.clear();L.forEach(p=>pathOk[p]=pathOk[p]||null);
  api.checkPaths(L).then(res=>{(res||[]).forEach((x,i)=>{pathOk[L[i]]=x||{}});bump()}).catch(()=>{})}

/* ---------- open / close / the classic pages ---------- */
function ensureCfg(){cfg.ui=cfg.ui||{};cfg.steam=cfg.steam||{enabled:true,path:""};cfg.apps=cfg.apps||{};cfg.ra=cfg.ra||{user:"",key:"",enabled:true};cfg.keys=cfg.keys||{};cfg.systems=cfg.systems||{};(info.apps||[]).forEach(a=>{cfg.apps[a.id]=cfg.apps[a.id]||{enabled:true,path:""}})}
const PAGE_MAP={bottom:["display","Bottom screen"],phone:"phone",consoles:"consoles",steam:"pc",pc:"pc",epic:"pc",custom:"custom",quiver:"custom",media:"apps",apps:"apps",ra:{svc:"ra"},connections:["acct","fanart.tv"],screenscraper:{svc:"screenscraper"},steamgriddb:{svc:"steamgriddb"},acct:"acct",accounts:"acct",display:"display",perf:"perf",performance:"perf",sound:"sound",controller:"pad",pad:"pad",museum:"museum",shelf:"shelf",library:["art","Fetch manuals for new games"],art:"art",previews:"previews",videos:"previews",trailers:"previews",reading:["reading","Find on the Internet Archive"],magazines:"reading",guides:"reading",manuals:"art",startup:"sys",system:"sys",sys:"sys"};
// (beta.5) Details → Rename: one game's name, on a page of its own (typed, or with the on-screen keyboard on a controller).
// Saved by main (cfg.games[key].title) the moment Enter is pressed; "Use the file's name" puts the scanned one back
let renameState=null;
// (the rows are made once per page: the text editor finds its row by identity)
function renameRows(){const s=renameState;if(!s)return [];
  if(!s.nameRow)s.nameRow=X("Name","","The game's name on the shelf, its case and in searches. Box art and manuals are looked up by it too. The file itself isn't touched. Enter saves it.",{get:()=>s.name,set:v=>{s.name=String(v||"")},nosave:true,live:v=>renameSave(v),ns:true,keys:"rename name title"});
  if(!s.resetRow)s.resetRow=A("Use the file's name","Reset",`Back to "${s.file}".`,()=>renameSave(""),{ns:true,sub:()=>({text:s.file})});
  return s.renamed?[s.nameRow,s.resetRow]:[s.nameRow]}
async function renameSave(v){const s=renameState;if(!s||!api.renameGame)return;v=String(v||"").replace(/\s+/g," ").trim();const back=!v||v===s.file;
  try{await api.renameGame({...s.ref,title:back?"":v});s.renamed=!back;s.name=back?s.file:v;s.saved=s.name;play("tick",1);toast(back?"Back to its file name":`Renamed to ${v}`);shelf("refresh",true)} /* (true: the shelf stays on this game) */catch(e){if(s.saved!=null)s.name=s.saved;toast(errMsg(e));play("error")}bump()} // (beta.10) a failed rename shows the name it still has
// (beta.5) the cover picker's Search by name: a name typed here (keyboard, or the on-screen one on a controller); Enter
// closes Settings and the picker opens again with SteamGridDB's covers for that name
let coverSearch=null;
function coverSearchRows(){const s=coverSearch;if(!s)return [];
  if(!s.row)s.row=X("Search for","",`A name to look up on SteamGridDB instead of "${s.title}". Enter searches. The game's own name stays as it is (Rename changes that).`,{get:()=>s.term,set:v=>{s.term=String(v||"")},nosave:true,live:v=>coverSearchGo(v),ns:true,keys:"search cover name steamgriddb"});
  return [s.row]}
async function coverSearchGo(v){const s=coverSearch;v=String(v||"").replace(/\s+/g," ").trim();if(!s||!v)return;play("open");await close();shelf("pcCoverSearch",s.id,v)}
// (beta.7) Add games (main/addgames.js): what was found is listed with a tick each (a game already on the Custom shelf
// isn't added twice); Add puts the ticked ones on the shelf. Playnite (experimental): a summary to tick (PC games,
// each console's games folders, what's left out and why), Import, and Remove what was imported.
const pcCfg=()=>{cfg.pc=cfg.pc||{};if(!Array.isArray(cfg.pc.custom))cfg.pc.custom=[];return cfg.pc};
const ADD={list:null,pn:null,pnOn:{pc:true},pnPick:new Set(),busy:false,rows:null,pnFound:undefined};
const addSlug=s=>String(s||"").toLowerCase().replace(/[^a-z0-9]+/g,"-").replace(/^-|-$/g,"").slice(0,60)||"game";
function addReset(){ADD.rows=null;bump()}
function addFound(items){const have=new Set(pcCfg().custom.map(c=>String(c.exe).toLowerCase()));ADD.pn=null;
  ADD.list=items.map(x=>{const h=have.has(String(x.exe).toLowerCase());return {title:String(x.title||"Game").slice(0,120),exe:String(x.exe),on:!h,have:h}});
  if(!ADD.list.length)toast("No games found there");addReset()}
function addCommit(){const pc=pcCfg();let n=0;for(const x of (ADD.list||[]).filter(x=>x.on&&!x.have)){if(pc.custom.some(c=>String(c.exe).toLowerCase()===x.exe.toLowerCase()))continue;pc.custom.push({id:Date.now().toString(36)+"-"+n,title:x.title,exe:x.exe,args:""});n++}
  ADD.list=null;changed({rescan:true});play("open");toast(n?`Added ${n} game${n===1?"":"s"} to the Custom shelf`:"Nothing new to add");addReset()}
async function addSeveral(){const p=await pick({kind:"file",multi:true,title:"Choose the games' programs (hold Ctrl to pick several)",filters:[{name:"Programs",extensions:["exe"]}]});const L=(Array.isArray(p)?p:p?[p]:[]).map(x=>typeof x==="string"?x:x&&x.path).filter(Boolean);if(!L.length)return;
  const named=await Promise.resolve(api.gameTitles?api.gameTitles(L):L.map(e=>({exe:e,title:e.split(/[\\/]/).pop().replace(/\.exe$/i,"")}))).catch(()=>L.map(e=>({exe:e,title:e.split(/[\\/]/).pop().replace(/\.exe$/i,"")})));addFound(named)}
async function addScan(){const p=await pick({kind:"folder",title:"Choose the folder your games are in (like D:\\Games)"}),root=typeof p==="string"?p:p&&p.path;if(!root||!api.scanGameFolder)return;
  ADD.busy=true;ADD.list=null;addReset();try{const r=await api.scanGameFolder(root);if(r&&r.error)toast(r.error);addFound((r&&r.games)||[])}catch(e){toast(errMsg(e))}finally{ADD.busy=false;addReset()}}
async function addOne(){const p=await pick({kind:"file",title:"Choose the game's program",filters:[{name:"Programs",extensions:["exe"]}]}),exe=typeof p==="string"?p:p&&p.path;if(!exe)return;const pc=pcCfg();
  if(pc.custom.some(c=>String(c.exe).toLowerCase()===String(exe).toLowerCase()))return toast("That game is already on the Custom shelf");
  const title=String(exe).split(/[\\/]/).pop().replace(/\.[^.]+$/,"").replace(/[_\-.]+/g," ").replace(/\s+/g," ").trim()||"Game";
  pc.custom.push({id:Date.now().toString(36),title,exe,args:""});changed({rescan:true});toast(`Added ${title}. Rename it in Custom games if you like.`)}
async function addPlaynite(dir){ADD.busy=true;ADD.list=null;ADD.pn=null;addReset();
  try{const P=await api.playnitePlan(dir);ADD.pn=P;ADD.pnOn={pc:true};Object.keys(P.consoles||{}).forEach(k=>{ADD.pnOn[k]=true});ADD.pnPick=new Set(P.pc.filter(g=>g.there!==false).map(g=>g.key))}
  catch(e){toast(errMsg(e))}finally{ADD.busy=false;addReset()}}
async function pnImport(){const P=ADD.pn;if(!P||ADD.busy)return;ADD.busy=true;const pc=pcCfg(),have=new Set(pc.custom.map(c=>String(c.exe).toLowerCase())),covers=[];let n=0,dirs=0;
  try{
    if(ADD.pnOn.pc)for(const g of P.pc){if(!ADD.pnPick.has(g.key)||have.has(String(g.exe).toLowerCase()))continue;const id="pn-"+addSlug(g.key).slice(0,40);
      pc.custom.push({id,title:g.title,exe:g.exe,args:g.args||"",src:"playnite"});have.add(String(g.exe).toLowerCase());n++;if(g.cover)covers.push({id,file:g.cover});
      if(g.favorite){const fav=cfg.ui.favorites=Array.isArray(cfg.ui.favorites)?cfg.ui.favorites:[];const fid="custom-"+addSlug(id);if(!fav.includes(fid))fav.push(fid)}}
    pc.pnDirs=pc.pnDirs||{};
    for(const [sys,c] of Object.entries(P.consoles||{})){if(!ADD.pnOn[sys]||!cfg.systems[sys])continue;const sc=sysCfg(sys);
      for(const d of c.dirs||[])if(!sc.romDirs.some(x=>x.toLowerCase()===d.toLowerCase())){sc.romDirs.push(d);(pc.pnDirs[sys]=pc.pnDirs[sys]||[]).push(d);dirs++}
      sc.enabled=true;if(!sc.emulator&&c.emulatorDir&&api.emulatorIn){const e=await Promise.resolve(api.emulatorIn(c.emulatorDir,sys)).catch(()=>null);if(e)sc.emulator=e}}
    changed({rescan:true});await save();if(covers.length&&api.gameCovers)api.gameCovers(covers).catch(()=>{});
    play("open");toast(`From Playnite: ${n} game${n===1?"":"s"} on the Custom shelf${dirs?`, ${dirs} games folder${dirs===1?"":"s"} for your consoles`:""}`);ADD.pn=null}
  finally{ADD.busy=false;addReset()}}
function pnUndo(){const pc=pcCfg(),n=pc.custom.filter(c=>c.src==="playnite").length;pc.custom.splice(0,pc.custom.length,...pc.custom.filter(c=>c.src!=="playnite"));let d=0;
  for(const [sys,L] of Object.entries(pc.pnDirs||{})){if(!cfg.systems[sys])continue;const sc=sysCfg(sys),rm=new Set(L.map(x=>x.toLowerCase())),b=sc.romDirs.length;sc.romDirs=sc.romDirs.filter(x=>!rm.has(x.toLowerCase()));d+=b-sc.romDirs.length}
  pc.pnDirs={};changed({rescan:true});toast(`Removed ${n} imported game${n===1?"":"s"}${d?` and ${d} games folder${d===1?"":"s"}`:""}`);addReset()}
function addRows(){if(ADD.rows)return ADD.rows;const R=[],win=!window.bsPlatform||!bsPlatform.os||bsPlatform.os==="windows"||bsPlatform.os==="web";
  if(ADD.pnFound===undefined&&api.playniteFind&&win){ADD.pnFound=null;Promise.resolve(api.playniteFind()).then(f=>{ADD.pnFound=f||false;addReset()}).catch(()=>{ADD.pnFound=false;addReset()})}
  R.push(A("Add several programs","Choose…","Pick many games' programs at once (hold Ctrl or Shift in the file window). You check their names before they're added.",addSeveral,{ns:true,keys:"add several many bulk multiple games programs exe"}),
    A("Scan a folder","Choose folder…","Point at the folder your games are in (like D:\\Games). Each game folder in it becomes one game, its main program picked for you; you tick which ones to add.",addScan,{ns:true,keys:"scan folder games bulk find"}));
  if(api.playnitePlan&&win)R.push(A("Import from Playnite (experimental)",()=>ADD.pnFound?"Import…":ADD.pnFound===false?"Find…":"…",
    "Brings your Playnite library over in one go: games you added there by hand, with their covers, onto the Custom shelf, and your emulated games' folders onto their console's shelf. Steam, Epic, GOG Galaxy and other store games are left out: Boxshelf reads those stores itself. Experimental: it couldn't be tried with every Playnite version, so tell us if something's missing.",
    async()=>{if(ADD.pnFound)return addPlaynite();const p=await pick({kind:"folder",title:"Choose Playnite's folder (the one with Playnite.DesktopApp.exe)"}),d=typeof p==="string"?p:p&&p.path;if(d)addPlaynite(d)},
    {ns:true,keys:"playnite import library experimental",sub:()=>ADD.pnFound?{text:"Found in "+folderName(ADD.pnFound.app)}:ADD.pnFound===false?{text:"Not found · choose its folder"}:{text:"Looking for Playnite…"}}));
  R.push(A("Add one program","Browse…","Any program: a recomp, a PC port, a game from another launcher. It gets a case on the Custom shelf and starts from its own folder.",addOne,{ns:true,keys:"add custom game recomp port program exe one"}));
  if(ADD.busy)R.push(infoRow("Looking…","",{text:""}));
  if(ADD.list&&ADD.list.length){const L=ADD.list;R.push(H(`Found ${L.length} game${L.length===1?"":"s"}`,{em:()=>`${L.filter(x=>x.on&&!x.have).length} ticked`}));
    L.forEach(x=>R.push({t:"toggle",label:x.title,info:x.exe,ns:true,nosave:true,get:()=>x.on&&!x.have,set:v=>{if(!x.have)x.on=!!v},sub:()=>({text:x.have?"Already on the Custom shelf":x.exe.split(/[\\/]/).pop()})}));
    R.push(A("Add the ticked games",()=>{const n=L.filter(x=>x.on&&!x.have).length;return n?`Add ${n}`:"Add"},"Puts the ticked games on the Custom shelf. Covers are found for them like for any PC game, and each can be renamed or given another cover from its Details.",addCommit,{ns:true}),
      A("Clear this list","Clear","Forgets this list without adding anything.",()=>{ADD.list=null;addReset()},{ns:true}))}
  if(ADD.pn){const P=ADD.pn,name=id=>((consoleList().find(c=>c.id===id)||{}).name||id);R.push(H("From Playnite",{em:()=>`${P.total} games in Playnite`}));
    R.push({t:"toggle",label:"PC games",info:"Games you added to Playnite by hand: each gets a case on the Custom shelf with its program, launch options and cover.",ns:true,nosave:true,get:()=>!!ADD.pnOn.pc,set:v=>{ADD.pnOn.pc=!!v;addReset()},sub:()=>({text:`${P.pc.length} game${P.pc.length===1?"":"s"}`})});
    for(const [sys,c] of Object.entries(P.consoles||{}))R.push({t:"toggle",label:`${name(sys)} games`,info:`Adds ${c.dirs.join(", ")} as ${name(sys)} games folders${c.emulatorDir?`, and Playnite's emulator if ${name(sys)} has none yet`:""}.`,ns:true,nosave:true,get:()=>!!ADD.pnOn[sys],set:v=>{ADD.pnOn[sys]=!!v},sub:()=>({text:`${c.count} game${c.count===1?"":"s"} · ${c.dirs.length} folder${c.dirs.length===1?"":"s"}`})});
    const s=P.skipped||{},left=[s.store?`${s.store} store game${s.store===1?"":"s"} (Boxshelf reads those stores itself)`:"",s.hidden?`${s.hidden} hidden`:"",s.link?`${s.link} started by a link`:"",s.nothing?`${s.nothing} with nothing to start`:""].filter(Boolean);
    if(left.length)R.push(infoRow("Left out","",{text:left.join(" · ")}));
    R.push(A("Import",()=>`Import`,"Adds what's ticked above. Remove what was imported (below) takes it all away again.",pnImport,{ns:true}));
    if(ADD.pnOn.pc&&P.pc.length){R.push(H("Its PC games"));P.pc.forEach(g=>R.push({t:"toggle",label:g.title,info:g.exe,ns:true,nosave:true,get:()=>ADD.pnPick.has(g.key),set:v=>{v?ADD.pnPick.add(g.key):ADD.pnPick.delete(g.key)},sub:()=>({text:g.there===false?"Its program isn't on this PC":g.exe.split(/[\\/]/).pop(),cls:g.there===false?"warn":""})}))}}
  if(pcCfg().custom.some(c=>c.src==="playnite"))R.push(H(""),A("Remove what was imported from Playnite","Remove","Takes the games and games folders the Playnite import added off your shelves again (nothing is deleted from your PC).",()=>twice("pnundo",pnUndo,"Press again to remove them"),{ns:true,ask:"pnundo"}));
  return ADD.rows=R}
// (beta.7) What's new: the newest version on GitHub (its own notes, as plain text, and its download page) when it's newer
// than this one, then each built-in version (renderer/whatsnew.js), newest first: a row per change, more in the side card
const wnList=()=>Array.isArray(window.BS_WHATSNEW)?window.BS_WHATSNEW:[];
const wnFor=v=>wnList().find(e=>window.bsPlatform&&bsPlatform.os==="android"?e.android===v:e.version===v)||null;
function wnPlain(md){return String(md||"").split(/\r?\n/).map(l=>/^\s*[-*•]\s+(.+)$/.exec(l)).filter(Boolean).map(m=>m[1].replace(/\[([^\]]+)\]\([^)]*\)/g,"$1").replace(/\*\*|__|`/g,"").replace(/<[^>]*>/g,"").trim()).filter(Boolean)}
let wnRowsKept=null,wnSig="";
function wnRows(){updBtn();const sig=(upd&&upd.latest||"")+"|"+(info.version||"");if(wnRowsKept&&wnSig===sig)return wnRowsKept;wnSig=sig;const R=[],android=window.bsPlatform&&bsPlatform.os==="android";
  if(upd&&upd.newer&&upd.latest){R.push(H(`New: Boxshelf ${upd.latest}`,{em:()=>"not installed yet"}));
    const items=wnPlain(upd.notes);items.slice(0,12).forEach(s=>R.push(infoRow(s.length>70?s.slice(0,67)+"…":s,"",{text:""},{info:s})));
    R.push(A("Get Boxshelf "+upd.latest,"Download","Opens its download page. Unzip it over this folder: your settings are kept.",checkUpdates,{ns:true}))}
  const cur=info.version||"";let shown=0;
  for(const e of wnList()){const v=android?e.android:e.version;if(!v)continue;if(shown>=4)break;shown++;
    R.push(H(`${v===cur?"This version":"Boxshelf"} ${v}`,{em:()=>e.date||""}));
    for(const sec of e.sections||[])for(const [h,more] of sec.items||[])R.push(infoRow(h,"",{text:sec.h},{info:more||h}))}
  if(!R.length)R.push(infoRow("No notes yet","",{text:""}));
  return wnRowsKept=R}
// (beta.7) Your preview videos: how many games of each console have a video, your own videos folders (add / remove),
// Find videos again, and Steam's trailer clips (their size, Clear). Rows kept until the answer changes.
// (beta.8) console trailers (main/ytclips.js): the helper's state for the Video previews section. clipOk: this PC can
// make clips (Windows). clipRows: "Get the helper" (with its progress) until it's there, then what's kept and Clear.
const CLIP={s:null,loading:false,pct:null,err:false,hooked:false};
function clipLoad(force){if(!api.clipStatus||(CLIP.loading&&!force))return;if(CLIP.s&&!force)return;CLIP.loading=true;
  if(!CLIP.hooked&&api.onClipProgress){CLIP.hooked=true;api.onClipProgress(d=>{if(!d)return;if(d.done){CLIP.pct=null;CLIP.err=!d.ok;clipLoad(true)}else CLIP.pct=d.pct|0;bump()})}
  Promise.resolve(api.clipStatus()).then(s=>{CLIP.s=s||{supported:false}}).catch(()=>{CLIP.s={supported:false}}) /* (never null again: a failed ask would ask again on every repaint) */.finally(()=>{CLIP.loading=false;bump()})}
const clipOk=()=>{clipLoad();return !!(CLIP.s&&CLIP.s.supported)};
// console trailers are on but can't work yet: the helper is missing (the section's dot, the switch's note)
const clipNeed=()=>gp("ui.previews")!=="off"&&gp("ui.previewClips")!==false&&clipOk()&&!CLIP.s.helper;
async function clipGet(){if(CLIP.pct!=null)return;CLIP.pct=0;CLIP.err=false;bump();const ok=await api.clipHelper().catch(()=>false);CLIP.pct=null;CLIP.err=!ok;clipLoad(true);
  toast(ok?"Console trailers are ready: rest on a console game to see its trailer":"Couldn't get the trailer helper. Check the internet connection and try again.")}
function clipRows(){if(gp("ui.previews")==="off"||gp("ui.previewClips")===false||!clipOk())return [];const s=CLIP.s;
  if(!s.helper)return [A(CLIP.pct!=null?`Getting the trailer helper… ${CLIP.pct}%`:"Get the trailer helper (needed)",CLIP.pct!=null?"":"Get",
    "Console trailers don't play until you get this. It's a free helper, yt-dlp (about 25 MB), that finds each trailer's video. It's fetched once from yt-dlp's own GitHub page, checked, and kept in Boxshelf's data folder; it updates itself when YouTube changes.",
    ()=>clipGet(),{ns:true,keys:"yt-dlp helper console trailers download",sub:()=>CLIP.err?{text:"Couldn't get it last time: try again",warn:true}:{text:"Not installed yet: console trailers are waiting for it",warn:true}})];
  return [A("Console trailer clips","Clear",`The trailer helper is ready. ${s.n?`${s.n} clip${s.n===1?"":"s"} kept (${mb(s.bytes||0)} MB).`:"No clips kept yet: rest on a console game."} Cleared clips are made again when you next rest on a game.`,
    async()=>{await api.clipClear();clipLoad(true);toast("Console trailer clips cleared")},{ns:true,keys:"console trailers clips clear",sub:()=>({text:s.n?`${s.n} clip${s.n===1?"":"s"} · ${mb(s.bytes||0)} MB`:"Ready"})})]}
// (beta.8) Linux / Steam Deck: Add Boxshelf to Steam (main/steamdeck.js), so it starts from Steam and Game Mode and its
// games get the controller as a gamepad. Shown where it can work: the AppImage, outside Game Mode (it's in Steam there)
const DECK={d:null,loading:false,busy:false};
function deckLoad(force){if(!api.deckInfo||DECK.loading||(DECK.d&&!force))return;DECK.loading=true;
  Promise.resolve(api.deckInfo()).then(d=>{DECK.d=d||{}}).catch(()=>{DECK.d={}}).finally(()=>{DECK.loading=false;bump()})}
function deckRows(){deckLoad();const d=DECK.d;if(!d||!d.canAdd||d.gameMode)return [];
  return [A(DECK.busy?"Adding Boxshelf to Steam…":"Add Boxshelf to Steam",DECK.busy?"":d.inSteam?"Update":"Add",
    "Adds Boxshelf to Steam's Library as a non-Steam game, so you can start it from Steam and Game Mode. Started from Steam, the controller works as a gamepad in your games (outside Steam on a Steam Deck, B is the Esc key, and most emulators quit their game on it). Steam closes and opens again while it's added; its old list is backed up first.",
    async()=>{if(DECK.busy)return;DECK.busy=true;bump();let r=null;try{r=await api.addToSteam(true)}catch{}DECK.busy=false;deckLoad(true);
      const why={"no-steam":"Steam hasn't been set up here yet","not-appimage":"Boxshelf isn't running from its AppImage","steam-would-not-close":"Steam didn't close: close it yourself and try again","write-failed":"Steam's list couldn't be written"};
      toast(r&&r.ok?"Boxshelf is in Steam: start it from your Library (Non-Steam), in Game Mode on a Steam Deck":"Couldn't add Boxshelf to Steam"+(r&&why[r.why]?": "+why[r.why]:""))},
    {ns:true,keys:"steam deck game mode non-steam shortcut add to steam library controller",sub:()=>({text:d.inSteam?"In Steam already":d.deck?"Recommended on a Steam Deck":"Not in Steam yet"})})]}const PVS={c:null,loading:false,rows:null};
function pvLoad(force){if(force){PVS.c=null;PVS.rows=null}if(PVS.c||PVS.loading||!api.previewCount)return;PVS.loading=true;
  Promise.resolve(api.previewCount()).then(c=>{PVS.c=c||{total:0,systems:[]}}).catch(()=>{PVS.c={total:0,systems:[]}}).finally(()=>{PVS.loading=false;PVS.rows=null;bump()})}
function pvRows(){pvLoad();if(PVS.rows)return PVS.rows;const c=PVS.c,R=[];if(!c)return [infoRow("Looking for videos…","")];
  R.push(H("Found",{em:()=>c.total?`${c.total} game${c.total===1?"":"s"}`:""}));
  if(!c.systems.length)R.push(infoRow("No video files yet","",{text:"Steam games still get their trailers, and YouTube's if it's on."}));
  c.systems.forEach(s=>R.push(infoRow(s.name,`${s.n} of ${s.of}`,{text:"games with a video"})));
  if(!(window.bsPlatform&&bsPlatform.os==="android")){ // (Android: the videos beside the games only)
  R.push(H("Your videos folders"));
  (gp("ui.previewDirs")||[]).forEach((d,i)=>R.push({t:"folder",label:"Videos folder",info:"Videos in here (or in a subfolder named after the console) are matched to your games. Select it twice to stop using it (nothing is deleted).",more:d,short:folderName,get:()=>d,ns:true,
    run:()=>twice("pvd:"+d,()=>{const L=(gp("ui.previewDirs")||[]).slice(),k=L.indexOf(d);if(k>=0)L.splice(k,1);sp("ui.previewDirs",L);changed(null);pvLoad(true);bump()},"Press again to stop using this folder"),ask:"pvd:"+d,sub:()=>pathSub(d,"dir")})); // (beta.10) keyed by the folder, not its place
  R.push(A("Add a videos folder","Add","A folder with game videos (named like the games, e.g. Super Mario 64 (USA).mp4), or with a folder per console.",async()=>{const p=await pick({kind:"folder",title:"Choose a folder with game videos"});
      if(p){const L=(gp("ui.previewDirs")||[]).slice();if(!L.includes(p))L.push(p);sp("ui.previewDirs",L);changed(null);await save();pvLoad(true);bump()}},{ns:true}),
    A("Find videos again","Find","Looks through every videos folder again (after you've added videos).",async()=>{await save();pvLoad(true);bump()},{ns:true}));
  } // (end of the Windows / Linux videos folders)
  if(api.previewClearSteam)R.push(H("Steam trailers"),A("Clear Steam trailer clips","Clear",`The short clips kept from Steam's trailers (${mb(c.steamBytes||0)} MB). They're fetched again the next time you rest on a game.`,async()=>{await api.previewClearSteam();pvLoad(true);toast("Steam trailer clips cleared");bump()},{ns:true,when:()=>(c.steamBytes||0)>0}));
  return PVS.rows=R}
// (beta.7) Set up for me (main/emusetup.js), one page per console: a switch per kind of change, the graphics preset, Set
// up, Show what will change, Undo. The switches are this page's (not saved); the preset is cfg.emuSetup.graphics. Rows are
// kept per console until the answer changes (the editor finds its row by identity).
const ES_NAME={controller:"Controller",hotkeys:"Home menu hotkeys",bios:"BIOS folder",graphics:"Graphics",quit:"No quit questions",resume:"Resume where you left off"};
const ES_INFO={controller:"Maps your controller as player 1, when the emulator is still on keyboard-only controls. A mapping you made yourself is kept.",
  hotkeys:"Puts save, load, pause and screenshot on the keys the Home menu presses, so its Save and Load work in your games.",
  bios:"Points the emulator at one shared BIOS folder (Boxshelf\\BIOS), only when its own BIOS folder is empty.",
  graphics:"Sets the resolution to the Graphics preset below.",quit:"No \"are you sure?\" question when a game is closed.",
  resume:"RetroArch saves when you quit a game and carries on from there next time."};
const ES_DEF={controller:true,hotkeys:true,bios:true,graphics:true,quit:true,resume:false};
const ES_KINDS=/retroarch|dolphin|duckstation|pcsx2|ppsspp/i;
const esCan=c=>!!(c&&c.emulator&&api.emuSetupStatus&&ES_KINDS.test(String(c.emulator).split(/[\\/]/).pop())&&(!window.bsPlatform||!bsPlatform.has||bsPlatform.has("emuSettings")));
const esS={};
function esGet(id){const s=esS[id]||(esS[id]={st:null,on:{...ES_DEF},plan:null,rows:null,busy:false,loading:false});const exe=(sysCfg(id)||{}).emulator||"";if(s.exe!==exe){s.exe=exe;s.st=null;s.plan=null;s.rows=null}
  if(s.st==null&&!s.loading){s.loading=true;Promise.resolve(api.emuSetupStatus(id)).then(st=>{s.st=st||{supported:false}}).catch(()=>{s.st={supported:false}}).finally(()=>{s.loading=false;s.rows=null;bump()})}
  return s}
function esReset(id){const s=esS[id];if(s){s.st=null;s.plan=null;s.rows=null}}
if(api.onCoreProgress)api.onCoreProgress(d=>{const s=d&&esS[d.systemId];if(s&&s.corePct!=null){s.corePct=d.pct|0;bump()}});
function esSub(id){const s=esS[id],st=s&&s.st;if(!st)return {text:"Checking…"};if(!st.supported)return {text:st.why||"Not for this emulator yet"};
  return st.done?{text:"✓ Set up"}:!st.ready?{text:st.pending?"Sets up after you play once":`Open ${st.name} once first`,cls:"warn"}:{text:"Not set up yet"}}
function emuSetupRows(id){const s=esGet(id);if(s.rows)return s.rows;const st=s.st,R=[];
  if(!st)return [infoRow("Checking the emulator…","")]; // (not kept: replaced when the answer comes)
  if(!st.supported){R.push(infoRow(st.none?"Choose this console's emulator first":"Boxshelf can't set this emulator up yet","",{text:st.why||"RetroArch, Dolphin, DuckStation, PCSX2 and PPSSPP for now."}));return s.rows=R}
  R.push(infoRow(!st.ready?`Open ${st.name} once first`:st.done?`${st.name} is set up`:st.used?`You've set ${st.name} up yourself`:`${st.name} isn't set up yet`,"",
    {text:!st.ready?(st.pending?"It's set up the next time you play.":`${st.name} makes its settings file the first time it runs; set it up after that.`)
      :st.done?`Since ${new Date(st.done.at).toLocaleDateString()}: ${st.done.groups.map(g=>ES_NAME[g]||g).join(", ")}.`
      :st.used?"Your settings stay as they are unless you set it up here. Everything it changes is backed up first.":"Everything it changes is backed up first, and Undo puts it back."}));
  for(const g of st.groups||[])R.push({t:"toggle",label:ES_NAME[g]||g,info:ES_INFO[g]||"",ns:true,nosave:true,get:()=>!!s.on[g],set:v=>{s.on[g]=!!v;if(s.plan){s.plan=null;s.rows=null}}});
  if((st.groups||[]).includes("graphics"))R.push(C("Graphics preset","emuSetup.graphics",[["auto","For this PC"],["fast","Fast"],["balanced","Balanced"],["pretty","Pretty"]],"auto",
    `Fast: the console's own resolution. Balanced: about twice that. Pretty: three to four times, for a strong graphics card. For this PC picks one from your graphics card${st.preset?` (${st.preset[0].toUpperCase()+st.preset.slice(1)} here)`:""}.`,{ns:true}));
  for(const k of st.skipped||[])R.push(infoRow(`${ES_NAME[k.group]||k.group}: left as it is`,"",{text:k.why}));
  R.push(H(""),
    A(st.done?"Set up again":`Set up ${st.name}`,st.done?"Again":"Set up",`Applies the switches above to ${st.name}. Your files are backed up first.`,async()=>{
      if(s.busy)return;s.busy=true;try{await save();const r=await api.emuSetupApply(id,s.on);
        toast(r&&r.ok?((r.applied||[]).length?`${st.name} set up: ${r.applied.map(g=>ES_NAME[g]||g).join(", ")}`:`${st.name} already matches`):`Couldn't set up ${st.name}: ${(r&&r.why)||"unknown problem"}`)}
      catch(e){toast(errMsg(e))}finally{s.busy=false;esReset(id);bump()}},{when:()=>!!st.ready,ns:true}),
    A("Show what will change","Show","Lists every setting it would write, file by file, before you set anything up.",async()=>{try{s.plan=await api.emuSetupPlan(id)}catch(e){toast(errMsg(e));return}s.rows=null;bump()},{when:()=>!!st.ready&&!s.plan,ns:true}),
    A("Undo","Undo",`Puts ${st.name}'s settings back exactly as they were before Boxshelf set it up.`,()=>twice("esundo:"+id,async()=>{try{const r=await api.emuSetupUndo(id);toast(r&&r.restored&&r.restored.length?`${st.name}'s settings are back as they were`:"Nothing to undo")}catch(e){toast(errMsg(e))}esReset(id);bump()},"Press again to undo"),{when:()=>!!st.done,ns:true,ask:"esundo:"+id}));
  // (beta.7) RetroArch's core for this console: there or not, Get it (it's also fetched by itself the first time you play)
  if(st.core)R.push(H("RetroArch core"),A(`${st.core.name} core`,st.core.installed?"":"Get it",
    st.core.installed?`RetroArch has the ${st.core.name} core it uses for this console.`:`Downloads the ${st.core.name} core from libretro, the same place RetroArch's Online Updater uses. It's also fetched by itself the first time you play.`,
    async()=>{if(st.core.installed||s.corePct!=null||!api.getCore)return;s.corePct=0;bump();
      try{const r=await api.getCore(id);toast(`${r&&r.name||st.core.name} core is ready`)}catch(e){toast(errMsg(e))}finally{s.corePct=null;esReset(id);bump()}},
    {ns:true,sub:()=>s.corePct!=null?{text:`Downloading… ${s.corePct}%`}:st.core.installed?{text:"✓ Ready"}:{text:"Not downloaded yet",cls:"warn"}}));
  // the BIOS files: each one the core wants, checked against the known good copies; the folder they go in, one press away
  if(st.bios){R.push(H("BIOS files"));
    const miss=f=>st.bios.need==="one"?"Missing (one of these is enough)":st.bios.need==="opt"?`Missing, optional: ${st.bios.note}`:`Missing${st.bios.note?` (${st.bios.note})`:""}`;
    if(st.bios.files.length)st.bios.files.forEach(f=>R.push(infoRow(f.name+(f.region?` · ${f.region}`:""),"",{text:f.state==="ok"?"✓ Good":f.state==="wrong"?"Wrong version: not the known good copy":miss(f),cls:f.state==="ok"||(f.state==="missing"&&(st.bios.need==="opt"||(st.bios.need==="one"&&st.bios.ok)))?"":"warn"})));
    else R.push(infoRow(st.bios.ok?"BIOS files found":"No BIOS files yet","",{text:st.bios.ok?"✓ Good":`${st.name} needs a BIOS dumped from your own console.`,cls:st.bios.ok?"":"warn"}));
    R.push(A("Open the BIOS folder","Open",`Opens the folder ${st.name} reads BIOS files from${st.bios.dir?`: ${st.bios.dir}`:""}. Copy the files from your own console there, then Check again.`,async()=>{try{await api.openBiosFolder(id)}catch(e){toast(errMsg(e))}},{ns:true,when:()=>!!api.openBiosFolder}),
      A("Check again","Check","Looks in the BIOS folder again.",()=>{esReset(id);bump()},{ns:true}))}
  if(s.plan&&s.plan.changes){const ch=s.plan.changes.filter(c=>s.on[c.group]);R.push(H("What will change"));
    if(!ch.length)R.push(infoRow("Nothing, with these switches","",{text:""}));
    ch.slice(0,60).forEach(c=>R.push(infoRow(`${c.key} = ${c.value}`,"",{text:`${ES_NAME[c.group]||c.group} · ${String(c.file).split(/[\\/]/).pop()}`})));
    if(ch.length>60)R.push(infoRow(`…and ${ch.length-60} more`,""))}
  return s.rows=R}
// (beta.7) Details → Collections: this game on your own shelves. cfg.ui.collections: [{ id, name, ids: [game ids] }]; each is
// a shelf of its own beside Home (app.js buildHome). A switch per collection; New collection makes one with this game on it;
// Settings → Shelf → Collections renames and deletes them. (Rows made once per page: the editor finds its row by identity)
let colState=null;const colRowsBy=new Map();
const colList=()=>{cfg.ui=cfg.ui||{};if(!Array.isArray(cfg.ui.collections))cfg.ui.collections=[];return cfg.ui.collections};
const colChanged=()=>{shelf("setCollections",colList().map(c=>({id:c.id,name:c.name,ids:[...c.ids]})))};
function colRows(){const s=colState;if(!s)return [];const out=[];
  for(const c of colList()){let r=colRowsBy.get("in|"+c.id+"|"+s.gid);
    if(!r){r=T(c.name,"",false,`On: ${s.title} is on this shelf.`,{get:()=>c.ids.includes(s.gid),set:v=>{const i=c.ids.indexOf(s.gid);if(v&&i<0)c.ids.push(s.gid);if(!v&&i>=0)c.ids.splice(i,1);colChanged()},keys:"collection shelf list"});colRowsBy.set("in|"+c.id+"|"+s.gid,r)}
    r.label=c.name;out.push(r)}
  if(!s.newRow)s.newRow=X("New collection","","A name for a new shelf of your own. Enter makes it, with this game on it.",{get:()=>s.newName,set:v=>{s.newName=String(v||"")},nosave:true,live:v=>colNew(v),ns:true,ph:"Couch co-op, Racing, Finished…",keys:"collection new create shelf"});
  out.push(s.newRow);return out}
function colNew(v){const s=colState;v=String(v||"").replace(/\s+/g," ").trim().slice(0,40);if(!s||!v)return;const L=colList();if(L.length>=50){toast("That's the most collections there can be (50).");return}
  const ex=L.find(c=>c.name.toLowerCase()===v.toLowerCase());if(ex){if(!ex.ids.includes(s.gid))ex.ids.push(s.gid)}else L.push({id:"c"+Date.now().toString(36),name:v,ids:[s.gid]});
  s.newName="";scheduleSave();colChanged();play("tick",1);toast(`${s.title} is on ${v}`);bump()}
// Settings → Shelf → Collections: each one's name (Enter renames) and Delete (pressed twice)
function colManageRows(){const L=colList();if(!L.length){if(!colRowsBy.has("none"))colRowsBy.set("none",A("No collections yet","","Open a game's Details and choose Collections to make one.",()=>{},{ns:true,keys:"collections"}));return [colRowsBy.get("none")]}const out=[];
  for(const c of L){let n=colRowsBy.get("name|"+c.id);
    if(!n){n=X("Name","",`The shelf's name (${c.ids.length} game${c.ids.length===1?"":"s"}). Enter renames it.`,{get:()=>c.name,set:v=>{const t=String(v||"").replace(/\s+/g," ").trim().slice(0,40);if(t)c.name=t},live:()=>colChanged(),keys:"collection rename"});colRowsBy.set("name|"+c.id,n)}
    let d=colRowsBy.get("del|"+c.id);
    if(!d){d=A("Delete "+c.name,"Delete","Removes this shelf (the games stay everywhere else). Press twice.",()=>{if(d.armed){const i=L.indexOf(c);if(i>=0)L.splice(i,1);scheduleSave();colChanged();play("back");toast(`${c.name} deleted`)}else{d.armed=true;d.btn="Press again";setTimeout(()=>{d.armed=false;d.btn="Delete";bump()},4000)}bump()},{ns:true,keys:"collection delete remove"});colRowsBy.set("del|"+c.id,d)}
    d.label="Delete "+c.name;out.push(H(c.name),n,d)}
  return out}
function route(page,o){if(!page)return;o=o||{};
  if(page==="addgames"){MORE.addgames=MORE.addgames||{id:"addgames",name:"Add games",lead:"Every way to put games on the Custom shelf. You see what was found before anything is added.",rows:[{t:"dyn",fn:addRows}]};goSecId("custom");focus="page";rowI=0;openSub({kind:"more",id:"addgames"});return} // (beta.7) the Custom shelf's Add games tile
  if(page==="whatsnew"){goSecId("sys");focus="page";rowI=0;openSub({kind:"more",id:"whatsnew"});return} // (beta.7) the note card's See what's new / All the notes
  if(page==="collections"&&o.id!=null){colState={gid:String(o.id),title:String(o.title||"This game"),newName:""};
    MORE.collections={id:"collections",name:`Collections: ${colState.title}`.slice(0,60),lead:"Put this game on shelves of your own. Each collection is a shelf beside Home.",rows:[{t:"dyn",fn:colRows}]};
    goSecId("shelf");focus="page";rowI=0;openSub({kind:"more",id:"collections"});return}
  if(page==="coversearch"&&o.id){coverSearch={id:String(o.id),title:String(o.title||""),term:String(o.term||o.title||"")};
    MORE.coversearch={id:"coversearch",name:"Search for a cover",lead:`Covers for ${coverSearch.title} under another name.`,rows:[{t:"dyn",fn:coverSearchRows}]};
    goSecId(/^(steam|epic)-/.test(coverSearch.id)?"pc":"custom");focus="page";rowI=0;openSub({kind:"more",id:"coversearch"});return}
  // (beta.9) any page of its own by its id (Android's Ports & custom APKs, Video previews…): opened in the section that holds it
  if(MORE[page]&&!PAGE_MAP[page]){const s=SECTIONS.find(x=>x&&Array.isArray(x.rows)&&x.rows.some(r=>r&&r.pg===page));if(s){goSecId(s.id);focus="page";rowI=0;openSub({kind:"more",id:page});return}}
  if(page==="rename"&&o.ref){const file=String(o.fileTitle||o.title||""),cur=String(o.title||"");renameState={ref:o.ref,name:cur,saved:cur,file,renamed:!!o.fileTitle&&o.fileTitle!==cur};
    MORE.rename={id:"rename",name:`Rename ${cur}`.slice(0,60),lead:o.fileTitle&&o.fileTitle!==cur?`Its file's name: ${o.fileTitle}`:"Give this game the name you want on your shelf.",rows:[{t:"dyn",fn:renameRows}]};
    goSecId(o.ref.systemId?"consoles":(/^(steam|epic)-/.test(String(o.ref.id||""))?"pc":"custom"));focus="page";rowI=0;openSub({kind:"more",id:"rename"});return}
  // Details → Box art: straight to that game's mix (B goes up to its console's games, then the Art page)
  // Details → Choose manual…: straight to that game's manuals (B: the Manual row, then its console's games, then the Art page)
  if(page==="manualgame"||page==="manualstudio"){goSecId("art");focus="page";rowI=0;if(!api.manualStudio||!o.systemId)return;inMore("art-manuals");sRet=-1;stack=[];
    if(page==="manualgame"&&o.file){stack=[{v:"mgames",sys:o.systemId,i:0,y:0,zone:"grid"}];studio={v:"mpick",sys:o.systemId,file:o.file,title:o.title||"",i:0,y:0}}else studio={v:"mgames",sys:o.systemId,i:0,y:0};sSig="";return}
  if(page==="artgame"||page==="artstudio"){goSecId("art");focus="page";rowI=0;if(!api.artStudio||!o.systemId)return;inMore("art-studio");sRet=-1;stack=[];
    if(page==="artgame"&&o.file){stack=[{v:"games",sys:o.systemId,i:0,y:0,zone:"grid"}];studio={v:"pick",sys:o.systemId,file:o.file,title:o.title||"",part:"front",i:-1,y:0}}
    else studio={v:"games",sys:o.systemId,i:0,y:0};sSig="";return}
  if(consoleList().some(c=>c.id===page)){goSecId("consoles");ci=tileList().findIndex(c=>c.id===page);subRet=0;sub={kind:"console",id:page};rowI=0;focus="page";if(page==="gc"&&api.findIpl&&iplCap())api.findIpl("gc").then(r=>{ipl=r;bump()}).catch(()=>{});return}
  const m=PAGE_MAP[page];if(!m)return;if(m.svc){goSecId("acct");focus="page";rowI=0;subRet=Math.max(0,rowsOf(sec()).findIndex(r=>r.status===m.svc));sub={kind:"svc",id:m.svc};return}const [id,label]=Array.isArray(m)?m:[m];goSecId(id);focus="page";rowI=0;if(label){const is=r=>r.label===label||r.was===label; /* (was: a row a platform renamed keeps its old name for links) */let k=rowsOf(sec()).findIndex(is);
    if(k<0){const pg=rowsOf(sec()).map(r=>r.pg).filter(Boolean).find(g=>rowsOf(expand(MORE[g])).some(is));if(pg){inMore(pg);k=rowsOf(sec()).findIndex(is)}} // a row that lives on one of the section's sub-pages
    if(k<0){const j=SECTIONS.findIndex(s=>rowsOf(expand(s)).some(is));if(j>=0){goSec(j);focus="page";k=rowsOf(sec()).findIndex(is)}} // (a row a platform moved to another section)
    if(k>=0)rowI=k}}
// a deep link into one of the current section's sub-pages: B comes back to the row that opens it
function inMore(id){const at=rowsOf(sec()).findIndex(r=>r.pg===id);if(at<0)return;subRet=at;sub={kind:"more",id};rowI=0}
function prep(inf,opts){opts=opts||{};ensureDom();openId++;info=inf;cfg=clone(inf.config);ensureCfg();colRowsBy.clear();ADD.rows=null;COV.n=null; /* (beta.8) rows built for the last visit held that visit's copy of the settings */
  studioClear();sRet=0;sInfo=null;sInfoBusy=false;sGames={};sCands={};sSets={};sBusy={};needRescan=false;raChanged=false;pending=false;raEmu=null;emuAsk=null;listSig="";edit=null;grab=null;results=null;query="";preSearch=null;sub=null;secI=0;rowI=0;ci=0;focus="side";infoOpen=false;det=null;ipl=null;pathOk={};status={};askT={};displays=null;outputs=null;hidden=null;readWhere=null;readOld=null;mInfo=null;mInfoBusy=false;mGames={};mCands={};mBusy={};mMsg={};
  device=document.body.classList.contains("pad-input")?"pad":"key";
  try{const g=window.BS_activePad?window.BS_activePad():[...(navigator.getGamepads?navigator.getGamepads():[])].find(x=>x&&x.connected);if(g)padId=g.id}catch{}
  document.body.classList.toggle("bss-reduce",gp("ui.reduceMotion")===true);
  applyTint();build();route(opts.page,opts)}
function show(inf,opts){if(window.Shelf&&Shelf.previewStop)Shelf.previewStop(); /* (beta.7: a playing preview ends when Settings opens) */prep(inf,opts);E.osk.classList.remove("on");root.classList.add("on");root.setAttribute("aria-hidden","false");isOpen=true;active=true;cover(true);fit();render();startPoll();loadAsync();
}
// (beta.10) how many games the last library scan found (every console's, from Shelf.gameCounts), for the rescan's "Found N new games"
function libTotal(){const m=shelf("gameCounts");if(!m||typeof m!=="object")return null;let n=0;for(const k in m)if(typeof m[k]==="number")n+=m[k];return n}
async function close(){if(closing||!isOpen)return;closing=true;
  try{if(edit)cancelEdit();active=false;stopPoll();cover(false);await save();isOpen=false;root.classList.remove("on");root.setAttribute("aria-hidden","true");E.mus.classList.remove("on");
    shelf("holdInput");shelf("setMode","browse");play("back"); /* (the press that closed Settings, A on a row above all, does nothing on the shelf) */
    // (beta.10) the rescan says what it's doing and what it found (it used to run silently)
    if(needRescan){needRescan=false;const n0=libTotal();shelf("toast","Looking for games…");try{await shelf("refresh",true);const n1=libTotal();shelf("toast",n0==null||n1==null?"Library updated":n1>n0?`Found ${n1-n0} new game${n1-n0===1?"":"s"}`:"No new games found")}catch(e){console.warn("settings: refresh",e)}} // a failed rescan must not skip settingsClosed / raReload below
    shelf("settingsClosed");
    if(raChanged&&window.Shelf&&Shelf.raReload)shelf("raReload")}
  finally{closing=false}}
// a classic page (box art planner, manual downloads, the Internet Archive finder, a console's detection lists…) over this
// screen: it shares this config, and closing it comes back here at the same row
async function legacy(page,o){o=o||{};if(!(window.Setup&&Setup.openLegacy)){toast("Not available");return}
  if(edit)cancelEdit();await save();active=false;stopPoll();cover(false);root.classList.remove("on");E.mus.classList.remove("on");
  if(o.wizard){isOpen=false;root.setAttribute("aria-hidden","true")}
  // the classic pages save through their own persist, so remember what the library depends on: resume() rescans on close
  // only when one of these changed there, or a box art / manual download ran (its "shows up when you close Settings")
  legacySnap=legacyKey();legacyDl=Setup.downloads?Setup.downloads():0;
  Setup.openLegacy(page,{info,cfg,wizard:!!o.wizard,back:o.wizard?null:resume})}
let legacySnap="",legacyDl=0;
const legacyKey=()=>JSON.stringify([cfg.systems,cfg.steam,cfg.apps,cfg.ui.artLibrary,cfg.ui.shelfOrder]);
function resume(){if(!isOpen)return;if(legacyKey()!==legacySnap||(Setup.downloads?Setup.downloads():0)!==legacyDl)needRescan=true;
  // listSig: rebuild the row elements too, or they'd keep the previous build's row objects (a console page's rows capture their config)
  ensureCfg();listSig="";build();root.classList.add("on");active=true;cover(true);hidden=null;const h=shelf("hiddenList");Promise.resolve(h).then(l=>{hidden=Array.isArray(l)?l:[];bump()}).catch(()=>{});
  const n=rowsOf(sec()).length;rowI=Math.min(rowI,Math.max(0,n-1));fit();render();startPoll()}

/* ---------- Art Studio (1.67) ---------- */
// Art & manuals → a console opens its games; from there its shelf style (five presets, a region, an optional look per
// part), each game's own mix (a look per part, locked to that game) and Pick exact (every version found for one part).
// These are sub-views of the Art page with a stack: B goes back one level. Controls: D-pad moves, A uses, Y puts things
// back on the shelf style, X turns the case, LB / RB change part (filter on the games grid). Lists come from main
// (art:* in preload.js); thumbnails arrive later through onArtCand and fade in over a placeholder of the same size, so
// nothing moves. Big grids only draw the rows in view; positions are worked out from one size read per build (never
// per move or per frame), and every picture decodes off the main thread (img.decode()).
// the "disc" part is named for what the console's games come on (1.73.5): a cartridge on Switch and 3DS, a UMD on PSP
const MEDIA_WORD=id=>id==="sw"||id==="sw2"||id==="n3ds"?"Cartridge":id==="psp"?"UMD":"Disc",isCartSys=id=>id==="sw"||id==="sw2"||id==="n3ds";
const partWord=(base)=>new Proxy(base,{get:(t,k)=>k==="disc"?MEDIA_WORD(studio&&studio.sys):t[k]});
const PARTS=["front","back","spine","disc"],PART_N=partWord({front:"Front",back:"Back",spine:"Spine",disc:"Disc"}),PART_L=partWord({front:"Front cover",back:"Back cover",spine:"Spine",disc:"Disc"});
const REGS=[["usa","USA"],["eur","Europe"],["jpn","Japan"]],REG_S={usa:"USA",eur:"EUR",jpn:"JPN",world:"World"};
const PRESETS=[["authentic","Authentic","Real scans of the original box and disc for every game"],["hd","Sharpest HD","The highest-resolution art for every game, clean and crisp"],["region","Region","Every game in one region's version"],["special","Special editions","Collector's and limited cases where a game had one, else authentic"],["sets","Complete sets","Front, back, spine and disc from one release, where a game has them"],["mix","Your mix","Choose a look per part below, or per game"]];
const SREGS=[["usa","USA"],["eur","Europe"],["jpn","Japan"],["hd","HD"]],SET_I=()=>PRESETS.findIndex(p=>p[0]==="sets"); // the Complete sets card's choices
const PP_LOOKS=["authentic","hd","region:usa","region:eur","region:jpn","special"];
const SRC_N={made:"Boxshelf, from the cover",launchbox:"LaunchBox",libretro:"libretro",gametdb:"GameTDB",steam:"Steam",steamgriddb:"SteamGridDB",screenscraper:"ScreenScraper",current:"your current file",earlier:"an earlier pick"},KIND_N={scan:"scan",clean:"clean",hd:"HQ"};
const FILTERS=[["all","All"],["nocover","No cover"],["missing","Missing a part"],["low","Low-res"],["locked","Your picks"]];
// (1.80) a part as a little picture of itself (cover, back, spine, disc or cartridge) with a mark for its state, so the grid
// reads without letters or colour alone: ✓ have it · ! low-res · × missing · • your pick
const PICON={front:'<rect x="3.5" y="1.5" width="9" height="13" rx="1.3"/>',back:'<rect x="3.5" y="1.5" width="9" height="13" rx="1.3"/><path d="M5.6 5h4.8M5.6 8h4.8M5.6 11h3"/>',spine:'<rect x="6" y="1.5" width="4" height="13" rx="1"/>',disc:'<circle cx="8" cy="8" r="6.4"/><circle cx="8" cy="8" r="1.5"/>',cart:'<rect x="2.5" y="3" width="11" height="10" rx="1.2"/><path d="M5 6h6v3.5H5z"/>'};
const partIcon=(p,sys)=>`<svg viewBox="0 0 16 16" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linejoin="round">${PICON[p==="disc"&&isCartSys(sys)?"cart":p]}</svg>`;
const SL_MARK={"":"✓",lo:"!",miss:"×",lk:"•","miss lk":"×"},SL_WORD={"":"have it",lo:"low-res",miss:"missing",lk:"your pick","miss lk":"missing"};
const LOCK_SVG='<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round"><rect x="5" y="11" width="14" height="10" rx="2"/><path d="M8 11V8a4 4 0 0 1 8 0v3"/></svg>';
function lookName(l,short){if(!l||l==="shelf")return "Shelf style";if(l==="authentic")return "Authentic";if(l==="hd")return short?"HD":"HD";if(l==="special")return short?"Special":"Special ed.";if(l==="exact")return short?"picked":"Exact pick";
  if(String(l).startsWith("region:")){const r=REG_S[l.slice(7)]||l.slice(7).toUpperCase();return short?r:"Region: "+r}return String(l)}
function styleLook(st,part){st=st||{};if(st.preset==="sets")return st.setsHd?"hd":"region:"+(st.region||"usa");if(st.preset==="mix")return (st.parts&&st.parts[part])||"authentic";if(st.preset==="region")return "region:"+(st.region||"usa");return st.preset||"authentic"}
function styleName(st){st=st||{};if(st.preset==="sets")return "Complete sets · "+(st.setsHd?"HD":REG_S[st.region||"usa"]||"USA");if(st.preset==="region")return "Region · "+(REG_S[st.region||"usa"]||"USA");if(st.preset==="mix")return "Your mix";return (PRESETS.find(p=>p[0]===st.preset)||PRESETS[0])[1]}
const scoreKey=l=>l==="hd"?"hd":String(l||"").startsWith("region")?"region":l==="special"?"special":"authentic";
const ck=(sys,file,part)=>sys+"|"+String(file||"").toLowerCase()+"|"+part;
let sViewK="",studio=null,stack=[],sRet=0,sInfo=null,sInfoBusy=false,sGames={},sCands={},sJobs={},jobSys={},sBusy={},sEls={},sGeo=null,sSig="",styleT=0;
const conOf=sys=>sInfo&&sInfo.consoles.find(c=>c.id===sys)||null;
const conName2=sys=>(conOf(sys)||{}).name||conName(sys);
const gameOf=(sys,file)=>{const e=sGames[sys];if(!e||!e.list)return null;const f=String(file||"").toLowerCase();return e.list.find(g=>String(g.file).toLowerCase()===f)||null};
const lockOf=(g,p)=>g&&g.lock&&g.lock[p]||null;
// frozen: the whole game locked as it is (1.68); isLocked: frozen or any part with a look / picture of its own
const isFrozen=g=>!!(g&&g.frozen);
const isLocked=g=>!!(g&&(g.frozen||g.lock&&PARTS.some(p=>g.lock[p])));
// pictures: decoded off the main thread, then faded in; one already shown before comes back at once (no flicker on a rebuild)
const seen=new Set();
function imgEl(src){const im=new Image();im.decoding="async";im.alt="";im.draggable=false;
  if(seen.has(src)){im.src=src;im.className="on now";return im}
  im.src=src;const ok=()=>{if(seen.size>3000)seen.clear();seen.add(src);im.classList.add("on");stopWait(im.parentNode)};
  if(im.decode)im.decode().then(ok,()=>im.classList.add("bad"));else im.onload=ok;return im}
// a picture slot: a placeholder of the same size underneath, the image on top once it's decoded
function slot(el,src,ph){if(el._u===src&&el._p===ph)return;el._u=src;el._p=ph;el.innerHTML=ph||"";if(src){const im=imgEl(src);el.append(im);if(im.classList.contains("on"))stopWait(el)}}
// the picture is up: the shimmer underneath stops (it ran on, hidden, as one animated layer per card)
function stopWait(el){const w=el&&el.querySelector(".bsa-ph.wait");if(w)w.classList.remove("wait")}
const phTitle=(t,cls)=>`<div class="bsa-ph${cls?" "+cls:""}"><span>${esc(t||"")}</span></div>`;

/* data from main */
function loadStudio(){if(!api.artStudio||sInfoBusy)return;sInfoBusy=true;
  Promise.resolve(api.artStudio()).then(r=>{sInfo={consoles:Array.isArray(r&&r.consoles)?r.consoles:[],looks:(r&&r.looks)||[]};
    // a restyle already running (Settings or the renderer opened mid-job): pick its progress up; art:job events go on from there
    sInfo.consoles.forEach(c=>{const j=c.job;if(!j||!j.jobId)return;jobSys[j.jobId]=c.id;latestJob[c.id]=j.jobId;const have=sJobs[j.jobId];if(!have||(have.state==="run"&&(have.done||0)<=(j.done||0)))sJobs[j.jobId]={id:j.jobId,label:j.label,done:j.done||0,total:j.total||0,state:"run"}});paintJob()}).catch(e=>{sInfo=sInfo||{consoles:[],looks:[],err:errMsg(e)}}).finally(()=>{sInfoBusy=false;if(isOpen)bump()})}
// (beta.3) a first load always redraws (a quiet re-read started by a finished download used to leave "Loading…" up), gives
// up after 20 s with a message, and an error is shown (it used to leave an empty grid); the page asks again 5 s later
function loadGames(sys,quiet){let e=sGames[sys];if(e&&e.busy)return e.busy;if(!e)e=sGames[sys]={list:null};const first=!e.list;if(first)quiet=false;
  const late=new Promise((_,no)=>setTimeout(()=>no(new Error("Boxshelf took too long to list them")),20000));
  e.busy=Promise.race([Promise.resolve(api.artGames({systemId:sys})),late]).then(l=>{e.list=Array.isArray(l)?l:[];e.err=null;e.errAt=0;e.stale=false;e.ver=(e.ver||0)+1}).catch(x=>{e.err=errMsg(x);e.errAt=Date.now();if(!first)e.list=e.list||[];else setTimeout(()=>{if(isOpen&&studio&&studio.sys===sys&&sGames[sys]===e&&!e.list)loadGames(sys)},5000)})
    // a quiet re-read (after a change, a finished job) patches what's on screen in place: the games grid swaps only the
    // tiles that changed (gamesFill) and the mix / pick views read the list live; nothing is rebuilt, nothing re-animates
    .finally(()=>{e.busy=null;if(isOpen&&studio&&studio.sys===sys){if(studio.v==="games"&&!quiet)sSig="";bump()}});return e.busy}
// a list read before still waiting on thumbnails is asked for again: main stops a part's downloads when another part (or
// game) is asked for, so coming back to it must restart them, or its cards would shimmer for ever
function loadCands(sys,file,part,force){const k=ck(sys,file,part);let c=sCands[k];if(c&&(c.busy||(c.list&&!force&&!c.list.some(x=>!x.thumb&&!x.failed))))return;if(!c)c=sCands[k]={list:null};
  c.busy=Promise.resolve(api.artCandidates({systemId:sys,file,part})).then(r=>{c.list=Array.isArray(r&&r.list)?r.list:[];c.gen=(c.gen||0)+1;c.note=r&&r.index==="missing"?"noindex":r&&r.index==="preparing"?"preparing":(r&&r.note)||"";c.err=null;sortCands(c,sys,file,part)}).catch(x=>{c.err=errMsg(x);c.list=c.list||[];c.sorted=c.sorted||[]})
    // a thumbnail that landed before its list did (a cached one can) was held back in c.early: apply it now
    .finally(()=>{c.busy=null;const early=c.early;c.early=null;if(early)early.forEach(onCand);if(isOpen&&studio&&studio.v==="pick")bump()})}
// best match first for the look this part follows (its own lock, else the shelf style); main's scores rank them
function partLook(sys,g,part){const l=lockOf(g,part);if(l&&l.look&&l.look!=="exact")return l.look;return styleLook((conOf(sys)||{}).style,part)}
function sortCands(c,sys,file,part){const g=gameOf(sys,file),k=scoreKey(partLook(sys,g,part));c.look=partLook(sys,g,part);
  c.sorted=c.list.map((x,i)=>[x,i]).sort((a,b)=>((b[0].score&&b[0].score[k])||0)-((a[0].score&&a[0].score[k])||0)||a[1]-b[1]).map(z=>z[0]);
  c.max=Math.max(1,...c.list.map(x=>(x.score&&x.score[k])||0))}
function studioLoad(){const st=studio;if(!st)return;if(MV[st.v]){msLoad(st);return}if(!sInfo)loadStudio();
  const e=sGames[st.sys];if(!e||(!e.list&&!e.busy&&!(e.err&&Date.now()-e.errAt<5000))||e.stale)loadGames(st.sys,!!(e&&e.list));
  if(st.v==="pick")loadCands(st.sys,st.file,st.part)}
// events: a thumbnail (or its size) arrived; a background job moved on
let candQ=new Map(),candRaf=0;
function onCand(e){if(!e||!e.part)return;if(e.set){onSetCand(e);return}/* a matching set's mini preview */const c=sCands[ck(e.systemId,e.file,e.part)];if(!c)return;if(!c.list){if(c.busy)(c.early=c.early||[]).push(e);return}let x=c.list.find(z=>z.key===e.key);
  // a guessed version (GameTDB in another region, say) is listed only once it really downloaded: it arrives whole, at the end
  if(!x&&e.cand&&!e.failed&&!e.dup){x=e.cand;c.list.push(x);if(c.sorted)c.sorted.push(x);if(isOpen&&studio&&studio.v==="pick"&&ck(studio.sys,studio.file,studio.part)===ck(e.systemId,e.file,e.part))bump();return}
  if(!x)return;
  const here=isOpen&&studio&&studio.v==="pick"&&ck(studio.sys,studio.file,studio.part)===ck(e.systemId,e.file,e.part);
  // the same picture as another candidate (same bytes and size): drop it; the cursor stays on the card it was on.
  // Only the cards are laid out again (pickUpdate sees the count change), not the parts list or the case preview
  if(e.dup){c.list.splice(c.list.indexOf(x),1);const j=c.sorted?c.sorted.indexOf(x):-1;if(j>=0){c.sorted.splice(j,1);if(here&&studio.i>j)studio.i--}if(x.inUse){const t=c.list.find(z=>z.key===e.dup);if(t)t.inUse=true}if(here)bump();return}
  // cancelled (always with failed): a newer art:cands took the downloads over. Not a failure: the card stops shimmering, and
  // loadCands asks again when the part is opened next (x.stopped has no thumb and isn't failed)
  if(e.cancelled)x.stopped=true;
  // a version that couldn't be downloaded isn't offered: its card goes, the cursor stays where it was (the next card moves up)
  else if(e.failed){const k=c.list.indexOf(x);c.list.splice(k,1);c.lost=(c.lost||0)+1;const j=c.sorted?c.sorted.indexOf(x):-1;if(j>=0){c.sorted.splice(j,1);if(here&&studio.i>j)studio.i--}if(here)bump();return}if("inUse" in e)x.inUse=!!e.inUse;if(e.score)x.score=e.score;
  if(e.thumb)x.thumb=e.thumb;if(e.w)x.w=e.w;if(e.h)x.h=e.h;
  if(here){candQ.set(x.key,x);if(!candRaf)candRaf=requestAnimationFrame(()=>{candRaf=0;const L=[...candQ.values()];candQ.clear();if(!studio||studio.v!=="pick")return;L.forEach(patchCard);render()})}}
let jobBumpT=0;
// which console a job belongs to: main says (art:job systemId, CONTRACT-CHANGES 15); jobSys keeps what art:style and
// art:studio told us, for an event that somehow lacks it
const jobOwner=e=>e.systemId?(jobSys[e.id]=e.systemId):jobSys[e.id]||null;
// the newest restyle per console (art:style's jobId, or art:studio's consoles[].job after a reload): a newer style
// replaces a running one, and the replaced one's "cancelled" is not news for the user
let latestJob={};const userStop=new Set();
function onJob(e){if(!e||!e.id)return;if(e.kind==="game"){onGameJob(e);return}const sys=jobOwner(e);
  // a replaced restyle's "cancelled" (a newer style for the same console took over) isn't news for the user
  if(e.state==="cancelled"&&!userStop.has(e.id)&&sys&&(latestJob[sys]&&latestJob[sys]!==e.id||styleBusy[sys]||Object.values(sJobs).some(j=>j.id!==e.id&&jobSys[j.id]===sys&&j.state==="run"))){
    delete sJobs[e.id];paintJob();return}
  if(e.state==="run"&&sys&&!latestJob[sys])latestJob[sys]=e.id;
  sJobs[e.id]=e;
  if(e.state!=="run"){userStop.delete(e.id);if(sys&&latestJob[sys]===e.id)delete latestJob[sys];if(sys&&sGames[sys])sGames[sys].stale=true;const me=e;setTimeout(()=>{if(sJobs[me.id]===me){delete sJobs[me.id];paintJob()}},me.state==="done"?3500:2000);
    if(isOpen){toast(e.state==="done"?`${e.label||"Re-fetch"}: done${e.msg?" · "+e.msg:""}`:e.state==="cancelled"?`${e.label||"Re-fetch"}: stopped`:`${e.label||"Re-fetch"} stopped: ${e.msg||"something went wrong"}`);
      if(sys&&studio&&studio.sys===sys)loadGames(sys,true);sInfoBusy=false;loadStudio()}}
  // the console's row on the Art page, its games header and its sidebar entry show the progress too (300 ms throttle)
  paintJob();if(isOpen&&(studio||onArt())&&!jobBumpT)jobBumpT=setTimeout(()=>{jobBumpT=0;if(isOpen&&(studio||onArt()))bump()},300)}
const onArt=()=>!results&&!!SECTIONS[secI]&&SECTIONS[secI].id==="art";
// a console's job is its newest one; any other job still marked running for it is one being replaced
const conJob=sys=>{const j=latestJob[sys]&&sJobs[latestJob[sys]];return j&&j.state==="run"?j:null};
function jobNow(){const L=Object.values(sJobs).filter(j=>{const o=jobSys[j.id];return !o||!latestJob[o]||latestJob[o]===j.id||j.state!=="run"});if(!L.length)return null;
  const mine=studio&&L.filter(j=>jobSys[j.id]===studio.sys);return (mine&&mine.find(j=>j.state==="run"))||L.find(j=>j.state==="run")||(mine&&mine[0])||L[0]}
function conJobText(sys){const j=conJob(sys);return j?`Re-fetching · ${j.done||0} of ${j.total||"…"}`:""}
// the pill shows on the Art page and in the studio only: other Settings pages keep their top-right corner clear
function paintJob(){const el=E.job;if(!el)return;const j=isOpen&&(studio||onArt())?jobNow():null;el.classList.toggle("on",!!j);if(!j)return;
  const t=`${j.label||"Re-fetching"} · ${j.state==="run"?`${j.done||0} of ${j.total||"…"}`:j.state==="done"?"done":j.state==="cancelled"?"stopped":"failed"}`;
  if(el._t!==t){el._t=t;el.querySelector(".t").textContent=t}el.classList.toggle("end",j.state!=="run");
  el.querySelector(".bar i").style.transform=`scaleX(${j.total?Math.min(1,(j.done||0)/j.total):0})`;el._id=j.id}
// Refresh art database (1.71): a forced rebuild of the LaunchBox index, with progress and size, cancellable. main shares one build at a time
let lbRef=null,lbRefInfo=null,lbRefAsked=false;
if(api.onArtIndex)api.onArtIndex(p=>{if(!p||typeof p!=="object")return;if(lbRef)lbRef=p;if(isOpen&&onArt())bump()});
const mb=b=>b>0?(b/1048576).toFixed(b<10485760?1:0):"0";
function refText(){if(lbRef){const p=lbRef;return p.phase==="download"?(p.total?`Downloading… ${mb(p.got)} of ${mb(p.total)} MB · ${p.pct||0}%`:`Downloading… ${mb(p.got)} MB`):p.phase==="unpack"?"Unpacking the database…":p.phase==="read"?`Reading the database… ${p.pct||0}%`:p.phase==="split"?"Finishing…":"Starting…"}
  if(!lbRefAsked&&api.artIndexInfo){lbRefAsked=true;Promise.resolve(api.artIndexInfo()).then(r=>{lbRefInfo=r||{};if(isOpen)bump()}).catch(()=>{lbRefInfo={}})}
  const i=lbRefInfo;return !i?"":i.at?`Last built ${when(i.at)}${i.bytes?` · index ${mb(i.bytes)} MB`:""}`:"Not built yet"}
async function refreshIndex(){if(!api.artRefreshIndex)return;if(lbRef){try{await api.artRefreshCancel()}catch(e){toast(errMsg(e))}return}
  lbRef={phase:"start"};toast("Refreshing the art database (about 110 MB)…");bump();
  try{lbRefInfo=await api.artRefreshIndex()||lbRefInfo;toast("Art database refreshed");Object.values(sCands).forEach(c=>c.dirty=true)}
  catch(e){const m=errMsg(e);toast(/cancel|stopped/i.test(m)?"Refresh stopped · the old database is still in use":m);if(!/cancel|stopped/i.test(m))play("error")}
  lbRef=null;bump()}
// the LaunchBox index (CONTRACT-CHANGES 3): a one-time download; progress shows on the classic covers:progress
let sIndexBusy=false;
async function buildIndex(st){if(sIndexBusy||!api.artBuildIndex)return;sIndexBusy=true;sSig="";toast("Building the LaunchBox index (one time, about 110 MB)…");bump();
  try{await api.artBuildIndex();toast("LaunchBox index ready");Object.values(sCands).forEach(c=>c.dirty=true);if(studio&&studio.v==="pick")loadCands(studio.sys,studio.file,studio.part,true)}catch(e){toast(errMsg(e));play("error")}
  sIndexBusy=false;sSig="";bump()}
async function cancelJob(id){if(id&&sJobs[id]&&sJobs[id].man){try{await api.manualCancel({jobId:id})}catch(e){toast(errMsg(e))}return} // a console's manual re-pick (Manual Studio)
  if(!id||!api.artCancel)return;userStop.add(id);try{await api.artCancel({jobId:id})}catch(e){toast(errMsg(e))}}

/* (1.80) the box art summary: games with no front cover, from art:studio's per-console counts (loaded on first look) */
function artNoCover(){if(!sInfo){if(api.artStudio&&isOpen)loadStudio();return 0}return sInfo.consoles.reduce((a,c)=>a+(c.noCover||0),0)}
function artSummary(){if(!api.artStudio)return "";if(!sInfo){loadStudio();return ""}const C=sInfo.consoles,t=C.reduce((a,c)=>a+(c.total||0),0),n=artNoCover();
  return !t?"":n?`${t-n} of ${t} games have a cover · ${n} without`:`All ${t} games have a cover`}
/* the Art page's rows: one per console */
function studioRows(){if(!api.artStudio)return [];
  if(!sInfo)return [{t:"action",label:"Loading your consoles…",btn:"",info:"Art Studio: how each console's box art looks.",run:()=>{},ns:true,dim:true}];
  if(sInfo.err&&!sInfo.consoles.length)return [{t:"action",label:"Couldn't load the Art Studio",btn:"Try again",info:sInfo.err,run:()=>{sInfo=null;loadStudio()},ns:true}];
  if(!sInfo.consoles.length)return [{t:"action",label:"No games on any shelf yet",btn:"",info:"Add a games folder for a console (Consoles) and it shows up here.",run:()=>{},ns:true,dim:true}];
  return sInfo.consoles.map(c=>({t:"action",label:c.name,btn:c.noCover?`${c.noCover} without a cover`:`${c.total} game${c.total===1?"":"s"}`,warnBtn:()=>c.noCover>0,ns:true,keys:"art studio box art covers style look region hd special edition disc spine "+c.name,
    info:`Pick how ${c.name}'s box art looks (authentic scans, sharpest HD, one region or special editions), then fine-tune any game.`+(c.locked?` ${c.locked} game${c.locked===1?" has":"s have"} a mix of ${c.locked===1?"its":"their"} own.`:""),
    sys:c.id,sub:()=>({text:conJobText(c.id)||(styleName(c.style)+(c.locked?` · ${c.locked} with picks or locked`:"")),cls:conJob(c.id)?"warn":""}),run:()=>studioOpen({v:"games",sys:c.id})}))}

/* opening, going back */
function studioOpen(v,reset){if(edit)cancelEdit();grab=null;artAcct=null;if(!studio||reset){if(!studio)sRet=rowI;stack=[]}else stack.push(studio);
  studio=Object.assign({i:0,y:0},v);focus="page";infoOpen=false;sSig="";play("open");studioLoad();render()}
function studioBack(){play("back");const was=studio;if(stack.length){studio=stack.pop();
    if(studio.v==="mgames"&&was.file){const k=mgList(studio).findIndex(g=>String(g.file).toLowerCase()===String(was.file).toLowerCase());if(k>=0){studio.i=k;studio.zone="grid";studio.reveal=true}}
    if(studio.v==="games"&&was.file){const L=gamesList(studio),k=L.findIndex(g=>String(g.file).toLowerCase()===String(was.file).toLowerCase());if(k>=0){studio.i=k;studio.zone="grid";studio.reveal=true}}
    sSig="";studioLoad();render();return}
  studio=null;sSig="";rowI=sRet;if(sRet<0){rowI=Math.max(0,rowsOf(sec()).findIndex(r=>r.sys===was.sys))}render()}
function studioClear(){studio=null;stack=[];sSig="";sViewK=""}
// (beta.9) an "add an account" card: that service's page in Accounts & services; leaving it (B / Left) comes back to the same
// game and part, which looks for versions again (after the key is saved, so main sees it)
let artAcct=null;
function acctFromArt(st,svc){const keep={studio:st,stack:stack.slice(),sRet,secI,rowI,sub,subRet,sg:sgAsk(),key:gp("keys.steamgriddb")};route(svc);
  if(!sub||sub.kind!=="svc"){({secI,rowI,sub,subRet}=keep);studio=keep.studio;stack=keep.stack;sRet=keep.sRet;sSig="";return}
  artAcct=Object.assign(keep,{page:sub});play("open");render()}
function artBackTo(){const a=artAcct;artAcct=null;secDrop();({secI,rowI,sub,subRet}=a);grab=null;focus="page";
  studio=a.studio;stack=a.stack;sRet=a.sRet;sSig="";sViewK="";const st=studio,c=sCands[ck(st.sys,st.file,"front")];
  // (only when the key changed: no point asking for the same versions again; a list still on its way is asked again after)
  if(st.part!=="front"||!c||(a.sg===sgAsk()&&a.key===gp("keys.steamgriddb")))return;
  if(c.busy){c.dirty=true;return}
  c.list=null;Promise.resolve(save()).catch(()=>{}).then(()=>{loadCands(st.sys,st.file,"front",true);if(isOpen)bump()})}
// the grids' edge fades are drawn with the page background (settings.css .bsa-vp::before/::after): line that background
// up with the stage. Two rect reads, once per build
function fadeFix(el){if(!el||!E.stage)return;const r=el.getBoundingClientRect(),s=E.stage.getBoundingClientRect(),k=el.offsetWidth?r.width/el.offsetWidth:1,sc=s.width/(parseFloat(E.stage.style.width)||s.width);if(!k||!sc)return;
  const f=v=>(v/k).toFixed(1),st=el.style;st.setProperty("--fx",f(r.left-s.left));st.setProperty("--fy0",f(r.top-s.top));st.setProperty("--fy1",f(r.bottom-s.top-34*k));st.setProperty("--fw",f(s.width));st.setProperty("--fh",f(s.height))}
function geo(){if(!sGeo){sGeo={w:E.bsa.clientWidth,h:E.bsa.clientHeight}}return sGeo} // the one size read: per build, after a resize

/* the console's games: filter pills, then a grid of covers with F / B / S / D chips */
function gamesList(st){const e=sGames[st.sys],L=e&&e.list||[],f=st.filter||"all";if(f==="all")return L;
  return L.filter(g=>f==="nocover"?!(g.art&&g.art.front):f==="missing"?PARTS.some(p=>!(g.art&&g.art[p])):f==="low"?PARTS.some(p=>g.art&&g.art[p]&&g.low&&g.low[p]):f==="locked"?isLocked(g):true)}
const fCount=(st,f)=>gamesList({sys:st.sys,filter:f}).length;
// (1.80) the info strip under the grid takes GI_H; a title gets two lines
const GI_H=54;
function gGeo(){const G0=geo(),gap=18,top=104,cols=Math.max(3,Math.floor((G0.w+gap)/(176+gap))),tw=(G0.w-gap*(cols-1))/cols,cvH=Math.round((tw-20)*10/7),th=cvH+106;return {cols,tw,gap,cvH,th,rowH:th+20,top,vpH:G0.h-top-GI_H}}
// the top row: the filters, then (showing games that lack art) Download missing art, then the shelf style
function gTop(st){const f=st.filter||"all",fix=(f==="nocover"||f==="missing")&&!!api.downloadCovers&&fCount(st,f)>0;return FILTERS.map(([k,l])=>({k:"f",f:k,l})).concat(fix?[{k:"fix"}]:[],[{k:"style"}])}
// Download missing art: the download page (each console's switch there says whether it's in the download)
function gFix(){studioClear();sub=null;focus="page";inMore("art-get");play("open");render()}
function gamesBuild(st){const c=conOf(st.sys),e=sGames[st.sys],T=gTop(st),mw=MEDIA_WORD(st.sys);
  E.bsa.innerHTML=`<div class="bsa-top"><div class="bsa-pills">${T.map((x,j)=>x.k==="f"?`<div class="bsa-pill" data-t="${j}">${esc(x.l)} <span class="c">${fCount(st,x.f)}</span></div>`:x.k==="fix"?`<span class="bsa-sp"></span><div class="bsa-pill fix" data-t="${j}">${svg("art")}<span>Download missing art</span></div>`:`${T.some(y=>y.k==="fix")?"":'<span class="bsa-sp"></span>'}<div class="bsa-pill sty" data-t="${j}">${svg("art")}<span>Shelf style · <b>${esc(styleName(c&&c.style))}</b></span></div>`).join("")}</div>
    <div class="bsa-legend"><span class="pk">${PARTS.map(p=>`<em>${partIcon(p,st.sys)}${esc(p==="disc"?mw:PART_N[p])}</em>`).join("")}</span><span class="st"><b>✓</b>have it</span><span class="st lo"><b>!</b>low-res</span><span class="st miss"><b>×</b>missing</span><span class="st lk"><b>•</b>your pick</span><span><b class="lki">${LOCK_SVG}</b>locked game</span></div></div>
    <div class="bsa-vp"><div class="bsa-grid"></div></div><div class="bsa-gi"></div>`;
  sEls={top:[...E.bsa.querySelectorAll(".bsa-pill")],items:T,vp:E.bsa.querySelector(".bsa-vp"),grid:E.bsa.querySelector(".bsa-grid"),gi:E.bsa.querySelector(".bsa-gi"),tiles:new Map(),selI:-1,y:-1,tilesVer:e&&e.ver,cntVer:e&&e.ver,hl:null};
  sEls.vp.style.bottom=GI_H+"px";
  sEls.top.forEach(p=>{const j=+p.dataset.t;p.onmouseenter=()=>{if(device==="mouse"&&!(studio.zone==="top"&&studio.ti===j)){studio.zone="top";studio.ti=j;render()}};p.onclick=()=>{studio.zone="top";studio.ti=j;gamesInput("a")}});
  fadeFix(sEls.vp);const L=gamesList(st);
  if(!e||!e.list){sEls.grid.innerHTML=`<div class="bsa-empty">${e&&e.err?esc("Couldn't load the games: "+e.err):`Loading ${esc(conName2(st.sys))}'s games…`}</div>`;return}
  if(!L.length){sEls.grid.innerHTML=`<div class="bsa-empty">${st.filter==="nocover"?"Every game here has a front cover.":st.filter==="missing"?"Nothing missing: every game has a front, back, spine and "+mw.toLowerCase()+".":st.filter==="low"?"No low-res pictures on this shelf.":st.filter==="locked"?`No game has a pick of yours or is locked yet. Using a version in a game makes it your pick; ${btnLabel("y")} on a game locks it as it is.`:"No games on this shelf yet."}</div>`;if(st.zone!=="top"){st.zone="top";st.ti=st.ti||0}return}
  const gg=gGeo();sEls.grid.style.height=Math.ceil(L.length/gg.cols)*gg.rowH+"px";if(st.i>=L.length)st.i=L.length-1;if(!st.zone)st.zone="grid";
  // one highlight for the whole grid, moved with a transform: a new tile under the cursor never has to repaint its glow
  sEls.hl=document.createElement("div");sEls.hl.className="bsa-thl";sEls.hl.style.cssText=`width:${gg.tw}px;height:${gg.th}px`;sEls.grid.prepend(sEls.hl)}
function tileHtml(g,gg){const fz=isFrozen(g);
  // a locked game: the padlock on its cover and every chip in the lock colour (a missing part stays missing, locked too)
  return `<div class="in${fz?" fz":""}"><div class="cv" style="height:${gg.cvH}px"></div>${fz?`<div class="lock" title="Locked">${LOCK_SVG}</div>`:""}<div class="t">${esc(g.title)}</div><div class="slots">${PARTS.map(p=>{const cls=slCls(g,p);return `<span class="sl ${cls}">${partIcon(p,studio&&studio.sys)}<b>${SL_MARK[cls]||"✓"}</b></span>`}).join("")}</div></div>`}
function slCls(g,p){const have=g.art&&g.art[p],fz=isFrozen(g);return !have?(fz?"miss lk":"miss"):fz||lockOf(g,p)?"lk":g.low&&g.low[p]?"lo":""}
// the strip under the grid: the highlighted game's full name and each part in words
function giPaint(st,g){const el=sEls.gi;if(!el)return;const mw=MEDIA_WORD(st.sys);
  const h=g?`<b>${esc(g.title)}</b><span>${PARTS.map(p=>`${esc(p==="disc"?mw:PART_N[p])} ${SL_WORD[slCls(g,p)]||"have it"}`).join(" · ")}${isFrozen(g)?" · locked as it is":""}</span>`:"";if(el._h!==h){el._h=h;el.innerHTML=h}}
function gamesFill(st){const L=gamesList(st),gg=gGeo(),y0=Math.min(sEls.y<0?st.y:sEls.y,st.y),y1=Math.max(sEls.y<0?st.y:sEls.y,st.y),last=Math.ceil(L.length/gg.cols)-1;
  // rows in view (and one either side) now; two more each way after, one row per frame, so a new row of covers is
  // already drawn when it scrolls in and no single frame has to draw a whole row at once
  const r0=Math.max(0,Math.floor(y0/gg.rowH)-1),r1=Math.min(last,Math.ceil((y1+gg.vpH)/gg.rowH)+1),k0=Math.max(0,r0-2),k1=Math.min(last,r1+2);
  // the list was read again (a change, a finished job): swap only the tiles whose game, chips or cover changed
  if(sEls.tilesVer!==((sGames[st.sys]||{}).ver)){sEls.tilesVer=(sGames[st.sys]||{}).ver;sEls.tiles.forEach((t,i)=>{const g=L[i];if(!g){t.remove();sEls.tiles.delete(i);return}tileFill(t,g,gg)})}
  sEls.tiles.forEach((t,i)=>{const r=Math.floor(i/gg.cols);if(r<k0||r>k1){t.remove();sEls.tiles.delete(i)}});
  for(let r=r0;r<=r1;r++)tileRow(st,L,gg,r);
  const q=[];for(let r=r1+1;r<=k1;r++)q.push(r);for(let r=r0-1;r>=k0;r--)q.push(r);sEls.ahead=q;
  if(q.length&&!sEls.aheadRaf){const my=sEls;const step=()=>{my.aheadRaf=0;if(sEls!==my||!studio||studio.v!=="games")return;const r=my.ahead.shift();if(r==null)return;tileRow(studio,gamesList(studio),gGeo(),r);if(my.ahead.length)my.aheadRaf=requestAnimationFrame(step)};my.aheadRaf=requestAnimationFrame(step)}}
function tileFill(d,g,gg){const h=tileHtml(g,gg),u=g.art&&g.art.front;if(d._h===h&&d._u===u)return;if(d._h!==h){d._h=h;d.innerHTML=h}d._u=u;slot(d.querySelector(".cv"),u,phTitle(g.title))}
function tileRow(st,L,gg,r){for(let i=r*gg.cols;i<Math.min(L.length,(r+1)*gg.cols);i++){if(sEls.tiles.has(i))continue;const g=L[i],d=document.createElement("div");d.className="bsa-tile"+(i===sEls.selI?" sel":"");d.style.width=gg.tw+"px";d.style.transform=`translate(${(i%gg.cols)*(gg.tw+gg.gap)}px,${r*gg.rowH}px)`;
    tileFill(d,g,gg);
    d.onmouseenter=()=>{if(device==="mouse"&&!(studio.zone==="grid"&&studio.i===i)){studio.zone="grid";studio.i=i;render()}};d.onclick=()=>{studio.zone="grid";studio.i=i;gamesInput("a")};
    sEls.grid.append(d);sEls.tiles.set(i,d)}}
function gamesUpdate(st){const L=gamesList(st),onTop=st.zone==="top",e=sGames[st.sys],T=sEls.items||[];sEls.top.forEach(p=>{const j=+p.dataset.t,x=T[j]||{};p.classList.toggle("cur",onTop&&st.ti===j);p.classList.toggle("on",x.k==="f"&&x.f===(st.filter||"all"))});
  // the counts (a filter with nothing in it is dimmed); a re-read list can change them
  if(e&&sEls.cntVer!==e.ver||!sEls.cntDone){sEls.cntVer=e&&e.ver;sEls.cntDone=!!(e&&e.list);sEls.top.forEach(p=>{const x=T[+p.dataset.t]||{},c=x.k==="f"&&p.querySelector(".c");if(c){const n=fCount(st,x.f);c.textContent=n;p.classList.toggle("zero",!n&&x.f!=="all")}})}
  giPaint(st,onTop?null:L[st.i]);
  if(!L.length||!sEls.grid.style.height)return;const gg=gGeo(),r=Math.floor(st.i/gg.cols),top=r*gg.rowH,maxY=Math.max(0,Math.ceil(L.length/gg.cols)*gg.rowH-gg.vpH+16);
  if(!onTop||st.reveal){if(top<st.y+6)st.y=Math.max(0,top-10);else if(top+gg.th>st.y+gg.vpH-14)st.y=top+gg.th-gg.vpH+24;st.reveal=false}else st.y=Math.min(st.y,0);st.y=Math.max(0,Math.min(st.y,maxY));
  gamesFill(st);if(sEls.y!==st.y){sEls.grid.style.transform=`translateY(${-st.y}px)`;sEls.y=st.y}
  const want=onTop?-1:st.i;if(sEls.selI!==want){const a=sEls.tiles.get(sEls.selI);if(a)a.classList.remove("sel");sEls.selI=want}const b=sEls.tiles.get(want);if(b&&!b.classList.contains("sel"))b.classList.add("sel");
  if(sEls.hl){sEls.hl.classList.toggle("on",want>=0);if(want>=0){const t=`translate(${(want%gg.cols)*(gg.tw+gg.gap)}px,${Math.floor(want/gg.cols)*gg.rowH}px)`;if(sEls.hl._t!==t){sEls.hl._t=t;sEls.hl.style.transform=t}}}}
function gamesInput(a,rep){const st=studio,L=gamesList(st),gg=L.length?gGeo():null;
  if(a==="lb"||a==="rb"){const j=FILTERS.findIndex(f=>f[0]===(st.filter||"all")),n=(j+(a==="rb"?1:-1)+FILTERS.length)%FILTERS.length;st.filter=FILTERS[n][0];st.i=0;st.y=0;st.zone=gamesList(st).length?"grid":"top";st.ti=n;sSig="";play("tick",a==="rb"?1:-1);render();return}
  if(a==="x"){studioOpen({v:"style",sys:st.sys});return}
  if(st.zone==="top"){const T=sEls.items||gTop(st),n=T.length;if(st.ti>=n)st.ti=n-1;
    if(a==="left"){if(st.ti>0){st.ti--;play("tick",-1)}else if(!rep)play("error")}
    else if(a==="right"){if(st.ti<n-1){st.ti++;play("tick",1)}else if(!rep)play("error")}
    else if(a==="down"){if(L.length){st.zone="grid";play("tick",1)}else if(!rep)play("error")}
    else if(a==="up"){if(!rep)play("error")}
    else if(a==="a"){const x=T[st.ti]||{};if(x.k==="style"){studioOpen({v:"style",sys:st.sys});return}if(x.k==="fix"){gFix();return}
      if(x.k==="f"&&x.f!==(st.filter||"all")){st.filter=x.f;st.i=0;st.y=0;sSig="";play("tick",1)}}
    render();return}
  // nothing in the grid (still loading, or a load error): the pills are all there is to move on
  if(!L.length){if(a==="up"||a==="down"||a==="left"||a==="right"){st.zone="top";st.ti=Math.max(0,FILTERS.findIndex(f=>f[0]===(st.filter||"all")));if(!rep)play("error")}render();return}
  const g=L[st.i];
  if(a==="left"){if(st.i%gg.cols>0){st.i--;play("tick",-1)}else if(!rep){focus="side";play("tick",-1)}}
  else if(a==="right"){if(st.i<L.length-1&&st.i%gg.cols<gg.cols-1){st.i++;play("tick",1)}else if(!rep)play("error")}
  else if(a==="up"){if(st.i>=gg.cols){st.i-=gg.cols;play("tick",-1)}else if(!rep){st.zone="top";st.ti=FILTERS.findIndex(f=>f[0]===(st.filter||"all"));play("tick",-1)}}
  else if(a==="down"){if(st.i+gg.cols<L.length){st.i+=gg.cols;play("tick",1)}else if(Math.floor(st.i/gg.cols)<Math.floor((L.length-1)/gg.cols)){st.i=L.length-1;play("tick",1)}else if(!rep)play("error")}
  else if(a==="lt"||a==="rt"){st.i=Math.max(0,Math.min(L.length-1,st.i+(a==="rt"?1:-1)*gg.cols*2))}
  else if(a==="home")st.i=0;else if(a==="end")st.i=L.length-1;
  else if(a==="a"){if(g){studioOpen({v:"pick",sys:st.sys,file:g.file,title:g.title,part:"front",i:-1});return}}
  else if(a==="y"){if(g){toggleLock(st.sys,g.file);return}}
  render()}

/* the shelf style: five preset cards (region pills on Region), then a look per part */
function fakeCover(title,i,o){o=o||{};const h=(String(title).split("").reduce((a,c)=>a+c.charCodeAt(0),0)*37+i*61)%360;
  return `<div class="bsa-fc${o.scan?" scan":""}${o.jp?" jp":""}" style="--h:${h}">${o.ban?`<div class="ban" style="background:${o.ban[0]};color:${o.ban[1]}">${esc(o.ban[2])}</div>`:""}<div class="tag">${esc(o.tag||"")}</div><b>${esc(o.label||title)}</b></div>`}
function styleCovers(st,k){const e=sGames[st.sys],T=(e&&e.list||[]).slice(0,3).map(g=>g.title.split(/[:\-–]/)[0].trim().split(" ")[0]);while(T.length<3)T.push(["Prime","Pikmin","Zelda"][T.length]);
  const tag=(conName2(st.sys)||"").toUpperCase(),reg=st.draft.region||"usa";
  const O=k==="authentic"?[{scan:1},{scan:1},{scan:1}]:k==="hd"?[{},{},{}]:k==="region"?[0,1,2].map(()=>reg==="jpn"?{jp:1}:reg==="eur"?{tag:"PAL · "+tag,ban:["#e5e5e5","#222","PEGI 12"]}:{}):k==="special"?[0,1,2].map(()=>({ban:["#c9a227","#111","COLLECTOR'S EDITION"]})):k==="sets"?[{scan:1,tag:"FRONT"},{scan:1,tag:"BACK"},{scan:1,tag:"DISC"}]:[{},{scan:1},{ban:["#c9a227","#111","COLLECTOR'S"]}];
  return O.map((o,j)=>`<div class="c">${fakeCover(T[j],j+(k==="mix"?j*2:0),{tag:o.tag||tag,...o})}</div>`).join("")}
function styleBuild(st){const c=conOf(st.sys);if(!st.draft)st.draft=clone((c&&c.style)||{preset:"authentic",region:"usa",parts:{}});st.draft.parts=st.draft.parts||{};
  if(st.zone==null){st.zone="pre";st.i=Math.max(0,PRESETS.findIndex(p=>p[0]===st.draft.preset));st.ri=Math.max(0,REGS.findIndex(r=>r[0]===(st.draft.region||"usa")));st.pi=0}
  E.bsa.innerHTML=`<div class="bsa-presets">${PRESETS.map(([k,b,s],j)=>`<div class="bsa-pre" data-j="${j}"><div class="use">IN USE</div><div class="im">${styleCovers(st,k)}</div><b>${esc(b)}</b><span>${esc(s)}</span>${k==="region"?`<div class="opt">${REGS.map(([r,l],q)=>`<i data-q="${q}">${esc(l)}</i>`).join("")}</div>`:k==="sets"?`<div class="opt so">${SREGS.map(([r,l],q)=>`<i data-q="${q}">${esc(l)}</i>`).join("")}</div>`:""}</div>`).join("")}</div>
    <div class="bsa-perpart"><h3>Per part, for the whole shelf <small>· optional</small></h3><p>Each part follows the style above unless you change it here, e.g. HD fronts with authentic spines and backs. Changing one switches the shelf to Your mix.</p>
    <div class="bsa-pp">${PARTS.map((p,j)=>`<div class="bsa-ppi" data-j="${j}"><div class="k">${PART_N[p]}</div><div class="v"><span></span><small></small></div></div>`).join("")}</div></div>`;
  if(st.si==null)st.si=Math.max(0,st.draft.setsHd?3:SREGS.findIndex(r=>r[0]===(st.draft.region||"usa")));
  sEls={pre:[...E.bsa.querySelectorAll(".bsa-pre")],reg:[...E.bsa.querySelectorAll(".bsa-pre .opt:not(.so) i")],sreg:[...E.bsa.querySelectorAll(".bsa-pre .opt.so i")],pp:[...E.bsa.querySelectorAll(".bsa-ppi")],regV:st.draft.region||"usa"};
  
  const hov=(z,k,j)=>{if(device==="mouse"&&!(studio.zone===z&&studio[k]===j)){studio.zone=z;studio[k]=j;render()}};
  sEls.pre.forEach((d,j)=>{d.onmouseenter=()=>hov("pre","i",j);d.onclick=()=>{studio.zone="pre";studio.i=j;styleInput("a")}});
  sEls.reg.forEach((d,q)=>{d.onmouseenter=e=>{e.stopPropagation();hov("reg","ri",q)};d.onclick=e=>{e.stopPropagation();studio.zone="reg";studio.i=2;studio.ri=q;styleInput("a")}});
  sEls.sreg.forEach((d,q)=>{d.onmouseenter=e=>{e.stopPropagation();hov("sreg","si",q)};d.onclick=e=>{e.stopPropagation();studio.zone="sreg";studio.i=SET_I();studio.si=q;styleInput("a")}});
  sEls.pp.forEach((d,j)=>{d.onmouseenter=()=>hov("pp","pi",j);d.onclick=()=>{studio.zone="pp";studio.pi=j;styleInput("a")}})}
function styleUpdate(st){const c=conOf(st.sys),saved=(c&&c.style)||{},d=st.draft;
  // another region on the Region card: redraw that card's three covers only
  if(sEls.regV!==(d.region||"usa")){sEls.regV=d.region||"usa";const im=sEls.pre[2]&&sEls.pre[2].querySelector(".im");if(im)im.innerHTML=styleCovers(st,"region")}
  sEls.sreg.forEach((el,q)=>{const sv=d.preset==="sets"||saved.preset==="sets"?(d.setsHd?"hd":d.region||"usa"):null;el.classList.toggle("on",SREGS[q][0]===sv);el.classList.toggle("cur",st.zone==="sreg"&&st.si===q)});
  sEls.pre.forEach((el,j)=>{el.classList.toggle("sel",(st.zone==="pre"&&st.i===j)||(st.zone==="reg"&&j===2)||(st.zone==="sreg"&&j===SET_I()));el.classList.toggle("inuse",PRESETS[j][0]===(saved.preset||"authentic"))});
  sEls.reg.forEach((el,q)=>{el.classList.toggle("on",REGS[q][0]===(d.region||"usa"));el.classList.toggle("cur",st.zone==="reg"&&st.ri===q)});
  sEls.pp.forEach((el,j)=>{const p=PARTS[j],l=styleLook(d,p),own=d.preset==="mix";el.classList.toggle("cur",st.zone==="pp"&&st.pi===j);el.classList.toggle("follow",!own);
    const v=el.querySelector(".v span"),sm=el.querySelector(".v small"),t=lookName(l),s2=st.zone==="pp"&&st.pi===j?"‹ ›":own?"":"follows style";if(v.textContent!==t)v.textContent=t;if(sm.textContent!==s2)sm.textContent=s2})}
function styleInput(a,rep){const st=studio,d=st.draft,c=conOf(st.sys);
  if(a==="y"){const j=conJob(st.sys);if(j){cancelJob(j.id);return}return}
  if(st.zone==="pre"){
    if(a==="left"||a==="right"){const n=st.i+(a==="right"?1:-1);if(n>=0&&n<PRESETS.length){st.i=n;play("tick",a==="right"?1:-1)}else if(!rep)play("error")}
    else if(a==="down"){st.zone=st.i===2?"reg":st.i===SET_I()?"sreg":"pp";if(st.zone==="pp")st.pi=Math.min(PARTS.length-1,Math.round(st.i*(PARTS.length-1)/(PRESETS.length-1)));play("tick",1)}
    else if(a==="up"){if(!rep)play("error")}
    else if(a==="a"){const k=PRESETS[st.i][0];if(k==="sets"){st.zone="sreg";play("tick",1);render();return} // Complete sets: choose the region (or HD) below the card first
      d.preset=k;if(k==="mix"){const prev=(c&&c.style)||{};PARTS.forEach(p=>{if(!d.parts[p])d.parts[p]=styleLook(prev.preset==="mix"?{preset:"authentic"}:prev,p)})}saveStyle(st.sys,d);return}}
  else if(st.zone==="reg"){
    if(a==="left"||a==="right"){const n=st.ri+(a==="right"?1:-1);if(n>=0&&n<REGS.length){st.ri=n;d.region=REGS[n][0];play("tick",a==="right"?1:-1)}else if(!rep)play("error")}
    else if(a==="up"){st.zone="pre";st.i=2;play("tick",-1)}else if(a==="down"){st.zone="pp";st.pi=2;play("tick",1)}
    else if(a==="a"){d.region=REGS[st.ri][0];d.preset="region";saveStyle(st.sys,d);return}}
  else if(st.zone==="sreg"){ // Complete sets: USA / Europe / Japan, or the HD sets
    if(a==="left"||a==="right"){const n=st.si+(a==="right"?1:-1);if(n>=0&&n<SREGS.length){st.si=n;play("tick",a==="right"?1:-1)}else if(!rep)play("error")}
    else if(a==="up"){st.zone="pre";st.i=SET_I();play("tick",-1)}else if(a==="down"){st.zone="pp";st.pi=3;play("tick",1)}
    else if(a==="a"){const v=SREGS[st.si][0];d.preset="sets";if(v==="hd")d.setsHd=true;else{d.setsHd=false;d.region=v}saveStyle(st.sys,d);return}}
  else{
    if(a==="left"||a==="right"){const n=st.pi+(a==="right"?1:-1);if(n>=0&&n<PARTS.length){st.pi=n;play("tick",a==="right"?1:-1)}else if(!rep)play("error")}
    else if(a==="up"){st.zone="pre";st.i=Math.round(st.pi*(PRESETS.length-1)/(PARTS.length-1));play("tick",-1)}
    else if(a==="down"){if(!rep)play("error")}
    else if(a==="a"){const p=PARTS[st.pi];if(d.preset!=="mix"){const base=clone(d);PARTS.forEach(q=>d.parts[q]=styleLook(base,q));d.preset="mix"}
      const cur=d.parts[p]||"authentic",j=PP_LOOKS.indexOf(cur);d.parts[p]=PP_LOOKS[(j+1)%PP_LOOKS.length];play("tick",1);
      // a few presses in a row are one change: the re-fetch starts once you stop
      clearTimeout(styleT);const sys=st.sys,snap=clone(d);styleT=setTimeout(()=>saveStyle(sys,snap,true),900)}}
  render()}
// one save per console at a time: a double press of A (or A right after the per-part timer fired) can't start two jobs
let styleBusy={};
async function saveStyle(sys,style,quiet){clearTimeout(styleT);styleT=0;if(styleBusy[sys]){if(quiet)styleT=setTimeout(()=>saveStyle(sys,style,true),400);return}const c=conOf(sys);style=clone(style);if(style.preset!=="mix")style.parts={};if(style.preset!=="sets")delete style.setsHd;styleBusy[sys]=true;
  try{const r=await api.artStyle({systemId:sys,style});if(c)c.style=clone(r&&r.style||style);if(sGames[sys])sGames[sys].stale=true;
    if(r&&r.jobId){jobSys[r.jobId]=sys;const old=latestJob[sys];latestJob[sys]=r.jobId;if(old&&old!==r.jobId&&sJobs[old]&&sJobs[old].state==="run")delete sJobs[old];if(!sJobs[r.jobId])sJobs[r.jobId]={id:r.jobId,label:`Re-fetching ${conName2(sys)}`,done:0,total:0,state:"run"};paintJob()}
    play("toast");toast(`${conName2(sys)}: ${styleName(style)} · ${r&&r.note||"fetching in the background"}`)}
  catch(e){toast(errMsg(e));play("error")}styleBusy[sys]=false;
  // the saved style comes back from main (validated): show it, unless more per-part presses are already waiting to be saved
  if(studio&&studio.v==="style"&&studio.sys===sys&&!styleT)studio.draft=null,sSig="";bump()}

/* the 3D case on the right: front, spine, back (and the disc) from the art that's in use, X turns it around */
function caseBuild(el,title){el.innerHTML=`<div class="k"></div><div class="bsa-scene"><div class="bsa-turn"><div class="bsa-case"><div class="f"></div><div class="s"></div><div class="bk"></div></div></div><div class="bsa-disc"></div><div class="bsa-spinebig"></div></div><div class="bsa-shadow"></div><div class="what"></div><div class="why"></div>`;
  return {k:el.querySelector(".k"),turn:el.querySelector(".bsa-turn"),f:el.querySelector(".f"),s:el.querySelector(".s"),bk:el.querySelector(".bk"),disc:el.querySelector(".bsa-disc"),sb:el.querySelector(".bsa-spinebig"),what:el.querySelector(".what"),why:el.querySelector(".why"),title}}
// (beta.9) the preview case takes the front art's shape (kept between .45 and 1.6 wide : tall), so no box is cropped;
// no front yet: the console's usual case shape
const CASE_AR={sw:.618,sw2:.618,psp:.578,n3ds:1.127,ps3:.789,ps4:.789,ps5:.789};
function caseShape(P,sys){const c=P.f&&P.f.parentNode;if(!c)return;const im=P.f.querySelector("img");
  const set=ar=>{ar=Math.max(.45,Math.min(1.6,ar||.714));const v=ar.toFixed(3);if(c.style.getPropertyValue("--ar")!==v)c.style.setProperty("--ar",v)};
  if(!im){set(CASE_AR[sys]||.714);return}
  const fit=()=>{if(im.naturalWidth&&im.naturalHeight)set(im.naturalWidth/im.naturalHeight)};
  if(im.complete&&im.naturalWidth)fit();else if(!im._shape){im._shape=1;set(CASE_AR[sys]||.714);im.addEventListener("load",fit,{once:true})}}
// (beta.9) the case is far bigger than a card: it shows the card's 360 px picture at once (already decoded), then a sharper copy
// (made by main off its thread, kept in data\thumbs) once that one has decoded: no blank, no flicker. Windows only
const CASE_T=Math.max(900,Math.min(1600,Math.round((window.devicePixelRatio||1)*640/100)*100));
function caseSharp(el){if(!el||(window.bsPlatform&&bsPlatform.os==="android"))return;const im=el.querySelector(":scope > img"),u=el._u;
  if(!im||im._sh||!u||!/[?&]t=360\b/.test(u))return;im._sh=1;const big=String(u).replace(/([?&])t=360\b/,"$1t="+CASE_T),pre=new Image();pre.decoding="async";pre.src=big;
  // (swapped only once the small one has settled: changing src while its own decode is pending would fail that decode,
  // and imgEl would mark the picture bad and leave the placeholder showing)
  const swap=()=>{if(el._u===u&&im.isConnected)im.src=big};
  pre.decode().then(()=>im.classList.contains("on")?swap():(im.decode?im.decode().then(swap,()=>{}):swap())).catch(()=>{})}
function casePaint(P,art,o){o=o||{};art=art||{};const t=P.title||"";
  slot(P.f,art.front,phTitle(t,"front"));caseShape(P,studio&&studio.sys);slot(P.s,art.spine,`<div class="bsa-ph spine"><span>${esc(t)}</span></div>`);slot(P.bk,art.back,phTitle("","back"));caseSharp(P.f);caseSharp(P.s);caseSharp(P.bk);
  slot(P.disc,art.disc,'<div class="bsa-ph disc"></div>');P.disc.classList.toggle("none",!art.disc&&!o.discBig);P.disc.classList.toggle("big",!!o.discBig);P.disc.classList.toggle("cart",isCartSys(studio&&studio.sys));P.turn.parentNode.classList.toggle("discmode",!!o.discBig);
  P.turn.classList.toggle("turned",!!o.turned);
  /* choosing a spine: the spine alone, standing up big, instead of a thin edge of the turning case */
  if(P.sb){P.turn.parentNode.classList.toggle("spinemode",!!o.spineBig);if(o.spineBig){slot(P.sb,art.spine,`<div class="bsa-ph spine"><span>${esc(t)}</span></div>`);caseSharp(P.sb)}}caseSharp(P.disc);P.disc.classList.toggle("still",!!o.still);
  if(o.k!=null&&P.k.textContent!==o.k)P.k.textContent=o.k;if(o.what!=null&&P.what.textContent!==o.what)P.what.textContent=o.what;if(o.why!=null&&P.why.textContent!==o.why)P.why.textContent=o.why}

/* this game's mix: a row per part with its looks, the case on the right */
// the list is loaded and this game isn't in it (a rescan dropped it): say so instead of "Loading…" for ever
// the whole-game lock switch under the parts list (A / click on it, Y anywhere on the game): on = every part stays as it is now
function lockBarPaint(st,g){const el=sEls.lock;if(!el)return;const fz=isFrozen(g),busy=!!sBusy[ck(st.sys,st.file,"*")];
  el.classList.toggle("on",fz);el.classList.toggle("busy",busy);
  const t=!g?"":busy?"One moment…":fz?"Every part stays as it is. Shelf styles and auto-fetch skip it.":"Off: a style change replaces picks too.";
  const s2=fz?"On":"Off",tx=el.querySelector(".tx span"),sw=el.querySelector(".v");if(tx.textContent!==t)tx.textContent=t;if(sw.textContent!==s2)sw.textContent=s2}
function artPatch(g,part,art){if(!g)return;g.art=g.art||{};if(typeof art==="string")g.art[part]=art;else if(art&&typeof art==="object")PARTS.forEach(p=>{if(p in art)g.art[p]=art[p]});if(g.low)g.low[part]=false}
function afterChange(sys,file,part){Object.keys(sCands).forEach(k=>{if(k===ck(sys,file,part))sCands[k].dirty=true});loadGames(sys,true);sInfoBusy=false;loadStudio()}
async function mixApply(sys,file,part,look){const k=ck(sys,file,part);if(sBusy[k])return;const g=gameOf(sys,file);sBusy[k]=look;play("tick",1);bump();
  try{const r=await api.artMix({systemId:sys,file,part,look});artPatch(g,part,r&&r.art);if(g){g.lock=g.lock||{};g.lock[part]=look==="shelf"?null:{look,key:r&&r.key}}play("toast");
    toast(look==="shelf"?`${PART_L[part]} follows the shelf style again`:`${PART_L[part]}: ${lookName(look)} · your pick`)}
  catch(e){toast(errMsg(e));play("error")}delete sBusy[k];afterChange(sys,file,part);bump()}
async function undoPart(sys,file,part){const k=ck(sys,file,part);if(sBusy[k]||!api.artUndo)return;const g=gameOf(sys,file);sBusy[k]="undo";bump();
  try{const r=await api.artUndo({systemId:sys,file,part});artPatch(g,part,r&&r.art);if(g){g.lock=g.lock||{};g.lock[part]=r&&"lock" in r?r.lock:{look:"exact"}}play("back");toast(`${PART_L[part]}: the previous picture is back`)}
  catch(e){toast(errMsg(e));play("error")}delete sBusy[k];afterChange(sys,file,part);bump()}
// Y (games grid, the game's mix) or the switch: lock the whole game as it is, or put it back on the shelf style. Unlocking
// re-picks only the parts that differ from the style, in the background (main's 'art:job' kind 'game' says when it's done)
async function toggleLock(sys,file){const g=gameOf(sys,file),k=ck(sys,file,"*");if(!g||sBusy[k]||!api.artLock)return;const want=!isFrozen(g);sBusy[k]="lock";bump();
  try{const r=await api.artLock({systemId:sys,file,lock:want});g.frozen=!!(r&&r.frozen);if(r&&r.lock)g.lock=r.lock;play(want?"toast":"back");
    if(want)toast(`${truncEnd(g.title,40)}: locked as it is`);
    else{const pk=PARTS.filter(p=>g.lock&&g.lock[p]).length,own=pk?" · your picks stay until the shelf style changes":"";toast(r&&r.repick?`Unlocked · re-picking ${r.repick} part${r.repick===1?"":"s"} for the shelf style${own}`:pk?`Unlocked${own}`:"Unlocked · already matches the shelf style");
      }}
  catch(e){toast(errMsg(e));play("error")}delete sBusy[k];afterChange(sys,file,"*");bump()}
// a part re-picked after an unlock: "Fetching…" on its row until main says the game is done
function onGameJob(e){const sys=e.systemId,file=e.file;if(!sys||!file)return;const parts=Array.isArray(e.parts)?e.parts.filter(p=>PARTS.includes(p)):[];
  if(e.state==="run"){parts.forEach((p,i)=>{const k=ck(sys,file,p);if(i>=(e.done||0)){if(!sBusy[k])sBusy[k]="shelf"}else if(sBusy[k]==="shelf")delete sBusy[k]})}
  else{parts.forEach(p=>{const k=ck(sys,file,p);if(sBusy[k]==="shelf")delete sBusy[k]});if(isOpen&&e.msg&&e.msg!=="Nothing needed changing"){const g=gameOf(sys,file);toast(`${g?truncEnd(g.title,30)+": ":""}${e.msg}`)}
    if(sGames[sys])sGames[sys].stale=true;if(isOpen&&studio&&studio.sys===sys)loadGames(sys,true)}
  if(isOpen&&studio&&studio.sys===sys)bump()}
// "My picture…": main opens the file picker (the screen never sends a path); the picture is copied in as the part's pick (not a lock)
async function ownPicture(sys,file,part,cut){const k=ck(sys,file,part);if(sBusy[k]||!api.artOwn)return;const g=gameOf(sys,file);sBusy[k]="own";play("open");bump();
  try{const r=await api.artOwn({systemId:sys,file,part,cut:!!cut});if(r&&r.canceled){delete sBusy[k];bump();return}
    artPatch(g,part,r&&r.art);if(g){g.lock=g.lock||{};g.lock[part]=(r&&r.lock)||{look:"exact",key:"own"}}play("toast");toast(`${PART_L[part]}: your own picture · your pick`)}
  catch(e){toast(errMsg(e));play("error")}delete sBusy[k];afterChange(sys,file,part);bump()}

/* Pick exact: the parts on the left (LB / RB), every version found in the middle, the case on the right */
// the disc part is the mockup's disc screen: one full-width row of big discs (no parts list, no case), the chosen one spins
/* 1.72: filters above the versions (Region / Source / Type / Edition; one value each, for this session, across games) and
   Matching sets (the "Matching sets" row at the top of the parts list: a whole front + back + spine + disc at once) */
const FLT_DIMS=[["region","Region",{usa:"USA",eur:"Europe",jpn:"Japan"}],["source","Source",{launchbox:"LaunchBox",gametdb:"GameTDB",screenscraper:"ScreenScraper",steamgriddb:"SteamGridDB",libretro:"libretro"}],["kind","Type",{scan:"Scans",clean:"Clean",hd:"HD"}],["edition","Edition",{original:"Original",reprint:"Reprint",special:"Special"}]];
let pkF={region:"all",source:"all",kind:"all",edition:"all"};
const fltOn=()=>FLT_DIMS.some(([d])=>pkF[d]!=="all");
const fltReal=x=>x.source!=="current"&&x.source!=="earlier";
// the values a dimension has among this part's versions, in the chip's order; a dimension shows only when it would change
// something (two values or more), or while it filters (so it can be cleared)
function fltVals(list,d,names){const have=new Set(list.filter(fltReal).map(x=>x[d]||""));return Object.keys(names).filter(v=>have.has(v))}
function fltChips(list){const out=[];for(const [d,lab,names] of FLT_DIMS){const vals=fltVals(list,d,names),n=new Set(list.filter(fltReal).map(x=>x[d]||"")).size;if(pkF[d]!=="all"||(vals.length&&n>=2))out.push({d,lab,names,vals})}return out}
const fltPass=x=>!fltReal(x)||FLT_DIMS.every(([d])=>pkF[d]==="all"||(x[d]||"")===pkF[d]); // the one in use and earlier ones always show
let sSets={};const SETS_ON=()=>!!api.artSets;
function loadSets(sys,file,force){const k=ck(sys,file,"sets");let e=sSets[k];if(e&&(e.busy||(e.list&&!force)))return;if(!e)e=sSets[k]={list:null};
  e.busy=Promise.resolve(api.artSets({systemId:sys,file})).then(r=>{e.list=(r&&r.sets||[]).map(x=>({...x,set:true,key:"set-"+x.id}));e.err=null;e.gen=(e.gen||0)+1}).catch(x=>{e.err=errMsg(x);e.list=e.list||[]}).finally(()=>{e.busy=null;if(isOpen&&studio&&studio.v==="pick")bump()})}
// a set's mini preview arrived (art:cand with set: true): the pictures of every set that uses it
function onSetCand(e){const S=sSets[ck(e.systemId,e.file,"sets")];if(!S||!S.list)return;let hit=false;
  for(const x of S.list)for(const p of PARTS){const c=x.parts&&x.parts[p];if(c&&c.key===e.key){if(e.thumb)c.thumb=e.thumb;if(e.failed)c.failed=true;hit=true}}
  if(hit&&isOpen&&studio&&studio.v==="pick"&&studio.view==="sets"){clearTimeout(onSetCand.t);onSetCand.t=setTimeout(()=>{if(!sEls.cards)return;sEls.cards.forEach(d=>{if(d._x&&d._x.set)setFill(d,d._x)});pickPreview()},60)}}
function setFill(d,x){const m=d.querySelector(".sx");if(!m)return;for(const p of PARTS){const el=m.querySelector("."+p[0]);const c=x.parts[p];slot(el,c&&c.thumb,`<div class="bsa-ph ${p==="disc"?"disc":p==="spine"?"spine":""}"></div>`)}}
// (1.80) the parts are tabs across the top (TAB_H with its gap), the case on the left, the versions on the right in as many
// columns as fit: a card is the picture with two short lines under it (no more four lines of text and a row of dots)
const TAB_H=68,TAB_GAP=18;
function pkGeo(){const G0=geo(),disc=!!(studio&&studio.part==="disc"&&studio.view!=="sets"),narrow=G0.w<1250;
  // (beta.3) the manual picker keeps its own left column (Manual, Lock this manual, the console's choice); the art picker's
  // parts became tabs in 1.80 (partsW 0), which had squeezed that column to nothing and pushed the first page off the screen
  const man=!!(studio&&studio.v==="mpick"),partsW=man?(narrow?260:300):0,pvW=narrow?340:400,gap=narrow?24:30,colW=G0.w-pvW-gap-(man?partsW+gap:0),cg=16,minW=disc?220:186,cols=Math.max(2,Math.min(6,Math.floor((colW+cg)/(minW+cg)))),cw=(colW-cg*(cols-1))/cols;
  // disc: a round picture as wide as the card (the same grid, not a layout of its own)
  const sets=!!(studio&&studio.view==="sets");
  const imW=cw-20,imH=Math.round(sets?imW*.78:disc?imW:studio&&studio.part==="spine"?imW*1.25:imW*10/7),ch=imH+(sets?150:84);return {disc,sets,partsW,pvW,gap,colW,cols,cg,cw,imW,imH,ch,rowH:ch+cg,vpH:G0.h-96-(man?0:TAB_H+TAB_GAP)}} // (vpH: under the tabs, the header and the filter row; the manual picker has no tabs)
// each part is either "Shelf style" (auto) or "Your pick" (until the shelf style changes; only "Lock this game" keeps a game as it is)
function partState(st,g,p){const l=lockOf(g,p),b=sBusy[ck(st.sys,st.file,p)],have=g&&g.art&&g.art[p],fz=isFrozen(g),low=have&&g.low&&g.low[p];
  if(b)return [b==="undo"?"Undoing…":b==="own"?"Choose a picture…":"Fetching…",l?"b-lock":"b-ok",l?"Your pick":"Shelf style"];
  if(l)return [(l.look==="exact"?(l.key==="own"||l.key==="chosen"?"Your own picture":l.key==="kept"?"Kept from before":"Picked by you"):`${lookName(l.look)} look`)+(fz?" · game locked":"")+(have?"":" · missing"),"b-lock","Your pick"];
  if(!have)return [fz?"Missing · game locked":"Missing · follows the shelf","b-miss","Shelf style"];
  return [(fz?"Game locked":"Follows the shelf")+(low?" · low-res":""),fz?"b-lock":low?"b-lo":"b-ok","Shelf style"]}
// after the candidates: what else you can do with the part (and, with nothing found, what to try)
// the parts list's extra rows: Manual (Manual Studio) after Disc, then the lock switch
const PK_MAN=PARTS.length,PK_LOCK=()=>PARTS.length+(api.manualStudio?1:0);
const ACT_L={own:"My picture…",cut:"Cut from a full box…",undo:"Undo",retry:"Try again",index:"Build the LaunchBox index",clearf:"Clear filters",undoset:"Undo set",sgdb:"Add SteamGridDB",sgdbfix:"Fix your SteamGridDB key"};
const ACT_S={own:"A picture of your own · copied, never moved",cut:"The box opened flat: back, spine and front",undo:"The picture before this one",retry:"Look for versions again",index:"One-time download, about 110 MB",clearf:"Show every version again",undoset:"All four parts as they were before the set",sgdb:"More front posters, many in HD · free key",sgdbfix:"The key wasn't accepted · no posters until it's fixed"};
// (beta.9) an empty "add an account" slot on the Front: SteamGridDB posters show only with the player's own key (Windows only;
// Android has its own studio). "" = nothing to suggest. ScreenScraper waits for Boxshelf's developer registration
const ACCT_KEY_SVG='<svg viewBox="0 0 24 24" width="46" height="46" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"><circle cx="8" cy="15" r="4"/><path d="M11 12l9-9M17 6l3 3M14 9l2 2"/></svg>';
const sgAsk=()=>window.bsPlatform&&bsPlatform.os==="android"?"":!svcSet("steamgriddb")?"sgdb":svcState("steamgriddb").state==="fail"?"sgdbfix":"";
function pkList(st,c){
  if(st.view==="sets"){const S=sSets[ck(st.sys,st.file,"sets")];if(!S||!S.list)return [];const k=(S.gen||0)+"|"+!!S.undo;if(S._k!==k){S._k=k;S._all=S.list.concat(S.undo?[{act:"undoset",key:"act-undoset"}]:[])}return S._all}
  const L0=c&&c.list?(c.sorted||c.list):[];if(!c||!c.list)return L0;const fk=JSON.stringify(pkF);if(c._fk!==fk||c._fsrc!==L0){c._fk=fk;c._fsrc=L0;c._fl=fltOn()?L0.filter(fltPass):L0}const L=c._fl,A=[];
  if(!L.length)A.push({act:c.note==="noindex"&&api.artBuildIndex?"index":"retry",key:"act-x"});
  const sg=st.part==="front"?sgAsk():"";if(sg)A.push({act:sg,key:"act-sgdb",acct:"steamgriddb"});
  if(api.artOwn){A.push({act:"own",key:"act-own"});if(st.part==="spine")A.push({act:"cut",key:"act-cut"})}
  if(api.artUndo)A.push({act:"undo",key:"act-undo"});
  if(!L.length&&L0.length)A.length=0,A.push({act:"clearf",key:"act-clearf"}),api.artOwn&&A.push({act:"own",key:"act-own"}); // every version filtered out: Clear filters comes first
  const ak=st.part+"|"+L.length+"|"+(c.note||"")+"|"+(c.gen||0)+"|"+sg;if(c._actK!==ak||c._src!==L){c._actK=ak;c._src=L;c._all=L.concat(A)}return c._all}
// the frame: parts on the left, the cards in the middle, the case (and the neighbours strip) on the right. Built once per
// game; another part, a list arriving or a duplicate dropping out only lays the cards out again (pickCards)
function pickBuild(st){const g=gameOf(st.sys,st.file);if(st.zone===undefined){st.zone="parts";st.pr=prOf(st)} // a game opens on its parts list
  E.bsa.innerHTML=`<div class="bsa-pk"><div class="bsa-parts"><div class="bsa-ghl"></div>${SETS_ON()?`<div class="bsa-part sets" data-r="-1"><div class="th bsa-sx4"><i></i><i></i><i></i><i></i></div><div class="tx"><div class="nm">Sets</div><div class="st">Front, back, spine and disc that belong together</div></div><span class="badge b-alt">Sets</span></div>`:""}${PARTS.map((p,r)=>`<div class="bsa-part" data-r="${r}"><div class="th ${p==="spine"?"tall":p==="disc"?"round":""}"></div><div class="tx"><div class="nm">${PART_N[p]}</div><div class="st"></div></div><span class="badge"></span></div>`).join("")}${api.manualStudio?`<div class="bsa-part man" data-r="${PK_MAN}"><div class="th bsm-th"><div class="bsa-ph mini"><span>📖</span></div></div><div class="tx"><div class="nm">Manual</div><div class="st">Every manual found for this game</div></div><span class="badge b-ok">Manuals</span></div>`:""}<div class="bsa-glock mini" role="switch"><span class="ic">${LOCK_SVG}</span><div class="tx"><b>Lock this game</b><span></span></div><div class="sw"><i></i></div><span class="v"></span></div></div>
    <div class="bsa-cw"><div class="bsa-ch"><h3></h3><span></span></div><div class="bsa-flt"></div><div class="bsa-cvp"><div class="bsa-cgrid"></div></div></div><div class="bsa-pv"></div></div>`;
  sEls={pk:E.bsa.querySelector(".bsa-pk"),flt:E.bsa.querySelector(".bsa-flt"),setsRow:E.bsa.querySelector(".bsa-part.sets"),parts:[...E.bsa.querySelectorAll(".bsa-part:not(.man):not(.sets)")],man:E.bsa.querySelector(".bsa-part.man"),hl:E.bsa.querySelector(".bsa-ghl"),h3:E.bsa.querySelector(".bsa-ch h3"),ch:E.bsa.querySelector(".bsa-ch span"),grid:E.bsa.querySelector(".bsa-cgrid"),cards:new Map(),selI:-1,y:-1,cardsK:null,chl:null,P:caseBuild(E.bsa.querySelector(".bsa-pv"),g?g.title:st.title)};
  // (1.80) the parts as tabs across the top; the picture in use now, small, beside the one you're trying
  sEls.pk.classList.add("ptabs");E.bsa.querySelector(".bsa-pv").insertAdjacentHTML("afterbegin",'<div class="bsa-now"><span>Now</span><div class="im"></div></div>');sEls.now=E.bsa.querySelector(".bsa-now");sEls.tabX=null;
  sEls.lock=E.bsa.querySelector(".bsa-glock");sEls.lock.onclick=()=>pickInput("y");if(sEls.man)sEls.man.onclick=()=>openManualFromArt();
  if(sEls.setsRow)sEls.setsRow.onclick=()=>{const s2=studio;s2.view="sets";s2.zone=null;s2.pr=null;s2.i=0;s2.y=0;loadSets(s2.sys,s2.file);render()};
  fadeFix(E.bsa.querySelector(".bsa-cvp"));sEls.parts.forEach((d,r)=>{d.onclick=()=>{studio.zone=null;studio.pr=null;if(studio.view==="sets"){studio.view=null;studio.part=PARTS[r];studio.i=-1;studio.y=0;loadCands(studio.sys,studio.file,studio.part);render();return}pickPart(r-PARTS.indexOf(studio.part))}});
  sEls.P.turn.onclick=()=>{studio.turned=!studio.turned;render()};sEls.P.disc.onclick=()=>{studio.still=!studio.still;render()}; // the case turns / the disc stops on a click
  // next to its neighbours: this game's spine between the games beside it on the shelf (mockup screen 2); only when the
  // canvas is tall enough (not at Extra large)
  const pv=E.bsa.querySelector(".bsa-pv");if(geo().h>=720){pv.classList.add("nb");pv.insertAdjacentHTML("beforeend",'<div class="bsa-nb"><div class="row"></div><div class="cap">Next to its neighbours</div></div>');sEls.P.nb=pv.querySelector(".bsa-nb");sEls.P.nbRow=sEls.P.nb.firstChild}}
function pickSig(st){const c=sCands[ck(st.sys,st.file,st.part)];if(st.view==="sets"){const S=sSets[ck(st.sys,st.file,"sets")];return ["sets",!S||!S.list?"w":S.list.length,S&&S.gen||0,S&&S.undo?1:0,S&&S.err?"e":""].join("|")}
  return [st.part,JSON.stringify(pkF),!c||c.list===null?"w":c.list.length,c&&c.gen||0,c&&c.note||"",c&&c.err?"e":"",sIndexBusy?1:0].join("|")}
function pickCards(st){const c=sCands[ck(st.sys,st.file,st.part)],pg=pkGeo(),gr=sEls.grid,newPart=sEls.cardsPart!==st.part;sEls.cardsPart=st.part;
  if(c&&c.dirty&&!c.busy){c.dirty=false;loadCands(st.sys,st.file,st.part,true)}
  const dm=sEls.pk.classList.contains("disc")!==pg.disc;sEls.pk.classList.toggle("disc",pg.disc);sEls.pk.classList.toggle("cart",isCartSys(st.sys));sEls.pk.style.gridTemplateColumns=`${pg.pvW}px ${pg.colW}px`;sEls.pk.style.gap=pg.gap+"px";sEls.h3.textContent=st.view==="sets"?"Matching sets":PART_L[st.part];if(dm)fadeFix(E.bsa.querySelector(".bsa-cvp"));
  sEls.cards.forEach(d=>d.remove());sEls.cards.clear();sEls.selI=-1;sEls.y=-1;sEls.chl=null;gr.innerHTML="";gr.style.height="";gr.classList.toggle("still",!!st.still);
  // another part starts at the top without the scroll animation (the old cards are gone already)
  if(newPart){gr.style.transition="none";gr.style.transform="";requestAnimationFrame(()=>requestAnimationFrame(()=>{gr.style.transition=""}))}
  const empty=h=>{gr.style.transform="";st.y=0;gr.innerHTML=h};
  if(st.view==="sets"){const S=sSets[ck(st.sys,st.file,"sets")];if(!S||!S.list){empty(`<div class="bsa-empty">Looking for front, back, spine and disc that belong together…</div>`);return}
    if(!S.list.length&&!S.undo){empty(`<div class="bsa-empty card"><b>No complete set for this game yet</b><p>${esc(S.err||"No source has a front, back, spine and disc from one release. Pick each part on its own.")}</p></div>`);return}}
  else if(!c||c.list===null){empty(`<div class="bsa-empty">Looking for every version of the ${esc(PART_L[st.part].toLowerCase())}…</div>`);return}
  const L=pkList(st,c);
  if(st.i>=L.length)st.i=L.length-1;if(st.i<0){const u=L.findIndex(x=>x.inUse);st.i=Math.max(0,u);st.y=0}
  gr.style.height=Math.ceil(L.length/pg.cols)*pg.rowH+"px";
  sEls.chl=document.createElement("div");sEls.chl.className="bsa-chl"+(pg.disc?" dk":"");sEls.chl.style.cssText=`width:${pg.cw}px;height:${pg.ch}px`;gr.prepend(sEls.chl)}
function cardHtml(x,pg,part,c){
  if(x.set){const SN=({launchbox:"LaunchBox",gametdb:"GameTDB",screenscraper:"ScreenScraper",mixed:"Mixed sources"})[x.source]||x.source;
    return `<div class="in set"><div class="im sx" style="height:${pg.imH}px"><i class="f"></i><i class="s"></i><i class="b"></i><i class="d"></i></div><div class="ed" title="${esc(x.label)}">${esc(x.label.replace(/ · complete$/,""))}</div><div class="src">${esc(SN)} · complete</div>
    <div class="meta">${x.region?`<span class="chip reg">${esc(REG_S[x.region]||x.region.toUpperCase())}</span>`:""}${x.hd?'<span class="chip sz hd">HD</span>':""}${x.same?'<span class="chip">One full scan</span>':""}</div></div><div class="use"></div>`}
  if(x.act)return `<div class="in act${x.acct?" acct":""}"><div class="im${part==="disc"?" disc":""}" style="height:${pg.imH}px${part==="disc"?`;width:${pg.imH}px`:""}"><span>${x.acct?ACCT_KEY_SVG:x.act==="undo"?"↶":x.act==="retry"||x.act==="index"?"↻":"+"}</span></div><div class="ed">${esc(x.act==="index"&&sIndexBusy?"Building…":ACT_L[x.act])}</div><div class="src">${esc(ACT_S[x.act])}</div></div><div class="use"></div>`;
  let disc=part==="disc",sz=x.w&&x.h?`${x.w} × ${x.h}`:"",short=Math.min(x.w||1e9,x.h||1e9),lim=part==="spine"?100:part==="disc"?500:700,low=x.w&&x.h&&short<lim,hd=x.kind==="hd"||(x.w&&x.h&&short>=(part==="spine"?160:1500));if(hd)low=false; // (a SteamGridDB 600×900 "HD" poster isn't also "Low-res")
  // (1.80) two short lines: where it's from and what it is ("Europe · scan" / "LaunchBox"), then its size and any warning
  const RG={usa:"USA",eur:"Europe",jpn:"Japan",world:"World"},where=x.source==="current"?"Your current file":x.source==="earlier"?"An earlier pick":[RG[x.region]||(x.region?x.region.toUpperCase():""),KIND_N[x.kind]||x.kind||""].filter(Boolean).join(" · ")||String(x.label||"Version");
  return `<div class="in"><div class="im${disc?" disc":part==="spine"?" sp":""}" style="height:${pg.imH}px${disc?`;width:${pg.imH}px`:part==="spine"?`;--L:${Math.round(pg.cw-28)}px`:""}"></div><div class="ed" title="${esc(x.label||"")}">${esc(where)}</div>
    <div class="meta"><span class="src">${esc(x.source==="current"||x.source==="earlier"?"":SRC_N[x.source]||x.source||"")}</span><span class="chip sz${hd?" hd":""}${sz?"":" wait"}">${sz?(hd?"HD":low?"Low-res":"Sharp"):"Size …"}</span>${x.failed?'<span class="chip warn">Couldn\'t download</span>':""}${x.edition==="reprint"?'<span class="chip warn">Reprint</span>':x.edition==="special"?'<span class="chip sp">Special</span>':""}</div></div><div class="use"></div>`}
const cardPh=(part,x)=>`<div class="bsa-ph ${part==="disc"?"disc":x&&x.stopped?"":"wait"}"></div>`;
function pickFill(st){const c=sCands[ck(st.sys,st.file,st.part)],L=pkList(st,c),pg=pkGeo(),y0=Math.min(sEls.y<0?st.y:sEls.y,st.y),y1=Math.max(sEls.y<0?st.y:sEls.y,st.y);
  const r0=Math.max(0,Math.floor(y0/pg.rowH)-1),r1=Math.ceil((y1+pg.vpH)/pg.rowH)+1,i0=r0*pg.cols,i1=Math.min(L.length,(r1+1)*pg.cols);
  sEls.cards.forEach((d,i)=>{if(i<i0||i>=i1){d.remove();sEls.cards.delete(i)}});
  for(let i=i0;i<i1;i++){if(sEls.cards.has(i))continue;const x=L[i],d=document.createElement("div");d.className="bsa-card"+(pg.disc?" dk":"")+(x.failed?" failed":"");d._x=x;d.style.cssText=`width:${pg.cw}px;height:${pg.ch}px;transform:translate(${(i%pg.cols)*(pg.cw+pg.cg)}px,${Math.floor(i/pg.cols)*pg.rowH}px)`;
    d.innerHTML=cardHtml(x,pg,st.part,c);if(x.set)setFill(d,x);else if(!x.act)slot(d.querySelector(".im"),x.thumb,cardPh(st.part,x));
    d.onmouseenter=()=>{if(device==="mouse"&&(studio.i!==i||studio.zone)){studio.i=i;studio.zone=null;render()}};d.onclick=()=>{studio.i=i;studio.zone=null;pickInput("a")};
    sEls.grid.append(d);sEls.cards.set(i,d)}}
// one card's thumbnail or size arrived: patch just that card
function patchCard(x){if(!sEls.cards||!studio||studio.v!=="pick"||studio.view==="sets")return;const st=studio,c=sCands[ck(st.sys,st.file,st.part)];if(!c)return;sEls.cards.forEach(d=>{if(d._x!==x)return;
  slot(d.querySelector(".im"),x.thumb,cardPh(st.part,x));const tmp=document.createElement("div");tmp.innerHTML=cardHtml(x,pkGeo(),st.part,c);
  const m=d.querySelector(".meta"),m2=tmp.querySelector(".meta");if(m&&m2&&m.innerHTML!==m2.innerHTML)m.innerHTML=m2.innerHTML;d.classList.toggle("failed",!!x.failed)})}
// the filter row: a chip per dimension that matters here (click / A cycles its value), then Clear filters. Always the same height,
// so the grid never moves when chips come and go
function fltPaint(st,c){const el=sEls.flt;if(!el)return;const sets=st.view==="sets",ch=sets||!c||!c.list?[]:fltChips(c.list),fz=st.zone==="flt";
  const items=ch.map(x=>({k:x.d,t:`${x.lab}: ${pkF[x.d]==="all"?"All":x.names[pkF[x.d]]||pkF[x.d]}`,on:pkF[x.d]!=="all"})).concat(fltOn()&&!sets?[{k:"clear",t:"Clear filters",on:false}]:[]);
  st.fItems=items.map(x=>x.k);if(st.fi>=items.length)st.fi=Math.max(0,items.length-1);
  const sig=items.map(x=>x.k+x.t+x.on).join()+"|"+(fz?st.fi:-1)+"|"+device;if(el._s===sig)return;el._s=sig;
  el.classList.toggle("cur",fz);
  el.innerHTML=items.length?items.map((x,j)=>`<i class="${x.on?"on ":""}${x.k==="clear"?"clr ":""}${fz&&st.fi===j?"cur":""}" data-k="${x.k}">${esc(x.t)}</i>`).join("")+`<span class="k">${sets?"":G("view","F")} Filters</span>`:sets?"":`<span class="k none">One kind of version here · nothing to filter</span>`;
  el.querySelectorAll("i").forEach((it,j)=>it.onclick=e=>{e.stopPropagation();studio.fi=j;fltStep(studio,it.dataset.k)})}
// one step of a dimension's value (All → each value present → All); "clear" empties every filter. The grid keeps the cursor on
// the same version where it's still shown
function fltStep(st,k){const c=sCands[ck(st.sys,st.file,st.part)],cur=pkList(st,c)[st.i];
  if(k==="clear")pkF={region:"all",source:"all",kind:"all",edition:"all"};
  else{const D=FLT_DIMS.find(x=>x[0]===k);if(!D||!c||!c.list)return;const vals=["all",...fltVals(c.list,k,D[2])],i=vals.indexOf(pkF[k]);pkF={...pkF,[k]:vals[(i+1)%vals.length]}}
  play("tick",1);const L=pkList(st,c),j=cur?L.indexOf(cur):-1;st.i=j>=0?j:0;st.y=0;sSig2();render()}
const prOf=st=>st.view==="sets"?-1:PARTS.indexOf(st.part); // the parts-list row of what the grid shows (-1: Matching sets)
const sSig2=()=>{if(sEls)sEls.cardsK=null}; // the cards are laid out again (their count changed), the frame stays
function pickUpdate(st){const g=gameOf(st.sys,st.file),c=sCands[ck(st.sys,st.file,st.part)],L=pkList(st,c);lockBarPaint(st,g);fltPaint(st,c);
  const inParts=st.zone==="parts",onMan=inParts&&st.pr===PK_MAN&&!!sEls.man;sEls.pk.classList.toggle("inparts",inParts);sEls.pk.classList.toggle("onman",onMan);if(sEls.lock)sEls.lock.classList.toggle("cur",inParts&&st.pr===PK_LOCK());sEls.hl.classList.toggle("focus",inParts&&st.pr!==PK_LOCK());if(sEls.man)sEls.man.classList.toggle("on",onMan);
  const sig=pickSig(st);if(sEls.cardsK!==sig){sEls.cardsK=sig;pickCards(st)}
  const onSets=st.view==="sets"&&!onMan;if(sEls.setsRow){sEls.setsRow.classList.toggle("on",onSets);const S=sSets[ck(st.sys,st.file,"sets")],t=S&&S.list?(S.list.length?`${S.list.length} complete set${S.list.length===1?"":"s"} found`:"No complete set yet"):"Front, back, spine and disc that belong together",se=sEls.setsRow.querySelector(".st");if(se.textContent!==t)se.textContent=t}
  sEls.parts.forEach((d,r)=>{const p=PARTS[r],[s,b,bt]=g?partState(st,g,p):["","",""];d.classList.toggle("on",p===st.part&&!onMan&&!onSets);const se=d.querySelector(".st"),be=d.querySelector(".badge");if(se.textContent!==s)se.textContent=s;if(be.textContent!==bt){be.textContent=bt;be.className="badge "+b}
    slot(d.querySelector(".th"),g&&g.art&&g.art[p],'<div class="bsa-ph mini"></div>')});
  // the highlight slides along the tabs (their places read once after a build, then kept)
  {const ti=(onSets?-1:onMan?PK_MAN:PARTS.indexOf(st.part))+(sEls.setsRow?1:0);if(!sEls.tabX)sEls.tabX=[...sEls.pk.querySelectorAll(".bsa-parts > .bsa-part")].map(e=>[e.offsetLeft,e.offsetWidth]);const p=sEls.tabX[ti]||[0,0],ht=`translateX(${p[0]}px)`;
    if(sEls.hl._t!==ht){sEls.hl._t=ht;sEls.hl.style.transform=ht;sEls.hl.style.width=p[1]+"px"}}
  const n=L.length,nc=c&&c.list?c.list.length:0;if(sEls.ch){const off=c&&c.note==="offline",idx=c&&c.note==="noindex",prep=c&&c.note==="preparing";const t=c&&c.list?(!nc?(off||c.lost?"couldn't download any of them · check the connection, then Try again":idx?"the LaunchBox index isn't built yet":prep?"getting the LaunchBox index ready…":c.err?c.err:"nothing found for this part yet · use a picture of your own"):n?(c.list.every(x=>x.failed)?"couldn't download any of them · check the connection":c.list.length===1&&c.list[0].source==="current"?"only one source for this game":`${nc} version${nc===1?"":"s"} · best match for the shelf style first${c.note==="noindex"?" · LaunchBox index not built yet":""}`):""):"";if(sEls.ch.textContent!==t)sEls.ch.textContent=t}
  if(onSets&&sEls.ch){const S=sSets[ck(st.sys,st.file,"sets")],n2=S&&S.list?S.list.length:null,t=n2==null?"looking…":n2?`${n2} complete set${n2===1?"":"s"} · A puts all four parts in at once`:"no complete set: pick each part on its own";if(sEls.ch.textContent!==t)sEls.ch.textContent=t}
  else if(fltOn()&&sEls.ch&&c&&c.list&&c.list.length){const nf=c._fl?c._fl.length:0,t=`${nf} of ${c.list.length} versions · filtered`;if(sEls.ch.textContent!==t)sEls.ch.textContent=t}
  if(onMan){const t="Every manual for this game · A opens them";if(sEls.ch.textContent!==t)sEls.ch.textContent=t;if(sEls.h3.textContent!=="Manual")sEls.h3.textContent="Manual"}else{const h=onSets?"Matching sets":PART_L[st.part];if(sEls.h3.textContent!==h)sEls.h3.textContent=h}
  if(n){const pg=pkGeo(),r=Math.floor(st.i/pg.cols),top=r*pg.rowH,maxY=Math.max(0,Math.ceil(n/pg.cols)*pg.rowH-pg.vpH+20);
    if(top<st.y+6)st.y=Math.max(0,top-12);else if(top+pg.ch>st.y+pg.vpH-10)st.y=top+pg.ch-pg.vpH+26;st.y=Math.max(0,Math.min(st.y,maxY));
    pickFill(st);if(sEls.y!==st.y){sEls.grid.style.transform=st.y?`translateY(${-st.y}px)`:"";sEls.y=st.y}
    const want=inParts?-1:st.i;if(sEls.selI!==want){const a=sEls.cards.get(sEls.selI);if(a)a.classList.remove("sel");sEls.selI=want}const b=sEls.cards.get(want);if(b&&!b.classList.contains("sel"))b.classList.add("sel");if(sEls.chl)sEls.chl.style.opacity=inParts?"0":"";
    if(sEls.chl){const t=`translate(${(st.i%pg.cols)*(pg.cw+pg.cg)}px,${r*pg.rowH}px)`;if(sEls.chl._t!==t){sEls.chl._t=t;sEls.chl.style.transform=t}}
    sEls.grid.classList.toggle("still",!!st.still);
    // badges: In use (the one on your shelf now) and Best match (the shelf style's first choice, when it isn't the one in use)
    const lk=lockOf(g,st.part),best=!fltOn()&&st.view!=="sets"&&L.length>1&&L[0]&&!L[0].act&&!L[0].inUse?L[0]:null;sEls.cards.forEach(d=>{const x=d._x,u=d.querySelector(".use"),t=x.inUse?(lk&&lk.look==="exact"&&lk.key===x.key?"In use · your pick":"In use"):x===best?"Best match":"";if(u.textContent!==t){u.textContent=t;u.classList.toggle("on",!!t);u.classList.toggle("best",x===best&&!x.inUse)}})}
  pickPreview()}
const hue=s=>[...String(s||"")].reduce((a,c)=>a+c.charCodeAt(0)*7,0)%360;
const spinePh=g=>`<div class="bsa-ph spine" style="background:hsl(${hue(g.title)},38%,22%)"><span>${esc(g.title)}</span></div>`;
// the neighbours strip: up to three games before this one and two after, as they stand on the shelf; this game's spine
// shows the version under the cursor while you're choosing a spine
function nbPaint(st,x){const P=sEls.P;if(!P||!P.nb)return;const e=sGames[st.sys],L=e&&e.list||[],f=String(st.file).toLowerCase(),k=L.findIndex(g=>String(g.file).toLowerCase()===f);
  if(k<0){P.nb.classList.remove("on");return}
  const a=Math.max(0,Math.min(k-3,L.length-6)),S=L.slice(a,a+6),sig=a+"|"+S.length+"|"+(e.ver||0);
  if(P.nb._s!==sig){P.nb._s=sig;P.nbRow.innerHTML=S.map((g,j)=>`<div class="sp${a+j===k?" me":""}"></div>`).join("");P.nbEls=[...P.nbRow.children];S.forEach((g,j)=>{if(a+j!==k)slot(P.nbEls[j],g.art&&g.art.spine,spinePh(g))})}
  const g=L[k],u=st.part==="spine"&&x&&x.thumb?x.thumb:g.art&&g.art.spine;slot(P.nbEls[k-a],u,spinePh(g));P.nb.classList.add("on")}
function pickPreview(){const st=studio;if(!st||st.v!=="pick"||!sEls.P)return;
  if(st.view==="sets"){const g=gameOf(st.sys,st.file),S=sSets[ck(st.sys,st.file,"sets")],x=S&&S.list?pkList(st,null)[st.i]:null,art=Object.assign({},g&&g.art||{});
    if(x&&x.set)for(const p of PARTS){const c=x.parts[p];if(c&&c.thumb)art[p]=c.thumb}
    casePaint(sEls.P,art,{turned:st.turned,k:"On your shelf",what:x?(x.act?ACT_L[x.act]:x.label.replace(/ · complete$/,"")):"",why:x&&x.set?`Front, back, spine and disc together${x.same?", back and spine from one full scan":""}. A puts all four in as your picks.`:x&&x.act?ACT_S[x.act]+".":""});nbPaint(st,null);return}const g=gameOf(st.sys,st.file),c=sCands[ck(st.sys,st.file,st.part)],L=pkList(st,c),x0=L[st.i],x=x0&&!x0.act?x0:null;
  const art=Object.assign({},g&&g.art||{});if(x&&x.thumb)art[st.part]=x.thumb;const lk=lockOf(g,st.part);
  if(st.part!=="disc")delete art.disc; // (1.80) the disc stays out of the way of the cover, unless it's the part being chosen
  let why="";if(x){const sz=x.w&&x.h?`${x.w} × ${x.h} `:"",kind=x.kind==="hd"?"HQ picture":x.kind==="clean"?"clean picture":"scan";why=`${sz}${kind} from ${SRC_N[x.source]||x.source}.`;
    why+=x.inUse?(lk&&lk.look==="exact"&&lk.key===x.key?" In use · your pick: auto-fetch leaves it, a shelf style change replaces it unless the game is locked.":" In use now.") :st.i===0&&L.length>1?` Best match for ${lookName(c.look)}.`:"";if(x.edition==="reprint")why+=" Has a reprint banner.";}
  if(x0&&x0.act){why=ACT_S[x0.act]+".";}
  // (1.80) trying another version: the case wears it ("Trying"), and the picture on your shelf now shows small beside it
  const trying=!!(x&&x.thumb&&!x.inUse&&st.zone!=="parts"),cur=g&&g.art&&g.art[st.part];
  if(sEls.now){sEls.now.classList.toggle("on",trying&&!!cur);if(trying&&cur)slot(sEls.now.querySelector(".im"),cur,"")}
  casePaint(sEls.P,art,{discBig:st.part==="disc",still:!!st.still,spineBig:st.part==="spine"&&!st.turned,turned:st.part==="back"?!st.turned:st.turned,k:trying?`Trying · ${btnLabel("a")} uses it`:"On your shelf",what:x?x.label||"":x0&&x0.act?ACT_L[x0.act]:"",why});nbPaint(st,x)}
// a whole set: all four downloaded and put in by main (nothing changes if one can't be had), each part your pick; Undo set
// (an action card after the sets) puts all four back
function setDirty(st){const pre=st.sys+"|"+String(st.file).toLowerCase()+"|";Object.keys(sCands).forEach(kk=>{if(kk.startsWith(pre))sCands[kk].dirty=true})}
async function useSetUI(st,x){const k=ck(st.sys,st.file,"*");if(sBusy[k])return;const g=gameOf(st.sys,st.file);sBusy[k]="set";PARTS.forEach(p=>sBusy[ck(st.sys,st.file,p)]="set");play("tick",1);toast(`Putting in ${x.label.replace(/ · complete$/,"")}…`);bump();
  try{const r=await api.artUseSet({systemId:st.sys,file:st.file,setId:x.id});if(g){artPatch(g,"front",r&&r.art);if(r&&r.lock)g.lock=r.lock}const S=sSets[ck(st.sys,st.file,"sets")];if(S)S.undo=true;play("toast");toast(`${x.label.replace(/ · complete$/,"")}: all four parts in · your pick`)}
  catch(e){toast(errMsg(e));play("error")}delete sBusy[k];PARTS.forEach(p=>delete sBusy[ck(st.sys,st.file,p)]);setDirty(st);afterChange(st.sys,st.file,"*");bump()}
async function undoSetUI(st){const k=ck(st.sys,st.file,"*");if(sBusy[k]||!api.artUndoSet)return;const g=gameOf(st.sys,st.file);sBusy[k]="undo";bump();
  try{const r=await api.artUndoSet({systemId:st.sys,file:st.file});if(g){artPatch(g,"front",r&&r.art);if(r&&r.lock)g.lock=r.lock}const S=sSets[ck(st.sys,st.file,"sets")];if(S)S.undo=false;play("back");toast("The four parts are back as they were")}
  catch(e){toast(errMsg(e));play("error")}delete sBusy[k];setDirty(st);afterChange(st.sys,st.file,"*");if(st.i>=pkList(st,null).length)st.i=0;bump()}
function pickPart(d){const st=studio,n=PARTS.indexOf(st.part)+d;if(n<0||n>=PARTS.length){play("error");return}st.part=PARTS[n];if(st.zone==="parts")st.pr=n;st.i=-1;st.y=0;play("tick",d);loadCands(st.sys,st.file,st.part);render()}
async function pickUse(st,x){const k=ck(st.sys,st.file,st.part);if(sBusy[k])return;if(x.inUse){const lk=lockOf(gameOf(st.sys,st.file),st.part);if(lk&&lk.look==="exact"&&lk.key===x.key){toast("Already in use");return}}
  const g=gameOf(st.sys,st.file),c=sCands[k];sBusy[k]="use";play("tick",1);bump();
  try{const r=await api.artUse({systemId:st.sys,file:st.file,part:st.part,key:x.key});artPatch(g,st.part,r&&r.art);if(g){g.lock=g.lock||{};g.lock[st.part]={look:"exact",key:x.key}}if(c)c.list.forEach(z=>z.inUse=z.key===x.key);play("toast");toast(`${PART_L[st.part]}: ${x.label||"this version"} · your pick`)}
  catch(e){toast(errMsg(e));play("error")}delete sBusy[k];loadGames(st.sys,true);bump()}
function pickInput(a,rep){const st=studio,c=sCands[ck(st.sys,st.file,st.part)],L=pkList(st,c),pg=pkGeo(),g=gameOf(st.sys,st.file);
  // View / F: the filter row (and back out of it)
  if(a==="flt"){if(rep)return;if(st.zone==="flt"){st.zone=st.fPrev===undefined?"parts":st.fPrev;if(st.zone==="parts")st.pr=prOf(st);play("back");render();return}
    if(st.view==="sets"||!(st.fItems&&st.fItems.length)){toast(st.view==="sets"?"Sets aren't filtered":"Nothing to filter for this part");play("error");return}
    st.fPrev=st.zone;st.zone="flt";st.fi=0;play("open");render();return}
  if(st.zone==="flt"){const n=(st.fItems||[]).length;
    if(a==="left"||a==="right"){const k=st.fi+(a==="right"?1:-1);if(k>=0&&k<n){st.fi=k;play("tick",a==="right"?1:-1)}else if(!rep)play("error")}
    else if(a==="a"&&n){fltStep(st,st.fItems[st.fi]);return}
    else if(a==="down"){st.zone=st.fPrev===undefined?"parts":st.fPrev;if(st.zone==="parts")st.pr=prOf(st);play("tick",1)}
    else if(!rep&&a!=="up")play("error");
    render();return}
  if(a==="lb"||a==="rb"){if(st.view==="sets"){st.view=null;st.i=-1;st.y=0;st.part=PARTS[0];if(st.zone==="parts")st.pr=0;loadCands(st.sys,st.file,st.part);play("tick",1);render();return}pickPart(a==="rb"?1:-1);return}
  // X: this part back to the shelf style (it stops being your pick and is re-picked by the style)
  if(a==="x"&&st.view==="sets"){toast("Choose a part to put it back on the shelf style");return}
  if(a==="x"){if(lockOf(g,st.part)){const p=st.part;mixApply(st.sys,st.file,p,"shelf")}else toast(`${PART_L[st.part]} already follows the shelf style`);return}
  if(a==="y"){if(g)toggleLock(st.sys,st.file);return}
  // the parts tabs (zone "parts", 1.80 across the top): left / right change part (Matching sets first, then the Manual and the
  // lock switch at the end), A or down go into the versions, left from the first tab goes to the sidebar
  if(st.zone==="parts"){const r=st.pr!=null?st.pr:PARTS.indexOf(st.part);
    if(a==="left"||a==="right"){const n=r+(a==="right"?1:-1),lo=SETS_ON()?-1:0;if(n<lo){if(!rep){focus="side";play("tick",-1)}}else if(n>PK_LOCK()){if(!rep)play("error")}else{st.pr=n;
      if(n===-1){st.view="sets";st.i=0;st.y=0;loadSets(st.sys,st.file)} // Matching sets: the grid shows the sets
      else if(n<PARTS.length&&(PARTS[n]!==st.part||st.view==="sets")){st.view=null;st.part=PARTS[n];st.i=-1;st.y=0;loadCands(st.sys,st.file,st.part)}play("tick",a==="right"?1:-1)}}
    else if((a==="a"||a==="down")&&r===-1){st.zone=null;st.pr=null;st.i=0;play("open")}
    else if(a==="a"&&r===PK_MAN&&PK_LOCK()>PK_MAN){openManualFromArt();return} // Manual: this game's manuals (B comes back here)
    else if((a==="a"||a==="down")&&r<PARTS.length){if(!L.length){if(!rep)play("error")}else{const u=L.findIndex(x=>x.inUse);st.i=Math.max(0,u);st.zone=null;st.pr=null;play("open")}}
    else if(a==="a"&&r===PK_LOCK()){if(g)toggleLock(st.sys,st.file);return}
    else if(a==="up"||a==="down"){if(!rep)play("error")}
    render();return}
  if(!L.length){if(a==="up"&&!rep){st.zone="parts";st.pr=prOf(st);play("tick",-1);render()}return}
  if(a==="left"){if(st.i%pg.cols>0){st.i--;play("tick",-1)}else if(!rep)play("error")}
  else if(a==="right"){if(st.i<L.length-1&&st.i%pg.cols<pg.cols-1){st.i++;play("tick",1)}else if(!rep)play("error")}
  else if(a==="up"){if(st.i>=pg.cols){st.i-=pg.cols;play("tick",-1)}else if(!rep){st.zone="parts";st.pr=prOf(st);play("tick",-1)}}
  else if(a==="down"){if(st.i+pg.cols<L.length){st.i+=pg.cols;play("tick",1)}else if(Math.floor(st.i/pg.cols)<Math.floor((L.length-1)/pg.cols)){st.i=L.length-1;play("tick",1)}else if(!rep)play("error")}
  else if(a==="lt"||a==="rt"){st.i=Math.max(0,Math.min(L.length-1,st.i+(a==="rt"?2:-2)*pg.cols))}
  else if(a==="home"||a==="end")st.i=a==="home"?0:L.length-1; // End: the action cards (My picture…, Undo) at the end
  else if(a==="a"&&st.view==="sets"){const x=L[st.i];if(!x)return;if(x.act==="undoset")undoSetUI(st);else if(x.set)useSetUI(st,x);return}
  else if(a==="a"){const x=L[st.i];if(x&&x.act==="clearf"){fltStep(st,"clear");return}if(x&&x.acct){acctFromArt(st,x.acct);return}if(x&&x.act){const p=st.part;if(x.act==="own"||x.act==="cut")ownPicture(st.sys,st.file,p,x.act==="cut");else if(x.act==="undo")undoPart(st.sys,st.file,p);else if(x.act==="index"){if(!sIndexBusy)buildIndex(st)}else{c.list=null;sSig="";loadCands(st.sys,st.file,p,true);render()}return}
    if(x&&x.failed){if(c.list.every(z=>z.failed)){c.list=null;sSig="";loadCands(st.sys,st.file,st.part,true);toast("Trying again…");render();return}toast("That one couldn't be downloaded");play("error");return}if(x)pickUse(st,x);return}
  render()}

/* ---------- Manual Studio: every manual for a game, side by side (the box art picker's look and controls) ----------
   views: mgames (a console's games with Have / Missing / Incomplete / Locked chips), mpref (the console's one-tap choice:
   best English manual, or a language / region), mpick (one game: the Manual row and lock switch on the left, every manual
   found in the middle, the highlighted one's first page on the right). The list comes back with what's on disk at once;
   copies found online arrive one by one (onManualCand), each only once it's proven downloadable, so no card ever fails */
const MV={mgames:1,mpick:1,mpref:1};
let mInfo=null,mInfoBusy=false,mGames={},mCands={},mBusy={},mMsg={};
const mk=(sys,file)=>sys+"|"+String(file||"").toLowerCase();
const M_FILTERS=[["all","All"],["missing","Missing"],["incomplete","Incomplete"],["locked","Locked"]];
const M_ST={have:["","Have"],missing:["miss","Missing"],incomplete:["lo","Incomplete"]};
const M_SRC={current:"In use now",earlier:"Earlier",archive:"Internet Archive",gamesdatabase:"GamesDatabase",collection:"Manual collection",own:"Your PDF"};
const M_ACT_L={own:"My PDF…",undo:"Undo",retry:"Try again"},M_ACT_S={own:"A PDF of your own · copied, never moved",undo:"The manual before this one",retry:"Look for manuals again"};
const M_FLAG={best:"EN","region:eur":"EN · PAL","region:jpn":"日本語","lang:fr":"FR","lang:de":"DE","lang:es":"ES","lang:it":"IT"};
const mCon=sys=>mInfo&&mInfo.consoles.find(c=>c.id===sys)||null;
const mName=sys=>(mCon(sys)||{}).name||conName2(sys);
const prefName=id=>{const p=mInfo&&(mInfo.prefs||[]).find(x=>x.id===(id||"best"));return p?p.label:"Best English manual"};
const mShort=x=>String(x.label||"Manual").replace(/^(Internet Archive|GamesDatabase|Collection scan|Official digital manual) · /,"").replace(/ · Earlier.*$/,"");
const mgGame=(sys,file)=>{const e=mGames[sys],f=String(file||"").toLowerCase();return e&&e.list&&e.list.find(g=>String(g.file).toLowerCase()===f)||null};
function msLoadInfo(){if(!api.manualStudio||mInfoBusy)return;mInfoBusy=true;
  Promise.resolve(api.manualStudio()).then(r=>{mInfo={consoles:Array.isArray(r&&r.consoles)?r.consoles:[],prefs:(r&&r.prefs)||[]};
    mInfo.consoles.forEach(c=>{const j=c.job;if(j&&j.jobId&&!sJobs[j.jobId]){jobSys[j.jobId]=c.id;sJobs[j.jobId]={id:j.jobId,label:j.label,done:j.done||0,total:j.total||0,state:"run",man:1}}});paintJob()})
    .catch(e=>{mInfo=mInfo||{consoles:[],prefs:[],err:errMsg(e)}}).finally(()=>{mInfoBusy=false;if(isOpen)bump()})}
function msLoadGames(sys){let e=mGames[sys];if(e&&e.busy)return;if(!e)e=mGames[sys]={list:null};
  e.busy=Promise.resolve(api.manualGames({systemId:sys})).then(l=>{e.list=Array.isArray(l)?l:[];e.err=null;e.stale=false;e.ver=(e.ver||0)+1}).catch(x=>{e.err=errMsg(x);e.list=e.list||[]})
    .finally(()=>{e.busy=null;if(isOpen&&studio&&studio.sys===sys&&MV[studio.v])bump()})}
function msLoadCands(sys,file,force){const k=mk(sys,file);let c=mCands[k];if(c&&(c.busy||(!force&&c.list&&!c.dirty)))return;if(!c)c=mCands[k]={list:null};c.dirty=false;c.early=[];
  c.busy=Promise.resolve(api.manualCands({systemId:sys,file})).then(r=>{c.list=Array.isArray(r&&r.list)?r.list:[];c.searching=!!(r&&r.searching);c.expect=r&&r.expect||{};c.locked=!!(r&&r.locked);c.canUndo=!!(r&&r.canUndo);c.pref=r&&r.pref||"best";c.err=null;c.offline=false;c.sorted=false;c.gen=(c.gen||0)+1})
    .catch(x=>{c.err=errMsg(x);c.list=c.list||[];c.searching=false})
    .finally(()=>{c.busy=null;const early=c.early;c.early=null;(early||[]).forEach(onManCand);if(isOpen&&studio&&studio.v==="mpick")bump()})}
function msLoad(st){if(!mInfo)msLoadInfo();const e=mGames[st.sys];if(!e||(!e.list&&!e.busy)||e.stale)msLoadGames(st.sys);if(st.v==="mpick")msLoadCands(st.sys,st.file)}
// one card arrived (appended: the cursor never jumps), or the search ended: the whole list comes again, best match first
function onManCand(e){if(!e)return;const c=mCands[mk(e.systemId,e.file)];if(!c)return;if(c.busy&&c.early){c.early.push(e);return}if(!c.list)return;
  const here=isOpen&&studio&&studio.v==="mpick"&&mk(studio.sys,studio.file)===mk(e.systemId,e.file);
  if(e.done){const keep=here&&studio.zone!=="parts"?msList(c)[studio.i]:null;if(Array.isArray(e.list)){c.list=e.list;c.sorted=true}c.searching=false;c.offline=!!e.offline;c.expect=e.expect||c.expect;c.gen=(c.gen||0)+1;
    if(keep){const j=msList(c).findIndex(x=>x.key===keep.key);if(j>=0)studio.i=j}}
  else if(e.cand&&!c.list.some(x=>x.key===e.cand.key)){c.list.push(e.cand);c.gen=(c.gen||0)+1}else return;
  if(here)bump()}
function onManJob(e){if(!e||!e.id)return;
  if(e.kind==="use"){const k=mk(e.systemId,e.file);if(mBusy[k]&&e.state==="run"){mMsg[k]=e.msg||"";if(isOpen&&studio&&studio.v==="mpick")bump()}return}
  jobSys[e.id]=e.systemId;sJobs[e.id]=Object.assign({man:1},e);
  if(e.state!=="run"){const me=sJobs[e.id];setTimeout(()=>{if(sJobs[me.id]===me){delete sJobs[me.id];paintJob()}},3500);
    if(isOpen){toast(`${e.label||"Manuals"}: ${e.state==="done"?"done":"stopped"}${e.msg?" · "+e.msg:""}`);if(mGames[e.systemId])mGames[e.systemId].stale=true;msLoadInfo();if(studio&&studio.sys===e.systemId&&MV[studio.v])msLoad(studio)}}
  paintJob();if(isOpen&&(studio||onArt()))bump()}
// the cards in order: arrival order while searching (nothing moves under the cursor), then in use first and best match
// for the console's choice; the action cards come last
function msList(c){if(!c||!c.list)return [];const ak=(c.gen||0)+"|"+c.canUndo+"|"+(c.searching?1:0)+"|"+(c.offline?1:0);if(c._ak===ak)return c._all;
  let L=c.list;if(c.sorted)L=L.slice().sort((a,b)=>(b.inUse?1:0)-(a.inUse?1:0)||(b.score||0)-(a.score||0));
  const A=[];if(!c.searching&&(!c.list.length||c.offline))A.push({act:"retry",key:"act-retry"});if(api.manualOwn)A.push({act:"own",key:"act-own"});if(c.canUndo)A.push({act:"undo",key:"act-undo"});
  c._ak=ak;c._all=L.concat(A);return c._all}
function msAfter(sys){if(mGames[sys])mGames[sys].stale=true;msLoadGames(sys);msLoadInfo()}
/* the Art page: a Manuals row per console (after the manual switches, so nothing below them moves when these load) */
function manualRows(){if(!api.manualStudio)return [];
  if(!mInfo){msLoadInfo();return [{t:"action",label:"Loading manuals by console…",btn:"",info:"Every manual found for each game, per console.",run:()=>{},ns:true,dim:true}]}
  return (mInfo.consoles||[]).map(c=>({t:"action",sys:c.id,label:`${c.name} manuals`,btn:`${c.have} of ${c.total}`,ns:true,keys:"manual studio manuals booklet choose language region french german japanese spanish italian pal own pdf "+c.name,
    info:`Every manual found for each ${c.name} game, side by side: another language, region or sharper scan, or a PDF of your own. Automatic downloads follow ${c.name}'s choice (now: ${prefName(c.pref)}) and never replace a manual you picked.`,
    sub:()=>{const j=Object.values(sJobs).find(x=>x.man&&x.state==="run"&&jobSys[x.id]===c.id);return {text:j?`Following the new choice · ${j.done||0} of ${j.total||"…"}`:prefName(c.pref)+(c.locked?` · ${c.locked} locked`:""),cls:j?"warn":""}},run:()=>studioOpen({v:"mgames",sys:c.id})}))}
/* a console's games */
function mgList(st){const e=mGames[st.sys],L=e&&e.list||[],f=st.filter||"all";return f==="all"?L:L.filter(g=>f==="locked"?g.locked:g.status===f)}
const mgCount=(st,f)=>mgList({sys:st.sys,filter:f}).length;
function mgBuild(st){const e=mGames[st.sys];
  E.bsa.innerHTML=`<div class="bsa-top"><div class="bsa-pills">${M_FILTERS.map(([k,l],j)=>`<div class="bsa-pill" data-t="${j}">${esc(l)} <span class="c">${mgCount(st,k)}</span></div>`).join("")}<span class="bsa-sp"></span>
    <div class="bsa-pill sty" data-t="4">${svg("art")}<span>Manuals · <b>${esc(prefName((mCon(st.sys)||{}).pref))}</b></span></div></div>
    <div class="bsa-legend"><span><i class="ok"></i>Have it</span><span><i class="lo"></i>Incomplete</span><span><i class="miss"></i>Missing</span><span><b class="lki">${LOCK_SVG}</b>Locked</span></div></div>
    <div class="bsa-vp"><div class="bsa-grid"></div></div>`;
  sEls={top:[...E.bsa.querySelectorAll(".bsa-pill")],vp:E.bsa.querySelector(".bsa-vp"),grid:E.bsa.querySelector(".bsa-grid"),tiles:new Map(),selI:-1,y:-1,tilesVer:e&&e.ver,cntVer:e&&e.ver,hl:null};
  sEls.top.forEach(p=>{const j=+p.dataset.t;p.onmouseenter=()=>{if(device==="mouse"&&!(studio.zone==="top"&&studio.ti===j)){studio.zone="top";studio.ti=j;render()}};p.onclick=()=>{studio.zone="top";studio.ti=j;mgInput("a")}});
  fadeFix(sEls.vp);const L=mgList(st);
  if(!e||!e.list){sEls.grid.innerHTML=`<div class="bsa-empty">${e&&e.err?esc("Couldn't load the games: "+e.err):`Loading ${esc(mName(st.sys))}'s games…`}</div>`;return}
  if(!L.length){sEls.grid.innerHTML=`<div class="bsa-empty">${st.filter==="missing"?"Every game has a manual.":st.filter==="incomplete"?"No incomplete manuals on this shelf.":st.filter==="locked"?`No manual is locked yet. ${btnLabel("y")} on a game locks the one it has.`:"No games on this shelf yet."}</div>`;if(st.zone!=="top"){st.zone="top";st.ti=st.ti||0}return}
  const gg=gGeo();sEls.grid.style.height=Math.ceil(L.length/gg.cols)*gg.rowH+"px";if(st.i>=L.length)st.i=L.length-1;if(!st.zone)st.zone="grid";
  sEls.hl=document.createElement("div");sEls.hl.className="bsa-thl";sEls.hl.style.cssText=`width:${gg.tw}px;height:${gg.th}px`;sEls.grid.prepend(sEls.hl)}
function mgTileHtml(g,gg){const s=M_ST[g.status]||M_ST.have;
  return `<div class="in${g.locked?" fz":""}"><div class="cv" style="height:${gg.cvH}px"></div>${g.locked?`<div class="lock" title="Locked">${LOCK_SVG}</div>`:""}<div class="t">${esc(g.title)}</div><div class="slots"><span class="sl ${s[0]}">${s[1]}${g.pages&&g.status!=="missing"?` · ${g.pages} p`:""}</span>${g.locked?'<span class="sl lk">Locked</span>':""}</div></div>`}
function mgTileFill(d,g,gg){const h=mgTileHtml(g,gg),u=g.page||g.cover;if(d._h===h&&d._u===u)return;if(d._h!==h){d._h=h;d.innerHTML=h}d._u=u;slot(d.querySelector(".cv"),u,phTitle(g.title))}
function mgRow(st,L,gg,r){for(let i=r*gg.cols;i<Math.min(L.length,(r+1)*gg.cols);i++){if(sEls.tiles.has(i))continue;const g=L[i],d=document.createElement("div");d.className="bsa-tile"+(i===sEls.selI?" sel":"");d.style.width=gg.tw+"px";d.style.transform=`translate(${(i%gg.cols)*(gg.tw+gg.gap)}px,${r*gg.rowH}px)`;
    mgTileFill(d,g,gg);d.onmouseenter=()=>{if(device==="mouse"&&!(studio.zone==="grid"&&studio.i===i)){studio.zone="grid";studio.i=i;render()}};d.onclick=()=>{studio.zone="grid";studio.i=i;mgInput("a")};
    sEls.grid.append(d);sEls.tiles.set(i,d)}}
function mgUpdate(st){const L=mgList(st),onTop=st.zone==="top",e=mGames[st.sys];sEls.top.forEach(p=>{const j=+p.dataset.t;p.classList.toggle("cur",onTop&&st.ti===j);p.classList.toggle("on",j<4&&M_FILTERS[j][0]===(st.filter||"all"))});
  if(e&&sEls.cntVer!==e.ver){sEls.cntVer=e.ver;sEls.top.forEach(p=>{const j=+p.dataset.t,c=j<4&&p.querySelector(".c");if(c)c.textContent=mgCount(st,M_FILTERS[j][0])})}
  const pb=sEls.top[4]&&sEls.top[4].querySelector("b"),pn=prefName((mCon(st.sys)||{}).pref);if(pb&&pb.textContent!==pn)pb.textContent=pn;
  if(!L.length||!sEls.grid.style.height)return;const gg=gGeo(),r=Math.floor(st.i/gg.cols),top=r*gg.rowH,maxY=Math.max(0,Math.ceil(L.length/gg.cols)*gg.rowH-gg.vpH+16);
  if(!onTop||st.reveal){if(top<st.y+6)st.y=Math.max(0,top-10);else if(top+gg.th>st.y+gg.vpH-14)st.y=top+gg.th-gg.vpH+24;st.reveal=false}else st.y=Math.min(st.y,0);st.y=Math.max(0,Math.min(st.y,maxY));
  // rows in view (and two either side) only, like the art grid
  if(sEls.tilesVer!==(e&&e.ver)){sEls.tilesVer=e&&e.ver;sEls.tiles.forEach((t,i)=>{const g=L[i];if(!g){t.remove();sEls.tiles.delete(i);return}mgTileFill(t,g,gg)})}
  const last=Math.ceil(L.length/gg.cols)-1,r0=Math.max(0,Math.floor(st.y/gg.rowH)-2),r1=Math.min(last,Math.ceil((st.y+gg.vpH)/gg.rowH)+2);
  sEls.tiles.forEach((t,i)=>{const rr=Math.floor(i/gg.cols);if(rr<r0||rr>r1){t.remove();sEls.tiles.delete(i)}});for(let rr=r0;rr<=r1;rr++)mgRow(st,L,gg,rr);
  if(sEls.y!==st.y){sEls.grid.style.transform=`translateY(${-st.y}px)`;sEls.y=st.y}
  const want=onTop?-1:st.i;if(sEls.selI!==want){const a=sEls.tiles.get(sEls.selI);if(a)a.classList.remove("sel");sEls.selI=want}const b=sEls.tiles.get(want);if(b&&!b.classList.contains("sel"))b.classList.add("sel");
  if(sEls.hl){sEls.hl.classList.toggle("on",want>=0);if(want>=0){const t=`translate(${(want%gg.cols)*(gg.tw+gg.gap)}px,${Math.floor(want/gg.cols)*gg.rowH}px)`;if(sEls.hl._t!==t){sEls.hl._t=t;sEls.hl.style.transform=t}}}}
async function mgLock(sys,file,locked){const k=mk(sys,file);if(mBusy[k])return;mBusy[k]="lock";
  try{const r=await api.manualLock({systemId:sys,file,locked});const g=mgGame(sys,file),c=mCands[k],on=!!(r&&r.locked);if(g){g.locked=on;const e=mGames[sys];if(e)e.ver=(e.ver||0)+1}if(c)c.locked=on;play("toast");
    toast(on?"Manual locked · automatic downloads leave it as it is":"Manual unlocked · it follows the console's choice again")}catch(e){toast(errMsg(e));play("error")}delete mBusy[k];msLoadInfo();bump()}
function mgInput(a,rep){const st=studio,L=mgList(st),gg=L.length?gGeo():null;
  if(a==="lb"||a==="rb"){const j=M_FILTERS.findIndex(f=>f[0]===(st.filter||"all")),n=(j+(a==="rb"?1:-1)+M_FILTERS.length)%M_FILTERS.length;st.filter=M_FILTERS[n][0];st.i=0;st.y=0;st.zone=mgList(st).length?"grid":"top";st.ti=n;sSig="";play("tick",a==="rb"?1:-1);render();return}
  if(a==="x"){studioOpen({v:"mpref",sys:st.sys});return}
  if(st.zone==="top"){
    if(a==="left"){if(st.ti>0){st.ti--;play("tick",-1)}else if(!rep)play("error")}else if(a==="right"){if(st.ti<4){st.ti++;play("tick",1)}else if(!rep)play("error")}
    else if(a==="down"){if(L.length){st.zone="grid";play("tick",1)}else if(!rep)play("error")}else if(a==="up"){if(!rep)play("error")}
    else if(a==="a"){if(st.ti===4){studioOpen({v:"mpref",sys:st.sys});return}const f=M_FILTERS[st.ti][0];if(f!==(st.filter||"all")){st.filter=f;st.i=0;st.y=0;sSig="";play("tick",1)}}
    render();return}
  if(!L.length){if(a==="up"||a==="down"||a==="left"||a==="right"){st.zone="top";st.ti=Math.max(0,M_FILTERS.findIndex(f=>f[0]===(st.filter||"all")));if(!rep)play("error")}render();return}
  const g=L[st.i];
  if(a==="left"){if(st.i%gg.cols>0){st.i--;play("tick",-1)}else if(!rep){focus="side";play("tick",-1)}}
  else if(a==="right"){if(st.i<L.length-1&&st.i%gg.cols<gg.cols-1){st.i++;play("tick",1)}else if(!rep)play("error")}
  else if(a==="up"){if(st.i>=gg.cols){st.i-=gg.cols;play("tick",-1)}else if(!rep){st.zone="top";st.ti=M_FILTERS.findIndex(f=>f[0]===(st.filter||"all"));play("tick",-1)}}
  else if(a==="down"){if(st.i+gg.cols<L.length){st.i+=gg.cols;play("tick",1)}else if(Math.floor(st.i/gg.cols)<Math.floor((L.length-1)/gg.cols)){st.i=L.length-1;play("tick",1)}else if(!rep)play("error")}
  else if(a==="lt"||a==="rt"){st.i=Math.max(0,Math.min(L.length-1,st.i+(a==="rt"?1:-1)*gg.cols*2))}
  else if(a==="home")st.i=0;else if(a==="end")st.i=L.length-1;
  else if(a==="a"){if(g){studioOpen({v:"mpick",sys:st.sys,file:g.file,title:g.title});return}}
  else if(a==="y"){if(g){mgLock(st.sys,g.file,!g.locked);return}}
  render()}
/* the console's choice: one card per option, A uses it (a background job re-picks what Boxshelf fetched itself) */
function mprefBuild(st){const P=(mInfo&&mInfo.prefs)||[];if(st.i==null||st.zone==null){st.zone="pre";st.i=Math.max(0,P.findIndex(p=>p.id===((mCon(st.sys)||{}).pref||"best")))}
  E.bsa.innerHTML=`<div class="bsa-presets bsm-prefs">${P.map((p,j)=>`<div class="bsa-pre" data-j="${j}"><div class="use">IN USE</div><div class="im"><div class="bsm-flag">${esc(M_FLAG[p.id]||p.id)}</div></div><b>${esc(p.label)}</b><span>${esc(p.sub||"")}</span></div>`).join("")}</div>
    <p class="bsm-note">Automatic downloads follow this for every ${esc(mName(st.sys))} game that isn't locked. Changing it swaps the manual of every unlocked game for one that fits, your picks and PDFs of your own too (the old one is kept, and Undo or Earlier brings it back); locked games and built-in manuals stay as they are. No copy that fits: the best English one.</p>`;
  sEls={pre:[...E.bsa.querySelectorAll(".bsa-pre")]};
  sEls.pre.forEach((d,j)=>{d.onmouseenter=()=>{if(device==="mouse"&&studio.i!==j){studio.i=j;render()}};d.onclick=()=>{studio.i=j;mprefInput("a")}})}
function mprefUpdate(st){const P=(mInfo&&mInfo.prefs)||[],cur=(mCon(st.sys)||{}).pref||"best";sEls.pre.forEach((el,j)=>{el.classList.toggle("sel",st.i===j);el.classList.toggle("inuse",P[j]&&P[j].id===cur)})}
async function mprefSave(sys,id){const bk="pref|"+sys;if(mBusy[bk]||!api.manualPref)return;mBusy[bk]=1;
  try{const r=await api.manualPref({systemId:sys,pref:id});const c=mCon(sys);if(c)c.pref=(r&&r.pref)||id;
    if(r&&r.jobId){jobSys[r.jobId]=sys;sJobs[r.jobId]={id:r.jobId,label:`${mName(sys)} manuals`,done:0,total:0,state:"run",man:1};paintJob()}
    Object.keys(mCands).forEach(k=>{if(k.startsWith(sys+"|"))mCands[k].dirty=true});play("toast");toast(`${mName(sys)}: ${prefName(id)} · automatic downloads follow it`)}
  catch(e){toast(errMsg(e));play("error")}delete mBusy[bk];bump()}
function mprefInput(a,rep){const st=studio,P=(mInfo&&mInfo.prefs)||[],cols=4;if(!P.length){render();return}
  if(a==="left"||a==="right"){const n=st.i+(a==="right"?1:-1);if(n>=0&&n<P.length&&Math.floor(n/cols)===Math.floor(st.i/cols)){st.i=n;play("tick",a==="right"?1:-1)}else if(a==="left"&&st.i%cols===0&&!rep){focus="side";play("tick",-1)}else if(!rep)play("error")}
  else if(a==="up"||a==="down"){const n=st.i+(a==="down"?cols:-cols);if(n>=0&&n<P.length){st.i=n;play("tick",a==="down"?1:-1)}else if(a==="down"&&Math.floor(st.i/cols)<Math.floor((P.length-1)/cols)){st.i=P.length-1;play("tick",1)}else if(!rep)play("error")}
  else if(a==="a"){const p=P[st.i];if(p){if(p.id===((mCon(st.sys)||{}).pref||"best")){toast("Already in use");return}mprefSave(st.sys,p.id)}return}
  render()}
/* one game's manuals */
function mpBuild(st){if(st.zone===undefined){st.zone="parts";st.pr=0}
  E.bsa.innerHTML=`<div class="bsa-pk bsm-pk"><div class="bsa-parts"><div class="bsa-ghl"></div><div class="bsa-part on" data-r="0"><div class="th"></div><div class="tx"><div class="nm">Manual</div><div class="st"></div></div><span class="badge"></span></div>
    <div class="bsa-glock mini" role="switch"><span class="ic">${LOCK_SVG}</span><div class="tx"><b>Lock this manual</b><span></span></div><div class="sw"><i></i></div><span class="v"></span></div><div class="bsm-pref"></div></div>
    <div class="bsa-cw"><div class="bsa-ch"><h3>Manuals</h3><span></span></div><div class="bsa-cvp"><div class="bsa-cgrid"></div></div></div>
    <div class="bsa-pv bsm-pv"><div class="k">First page</div><div class="bsm-page"></div><div class="what"></div><div class="why"></div></div></div>`;
  const q=s=>E.bsa.querySelector(s);
  sEls={pk:q(".bsa-pk"),part:q(".bsa-part"),hl:q(".bsa-ghl"),lock:q(".bsa-glock"),pref:q(".bsm-pref"),ch:q(".bsa-ch span"),grid:q(".bsa-cgrid"),cards:new Map(),selI:-1,y:-1,cardsK:null,chl:null,page:q(".bsm-page"),what:q(".bsm-pv .what"),why:q(".bsm-pv .why")};
  sEls.part.onclick=()=>{studio.zone="parts";studio.pr=0;mpInput("a")};sEls.lock.onclick=()=>{mpLockToggle(studio)};sEls.pref.onclick=()=>studioOpen({v:"mpref",sys:studio.sys});
  fadeFix(q(".bsa-cvp"))}
function mpCardHtml(x,pg){if(x.act)return `<div class="in act"><div class="im" style="height:${pg.imH}px"><span>${x.act==="undo"?"↶":x.act==="retry"?"↻":"+"}</span></div><div class="ed">${esc(M_ACT_L[x.act])}</div><div class="src">${esc(M_ACT_S[x.act])}</div></div><div class="use"></div>`;
  const lr=[x.langName,x.regionName].filter(Boolean).join(" · ");
  return `<div class="in"><div class="im" style="height:${pg.imH}px"></div><div class="ed" title="${esc(x.label||"")}">${esc(mShort(x))}</div><div class="src">${esc((M_SRC[x.source]||x.source||"")+(lr?" · "+lr:""))}</div>
    <div class="meta"><span class="chip${x.pages?"":" wait"}">${x.pages?x.pages+" pages":"Pages ?"}</span>${x.completeText?`<span class="chip ${x.complete?"hd":"warn"}">${esc(x.completeText)}</span>`:""}${x.qualityText?`<span class="chip${x.quality==="low"?" warn":""}">${esc(x.qualityText)}</span>`:""}</div></div><div class="use"></div>`}
function mpCards(st){const c=mCands[mk(st.sys,st.file)],pg=pkGeo(),gr=sEls.grid;sEls.pk.style.gridTemplateColumns=`${pg.partsW}px ${pg.colW}px ${pg.pvW}px`;sEls.pk.style.gap=pg.gap+"px";
  sEls.cards.forEach(d=>d.remove());sEls.cards.clear();sEls.selI=-1;sEls.y=-1;sEls.chl=null;gr.innerHTML="";gr.style.height="";
  if(!c||c.list===null){gr.style.transform="";st.y=0;gr.innerHTML='<div class="bsa-empty">Looking for every manual of this game…</div>';return}
  const L=msList(c);if(st.i>=L.length)st.i=L.length-1;if(st.i<0)st.i=0;
  gr.style.height=Math.ceil(L.length/pg.cols)*pg.rowH+"px";sEls.chl=document.createElement("div");sEls.chl.className="bsa-chl";sEls.chl.style.cssText=`width:${pg.cw}px;height:${pg.ch}px`;gr.prepend(sEls.chl)}
function mpFill(st){const c=mCands[mk(st.sys,st.file)],L=msList(c),pg=pkGeo(),r0=Math.max(0,Math.floor(st.y/pg.rowH)-1),r1=Math.ceil((st.y+pg.vpH)/pg.rowH)+1,i0=r0*pg.cols,i1=Math.min(L.length,(r1+1)*pg.cols);
  sEls.cards.forEach((d,i)=>{if(i<i0||i>=i1){d.remove();sEls.cards.delete(i)}});
  for(let i=i0;i<i1;i++){if(sEls.cards.has(i))continue;const x=L[i],d=document.createElement("div");d.className="bsa-card";d._x=x;d.style.cssText=`width:${pg.cw}px;height:${pg.ch}px;transform:translate(${(i%pg.cols)*(pg.cw+pg.cg)}px,${Math.floor(i/pg.cols)*pg.rowH}px)`;
    d.innerHTML=mpCardHtml(x,pg);if(!x.act)slot(d.querySelector(".im"),x.thumb,x.thumb?'<div class="bsa-ph wait"></div>':phTitle(x.pdfOnly?"PDF · not turned into pages yet":mShort(x)));
    d.onmouseenter=()=>{if(device==="mouse"&&(studio.i!==i||studio.zone)){studio.i=i;studio.zone=null;render()}};d.onclick=()=>{studio.i=i;studio.zone=null;mpInput("a")};
    sEls.grid.append(d);sEls.cards.set(i,d)}}
const bigThumb=u=>u?String(u).replace(/([?&])t=360\b/,"$1t=900"):u;
function mpUpdate(st){const k=mk(st.sys,st.file),c=mCands[k],L=msList(c),inParts=st.zone==="parts",cur=c&&c.list&&c.list.find(x=>x.inUse),busy=mBusy[k];
  sEls.pk.classList.toggle("inparts",inParts);sEls.hl.classList.toggle("focus",inParts&&st.pr===0);sEls.lock.classList.toggle("cur",inParts&&st.pr===1);
  // the Manual row: what's in use, whether it's yours (locked) or follows the console's choice
  const locked=!!(c&&c.locked),s=busy?(busy==="lock"?"One moment…":mMsg[k]||"Working…"):!c||!c.list?"Looking…":!cur?"Missing · "+prefName(c.pref):(locked?"Locked":cur.origin==="studio"||cur.origin==="own"?"Your pick":"Follows "+prefName(c.pref))+(cur.completeText&&cur.complete===false?" · "+cur.completeText.toLowerCase():"");
  const pick=!!(cur&&(cur.origin==="studio"||cur.origin==="own")),se=sEls.part.querySelector(".st"),be=sEls.part.querySelector(".badge"),bt=pick?"Your pick":locked?"Locked":"Console";if(se.textContent!==s)se.textContent=s;if(be.textContent!==bt){be.textContent=bt;be.className="badge "+(pick||locked?"b-lock":cur?"b-ok":"b-miss")}
  slot(sEls.part.querySelector(".th"),cur&&cur.thumb,'<div class="bsa-ph mini"></div>');
  sEls.lock.classList.toggle("on",locked);sEls.lock.classList.toggle("busy",busy==="lock");const lt=busy==="lock"?"One moment…":locked?"Automatic downloads and a new console choice leave it as it is.":pick?"Off: a new console choice replaces picks too.":"Off: automatic downloads may swap it for a better match.",ls=sEls.lock.querySelector(".tx span"),lv=sEls.lock.querySelector(".v");if(ls.textContent!==lt)ls.textContent=lt;if(lv.textContent!==(locked?"On":"Off"))lv.textContent=locked?"On":"Off";
  const pt=`${mName(st.sys)}: ${prefName((mCon(st.sys)||{}).pref||(c&&c.pref))} · change`;if(sEls.pref.textContent!==pt)sEls.pref.textContent=pt;
  const sig=[!c||c.list===null?"w":L.length,c&&c.gen||0].join("|");if(sEls.cardsK!==sig){sEls.cardsK=sig;mpCards(st)}
  const n=c&&c.list?c.list.length:0,t=!c||!c.list?"":c.err?c.err:c.searching?`${n} so far · still looking…`:!n?(c.offline?"couldn't reach the manual sites · check the connection, then Try again":"no manual found online · add a PDF of your own"):`${n} manual${n===1?"":"s"} · best match for ${prefName(c.pref)} first`;if(sEls.ch.textContent!==t)sEls.ch.textContent=t;
  if(L.length){const pg=pkGeo(),r=Math.floor(st.i/pg.cols),top=r*pg.rowH,maxY=Math.max(0,Math.ceil(L.length/pg.cols)*pg.rowH-pg.vpH+20);
    if(top<st.y+6)st.y=Math.max(0,top-12);else if(top+pg.ch>st.y+pg.vpH-10)st.y=top+pg.ch-pg.vpH+26;st.y=Math.max(0,Math.min(st.y,maxY));
    mpFill(st);if(sEls.y!==st.y){sEls.grid.style.transform=st.y?`translateY(${-st.y}px)`:"";sEls.y=st.y}
    const want=inParts?-1:st.i;if(sEls.selI!==want){const a=sEls.cards.get(sEls.selI);if(a)a.classList.remove("sel");sEls.selI=want}const b=sEls.cards.get(want);if(b&&!b.classList.contains("sel"))b.classList.add("sel");
    if(sEls.chl){sEls.chl.style.opacity=inParts?"0":"";const tt=`translate(${(st.i%pg.cols)*(pg.cw+pg.cg)}px,${r*pg.rowH}px)`;if(sEls.chl._t!==tt){sEls.chl._t=tt;sEls.chl.style.transform=tt}}
    sEls.cards.forEach(d=>{const x=d._x,u=d.querySelector(".use"),tx=busy===x.key?(mMsg[k]||"Downloading…"):x.inUse?(locked?"In use · locked":"In use"):""; /* "locked" here is the manual's own Lock switch */if(u.textContent!==tx){u.textContent=tx;u.classList.toggle("on",!!tx)}})}
  // the preview: the highlighted manual's first page, large, with what it is
  const x0=inParts?cur:L[st.i],x=x0&&!x0.act?x0:null;slot(sEls.page,x&&bigThumb(x.thumb),x?phTitle(mShort(x)):x0&&x0.act?`<div class="bsa-ph"><span>${esc(M_ACT_L[x0.act])}</span></div>`:'<div class="bsa-ph"><span>No manual yet</span></div>');
  const what=x?mShort(x):x0&&x0.act?M_ACT_L[x0.act]:"",why=x?[(M_SRC[x.source]||x.source)+([x.langName,x.regionName].filter(Boolean).length?" · "+[x.langName,x.regionName].filter(Boolean).join(" · "):""),(x.pages?x.pages+" pages":"")+(x.completeText?" · "+x.completeText:""),x.qualityText,x.inUse?(locked?"In use and locked: nothing automatic replaces it.":"In use now."):"A uses it (your pick: a new console choice replaces it unless the manual is locked); the manual it replaces is kept."].filter(Boolean).join(". "):x0&&x0.act?M_ACT_S[x0.act]+".":"";
  if(sEls.what.textContent!==what)sEls.what.textContent=what;if(sEls.why.textContent!==why)sEls.why.textContent=why}
async function mpLockToggle(st){const c=mCands[mk(st.sys,st.file)];await mgLock(st.sys,st.file,!(c&&c.locked))}
async function mpUse(st,x){const k=mk(st.sys,st.file),c=mCands[k];if(mBusy[k])return;if(x.inUse){toast("Already in use");return}
  mBusy[k]=x.key;mMsg[k]="Downloading…";play("tick",1);bump();
  try{const r=await api.manualUse({systemId:st.sys,file:st.file,key:x.key});if(c){c.locked=!!(r&&r.locked);c.canUndo=!!(r&&r.canUndo)}play("toast");toast(`Manual: ${mShort(x)} · your pick`);if(!x.inUse){st.i=0;msLoadCands(st.sys,st.file,true)}}
  catch(e){toast(errMsg(e));play("error")}delete mBusy[k];delete mMsg[k];msAfter(st.sys);bump()}
async function mpUndo(st){const k=mk(st.sys,st.file);if(mBusy[k])return;mBusy[k]="undo";mMsg[k]="Undoing…";bump();
  try{await api.manualUndo({systemId:st.sys,file:st.file});play("toast");toast("The previous manual is back");st.i=0;msLoadCands(st.sys,st.file,true)}catch(e){toast(errMsg(e));play("error")}delete mBusy[k];delete mMsg[k];msAfter(st.sys);bump()}
async function mpOwn(st){const k=mk(st.sys,st.file);if(mBusy[k]||!api.manualOwn)return;mBusy[k]="own";mMsg[k]="Choose a PDF…";play("open");bump();
  try{const r=await api.manualOwn({systemId:st.sys,file:st.file});if(!(r&&r.canceled)){play("toast");toast("Your PDF is this game's manual now · your pick");st.i=0;msLoadCands(st.sys,st.file,true)}}catch(e){toast(errMsg(e));play("error")}delete mBusy[k];delete mMsg[k];msAfter(st.sys);bump()}
function mpInput(a,rep){const st=studio,c=mCands[mk(st.sys,st.file)],L=msList(c),pg=pkGeo();
  if(a==="y"){mpLockToggle(st);return}
  if(a==="x"){studioOpen({v:"mpref",sys:st.sys});return}
  // the left column: the Manual row, then the lock switch; A on Manual opens the versions (on the one in use)
  if(st.zone==="parts"){
    if(a==="up"||a==="down"){const n=(st.pr||0)+(a==="down"?1:-1);if(n<0||n>1){if(!rep)play("error")}else{st.pr=n;play("tick",a==="down"?1:-1)}}
    else if(a==="a"){if(st.pr===1){mpLockToggle(st);return}if(!L.length){play("error");return}const u=L.findIndex(x=>x.inUse);st.i=Math.max(0,u);st.zone=null;play("open")}
    else if(a==="right"){if(!rep)play("error")}else if(a==="left"){if(!rep){focus="side";play("tick",-1)}}
    render();return}
  if(!L.length){if(a==="left"&&!rep){st.zone="parts";st.pr=0;play("tick",-1);render()}return}
  if(a==="left"){if(st.i%pg.cols>0){st.i--;play("tick",-1)}else if(!rep){st.zone="parts";st.pr=0;play("tick",-1)}}
  else if(a==="right"){if(st.i<L.length-1&&st.i%pg.cols<pg.cols-1){st.i++;play("tick",1)}else if(!rep)play("error")}
  else if(a==="up"){if(st.i>=pg.cols){st.i-=pg.cols;play("tick",-1)}else if(!rep)play("error")}
  else if(a==="down"){if(st.i+pg.cols<L.length){st.i+=pg.cols;play("tick",1)}else if(Math.floor(st.i/pg.cols)<Math.floor((L.length-1)/pg.cols)){st.i=L.length-1;play("tick",1)}else if(!rep)play("error")}
  else if(a==="lt"||a==="rt"){st.i=Math.max(0,Math.min(L.length-1,st.i+(a==="rt"?2:-2)*pg.cols))}
  else if(a==="home"||a==="end")st.i=a==="home"?0:L.length-1;
  else if(a==="a"){const x=L[st.i];if(!x)return;if(x.act==="own")mpOwn(st);else if(x.act==="undo")mpUndo(st);else if(x.act==="retry"){c.list=null;sSig="";msLoadCands(st.sys,st.file,true);render()}else mpUse(st,x);return}
  render()}
/* the header, the hints, input: the same frame as the art views */
function msRender(st){const nm=mName(st.sys),c=mCon(st.sys),g=st.file?mgGame(st.sys,st.file):null,full=g?g.title:st.title||"",e=mGames[st.sys];
  const H0={mgames:[`Box art & manuals  ›  ${nm} manuals`,`${nm} manuals`,()=>{const L=e&&e.list||[],n=L.length||(c?c.total:0),have=e&&e.list?L.filter(x=>x.status!=="missing").length:c?c.have:0,lk=e&&e.list?L.filter(x=>x.locked).length:c?c.locked:0;return `${have} of ${n} game${n===1?"":"s"} have a manual · ${prefName(c&&c.pref)}${lk?` · ${lk} locked`:""}`}],
    mpref:[`Box art & manuals  ›  ${nm} manuals  ›  Choice`,`Which manuals for ${nm}?`,()=>"One tap. Boxshelf's automatic downloads follow it, now and for new games."],
    mpick:[`Box art & manuals  ›  ${nm}  ›  ${truncEnd(full,40)}  ›  Manual`,full,()=>{const cc=mCands[mk(st.sys,st.file)];return !cc||!cc.list?"Looking for every manual of this game…":cc.searching?"Every manual found for this game, side by side. Still looking online…":"A uses one as your pick and sets the old one aside (Undo brings it back)."}]}[st.v];
  if(E.crumb.textContent!==H0[0])E.crumb.textContent=H0[0];if(E.title.textContent!==H0[1])E.title.textContent=H0[1];const ld=H0[2]();if(E.lead.textContent!==ld)E.lead.textContent=ld;
  const sig=[st.v,st.sys,st.file||"",st.filter||"",e&&e.list?"L":"-",st.v==="mpref"&&mInfo?"P":"-"].join("|");
  if(sig!==sSig){sSig=sig;sGeo=null;geo();({mgames:mgBuild,mpref:mprefBuild,mpick:mpBuild})[st.v](st);
    const vk=st.v+"|"+st.sys+"|"+(st.file||"");if(vk!==sViewK){sViewK=vk;if(E.bsa.animate&&!document.body.classList.contains("bss-reduce"))E.bsa.animate([{opacity:.35,transform:"translateY(10px)"},{opacity:1,transform:"none"}],{duration:160,easing:"cubic-bezier(.2,.8,.2,1)"})}}
  ({mgames:mgUpdate,mpref:mprefUpdate,mpick:mpUpdate})[st.v](st);paintJob()}
function msHints(st){const L=[],H=(ks,label,a)=>`<div class="h"${a?` data-a="${a}"`:""}><span class="ks">${ks}</span>${esc(label)}</div>`,pad=device==="pad";
  const dirs=(v,h)=>pad?(v?G("ud"):"")+(h?G("lr"):""):kc(v&&h?"↑↓←→":v?"↑ ↓":"← →"),lbrb=pad?G("lb")+G("rb"):kc("Tab");
  if(st.v==="mgames"){const g=mgList(st)[st.i];if(st.zone==="top")L.push(H(dirs(0,1),"Move"),H(G("a","Enter"),st.ti===4?"Console choice":"Show","a"));else{L.push(H(dirs(1,1),"Browse"),H(G("a","Enter"),"Choose manual","a"));if(g)L.push(H(G("y","Del"),g.locked?"Unlock":"Lock","y"))}
    L.push(H(G("x","F1"),"Console choice","x"),H(lbrb,"Filter","rb"))}
  else if(st.v==="mpref")L.push(H(dirs(1,1),"Move"),H(G("a","Enter"),`Use for ${mName(st.sys)}`,"a"));
  else{const c=mCands[mk(st.sys,st.file)],locked=!!(c&&c.locked);
    if(st.zone==="parts")L.push(H(dirs(1,0),"Move"),H(G("a","Enter"),st.pr===1?(locked?"Unlock":"Lock"):"Choose a manual","a"));
    else{const x=msList(c)[st.i];L.push(H(dirs(1,1),"Manuals"));if(x)L.push(H(G("a","Enter"),x.act?M_ACT_L[x.act]:x.inUse?"In use":"Use this","a"));L.push(H(G("y","Del"),locked?"Unlock":"Lock","y"))}
    L.push(H(G("x","F1"),"Console choice","x"))}
  L.push(H(G("b","Esc"),"Back","b"));return L.join("")}
function msInput(a,rep){const st=studio;
  // (beta.9) B from the manual cards goes straight back to where you came from (the game's art page, or the games grid):
  // it used to stop on the Manual row first, which looked like nothing happened (the row is a Left / Up away)
  if(a==="b"){studioBack();return}if(a==="start"){close();return}
  ({mgames:mgInput,mpref:mprefInput,mpick:mpInput})[st.v](a,rep)}
// the box art picker's "Manual" entry (after Disc): this game's manuals, B comes back to it
function openManualFromArt(){const st=studio;if(!st||!api.manualStudio)return;studioOpen({v:"mpick",sys:st.sys,file:st.file,title:st.title})}

/* drawing: header, the view (built once per view, patched on every move), the job pill */
// long titles: shortened in the crumb (so "› Choose art" stays on screen) and before "· your mix"; the h1 ellipsis does the rest
function renderStudio(){if(MV[studio.v]){msRender(studio);return}const st=studio,nm=conName2(st.sys),g=st.file?gameOf(st.sys,st.file):null,full=g?g.title:st.title||"",title=truncEnd(full,40);
  const H0={games:[`Box art & manuals  ›  ${nm}`,`${nm} art`,()=>{const e=sGames[st.sys],c=conOf(st.sys),n=e&&e.list?e.list.length:c?c.total:0,lk=e&&e.list?e.list.filter(isLocked).length:c?c.locked:0;const jt=conJobText(st.sys);const nc=e&&e.list?e.list.filter(x=>!(x.art&&x.art.front)).length:0;return `${n} game${n===1?"":"s"}${nc?` · ${nc} without a cover`:""}${lk?` · ${lk} with picks or locked`:""}${jt?" · "+jt:""}`}], /* (1.80: the shelf style is on its pill, not repeated here) */
    style:[`Box art & manuals  ›  ${nm}  ›  Shelf style`,`How should ${nm} look?`,()=>"Pick one. It changes every game except locked ones, your picks too (Lock this game keeps a game as it is). Replaced pictures go to the replaced folder; Undo brings them back."],
    pick:[`Box art & manuals  ›  ${nm}  ›  ${title}`,full,()=>{const c=sCands[ck(st.sys,st.file,st.part)],n=c&&c.list?c.list.filter(x=>!x.failed).length:null,pl=PART_L[st.part].toLowerCase();
      return isFrozen(g)?"This game is locked as it is: nothing automatic changes it. Your own changes here still work.":n==null?`Looking for every version of the ${pl}…`:`Each part follows the shelf style until you pick one. ${n} version${n===1?"":"s"} of the ${pl} found.`}]}[st.v];
  if(E.crumb.textContent!==H0[0])E.crumb.textContent=H0[0];if(E.title.textContent!==H0[1])E.title.textContent=H0[1];const ld=H0[2]();if(E.lead.textContent!==ld)E.lead.textContent=ld;
  // a view is built when it opens (or its shape changes: a filter, the list arriving); the fade-in plays only when you
  // arrive at another view or game, never when the same view is rebuilt under you. Pick keeps its frame across parts
  const e=sGames[st.sys],sig=[st.v,st.sys,st.file||"",st.v==="pick"?"":st.part||"",st.filter||"",e&&e.list?"L":"-"].join("|");
  if(sig!==sSig){sSig=sig;sGeo=null;geo();({games:gamesBuild,style:styleBuild,pick:pickBuild})[st.v](st);
    const vk=st.v+"|"+st.sys+"|"+(st.file||"");if(vk!==sViewK){sViewK=vk;if(E.bsa.animate&&!document.body.classList.contains("bss-reduce"))E.bsa.animate([{opacity:.35,transform:"translateY(10px)"},{opacity:1,transform:"none"}],{duration:160,easing:"cubic-bezier(.2,.8,.2,1)"})}}
  ({games:gamesUpdate,style:styleUpdate,pick:pickUpdate})[st.v](st);paintJob()}
function studioHints(){if(MV[studio.v])return msHints(studio);const st=studio,L=[],H=(ks,label,a)=>`<div class="h"${a?` data-a="${a}"`:""}><span class="ks">${ks}</span>${esc(label)}</div>`,pad=device==="pad";
  const dirs=(v,h)=>pad?(v?G("ud"):"")+(h?G("lr"):""):kc(v&&h?"↑↓←→":v?"↑ ↓":"← →"),lbrb=pad?G("lb")+G("rb"):kc("Tab");
  if(st.v==="games"){const g=gamesList(st)[st.i];
    if(st.zone==="top"){const x=(sEls.items||[])[st.ti]||{};L.push(H(dirs(0,1),"Move"),H(G("a","Enter"),x.k==="style"?"Shelf style":x.k==="fix"?"Download":"Show","a"))}else{L.push(H(dirs(1,1),"Browse"),H(G("a","Enter"),"Choose art","a"));if(g)L.push(H(G("y","Del"),isFrozen(g)?"Unlock game":"Lock game","y"))}
    L.push(H(G("x","F1"),"Shelf style","x"),H(lbrb,"Filter","rb"))}
  else if(st.v==="style"&&st.zone==="sreg"){L.push(H(dirs(0,1),"Region or HD"),H(G("a","Enter"),`Complete sets for ${conName2(st.sys)}`,"a"),H(dirs(1,0),"Move"))}
  else if(st.v==="style"){L.push(H(dirs(0,1),st.zone==="reg"?"Region":st.zone==="pp"?"Part":"Style"),H(G("a","Enter"),st.zone==="pp"?"Change look":`Use for ${conName2(st.sys)}`,"a"),H(dirs(1,0),st.zone==="pre"?(st.i===2?"Region":"Per part"):"Move"));if(conJob(st.sys))L.push(H(G("y","Del"),"Stop re-fetch","y"))}
  else if(st.v==="pick"&&st.zone==="flt"){L.push(H(dirs(0,1),"Filter"),H(G("a","Enter"),"Change","a"),H(G("view","F"),"Done","flt"))}
  else if(st.v==="pick"&&st.view==="sets"&&st.zone!=="parts"){const x=pkList(st,null)[st.i];L.push(H(dirs(1,1),"Sets"));if(x)L.push(H(G("a","Enter"),x.act?ACT_L[x.act]:"Use this set","a"));L.push(H(G("y","Del"),isFrozen(gameOf(st.sys,st.file))?"Unlock game":"Lock game","y"))}
  else if(st.v==="pick"&&st.zone==="parts"&&st.pr===-1){L.push(H(dirs(0,1),"Part"),H(G("a","Enter"),"Choose a set","a"))}
  else if(st.zone==="parts"){const g=gameOf(st.sys,st.file),sw=st.pr===PK_LOCK(),man=st.pr===PK_MAN&&!sw;L.push(H(dirs(0,1),"Part"),H(G("a","Enter"),sw?(isFrozen(g)?"Unlock game":"Lock game"):man?"Choose manual":"Choose versions","a"));if(!sw&&!man&&st.fItems&&st.fItems.length)L.push(H(G("view","F"),"Filters","flt"));if(lockOf(g,st.part)&&!sw&&!man)L.push(H(G("x","F1"),"Back to shelf style","x"))}
  else{const c=sCands[ck(st.sys,st.file,st.part)],g=gameOf(st.sys,st.file),x=pkList(st,c)[st.i];L.push(H(lbrb,"Part","rb"),H(dirs(1,1),"Versions"));if(st.fItems&&st.fItems.length)L.push(H(G("view","F"),fltOn()?"Filters ·  on":"Filters","flt"));
    if(x)L.push(H(G("a","Enter"),x.act?ACT_L[x.act]:x.inUse&&lockOf(g,st.part)?"In use":"Use this","a"));
    if(lockOf(g,st.part))L.push(H(G("x","F1"),"Back to shelf style","x"));if(g)L.push(H(G("y","Del"),isFrozen(g)?"Unlock game":"Lock game","y"))}
  L.push(H(G("b","Esc"),st.v==="pick"&&st.zone!=="parts"?"Parts":"Back","b"));return L.join("")}
function studioInput(a,rep){const st=studio;if(MV[st.v]){msInput(a,rep);return}
  if(a==="b"&&st.v==="pick"&&st.zone==="flt"){st.zone=st.fPrev===undefined?"parts":st.fPrev;if(st.zone==="parts")st.pr=prOf(st);play("back");render();return} // the filter row → where the cursor was
  if(a==="b"&&st.v==="pick"&&st.zone!=="parts"){st.zone="parts";st.pr=prOf(st);play("back");render();return} // the cards → this game's parts list (not out of the game)
  if(a==="b"){studioBack();return}if(a==="start"){close();return}
  if(a==="home"||a==="end"){if(st.v==="games"||st.v==="pick"){(st.v==="games"?gamesInput:pickInput)(a,rep);return}return}
  ({games:gamesInput,style:styleInput,pick:pickInput})[st.v](a,rep)}
// the consoles under Art & manuals in the sidebar while the studio is open (mouse: jump to another console)
let subSig="";
function renderSubnav(){const artI=SECTIONS.findIndex(s=>s.id==="art"),nav=sideNavs[artI];if(!nav)return;let el=E.side.querySelector(".bss-subnav");
  const show=!!(studio&&sInfo&&sInfo.consoles.length&&!results);if(!show){if(el&&el.classList.contains("on"))el.classList.remove("on");return}
  if(!el){el=document.createElement("div");el.className="bss-subnav";nav.after(el)}
  const sig=sInfo.consoles.map(c=>c.id+c.total).join();if(sig!==subSig||!el.children.length){subSig=sig;el.innerHTML=sInfo.consoles.map(c=>`<div class="bss-snav" data-id="${esc(c.id)}"><span class="dot"></span><span>${esc(c.name)}</span><span class="n">${c.total}</span></div>`).join("");
    el.querySelectorAll(".bss-snav").forEach(d=>d.onclick=()=>{if(studio&&studio.sys===d.dataset.id&&studio.v==="games"&&!stack.length)return;
      // into Art Studio's own sub-page (from Manuals by console, say): B then goes back to Art Studio's row, not the manuals page
      let moved=false;if(sub&&sub.id!=="art-studio"){const at=rowsOf(expand(SECTIONS[artI])).findIndex(r=>r.pg==="art-studio");if(at>=0){sub={kind:"more",id:"art-studio"};subRet=at;moved=true}}
      studioOpen({v:"games",sys:d.dataset.id},true);if(moved)sRet=-1})}
  // a console re-fetching shows its progress in place of its game count (and its dot spins)
  el.classList.add("on");el.querySelectorAll(".bss-snav").forEach(d=>{const id=d.dataset.id,j=conJob(id),c=conOf(id),t=j?`${j.done||0}/${j.total||"…"}`:String(c?c.total:"");d.classList.toggle("cur",id===studio.sys);d.classList.toggle("job",!!j);const n=d.lastChild;if(n.textContent!==t)n.textContent=t})}

/* ---------- DOM ---------- */
function ensureDom(){if(root)return;
  root=document.createElement("div");root.id="bss";root.setAttribute("aria-hidden","true");root.setAttribute("role","dialog");root.setAttribute("aria-label","Settings");
  root.innerHTML=`<div class="bss-stage"><div class="bss-mus"><i></i><b class="bss-mtv"><s></s><u></u></b></div><div class="bss-bg"></div><div class="bss-root"><aside class="bss-side"></aside>
    <main class="bss-page"><div class="bss-crumb"></div><h1 class="bss-title"></h1><p class="bss-lead"></p><div class="bss-scroll"><div class="bss-list"></div></div><div class="bsa"></div><div class="bsa-job"><i class="sp"></i><span class="t"></span><span class="x">Stop</span><b class="bar"><i></i></b></div></main>
    <aside class="bss-info"><div class="bss-card"><h3></h3><p></p><div class="bss-istat"></div><div class="bss-more"></div><div class="bss-meta"></div></div></aside>
    <div class="bss-osk"></div><div class="bss-hints"><nav class="bss-pill"></nav></div></div><div class="bss-toast"></div></div>`;
  (document.getElementById("app")||document.body).appendChild(root);
  const q=s=>root.querySelector(s);
  E={stage:q(".bss-stage"),mus:q(".bss-mus"),root:q(".bss-root"),side:q(".bss-side"),crumb:q(".bss-crumb"),title:q(".bss-title"),lead:q(".bss-lead"),scroll:q(".bss-scroll"),list:q(".bss-list"),info:q(".bss-info"),ih:q(".bss-card h3"),ip:q(".bss-card p"),is:q(".bss-istat"),im:q(".bss-more"),it:q(".bss-meta"),osk:q(".bss-osk"),hints:q(".bss-pill"),toast:q(".bss-toast"),hl:null,bsa:q(".bsa"),job:q(".bsa-job")};
  E.job.onclick=()=>cancelJob(E.job._id);
  if(api.onArtCand)try{api.onArtCand(onCand)}catch{}
  if(api.onArtJob)try{api.onArtJob(onJob)}catch{}
  if(api.onManualCand)try{api.onManualCand(onManCand)}catch{}if(api.onManualJob)try{api.onManualJob(onManJob)}catch{}
  stage=E.stage;
  addEventListener("resize",()=>{if(isOpen)fit()});
  // back from an emulator (or any other window): look at the achievements sign-ins again
  addEventListener("focus",()=>{if(isOpen&&active)loadRaEmu()});
  if(api.onApiStatus)try{api.onApiStatus(st=>{if(st&&typeof st==="object"){status=Object.assign({},status,st);if(isOpen)bump()}})}catch{}
  root.addEventListener("mousemove",e=>{if(Math.abs(e.movementX)+Math.abs(e.movementY)>2&&device!=="mouse"){device="mouse";renderHints();renderInfo();renderSearchBox()}},{passive:true});
  root.addEventListener("wheel",e=>{if(!active)return;if(studio&&focus==="page"){if(studio.v==="games"||studio.v==="pick"||studio.v==="mgames"||studio.v==="mpick"){const t=e.target.closest&&e.target.closest(".bsa-vp,.bsa-cvp");if(t)studioInput(e.deltaY>0?"down":"up")}return}if(focus==="page"&&!grab){const n=rowsOf(sec()).length;const r=Math.max(0,Math.min(n-1,rowI+(e.deltaY>0?1:-1)));if(r!==rowI){rowI=r;render()}}},{passive:true});
  root.addEventListener("contextmenu",e=>{e.preventDefault();if(active&&studio&&focus==="page"){device="mouse";input("b")}}); // in the Art Studio a right-click backs out one level, like Esc / B
  // (beta.10) touch (the Thor): a vertical drag on the list moves the selection, a row per row-height of travel (the list
  // follows it, as for the pad), so a long page no longer needs a tap per row. A tap still acts on its row; a drag never
  // does (its click is eaten). Mouse and pen-less Windows use are untouched (the wheel already scrolls there).
  {let dg=null,eatClick=0;
    E.scroll.addEventListener("pointerdown",e=>{dg=null;eatClick=0;if(!active||e.pointerType==="mouse"||!e.isPrimary||studio||grab||edit||focus==="osk"||focus==="edit")return;
      const c=rowEls[rowI],h=c&&c.isConnected?c.getBoundingClientRect().height:0; // (one layout read per touch, none per move)
      dg={id:e.pointerId,x:e.clientX,y0:e.clientY,on:false,step:Math.max(28,Math.min(90,h||48))}},{passive:true});
    E.scroll.addEventListener("pointermove",e=>{const g=dg;if(!g||e.pointerId!==g.id||!active)return;
      if(!g.on){const dy=e.clientY-g.y0,dx=e.clientX-g.x;if(Math.abs(dx)>16&&Math.abs(dx)>Math.abs(dy)){dg=null;return} /* sideways: the back swipe's */if(Math.abs(dy)<12||Math.abs(dy)<Math.abs(dx))return;
        g.on=true;g.y0=e.clientY;try{E.scroll.setPointerCapture(e.pointerId)}catch{}if(focus!=="page"&&!results){focus="page"}return}
      const k=Math.trunc((g.y0-e.clientY)/g.step);if(!k)return;g.y0-=k*g.step; // finger up: the rows below come into view
      const n=rowsOf(sec()).length,r=Math.max(0,Math.min(n-1,rowI+k));if(r!==rowI){rowI=r;focus="page";play("tick",k>0?1:-1);render()}},{passive:true});
    const end=e=>{const g=dg;if(!g||e.pointerId!==g.id)return;if(g.on)eatClick=performance.now();dg=null};
    E.scroll.addEventListener("pointerup",end,{passive:true});E.scroll.addEventListener("pointercancel",end,{passive:true});
    E.scroll.addEventListener("click",e=>{if(eatClick&&performance.now()-eatClick<600){eatClick=0;e.stopPropagation();e.preventDefault()}},true)}
}
const TINT={spectrum:"#6a5cff",classic:"#7b4dff",keys:"#4f7dff",arcade:"#e04fd0",aurora:"#22c49a",midnight:"#4c5a8c",
  artwork:"#6a5cff",blur:"#7b6cff",cube:"#5a4fd0",blueshift:"#2f6bff",swirl:"#e8662a",pocket:"#6f9a2a",oled:"#4c5a8c",walnut:"#b07040",forest:"#2f9a62",crt:"#3d8fd6",vhs:"#c23d8a",synth:"#ff4fa8"};
function applyTint(){if(!root)return;const id=gp("ui.theme")||"spectrum",c=(window.BS_CUSTOM_THEMES||[]).find(x=>x.id===id);root.style.setProperty("--tint",TINT[id]||(c&&/^#[0-9a-f]{6}$/i.test(c.accent||"")?c.accent:"")||TINT.spectrum)}
function fit(){if(!stage)return;const u=[1,1.15,1.3].includes(+gp("ui.uiScale"))?+gp("ui.uiScale"):1,safe=Math.max(.9,Math.min(1,(+gp("ui.safeArea")||100)/100));
  // (the handheld: a 6" screen at arm's length, so a quarter bigger again, but never narrower than a 1300-wide page)
  // (0.3.4: with no info column the page has room for bigger still: 1.45x, never narrower than a 1100-wide page)
  const hh=!!(window.bsPlatform&&bsPlatform.os==="android"),s=Math.min(Math.min(innerWidth/1920,innerHeight/1080)*u*(hh?1.45:1),hh?innerWidth/1100:Infinity),W=innerWidth/s,Hh=innerHeight/s;
  stage.style.width=W+"px";stage.style.height=Hh+"px";stage.style.transform=`scale(${s})`;
  E.root.classList.toggle("narrow",W<1760);E.root.style.transform=safe<1?`scale(${safe})`:"";E.root.style.transformOrigin="50% 50%";listSig="";sGeo=null;sSig="";E.side._sk=""}
let sideNavs=[],sideSig="";
// written only when it changes (it ran on every move: a new search box each time, and the sidebar laid out again)
function renderSearchBox(){const sb=E.side.querySelector(".bss-search");if(!sb)return;const cn="bss-search"+(focus==="search"?" cur":"")+(results&&focus!=="side"?" typing":"");if(sb.className!==cn)sb.className=cn;
  const h=(query?`<span class="q">${esc(query)}</span>`:`<span class="ph">Search settings</span>`)+'<span class="caret"></span>'+(device==="pad"?`<span class="kb">${viewWord()}</span>`:'<span class="kb">type</span>');if(sb._h!==h){sb._h=h;sb.innerHTML=svg("search")+h}}
function renderSide(){
  if(sideSig!==SECTIONS.map(s=>s.id).join()){sideSig=SECTIONS.map(s=>s.id).join();E.side.innerHTML='<div class="bss-brand">SETTINGS</div>';
    const sb=document.createElement("div");sb.className="bss-search";sb.onclick=()=>{if(edit)commitEdit();openSearch(device==="pad"||!!(window.bsPlatform&&bsPlatform.os==="android"))};E.side.append(sb);
    let g="";sideNavs=SECTIONS.map((s,i)=>{if(s.g!==g){g=s.g;E.side.insertAdjacentHTML("beforeend",`<div class="bss-grp">${esc(g)}</div>`)}
      const n=document.createElement("div");n.className="bss-nav";n.innerHTML=svg(s.ic)+`<span>${esc(s.name)}</span><span class="badge"></span>`;
      n.onclick=()=>{if(edit)commitEdit();if(results){results=null;query="";closeOsk();preSearch=null}goSec(i);focus="page";rowI=0;render()};E.side.append(n);return n});
    E.side.insertAdjacentHTML("beforeend",`<div class="bss-ver">Boxshelf ${esc(info.version||"")}</div>`)}
  renderSearchBox();renderSubnav();
  SECTIONS.forEach((s,i)=>{const n=sideNavs[i];if(!n)return;n.classList.toggle("on",!results&&i===secI);n.classList.toggle("cur",focus==="side"&&!results&&i===secI);
    const b=n.lastChild,badge=s.badge?s.badge():"",dot=s.dot?s.dot():null;
    if(badge){b.className="badge";b.textContent=badge}else if(dot){b.className="dot"+(dot==="warn"?" warn":"");b.textContent=""}else{b.className="badge";b.textContent=""}})
  // keep the current section in view: a layout read, so only when the section (or the studio's console list) changed
  const sk=secI+"|"+!!studio+"|"+(results?1:0);if(E.side._sk===sk)return;E.side._sk=sk;
  const on=sideNavs[secI];if(on&&E.side.scrollHeight>E.side.clientHeight+2){const t=on.offsetTop,h=on.offsetHeight,st=E.side.scrollTop,H=E.side.clientHeight;if(t-60<st)E.side.scrollTop=Math.max(0,t-60);else if(t+h+60>st+H)E.side.scrollTop=t+h+60-H}}
// the value side of a row: readable text and a chevron, a sliding choice, a springy switch, a slider
const truncMid=(s,n)=>{s=String(s||"");if(s.length<=n)return s;const k=Math.ceil((n-1)*.42);return s.slice(0,k)+"…"+s.slice(s.length-(n-1-k))};
const truncEnd=(s,n)=>{s=String(s||"");return s.length<=n?s:s.slice(0,n-1)+"…"};
const CHEV='<span class="chev">›</span>';
const stHtml=(cls,text)=>`<span class="bss-st ${cls}"><i></i>${esc(text)}</span>`;
function svcChip(s){const st=svcState(s);if(svcBusy[s])return stHtml("busy","Checking…");if(st.state==="none")return stHtml("none",s==="ra"?"Not set up":"Not set");if(st.state==="unknown")return stHtml("none","Not checked yet");
  if(st.state==="ok")return stHtml("ok",s==="ra"?`Connected · ${st.user||gp("ra.user")}${st.points!=null?` · ${st.points} pts`:""}`:"Working");
  // (beta.7) "no developer login": ScreenScraper turns every app away until it's registered with them (not the player's fault)
  if(s==="screenscraper"&&/no developer login/i.test(st.msg||""))return stHtml("bad","Not available yet");
  if(s!=="ra"&&/not accepted|refused|login/i.test(st.msg||""))return stHtml("bad",s==="screenscraper"?(/developer/i.test(st.msg)?"Developer login needed":"Login not accepted"):"Key not accepted");return stHtml("bad",truncEnd(st.msg||"Didn't work",40))}
// the theme row's tiles (the swatch goes through esc: a custom theme's picture is url("…"), whose quotes would end the style attribute)
function tilesHtml(i){return THEMES.map(([id,nm,sw],j)=>`<div class="bss-tile ${j===i?"on":""}" data-j="${j}"><div class="sw2" style="background:${esc(sw)}"></div><span>${esc(nm)}</span></div>`).join("")}
function valHtml(R,compact){const v=R.get?R.get():undefined;
  switch(R.t){
    case "choice":case "theme":{const o=optsOf(R);if(!o.length)return "";const i=idxOf(R,o);
      if(R.t==="theme"&&!compact)return `<div class="bss-tiles">${tilesHtml(i)}</div>`;
      if(compact)return `<span class="vt">${esc(o[i][1])}</span>${CHEV}`;
      return `<span class="arr l ${i<=0?"off":""}">‹</span><span class="txt"><b>${esc(o[i][1])}</b></span><span class="arr r ${i>=o.length-1?"off":""}">›</span>`}
    case "toggle":return `<span class="tv">${v?"On":"Off"}</span><span class="bss-sw ${v?"on":""}"><i></i></span>`;
    case "slider":{const p=Math.max(0,Math.min(1,(v-R.min)/(R.max-R.min)));return `<span class="bss-bar"><i style="transform:scaleX(${p})"></i><b style="left:${p*100}%"></b></span><span class="num">${esc(R.fmt?R.fmt(+v):v)}</span>`}
    case "action":{if(R.status)return svcChip(R.status)+CHEV;if(R.emu)return emuChip(R.emu)+CHEV;if(R.dim)return "";const b=typeof R.btn==="function"?R.btn():R.btn;return `<span class="vt${R.warnBtn&&R.warnBtn()?" warn":""}">${esc(b)}</span>${CHEV}`}
    case "path":return (v?`<span class="vt path">${esc(R.short?R.short(v):truncMid(v,44))}</span>`:`<span class="vt ph">${esc(R.ph||"Not set")}</span>`)+CHEV;
    case "folder":return `<span class="vt path">${esc(R.short?R.short(v):truncMid(v,40))}</span><span class="vt${asking(R.ask)?" warn":""}">${asking(R.ask)?"Press again to remove":""}</span>${CHEV}`;
    case "text":{const t=v?(R.secret?"•".repeat(Math.min(14,String(v).length)):truncMid(v,40)):"";return (t?`<span class="vt path">${esc(t)}</span>`:`<span class="vt ph">${esc(R.ph||"Not set")}</span>`)+CHEV}
    case "order":return `<span class="num">${R.n}</span><span class="vt${grab===R.id?" warn":""}">${grab===R.id?"Moving · ▲▼":""}</span>${CHEV}`;
  }return ""}
function subOf(r,R){if(r.t==="result")return null;if(R&&R.sub)return R.sub();if(R&&R.t==="path"&&R.want)return pathSub(R.get(),R.want);if(r.t==="theme"){const o=optsOf(r);return {text:o[idxOf(r,o)][1]}}return null}
// patch one row in place (so a switch slides and a value glides instead of the row being rebuilt)
function updateRow(el,r){const R=r.t==="result"?r.row:r;if(!R&&r.t!=="result")return;
  el.classList.toggle("changed",!!(R&&isChanged(R)));el.classList.toggle("dis",!!(r.dim||(R&&R.dim))); // (a row can grey out live: Start download with nothing picked)
  if(r.t!=="result"){const sb=subOf(r,R),sh=sb&&sb.text?esc(sb.text):"",sc="sub"+(sb&&sb.cls?" "+sb.cls:"");const se=el._sub;if(se._h!==sh||se.className!==sc){se._h=sh;se.className=sc;se.innerHTML=sh}}
  if(el.classList.contains("editing"))return;
  const ve=el._val;if(!R){if(!ve._h){ve._h=1;ve.innerHTML=`<span class="vt">Open</span>${CHEV}`}return}
  const compact=r.t==="result",v=R.get?R.get():undefined;
  if(!compact&&R.t==="toggle"&&ve._t==="toggle"){ve.querySelector(".bss-sw").classList.toggle("on",!!v);ve.querySelector(".tv").textContent=v?"On":"Off";return}
  if(!compact&&R.t==="slider"&&ve._t==="slider"){const p=Math.max(0,Math.min(1,(v-R.min)/(R.max-R.min)));ve.querySelector(".bss-bar i").style.transform=`scaleX(${p})`;ve.querySelector(".bss-bar b").style.left=p*100+"%";ve.querySelector(".num").textContent=R.fmt?R.fmt(+v):v;return}
  if(!compact&&R.t==="choice"&&ve._t==="choice"){const o=optsOf(R),i=idxOf(R,o),lab=o[i]?o[i][1]:"",b=ve.querySelector(".txt b");if(b&&b.textContent!==lab){const nb=document.createElement("b");nb.textContent=lab;if(lastChange&&lastChange.r===R)nb.className=lastChange.d<0?"sl":"sr";b.replaceWith(nb)}
    ve.querySelector(".arr.l").classList.toggle("off",i<=0);ve.querySelector(".arr.r").classList.toggle("off",i>=o.length-1);return}
  // the theme row's value element is the tile box itself: mark the current tile, or (a custom theme added or gone) draw the tiles again inside it
  if(!compact&&R.t==="theme"&&ve._t==="theme"){const i=idxOf(R,optsOf(R));if(ve._n!==THEMES.length){ve._n=THEMES.length;ve.innerHTML=tilesHtml(i);return}
    [...ve.querySelectorAll(".bss-tile")].forEach((t,j)=>t.classList.toggle("on",j===i));return}
  const h=valHtml(R,compact);if(ve._h!==h){ve._h=h;ve.innerHTML=h;ve._t=compact?"":R.t}}
let listSig="",listPage="",rowEls=[],gridEl=null,lastChange=null;
// the row being typed into: a console page builds new row objects on every draw, so the same setting (path) counts as the same row
const sameRow=(a,b)=>a===b||!!(a&&b&&a.path&&a.t===b.t&&a.path===b.path);
function renderPage(){
  const s=sec(),pageKey=(results?"r":secI)+"|"+(sub?sub.kind+(sub.id||""):""),shownRows=s.rows.filter(shown);
  const sig=pageKey+"|"+(results?query:"")+"|"+shownRows.map(r=>r.t+":"+(r.label||(typeof r.text==="string"?r.text:"")||"")).join("\u0001")+"|"+(edit?focus+":"+edit.row.label:"");
  E.crumb.textContent=results?"Search":sub?crumbOf(sub):s.g;if(E.title.textContent!==s.name)E.title.textContent=s.name;E.lead.textContent=s.lead||"";
  let jump=false;
  if(sig!==listSig){const newPage=pageKey!==listPage;listSig=sig;listPage=pageKey;rowEls=[];gridEl=null;jump=newPage;const list=E.list;list.innerHTML="";
    E.hl=document.createElement("div");E.hl.className="bss-hl jump";list.append(E.hl);let k=0;
    if(results&&!results.length&&query)list.insertAdjacentHTML("beforeend",'<div class="bss-empty">Nothing matches that. Try another word.</div>');
    shownRows.forEach(r=>{
      if(r.t==="head"&&r.pair){const d=document.createElement("div");d.className="bss-pairbox";d._pair=1;list.append(d);return} // the phone companion's pairing card (not a row: nothing to select)
      if(r.t==="head"){if(r.text){const d=document.createElement("div");d.className="bss-head";d.innerHTML=`<span>${esc(r.text)}</span>${r.em?`<em>${esc(r.em()||"")}</em>`:""}`;d._r=r;list.append(d)}return}
      const idx=k++;
      if(r.t==="consoles"){const wrap=document.createElement("div");wrap.className="bss-consoles";
        tileList().forEach((c,j)=>{const d=document.createElement("div");d.className="bss-ctile "+(conState(c)||"none");d.innerHTML=`<b>${esc(c.name)}</b><span>${esc(emuShownName(c))}</span><em>${esc(conStatus(c))}</em>`;
          d.onmouseenter=()=>{if(device==="mouse"&&focus!=="osk"&&!edit&&!(focus==="page"&&rowI===idx&&ci===j)){focus="page";rowI=idx;ci=j;render()}};
          d.onclick=()=>{if(edit)commitEdit();ci=j;rowI=idx;focus="page";openConsole(c.id)};wrap.append(d)});
        list.append(wrap);rowEls[idx]=wrap;gridEl=wrap;return}
      const R=r.t==="result"?r.row:r,d=document.createElement("div"),editing=edit&&sameRow(edit.row,r)&&idx===rowI;
      d.className="bss-row"+(r.t==="theme"?" theme":"")+(editing?" editing":"")+(r.dim?" dis":"");
      d.innerHTML=`<div class="lbl"><span class="nm">${r.t==="result"?hi(r.label):esc(r.label)}</span>${r.t==="result"?`<span class="where">${esc(r.where)}</span>`:'<span class="sub"></span>'}</div><span class="chg"></span><div class="bss-val"></div>`;
      d._r=r;d._sub=d.querySelector(".sub")||{className:"",_h:""};d._val=d.querySelector(".bss-val");
      // the handheld (Android, 0.3.4): no info column; the focused row shows its own explanation under its name (android.css)
      if(document.documentElement.classList.contains("platform-android")&&R&&typeof R.info==="string"&&R.info){const ds=document.createElement("div");ds.className="desc";ds.textContent=R.info;d.querySelector(".lbl").appendChild(ds)}
      if(r.t==="theme")d._val.outerHTML=valHtml(r,false),d._val=d.querySelector(".bss-tiles"),d._val._t="theme",d._val._n=THEMES.length;
      if(editing){const shownV=r.secret?"•".repeat(edit.value.length):edit.value;
        d._val.innerHTML=focus==="edit"?`<input class="bss-edit" type="${r.secret?"password":"text"}" spellcheck="false" value="${esc(edit.value)}">`:`<div class="bss-editv"><span>${esc(shownV)}</span><i class="c"></i></div>`}
      d.onmouseenter=()=>{if(device==="mouse"&&focus!=="osk"&&!edit&&!grab&&!(focus==="page"&&rowI===idx)){focus="page";rowI=idx;render()}};
      d.onclick=e=>{if(edit){if(sameRow(edit.row,r))return;commitEdit()}focus="page";if(grab&&rowI!==idx)grab=null;rowI=idx;
        if(r.t==="result"){goResult(r);return}
        if(r.t==="choice"||r.t==="slider"||r.t==="theme"){const v=d._val;if(v){const rc=v.getBoundingClientRect();if(r.t==="theme"){const tiles=[...v.children],j=tiles.findIndex(t=>{const q=t.getBoundingClientRect();return e.clientX>=q.left&&e.clientX<=q.right&&(!XTHEMES||e.clientY>=q.top-6&&e.clientY<=q.bottom+6)}); /* (the tiles wrap onto two lines, so the line counts too) */if(j>=0){if(!eq(r.get(),THEMES[j][0])){lastChange={r,d:1};r.set(THEMES[j][0]);play("tick",1);changed(r)}else render();return}} /* (a click on the theme already chosen keeps it: it used to step on to the next one) */
          else{change(e.clientX<rc.left+rc.width/2?-1:1);render();return}}}
        act()};
      list.append(d);rowEls[idx]=d});
    checkPathsSoon();
    if(newPage){E.scroll.classList.remove("enter");void E.scroll.offsetWidth;E.scroll.classList.add("enter")}
    if(edit&&focus==="edit"){const inp=list.querySelector(".bss-edit");if(inp){inp.oninput=()=>{if(edit)edit.value=inp.value};setTimeout(()=>{if(document.activeElement!==inp){inp.focus();try{inp.setSelectionRange(inp.value.length,inp.value.length)}catch{}}},0)}}}
  // values (patched only where they changed), heads' summaries, the console tiles
  rowEls.forEach(el=>{if(el&&el!==gridEl&&el._r)updateRow(el,el._r)});
  E.list.querySelectorAll(".bss-pairbox").forEach(el=>{const h=pairHtml();if(el._h!==h){el._h=h;el.innerHTML=h}});
  E.list.querySelectorAll(".bss-head").forEach(h=>{const r=h._r;if(r&&r.em){const em=h.querySelector("em"),t=r.em()||"";if(em&&em.textContent!==t)em.textContent=t}});
  if(gridEl)tileList().forEach((c,j)=>{const t=gridEl.children[j];if(!t)return;const cls="bss-ctile "+(conState(c)||"none"),st=conStatus(c);if(!t.classList.contains("cur")&&t.className!==cls)t.className=cls;const em=t.querySelector("em");if(em.textContent!==st)em.textContent=st});
  // the highlight and the scroll position: no rebuild for a move
  const on=focus==="page"||focus==="edit"||(focus==="osk"&&!!edit);
  rowEls.forEach((el,i)=>{if(!el||el===gridEl)return;el.classList.toggle("cur",on&&i===rowI);el.classList.toggle("pre",on&&i===rowI-1)});
  if(gridEl){const gi=rowEls.indexOf(gridEl);[...gridEl.children].forEach((t,j)=>t.classList.toggle("cur",on&&gi===rowI&&j===ci))}
  const cur=rowEls[rowI],box=E.scroll;let y=+(E.list.dataset.y||0);
  let target=cur;if(cur&&cur===gridEl)target=gridEl.children[ci]||cur;
  if(cur&&on){const top=target===cur?cur.offsetTop:cur.offsetTop+target.offsetTop,h=target.offsetHeight,Hh=box.clientHeight;if(top<y+20)y=Math.max(0,top-80);else if(top+h>y+Hh-20)y=top+h-Hh+80;y=Math.max(0,Math.min(y,E.list.scrollHeight-Hh))}
  else if(!on)y=Math.min(y,Math.max(0,E.list.scrollHeight-box.clientHeight));
  if(focus==="side"||focus==="search")y=0;
  if(+(E.list.dataset.y||0)!==y){E.list.dataset.y=y;E.list.style.transform=`translateY(${-y}px)`}
  const hl=E.hl;if(hl){if(cur&&on){const x=target===cur?cur.offsetLeft:target.offsetLeft,t=target===cur?cur.offsetTop:cur.offsetTop+target.offsetTop;
      hl.style.transform=`translate(${x}px,${t}px)`;hl.style.width=target.offsetWidth+"px";hl.style.height=target.offsetHeight+"px";hl.classList.add("on");hl.classList.toggle("grab",!!grab)}
    else hl.classList.remove("on");
    if(jump||hl.classList.contains("jump")){requestAnimationFrame(()=>requestAnimationFrame(()=>hl.classList.remove("jump")))}}
}
function defLabel(R){if(R.t==="toggle")return R.d?"On":"Off";if(R.t==="slider")return R.fmt?R.fmt(+R.d):String(R.d);const o=R.opts?optsOf(R):[];const x=o.find(z=>eq(z[0],R.d));return x?x[1]:String(R.d||"empty")}
function renderInfo(){const r=curRow(),s=sec();E.info.classList.toggle("open",infoOpen&&!!r);let st="",meta=[];
  if((focus==="search"||focus==="osk")&&!edit){E.ih.textContent="Search";E.ip.textContent=device==="pad"?"Use the keyboard below. Results update as you type; press "+startWord()+" (or Done) to go to them.":"Just type — results update as you go. Down or Enter moves into the results, Esc clears.";E.im.textContent="";E.is.innerHTML="";E.it.innerHTML="";return}
  if(!r){E.ih.textContent=s.name;E.ip.textContent=s.lead||"";E.im.textContent="";E.is.innerHTML="";E.it.innerHTML=`<div class="pv">${device==="pad"?"Right or "+btnName("a"):"→ or Enter"} opens this page</div>`;return}
  const R=r.t==="result"?r.row:r,c=r.t==="consoles"?tileList()[ci]:null;
  E.ih.textContent=c?c.name:r.label;
  E.ip.textContent=c?`Uses ${c.emulatorName||"its emulator"}. ${conStatus(c)}.`:(r.info||"");
  if(c)st=stHtml(conState(c)==="ok"?"ok":conState(c)==="warn"?"warn":"none",conStatus(c));
  E.im.textContent=R&&(typeof R.more==="function"?R.more():R.more)||"";
  if(R&&R.status){const x=svcState(R.status);st=svcChip(R.status);if(x.at&&(x.state==="ok"||x.state==="fail"))meta.push(["",`Checked ${when(x.at)}`])}
  if(R&&R.emu){st=emuChip(R.emu);const e=emuOf(R.emu);if(e&&e.state!=="unsupported"&&e.state!=="no-emulator")meta.push(["pv",emuAsk===R.emu?`${btnLabel("a")} again opens ${e.emulator||"the emulator"}`:`${btnLabel("a")} shows how · then opens ${e.emulator||"the emulator"}`])}
  if(R&&R.preview)meta.push(["pv","Previews live behind this page"]);
  if(r.t==="result")meta.push(["",r.where]);
  if(R&&R.rescan)meta.push(["","Shelves update when you close Settings"]);
  if(R&&R.d!==undefined&&R.get&&!eq(R.get(),R.d))meta.push(["chg",`Changed · ${btnLabel("y")} resets to ${defLabel(R)}`]);
  E.is.innerHTML=st;E.it.innerHTML=meta.map(([c2,t])=>`<div class="${c2}">${esc(t)}</div>`).join("")}
function when(at){const d=new Date(at);if(isNaN(d))return "";const same=new Date().toDateString()===d.toDateString();return same?d.toLocaleTimeString([],{hour:"numeric",minute:"2-digit"}):d.toLocaleDateString([],{month:"short",day:"numeric"})}
// button pictures for the last input device (the shelf's own drawings), following Button prompts and Confirm button
const XB_L=["A","B","X","Y"],XB_COL=["#5fbf4a","#e5484d","#3b82f6","#e8b81a"];
const xbFace=i=>`<svg viewBox="0 0 16 16"><circle cx="8" cy="8" r="7.4" fill="${XB_COL[i]}"/><text x="8" y="11.5" text-anchor="middle" font-size="9.5" font-weight="700" font-family="Lexend,Segoe UI,sans-serif" fill="#0b0b0f">${XB_L[i]}</text></svg>`;
const PS_IN=['<path d="M5.3 5.3l5.4 5.4M10.7 5.3l-5.4 5.4" stroke="var(--cross,#86a8ff)" stroke-width="1.6" stroke-linecap="round"/>','<circle cx="8" cy="8" r="3.3" fill="none" stroke="var(--circ,#ff7a7a)" stroke-width="1.5"/>','<rect x="5" y="5" width="6" height="6" rx=".7" fill="none" stroke="var(--sq,#ee8fd6)" stroke-width="1.5"/>','<path d="M8 4.6 11.4 10.6H4.6z" fill="none" stroke="var(--tri,#3fd0b6)" stroke-width="1.5" stroke-linejoin="round"/>'];
const psFace=i=>`<svg viewBox="0 0 16 16"><circle cx="8" cy="8" r="7.4" fill="#1d2130" stroke="rgba(255,255,255,.28)" stroke-width=".6"/>${PS_IN[i]}</svg>`;
const ICO="rgba(255,255,255,.95)",ICD="rgba(255,255,255,.38)";
const icoDpad=lit=>{const arm=(d,x,y,w,h)=>`<rect x="${x}" y="${y}" width="${w}" height="${h}" rx="1.3" fill="${lit.includes(d)?ICO:ICD}"/>`;return `<svg viewBox="0 0 18 18">${arm("u",6.3,.6,5.4,6.4)}${arm("d",6.3,11,5.4,6.4)}${arm("l",.6,6.3,6.4,5.4)}${arm("r",11,6.3,6.4,5.4)}<rect x="6.3" y="6.3" width="5.4" height="5.4" fill="${ICD}"/></svg>`};
const icoBumper=t=>`<svg viewBox="0 0 28 16" style="width:44px;height:26px"><path d="M3 13.5 L3 7 Q3 3 8 2.6 L20 2.6 Q25 3 25 7 L25 13.5 Z" fill="none" stroke="${ICO}" stroke-width="1.3" stroke-linejoin="round"/><text x="14" y="11.4" text-anchor="middle" font-size="7.2" font-weight="700" font-family="Lexend,Segoe UI,sans-serif" fill="${ICO}">${t}</text></svg>`;
const icoTrigger=t=>`<svg viewBox="0 0 22 18" style="width:34px;height:28px"><path d="M3.5 16.5 L3.5 6 Q3.5 1.4 11 1.4 Q18.5 1.4 18.5 6 L18.5 16.5 Z" fill="none" stroke="${ICO}" stroke-width="1.3" stroke-linejoin="round"/><text x="11" y="12.4" text-anchor="middle" font-size="6.8" font-weight="700" font-family="Lexend,Segoe UI,sans-serif" fill="${ICO}">${t}</text></svg>`;
const icoMenu=`<svg viewBox="0 0 18 18"><circle cx="9" cy="9" r="8" fill="#2a2e3a" stroke="${ICD}" stroke-width=".8"/><path d="M5.4 6.4h7.2M5.4 9h7.2M5.4 11.6h7.2" stroke="${ICO}" stroke-width="1.3" stroke-linecap="round"/></svg>`;
const icoView=`<svg viewBox="0 0 18 18"><circle cx="9" cy="9" r="8" fill="#2a2e3a" stroke="${ICD}" stroke-width=".8"/><rect x="5" y="5.4" width="5.6" height="4.6" rx=".8" fill="none" stroke="${ICO}" stroke-width="1.1"/><rect x="7.4" y="7.8" width="5.6" height="4.6" rx=".8" fill="#2a2e3a" stroke="${ICO}" stroke-width="1.1"/></svg>`;
function physIdx(a){let i={a:0,b:1,x:2,y:3}[a];if(swapAB()&&i<2)i=1-i;return i}
function btnName(a){const i=physIdx(a),st=padStyle();return st==="ps"?["Cross","Circle","Square","Triangle"][i]:st==="nintendo"?["B","A","Y","X"][i]:XB_L[i]}
const ntFace=i=>`<svg viewBox="0 0 16 16"><circle cx="8" cy="8" r="7" fill="#e9ebf1"/><text x="8" y="11.6" text-anchor="middle" font-size="9.5" font-weight="700" font-family="Lexend,Segoe UI,sans-serif" fill="#15161c">${["B","A","Y","X"][i]}</text></svg>`;
const KEYN={a:"Enter",b:"Esc",x:"F1",y:"Del"};
const btnLabel=a=>device==="pad"?btnName(a):KEYN[a];
const kc=t=>`<span class="bss-kc">${esc(t)}</span>`;
// a glyph: a face button, a shoulder button, the d-pad, View / Menu — or the keyboard key
function G(a,key){if(device!=="pad")return kc(key||KEYN[a]||a);const ps=padStyle()==="ps";
  const nt=padStyle()==="nintendo";
  if(a in KEYN){const i=physIdx(a);return `<span class="bss-g">${ps?psFace(i):nt?ntFace(i):xbFace(i)}</span>`}
  if(a==="lb"||a==="rb")return `<span class="bss-g wide">${icoBumper(ps?(a==="lb"?"L1":"R1"):nt?(a==="lb"?"L":"R"):a.toUpperCase())}</span>`;
  if(a==="lt"||a==="rt")return `<span class="bss-g wide">${icoTrigger(ps?(a==="lt"?"L2":"R2"):nt?(a==="lt"?"ZL":"ZR"):a.toUpperCase())}</span>`;
  if(a==="lr"||a==="ud")return `<span class="bss-g">${icoDpad(a==="lr"?"lr":"ud")}</span>`;
  if(a==="view")return ps?kc("Create"):nt?kc("−"):`<span class="bss-g">${icoView}</span>`; // a Switch pad's − / + sit where View / Menu are
  if(a==="start")return ps?kc("Options"):nt?kc("+"):`<span class="bss-g">${icoMenu}</span>`;
  return kc(a)}
const startWord=()=>({ps:"Options",nintendo:"+"})[padStyle()]||"Menu",viewWord=()=>({ps:"CREATE",nintendo:"−"})[padStyle()]||"VIEW";
function renderHints(){const r=curRow(),L=[],R2=[];const H=(ks,label,a,cls)=>`<div class="h${cls?" "+cls:""}"${a?` data-a="${a}"`:""}><span class="ks">${ks}</span>${esc(label)}</div>`;
  if(studio&&focus==="page")L.push(studioHints());
  else if(focus==="edit"){L.push(H(kc("Enter"),"Save"),H(kc("Esc"),"Cancel"))}
  else if(focus==="osk"){L.push(H(G("a","Enter"),"Type","a"),H(G("x","Backspace"),"Delete","x"),H(G("y","Space"),"Space","y"),H(device==="pad"?G("start"):kc("Tab"),edit?"Save":"Done","start"),H(G("b","Esc"),edit?"Cancel":"Close keyboard","b"))}
  else if(focus==="search"){if(device==="pad"){L.push(H(G("a"),"Keyboard","a"),H(G("b"),results?"Clear search":"Back","b"));if(results&&results.length)L.push(H(G("ud"),"Results"))}
    else{L.push(window.bsPlatform&&bsPlatform.os==="android"?H(kc("A–Z"),"Keyboard","a"):H(kc("A–Z"),"Type"));if(results&&results.length)L.push(H(kc("↓"),"Results"));L.push(H(kc("Esc"),query?"Clear":"Back","b"))}}
  else{let a="Open";if(r){a=r.t==="toggle"?(r.get()?"Turn off":"Turn on"):r.t==="action"?(r.emu?(emuAsk===r.emu?"Open emulator":"How to fix"):r.open?"Open":r.status?"Test":(typeof r.btn==="function"?r.btn():r.btn)):r.t==="folder"?"Remove":({choice:"Next",slider:"Adjust",path:"Change",text:"Edit",theme:"Choose",consoles:"Open",result:"Go to setting",order:grab?"Put down":"Pick up"})[r.t]||"Select"}
    if(r&&!grab&&["choice","slider","theme","consoles"].includes(r.t))L.push(H(device==="pad"?G("lr"):kc("← →"),r.t==="consoles"?"Move":"Change"));
    if(grab)L.push(H(device==="pad"?G("ud"):kc("↑ ↓"),"Move"));
    if(!(r&&r.dim))L.push(H(G("a","Enter"),a,"a"));
    L.push(H(G("b","Esc"),grab?"Put down":sub?subUpName(sub):results?"Search box":focus==="page"?"Back":"Close","b"));
    const RD=r&&(r.t==="result"?r.row:r);if(RD&&RD.d!==undefined&&RD.get&&!eq(RD.get(),RD.d))L.push(H(G("y","Del"),"Reset","y"));
    if(RD&&RD.more)L.push(H(G("x","F1"),infoOpen?"Less":"Learn more","x"));
    R2.push(H(device==="pad"?G("view"):kc("Type"),"Search","search"),H(device==="pad"?G("lb")+G("rb"):kc("Tab"),"Section",null,"wide-only"))}
  const html=L.join("")+(R2.length?'<span class="sep"></span>'+R2.join(""):"");
  if(E.hints._h!==html){E.hints._h=html;E.hints.innerHTML=html;E.hints.querySelectorAll(".h[data-a]").forEach(e=>e.onclick=ev=>{ev.stopPropagation();device="mouse";input(e.dataset.a)})}}
function render(){secDrop();if(!root||!isOpen)return;E.root.classList.toggle("studio",!!studio);
  if(focus==="page"&&!studio){const n=rowsOf(sec()).length;if(rowI>n-1)rowI=Math.max(0,n-1)} // (beta.8) the focused row went (the last hidden game shown again, the last collection deleted): focus the row now last, never nothing
  if(studio){renderSide();renderStudio();renderOsk();renderHints();E.mus.classList.remove("on");return}
  renderSide();renderPage();renderOsk();renderInfo();renderHints();paintJob();
  E.mus.classList.toggle("on",!results&&(!sub||sub.kind==="more")&&SECTIONS[secI]&&(SECTIONS[secI].id==="museum"||(SECTIONS[secI].alias&&SECTIONS[secI].alias.includes("museum")&&focus==="page"&&!!(curRow()||{})._mus))&&focus!=="search"); /* (a section with the museum's rows merged in: the room shows behind them) */if(E.mus.classList.contains("on"))musLook()}
function toast(m){if(!E.toast)return;E.toast.textContent=m;E.toast.classList.add("on");clearTimeout(toastT);toastT=setTimeout(()=>E.toast.classList.remove("on"),2200)}
// live previews: the page fades back for a moment so you can see the shelf (or the room) behind it
// hide the shelf behind only after the fade in (#bss transition) has covered it; show it again at once on the way out
let coverT=0;const cover=on=>{clearTimeout(coverT);if(!on){document.body.classList.remove("bss-cover");return}if(document.body.classList.contains("bss-cover"))return;coverT=setTimeout(()=>{if(isOpen&&active)document.body.classList.add("bss-cover")},300)};
// only the shelf's look (theme, background) and the museum room preview; the shelf behind stays hidden apart from its
// backdrop (cheap, already there), so a preview never has to wake the whole shelf (that was the one-second stall)
function peek(kind){if(kind==="mus")musLook();if(kind!=="shelf"&&kind!=="mus")return;document.body.classList.add("bss-peek");E.stage.classList.add("peek");clearTimeout(peekT);
  peekT=setTimeout(()=>{E.stage.classList.remove("peek");document.body.classList.remove("bss-peek")},900)}
const MOOD={gallery:"#6a4a2c",daylight:"#9a9c94",night:"#5a2e3e",neon:"#6a1f70",spotlit:"#3e2c18",lightsout:"#0c0c12"},WALL={plaster:"#2c2723",wood:"#4a2f1c",brick:"#5a2a20",wallpaper:"#3c3446",green:"#1f3a2c",burgundy:"#4a1622",navy:"#18233f"},FLOOR={stone:"#5a5650",wood:"#6a4426",carpet:"#5a1f26",checker:"#cfcfcf"};
function musStyleNow(){return Object.assign({},MUS_DEF,(window.BoxshelfMuseumRoom||{}).defaults||{},cfg.ui.museumStyle||{},{light:cfg.ui.museumLight||"gallery"})}
function musLook(){const st=musStyleNow(),el=E.mus;el.style.setProperty("--mus0",`color-mix(in srgb,${MOOD[st.light]||MOOD.gallery} 60%,${WALL[st.wall]||WALL.plaster})`);el.style.setProperty("--musFloor",FLOOR[st.floor]||FLOOR.stone);
  const b=+st.bright||1,w=+st.warmth||0;el.style.filter=`brightness(${b}) sepia(${Math.max(0,w)*.4}) hue-rotate(${w<0?w*20:0}deg)`;
  // the Retro TV's stand, size and place, drawn in the preview so a change shows at once (the room itself is 3D only in the museum)
  el.classList.toggle("tv",!!st.tv);el.dataset.scene=st.light||"gallery";const tv=el.querySelector(".bss-mtv");if(tv){tv.dataset.stand=st.tvStand||"cart";tv.style.setProperty("--x",st.tvPos==="left"?"34%":st.tvPos==="centre"?"52%":"70%") /* within the page, clear of the sidebar and the info card */;tv.style.setProperty("--s",({s:.8,m:1,l:1.3})[st.tvSize]||1);tv.style.setProperty("--k",Math.max(0,Math.min(1,st.tvCrt==null?.6:+st.tvCrt)))}}

/* ---------- one set of actions, from controller, keyboard and mouse ---------- */
function input(a,rep){
  if(!active)return;
  if(a!=="a")emuAsk=null;
  if(studio&&focus==="page"&&a==="search"&&studio.v==="pick"){studioInput("flt",rep);return} // View / − / Create: the picker's filters
  if(studio&&focus==="page"&&a!=="search"){studioInput(a,rep);return}
  // X is Learn more, except on the on-screen keyboard where it's Delete (it used to toggle the info card there instead)
  if(a==="b"&&infoOpen&&focus!=="osk"){infoOpen=false;play("back");render();return} /* (beta.8) B closes the Learn more card first, and only that */
  if(a!=="x"||focus==="osk"){const r0=curRow();if(!(r0&&r0.keepInfo&&a==="a"))infoOpen=false}else{const r0=curRow(),R0=r0&&(r0.t==="result"?r0.row:r0);if(R0&&R0.more)infoOpen=!infoOpen;render();return}
  if(focus==="edit")return;
  if(a==="search"){if(focus!=="osk")openSearch(device==="pad"||!!(window.bsPlatform&&bsPlatform.os==="android"));return} /* (beta.10) Android: a tap opens the on-screen keyboard too (no keyboard to type on) */
  if((a==="lb"||a==="rb")&&focus!=="osk"){grab=null;if(results){results=null;query="";closeOsk();preSearch=null}goSec(secI+(a==="rb"?1:-1));focus=focus==="side"||focus==="search"?"side":"page";play("tick",a==="rb"?1:-1);render();return}
  if(focus==="osk"){
    if(["up","down","left","right"].includes(a))oskMove(a);
    else if(a==="a"){pressKey(keysNow().flat()[oskI])}
    else if(a==="x")backspace();else if(a==="y")typeChar(" ");else if(a==="start"||a==="rt")oskDone();
    else if(a==="b"){if(edit)cancelEdit();else closeOsk()}
    render();return}
  if(focus==="search"){
    if(a==="a")openOsk("search");
    else if(a==="down"){if(results&&results.length){focus="page";rowI=0}else if(!results)focus="side"}
    else if(a==="b"){if(results)closeSearch();else focus="side"}
    else if(a==="right"&&results&&results.length){focus="page";rowI=0}
    else if(a==="start"){close();return} /* (beta.8: Start closes Settings everywhere, search included) */
    render();return}
  const n=rowsOf(sec()).length,r=curRow();
  if(a==="start"){close();return}
  if(focus==="side"){
    if(a==="up"){if(secI===0){if(!rep)focus="search"}else{secI--;play("tick",-1)}sub=null;rowI=0;studioClear()}
    else if(a==="down"){if(secI<SECTIONS.length-1){secI++;play("tick",1)}sub=null;rowI=0;studioClear()}
    else if(a==="right"||a==="a"){focus="page";rowI=0;play("open")}
    else if(a==="b"){close();return}
  }else{
    if(grab){if(a==="up")moveOrder(-1);else if(a==="down")moveOrder(1);else if(a==="a"||a==="b"){grab=null;play("tick",1)}else if(a==="lt"||a==="rt"){}render();return}
    if(r&&r.t==="consoles"&&(a==="up"||a==="down"||a==="left"||a==="right")){const L=consoleList().length,G=E.root.classList.contains("narrow")?3:4;
      if(a==="right"){if(ci<L-1){ci++;play("tick",1)}render();return}
      if(a==="left"){if(ci%G>0){ci--;play("tick",-1);render();return}if(rep){return}focus="side";render();return}
      if(a==="down"&&ci+G<L){ci+=G;play("tick",1);render();return}
      if(a==="down"&&Math.floor(ci/G)<Math.floor((L-1)/G)){ci=L-1;render();return}
      if(a==="up"&&ci>=G){ci-=G;play("tick",-1);render();return}}
    // the theme tiles wrap onto lines: up / down move to the tile above or below (same column) before leaving the row
    if(r&&r.t==="theme"&&!results&&(a==="up"||a==="down")){const v=rowEls[rowI]&&rowEls[rowI]._val,tiles=v?[...v.querySelectorAll(".bss-tile")]:[],o=optsOf(r),i=idxOf(r,o);
      if(tiles.length>1&&tiles[i]){const xs=tiles.map(t=>t.offsetLeft),ys=tiles.map(t=>t.offsetTop),line=[...new Set(ys)].sort((p,q)=>p-q),li=line.indexOf(ys[i]),to=line[li+(a==="down"?1:-1)];
        if(to!=null){let j=-1,best=1e9;tiles.forEach((t,k)=>{if(ys[k]===to){const dx=Math.abs(xs[k]-xs[i]);if(dx<best){best=dx;j=k}}});
          if(j>=0){lastChange={r,d:a==="down"?1:-1};r.set(o[j][0]);play("tick",a==="down"?1:-1);changed(r);return}}}}
    if(a==="up"){if(rowI>0){rowI--;play("tick",-1)}else if(results&&!rep)focus="search";else if(!rep)play("error")}
    else if(a==="down"){if(rowI<n-1){rowI++;play("tick",1)}else if(!rep)play("error")}
    else if(a==="left"){const adj=r&&["choice","theme","slider","toggle"].includes(r.t);
      let can=false;if(r&&(r.t==="choice"||r.t==="theme")){can=idxOf(r,optsOf(r))>0}else if(r&&r.t==="slider")can=+r.get()>r.min+1e-9;else if(r&&r.t==="toggle")can=!!r.get();
      // a setting you can change: Left changes it (and stops at the end); only other rows step back out, and never on a held key
      if(can){change(-1);return}if(adj||rep){}else if(sub){leaveSub()}else if(results)focus="search";else focus="side"}
    else if(a==="right"){if(r){change(1);return}}
    else if(a==="home"){rowI=0}else if(a==="end"){rowI=Math.max(0,n-1)}
    else if(a==="a"){act();return}
    else if(a==="b"){if(sub)leaveSub();else if(results)focus="search";else focus="side";play("back")}
    else if(a==="y"){reset();return}
    else if(a==="lt"||a==="rt"){rowI=Math.max(0,Math.min(n-1,rowI+(a==="rt"?5:-5)))}
  }
  render();
}
// keys arrive from app.js while Settings is open (mode "setup")
function key(e){if(!active){return}
  if(focus==="edit"){device="key";if(e.key==="Enter"||e.key==="Tab"){e.preventDefault();const inp=E.list.querySelector(".bss-edit");if(inp&&edit)edit.value=inp.value;commitEdit()}else if(e.key==="Escape"){e.preventDefault();cancelEdit()}return}
  if(device!=="key"){device="key";oskSig=""}
  const typingKey=e.key.length===1&&!e.ctrlKey&&!e.altKey&&!e.metaKey&&/[\w '.\-]/.test(e.key);
  // typing into a setting with the on-screen keyboard up: switch to the real keyboard instead
  if(focus==="osk"&&edit){if(typingKey||e.key==="Backspace"){e.preventDefault();const r=edit.row,v=edit.value;edit=null;closeOsk();focus="page";startEdit(r);edit.value=e.key==="Backspace"?v.slice(0,-1):v+e.key;render();return}}
  else if(studio&&studio.v==="pick"&&focus==="page"&&(e.key==="f"||e.key==="F")&&!e.ctrlKey&&!e.altKey&&!e.metaKey){e.preventDefault();input("flt");return} // F: the picker's filters (not a search)
  else if(typingKey&&!(e.key===" "&&focus!=="search"&&focus!=="osk")&&!grab){e.preventDefault();if(!results)openSearch(false);if(focus==="osk")closeOsk();focus="search";typeChar(e.key);return}
  if((focus==="search"||focus==="osk")&&!edit&&e.key==="Backspace"){e.preventDefault();if(query)backspace();else closeSearch();return}
  if((focus==="search"||focus==="osk")&&!edit&&e.key==="Escape"){e.preventDefault();if(query){query="";runSearch();closeOsk();focus="search";render()}else closeSearch();return}
  if(focus==="search"&&e.key==="Enter"){e.preventDefault();if(results&&results.length)goResult(results[0]);return}
  if((e.key==="f"&&e.ctrlKey)||e.key==="/"){e.preventDefault();openSearch(false);return}
  if(focus==="osk"){const o={ArrowUp:"up",ArrowDown:"down",ArrowLeft:"left",ArrowRight:"right",Enter:"a",Tab:"start",Escape:"b"}[e.key];if(o){e.preventDefault();input(o,e.repeat)}return}
  const m={ArrowUp:"up",ArrowDown:"down",ArrowLeft:"left",ArrowRight:"right",Enter:"a"," ":"a",Escape:"b",Backspace:"b",Delete:"y",F1:"x",PageUp:"lt",PageDown:"rt",Home:"home",End:"end"}[e.key];
  if(e.key==="Tab"){e.preventDefault();input(e.shiftKey?"lb":"rb");return}
  if(e.key==="F11"){if(api.toggleFullscreen)api.toggleFullscreen();e.preventDefault();return}
  if(m){e.preventDefault();if(e.repeat&&(m==="a"||m==="b"||m==="y"||m==="x"))return;input(m,e.repeat)}
}
// the controller: polled here while Settings is up (every button, with repeat on held directions)
const BTN={2:"x",3:"y",4:"lb",5:"rb",8:"search",9:"start",12:"up",13:"down",14:"left",15:"right"},REP=new Set(["up","down","left","right","lt","rt"]);
const ACTS=["p0","p1","x","y","lb","rb","lt","rt","search","start","up","down","left","right"];
function repMs(){return ({relaxed:140,fast:60})[gp("ui.scrollSpeed")]||90}
function startPoll(){primed=false;if(!rafId)rafId=requestAnimationFrame(poll)}
function stopPoll(){if(rafId)cancelAnimationFrame(rafId);rafId=0;for(const k in held)delete held[k]}
function poll(t){rafId=0;if(!active)return;rafId=requestAnimationFrame(poll);
  // another window in front (an emulator opened from here, a file dialog): its controller presses aren't for Settings; back
  // in front, whatever is still held from there doesn't act either
  if(!document.hasFocus()){primed=false;return}
  let gpd=null;try{if(window.BS_activePad)gpd=window.BS_activePad();else for(const g of navigator.getGamepads?navigator.getGamepads():[])if(g&&g.connected){gpd=g;break}}catch{} /* the pad last used, as on the shelf */
  if(!gpd){primed=true;return}
  if(window.Shelf&&Shelf.padBlocked&&Shelf.padBlocked()){for(const k in held)held[k]=Infinity;for(const b of [0,1,2,3,4,5,6,7,8,9,12,13,14,15])if(gpd.buttons[b]&&gpd.buttons[b].pressed)held[b===0?"p0":b===1?"p1":b===6?"lt":b===7?"rt":BTN[b]]=Infinity;held.up=held.down=held.left=held.right=Infinity;return}
  if(gpd.id!==padId){padId=gpd.id;oskSig="";if(device==="pad")render()}
  const B=i=>!!(gpd.buttons[i]&&(gpd.buttons[i].pressed||gpd.buttons[i].value>.5)),ax=gpd.axes||[],st={};
  st.p0=B(0);st.p1=B(1);for(const[i,a]of Object.entries(BTN))if(B(+i))st[a]=true;if(B(6))st.lt=true;if(B(7))st.rt=true;
  // the left stick moves along its stronger axis only: held on a diagonal it would step a grid (the Art Studio's) both ways at once
  {const x=ax[0]||0,y=ax[1]||0,v=Math.abs(y)>=Math.abs(x);if(v&&y<-.55||(ax[3]||0)<-.6)st.up=true;if(v&&y>.55||(ax[3]||0)>.6)st.down=true;if(!v&&x<-.55)st.left=true;if(!v&&x>.55)st.right=true}
  if(!primed){primed=true;for(const a of ACTS)if(st[a])held[a]=Infinity;return} // the button that opened Settings doesn't act in it
  for(const a of ACTS){if(st[a]){if(held[a]===undefined){held[a]=t+380;padInput(a,false)}else if(REP.has(a)&&t>held[a]){held[a]=t+repMs();padInput(a,true)}}else delete held[a]}}
function padInput(a,rep){if(a==="p0")a=swapAB()?"b":"a";else if(a==="p1")a=swapAB()?"a":"b"; // held by the physical button, so swapping A / B mid-press can't fire the other one
  if(device!=="pad"){device="pad";oskSig="";if(focus==="edit"&&edit){const r=edit.row,v=(E.list.querySelector(".bss-edit")||{}).value;edit.value=v!=null?v:edit.value;focus="page";openOsk("edit")}}
  try{if(window.Shelf&&Shelf._wake)Shelf._wake()}catch{}input(a,rep)}

// Built and drawn once, invisibly, while Boxshelf is idle after starting: the first real open then has nothing left to
// prepare (building the page and drawing it the first time landed on the fade's first frame, a small hitch)
let warmed=false;
function prewarm(inf){if(warmed||isOpen||!inf||!inf.config)return;warmed=true;
  try{prep(inf,{});fit();isOpen=true;try{render()}finally{isOpen=false;active=false}root.classList.add("warm");void root.offsetWidth;
    requestAnimationFrame(()=>requestAnimationFrame(()=>requestAnimationFrame(()=>{if(root)root.classList.remove("warm")})))}catch(e){if(root)root.classList.remove("warm")}}
window.Settings={show,close,key,input,prewarm,bindv,bindb,active:()=>active,isOpen:()=>isOpen,repaint:()=>{if(isOpen)bump()}, // repaint: a background job's progress line (Android's box art fetch)
  
  _state:()=>({secI,sec:SECTIONS[secI]&&SECTIONS[secI].id,rowI,focus,sub:sub&&(sub.id||sub.kind),query,results:results&&results.length,device,osk:!!(E.osk&&E.osk.classList.contains("on")),edit:edit&&edit.value,row:curRow()&&curRow().label,needRescan,pending,studio:studio&&{v:studio.v,sys:studio.sys}})};
})();
