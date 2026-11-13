// Demo data so the screen can be previewed in a normal browser (no Electron).
// The real app replaces window.boxshelf from preload.js, so this file does nothing there.
(function(){
if(window.boxshelf)return;
const titles={
  gc:["Aether Drift","Bramblewood Tales","Cinder Circuit","Coral Kingdom","Echo Hollow","Ferrous Fist","Gale Riders","Kettle Knight","Lumen Isle","Moonmallow","Orbit Odyssey","Sky Harbor","Tidewalker"],
  wii:["Bubble Bay Bowling","Clockwork Carnival","Felt Friends","Jumble Jam","Paper Pilots","Snowglobe Party","Woolly Wonders"],
  wiiu:["Canopy Cup","Glimmer Gorge","Hollow Harbor Heroes","Pixel Plaza","Sunfield Saga","Tumble Town"],
  sw:["Astra Vanguard","Driftlight","Ember Hollow","Kitebound","Mosslight","Riftrunners","Tanuki Trail"],
  // ?big=N (beta.4 large-library check): every console gets N made-up titles instead (see the end of this list)
  psp:["Ashen Blade","Dusk Legion","Gridlock GP","Nova Ronin","Shardfall"],
  ps3:["Iron Meridian","Lanternfall","Saltwind","Velvet Static"],
  n3ds:["Pocket Pilots","Bramble Quest","Starfold","Chime Island"]
};
{const big=+((location.search.match(/[?&]big=(\d+)/)||[])[1]||0);if(big>0){const A=["Aether","Bramble","Cinder","Coral","Echo","Ferrous","Gale","Kettle","Lumen","Moon","Orbit","Sky","Tide","Ember","Frost","Nova"],B=["Drift","Tales","Circuit","Kingdom","Hollow","Fist","Riders","Knight","Isle","Quest","Odyssey","Harbor","Run","Saga","Party","Rally"];
  Object.keys(titles).forEach((k,ki)=>{titles[k]=Array.from({length:Math.min(big,20000)},(_,i)=>`${A[(i+ki)%16]} ${B[Math.floor(i/16)%16]} ${Math.floor(i/256)+1}`)})}}
const consoles=[
  {id:"gc",name:"GameCube",emulatorName:"Dolphin",exeNames:["Dolphin.exe"],extensions:["rvz","iso","gcm","ciso"],gameArgs:'-b -C Dolphin.Display.Fullscreen=True -C Dolphin.Interface.ConfirmStop=False -e "{rom}"'},
  {id:"wii",name:"Wii",emulatorName:"Dolphin",exeNames:["Dolphin.exe"],extensions:["rvz","iso","wbfs","wia"],gameArgs:'-b -C Dolphin.Display.Fullscreen=True -C Dolphin.Interface.ConfirmStop=False -e "{rom}"'},
  {id:"wiiu",name:"Wii U",emulatorName:"Cemu",exeNames:["Cemu.exe"],extensions:["wua","wud","wux","rpx"],gameArgs:'-f -g "{rom}"'},
  {id:"sw",name:"Switch",emulatorName:"Eden",exeNames:["eden.exe"],extensions:["nsp","xci","nca","nro"],gameArgs:'-f -g "{rom}"'},
  {id:"psp",name:"PSP",emulatorName:"PPSSPP",exeNames:["PPSSPPWindows64.exe"],extensions:["iso","cso","chd","pbp"],gameArgs:'--fullscreen --pause-menu-exit "{rom}"'},
  {id:"ps3",name:"PS3",emulatorName:"RPCS3",exeNames:["rpcs3.exe"],extensions:["bin","iso"],gameArgs:'--no-gui --fullscreen "{rom}"'},
  {id:"n3ds",name:"3DS",emulatorName:"Azahar",exeNames:["azahar.exe"],extensions:["3ds","cci","cxi"],gameArgs:'-f "{rom}"'}
];
const root="C:\\Users\\you\\Desktop\\Boxshelf";
const emu={gc:"Dolphin\\Dolphin.exe",wii:"Dolphin\\Dolphin.exe",wiiu:"Cemu\\Cemu.exe",sw:"Eden\\eden.exe",psp:"PPSPP\\PPSSPPWindows64.exe",ps3:"RPCS3\\rpcs3.exe",n3ds:"Azahar\\azahar.exe"};
const folder={gc:"Gamecube",wii:"Wii",wiiu:"Wii U",sw:"Switch",psp:"PSP",ps3:"PS3",n3ds:"3DS"};
// ?sw2=1 (beta.3 device test): a Switch 2 shelf with three Eglantine builds, for Settings → Switch 2 → Builds. Off by default,
// so the other passes keep their console counts
const SW2=/[?&]sw2=1/.test(location.search),PCX=/[?&]pcx=1/.test(location.search);
if(SW2){consoles.push({id:"sw2",name:"Switch 2",emulatorName:"Eglantine",exeNames:["eglantine.exe"],extensions:["nsp","xci","nup"],gameArgs:'-f -g "{rom}"'});titles.sw2=["Glimmer Kart","Hollow Ridge","Tidal Run"];emu.sw2="Switch 2 emu\\Selvet Forks\\performance-6\\eglantine.exe";folder.sw2="Switch 2"}
// ?retro=1 (beta.6 device test): a few of the retro shelves (RetroArch, DuckStation) with games, for their cases, titles,
// Settings pages and the wizard
const RETRO=/[?&]retro=1/.test(location.search);
if(RETRO){[["psx","PlayStation","DuckStation","DuckStation\\duckstation-qt-x64-ReleaseLTCG.exe","PS1","cue",["Crystal Cavern","Neon Drift","Polygon Knights"]],
  ["snes","SNES","RetroArch","RetroArch-Win64\\retroarch.exe","SNES","sfc",["Super Moon Quest","Pixel Paddle","Kart Comet"]],
  ["gba","Game Boy Advance","RetroArch","RetroArch-Win64\\retroarch.exe","GBA","gba",["Pocket Pilots Advance","Rune Rangers"]],
  ["megadrive","Mega Drive","RetroArch","RetroArch-Win64\\retroarch.exe","Mega Drive","md",["Blast Hedgehog","Street Rage"]],
  ["dreamcast","Dreamcast","RetroArch","RetroArch-Win64\\retroarch.exe","Dreamcast","chd",["Soul Spiral","Crazy Courier"]]].forEach(([id,name,en,ex,fo,e,ts])=>{
  consoles.push({id,name,emulatorName:en,exeNames:[ex.split("\\").pop()],extensions:[e],gameArgs:'"{rom}"'});titles[id]=ts;emu[id]=ex;folder[id]=fo})}
// ?x360=1 (beta.11): an Xbox 360 shelf, for its wrap-around cover
if(/[?&]x360=1/.test(location.search)){consoles.push({id:"x360",name:"Xbox 360",emulatorName:"Xenia",exeNames:["xenia_canary.exe"],extensions:["iso","xex"],gameArgs:'"{rom}"'});titles.x360=["Halo Frontier","Gear Storm","Forza Lane"];emu.x360="Xenia\\xenia_canary.exe";folder.x360="Xbox 360"}
// ?wave2=1 (beta.7): RetroArch installed and every other retro console it can run, without games, for the first run's
// tidy "More consoles" (RetroArch alone ticks none of them)
const WAVE2=/[?&]wave2=1/.test(location.search);
if(WAVE2)[["nes","NES"],["n64","Nintendo 64"],["gb","Game Boy"],["gbc","Game Boy Color"],["nds","DS"],["mastersystem","Master System"],["gamegear","Game Gear"],["saturn","Saturn"],["pcengine","TurboGrafx-16"],["neogeo","Neo Geo"],["arcade","Arcade"],["segacd","Sega CD"],["sega32x","32X"],["atari2600","Atari 2600"],["atarilynx","Lynx"],["wonderswan","WonderSwan"],["dos","DOS"]]
  .forEach(([id,name])=>{if(consoles.some(c=>c.id===id))return;consoles.push({id,name,emulatorName:"RetroArch",retro:true,exeNames:["retroarch.exe"],extensions:["zip"],gameArgs:'"{rom}"'});titles[id]=[];emu[id]="RetroArch-Win64\\retroarch.exe"});
// three of them with games, for their cases and wordmarks
if(WAVE2){Object.assign(titles,{saturn:["Astral Knights","Panzer Skies"],arcade:["Street Brawlers II","Metal Squad"],atari2600:["Pit Runner"]});Object.assign(folder,{saturn:"Saturn",arcade:"Arcade",atari2600:"Atari 2600"})}
// ?dos=1 (beta.11, with wave2): a DOS game, for its floppy disk (off by default: the first-run test wants DOS without games)
if(WAVE2&&/[?&]dos=1/.test(location.search)){titles.dos=["Dungeon Quest"];folder.dos="DOS"}
function mockSw2Builds(){const s=config.systems.sw2||{},B=[["Switch 2 emu\\Selvet Forks\\performance-6\\eglantine.exe","Selvet · Performance 6",false],["Switch 2 emu\\Lichii\\pgo\\lichii.exe","Lichii pgo",true],["Switch 2 emu\\Orchid v0.7.0\\orchid.exe","Orchid v0.7.0",false]].map(([e,label,nup],i)=>({exe:root+"\\"+e,group:"",name:label,label,date:Date.UTC(2026,9,20+i),nup}));
  (Array.isArray(s.buildDirs)?s.buildDirs:[]).forEach((d,i)=>B.push({exe:d+"\\fork-"+i+"\\eglantine.exe",group:"",name:"Fork "+(i+1),label:"Fork "+(i+1),date:Date.UTC(2026,10,1),nup:false}));
  (Array.isArray(s.buildExes)?s.buildExes:[]).forEach(x=>B.push({exe:x,group:"",name:x.split("\\").pop(),label:"Added · "+x.split("\\").pop(),date:Date.UTC(2026,10,2),nup:false}));
  const hid=new Set((Array.isArray(s.buildHidden)?s.buildHidden:[]).map(x=>String(x).toLowerCase())); // (beta.11) removed in Settings, as main.js does
  return B.filter(b=>!hid.has(b.exe.toLowerCase()))}
let config={version:1,setupDone:false,systems:{},steam:{enabled:true,path:""},apps:{stremio:{enabled:true,path:""},nuvio:{enabled:true,path:""}},ui:{sound:true,startFullscreen:true}};
{const q=(location.search.match(/[?&]previews=(\w+)/)||[])[1];if(q)Object.assign(config.ui,{previews:q,previewDelay:1})} // (beta.7) ?previews=details|shelf: video previews on
consoles.forEach(c=>config.systems[c.id]={enabled:true,emulator:"",gameArgs:c.gameArgs,emulatorArgs:"",romDirs:[],artDir:"",bootOnLaunch:true});
// Inside the Android shell (or with ?shelf=1) skip the wizard and open on a filled demo shelf, for performance testing
if(window.Capacitor||/[?&]shelf=1/.test(location.search)){config.setupDone=true;consoles.forEach(c=>{config.systems[c.id].emulator=root+"\\"+emu[c.id];config.systems[c.id].romDirs=[root+"\\ROMS\\"+folder[c.id]];});}
const ext={gc:"rvz",wii:"rvz",wiiu:"wua",sw:"nsp",psp:"iso",ps3:"iso",n3ds:"3ds",sw2:"nsp"};
if(RETRO)Object.assign(ext,{psx:"cue",snes:"sfc",gba:"gba",megadrive:"md",dreamcast:"chd"});
if(WAVE2)Object.assign(ext,{saturn:"chd",arcade:"zip",atari2600:"a26"});
let onExit=()=>{};const apiHooks={};
const wait=ms=>new Promise(r=>setTimeout(r,ms));const mockDl={};
window.boxshelf={
  // Demo platform: 'android' inside the Capacitor shell (window.Capacitor), else 'web'; ?platform=windows|android overrides for testing
  platform:(()=>{const q=(location.search.match(/[?&]platform=(\w+)/)||[])[1];const os=q||(window.Capacitor?'android':'web');const all=os==='windows';
    const lx=os==='linux'; /* (beta.12) ?platform=linux: what preload.js reports there (power and start at login on, no Home menu or Windows-only rows) */
 return {os,caps:{steam:all||lx,power:all||lx,homeMenu:all,emuSettings:all,autostart:all||lx,displays:all||lx,folders:all||lx,companionServer:all||lx,winMode:all,dolphinIpl:all,raAutoConfig:all}};})(),
  // Phone companion (Settings → Phone companion): a fake server state; the code is a stand-in picture (the real one is drawn in main/companion.js)
  ...(()=>{const qr='<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 29 29"><rect width="29" height="29" fill="#fff"/><path d="M4 4h7v7H4zM18 4h7v7h-7zM4 18h7v7H4zM13 13h3v3h-3zM18 18h2v2h-2zM22 21h3v3h-3z" fill="#000"/></svg>';
    let st={enabled:false,on:false,url:"",port:8730,wantPort:8730,pin:"",qr:"",error:"",note:"",locked:0,phones:[{id:"a1",name:"Pixel 7",added:Date.now()-864e5*3,seen:Date.now()-3600e3}]};
    const view=()=>JSON.parse(JSON.stringify(st)),up=()=>{st.on=st.enabled;st.port=st.wantPort;st.url=st.on?`http://192.168.1.40:${st.port}`:"";st.pin=st.on?"482910":"";st.qr=st.on?qr:""};
    return {companionState:async()=>view(),companionSet:async o=>{await wait(150);if(o.enabled!=null)st.enabled=!!o.enabled;if(o.port!=null)st.wantPort=+o.port;up();return view()},
      companionForget:async id=>{st.phones=id==="all"?[]:st.phones.filter(p=>p.id!==id);return view()},companionPin:async()=>{st.pin="173064";return view()},onCompanionState:()=>{},onCompanionInput:fn=>{window.__companionIn=fn}}})(),
  history:async()=>({}),findModel:async()=>({ok:true,dir:"Downloads"}),cancelFindModel:async()=>true,onModelInstalled:()=>{},historyTouch:async()=>true,openTrailer:async()=>({external:false}),
  // (beta.7) video previews: a short clip the page records itself (no video file shipped), YouTube answered as playing
  ...(()=>{let demo=null;const ytFns=[];const demoUrl=()=>window.__previewUrl?Promise.resolve(window.__previewUrl) /* (the device test hands in a real clip: android-app/ci/fixtures/preview.webm) */:demo||(demo=new Promise(res=>{try{const c=document.createElement("canvas");c.width=480;c.height=270;const x=c.getContext("2d"),st=c.captureStream(24),rec=new MediaRecorder(st,{mimeType:"video/webm"}),parts=[];
      rec.ondataavailable=e=>{if(e.data&&e.data.size)parts.push(e.data)};rec.onstop=()=>res(URL.createObjectURL(new Blob(parts,{type:"video/webm"})));const t0=performance.now();
      const iv=setInterval(()=>{const s=(performance.now()-t0)/1000;const g=x.createLinearGradient(0,0,480,270);g.addColorStop(0,`hsl(${220+s*40},70%,30%)`);g.addColorStop(1,`hsl(${300+s*40},70%,18%)`);x.fillStyle=g;x.fillRect(0,0,480,270);
        for(let i=0;i<6;i++){x.fillStyle=`hsla(${(i*60+s*90)%360},80%,60%,.55)`;x.beginPath();x.arc(240+Math.cos(s*1.6+i)*150,135+Math.sin(s*2+i*1.3)*80,22+i*4,0,7);x.fill()}
        x.fillStyle="rgba(255,255,255,.9)";x.font="bold 30px sans-serif";x.fillText("Preview",176,145);if(s>2.6){clearInterval(iv);rec.stop()}},40);rec.start(250)}catch(e){res(null)}}));
    const hash=s=>[...String(s||"")].reduce((a,ch)=>(a*31+ch.charCodeAt(0))>>>0,7);
    // (beta.8) ?clips=1: console trailers. Every console game has only a YouTube trailer; its clip is "made" 1.5 s after
    // the first ask (api.onPreviewClip), then kept. The helper starts missing; clipHelper "fetches" it with progress.
    const CLIPS=/[?&]clips=1/.test(location.search),clipFns=[],progFns=[],kept=new Set();let helper=!CLIPS||/[?&]clips=1&helper=1/.test(location.search); /* (only ?clips=1 alone starts without the helper) */
    return {previewFind:async o=>{const m=(config.ui||{}).previews||"off";if(m==="off")return null;await wait(60);
        if(/^steam-/.test(String(o.id||""))){const u=await demoUrl();return u?{type:"file",url:u,steam:true}:null}
        if(CLIPS&&o.file){const id="c"+hash(o.title).toString(36).slice(0,8);if(kept.has(id)){const u=await demoUrl();return {type:"file",url:u,yt:id}}
          if(helper){setTimeout(async()=>{const u=await demoUrl();kept.add(id);window.__clipsMade=(window.__clipsMade||0)+1;clipFns.forEach(f=>f({id,url:u}))},1500);return {type:"youtube",id,pending:true}}
          return {type:"youtube",id}}
        if(o.file&&hash(o.title)%3!==0){const u=await demoUrl();return u?{type:"file",url:u}:null}
        return o.youtube&&(config.ui||{}).previewYoutube!==false?{type:"youtube",id:"aaaaaaaaaaa"}:null},
      previewYt:async(id,rect,muted)=>{window.__ytRect=rect;window.__ytOn=true;window.__ytMuted=muted;setTimeout(()=>ytFns.forEach(f=>f({id,state:"playing"})),500);return true},
      // (beta.12) a clip's sound for Details' box: a few seconds of silence as a WAV (made here, nothing fetched); window.__noClipAudio:
  // the sound can't be had; window.__ownSound: the video has its own (a Steam trailer)
  previewClipAudio:async url=>{await new Promise(r=>setTimeout(r,250));if(window.__ownSound)return {clip:false};if(window.__noClipAudio)return {clip:true,url:null};
    const n=8000*3,b=new Uint8Array(44+n),dv=new DataView(b.buffer),s=(p,t)=>{for(let i=0;i<t.length;i++)b[p+i]=t.charCodeAt(i)};
    s(0,"RIFF");dv.setUint32(4,36+n,true);s(8,"WAVEfmt ");dv.setUint32(16,16,true);dv.setUint16(20,1,true);dv.setUint16(22,1,true);dv.setUint32(24,8000,true);dv.setUint32(28,8000,true);dv.setUint16(32,1,true);dv.setUint16(34,8,true);s(36,"data");dv.setUint32(40,n,true);b.fill(128,44);
    window.__clipAudioAsked=(window.__clipAudioAsked||0)+1;return {clip:true,url:URL.createObjectURL(new Blob([b],{type:"audio/wav"}))}}, /* (blob:, as the page's media-src allows; data: isn't) */
  previewYtMove:async(rect,show)=>{window.__ytOn=!!show;if(rect)window.__ytRect=rect},previewYtStop:async()=>{window.__ytOn=false;window.__ytStops=(window.__ytStops||0)+1},
      onPreviewYt:fn=>{ytFns.push(fn)},previewClearSteam:async()=>true,
      onPreviewClip:fn=>{clipFns.push(fn)},onClipProgress:fn=>{progFns.push(fn)},
      /* (beta.12) the Linux Game Mode browser (picker.js): window.__pickOpen(d) asks, window.__picked is the answer; a made-up home folder */
      onPickOpen:fn=>{window.__pickOpen=fn},pickDone:(id,p)=>{window.__picked={id,path:p}},onNotice:fn=>{window.__notice=fn},
      fsPlaces:async()=>[{label:"Home",path:"/home/deck"},{label:"SD card",path:"/run/media/mmcblk0p1"},{label:"Emulation (EmuDeck)",path:"/home/deck/Emulation"}],
      fsList:async dir=>{const T={"/home/deck":[{n:"Applications",d:true},{n:"Emulation",d:true},{n:"notes.txt",d:false}],"/home/deck/Emulation":[{n:"roms",d:true},{n:"bios",d:true}],"/home/deck/Emulation/roms":[{n:"gc",d:true},{n:"snes",d:true}],"/home/deck/Emulation/roms/snes":[{n:"Game.sfc",d:false},{n:"Other.zip",d:false}],"/home/deck/Applications":[{n:"Dolphin.AppImage",d:false}],"/run/media/mmcblk0p1":[{n:"Emulation",d:true}]};
        const d=dir||"/home/deck",up=d.replace(/\/[^/]+$/,"")||"/";return {dir:d,parent:d==="/"?"":up,entries:T[d]||[]}},
      // (beta.8) ?deck=desktop: a Steam Deck in Desktop Mode, Boxshelf not started by Steam (?deck=steam: started by Steam)
      deckInfo:async()=>{const m=(location.search.match(/[?&]deck=(\w+)/)||[])[1];return m?{linux:true,deck:true,steam:m==="steam",gameMode:false,canAdd:true,inSteam:!!window.__inSteam,flags:[]}:{linux:false,deck:false,steam:false,gameMode:false,canAdd:false}},
      addToSteam:async()=>{await wait(200);window.__added=(window.__added||0)+1;window.__inSteam=true;return {ok:true,users:1,updated:false,reopened:true}},
      clipStatus:async()=>({supported:true,helper,getting:false,n:kept.size,bytes:kept.size*4e6}),
      clipHelper:async()=>{for(const p of [10,45,80,100]){await wait(120);progFns.forEach(f=>f({pct:p}))}helper=true;progFns.forEach(f=>f({done:true,ok:true}));return true},
      clipClear:async()=>{kept.clear();return true},
      previewCount:async()=>{await wait(300);return {total:23,systems:[{id:"gc",name:"GameCube",n:9,of:13},{id:"wii",name:"Wii",n:5,of:7},{id:"psp",name:"PSP",n:4,of:5},{id:"ps3",name:"PS3",n:5,of:7}],steamBytes:18874368}}}})(),closeTrailer:async()=>true,onTrailerClosed:()=>{},
  getConfig:async()=>({...((location.search.match(/[?&]ver=([\w.-]+)/)||[])[1]?{version:(location.search.match(/[?&]ver=([\w.-]+)/)||[])[1]}:{}) /* (beta.7) ?ver=: the app version, for What's new */,config:JSON.parse(JSON.stringify(config)),consoles,apps:[{id:"stremio",name:"Stremio",exeNames:["stremio.exe"]},{id:"nuvio",name:"Nuvio",exeNames:["Nuvio.exe"]}],appRoot:root}),
  saveConfig:async c=>{config=JSON.parse(JSON.stringify(c));return config},
  detect:async()=>{await wait(700);const systems={};consoles.forEach(c=>systems[c.id]={emulator:root+"\\"+emu[c.id],romDirs:folder[c.id]?[root+"\\ROMS\\"+folder[c.id]]:[]});return {systems,steam:{path:"C:\\Program Files (x86)\\Steam"},apps:{stremio:{path:"C:\\Users\\you\\AppData\\Local\\Programs\\LNV\\Stremio-4\\stremio.exe"},nuvio:{path:null}},appRoot:root}},
  scan:async()=>{
    const out=[];
    consoles.forEach(c=>{const s=config.systems[c.id];if(!s.enabled||(!s.emulator&&!s.romDirs.length))return;
      out.push({id:c.id,kind:"console",name:c.name,emulatorName:c.emulatorName,hasEmulator:!!s.emulator,emulator:s.emulator,gameArgs:s.gameArgs,bootOnLaunch:s.bootOnLaunch,
        games:s.romDirs.length?titles[c.id].map((t,i)=>({kind:"rom",id:c.id+i,...(()=>{const o=(config.games||{})[c.id+":"+`${s.romDirs[0]}\\${t} (USA).${ext[c.id]}`];return o&&o.title?{title:o.title,fileTitle:t}:{title:t}})(),region:"USA",ext:ext[c.id],sizeBytes:(400+i*97)*1048576,file:`${s.romDirs[0]}\\${t} (USA).${ext[c.id]}`,discs:[1],info:{release:String(1999+((i*7)%12))+"-06-01",dev:["Moonbeam Works","Hollow Pine","Studio Kite"][i%3],pub:["Northlight","Moonbeam Works"][i%2],genres:["Platform","Racing","Puzzle","Adventure"][i%4],rating:(2.8+((i*13)%22)/10).toFixed(1),...(window.__TRAILERS&&i%2===0?{video:"https://www.youtube.com/watch?v=aaaaaaaaaaa"}:{}),...(/[?&]shots=1/.test(location.search)?{shots:Array.from({length:8},(_,k)=>"data:image/svg+xml,"+encodeURIComponent(`<svg xmlns="http://www.w3.org/2000/svg" width="320" height="180"><rect width="320" height="180" fill="hsl(${k*40},50%,40%)"/></svg>`))}:{})} /* __TRAILERS: some games with a trailer (the museum TV's trailers channel); ?shots=1: eight screenshots each */,art:i===0&&c.id==="gc"?{manual:window.__mockManual||[]}:{}})):[]})});
    if(config.steam.enabled&&config.steam.path)out.push({id:"pc",kind:"steam",name:"PC",games:["Cobalt Drift","Glasshouse","Hollow Crown","Night Courier"].map((t,i)=>({kind:"steam",id:"s"+i,appid:String(400000+i),title:t,minutes:i*510,lastPlayed:Date.now()/1000-i*86400*3,sizeBytes:(8+i*11)*1073741824,art:{}}))});
    // ?pcx=1 (beta.4 device test): an Epic game on the PC shelf, and a Custom shelf (a Quiver recomp + the games added in Settings)
    if(PCX){const pc=out.find(s=>s.id==="pc")||(out.push({id:"pc",kind:"steam",name:"PC",games:[]}),out[out.length-1]);pc.games.push({kind:"steam",source:"epic",id:"epic-ember-trail",title:"Ember Trail",art:{}});/* (beta.7) one from each new store */for(const [src,id,title] of [["gog","gog-1207658924","Lantern Keep"],["ubisoft","ubisoft-4740","Harbor Run"],["ea","ea-origin-1234","Dust Rally"],["xbox","xbox-lumen-garden","Lumen Garden"]])pc.games.push({kind:"steam",source:src,id,title,art:{}});
      const own=(id,t)=>{const o=(config.games||{})[id];return o&&o.title?{title:o.title,fileTitle:t}:{title:t}};
      const mine=[{kind:"steam",source:"quiver",id:"quiver-quarantine",...own("quiver-quarantine","Quarantine"),art:{}},...((config.pc&&config.pc.custom)||[]).map(c=>({kind:"steam",source:"custom",id:"custom-"+c.id,title:c.title||"Game",art:{}}))];
      out.push({id:"custom",kind:"steam",name:"Custom",games:mine})}
    const apps=["stremio","nuvio"].filter(a=>config.apps[a].enabled&&config.apps[a].path).map(a=>({kind:"app",id:"app-"+a,appId:a,title:a==="stremio"?"Stremio":"Nuvio",path:config.apps[a].path,art:{}}));
    if(apps.length)out.push({id:"media",kind:"apps",name:"Media",games:apps});
    return out;
  },
  pick:async o=>o.kind==="folder"?root+"\\ROMS\\Picked":o.multi?[root+"\\Games\\Star Rally\\StarRally.exe",root+"\\Games\\Moon Forge\\bin\\x64\\MoonForge.exe"]:root+"\\Picked\\program.exe",
  // (beta.7) Add games: a pretend folder of games and a pretend Playnite library
  gameTitles:async exes=>exes.map(e=>({exe:e,title:/Moon Forge/.test(e)?"Moon Forge":/Star Rally/.test(e)?"Star Rally":e.split("\\").pop().replace(/\.exe$/i,"")})),
  scanGameFolder:async r=>{await wait(300);return {root:r,games:[["Aurora Drift","AuroraDrift.exe"],["Clockwork Garden","Clockwork.exe"],["Neon Harbor","NeonHarbor.exe"]].map(([t,e])=>({folder:r+"\\"+t,title:t,exe:r+"\\"+t+"\\"+e,others:[]}))}},
  // (beta.12) RomM, pretend: pairing approves on the 2nd poll; a wrong code is refused; Test / Sync answer
  rommStatus:async()=>({server:"",paired:!!window.__rommPaired,count:window.__rommPaired?3:null,at:null,offline:false,skipped:null}),
  rommPairStart:async()=>{window.__rommPolls=0;return {ok:true,userCode:"ABCD-EFGH",qrUrl:"http://romm.local/pair/device?user_code=ABCDEFGH",qr:'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 10 10" class="pretend-qr"><rect width="10" height="10" fill="#fff"/><rect x="1" y="1" width="3" height="3"/></svg>',expiresAt:Date.now()+600000}},
  rommPairPoll:async()=>{window.__rommPolls=(window.__rommPolls||0)+1;if(window.__rommPolls>=2){window.__rommPaired=true;return {state:"ok"}}return {state:"pending",wait:1000}},
  rommPairCancel:async()=>true,
  rommPairCode:async code=>code==="12345678"?(window.__rommPaired=true,{ok:true}):{ok:false,error:"not accepted"},
  rommTest:async()=>window.__rommPaired?{ok:true,version:"5.4.1",platforms:3}:{ok:false,error:"auth"},
  rommSync:async()=>({ok:true,count:3,offline:false}),
  rommForget:async()=>{window.__rommPaired=false;return true},
  rommCancel:async()=>{window.__rommCancel=(window.__rommCancel||0)+1;return true},
  // (beta.12) RocketLauncher, pretend: found with ?rl=1, GameCube set up and in the same folder, NES with no emulator yet
  rocketFind:async()=>/[?&]rl=1/.test(location.search)?"C:\\HyperSpin\\RocketLauncher\\RocketLauncher.exe":"",
  rocketSystems:async()=>/[?&]rl=1/.test(location.search)?{list:[{name:"Nintendo GameCube",ready:true},{name:"Nintendo Entertainment System",ready:false},{name:"Sony PlayStation 2",ready:true}],
    names:{gc:{name:"Nintendo GameCube",known:true,ready:true,folders:1,matches:true},nes:{name:"Nintendo Entertainment System",known:true,ready:false,folders:1,matches:false},ps2:{name:"Sony PlayStation 2",known:true,ready:true,folders:1,matches:false}}}:{list:[],names:{}},
  playniteFind:async()=>/[?&]playnite=1/.test(location.search)?{app:"C:\\Users\\you\\AppData\\Local\\Playnite",library:"C:\\Users\\you\\AppData\\Roaming\\Playnite\\library",portable:false}:null,
  playnitePlan:async()=>{await wait(400);return {total:9,app:"C:\\Users\\you\\AppData\\Local\\Playnite",portable:false,pc:[{key:"p1",title:"Lantern Path",exe:"D:\\Games\\Lantern Path\\lantern.exe",args:"",cover:null,favorite:true,there:true},{key:"p2",title:"Old Tower",exe:"D:\\Games\\Old Tower\\tower.exe",args:"-w",cover:null,favorite:false,there:false}],consoles:{gc:{dirs:["E:\\ROMs\\GameCube"],count:3,emulatorDir:"D:\\Emus\\Dolphin"}},skipped:{store:2,hidden:1,link:1,nothing:0}}},
  emulatorIn:async()=>null,gameCovers:async items=>items.length,
  // (beta.5) Details → Rename: a name of its own per game (main keeps it in config.games; the scan above applies it)
  renameGame:async({systemId,file,id,title})=>{await wait(80);const k=systemId?systemId+":"+file:String(id);config.games=config.games||{};const v=String(title||"").trim();if(v)config.games[k]=Object.assign({},config.games[k],{title:v});else if(config.games[k])delete config.games[k].title;return {ok:true,title:v}},
  ...(PCX?{pcCoverOptions:async(id,term)=>{await wait(150);window.__pcCoverTerm=term||"";const svg=(c,t)=>"data:image/svg+xml;charset=utf-8,"+encodeURIComponent(`<svg xmlns="http://www.w3.org/2000/svg" width="200" height="300"><rect width="200" height="300" fill="${c}"/><text x="20" y="160" font-size="22" fill="#fff">${t}</text></svg>`);
      const cur=(window.__pcCovers||{})[id];
      if(window.__noSgdb)return {sgdb:false,current:!!cur,list:[...(cur?[{key:"current",thumb:svg("#555","Yours"),source:"yours",label:"Your choice now"}]:[]),{key:"st0",thumb:svg("#1b2838","Steam"),source:"steamstore",label:"Steam's cover",sub:"On Steam"}]}; // (beta.9) no SteamGridDB key: Steam's store cover only
      return {sgdb:true,current:!!cur,list:[...(cur?[{key:"current",thumb:svg("#555","Yours"),source:"yours",label:"Your choice now"}]:[]),{key:"sg0",thumb:svg("#a33","Grid 1"),source:"steamgriddb",label:"SteamGridDB · Alternate",sub:"by someone"},{key:"sg1",thumb:svg("#3a3","Grid 2"),source:"steamgriddb",label:"SteamGridDB",sub:"by another"},{key:"sg2",thumb:svg("#33a","Grid 3"),source:"steamgriddb",label:"SteamGridDB · White logo",sub:""}]}},
    pcCoverUse:async(id,key)=>{await wait(100);window.__pcCovers=Object.assign(window.__pcCovers||{},{[id]:key});return {ok:true}},pcCoverOwn:async id=>{window.__pcCovers=Object.assign(window.__pcCovers||{},{[id]:"own"});return {ok:true}},
    pcCoverReset:async id=>{if(window.__pcCovers)delete window.__pcCovers[id];return {ok:true}},
    // (beta.9) the Custom shelf's games without a cover chosen yet (the batch cover finder)
    pcCoverMissing:async()=>{await wait(80);const s=(window.Shelf&&Shelf._lib?Shelf._lib.shelves():[]).find(x=>x.id==="custom");return (s?s.ids:[]).filter(id=>!(window.__pcCovers||{})[id]).map(id=>({id,title:id,source:"custom"}))},
    launchPc:async id=>{window.__pcLaunched=(window.__pcLaunched||[]).concat(id);await wait(100);if(/^(epic|ubisoft|ea|xbox)-/.test(id))return {external:true};setTimeout(()=>onExit({code:0}),3000);return {pid:4242,cover:3}},pcStatus:async()=>({quiver:root+"\\Quiver",epic:1,gog:1,ubisoft:1,ea:1,xbox:1}),findQuiver:async()=>root+"\\Quiver"}:{}),
  ...(SW2?{sw2Builds:async()=>{await wait(120);const b=mockSw2Builds();return {builds:b,nup:false,first:b[0]&&b[0].exe}},
    sw2AddBuild:async exe=>{const s=config.systems.sw2=config.systems.sw2||{},low=String(exe).toLowerCase();s.buildHidden=(s.buildHidden||[]).filter(x=>String(x).toLowerCase()!==low);if(!(s.buildExes||[]).some(x=>String(x).toLowerCase()===low))s.buildExes=[...(s.buildExes||[]),exe];return {ok:true}}}:{}),
  downloadCovers:async ids=>({systems:ids.map(id=>({id,name:id,total:10,front:8,back:6,spine:5,had:0,missed:["Example Game"]}))}),
  onCoversProgress:fn=>{(window.__mockCov=window.__mockCov||[]).push(fn)},
  findManuals:async ids=>{await wait(800);return ["Aether Drift","Bramblewood Tales","Cinder Circuit","Coral Kingdom","Echo Hollow","Ferrous Fist","Gale Riders","Kettle Knight","Lumen Isle","Moonmallow","Orbit Odyssey","Sky Harbor"].map((t,i)=>({systemId:"gc",system:"GameCube",title:t,file:root+"\\ROMS\\Gamecube\\"+t+" (USA).rvz",have:i===2,candidates:i===2?[]:[{id:"demo-"+i,title:t+" GameCube Manual",language:"eng",pages:24,score:8,thumb:"",url:"#"},{id:"demo-fr-"+i,title:t+" mode d'emploi",language:"fre",pages:24,score:4.5,thumb:"",url:"#"}]}))},
  downloadManuals:async ch=>{await wait(800);return ch.map(c=>({file:c.file,pages:24}))},
  findIpl:async()=>({found:[],userDir:root+"\\Dolphin\\User",usaFolder:root+"\\Dolphin\\User\\GC\\USA"}),
  openIplFolder:async()=>"",
  checkPaths:async list=>list.map(p=>({path:p,file:/\.exe$/i.test(p),dir:!/\.exe$/i.test(p)})),
  launchGame:async()=>{setTimeout(()=>onExit({code:0}),4000);return {}},
  launchEmulator:async()=>{setTimeout(()=>onExit({code:0}),3000);return {}},
  launchApp:async()=>{setTimeout(()=>onExit({code:0}),3000);return {}},
  // (beta.12) ?watch=1: Seamless play watches the Steam game (it "ends" 3 s later, or ?watch=giveup: it never shows up),
  // as main/seamless.js does; Seamless play off (or no ?watch): handed to Steam, as before
  steamExit:async()=>{window.__steamExit=(window.__steamExit||0)+1;return true}, // (beta.12)
  launchSteam:async appid=>{const w=window.__watchMode||(/[?&]watch=(\w+)/.exec(location.search)||[])[1];if(!w||(config.ui&&config.ui.seamless===false))return true;window.__watched=(window.__watched||[]).concat(String(appid));
    if(w==="giveup")setTimeout(()=>{if(window.__gaveUp)window.__gaveUp()},2500);else setTimeout(()=>onExit({code:0,watched:true}),3000);return {watching:true,cover:3}},
  setScreenMode:async m=>{(window.__screenModes=window.__screenModes||[]).push(m);config.ui.screenMode=m;return m},
  watchCancel:async()=>({cancelled:false}),onWatchGaveUp:fn=>{window.__gaveUp=fn},
  stopRunning:async()=>{},
  themes:async()=>(window.__mockThemes||{}),
  // (1.76) custom themes: one pretend picture theme (an SVG sky); "Make a theme from a picture" adds a second; tests can
  // set window.__mockCustom to a list of their own
  customThemes:async()=>(window.__mockCustom||(window.__mockCustom=[{id:"custom-test-sky",name:"Test Sky",accent:"#3a8fd6",shade:.25,blur:false,glow:.4,sys:{},
    bg:"data:image/svg+xml,"+encodeURIComponent('<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 160 90"><defs><linearGradient id="g" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#0b2a5a"/><stop offset="1" stop-color="#5fb4f0"/></linearGradient></defs><rect width="160" height="90" fill="url(#g)"/><circle cx="120" cy="24" r="10" fill="#fff6c8"/><path d="M0 70 Q40 52 80 68 T160 64 V90 H0Z" fill="#1d4f2a"/></svg>')}])),
  customThemeCreate:async(file,name)=>{const l=window.__mockCustom||[];const id="custom-picked-"+(l.length+1);l.push({id,name:name||"Picked "+(l.length+1),accent:"#d66a3a",shade:.35,blur:true,glow:.35,sys:{},bg:l[0]?l[0].bg:""});window.__mockCustom=l;return id},
  customThemesOpen:async()=>true,
  setTheme:async()=>{throw new Error("Preview mode: logos work in the desktop app")},
  removeTheme:async()=>{},
  // Find a logo: drawn stand-ins at the real files' sizes (960px renders: wide black wordmark, square symbol, tall stack, a broken one). window.__mockLogos = "offline" tries that state
  findLogos:async id=>{await new Promise(r=>setTimeout(r,350));const e=window.__mockLogos;if(e==="offline")return{ok:true,list:[],offline:true};
    const word=String(id||"").toUpperCase(),svg=(w,h,fill,txt,bg)=>"data:image/svg+xml;utf8,"+encodeURIComponent(`<svg xmlns="http://www.w3.org/2000/svg" width="${w}" height="${h}" viewBox="0 0 ${w} ${h}">${bg?`<rect width="${w}" height="${h}" rx="${h/6}" fill="${bg}"/>`:""}<text x="${w/2}" y="${h*0.68}" font-family="Arial Black,Arial" font-weight="900" font-size="${Math.min(h*0.7,w/(String(txt||word).length*0.7))}" text-anchor="middle" fill="${fill}">${txt||word}</text></svg>`);
    const list=[{key:"cm-"+id+"-0",source:"commons",label:`${word} logo`,licence:"Public domain",by:"",preview:svg(960,163,"#111")},{key:"cm-"+id+"-1",source:"commons",label:`${word} symbol`,licence:"CC0",by:"",preview:svg(960,960,"#7b6cff","◆")},
      {key:"cm-"+id+"-2",source:"commons",label:`${word} logo, stacked`,licence:"Public domain",by:"",preview:svg(600,960,"#fff","S","#e60012")},{key:"cm-"+id+"-3",source:"commons",label:"Broken file",licence:"Public domain",by:"",preview:"data:image/png;base64,AAAA"}];
    return{ok:true,list,offline:false}},
  useLogo:async(id,key)=>{const L=await window.boxshelf.findLogos(id);const c=L.list.find(x=>x.key===key);if(!c)return{ok:false,error:"That logo is no longer offered."};const T=window.__mockThemes=window.__mockThemes||{};const t=T[id]=T[id]||{};t.logoPrev=true;t.logo=URL.createObjectURL(new Blob([decodeURIComponent(c.preview.split(",")[1])],{type:"image/svg+xml"}));return{ok:true}}, /* (a blob: address, as the CSP lets the logo be read; the app hands over bsfile: ones) */
  restoreLogo:async id=>{const T=window.__mockThemes||{},t=T[id];if(!t||!t.logoPrev)throw new Error("No earlier logo to bring back.");t.logo=null;t.logoPrev=false;return true},
  saveConsoleFit:async(id,p)=>{(window.__fitSaves=window.__fitSaves||[]).push([id,p]);return p},
  openThemeFolder:async()=>{},
  pathForFile:()=>null,
  toggleFullscreen:async()=>{},
  quit:async()=>{},power:async k=>{console.log("power",k);return true},chooseArt:async id=>({ok:false,canceled:true}),gameEmulator:async id=>{config.games=config.games||{};if(config.games[id])delete config.games[id];else config.games[id]={emulator:"C:/Emus/shadPS4-perf/shadps4.exe"};return {ok:true,games:JSON.parse(JSON.stringify(config.games))}},
  // (beta.12) Details → Status, kept per game as main does (cfg.games[id].status)
  gameStatus:async(id,status)=>{config.games=config.games||{};const k=String(id),o=Object.assign({},config.games[k]);if(["playing","finished","notstarted","abandoned"].includes(status))o.status=status;else delete o.status;if(Object.keys(o).length)config.games[k]=o;else delete config.games[k];window.__statusSaved=(window.__statusSaved||0)+1;return {ok:true,games:JSON.parse(JSON.stringify(config.games))}},
  onExited:fn=>{onExit=fn},
  // 1.55 Settings APIs (fakes): displays, backup / restore, problem report, play history, account status
  listDisplays:async()=>[{id:"1",label:"Display 1 — 1920×1080 (main)",primary:true,current:true,width:1920,height:1080},{id:"2",label:"Display 2 — 3840×2160",primary:false,current:false,width:3840,height:2160}],
  moveToDisplay:async id=>{(window.__moves=window.__moves||[]).push(id);return true},
  backupSettings:async()=>({ok:true,file:"C:\\Users\\you\\Documents\\Boxshelf settings.json"}),
  restoreSettings:async()=>{config=Object.assign(JSON.parse(JSON.stringify(config)),{ui:Object.assign({},config.ui,{theme:"aurora"})});(apiHooks.lib||[]).forEach(f=>f({restored:1}));(apiHooks.hist||[]).forEach(f=>f({restored:1}));return {ok:true}},
  reportProblem:async()=>({ok:true,file:"C:\\Users\\you\\Desktop\\Boxshelf problem report.zip"}),
  // (1.82) the beta page: a newer beta is out (tests check the Settings rows and the shelf's notice)
  updateCheck:async()=>({current:"1.82.0-beta.1",latest:"1.82.0-beta.2",newer:true,url:"https://example.com/releases/beta.2",notes:"## What's new\n- **Video previews** in Details\n- A new [Details](https://example.com) page\n- <b>Ten</b> more consoles",links:{page:"https://example.com/",releases:"https://example.com/releases",issues:"https://example.com/issues/new/choose"},checkedAt:Date.now()}),
  onUpdate:cb=>{if(/[?&]update=1/.test(location.search))setTimeout(()=>cb({current:"1.82.0-beta.1",latest:"1.82.0-beta.2",newer:true,url:"https://example.com/releases/beta.2",notes:"## What's new\n- **Video previews** in Details\n- A new [Details](https://example.com) page\n- <b>Ten</b> more consoles\n- Fourth item"}),4000)}, // only on a test page that asks for it
  clearHistory:async()=>{(apiHooks.hist||[]).forEach(f=>f({cleared:1}));return {ok:true}},
  onHistoryChanged:fn=>{(apiHooks.hist=apiHooks.hist||[]).push(fn)},
  onLibraryChanged:fn=>{(apiHooks.lib=apiHooks.lib||[]).push(fn)},
  apiStatus:async()=>({ra:config.ra&&config.ra.user&&config.ra.key?{state:"ok",at:Date.now(),msg:"Connected",user:config.ra.user,points:1234}:{state:"none",at:0,msg:"Not set up"},fanart:config.keys&&config.keys.fanart?{state:"fail",at:Date.now(),msg:"not accepted"}:{state:"none",at:0,msg:"Not set up"},steamgriddb:config.keys&&config.keys.steamgriddb?{state:"ok",at:Date.now(),msg:"Key works"}:{state:"none",at:0,msg:"Not set up"},
    // ScreenScraper (1.71): an account with no developer login answers "developer login not accepted" (window.__ssNoDev)
    screenscraper:config.keys&&config.keys.ssUser&&config.keys.ssPass?(window.__ssNoDev&&!config.keys.ssDevId?{state:"fail",at:Date.now(),msg:"developer login not accepted"}:{state:"ok",at:Date.now(),msg:"Working",left:19876}):{state:"none",at:0,msg:"Not set up"}}),
  clipboardText:async()=>String(window.__bsClip||"").split(/\r?\n/)[0].trim(), // (beta.9: tests set window.__bsClip)
  testApi:async s=>{await wait(300);const st=await window.boxshelf.apiStatus();(apiHooks.api||[]).forEach(f=>f(st));return st[s]},
  onApiStatus:fn=>{(apiHooks.api=apiHooks.api||[]).push(fn)},
  raEmulators:async()=>[{system:"gc",emulator:"Dolphin",state:"ok",user:"paul"},{system:"wii",emulator:"Dolphin",state:"off",user:"paul",detail:"Achievements are turned off in Dolphin"},{system:"psp",emulator:"PPSSPP",state:"signed-out"},{system:"ps2",emulator:"PCSX2",state:"no-emulator"}],
  // the rest of preload.js, as neutral fakes (empty lists, no account, nothing opened) so every screen runs here the way it
  // does with nothing set up: a call the renderer makes must never be a TypeError in the preview (test scenarios patch these)
  log:()=>{},uiBusy:()=>{},homeMeta:()=>{},shelfInfo:()=>{},openShelfMenu:()=>{},shelfMenuKey:()=>{},
  onLaunchStatus:()=>{},onRunReady:()=>{},onRunQuitting:()=>{},onRunAside:()=>{},onRaUnlocks:()=>{},onTvFound:()=>{},onShelfMenu:()=>{},onShelfMenuRequest:()=>{},onUiScale:()=>{},onArchiveProgress:()=>{},
  cachedLibrary:async()=>null,scanIfChanged:async()=>window.boxshelf.scan(),
  raEnabled:async()=>({enabled:/[?&]ra=1/.test(location.search),systems:["gc","wii","psp","ps2","snes","n64","gba","psx","ps1"]}),raProfile:async()=>null,raShelf:async()=>({}),
  /* (beta.12, ?ra=1) a pretend set for the achievements screen: 40 of them, 7 unlocked (one hardcore), story steps, a win condition, missables, every rarity */
  raProgress:async(sys,title)=>{if(!/[?&]ra=1/.test(location.search))return null;
    const ic=(h,k)=>"data:image/svg+xml,"+encodeURIComponent(`<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 64 64"><rect width="64" height="64" rx="10" fill="hsl(${h},55%,38%)"/><circle cx="32" cy="32" r="17" fill="hsl(${h},70%,62%)"/><text x="32" y="39" font-size="20" font-family="Arial" font-weight="700" text-anchor="middle" fill="#fff">${k}</text></svg>`);
    const R=[95,61,42,22,14,36,26,10,3,39,22,2,9,1,42,29,8,23,6,4],W=["Pay the first loan","Open the museum","Donate 10 fish","Catch every bug","Find a fossil","Fill the catalog","Visit the island","Earn a million"];
    const achievements=Array.from({length:40},(_,i)=>({id:9000+i,title:i===3?"A Very Long Achievement Name That Used To Be Cut Off":`${W[i%W.length]} ${Math.floor(i/W.length)+1}`,desc:i===3?"A longer description, to show it's never cut off in the panel: register every piece of furniture, wallpaper and carpet in the catalog.":`Do the thing number ${i+1}`,
      points:[1,5,10,25,2,3,50,100][i%8],type:i<5?"progression":i===5?"win_condition":i===12||i===18||i===27?"missable":"",badge:ic((i*37)%360,String(i+1)),earned:i<7&&i!==4,hardcore:i===1,date:i<7&&i!==4?`2026-11-0${i+1} 20:1${i}:00`:null,rarity:R[i%R.length]}));
    const got=achievements.filter(a=>a.earned);
    return {id:1,title,console:"Test",icon:ic(200,"★"),box:null,total:40,earned:got.length,points:achievements.reduce((s,a)=>s+a.points,0),pointsEarned:got.reduce((s,a)=>s+a.points,0),completion:null,completionHardcore:null,mastered:null,achievements,url:"https://retroachievements.org/game/1"}},
  
  openExternal:async()=>true,openSongsFolder:async()=>true,openLibraryFolder:async()=>true,openAppFolder:async()=>true,restoreEmuSettings:async()=>[],
  // (beta.7) Set up for me: a pretend state per emulator (RetroArch, Dolphin, DuckStation here)
  ...(()=>{const done={},coreGot={},kind=id=>{const sc=config.systems[id];if(!sc||!sc.emulator)return null;const n=sc.emulator.split("\\").pop();return /retroarch/i.test(n)?"RetroArch":/dolphin/i.test(n)?"Dolphin":/duckstation/i.test(n)?"DuckStation":"other"};
    const G={RetroArch:["quit","hotkeys","controller","bios","resume"],Dolphin:["controller","graphics"],DuckStation:["quit","hotkeys","controller","bios","graphics"]};
    return {emuSetupStatus:async id=>{const k=kind(id);if(!k)return {supported:false,none:true};if(k==="other")return {supported:false};return {supported:true,kind:k.toLowerCase(),name:k,ready:true,used:k==="Dolphin",done:done[k]||null,groups:G[k],skipped:k==="Dolphin"?[]:[{group:"bios",why:"its BIOS folder already has files"}].slice(0,k==="DuckStation"?1:0),preset:"balanced",
        core:k==="RetroArch"?{id:id+"_core",name:{snes:"Snes9x",gba:"mGBA",megadrive:"Genesis Plus GX",dreamcast:"Flycast"}[id]||"Core",installed:!!coreGot[id]}:null,
        bios:id==="gba"?{need:"opt",note:"games run without it",ok:true,files:[{name:"gba_bios.bin",region:"",state:"missing"}],dir:root+"\\RetroArch-Win64\\system"}:id==="psx"?{need:"any",ok:true,files:[],dir:root+"\\DuckStation\\bios"}:null}},
      getCore:async id=>{await wait(700);coreGot[id]=true;return {ok:true,name:"Core"}},openBiosFolder:async()=>root+"\\BIOS",
      emuSetupPlan:async id=>{const k=kind(id);return {ok:true,changes:(G[k]||[]).map(g=>({group:g,file:root+"\\"+k+"\\settings.ini",key:"["+g+"] Example",value:"on"})),skipped:[]}},
      emuSetupApply:async(id,on)=>{const k=kind(id);await wait(300);const a=(G[k]||[]).filter(g=>on?on[g]:g!=="resume");done[k]={at:Date.now(),groups:a};return {ok:true,applied:a,skipped:[]}},
      emuSetupUndo:async id=>{const k=kind(id),had=!!done[k];delete done[k];return {ok:true,restored:had?["settings.ini"]:[]}},
      emuSetupAuto:async()=>{await wait(400);const names=[...new Set(Object.keys(config.systems).filter(id=>config.systems[id].enabled!==false).map(kind).filter(k=>k&&k!=="other"))];names.filter(k=>k!=="Dolphin").forEach(k=>{done[k]={at:Date.now(),groups:G[k].filter(g=>g!=="resume")}});
        return {done:names.filter(k=>k!=="Dolphin"),later:[],left:names.filter(k=>k==="Dolphin"),emuSetup:{auto:true,graphics:"auto",resume:false,pending:[]}}}}})(),
  getAutostart:async()=>({enabled:false}),setAutostart:async v=>({enabled:!!v,how:"shortcut"}),
  songs:async()=>[],songRead:async()=>{throw new Error("Preview mode")},
  library:async()=>/[?&]mags=1/.test(location.search)?[1,2,3].map(n=>({id:"np"+n,kind:"magazine",type:"pdf",title:`Nintendo Power Issue ${n} (198${7+n})`,series:"Nintendo Power"})).concat([{id:"gi1",kind:"magazine",type:"pdf",title:"Game Informer Issue 1 (1991)",series:"Game Informer"}]):[] /* (beta.11 ?mags=1: a file box of three issues and one on its own) */,libraryCover:async()=>null,libraryPages:async()=>[],stremioLibrary:async()=>[],stremioPoster:async()=>null,stremioDetails:async()=>null,
  tvScan:async()=>true,tvList:async()=>[],tvAdFor:async()=>null,tvLocal:async()=>null,
  manualCoverage:async()=>consoles.filter(c=>config.systems[c.id]&&config.systems[c.id].romDirs.length).map(c=>({id:c.id,name:c.name,total:titles[c.id].length,have:0,low:0})),
  artCoverage:async()=>consoles.filter(c=>config.systems[c.id]&&config.systems[c.id].romDirs.length).map(c=>({id:c.id,name:c.name,total:titles[c.id].length,front:0,back:0,spine:0,disc:0,frontLow:0,backLow:0,spineLow:0,discLow:0})),
  recentManuals:async()=>[],cancelManuals:async()=>true,cancelCovers:async()=>true,pauseCovers:async()=>({ok:true}),resumeCovers:async()=>({ok:true}),/* (beta.10: the shelf's art card; window.BS_artCard feeds it in tests) */onGpuBack:fn=>{(window.__mockGpu=window.__mockGpu||[]).push(fn)},/* (beta.10: tests call these as main's "gpu:back") */// (beta.9) Wrong game / Repair as main does them: the "waiting" / "go" steps, Wrong game's pages arriving one by one
  onManualTurn:fn=>{(window.__mockTurn=window.__mockTurn||[]).push(fn)},
  // (beta.9) Check my manuals as main does it: a step per console, then the set-aside games looked up again (Cancel stops it)
  onManualCheck:fn=>{(window.__mockCheck=window.__mockCheck||[]).push(fn)},
  cancelCheckManuals:async()=>{window.__mockCheckStop=true;return true},
  checkManuals:async()=>{const F=window.__mockCheck||[],say=d=>F.forEach(f=>f(d)),S=["GameCube","Wii","Switch","PSP"];window.__mockCheckStop=false;
    for(let i=0;i<S.length;i++){say({stage:"Checking manuals · "+S[i],done:i+1,total:S.length,checked:i*4,moved:i>1?2:0});await wait(350);if(window.__mockCheckStop)return {checked:i*4,moved:i>1?2:0,shrunk:0,got:0,cancelled:true}}
    say({stage:"Looking up the set-aside games…",checked:12,moved:2});await wait(400);return {checked:12,moved:2,shrunk:1,got:2,cancelled:false}},
  rejectManual:async()=>{const T=window.__mockTurn||[],C=window.__mockCov||[];T.forEach(f=>f({wait:true}));await wait(300);T.forEach(f=>f({go:true}));for(let i=1;i<=3;i++){await wait(200);C.forEach(f=>f({stage:`Aether Drift: page ${i} of 3`}))}return {got:3,pages:window.__mockManual||[]}},
  repairManual:async()=>{const T=window.__mockTurn||[];T.forEach(f=>f({go:true}));await wait(500);return {changed:true,what:"the box front added as its cover",pages:window.__mockManual||[]}},saveOptimizedModel:async()=>true,
  archiveSearch:async(t,k,page,plain)=>{await wait(250);const p=page||1,n=p<3?36:8;return {page:p,total:80,rows:36,plain:!!plain,items:Array.from({length:n},(_,i)=>({id:/\bbig\b/i.test(t)&&i===0?`${k==="guide"?"g":"m"}${p}_big`:`${k==="guide"?"g":"m"}${p}_${i}`,title:`${String(t||"Magazine").replace(/\b\w/g,c=>c.toUpperCase())} ${k==="guide"?"Official Guide Vol.":"Issue"} ${(p-1)*36+i+1}`,date:`199${i%10}-0${1+i%9}`,pages:96+i,downloads:999-i,thumb:"",page:"https://archive.org/details/x"}))}},
  archiveCheck:async ids=>{await wait(150);return Object.fromEntries((ids||[]).map((id,i)=>[id,/_3$|_7$/.test(id)?{ok:false,why:"borrow-only"}:/_big$/.test(id)?{ok:true,type:"file",size:7.9e8,files:1,pages:124} /* (beta.11: a 750 MB one, for the size warning) */:{ok:true,type:i%3?"file":"pages",size:i%3?4.2e7:0,files:1,pages:120}]))},
  archiveDownload:(id,kind,title)=>new Promise((res,rej)=>{mockDl[id]={rej,t:setTimeout(()=>{delete mockDl[id];res({existed:false,name:`${title}.pdf`,count:0})},4000)}}),
  archiveCancel:async id=>{const j=mockDl[id];if(!j)return false;clearTimeout(j.t);delete mockDl[id];setTimeout(()=>j.rej(new Error("Cancelled.")),50);return true},
  readingWhere:async()=>({root,custom:false,magazines:root+"\\Magazines",guides:root+"\\Guides"}),
  readingOld:async()=>[{root:"C:\\Users\\you\\Desktop\\Old Boxshelf",why:"log",magazines:{files:12,bytes:9.6e8},guides:{files:3,bytes:2.1e8},files:15,bytes:1.17e9}],
  readingImport:async()=>{await wait(600);return {copied:15,skipped:0,failed:0,bytes:1.17e9,total:15}},readingImportStop:async()=>true,onReadingImport:()=>{}
};
artStudioMock(window.boxshelf,{consoles,titles,config:()=>config,root,folder,ext,wait});
manualStudioMock(window.boxshelf,{consoles,titles,config:()=>config,root,folder,ext,wait});
})();

// ---------- Art Studio (1.67) fake: the whole art:* contract (scratchpad/studio/SPEC.md) ----------
// Every picture is a generated SVG (covers with reprint / special banners, JPN prints, blurry low-res scans, backs,
// spines, discs). Candidate lists come back at once with thumb:null; the thumbnails then arrive one by one through
// onArtCand (150–1600 ms later), as the real backend's background downloads do. Shelf-style changes run a job that
// reports through onArtJob and can be cancelled; Undo walks back a per-part history.
function artStudioMock(api,o){
const PARTS=["front","back","spine","disc"],TAG={gc:"NINTENDO GAMECUBE",wii:"Wii",wiiu:"Wii U",sw:"NINTENDO SWITCH",psp:"PSP",ps3:"PS3",n3ds:"NINTENDO 3DS"};
const hash=s=>{let h=2166136261;for(const c of String(s)){h^=c.charCodeAt(0);h=Math.imul(h,16777619)}return h>>>0};
const xe=s=>String(s).replace(/[&<>"]/g,c=>({"&":"&amp;","<":"&lt;",">":"&gt;",'"':"&quot;"}[c]));
const url=s=>"data:image/svg+xml;charset=utf-8,"+encodeURIComponent(s);
const F='font-family="Arial,Helvetica,sans-serif"';
function wrap(t,n){const L=[];let l="";for(const x of String(t).split(" ")){if((l+" "+x).trim().length>n&&l){L.push(l);l=x}else l=(l+" "+x).trim()}if(l)L.push(l);return L.slice(0,3)}
// one look of a game's picture: o = {ed:'original'|'reprint'|'special', reg:'usa'|'eur'|'jpn', low, hd, scan}
function cover(sys,title,c){const h=hash(title)%360,W=280,Hh=400,jp=c.reg==="jpn",ink=jp?"#16171d":"#fff";
  let s=`<svg xmlns="http://www.w3.org/2000/svg" width="${W}" height="${Hh}" viewBox="0 0 ${W} ${Hh}"><defs><linearGradient id="g" x1="0" y1="0" x2=".6" y2="1"><stop offset="0" stop-color="hsl(${h},${c.ed==="special"?35:58}%,${c.ed==="special"?18:32}%)"/><stop offset="1" stop-color="#05060c"/></linearGradient><radialGradient id="r" cx=".7" cy=".32" r=".62"><stop offset="0" stop-color="hsl(${(h+28)%360},88%,${c.hd?66:58}%)"/><stop offset="1" stop-color="hsl(${h},80%,50%)" stop-opacity="0"/></radialGradient><pattern id="sl" width="4" height="3" patternUnits="userSpaceOnUse"><rect width="4" height="1" fill="#fff" opacity=".08"/></pattern><filter id="b"><feGaussianBlur stdDeviation="${c.low?2.6:.5}"/></filter></defs><g ${c.low||c.scan?'filter="url(#b)"':""}>`;
  s+=jp?`<rect width="${W}" height="${Hh}" fill="hsl(${h},28%,92%)"/><circle cx="196" cy="150" r="150" fill="hsl(${h},72%,62%)" opacity=".6"/>`:`<rect width="${W}" height="${Hh}" fill="url(#g)"/><rect width="${W}" height="${Hh}" fill="url(#r)"/>`;
  if(c.ed==="special")s+=`<rect x="10" y="10" width="${W-20}" height="${Hh-20}" fill="none" stroke="#d8b13a" stroke-width="3"/>`;
  let top=16;
  if(c.ed==="reprint"){s+=`<rect width="${W}" height="46" fill="#ffd400"/><text x="14" y="30" ${F} font-weight="800" font-size="18" fill="#111">${sys==="ps3"||sys==="psp"?"GREATEST HITS":sys==="wii"?"NINTENDO SELECTS":"PLAYER'S CHOICE"}</text>`;top=58}
  if(c.ed==="special"){s+=`<rect width="${W}" height="46" fill="#c9a227"/><text x="14" y="30" ${F} font-weight="800" font-size="17" fill="#111">COLLECTOR'S EDITION</text>`;top=58}
  if(c.reg==="eur"){s+=`<rect x="${W-58}" y="${Hh-74}" width="44" height="56" rx="4" fill="#fff"/><text x="${W-36}" y="${Hh-39}" text-anchor="middle" ${F} font-weight="800" font-size="20" fill="#111">12</text>`}
  s+=`<text x="16" y="${top+14}" ${F} font-weight="700" font-size="12" letter-spacing="1.6" fill="${ink}" opacity=".85">${xe((c.reg==="eur"?"PAL · ":"")+(TAG[sys]||sys))}</text>`;
  const lines=wrap(jp?"ゲーム "+title.split(" ")[0]:title,jp?9:12),fs=jp?30:35;
  s+=lines.map((l,i)=>`<text x="16" y="${Hh-(c.reg==="eur"?84:26)-(lines.length-1-i)*fs*1.06}" ${F} font-weight="800" font-size="${fs}" fill="${ink}">${xe(l)}</text>`).join("");
  if(c.scan)s+=`<rect width="${W}" height="${Hh}" fill="url(#sl)"/>`;
  return url(s+"</g></svg>")}
function back(sys,title,c){const h=hash(title)%360;let s=`<svg xmlns="http://www.w3.org/2000/svg" width="280" height="400" viewBox="0 0 280 400"><defs><filter id="b"><feGaussianBlur stdDeviation="${c.low?2.2:.4}"/></filter></defs><g filter="url(#b)"><rect width="280" height="400" fill="hsl(${h},${c.reg==="jpn"?20:40}%,${c.reg==="jpn"?88:14}%)"/>`;
  const ink=c.reg==="jpn"?"#222":"#fff";s+=`<text x="16" y="34" ${F} font-weight="800" font-size="20" fill="${ink}">${xe(title)}</text>`;
  for(let i=0;i<3;i++)s+=`<rect x="${16+i*84}" y="56" width="76" height="56" rx="3" fill="hsl(${(h+i*40)%360},60%,45%)"/>`;
  for(let i=0;i<9;i++)s+=`<rect x="16" y="${132+i*16}" width="${200-((i*37)%90)}" height="7" rx="3" fill="${ink}" opacity=".35"/>`;
  s+=`<rect x="16" y="330" width="70" height="46" fill="#fff"/>${Array.from({length:22},(_,i)=>`<rect x="${20+i*3}" y="334" width="${i%3?1:2}" height="32" fill="#000"/>`).join("")}`;
  if(c.ed==="reprint")s+=`<rect x="0" y="0" width="280" height="8" fill="#ffd400"/>`;
  return url(s+"</g></svg>")}
function spine(sys,title,c){const h=hash(title)%360,jp=c.reg==="jpn";return url(`<svg xmlns="http://www.w3.org/2000/svg" width="40" height="400" viewBox="0 0 40 400"><rect width="40" height="400" fill="${c.ed==="reprint"?"#ffd400":jp?"#efeeea":`hsl(${h},50%,${c.hd?30:24}%)`}"/><rect width="40" height="56" fill="${jp?"#d8202f":"#111"}"/><text transform="translate(26 74) rotate(90)" ${F} font-weight="800" font-size="17" fill="${c.ed==="reprint"||jp?"#111":"#fff"}">${xe(title.toUpperCase())}</text></svg>`)}
function disc(sys,title,c){const h=hash(title)%360,fill=c.ed==="reprint"?"#ffd400":c.reg==="jpn"?"#ece8dc":`hsl(${h},60%,${c.hd?40:32}%)`,ink=c.ed==="reprint"||c.reg==="jpn"?"#111":"#fff";
  return url(`<svg xmlns="http://www.w3.org/2000/svg" width="300" height="300" viewBox="0 0 300 300"><defs><radialGradient id="d"><stop offset=".3" stop-color="${fill}"/><stop offset="1" stop-color="#0d1020"/></radialGradient></defs><circle cx="150" cy="150" r="148" fill="url(#d)"/><text x="150" y="86" text-anchor="middle" ${F} font-weight="800" font-size="22" fill="${ink}">${xe(c.reg==="jpn"?"ゲーム "+title.split(" ")[0]:title.toUpperCase().slice(0,18))}</text><circle cx="150" cy="150" r="40" fill="#dcdfe8" opacity=".9"/><circle cx="150" cy="150" r="22" fill="#0b0d16"/></svg>`)}
const DRAW={front:cover,back,spine,disc};
const SRC={launchbox:"LaunchBox",libretro:"libretro",gametdb:"GameTDB",steam:"Steam",steamgriddb:"SteamGridDB",screenscraper:"ScreenScraper",current:"In use",earlier:"Earlier"};
const RG={usa:"USA",eur:"EUR",jpn:"JPN",world:"World"};
const TDB=new Set(["gc","wii","wiiu"]);
// every version Boxshelf "found" for one game + part; deterministic per game, so lists are stable between visits
function versions(sys,g,part){const n=hash(g.title+part),L=[],add=(key,source,reg,ed,kind,w,h,x={})=>L.push({key,source,region:reg,edition:ed,kind,w,h,...x});
  if(g.noCands&&g.noCands.includes(part)&&!(part==="disc"&&indexBuilt))return L;
  if(g.only){add("cur-"+part,"current",g.region,"original","scan",600,860);return L}
  const jpOnly=g.region==="jpn";
  if(part==="front"){
    if(!jpOnly)add("lb-usa","launchbox","usa","original","scan",1548,2160);
    add("lr-"+g.region,"libretro",g.region,"original","clean",512,720);
    if(!jpOnly&&n%3!==0)add("lb-pc","launchbox","usa","reprint","scan",1500,2100);
    if(TDB.has(sys))add("tdb-hq","gametdb","usa","original","hd",2400,3360);
    if(n%2===0)add("tdb-eur",TDB.has(sys)?"gametdb":"launchbox","eur","original",TDB.has(sys)?"hd":"scan",1600,2240);
    add("lb-jpn","launchbox","jpn","original","scan",1400,1960);
    if(n%4===1)add("lb-ce","launchbox","usa","special","scan",1700,2380);
    if(n%5===2)add("lr-pc","libretro","usa","reprint","clean",480,680);
    // SteamGridDB community posters (main: only with a key in Accounts & services; no region, never an authentic pick)
    add("sg-1","steamgriddb","","original","hd",600,900,{label:"SteamGridDB · Alternate · by pixelsmith"});
    add("sg-2","steamgriddb","","original","hd",660,930,{label:"SteamGridDB · No logo · by Kirbo"});
    if(n%2===0)add("sg-3","steamgriddb","","original","clean",342,482,{label:"SteamGridDB · White logo · by retro_rat"});
    add("ss-f-us","screenscraper","usa","original","scan",1100,1540,{label:"Box front · USA"});if(n%2===0)add("ss-f-eu","screenscraper","eur","original","scan",1000,1400,{label:"Box front · Europe"}); // ScreenScraper (1.71)
  }else if(part==="back"){
    if(TDB.has(sys))add("tdb-full-b","gametdb","usa","original","hd",1000,1400,{label:"From GameTDB's full cover · USA"}); // the back half of GameTDB's full cover (1.71)
    add("ss-b-us","screenscraper","usa","original","scan",1100,1540,{label:"Box back · USA"});
    if(!jpOnly)add("lb-usa-b","launchbox","usa","original","scan",1520,2140);
    add("lb-rec-b","launchbox",jpOnly?"jpn":"usa","original","clean",1200,1700);
    if(n%2===0)add("lb-eur-b","launchbox","eur","original","scan",900,1270);
    if(n%3===1)add("lb-pc-b","launchbox","usa","reprint","scan",1400,1980);
  }else if(part==="spine"){
    add("lb-full-s","launchbox",jpOnly?"jpn":"usa","original","scan",120,1400,{label:"From the full box scan"});
    if(TDB.has(sys))add("tdb-full-s","gametdb","usa","original","hd",180,2100,{label:"From GameTDB's full cover · HQ"});
    if(n%2===1)add("lb-jpn-s","launchbox","jpn","original","scan",110,1300);
    add("ss-s-wrap","screenscraper","usa","original","scan",130,1500,{label:"Cut from full wrap · USA"});
  }else{
    if(!jpOnly)add("lb-usa-d","launchbox","usa","original","scan",1000,1000);
    if(TDB.has(sys))add("tdb-eur-d","gametdb","eur","original","hd",1024,1024);
    add("lb-jpn-d","launchbox","jpn","original","scan",900,900);
    if(n%3===0&&!jpOnly)add("lb-pc-d","launchbox","usa","reprint","scan",1000,1000);
    add("ss-d-us","screenscraper","usa","original","scan",800,800,{label:(sys==="n3ds"||sys==="sw"?"Cartridge":"Disc")+" · USA"});
  }
  if(g.hist[part]&&g.hist[part].length){const k=g.hist[part][g.hist[part].length-1];const prev=L.find(x=>x.key===k);if(prev)L.push({...prev,key:"earlier-"+part,source:"earlier"})}
  return L}
const EDN={original:"Original release",reprint:"Reprint",special:"Collector's edition"};
function label(c){if(c.label)return c.label;const ed=c.edition==="reprint"?(c.key.includes("pc")?"Player's Choice":"Reprint"):EDN[c.edition];return `${c.source==="earlier"?"Earlier · ":""}${ed} · ${RG[c.region]||"World"}`}
function scoreOf(c,style){const px=(c.w||0)*(c.h||0),hi=Math.min(1,px/(2400*3360)),reg=style.region||"usa";
  const a=(c.kind==="scan"?60:c.kind==="hd"?42:30)+(c.edition==="original"?30:c.edition==="special"?10:-25)+(c.region===reg?10:0)+hi*8;
  const hd=hi*90+(c.kind==="hd"?10:0)-(c.edition==="reprint"?8:0);
  const rg=(c.region===reg?70:0)+(c.edition==="original"?18:0)+hi*10;
  const sp=(c.edition==="special"?80:c.edition==="original"?40:0)+hi*10+(c.region===reg?8:0);
  const sg=c.source==="steamgriddb"?100:0; // community posters: HD may pick one on its size, the release looks never do
  return {authentic:Math.round(a-sg),hd:Math.round(hd),region:Math.round(rg-sg),special:Math.round(sp-sg)}}
const cfg=o.config,games={},styles={},jobs={},candL=[],jobL=[],thumbs={},idxL=[];let indexBuilt=false,refr=null;
const look2score=l=>l==="hd"?"hd":l&&l.startsWith("region")?"region":l==="special"?"special":"authentic";
function lookFor(style,part){return style.preset==="mix"?(style.parts&&style.parts[part])||"authentic":style.preset==="region"?"region:"+(style.region||"usa"):style.preset}
o.consoles.forEach(c=>{styles[c.id]={preset:"authentic",region:"usa",parts:{}};
  // window.__artBig = n: n extra GameCube games, to test the big grid (only the rows in view are drawn)
  games[c.id]=(o.titles[c.id]||[]).concat(c.id==="gc"&&window.__artBig?Array.from({length:+window.__artBig},(_,k)=>"Extra Game "+(k+1)):[]).map((t,i)=>{const n=hash(t),g={title:t,i,region:n%9===4?"jpn":n%7===3?"eur":"usa",hist:{front:[],back:[],spine:[],disc:[]},lock:{front:null,back:null,spine:null,disc:null},sel:{},missing:{}};
    if(c.id==="ps3"&&i===3)g.only=true; // one source only (like a Steam / PS4 game)
    if(c.id==="gc"&&i===6)g.noCands=["back","disc"]; // nothing found anywhere
    if(n%6===1)g.missing.back=true;if(n%5===3)g.missing.disc=true;if(n%8===5)g.missing.spine=true;
    PARTS.forEach(p=>{const L=versions(c.id,g,p);if(!L.length||g.missing[p]){g.sel[p]=null;return}
      // what's on disk now: usually the best authentic match; a few games came with a low-res libretro front
      g.sel[p]=(p==="front"&&(i%5===2||n%4===2)?L.find(x=>x.source==="libretro"):null)||L.slice().sort((a,b)=>scoreOf(b,styles[c.id]).authentic-scoreOf(a,styles[c.id]).authentic)[0];g.sel[p]=g.sel[p].key});
    if(c.id==="gc"&&(i===0||i===3)){g.lock.front={look:"hd"};const L=versions(c.id,g,"front"),hd=L.find(x=>x.kind==="hd");if(hd)g.sel.front=hd.key}
    if(c.id==="wii"&&i===1){g.lock.disc={look:"region:jpn"};g.sel.disc="lb-jpn-d"}
    if(c.id==="gc"&&i===4)g.frozen=true; // a game locked as it is (1.68)
    // pictures from before 1.68 that Boxshelf can't vouch for: kept (locked) at the console's first style change
    g.legacy=PARTS.filter(p=>g.sel[p]&&(hash(t+p)%5===1));
    return g})});
const keptOnce={};
const fileOf=(sys,g)=>{const s=cfg().systems[sys],d=s&&s.romDirs&&s.romDirs[0]||o.root+"\\ROMS\\"+o.folder[sys];return `${d}\\${g.title} (USA).${o.ext[sys]}`};
const find=(sys,file)=>{const L=games[sys];if(!L)throw new Error("Unknown console");const g=L.find(x=>fileOf(sys,x).toLowerCase()===String(file).toLowerCase());if(!g)throw new Error("That game isn't on the shelf");return g};
// "own-<part>": a picture of the user's own (art:own), shown like a big clean scan
const candOf=(sys,g,p,key)=>String(key).startsWith("own-")?{key,source:"current",region:"",edition:"original",kind:"scan",w:1200,h:1700}:versions(sys,g,p).find(x=>x.key===key);
const pic=(sys,g,p,c)=>c?DRAW[p](sys,g.title,{ed:c.edition,reg:c.region,low:Math.min(c.w||999,c.h||999)<(p==="spine"?100:700),hd:c.kind==="hd",scan:c.kind==="scan"}):null;
function artOf(sys,g){const a={},low={};PARTS.forEach(p=>{const c=g.sel[p]&&candOf(sys,g,p,g.sel[p]);a[p]=pic(sys,g,p,c);low[p]=!!(c&&Math.min(c.w,c.h)<(p==="spine"?100:700))});return {art:a,low}}
const delay=()=>o.wait(250+Math.random()*350);
const emitC=e=>candL.forEach(f=>{try{f(e)}catch(x){console.error(x)}}),emitJ=e=>window.__artMuteJobs?0:jobL.forEach(f=>{try{f(e)}catch(x){console.error(x)}});
let jobN=0,candGen=0;
// job events carry systemId (CONTRACT-CHANGES 15); a newer style for the same console first announces itself with run /
// total 0, then the job it replaces ends "cancelled"
const jobEv=j=>{const {sys,...ev}=j;return {...ev,systemId:sys}};
function runJob(sys,label,kept){const id="job"+(++jobN),L=games[sys].filter(g=>!g.frozen),j={id,label,done:0,total:L.length,state:"run",msg:"",sys,kept:kept||0};jobs[id]=j;
  const old=Object.values(jobs).filter(x=>x!==j&&x.sys===sys&&x.state==="run");
  if(old.length)setTimeout(()=>{emitJ({...jobEv(j),total:0});old.forEach(x=>{x.state="cancelled";emitJ(jobEv(x))})},0);
  const step=()=>{if(j.state!=="run")return;const g=L[j.done];if(g)PARTS.forEach(p=>{g.lock[p]=null;if(!g.sel[p])return;const vs=versions(sys,g,p),lk=lookFor(styles[sys],p),sk=look2score(lk);const best=vs.slice().sort((a,b)=>scoreOf(b,styles[sys])[sk]-scoreOf(a,styles[sys])[sk])[0];if(best&&best.key!==g.sel[p]){g.hist[p].push(g.sel[p]);g.sel[p]=best.key}});
    j.done++;if(j.done>=j.total){j.state="done";j.msg=styles[sys].preset==="sets"?(()=>{const all=games[sys],sk=all.filter(g=>g.frozen).length,full=all.filter(g=>!g.frozen&&mockSets(sys,g).some(x=>styles[sys].setsHd?x.hd:x.region===styles[sys].region)).length;return `${full} full sets · ${all.length-sk-full} partly · ${sk} skipped (locked)`})():`${j.total} game${j.total===1?"":"s"} checked`+(j.kept?` · ${j.kept} kept as they were`:"")}emitJ(jobEv(j));if(j.state==="run")setTimeout(step,window.__artJobMs||140)};
  setTimeout(step,200);return id}
// Matching sets (1.72): per region + source family, all four parts from one source; plus the HD set
const REGN={usa:"USA",eur:"Europe",jpn:"Japan"},FAM={launchbox:"LaunchBox scans",gametdb:"GameTDB",screenscraper:"ScreenScraper scans"};
function mockSets(sys,g){const out=[],V=Object.fromEntries(PARTS.map(p=>[p,versions(sys,g,p).filter(c=>!["current","earlier","steamgriddb"].includes(c.source))]));
  for(const R of ["usa","eur","jpn"])for(const f of Object.keys(FAM)){const parts={};for(const p of PARTS){const c=V[p].find(c=>c.source===f&&(c.region===R||(p==="disc"&&!c.region)));if(!c)break;parts[p]=c}if(PARTS.every(p=>parts[p]))out.push({id:R+"-"+f,label:`${REGN[R]} · ${FAM[f]} · complete`,region:R,source:f,hd:false,same:f==="gametdb",parts})}
  const hd={};for(const p of PARTS){const c=V[p].slice().sort((a,b)=>Math.max(b.w,b.h)-Math.max(a.w,a.h))[0];if(c)hd[p]=c}if(PARTS.every(p=>hd[p]))out.push({id:"hd",label:"HD · complete",region:"",source:"mixed",hd:true,same:false,parts:hd});return out}
const lastSetM={};
Object.assign(api,{
  artSets:async({systemId,file})=>{await o.wait(window.__artSlow||300);const g=find(systemId,file);if(window.__artNoSets)return {sets:[]};
    return {sets:mockSets(systemId,g).map(x=>({...x,parts:Object.fromEntries(PARTS.map(p=>[p,{key:x.parts[p].key,source:x.parts[p].source,region:x.parts[p].region,thumb:pic(systemId,g,p,x.parts[p]),w:x.parts[p].w,h:x.parts[p].h}]))}))}},
  artUseSet:async({systemId,file,setId})=>{await delay();const g=find(systemId,file),x=mockSets(systemId,g).find(s=>s.id===setId);if(!x)throw new Error("That set is no longer offered. Open the sets again.");
    lastSetM[systemId+file]={sel:{...g.sel},lock:JSON.parse(JSON.stringify(g.lock))};PARTS.forEach(p=>{if(g.sel[p]&&g.sel[p]!==x.parts[p].key)g.hist[p].push(g.sel[p]);g.sel[p]=x.parts[p].key;g.lock[p]={look:"exact",key:x.parts[p].key}});
    return {art:artOf(systemId,g).art,lock:JSON.parse(JSON.stringify(g.lock)),changed:PARTS}},
  artUndoSet:async({systemId,file})=>{await delay();const g=find(systemId,file),m=lastSetM[systemId+file];if(!m)throw new Error("Nothing to undo");g.sel=m.sel;g.lock=m.lock;delete lastSetM[systemId+file];return {art:artOf(systemId,g).art,lock:JSON.parse(JSON.stringify(g.lock))}},
  artStudio:async()=>{await o.wait(120);return {consoles:o.consoles.filter(c=>games[c.id]&&games[c.id].length).map(c=>({id:c.id,name:c.name,total:games[c.id].length,noCover:games[c.id].filter(g=>!artOf(c.id,g).art.front).length,style:JSON.parse(JSON.stringify(styles[c.id])),locked:games[c.id].filter(g=>g.frozen||PARTS.some(p=>g.lock[p])).length,frozen:games[c.id].filter(g=>g.frozen).length,kept:games[c.id].reduce((a,g)=>a+PARTS.filter(p=>g.lock[p]&&g.lock[p].key==="kept").length,0),job:(j=>j?{jobId:j.id,label:j.label,done:j.done,total:j.total}:null)(Object.values(jobs).filter(x=>x.sys===c.id&&x.state==="run").pop())})),looks:["shelf","authentic","hd","region:usa","region:eur","region:jpn","special"]}},
  artGames:async({systemId})=>{await o.wait(160);if(!games[systemId])throw new Error("Unknown console");return games[systemId].map(g=>{const {art,low}=artOf(systemId,g);return {file:fileOf(systemId,g),title:g.title,region:g.region,art,low,lock:JSON.parse(JSON.stringify(g.lock)),frozen:!!g.frozen}})},
  artStyle:async({systemId,style})=>{await o.wait(150);if(!games[systemId])throw new Error("Unknown console");styles[systemId]={preset:style.preset||"authentic",region:style.region||"usa",parts:Object.assign({},style.parts||{}),...(style.preset==="sets"&&style.setsHd?{setsHd:true}:{})};
    // the first style change keeps what may be the user's own pictures (main: keepUnknown); the job reports how many
    let kept=0; // (1.73: nothing is kept from a style change; only locked games are left alone)
    const name=(o.consoles.find(c=>c.id===systemId)||{}).name||systemId;return {jobId:runJob(systemId,`Re-fetching ${name}`,kept),style:JSON.parse(JSON.stringify(styles[systemId]))}},
  artCandidates:async({systemId,file,part})=>{const gen=++candGen;await o.wait(window.__artSlow||180);const g=find(systemId,file);if(!PARTS.includes(part))throw new Error("Unknown part");
    const L=versions(systemId,g,part).map(c=>{const k=systemId+"|"+file+"|"+part+"|"+c.key,t=thumbs[k];
      const sizeKnown=t||c.source==="current"||(hash(c.key+g.title)%3!==0);
      return {key:c.key,source:c.source,region:c.region,edition:c.edition,kind:c.kind,label:label(c),w:sizeKnown?c.w:null,h:sizeKnown?c.h:null,thumb:t||null,inUse:g.sel[part]===c.key||(c.source==="earlier"&&false),score:scoreOf(c,styles[systemId])}});
    // Gale Riders' disc: nothing until the LaunchBox index is built (scratchpad/studio/CONTRACT-CHANGES.md 2–3)
    const index=systemId==="gc"&&g.i===6&&part==="disc"&&!indexBuilt?"missing":"ready";
    // (beta.12) a slow source (window.__artLate, ms): one version is held back, the list says pending, and the version arrives
    // later as a whole card (late) followed by listDone, as main does when a source answers after the list went back
    let held=null;if(window.__artLate){const h=L.map((c,j)=>[c,j]).reverse().find(([c])=>!c.inUse&&c.source!=="current");if(h){held=h[0];L.splice(h[1],1)}}
    if(held)setTimeout(()=>{const v=candOf(systemId,g,part,held.key),k=systemId+"|"+file+"|"+part+"|"+held.key;if(v){thumbs[k]=thumbs[k]||pic(systemId,g,part,v);held.thumb=thumbs[k];held.w=v.w;held.h=v.h}
      emitC({systemId,file,part,key:held.key,late:true,cand:held});emitC({systemId,file,part,listDone:true})},window.__artLate);
    // the thumbnails trickle in afterwards, as downloads finish
    // offline (window.__artOffline): every download fails; an "Earlier" entry turns out to be the same picture as its original (dup)
    L.forEach(c=>{if(c.thumb)return;const k=systemId+"|"+file+"|"+part+"|"+c.key;setTimeout(()=>{const v=candOf(systemId,g,part,c.key);if(!v)return;
      if(gen!==candGen&&!window.__artNoCancel){emitC({systemId,file,part,key:c.key,failed:true,cancelled:true});return} // a newer request took over (CONTRACT-CHANGES 16)
      if(window.__artOffline){emitC({systemId,file,part,key:c.key,failed:true});return}
      if(c.source==="earlier"){const orig=L.find(z=>z.source!=="earlier"&&z.region===c.region&&z.edition===c.edition&&z.w===v.w);if(orig&&!window.__artNoDup){emitC({systemId,file,part,key:c.key,dup:orig.key});return}}
      thumbs[k]=pic(systemId,g,part,v);emitC({systemId,file,part,key:c.key,thumb:thumbs[k],w:v.w,h:v.h})},150+Math.random()*(window.__artThumbMs||1500))});
    return {list:L,index,pending:!!held}},
  artBuildIndex:async()=>{await o.wait(2500);indexBuilt=true;return {index:"ready"}},
  // Refresh art database (1.71): a fake 110 MB download, then unpacking and reading, cancellable
  artIndexInfo:async()=>({state:"ready",at:Date.now()-41*864e5,bytes:47.3*1048576}),
  artRefreshIndex:async()=>{if(refr)throw new Error("Already refreshing");const me=refr={stop:false};const T=115343360,send=p=>idxL.forEach(f=>f(p));
    try{for(let i=0;i<=100;i+=4){if(me.stop)throw new Error("Refresh cancelled");send({phase:"download",pct:i,got:Math.round(T*i/100),total:T});await o.wait(window.__refreshMs||120)}
      send({phase:"unpack"});await o.wait(400);for(let i=0;i<=100;i+=10){if(me.stop)throw new Error("Refresh cancelled");send({phase:"read",pct:i});await o.wait(80)}
      return {state:"ready",at:Date.now(),bytes:52.1*1048576}}finally{refr=null}},
  artRefreshCancel:async()=>{if(refr){refr.stop=true;return {cancelled:true}}return {cancelled:false}},
  onArtIndex:fn=>{idxL.push(fn);return ()=>{const i=idxL.indexOf(fn);if(i>=0)idxL.splice(i,1)}},
  // both return an unsubscribe function, as preload.js does (CONTRACT-CHANGES 7)
  onArtCand:fn=>{candL.push(fn);return ()=>{const i=candL.indexOf(fn);if(i>=0)candL.splice(i,1)}},onArtJob:fn=>{jobL.push(fn);return ()=>{const i=jobL.indexOf(fn);if(i>=0)jobL.splice(i,1)}},
  artUse:async({systemId,file,part,key})=>{await delay();const g=find(systemId,file),c=candOf(systemId,g,part,key);if(!c)throw new Error("That version isn't in the list any more");
    if(g.sel[part]!==key){if(g.sel[part])g.hist[part].push(g.sel[part]);g.sel[part]=key}g.lock[part]={look:"exact",key};return {art:artOf(systemId,g).art}},
  artMix:async({systemId,file,part,look})=>{await delay();const g=find(systemId,file),vs=versions(systemId,g,part);
    const lk=look==="shelf"?lookFor(styles[systemId],part):look,sk=look2score(lk),st=lk.startsWith("region:")?{...styles[systemId],region:lk.slice(7)}:styles[systemId];
    const best=vs.filter(c=>c.source!=="earlier").sort((a,b)=>scoreOf(b,st)[sk]-scoreOf(a,st)[sk])[0];
    g.lock[part]=look==="shelf"?null:{look,key:best&&best.key};if(!best)throw new Error("No version found for that look");
    if(g.sel[part]!==best.key){if(g.sel[part])g.hist[part].push(g.sel[part]);g.sel[part]=best.key}return {art:artOf(systemId,g).art,key:best.key}},
  artUndo:async({systemId,file,part})=>{await delay();const g=find(systemId,file),h=g.hist[part];if(!h.length)throw new Error("Nothing to undo");const k=h.pop();g.sel[part]=k;g.lock[part]={look:"exact"};return {art:artOf(systemId,g).art}},
  // 1.68: the whole game locked as it is; unlocking re-picks the parts that differ from the shelf style in the background
  artLock:async({systemId,file,lock})=>{await o.wait(200);const g=find(systemId,file);
    if(lock){g.frozen=true;return {frozen:true,lock:JSON.parse(JSON.stringify(g.lock)),repick:0}}
    // 1.73 (main/artstudio.js lockGame): only the game lock goes; picks stay picks; a part that followed the shelf is re-picked if it's missing
    const own=[],todo=PARTS.filter(p=>!g.lock[p]&&!g.sel[p]);
    g.frozen=false;g.legacy=[];
    let jobId=null;if(todo.length){const id="g"+(++jobN),ev={id,kind:"game",systemId,file,parts:todo,label:"Back to the shelf style",done:0,total:todo.length,state:"run",msg:""};jobId=id;
      const step=()=>{if(ev.done<todo.length){const p=todo[ev.done],vs=versions(systemId,g,p),sk=look2score(lookFor(styles[systemId],p)),best=vs.filter(c=>c.source!=="earlier").sort((a,b)=>scoreOf(b,styles[systemId])[sk]-scoreOf(a,styles[systemId])[sk])[0];
        if(best&&!g.lock[p]&&g.sel[p]!==best.key){if(g.sel[p])g.hist[p].push(g.sel[p]);g.sel[p]=best.key}ev.done++}
        if(ev.done>=todo.length){ev.state="done";ev.msg=`${todo.length} part${todo.length===1?"":"s"} re-picked`}emitJ({...ev});if(ev.state==="run")setTimeout(step,window.__artJobMs||400)};
      setTimeout(()=>{emitJ({...ev});setTimeout(step,300)},0)}
    return {frozen:false,lock:JSON.parse(JSON.stringify(g.lock)),repick:todo.length,jobId,ownKept:own}},
  // your own picture: main opens a file picker (window.__artOwnCancel: the picker is closed; __artOwnErr: a bad file)
  artOwn:async({systemId,file,part,cut})=>{await o.wait(400);const g=find(systemId,file);if(!PARTS.includes(part))throw new Error("Unknown part");
    if(window.__artOwnCancel)return {canceled:true};if(window.__artOwnErr)throw new Error(window.__artOwnErr);if(cut&&part!=="spine")throw new Error("Only a spine can be cut");
    const k="own-"+part;if(g.sel[part]&&g.sel[part]!==k)g.hist[part].push(g.sel[part]);g.sel[part]=k;g.lock[part]={look:"exact",key:"own"};return {art:artOf(systemId,g).art,lock:{look:"exact",key:"own"}}},
  // kept pictures handed to the shelf style: a restyle re-picks them
  artRelease:async({systemId})=>{await o.wait(200);if(!games[systemId])throw new Error("Unknown console");let n=0;games[systemId].forEach(g=>PARTS.forEach(p=>{if(g.lock[p]&&g.lock[p].key==="kept"){g.lock[p]=null;n++}}));
    if(!n)return {released:0,jobId:null};const name=(o.consoles.find(c=>c.id===systemId)||{}).name||systemId;return {released:n,jobId:runJob(systemId,`Restyling ${name}`)}},
  artCancel:async({jobId})=>{const j=jobs[jobId];if(j&&j.state==="run"){j.state="cancelled";emitJ(jobEv(j))}return true}
});
window.__artMock={games,styles,jobs,fileOf};
}

// ---------- Manual Studio fake: the manual:* contract (main/manualstudio.js) ----------
// Every game gets the manual on disk (most of them), an Earlier copy now and then, and 3–7 remote copies (archive.org,
// GamesDatabase, a collection scan) in English / French / German / Japanese with different page counts and sharpness.
// The list comes back with the local cards at once; remote cards arrive one by one through onManualCand (200–1800 ms),
// some never (they "couldn't be downloaded"), then a done event. Use / Undo / My PDF / lock / the console's choice work.
function manualStudioMock(api,o){
const hash=s=>{let h=2166136261;for(const c of String(s)){h^=c.charCodeAt(0);h=Math.imul(h,16777619)}return h>>>0};
const xe=s=>String(s).replace(/[&<>"]/g,c=>({"&":"&amp;","<":"&lt;",">":"&gt;",'"':"&quot;"}[c]));
const url=s=>"data:image/svg+xml;charset=utf-8,"+encodeURIComponent(s);
const LN={en:"English",fr:"French",de:"German",ja:"Japanese",es:"Spanish",it:"Italian"},RN={usa:"USA",eur:"Europe",jpn:"Japan"};
const PREFS=[{id:"best",label:"Best English manual",sub:"The sharpest complete English manual (how Boxshelf always chose)"},{id:"region:eur",label:"English · Europe",sub:"PAL booklets in English"},{id:"region:jpn",label:"Japanese",sub:"Japanese booklets"},{id:"lang:fr",label:"French",sub:"Manuals in French"},{id:"lang:de",label:"German",sub:"Manuals in German"},{id:"lang:es",label:"Spanish",sub:"Manuals in Spanish"},{id:"lang:it",label:"Italian",sub:"Manuals in Italian"}];
// a booklet's first page: the game's colour, its title, the language's word for "instruction booklet", blurred when low-res
function page(title,lang,w,v){const h=(hash(title)+v*37)%360,blur=w&&w<700?2.2:w&&w<1000?.9:0,word={en:"INSTRUCTION BOOKLET",fr:"MODE D'EMPLOI",de:"SPIELANLEITUNG",ja:"取扱説明書",es:"MANUAL DE INSTRUCCIONES",it:"ISTRUZIONI"}[lang]||"INSTRUCTION BOOKLET";
  return url(`<svg xmlns="http://www.w3.org/2000/svg" width="260" height="360" viewBox="0 0 260 360"><g ${blur?`filter="url(#b)"`:""}><defs><filter id="b"><feGaussianBlur stdDeviation="${blur}"/></filter></defs><rect width="260" height="360" fill="#f3efe4"/><rect x="0" y="0" width="260" height="120" fill="hsl(${h},55%,38%)"/><circle cx="200" cy="70" r="70" fill="hsl(${(h+40)%360},70%,60%)" opacity=".5"/><text x="18" y="160" font-family="Arial" font-weight="800" font-size="20" fill="#222">${xe(title.slice(0,22))}</text><text x="18" y="186" font-family="Arial" font-size="11" letter-spacing="1.5" fill="#555">${xe(word)}</text>${[0,1,2,3,4,5].map(i=>`<rect x="18" y="${214+i*18}" width="${150+((v+i)*29)%70}" height="6" fill="#bbb"/>`).join("")}</g></svg>`)}
const games={},hist={},prefs={},jobs={},candL=[],jobL=[],lists={};let jobN=0,gen=0;
const fileOf=(sys,t)=>{const s=o.config().systems[sys],d=s&&s.romDirs&&s.romDirs[0]||o.root+"\\ROMS\\"+o.folder[sys];return `${d}\\${t} (USA).${o.ext[sys]}`};
function gamesOf(sys){if(games[sys])return games[sys];return games[sys]=(o.titles[sys]||[]).map((t,i)=>{const n=hash(t),have=n%5!==0,pages=have?(n%7===0?2:12+n%30):0;return {title:t,file:fileOf(sys,t),locked:n%11===0&&have,cur:have?{key:"cur",source:"current",label:n%3?"Internet Archive · "+t+" manual":"The manual on disk",lang:"en",region:"usa",pages,w:n%4?1600:640,v:0}:null,earlier:n%4===1?[{key:"earlier:2026-09-01",source:"earlier",label:"Collection scan · Earlier (01/09/2026)",lang:"en",region:"usa",pages:pages||20,w:1230,v:5}]:[]}})}
const find=(sys,file)=>{const g=gamesOf(sys).find(x=>x.file===file);if(!g)throw new Error("That game is not on this shelf.");return g};
function remote(sys,g){const n=hash(g.title),base=(g.cur&&g.cur.pages>2?g.cur.pages:16+n%20),L=[
  {key:"ia:"+n+"a",source:"archive",label:"Internet Archive · "+g.title+" Instruction Booklet (USA)",lang:"en",region:"usa",pages:base,w:1600,v:1},
  {key:"gdb:"+n,source:"gamesdatabase",label:"GamesDatabase · "+g.title,lang:"en",region:"usa",pages:base,bpp:180000,v:2},
  {key:"coll:"+n,source:"collection",label:"Collection scan · "+g.title,lang:"en",region:"usa",pages:base-2,w:1230,v:3},
  {key:"ia:"+n+"f",source:"archive",label:"Internet Archive · "+g.title+" mode d'emploi",lang:"fr",region:"eur",pages:base+4,w:1100,v:4},
  {key:"ia:"+n+"d",source:"archive",label:"Internet Archive · "+g.title+" Spielanleitung (PAL)",lang:"de",region:"eur",pages:base+4,w:820,v:6},
  {key:"ia:"+n+"j",source:"archive",label:"Internet Archive · "+g.title+" 取扱説明書",lang:"ja",region:"jpn",pages:base-4,w:1500,v:7},
  {key:"ia:"+n+"x",source:"archive",label:"Internet Archive · "+g.title+" (broken upload)",lang:"en",region:"",pages:0,w:0,v:8,dead:true}];
  return L.filter((c,i)=>i<3+n%5||c.dead)}
const qual=c=>c.w?(c.w>=1400?["sharp",`Sharp · ${c.w} px`]:c.w>=900?["good",`Good · ${c.w} px`]:c.w>=600?["fair",`Fair · ${c.w} px`]:["low",`Low-res · ${c.w} px`]):c.bpp?["good",`PDF · ${Math.round(c.bpp/1024)} KB a page`]:["",""];
const fits=(c,p)=>{const en=!c.lang||c.lang==="en";if(!p||p==="best")return en;const m=/^lang:(\w\w)$/.exec(p);if(m)return c.lang===m[1];if(p==="region:eur")return en&&c.region==="eur";if(p==="region:jpn")return c.lang==="ja"||c.region==="jpn";return en};
function expectOf(L){const by={};L.forEach(c=>{if(c.pages)(by[c.lang||"en"]=by[c.lang||"en"]||[]).push(c.pages)});const out={};for(const[l,ns]of Object.entries(by)){const f={};ns.forEach(n=>f[n]=(f[n]||0)+1);const b=Object.entries(f).sort((a,b)=>b[1]-a[1]||b[0]-a[0])[0];if(b&&b[1]>=2)out[l]=+b[0]}return out}
function pub(c,ex,p){const want=(ex||{})[c.lang||"en"],k=!c.pages||!want?[null,""]:c.pages>=want?[true,"Complete"]:[false,`${want-c.pages} page${want-c.pages===1?"":"s"} missing`],q=qual(c);
  return {key:c.key,source:c.source,label:c.label,lang:c.lang,langName:LN[c.lang]||"Language unknown",region:c.region,regionName:RN[c.region]||"",pages:c.pages||null,complete:k[0],completeText:k[1],w:c.w||null,quality:q[0],qualityText:q[1],thumb:page(c.label.split(" · ").pop(),c.lang,c.w,c.v),inUse:c.key==="cur",origin:c.key==="cur"?c.origin||"":undefined,fits:fits(c,p),score:(fits(c,p)?30:0)+(c.w||0)/400}}
const emit=d=>candL.forEach(f=>{try{f(d)}catch(e){console.error(e)}}),ejob=d=>jobL.forEach(f=>{try{f(d)}catch(e){console.error(e)}});
Object.assign(api,{
  manualStudio:async()=>{await o.wait(120);return {consoles:o.consoles.filter(c=>(o.titles[c.id]||[]).length).map(c=>{const G=gamesOf(c.id);return {id:c.id,name:c.name,total:G.length,have:G.filter(g=>g.cur).length,locked:G.filter(g=>g.locked).length,pref:prefs[c.id]||"best",job:null}}),prefs:PREFS}},
  manualGames:async({systemId})=>{await o.wait(150);return gamesOf(systemId).map(g=>({file:g.file,title:g.title,cover:null,page:g.cur?page(g.title,g.cur.lang,g.cur.w,g.cur.v):null,pages:g.cur?g.cur.pages:0,status:!g.cur?"missing":g.cur.pages<=2?"incomplete":"have",locked:!!g.locked,source:g.cur?g.cur.label:""}))},
  manualCands:async({systemId,file})=>{await o.wait(140);const g=find(systemId,file),p=prefs[systemId]||"best",my=++gen,local=[...(g.cur?[g.cur]:[]),...g.earlier],all=local.slice();lists[file]=all;
    remote(systemId,g).filter(c=>!(g.cur&&g.cur.src===c.key)).forEach((c,i)=>setTimeout(()=>{if(my!==gen||c.dead)return;all.push(c);emit({systemId,file,key:c.key,cand:pub(c,expectOf(all),p),expect:expectOf(all)})},200+i*260+(hash(c.key)%400)));
    setTimeout(()=>{if(my!==gen)return;const ex=expectOf(all);emit({systemId,file,done:true,expect:ex,count:all.length,list:all.map(c=>pub(c,ex,p))})},2400);
    const ex=expectOf(local);return {list:local.map(c=>pub(c,ex,p)),searching:true,expect:ex,locked:!!g.locked,pref:p,canUndo:!!(hist[file]||[]).length,title:g.title}},
  manualUse:async({systemId,file,key})=>{const g=find(systemId,file),c=(lists[file]||[]).find(x=>x.key===key);if(!c)throw new Error("That version isn't in the list any more. Open the game again.");
    if(key==="cur")return {manual:[],locked:!!g.locked,canUndo:!!(hist[file]||[]).length}; // the one in use: nothing to do (1.73: Use doesn't lock)
    const id="mu"+(++jobN);ejob({id,kind:"use",systemId,file,label:g.title,state:"run",msg:"Downloading…"});for(let i=1;i<=3;i++){await o.wait(260);ejob({id,kind:"use",systemId,file,label:g.title,state:"run",msg:`page ${Math.round(i*(c.pages||12)/3)} of ${c.pages||12}`})}
    // (beta.9, as main does it) an earlier copy put back leaves the Earlier list (moved, not copied), and the same manual is
    // never listed twice
    (hist[file]=hist[file]||[]).push({cur:g.cur,locked:g.locked});if(c.source==="earlier")g.earlier=g.earlier.filter(x=>x.key!==key);
    if(g.cur&&!g.earlier.some(x=>x.src&&x.src===g.cur.src))g.earlier.unshift({...g.cur,key:"earlier:"+Date.now(),source:"earlier",label:g.cur.label+" · Earlier"});g.cur={...c,key:"cur",source:"current",src:c.source==="earlier"?(c.src||key):key,origin:"studio"};
    ejob({id,kind:"use",systemId,file,label:g.title,state:"done",msg:"Manual in place"});return {manual:[page(g.title,c.lang,c.w,c.v)],locked:!!g.locked,canUndo:true,cur:{src:key,origin:"studio"}}},
  manualUndo:async({systemId,file})=>{await o.wait(200);const g=find(systemId,file),h=(hist[file]||[]).pop();if(!h)throw new Error("Nothing to undo");if(g.cur)g.earlier.unshift({...g.cur,key:"earlier:"+Date.now(),source:"earlier",label:g.cur.label.replace(/ · Earlier.*$/,"")+" · Earlier"});g.cur=h.cur;g.locked=h.locked;return {manual:[],locked:!!g.locked,canUndo:!!hist[file].length}},
  manualLock:async({systemId,file,locked})=>{await o.wait(80);const g=find(systemId,file);g.locked=!!locked;return {locked:g.locked}},
  manualOwn:async({systemId,file})=>{await o.wait(300);const g=find(systemId,file);if(window.__mockOwnCancel)return {canceled:true};(hist[file]=hist[file]||[]).push({cur:g.cur,locked:g.locked});g.cur={key:"cur",source:"current",label:"Your PDF · My scan",lang:"",region:"",pages:20,w:1600,v:9,src:"own",origin:"own"};return {manual:[],locked:!!g.locked,canUndo:true}},
  manualPref:async({systemId,pref})=>{await o.wait(100);if(!PREFS.some(p=>p.id===pref))throw new Error("Unknown choice.");prefs[systemId]=pref;const id="mj"+(++jobN),G=gamesOf(systemId),n=Math.min(6,G.length),label=(o.consoles.find(c=>c.id===systemId)||{}).name+" manuals · "+PREFS.find(p=>p.id===pref).label;let d=0;jobs[id]={cancel:false};
    const tick=()=>{if(jobs[id].cancel){ejob({id,systemId,label,done:d,total:n,state:"cancelled",msg:"Stopped"});return}if(d>=n){ejob({id,systemId,label,done:d,total:n,state:"done",msg:`${Math.floor(n/2)} manuals changed`});return}d++;ejob({id,systemId,label,done:d,total:n,state:"run",msg:`${d} of ${n}`});setTimeout(tick,400)};setTimeout(tick,200);return {pref,jobId:id}},
  manualCancel:async({jobId})=>{if(jobs[jobId])jobs[jobId].cancel=true;return true},
  onManualCand:fn=>{candL.push(fn);return ()=>{const i=candL.indexOf(fn);if(i>=0)candL.splice(i,1)}},
  onManualJob:fn=>{jobL.push(fn);return ()=>{const i=jobL.indexOf(fn);if(i>=0)jobL.splice(i,1)}}
});
}
