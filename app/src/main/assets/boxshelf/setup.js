// First-run wizard and Settings: emulators, game folders, Steam, media apps.
(function(){
"use strict";
const api=window.boxshelf;
const $=id=>document.getElementById(id);
const el=(tag,attrs,...kids)=>{const e=document.createElement(tag);for(const[k,v]of Object.entries(attrs||{})){if(k==="class")e.className=v;else if(k==="text")e.textContent=v;else if(k.startsWith("on"))e.addEventListener(k.slice(2),v);else if(v===true)e.setAttribute(k,"");else if(v!==false&&v!=null)e.setAttribute(k,v)}for(const c of kids.flat())if(c!=null)e.append(c);return e};

let coverBusy=false,legacyBack=null,dlRuns=0; // dlRuns: box art / manual downloads started here (Settings rescans on close only after one)
let opening=false,info=null,cfg=null,det=null,wizard=false,step=0,steps=[],saveT=null,detecting=false;
const dirsGone=new Set(); // (beta.10) Android: games folders removed this session (see the Game folders card)

const CONSOLE_HELP={
  // (beta.7) wave 2: RetroArch runs these; only the ones with something to know get a note
  arcade:"RetroArch's FinalBurn Neo core runs arcade games. Games are zips named by their ROM set (sf2.zip): keep them zipped and named as they are, and Boxshelf shows each game's real name. BIOS zips (like neogeo.zip, pgm.zip) go in the same folder and stay off the shelf.",
  neogeo:"RetroArch's FinalBurn Neo core runs Neo Geo games. Games are zips named by their ROM set (mslug.zip); keep neogeo.zip (the BIOS) in the same folder. It stays off the shelf.",
  saturn:"RetroArch's Beetle Saturn core runs Saturn games (.cue, .chd, .m3u for several discs). It needs the Saturn BIOS files in RetroArch's system folder.",
  segacd:"RetroArch's Genesis Plus GX core runs Sega CD games (.cue, .chd). It needs the Sega CD BIOS files (bios_CD_U.bin and friends) in RetroArch's system folder.",
  dos:"RetroArch's DOSBox Pure core runs DOS games straight from their .zip (or .dosz). One zip per game, as it came.",
  gc:"Dolphin runs GameCube games. Point to Dolphin.exe.",
  wii:"Dolphin also runs Wii games. It can be the same Dolphin.exe as GameCube.",
  sw:"Eden runs Switch games. Point to eden.exe.",
  sw2:"Eglantine (an Eden fork) runs Switch 2 games. Point to eglantine.exe. Games can be unpacked folders (exefs + romfs, or base + updates): the newest update is started.",
  psp:"PPSSPP runs PSP games. Point to PPSSPPWindows64.exe.",
  wiiu:"Cemu runs Wii U games. Point to Cemu.exe. Games can be .wua files (Cemu's one-file format, best), .wud / .wux disc images, or extracted game folders (the shelf finds code\\*.rpx and reads the title from meta\\meta.xml). Disc images need your keys.txt set up in Cemu. Install updates and DLC inside Cemu (File → Install game title).",
  ps2:"PCSX2 runs PS2 games. Point to pcsx2-qt.exe. Games are .iso, .chd, .cso or .zso images. PCSX2 needs a PS2 BIOS of its own (set it up in PCSX2 once). RetroAchievements works here too: log in inside PCSX2 (Settings → Achievements).",
  x360:"Xenia runs Xbox 360 games. Point to xenia_canary.exe (or xenia.exe; if you pick Xenia Manager, Boxshelf finds the Canary build inside its Emulators folder). Titles, art and info come from the title ID inside each game, so file names don't have to be exact. Games can be .iso discs, extracted folders with a default.xex inside (the folder name is the title), XBLA .xex files or .zar archives.",
  ps3:"RPCS3 runs PS3 games. Point to rpcs3.exe (install the PS3 system software in RPCS3 first: File → Install Firmware). Disc games are folders (Game\\PS3_GAME\\USRDIR\\EBOOT.BIN); digital games live in RPCS3's dev_hdd0\\game folder — add that as a games folder too. Titles come from each game's PARAM.SFO; installed patches are left off the shelf.",
  n3ds:"Azahar runs 3DS games. Point to azahar.exe. Games are decrypted .3ds / .cci / .cxi images, Azahar's compressed .zcci, or decrypted .cia files (Boxshelf installs a .cia into Azahar the first time you play it). Encrypted copies won't start.",
  ps4:"shadPS4 runs PS4 games. Point to shadPS4.exe. Games are folders with an eboot.bin inside (CUSAxxxxx\\eboot.bin); the shelf reads each game's title from its param.sfo.",
  ps5:"Kyty runs PS5 games. Point to kyty_emulator.exe (the KytyJets build is the one Boxshelf looks for first). Games are folders with an eboot.bin inside; the shelf reads each game's title from sce_sys\\param.json, and starts it with your Kyty launcher's settings."
};

// Android's wizard (the Thor's small screen, 23 consoles): one Consoles page instead of a step per console. It shows the
// consoles this device can already play first and folds the rest away under "More consoles" (consolesHub).
const onAndroid=()=>!!(window.bsPlatform&&bsPlatform.os==="android");
const hubWizard=()=>wizard&&onAndroid();
// Windows first run (1.75): five short pages instead of a page per console. Pick the consoles you play (found ones come
// ticked), fix only what's missing for those, choose how the boxes look, done. Everything else (boot screens, the
// GameCube startup, launch options, your own art folder) keeps its default and lives in Settings.
const winWizard=()=>wizard&&!onAndroid();
function sectionList(){
  if(winWizard())return [{id:"welcome",label:"Welcome"},{id:"pick",label:"Your consoles"},{id:"fix",label:"Finish setting up"},{id:"artlook",label:"Box art"},{id:"accounts",label:"Accounts"},{id:"done",label:"Done"}]; // (beta.9: Accounts, optional)
  const s=[],hub=!wizard||hubWizard();
  if(wizard)s.push({id:"welcome",label:"Welcome"});
  // the Android build's own wizard questions (android-wizard.js: games folder, box art, bottom screen, Home app), one per page;
  // a page can drop out when an earlier answer makes it moot (no art download → no "where to save it")
  const X=wizard&&window.BS_WIZARD_EXTRA;if(X)X.pages().forEach(p=>{if(!p.when||p.when(cfg))s.push({id:p.id,label:p.label,extra:p})});
  if(hub)s.push({id:"consoles",label:"Consoles"});
  for(const c of info.consoles)s.push({id:c.id,label:c.name,console:c,parent:hub?"consoles":null});
  if(wizard&&(!window.bsPlatform||bsPlatform.has("steam")))s.push({id:"steam",label:"Steam"},{id:"media",label:"Stremio & Nuvio"}); // Settings has its own PC and Apps pages; not on Android yet
  if(!wizard)SETTINGS_PAGES.forEach(([id,label])=>s.push({id,label,settings:true}));
  if(wizard)s.push({id:"done",label:"Finish"});
  return s;
}

function dotFor(sec){
  if(sec.console){const c=cfg.systems[sec.id];if(!c.enabled)return"off";return c.emulator&&c.romDirs.length?"ok":c.emulator||c.romDirs.length?"warn":""}
  if(sec.id==="steam")return !cfg.steam.enabled?"off":cfg.steam.path?"ok":"";
  if(sec.id==="media")return Object.values(cfg.apps).some(a=>a.enabled&&a.path)?"ok":"";
  if(sec.id==="pick")return pickItems().some(x=>x.on())?"ok":"";
  if(sec.id==="fix")return fixNeeds().length?"warn":"ok";
  if(sec.id==="artlook")return cfg.ui.autoCovers===true?"ok":"";
  if(sec.id==="accounts")return acctAny()?"ok":"";
  return "";
}

function openLink(url){if(!url||!/^https?:\/\//i.test(url))return;if(api.openExternal)Promise.resolve(api.openExternal(url)).catch(e=>Shelf.toast(e.message||String(e)));else Shelf.toast("Opening links needs a newer Boxshelf")}

/* ---------- open / close ---------- */
async function open(opts){
  opts=opts||{};
  const m=Shelf.getMode();if(m!=="browse"&&m!=="details")return; // never over Inspect, the museum, Sort or a starting game
  // claim the screen before waiting for the config, so nothing else (the museum, a launch…) can open in the meantime
  if(Shelf.closePanels)Shelf.closePanels();
  Shelf.setMode("setup");opening=true; // keys and pad are ignored until the panel is up (no close/save with a stale config)
  try{if(window.Shelf&&Shelf.flushSaves)await Shelf.flushSaves();info=await api.getConfig()}catch(e){opening=false;Shelf.setMode("browse");Shelf.toast(e.message||String(e));return}
  opening=false;if(Shelf.getMode()!=="setup")return; // something took the screen back meanwhile
  cfg=JSON.parse(JSON.stringify(info.config));
  wizard=!!opts.wizard||!cfg.setupDone;
  // everything but the first-run wizard opens the full-screen Settings (settings.js); it hands the classic pages back here
  if(!wizard&&window.Settings&&Settings.show){legacyBack=null;Settings.show(info,opts);return}
  steps=sectionList();step=0;acctOsk=null;acctAsked=false;acctMore=false;hubMain.clear();hubMore=false;pickMain=null;pickMore=false;wizLook=null;artChosen=null;searched=false;
  $("setup").classList.add("on");$("setup").setAttribute("aria-hidden","false");
  render();
}
async function close(){
  if(opening)return;clearTimeout(saveT);await persist();
  $("setup").classList.remove("on");$("setup").setAttribute("aria-hidden","true");
  if(legacyBack){const back=legacyBack;legacyBack=null;back();return} // a classic page opened from Settings: back to it
  Shelf.setMode("browse");await Shelf.refresh(true);if(Shelf.raReload)Shelf.raReload();
}
async function persist(){try{info.config=await api.saveConfig(cfg)}catch(e){Shelf.toast(e.message)}}
function changed(){clearTimeout(saveT);saveT=setTimeout(persist,400);renderNav()}

/* ---------- auto-detect ---------- */
// (beta.10) one search at a time: a second press (or Android's Get started tapped twice) waits for the one running instead
// of starting another alongside
let detectRun=null;
function runDetect(applyEmpty){if(detectRun)return detectRun;const p=detectRun=detectNow(applyEmpty);p.finally(()=>{if(detectRun===p)detectRun=null}).catch(()=>{});return p}
async function detectNow(applyEmpty){
  detecting=true;render();
  try{det=await api.detect()}catch(e){Shelf.toast(e.message);det=null}
  detecting=false;
  if(det&&applyEmpty){
    for(const[id,d]of Object.entries(det.systems)){const c=cfg.systems[id];if(!c)continue;const had=!!(c.emulator||c.romDirs.length);if(!c.emulator&&d.emulator)c.emulator=d.emulator;if(!c.romDirs.length&&d.romDirs.length)c.romDirs=[...d.romDirs];
      if(winWizard()&&pickDone&&!had&&pickFound(c))c.enabled=true} // (the Windows wizard's Search again: a console found now comes ticked)
    if(winWizard())pickMain=null; // found ones move up top
    if(!cfg.steam.path&&det.steam.path)cfg.steam.path=det.steam.path;
    for(const[id,a]of Object.entries(det.apps))if(cfg.apps[id]&&!cfg.apps[id].path&&a.path)cfg.apps[id].path=a.path;
    changed();
  }
  render();
}

/* ---------- rendering ---------- */
// Re-rendering the same page (after a seg pick, a toggle…) keeps the scroll position and the focused control,
// so a controller user doesn't get thrown back to the top of the page.
let lastPage=null;
function render(){const b=$("suBody"),pg=steps[step]&&steps[step].id,same=pg===lastPage,top=b.scrollTop,fi=same?focusables().indexOf(document.activeElement):-1,fromNav=inNav(document.activeElement);
  renderNav();renderBody();renderFoot();lastPage=pg;
  if(!same&&fromNav){const n=navActive();if(n)n.focus({preventScroll:true})}
  if(same){b.scrollTop=top;if(fi>=0){const f=focusables();if(f[fi])f[fi].focus({preventScroll:true})}}}
/* Discord-style sidebar for the classic pages opened from Settings: Consoles (console pages live behind its grid) and
   the Library pages, an icon per entry. The wizard keeps its plain step list. */
const ICON={consoles:'<path d="M6 11h4M8 9v4M15 12h.01M18 10h.01"/><rect x="2" y="6" width="20" height="12" rx="6"/>',library:'<path d="M4 4h4v16H4zM10 4h4v16h-4z"/><path d="M16 5l3.5-1 2.5 15.5-3.5 1z"/>',art:'<rect x="3" y="4" width="18" height="16" rx="2"/><circle cx="9" cy="10" r="2"/><path d="M21 16l-5-5-9 9"/>',reading:'<path d="M3 5h7a2 2 0 0 1 2 2v12a2 2 0 0 0-2-2H3z"/><path d="M21 5h-7a2 2 0 0 0-2 2v12a2 2 0 0 1 2-2h7z"/>'};
const NAV_GROUPS=[["Systems",["consoles"]],["Library",["art","library","reading"]]];
const ico=k=>{const sp=document.createElement("span");sp.className="su-ico";sp.innerHTML=`<svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round">${ICON[k]||""}</svg>`;return sp};
function renderNav(){
  const nav=$("suNav");nav.innerHTML="";nav.append(el("div",{class:"su-brand",text:wizard?"Boxshelf setup":"Settings"}));
  const ver=el("div",{class:"su-ver",text:info.version?"Boxshelf "+info.version:"Boxshelf"});
  if(wizard){const curId=(steps[step]||{}).parent||(steps[step]||{}).id; // a console page opened from the Consoles page lights up Consoles
    steps.forEach((s,i)=>{if(s.parent)return;const d=dotFor(s);nav.append(el("button",{type:"button",class:"su-step"+(s.id===curId?" on":""),onclick:()=>{step=i;render()}},el("span",{class:"su-label",text:s.label}),s.id==="consoles"?hubCount():el("span",{class:"dot "+d})))});nav.append(ver);return}
  const cur=steps[step]||{},curId=cur.parent||cur.id,idx=id=>steps.findIndex(x=>x.id===id);
  NAV_GROUPS.forEach(([g,items])=>{nav.append(el("div",{class:"su-group",text:g}));
    items.forEach(id=>{const i=idx(id);if(i<0)return;const s=steps[i],d=dotFor(s);
      nav.append(el("button",{type:"button",class:"su-step"+(curId===id?" on":""),onclick:()=>{step=i;render()}},ico(id),el("span",{class:"su-label",text:s.label}),(d&&id!=="consoles")?el("span",{class:"dot "+d}):"",id==="consoles"?el("span",{class:"su-count",text:consolesReady()}):""));
    })});
  nav.append(ver);
}
function consolesReady(){const on=info.consoles.filter(c=>cfg.systems[c.id]&&cfg.systems[c.id].enabled),ok=on.filter(c=>dotFor({console:c,id:c.id})==="ok");return `${ok.length}/${on.length}`}
/* ---------- the Consoles page: a card per console; pick one to open its page ---------- */
function consolesSection(b){
  if(hubWizard())return consolesHub(b);
  head(b,"Systems","Consoles","Every console Boxshelf can run. Pick one to set its emulator and game folders.");
  const grid=el("div",{class:"con-grid"});
  info.consoles.forEach(c=>{const sc=cfg.systems[c.id]||{},d=dotFor({console:c,id:c.id}),i=steps.findIndex(x=>x.id===c.id);
    const st=d==="off"?"Shelf hidden":d==="ok"?`${(sc.romDirs||[]).length} folder${(sc.romDirs||[]).length===1?"":"s"} · ready`:d==="warn"?(sc.emulator?"Add a games folder":"Point to the emulator"):"Not set up";
    grid.append(el("button",{type:"button",class:"con-card "+(d||"none"),onclick:()=>{step=i;render()}},el("span",{class:"con-name",text:c.name}),el("span",{class:"con-emu",text:c.emulatorName||""}),el("span",{class:"con-st"},el("span",{class:"dot "+d}),st)))});
  b.append(grid);
}
/* Android wizard: the consoles that matter on this device first, as big cards (an emulator app found that runs it, or
   games folders already added), everything else folded under "More consoles". A console that earned a place up top
   keeps it while the wizard is open (hubMain), so cards never jump around under your thumb after adding a folder. */
let hubMain=new Set(),hubMore=false;
const isRA=e=>/retroarch/i.test(e||"");
const emuLabel=e=>String(e||"").replace(/\s*\([^)]*\)\s*$/,"");
function hubGroup(c){const sc=cfg.systems[c.id]||{},d=det&&det.systems&&det.systems[c.id]||{};
  if(hubMain.has(c.id)||(sc.romDirs||[]).length||(d.romDirs||[]).length||(sc.emulator&&!isRA(sc.emulator))||(d.emulator&&!isRA(d.emulator)))return "main";
  return isRA(sc.emulator)||isRA(d.emulator)?"ra":"rest"}
// the wizard's Consoles step counts what's ready to play ("2 ready"), not "0/23" against consoles you may never own
function hubCount(){const n=info.consoles.filter(c=>dotFor({console:c,id:c.id})==="ok").length;return n?el("span",{class:"su-count",text:`${n} ready`}):el("span",{class:"dot"})}
function hubCard(c,big){const sc=cfg.systems[c.id]||{},d=dotFor({console:c,id:c.id}),i=steps.findIndex(x=>x.id===c.id),n=(sc.romDirs||[]).length;
  const st=d==="off"?"Shelf hidden":d==="ok"?`${n} folder${n===1?"":"s"} · ready`:d==="warn"?(sc.emulator?"Add a games folder":"Choose the emulator app"):"Not set up";
  return el("button",{type:"button",class:"con-card "+(big?"big ":"")+(d||"none"),"data-con":c.id,onclick:()=>{step=i;render()}},
    el("span",{class:"con-name",text:c.name}),el("span",{class:"con-emu",text:sc.emulator?emuLabel(sc.emulator):`${c.emulatorName||"Emulator"} not found`}),
    el("span",{class:"con-st"},el("span",{class:"dot "+d}),st))}
function consolesHub(b){
  const groups={main:[],ra:[],rest:[]};info.consoles.forEach(c=>groups[hubGroup(c)].push(c));groups.main.forEach(c=>hubMain.add(c.id));
  head(b,"Systems","Your consoles",groups.main.length?"These can already play on this device. Pick one to check its emulator and add the folder with its games.":"No emulator apps found yet. Install one (like Dolphin, RetroArch or DuckStation) and press Search again, or set a console up by hand below.");
  if(groups.main.length)b.append(el("div",{class:"con-grid hub"},groups.main.map(c=>hubCard(c,true))));
  const others=groups.ra.length+groups.rest.length;if(!others)return;
  const open=hubMore||!groups.main.length;
  b.append(el("button",{type:"button",class:"con-more","aria-expanded":String(open),onclick:()=>{hubMore=!open;render();if(hubMore){const m=$("suBody").querySelector(".con-more");if(m)m.scrollIntoView({block:"start",behavior:"smooth"})}}},
    el("span",{text:`More consoles (${others})`}),el("span",{class:"con-more-ch","aria-hidden":"true"})));
  if(!open)return;
  if(groups.ra.length)b.append(el("div",{class:"con-sub",text:"RetroArch can run these"}),el("div",{class:"con-grid hub small"},groups.ra.map(c=>hubCard(c,false))));
  if(groups.rest.length)b.append(el("div",{class:"con-sub",text:"No emulator app found"}),el("div",{class:"con-grid hub small"},groups.rest.map(c=>hubCard(c,false))));
}
function renderFoot(){
  const f=$("suFoot");f.innerHTML="";
  const sec=steps[step];
  if(wizard){
    // run again from Settings (setup already done): the first step's Back leaves, so the wizard can't trap you
    const out=step===0&&!!(cfg&&cfg.setupDone);f.append(el("button",{type:"button",class:"btn",disabled:step===0&&!out,onclick:()=>{if(out)close();else stepBy(-1)}},out?"Cancel":"Back"),el("div",{class:"grow"}));
    if(sec.id==="done")f.append(el("button",{type:"button",class:"btn primary",onclick:finish},"Open Boxshelf"));
    else if(sec.parent)f.append(el("button",{type:"button",class:"btn primary",onclick:()=>{persist();stepBy(-1)}},"Done")); // a console page from the Consoles page: back to it
    else if(sec.id!=="welcome"){if(sec.id==="consoles"||sec.id==="pick")f.append(el("button",{type:"button",class:"btn",onclick:()=>runDetect(true)},detecting?"Searching…":"Search again"));
      // (beta.9) Accounts: "Skip for now" until something is filled in
      f.append(el("button",{type:"button",class:"btn primary","data-next":sec.id,onclick:()=>{persist();stepBy(1)}},sec.id==="accounts"&&!acctAny()?"Skip for now":"Next"))}
  }else{
    if(sec.id==="consoles"||sec.console)f.append(el("button",{type:"button",class:"btn",onclick:()=>runDetect(true)},detecting?"Searching…":"Find emulators again"));f.append(el("div",{class:"grow"}),el("button",{type:"button",class:"btn primary",onclick:close},"Done"));
  }
}
// back to the Consoles page from a console's page: the card you came from gets the focus and scrolls into view, so a
// console deep in "More consoles" doesn't drop you back at the top of the list
function toHub(from){step=steps.findIndex(x=>x.id==="consoles");render();const c=from&&$("suBody").querySelector(`.con-card[data-con="${from}"]`);if(c)focusEl(c)}
// wizard Back / Next: a console page opened from the Consoles page goes back to it, and Next from the Consoles page skips
// over the console pages to Finish
function stepBy(d){const sec=steps[step];if(sec&&sec.parent&&d<0)return toHub(sec.id);
  let n=step+d;while(n>0&&n<steps.length-1&&steps[n].parent)n+=d;step=Math.max(0,Math.min(steps.length-1,n));render()}
function renderBody(){
  const b=$("suBody");b.innerHTML="";b.scrollTop=0;
  const sec=steps[step];
  if(sec.id==="welcome")return window.BS_WIZARD_EXTRA&&BS_WIZARD_EXTRA.welcome?BS_WIZARD_EXTRA.welcome(b,wizCtx()):welcome(b);
  if(sec.extra)return sec.extra.render(b,wizCtx());
  if(sec.id==="pick")return pickSection(b);
  if(sec.id==="fix")return fixSection(b);
  if(sec.id!=="accounts")acctOsk=null; // (the keyboard never follows you to another page)
  if(sec.id==="artlook")return artLookSection(b);
  if(sec.id==="accounts")return acctSection(b);
  if(sec.id==="consoles")return consolesSection(b);
  if(sec.console){if(sec.parent)b.append(el("button",{type:"button",class:"su-back",onclick:()=>wizard?toHub(sec.id):(step=steps.findIndex(x=>x.id==="consoles"),render())},"‹ All consoles"));return consoleSection(b,sec.console)}
  if(sec.id==="steam")return steamSection(b);
  if(sec.id==="media")return mediaSection(b);
  if(sec.id==="library")return librarySection(b);if(sec.id==="art")return artSection(b);if(sec.id==="reading")return readingSection(b);
  if(sec.id==="done")return doneSection(b);
}
// Official download pages, one press away while setting up (opened in your browser; nothing is downloaded by Boxshelf)
// (beta.4) download pages rather than home pages, checked against each project's own GitHub; Switch 2 added. Eden lives
// only at eden-emu.dev (look-alike sites serve fakes)
const OFFICIAL={gc:[["Dolphin","https://dolphin-emu.org/download/"]],wii:[["Dolphin","https://dolphin-emu.org/download/"]],wiiu:[["Cemu","https://cemu.info/"]],sw:[["Eden","https://eden-emu.dev/downloads"]],sw2:[["Eglantine","https://github.com/Djihads80/Eglantine/releases"]],
  psp:[["PPSSPP","https://www.ppsspp.org/download/"]],ps2:[["PCSX2","https://pcsx2.net/downloads/"]],ps3:[["RPCS3","https://rpcs3.net/download"]],ps4:[["shadPS4","https://shadps4.net/downloads"]],ps5:[["KytyPS5","https://github.com/Jetsku/KytyPS5/releases"]],n3ds:[["Azahar","https://azahar-emu.org/pages/download/"]],
  x360:[["Xenia","https://xenia.jp/download/"],["Xenia Manager","https://github.com/xenia-manager/xenia-manager"]],
  // (beta.6) the retro shelves: RetroArch (one download for all of them; cores from its Online Updater) and each one's best standalone
  ...(()=>{const RA=["RetroArch","https://www.retroarch.com/?page=platforms"],o={psx:[["DuckStation","https://github.com/stenzek/duckstation/releases"],RA],nds:[["melonDS","https://melonds.kuribo64.net/downloads.php"],RA],
    gba:[RA,["mGBA","https://mgba.io/downloads.html"]],n64:[RA,["RMG","https://github.com/Rosalie241/RMG/releases"]],megadrive:[RA,["BlastEm","https://www.retrodev.com/blastem/downloads.html"]],dreamcast:[RA,["Flycast","https://github.com/flyinghead/flycast/releases"]],saturn:[RA],pcengine:[RA],neogeo:[RA],arcade:[RA],segacd:[RA],sega32x:[RA],atari2600:[RA],atarilynx:[RA],wonderswan:[RA],dos:[RA]};
    for(const id of ["nes","snes","gb","gbc","mastersystem","gamegear"])o[id]=[RA];return o})(),steam:[["Steam","https://store.steampowered.com/about/"]],
  stremio:[["Stremio","https://www.stremio.com/downloads"]],nuvio:[["Nuvio","https://github.com/NuvioMedia/NuvioDesktop"]]};
function siteLinks(id){const l=OFFICIAL[id];if(!l)return null;return el("div",{class:"row su-links"},el("span",{class:"hint-text",text:"Official site:"}),...l.map(([n,u])=>el("button",{type:"button",class:"btn small ghost",title:u,onclick:()=>openLink(u)},`Get ${n} ↗`)))}
function head(b,kicker,title,lead){b.append(el("div",{class:"su-kicker",text:kicker}),el("div",{class:"su-title",id:"suTitle",text:title}));if(lead)b.append(el("p",{class:"su-lead",text:lead}))}

function badge(target,p,want,optional){
  target.innerHTML="";if(!p){if(!optional)target.append(el("span",{class:"badge bad",text:"Not set"}));return}
  api.checkPaths([p]).then(([r])=>{target.innerHTML="";const ok=want==="file"?r.file:r.dir;target.append(el("span",{class:"badge "+(ok?"ok":"bad"),text:ok?(want==="file"?"Found":"Folder found"):"Can't find it"}))}).catch(()=>{});
}
function pathField(label,value,opts){
  const status=el("span");const input=el("input",{class:"text",type:"text",value:value||"",placeholder:opts.placeholder||"",spellcheck:"false","aria-label":label});
  input.addEventListener("change",()=>{const v=input.value.trim().replace(/^"(.*)"$/,"$1");input.value=v;opts.set(v);badge(status,v,opts.want,opts.optional);changed()}); // Explorer's "Copy as path" adds quotes
  const browse=el("button",{type:"button",class:"btn small",onclick:async()=>{const p=await api.pick({kind:opts.want==="file"?"file":"folder",title:opts.pickTitle||label,filters:opts.filters,defaultPath:input.value||undefined}).catch(e=>{Shelf.toast(e.message)});if(p){input.value=p;opts.set(p);badge(status,p,opts.want,opts.optional);changed();if(opts.after)opts.after(p)}}},"Browse");
  const row=el("div",{class:"row"},input,browse);if(opts.extra)row.append(...opts.extra);
  const f=el("div",{class:"field"},el("div",{class:"lbl"},label,status),row);
  if(opts.help)f.append(el("div",{class:"hint-text",text:opts.help}));
  if(opts.want)badge(status,value,opts.want,opts.optional);
  return f;
}
// the art library: one folder for every console's box art and manuals (Art\<console>\covers and \manuals)
function artLibCard(title){return card(title||"Art library","Where box art and manuals are kept. Pick one folder and every console gets its own folder in it, with covers and manuals inside, easy to back up or reuse on a new setup. Art already beside your games is copied there in the background (nothing is moved, deleted or downloaded again). Leave it empty to keep art beside each console's games.",
  pathField("Art library folder",cfg.ui.artLibrary||"",{want:"dir",optional:true,set:v=>{cfg.ui.artLibrary=v},pickTitle:"Choose a folder for your box art and manuals",placeholder:"Beside each console's games (a covers folder)"}))}
// what android-wizard.js's pages get: the config being edited and the wizard's own building blocks
function wizCtx(){return {cfg,info,el,head,card,toggle,persist,changed,render,toast:m=>Shelf.toast(m),detect:()=>runDetect(true),
  // on to the next page (the list is made again first: an answer can add or drop pages); a controller user keeps focus in the page
  next:()=>{const id=(steps[step]||{}).id,pad=$("setup").contains(document.activeElement);steps=sectionList();step=Math.min(steps.length-1,steps.findIndex(x=>x.id===id)+1);persist();render();
    if(pad){const f=$("suBody").querySelector(".wz-choice.on")||$("suBody").querySelector(".wz-choice, button");if(f)f.focus({preventScroll:true})}},
  goto:id=>{const i=steps.findIndex(x=>x.id===id||(x.console&&x.console.id===id));if(i>=0){step=i;render()}}}}
function toggle(label,checked,onchange,id){
  const inp=el("input",{type:"checkbox",id});inp.checked=!!checked;inp.addEventListener("change",()=>{onchange(inp.checked);changed()});
  return el("label",{class:"switch"},inp,label);
}

function welcome(b){
  if(onAndroid())head(b,"Welcome","Let's connect your emulators and games","Boxshelf looks for the emulator apps on this device and the game folders you've already let it see. Next you'll see the consoles it found first; every other console is one tap away under More consoles.");
  else head(b,"Welcome to Boxshelf","Let's find your games","Boxshelf looks for your emulators, games, Steam library and streaming apps. Next you pick which consoles you play, and it's ready in a minute.");
  if(onAndroid()){const root=info.appRoot;b.append(el("div",{class:"field"},el("div",{class:"lbl"},"Boxshelf folder"),el("div",{class:"d-cmd",text:root})))}
  b.append(el("div",{class:"d-actions"},
    el("button",{type:"button",class:"btn primary",onclick:async()=>{if(detecting)return;await runDetect(true);searched=true;pickInit();if(step===0)step=1;render()}} /* (beta.10) only moves on if you're still on Welcome */,detecting?el("span",{class:"scanning"}):null,detecting?" Looking…":winWizard()?"Find my emulators and games":"Find automatically"),
    el("button",{type:"button",class:"btn",onclick:()=>{pickInit();step=1;render()}},winWizard()?"I'll pick them myself":"Set up by hand")));
}

/* ---------- the Windows first run's own pages ---------- */
// Your consoles: every console plus Steam and the media apps as a card with a tick. On the very first run the ones with
// an emulator or games found come ticked (pickInit, once); the rest wait under "More consoles". A card that started up top
// stays there while the wizard is open (pickMain), so cards don't jump under the pointer. Unticked = no shelf.
let wzPn,wzPnOn=false,emuAutoRan=false,pickDone=false,pickMain=null,pickMore=false,wizLook=null,artWatch=null,searched=false; // searched: Find was pressed (1.82)
function pickInit(){
  if(!winWizard()||pickDone||(info.config&&info.config.setupDone))return;pickDone=true; // (Android's wizard has its own Consoles page)
  for(const c of info.consoles){const sc=cfg.systems[c.id];if(sc)sc.enabled=pickFound(sc)} // (beta.7) RetroArch alone ticks nothing: it runs 22 consoles
  cfg.steam.enabled=!!cfg.steam.path;
  for(const a of info.apps||[])if(cfg.apps[a.id])cfg.apps[a.id].enabled=!!cfg.apps[a.id].path;
  changed();
}
// (beta.7) found = its games, or an emulator of its own. RetroArch alone runs every retro console, so it doesn't make one
// "found": those wait, unticked, under More consoles ("RetroArch can run these") until you pick them.
const pickFound=sc=>!!(sc&&((sc.romDirs||[]).length||(sc.emulator&&!isRA(sc.emulator))));
function pickItems(){
  const L=info.consoles.map(c=>{const sc=cfg.systems[c.id];return {id:c.id,name:c.name,
    found:()=>pickFound(sc),ra:()=>!pickFound(sc)&&isRA(sc.emulator),on:()=>sc.enabled!==false,set:v=>{sc.enabled=v},
    status:()=>{const n=sc.romDirs.length,e=c.emulatorName||"Emulator";return sc.emulator?`${e} found${n?` · ${n} game folder${n===1?"":"s"}`:""}`:n?`Games found · ${e} not found yet`:`${e} not found`}}});
  if(!window.bsPlatform||bsPlatform.has("steam"))L.push({id:"steam",name:"Steam",found:()=>!!cfg.steam.path,on:()=>!!cfg.steam.enabled,set:v=>{cfg.steam.enabled=v},status:()=>cfg.steam.path?"Steam found · your PC games":"Steam not found"});
  for(const a of info.apps||[]){const c=cfg.apps[a.id];if(c)L.push({id:"app:"+a.id,name:a.name,found:()=>!!c.path,on:()=>!!c.enabled,set:v=>{c.enabled=v},status:()=>c.path?`${a.name} found · Media shelf`:`${a.name} not found`})}
  return L;
}
function pickCard(x,big){
  const on=x.on();
  return el("button",{type:"button",class:"wz2-card"+(big?" big":"")+(on?" on":""),role:"checkbox","aria-checked":String(on),"data-pick":x.id,onclick:()=>{x.set(!x.on());changed();render()}},
    el("span",{class:"wz2-tick","aria-hidden":"true"}),el("span",{class:"wz2-name",text:x.name}),el("span",{class:"wz2-st"+(x.found()?" ok":""),text:x.status()}));
}
// (beta.7) the folded "More consoles" list: small tick chips grouped by maker, so 30-odd consoles stay a tidy page
const PICK_FAMILIES=[["Nintendo",["nes","snes","n64","gc","wii","wiiu","sw","sw2","gb","gbc","gba","nds","n3ds"]],["PlayStation",["psx","ps2","ps3","ps4","ps5","psp"]],
  ["Sega",["mastersystem","megadrive","segacd","sega32x","saturn","dreamcast","gamegear"]],["Retro & arcade",[]],["Xbox, PC & apps",["x360","steam"]]];
function pickFamily(id){if(/^app:/.test(id))return "Xbox, PC & apps";for(const[f,ids]of PICK_FAMILIES)if(ids.includes(id))return f;return "Retro & arcade"}
function pickChip(x){const on=x.on();
  return el("button",{type:"button",class:"wz2-chip"+(on?" on":""),role:"checkbox","aria-checked":String(on),"data-pick":x.id,title:x.status(),onclick:()=>{x.set(!x.on());changed();render()}},
    el("span",{class:"wz2-tick","aria-hidden":"true"}),x.name)}
function pickSection(b){
  pickInit();
  const L=pickItems();if(!pickMain)pickMain=new Set(L.filter(x=>x.found()||x.on()).map(x=>x.id));
  const main=L.filter(x=>pickMain.has(x.id)),rest=L.filter(x=>!pickMain.has(x.id)),n=L.filter(x=>x.on()).length;
  head(b,"Step 1 of 3","Which consoles do you play?",main.length?"Ticked ones get a shelf. Boxshelf found these on your PC; tick any others you play. You can change this any time in Settings.":searched?"Boxshelf didn't find any emulators yet. Tick the consoles you play and you'll point to their emulator and games next.":"Tick the consoles you play. Next you'll point to each one's emulator and games.");
  if(main.length)b.append(el("div",{class:"wz2-grid"},main.map(x=>pickCard(x,true))));
  if(rest.length){const open=pickMore||!main.length;
    b.append(el("button",{type:"button",class:"con-more","aria-expanded":String(open),onclick:()=>{pickMore=!open;render()}},el("span",{text:`More consoles and apps (${rest.length})`}),el("span",{class:"con-more-ch","aria-hidden":"true"})));
    if(open){
      if(rest.some(x=>x.ra&&x.ra()))b.append(el("p",{class:"wz2-more-note",text:"RetroArch is installed, so it can run any of the retro consoles here. Tick only the ones you play."}));
      for(const[fam,ids]of PICK_FAMILIES){const xs=rest.filter(x=>pickFamily(x.id)===fam);if(!xs.length)continue;
        b.append(el("div",{class:"con-sub",text:fam}),el("div",{class:"wz2-chips wz2-more"},xs.map(pickChip)))}}}
  // (beta.7) Playnite on this PC: its library can come over right after setup (Settings → Custom games → Add games)
  if(winWizard()&&api.playniteFind){if(wzPn===undefined){wzPn=null;Promise.resolve(api.playniteFind()).then(f=>{wzPn=f||false;if(f)render()}).catch(()=>{wzPn=false})}
    if(wzPn)b.append(card("","",toggle("Import my Playnite library after setup (experimental)",wzPnOn,v=>{wzPnOn=!!v},"wz-pn"),
      el("div",{class:"hint-text",text:"Your Playnite games come over in one go: games you added by hand onto a Custom shelf, emulated games' folders onto their consoles. You see what's found before anything is added."})))}
  b.append(el("p",{class:"wz2-foot",text:n?`${n} shel${n===1?"f":"ves"} picked.`:"Nothing picked yet."}));
}
// Finish setting up: only what the picked consoles still need (the emulator program, a games folder). Nothing missing:
// this page just says so.
function fixNeeds(){
  const out=[];
  for(const c of info.consoles){const sc=cfg.systems[c.id];if(sc&&sc.enabled!==false&&!(sc.emulator&&sc.romDirs.length))out.push({kind:"con",def:c,sc})}
  if(cfg.steam.enabled&&!cfg.steam.path)out.push({kind:"steam"});
  for(const a of info.apps||[]){const c=cfg.apps[a.id];if(c&&c.enabled&&!c.path)out.push({kind:"app",def:a,c})}
  return out;
}
function fixRow(label,state,ok,...ctl){return el("div",{class:"wz2-row"},el("div",{class:"wz2-rl"},el("b",{text:label}),el("span",{class:"wz2-st"+(ok?" ok":""),text:state})),el("div",{class:"wz2-rr"},...ctl))}
const shortPath=p=>{const s=String(p||"");return s.length>58?"…"+s.slice(-56):s};
function fixSection(b){
  const need=fixNeeds();
  head(b,"Step 2 of 3",need.length?"A few things to finish":"Everything's ready",need.length?"Only what's missing for the shelves you picked. Anything you skip now can be done later in Settings.":"Every shelf you picked has its emulator and its games. On to the box art.");
  // the picked shelves that are ready, as a row of cards with a tick (nothing to do there; it shows what you'll get)
  const ready=pickItems().filter(x=>x.on()&&!need.some(n=>(n.def&&("app:"+n.def.id===x.id||n.def.id===x.id))||(n.kind==="steam"&&x.id==="steam")));
  if(ready.length){if(need.length)b.append(el("div",{class:"wz2-sub",text:"Ready"}));
    b.append(el("div",{class:"wz2-grid"+(need.length?" small":"")},ready.map(x=>el("div",{class:"wz2-card on ready"},el("span",{class:"wz2-tick","aria-hidden":"true"}),el("span",{class:"wz2-name",text:x.name}),el("span",{class:"wz2-st ok",text:x.status()})))))}
  for(const n of need){
    const box=el("section",{class:"wz2-fix"});
    if(n.kind==="con"){const {def,sc}=n,emu=def.emulatorName||"Emulator";
      box.append(el("div",{class:"wz2-fh"},el("span",{class:"wz2-fname",text:def.name}),el("button",{type:"button",class:"btn small ghost",onclick:()=>{sc.enabled=false;changed();render()}},"No shelf for now")));
      const find=el("button",{type:"button",class:"btn small"+(sc.emulator?"":" primary"),onclick:async()=>{const p=await api.pick({kind:"file",title:`Find ${def.exeNames[0]}`,filters:[{name:emu,extensions:["exe"]}]}).catch(()=>null);if(p){sc.emulator=p;changed();render()}}},sc.emulator?"Change":"Find it…");
      const get=OFFICIAL[def.id]?el("button",{type:"button",class:"btn small ghost",onclick:()=>openLink(OFFICIAL[def.id][0][1])},`Get ${OFFICIAL[def.id][0][0]} ↗`):null;
      box.append(fixRow(emu,sc.emulator?"Found":"Not found yet",!!sc.emulator,find,sc.emulator?null:get));
      const sugg=det&&det.systems&&det.systems[def.id]?det.systems[def.id].romDirs.filter(d=>!sc.romDirs.includes(d)):[];
      const add=el("button",{type:"button",class:"btn small"+(sc.romDirs.length?"":" primary"),onclick:async()=>{const p=await api.pick({kind:"folder",title:`Choose the folder with your ${def.name} games`}).catch(()=>null);if(p&&!sc.romDirs.includes(p)){sc.romDirs.push(p);changed();render()}}},sc.romDirs.length?"Add another":"Add folder");
      box.append(fixRow("Games",sc.romDirs.length?sc.romDirs.map(shortPath).join(", "):`The folder with your ${def.name} games (subfolders count too)`,!!sc.romDirs.length,add));
      sugg.forEach(d=>box.append(fixRow("Found games here",shortPath(d),true,el("button",{type:"button",class:"btn small",onclick:()=>{sc.romDirs.push(d);changed();render()}},"Use it"))));
    }else if(n.kind==="steam"){
      box.append(el("div",{class:"wz2-fh"},el("span",{class:"wz2-fname",text:"Steam"}),el("button",{type:"button",class:"btn small ghost",onclick:()=>{cfg.steam.enabled=false;changed();render()}},"No shelf for now")));
      box.append(fixRow("Steam folder","The folder that has steam.exe in it",false,el("button",{type:"button",class:"btn small primary",onclick:async()=>{const p=await api.pick({kind:"folder",title:"Choose your Steam folder"}).catch(()=>null);if(p){cfg.steam.path=p;changed();render()}}},"Find it…")));
    }else{const {def,c}=n;
      box.append(el("div",{class:"wz2-fh"},el("span",{class:"wz2-fname",text:def.name}),el("button",{type:"button",class:"btn small ghost",onclick:()=>{c.enabled=false;changed();render()}},"Skip")));
      box.append(fixRow(def.name,"Not found yet",false,el("button",{type:"button",class:"btn small primary",onclick:async()=>{const p=await api.pick({kind:"file",title:`Find ${def.exeNames[0]}`,filters:[{name:def.name,extensions:["exe"]}]}).catch(()=>null);if(p){c.path=p;changed();render()}}},"Find it…"),
        OFFICIAL[def.id]?el("button",{type:"button",class:"btn small ghost",onclick:()=>openLink(OFFICIAL[def.id][0][1])},`Get ${def.name} ↗`):null));
    }
    b.append(box);
  }
  // (beta.7) Set up for me: one switch for every emulator Boxshelf knows how to set up (main/emusetup.js)
  if(winWizard()&&api.emuSetupAuto&&setupKinds().length){if(!cfg.emuSetup)cfg.emuSetup={auto:true,graphics:"auto",resume:false,pending:[]};
    b.append(el("section",{class:"wz2-fix wz2-auto"},
      el("div",{class:"wz2-fh"},el("span",{class:"wz2-fname",text:"Set up my emulators for me"}),el("span",{class:"wz2-rec",text:"Recommended"})),
      el("p",{class:"hint-text",text:`${setupKinds().join(", ")}: your controller mapped, the Home menu's Save and Load keys, one BIOS folder and graphics that suit this PC. Emulators you've used before are left as they are. Everything is backed up first, and Settings → each console → Set up for me can undo it.`}),
      toggle("Set them up when I finish",cfg.emuSetup.auto!==false,v=>{cfg.emuSetup.auto=!!v;changed()},"wz-emusetup")))}
}
// the emulators of the picked consoles that Set up for me knows (RetroArch, Dolphin, DuckStation, PCSX2, PPSSPP)
const SETUP_KIND=[[/retroarch/i,"RetroArch"],[/dolphin/i,"Dolphin"],[/duckstation/i,"DuckStation"],[/pcsx2/i,"PCSX2"],[/ppsspp/i,"PPSSPP"]];
function setupKinds(){const out=new Set();for(const c of info.consoles){const sc=cfg.systems[c.id];if(!sc||sc.enabled===false||!sc.emulator)continue;const n=String(sc.emulator).split(/[\\/]/).pop();const k=SETUP_KIND.find(([re])=>re.test(n));if(k)out.add(k[1])}return [...out]}
// Box art: whether to look art up online (game titles are sent, nothing else), and in which style. The style is the Art
// Studio's own: Original = each game's own release (the built-in default), One region = Box art region, Sharpest = the
// HD shelf style for every picked console (set when setup finishes, first run only: an existing Studio style is never
// replaced). Backs / spines / discs no source has are made (ui.makeBacks); manuals are their own switch.
const LOOKS=[["original","Original boxes","The real box from each game's own release.","Recommended"],["region","One region","Every box from the region you pick: USA, Europe or Japan.",""],["hd","Sharpest art","The highest-resolution pictures, and fan-made posters where no box scan exists.",""]];
function lookNow(){if(wizLook)return wizLook;const st=cfg.art&&cfg.art.styles||{};if(Object.values(st).some(s=>s&&s.preset==="hd"))return "hd";return ["usa","eur","jpn"].includes(cfg.ui.artRegion)?"region":"original"}
function setLook(v){wizLook=v;if(v==="region"){if(!["usa","eur","jpn"].includes(cfg.ui.artRegion))cfg.ui.artRegion="usa"}else cfg.ui.artRegion="auto";changed();render()}
// (1.82) First run: downloading art is a plain choice (Yes / Not now). Opening the page used to turn it on, so clicking
// Next agreed to send game titles online; now nothing goes online unless Yes is pressed (Next with no answer is Not now).
let artChosen=null;
function artLookSection(b){
  if(artChosen===null&&!(info.config&&info.config.setupDone)&&cfg.ui.autoCovers!==true){
    head(b,"Step 3 of 3","Download box art?","Boxshelf can find the covers, backs, spines, discs and manuals for your games online. To find them it sends your game titles (and GameCube / Wii disc IDs) to LaunchBox, libretro, GameTDB and the Super Mario Wiki. Nothing else about you or your PC is sent.");
    b.append(el("div",{class:"d-actions"},
      el("button",{type:"button",class:"btn primary","data-art":"yes",onclick:()=>{artChosen=true;cfg.ui.autoCovers=true;if(cfg.ui.autoManuals==null)cfg.ui.autoManuals=true;if(!pvTouched&&api.previewFind&&!(window.bsPlatform&&bsPlatform.os==="android"))cfg.ui.previews="shelf"; /* (beta.9: Android previews stay off until chosen) */changed();render()}},"Yes, download box art"),
      el("button",{type:"button",class:"btn","data-art":"no",onclick:()=>{artChosen=false;cfg.ui.autoCovers=false;cfg.ui.autoManuals=false;changed();stepBy(1)}},"Not now")));
    b.append(el("p",{class:"wz2-foot",text:"You can change this any time in Settings → Box art & manuals."}));
    return;
  }
  const on=cfg.ui.autoCovers===true,look=lookNow();
  head(b,"Step 3 of 3","How should your boxes look?","Boxshelf finds the covers, backs, spines and discs for your games online.");
  b.append(card("","",toggle("Download box art automatically",on,v=>{cfg.ui.autoCovers=v;render()},"wz2-ac"),
    el("div",{class:"hint-text",text:"Sends your game titles (and GameCube / Wii disc IDs) to LaunchBox, libretro, GameTDB and the Super Mario Wiki to find pictures (SteamGridDB too, if you add a key later). Nothing else about you or your PC is sent."})));
  const g=el("div",{class:"wz2-looks"+(on?"":" off")});
  LOOKS.forEach(([id,t,d,tag])=>g.append(el("button",{type:"button",class:"wz2-look"+(look===id?" on":""),role:"radio","aria-checked":String(look===id),disabled:!on,onclick:()=>setLook(id)},
    el("span",{class:"wz2-art "+id,"aria-hidden":"true"}),el("span",{class:"wz2-name",text:t}),el("span",{class:"wz2-st",text:d}),tag?el("span",{class:"wz2-tag",text:tag}):null)));
  b.append(g);
  if(on&&look==="region"){const r=el("div",{class:"wz2-regions"});[["usa","USA"],["eur","Europe"],["jpn","Japan"]].forEach(([k,n])=>r.append(el("button",{type:"button",class:"btn small"+(cfg.ui.artRegion===k?" primary":""),onclick:()=>{cfg.ui.artRegion=k;changed();render()}},n)));b.append(r)}
  b.append(card("","",toggle("Complete the cases",cfg.ui.makeBacks!==false,v=>{cfg.ui.makeBacks=v},"wz2-mb"),el("div",{class:"hint-text",text:"Backs, spines and discs too. Boxshelf makes any that no source has, and swaps in a real scan when one turns up."}),
    toggle("Download manuals",cfg.ui.autoManuals===true,v=>{cfg.ui.autoManuals=v},"wz2-am"),el("div",{class:"hint-text",text:"Game manuals, where they're available, to read from the game's case."})));
  if(api.previewFind)b.append(pvCard());
  const more=el("details",{class:"adv"},el("summary",{},"More options"));more.append(artLibCard("Where to keep box art & manuals"));b.append(more);
  b.append(el("p",{class:"wz2-foot",text:"You can change any console, or a single game, later in the Art Studio."}));
}
/* ---------- (beta.9) Accounts: the Windows first run's optional sign-ins and keys ----------
   The same ones Settings → Accounts & services keeps (ra.user / ra.key, keys.*), saved the same way (main encrypts them).
   Each card says what it adds, links to where its free key is made, and tests it. With a controller, A on a box (or its
   Type button) brings up an on-screen keyboard; B puts it away. Skipping the page leaves everything as it was. */
const ACCTS=[
  {id:"ra",name:"RetroAchievements",what:"Achievements and your progress on each game's details, and a summary after you play.",
    fields:[["Username","ra.user",false],["Web API key","ra.key",true]],link:"https://retroachievements.org/settings",get:"Get your key",how:"Sign in, then Settings → Keys → Web API Key."},
  {id:"steamgriddb",name:"SteamGridDB",what:"Community-made posters for your games in the Art Studio, next to the real box scans.",
    fields:[["API key","keys.steamgriddb",true]],link:"https://www.steamgriddb.com/profile/preferences/api",get:"Get a free key",how:"Sign in with Steam (free), then Preferences → API."},
  {id:"screenscraper",name:"ScreenScraper",what:"Box backs, spines, and disc or cartridge art by region, in the Art Studio.",
    fields:[["Username","keys.ssUser",false],["Password","keys.ssPass",true]],link:"https://www.screenscraper.fr/membreinscription.php",get:"Make a free account",how:"A free account allows a set number of look-ups a day."},
  {id:"fanart",name:"fanart.tv",what:"Official disc art and logos for the museum's movie shelves.",
    fields:[["API key","keys.fanart",true]],link:"https://fanart.tv/get-an-api-key/",get:"Get a free key",how:"A free personal key.",more:true}];
const acctSt={},acctBusy={};let acctOsk=null,acctShift=false,acctAsked=false,acctMore=false; // acctOsk: the box the on-screen keyboard types into ("ra.key"…)
const cget=p=>{let o=cfg;for(const k of p.split(".")){if(o==null||typeof o!=="object")return "";o=o[k]}return o==null?"":String(o)};
const cset=(p,v)=>{const ks=p.split(".");let o=cfg;for(let i=0;i<ks.length-1;i++){if(o[ks[i]]==null||typeof o[ks[i]]!=="object")o[ks[i]]={};o=o[ks[i]]}o[ks[ks.length-1]]=v};
const acctFilled=s=>s.fields.every(([,p])=>!!cget(p).trim());
const acctAny=()=>ACCTS.some(s=>s.fields.some(([,p])=>!!cget(p).trim()));
const onAcct=()=>!!(steps[step]&&steps[step].id==="accounts");
function acctOkText(s,st){if(s.id==="ra")return st.points?`Connected · ${Number(st.points).toLocaleString()} pts`:"Connected";
  if(s.id==="screenscraper")return st.left!=null?`Working · ${st.left} look-ups left today`:"Working";return "Key works"}
function acctSection(b){
  head(b,"Optional","Connect your accounts","Free sign-ins that add more to Boxshelf. Fill in any you like, or skip this page: they're all in Settings → Accounts & services later.");
  // run again from Settings: what was tested before shows on its card
  if(!acctAsked&&api.apiStatus){acctAsked=true;Promise.resolve(api.apiStatus()).then(st=>{if(!st)return;let any=false;
    for(const s of ACCTS){const x=st[s.id];if(x&&(x.state==="ok"||x.state==="fail")&&!acctSt[s.id]){acctSt[s.id]=x;any=true}}if(any&&onAcct())render()}).catch(()=>{})}
  ACCTS.filter(s=>!s.more).forEach(s=>b.append(acctCard(s)));
  const more=ACCTS.filter(s=>s.more);
  if(more.length){const d=el("details",{class:"adv wz2-acct-more"},el("summary",{},"More services"));if(acctMore)d.open=true;d.addEventListener("toggle",()=>{acctMore=d.open});more.forEach(s=>d.append(acctCard(s)));b.append(d)}
  b.append(el("p",{class:"wz2-foot",text:"Keys are stored encrypted on this PC and sent only to their own service."}));
}
function acctCard(s){
  const st=acctSt[s.id],busy=acctBusy[s.id];
  const badge=busy?el("span",{class:"badge",text:"Checking…"}):st&&st.state==="ok"?el("span",{class:"badge ok",text:acctOkText(s,st)}):st&&st.state==="fail"?el("span",{class:"badge bad",text:"Didn't work"}):null;
  const sec=el("section",{class:"card grp wz2-acct","data-svc":s.id}),box=el("div",{class:"grp-box"});
  sec.append(el("div",{class:"grp-head"},el("div",{class:"grp-title"},el("span",{text:s.name}),badge),el("div",{class:"grp-d",text:s.what})));
  // a box changed after a test: its old answer no longer applies
  const stale=()=>{if(!acctSt[s.id])return;delete acctSt[s.id];const x=sec.querySelector(".grp-title .badge");if(x)x.remove();const m=sec.querySelector(".miss");if(m)m.remove()};
  s.fields.forEach(([label,p,secret])=>{const typing=acctOsk===p;
    const inp=el("input",{class:"text",type:secret&&!typing?"password":"text",value:cget(p),spellcheck:"false",autocomplete:"off",placeholder:"Not set","aria-label":`${s.name} ${label.toLowerCase()}`,"data-osk":p});
    inp.addEventListener("input",()=>{cset(p,inp.value);stale();changed();renderFoot()});
    inp.addEventListener("change",()=>{const v=inp.value.trim();inp.value=v;cset(p,v);changed()}); // (a pasted key often brings a space along)
    inp.addEventListener("keydown",e=>{if(e.key==="Enter"){e.preventDefault();inp.blur();acctTest(s)}});
    const kb=el("button",{type:"button",class:"btn small wz2-kb","aria-label":typing?"Hide the keyboard":`Type the ${label.toLowerCase()}`,onclick:()=>typing?acctOskClose():acctOskOpen(p)},typing?"Hide keyboard":"⌨ Type");
    // Paste: most keys are copied from the service's page in the browser (Ctrl+V and right-click → Paste work too)
    const paste=api.clipboardText?el("button",{type:"button",class:"btn small wz2-paste","data-paste":p,"aria-label":`Paste the ${label.toLowerCase()}`,onclick:()=>acctPaste(inp,p,stale)},"Paste"):null;
    box.append(grpRow(label,"",null,el("div",{class:"row"},inp,paste,kb)));
    if(typing)box.append(el("div",{class:"grp-item"},acctKeys(inp,p,stale)))});
  if(st&&st.state==="fail")box.append(el("div",{class:"miss",text:"That didn't work: "+(st.msg||"not accepted")}));
  const get=el("button",{type:"button",class:"btn small ghost",title:s.link,onclick:()=>openLink(s.link)},s.get+" ↗");
  const test=el("button",{type:"button",class:"btn small","data-test":s.id,disabled:!!busy,onclick:()=>acctTest(s)},busy?"Checking…":"Test");
  box.append(grpRow("",s.how,null,el("div",{class:"row-btns"},get,test)));
  sec.append(box);return sec}
// the on-screen keyboard (plain buttons: the D-pad / stick and A reach every key). Shift gives capitals, and the symbols over the digits
const ACCT_KEYS=[["1","2","3","4","5","6","7","8","9","0"],["q","w","e","r","t","y","u","i","o","p"],["a","s","d","f","g","h","j","k","l","@"],["z","x","c","v","b","n","m",".","_","-"]];
const ACCT_SHIFT={"1":"!","2":"#","3":"$","4":"%","5":"&","6":"*","7":"+","8":"=","9":"(","0":")","@":"?",".":",","_":"~","-":"/"};
function acctKeys(inp,p,stale){
  const put=v=>{cset(p,v);inp.value=v;stale();changed();renderFoot()};
  const ch=c=>acctShift?(ACCT_SHIFT[c]||c.toUpperCase()):c;
  const k=(label,run,cls,aria)=>el("button",{type:"button",class:"ia-key"+(cls?" "+cls:""),"aria-label":aria||label,onclick:e=>{e.preventDefault();run()}},label);
  return el("div",{class:"ia-osk wz2-osk",role:"group","aria-label":"On-screen keyboard"},
    ...ACCT_KEYS.map(row=>el("div",{class:"ia-krow"},...row.map(c=>{const t=ch(c);return k(t,()=>put(cget(p)+t))}))),
    el("div",{class:"ia-krow"},k(acctShift?"abc":"ABC",()=>{acctShift=!acctShift;render()},"mid",acctShift?"Small letters":"Capitals and symbols"),k("Space",()=>put(cget(p)+" "),"wide"),
      k("⌫",()=>put(cget(p).slice(0,-1)),"mid","Delete"),k("Clear",()=>put(""),"mid"),k("Done",()=>acctOskClose(),"go","Done typing")))}
async function acctPaste(inp,p,stale){let t="";try{t=String(await api.clipboardText()||"").trim()}catch(e){}
  if(!t){Shelf.toast("Nothing to paste: copy the key first");return}
  cset(p,t);inp.value=t;stale();changed();renderFoot()}
function acctOskOpen(p){acctOsk=p;acctShift=false;render();const k=$("suBody").querySelector(".wz2-osk .ia-key");if(k)focusEl(k)}
function acctOskClose(){const p=acctOsk;acctOsk=null;render();const i=p&&$("suBody").querySelector(`input[data-osk="${p}"]`);if(i)focusEl(i)}
async function acctTest(s){
  if(acctBusy[s.id])return;
  if(!acctFilled(s)){Shelf.toast(`Fill in the ${s.fields.map(f=>f[0].toLowerCase()).join(" and ")} first`);return}
  acctBusy[s.id]=true;if(onAcct())render();
  try{clearTimeout(saveT);await persist();acctSt[s.id]=api.testApi?await api.testApi(s.id):{state:"fail",msg:"Testing isn't available here"}}
  catch(e){acctSt[s.id]={state:"fail",msg:e.message||String(e)}}
  acctBusy[s.id]=false;const st=acctSt[s.id]||{};
  Shelf.toast(st.state==="ok"?`${s.name} connected`:"That didn't work: "+(st.msg||"not accepted"));
  if(onAcct())render()}
// setup finishing (first run): "Sharpest" becomes the HD style for every picked console that has no style yet. Returns
// those consoles: (beta.10) finish() sets each through api.artStyle, the path main (and Android's art store) honours: a
// config save keeps main's own cfg.art, so the copy written here alone was dropped. Original / One region write no style
// (they're the default look, which follows cfg.ui.artRegion, saved as usual)
function applyLook(){
  if(info.config&&info.config.setupDone)return [];if(lookNow()!=="hd"||cfg.ui.autoCovers!==true)return [];
  const art=cfg.art&&typeof cfg.art==="object"?cfg.art:{styles:{},locks:{}};const styles={...(art.styles||{})},ids=[];
  for(const c of info.consoles){const sc=cfg.systems[c.id];if(sc&&sc.enabled!==false&&!styles[c.id]){styles[c.id]={preset:"hd",region:"usa",parts:{}};ids.push(c.id)}}
  cfg.art={...art,styles};return ids
}

function consoleSection(b,def){
  const c=cfg.systems[def.id];
  head(b,def.emulatorName,def.name,CONSOLE_HELP[def.id]);
  b.append(card("Shelf","",toggle(`Show the ${def.name} shelf`,c.enabled,v=>{c.enabled=v},"en-"+def.id)));

  // Emulator
  const share=def.id==="wii"&&!c.emulator&&cfg.systems.gc.emulator?[el("button",{type:"button",class:"btn small",onclick:()=>{c.emulator=cfg.systems.gc.emulator;changed();render()}},"Use GameCube's Dolphin")]:
              def.id==="gc"&&!c.emulator&&cfg.systems.wii.emulator?[el("button",{type:"button",class:"btn small",onclick:()=>{c.emulator=cfg.systems.wii.emulator;changed();render()}},"Use Wii's Dolphin")]:[];
  b.append(card("Emulator","",pathField(`${def.emulatorName} program`,c.emulator,{want:"file",set:v=>{c.emulator=v},pickTitle:`Find ${def.exeNames[0]}`,filters:[{name:def.emulatorName,extensions:["exe"]}],placeholder:`…\\${def.exeNames[0]}`,extra:share,
    help:det&&det.systems[def.id]&&det.systems[def.id].emulator&&det.systems[def.id].emulator!==c.emulator?`Also found: ${det.systems[def.id].emulator}`:null}),siteLinks(def.id)));

  // Game folders
  const list=el("div",{class:"field folders"});
  if(!c.romDirs.length)list.append(el("div",{class:"hint-text",text:`No folders yet. Add the folder that holds your ${def.name} games (${def.hint||def.extensions.slice(0,4).map(e=>"."+e).join(", ")+"…"}). Subfolders are searched too.`}));
  c.romDirs.forEach((d,i)=>{const st=el("span");list.append(el("div",{class:"folder"},el("span",{class:"p",text:d}),st,el("button",{type:"button",class:"btn small",onclick:()=>{c.romDirs.splice(i,1);if(window.bsPlatform&&bsPlatform.os==="android")dirsGone.add(d);changed();render()}},"Remove")));badge(st,d,"dir")});
  // (beta.10) Android gives a removed folder's permission back at once: it's not offered again as "Found" (added back that
  // way it couldn't be read); picking it again works
  const sugg=det&&det.systems[def.id]?det.systems[def.id].romDirs.filter(d=>!c.romDirs.includes(d)&&!dirsGone.has(d)):[];
  sugg.forEach(d=>list.append(el("div",{class:"suggest"},el("span",{class:"p",text:"Found: "+d}),el("button",{type:"button",class:"btn small",onclick:()=>{c.romDirs.push(d);changed();render()}},"Add"))));
  list.append(el("div",{class:"row"},el("button",{type:"button",class:"btn small",onclick:async()=>{const p=await api.pick({kind:"folder",title:`Choose a ${def.name} games folder`}).catch(()=>null);if(p)dirsGone.delete(p);if(p&&!c.romDirs.includes(p)){c.romDirs.push(p);changed();render()}}},"Add folder")));
  b.append(card("Game folders","",list));

  // Cover art
  b.append(card("Box art & manuals","",wizard?null:el("div",{class:"slider-lbl",text:"Find and download"}),wizard?null:el("div",{class:"row"},el("button",{type:"button",class:"btn small",onclick:()=>{const k=steps.findIndex(x=>x.id==="art");if(k>=0){step=k;render()}}},"Box art"),el("button",{type:"button",class:"btn small",onclick:()=>{manualPick=new Set([def.id]);const k=steps.findIndex(x=>x.id==="library");if(k>=0){step=k;render()}}},"Manuals")),
    pathField("Your own cover art folder (optional)",c.artDir,{want:"dir",optional:true,set:v=>{c.artDir=v},pickTitle:"Choose a cover art folder",help:"Name images after the game file, like Pikmin (USA).png. Add -back for the back cover. Games without art get a generated cover."})));

  b.append(card("When you press Play","",toggle("Boot screen with the game's cover",c.insertAnim!==false,v=>{c.insertAnim=v},"ins-"+def.id),
    el("div",{class:"hint-text",text:"The console's start-up look with the game's cover and a progress line while the emulator opens."}),
    toggle("Your boot video before games",c.bootOnLaunch!==false,v=>{c.bootOnLaunch=v},"boot-"+def.id),
    el("div",{class:"hint-text",text:"Plays the boot video you added for this console (Customize) instead of the boot screen. Without a video, the built-in intro plays only when the boot screen is off."})));
  if(def.id==="gc"&&api.findIpl&&(!window.bsPlatform||bsPlatform.has("dolphinIpl"))){ /* (beta.10) Windows only: Android has no findIpl (stuck on "Checking…"), Linux's Dolphin folders differ */
    const st=el("div",{class:"hint-text",text:"Checking for IPL.bin…"});
    const ic=el("input",{class:"text",type:"number",min:"0",max:"15",step:"0.5",value:String(c.iplCoverSeconds??2.5),style:"max-width:110px;flex:none","aria-label":"Seconds to hide Dolphin when the startup is on"});ic.addEventListener("change",()=>{c.iplCoverSeconds=Math.max(0,Math.min(15,+ic.value||0));changed()});
    const f=card("GameCube startup","",
      toggle("Play the real GameCube startup",c.useIpl,v=>{c.useIpl=v},"ipl-gc"),st,
      el("div",{class:"slider-lbl",text:"IPL.bin folder"}),el("button",{type:"button",class:"btn small",onclick:()=>{persist().then(()=>api.openIplFolder("gc","USA")).catch(e=>Shelf.toast(e.message))}},"Open folder (USA)"),
      el("div",{class:"grp-row"},rowL("Hide Dolphin for","Seconds Dolphin's window stays hidden while the startup plays. Lower shows more of it but may flash Dolphin. Default 2.5."),el("div",{class:"grp-r num"},ic,el("span",{class:"grp-unit",text:"sec"}))),
      toggle("Skip Dolphin's warning pop-ups",c.hideDolphinWarnings,v=>{c.hideDolphinWarnings=v},"ipl-warn"),el("div",{class:"hint-text",text:"Like \"IPL file is not a known good dump\"."}));
    b.append(f);
    persist().then(()=>api.findIpl("gc")).then(r=>{
      st.textContent="";
      if(r.found.length)st.append(el("span",{class:"badge ok",text:"Found: "+r.found.map(x=>x.region).join(", ")}),document.createTextNode(" "+r.found[0].file));
      else st.append(el("span",{class:"badge warn",text:"No IPL.bin yet"}),document.createTextNode(` Put the IPL.bin dumped from your GameCube in ${r.usaFolder}\\ (use EUR or JAP for those regions). When it's on, your boot video is skipped for GameCube.`));
    }).catch(()=>{st.textContent="Set the Dolphin program first."});
  }

  // Advanced
  const adv=el("details",{class:"adv"},el("summary",{},"Launch options"));
  const ga=el("input",{class:"text",type:"text",value:c.gameArgs,spellcheck:"false","aria-label":"Game launch options"});ga.addEventListener("change",()=>{c.gameArgs=ga.value;changed()});
  const ea=el("input",{class:"text",type:"text",value:c.emulatorArgs,spellcheck:"false","aria-label":"Emulator-only options"});ea.addEventListener("change",()=>{c.emulatorArgs=ea.value;changed()});
  const cs=el("input",{class:"text",type:"number",min:"0",max:"30",step:"0.5",value:String(c.coverSeconds??def.coverSeconds),style:"max-width:110px;flex:none","aria-label":"Seconds to hide the emulator while it starts"});cs.addEventListener("change",()=>{c.coverSeconds=Math.max(0,Math.min(30,+cs.value||0));changed()});
  adv.append(
    el("div",{class:"field"},el("div",{class:"lbl"},"Game launch options"),el("div",{class:"row"},ga,el("button",{type:"button",class:"btn small",onclick:()=>{ga.value=c.gameArgs=def.gameArgs;changed()}},"Reset")),el("div",{class:"hint-text",text:`{rom} is replaced with the game file. Default: ${def.gameArgs}`})),
    el("div",{class:"field"},el("div",{class:"lbl"},"Options when opening the emulator on its own"),el("div",{class:"row"},ea)),
    el("div",{class:"field"},el("div",{class:"lbl"},"Hide the emulator while it starts"),el("div",{class:"row"},cs,el("span",{class:"hint-text",text:"seconds"})),el("div",{class:"hint-text",text:`Boxshelf's loading screen stays in front this long so you don't see ${def.emulatorName}'s window before the game goes fullscreen. Raise it if you still see a flash, 0 turns it off. Default: ${def.coverSeconds}.`})),
    el("div",{class:"d-actions"},el("button",{type:"button",class:"btn small",disabled:!c.emulator,onclick:()=>api.launchEmulator(def.id).catch(e=>Shelf.toast(e.message))},`Test: open ${def.emulatorName}`)));
  b.append(card("Advanced","",adv));
}

function steamSection(b){
  head(b,"PC games","Steam","Shows the Steam games installed on this PC with their real covers, and launches them through Steam.");
  b.append(card("Steam","",toggle("Show installed Steam games",cfg.steam.enabled,v=>{cfg.steam.enabled=v},"steam-en"),
    pathField("Steam folder",cfg.steam.path,{want:"dir",set:v=>{cfg.steam.path=v},pickTitle:"Choose your Steam folder",placeholder:"C:\\Program Files (x86)\\Steam",help:"The folder that contains steam.exe."}),siteLinks("steam")));
}
function mediaSection(b){
  head(b,"Media","Stremio & Nuvio","Adds tiles for your streaming apps on the Media shelf.");
  for(const a of info.apps){
    const c=cfg.apps[a.id];
    b.append(card(a.name,"",toggle(`Show ${a.name}`,c.enabled,v=>{c.enabled=v},"app-"+a.id),
      pathField(`${a.name} program`,c.path,{want:"file",set:v=>{c.path=v},pickTitle:`Find ${a.exeNames[0]}`,filters:[{name:a.name,extensions:["exe"]}],placeholder:`…\\${a.exeNames[0]}`}),siteLinks(a.id),
      toggle("Go fullscreen after start",c.fullscreen!==false,v=>{c.fullscreen=v},"fs-"+a.id),el("div",{class:"hint-text",text:"Presses F11 once the app is open."}),
      toggle("Controller as mouse",c.padMouse===true,v=>{c.padMouse=v},"pm-"+a.id),
      el("div",{class:"hint-text",text:"The left stick moves a pointer and A clicks, for apps with no controller support. Leave it off when the app has its own (Stremio: Settings → Enable gamepad)."})));
  }
}
// ---- Settings, grouped the way a console's settings app is: one topic per page, a card per control with a one-line note ----
// the classic pages Settings still opens (box art planner, manual downloads, the Internet Archive finder); everything else
// lives in Settings now, so the old Display / Sound / Controller / Museum / Connections / Startup pages are gone
const SETTINGS_PAGES=[["art","Box art"],["library","Manuals"],["reading","Magazines & guides"]];
// Settings rows, like a system settings app: a section title, then a rounded group of rows with the name (and a one-line
// hint) on the left and the control on the right, thin lines between. Longer help hides behind a small "?".
function splitNote(note){if(!note)return ["",""];const m=/^(.{0,140}?[.!?])(\s+|$)([\s\S]*)$/.exec(note);return m?[m[1],m[3].trim()]:[note,""]}
function helpBtn(more){if(!more)return ["",""];const tip=el("div",{class:"grp-more",hidden:true,text:more});const b=el("button",{type:"button",class:"grp-help","aria-expanded":"false",onclick:e=>{e.preventDefault();tip.hidden=!tip.hidden;b.setAttribute("aria-expanded",String(!tip.hidden));b.textContent=tip.hidden?"Learn more":"Show less"}},"Learn more");return [b,tip]}
const segLen=x=>x.classList.contains("seg")?[...x.children].reduce((n,c)=>n+c.textContent.length+4,0):0;
const compact=x=>x&&x.classList&&(x.classList.contains("seg")||x.classList.contains("btn")||x.classList.contains("row-btns")||(x.classList.contains("row")&&x.children.length<=3&&[...x.children].every(c=>c.classList&&c.classList.contains("btn"))));
function rowL(title,short,h){if(short&&short.nodeType){short.classList.add("grp-d");short.classList.remove("hint-text");return el("div",{class:"grp-l"},el("div",{class:"grp-t",text:title}),short)}
  return el("div",{class:"grp-l"},el("div",{class:"grp-t",text:title}),short||(h&&h[0])?el("div",{class:"grp-d"},short?el("span",{text:short+" "}):"",h&&h[0]||""):"",h&&h[1]||"")}
const isSw=x=>x&&x.classList&&x.classList.contains("switch"),isLbl=x=>x&&x.classList&&x.classList.contains("slider-lbl"),isHint=x=>x&&x.classList&&x.classList.contains("hint-text");
// one row: words on the left, the control on the right (or underneath when it's wide)
function grpRow(title,short,h,ctl){
  if(isSw(ctl)){[...ctl.childNodes].forEach(n=>{if(n.nodeType===3)n.remove()});ctl.classList.add("bare");ctl.querySelector("input").setAttribute("aria-label",title);
    const row=el("label",{class:"grp-row is-sw"},rowL(title,short,h),el("div",{class:"grp-r"},ctl));row.addEventListener("click",e=>{if(e.target.closest(".grp-help"))e.preventDefault()});return row}
  return el("div",{class:"grp-row"+(segLen(ctl)>58||!compact(ctl)?" stack":"")},rowL(title,short,h),el("div",{class:"grp-r"},ctl))}
function card(title,note,...controls){
  const [short,more]=splitNote(note),ctl=controls.flat().filter(Boolean),h=helpBtn(more);
  const sec=el("section",{class:"card grp"}),box=el("div",{class:"grp-box"});
  // pair up: "label, control" and "switch, its hint" become single rows
  const rows=[];for(let i=0;i<ctl.length;i++){const x=ctl[i],n=ctl[i+1];
    if(isLbl(x)&&n){rows.push({t:x.textContent,c:n});i++}
    else if(isSw(x)){const r={t:x.textContent.trim(),c:x};if(isHint(n)){r.d=n;i++}rows.push(r)}
    else rows.push({c:x})}
  let headed=false;
  // the first row carries the card's title and note when it's a switch or the card's only short control
  if(rows.length&&(isSw(rows[0].c)||rows.length===1&&compact(rows[0].c)&&!rows[0].t)){const r=rows.shift();box.append(grpRow(isSw(r.c)&&r.t?r.t:title,short||r.d,short?h:null,r.c));if(short&&r.d)box.append(el("div",{class:"grp-item"},r.d));headed=true}
  else sec.append(el("div",{class:"grp-head"},el("div",{class:"grp-title",text:title}),short||h[0]?el("div",{class:"grp-d"},short?el("span",{text:short+" "}):"",h[0]):"",h[1]));
  rows.forEach(r=>{if(r.c.classList&&r.c.classList.contains("grp-row"))box.append(r.c);else if(r.t||isSw(r.c))box.append(grpRow(r.t||"",r.d||"",null,r.c));else box.append(el("div",{class:"grp-item"},r.c))});
  sec.append(box);return sec}

/* ---------- Settings → Manuals: the same three screens as Box art (choose, progress, finish) ---------- */
let manualPick=null,manBox=null;
const manState={screen:"plan",cov:null,prog:{},stage:"",rep:null,err:null,started:0,ids:[]};
if(api.onCoversProgress)api.onCoversProgress(p=>{if(manState.screen!=="run")return;if(p.systemId)manState.prog[p.systemId]=p;if(p.stage)manState.stage=p.stage;manPaint()});
// the bottom of the Box art / Manuals plans: what Start will do, and Start
function startBar(sum,btn,note){sum.classList.add("grp-t");return el("section",{class:"card grp start-bar"},el("div",{class:"grp-box"},el("div",{class:"grp-row"},el("div",{class:"grp-l"},sum,note?el("div",{class:"grp-d",text:note}):""),el("div",{class:"grp-r"},btn))))}
function librarySection(b){
  head(b,"Library","Get manuals","Pick the consoles to look for manuals on. They come from the Internet Archive and GamesDatabase.org; manuals you already have are kept.");
  manBox=el("div",{class:"art-box"});b.append(manBox);
  if(manState.screen!=="run")manState.screen=manState.rep||manState.err?"done":"plan";
  if(manState.screen==="plan"){manState.cov=null;api.manualCoverage&&api.manualCoverage().then(l=>{manState.cov=l||[];manPaint()}).catch(()=>{manState.cov=[];manPaint()})}
  manPaint();
  // which manuals were added or swapped for sharper English copies lately (from the manual collections)
  if(api.recentManuals){const box=el("details",{class:"field man-recent-box"},el("summary",{class:"lbl",text:"Recent manual updates"}),el("div",{class:"hint-text",text:"Loading…"}));b.append(box);
    api.recentManuals().then(list=>{box.lastChild.remove();if(!list||!list.length){box.append(el("div",{class:"hint-text",text:"None yet. Manuals are checked in the background a minute after Boxshelf starts; older or non-English ones are swapped for sharp English copies when one is found (the old pages are kept in covers\\.manual-originals)."}));return}
      const ul=el("div",{class:"man-recent"});list.forEach(x=>{const d=new Date(x.at);ul.append(el("div",{class:"man-recent-row"},el("b",{text:x.title}),` ${x.system?"· "+x.system+" ":""}· ${x.upgraded?(x.w>=x.oldW?`sharper copy (${x.oldW} → ${x.w} px wide)`:`English edition (was ${x.oldW} px, now ${x.w} px)`):"added"} · ${x.pages} pages · ${d.toLocaleDateString()}`))});box.append(ul)}).catch(()=>{box.lastChild.textContent="Couldn't read the list."})}
}
// repainting a panel (after a tick, a toggle…) replaced the button you were on, so the controller fell back to the
// sidebar: put the highlight back on the same control (matched by its label, else its position)
function keepFocus(box,paint){const ae=document.activeElement,inBox=box&&ae&&box.contains(ae);let key=null,idx=-1;
  if(inBox){key=ae.getAttribute("aria-label")||ae.id||ae.textContent.trim();idx=[...box.querySelectorAll("button,input,summary")].indexOf(ae)}
  paint();if(!inBox||!box.isConnected)return;const all=[...box.querySelectorAll("button:not([disabled]),input:not([disabled]),summary")];
  const hit=all.find(e=>(e.getAttribute("aria-label")||e.id||e.textContent.trim())===key)||all[Math.min(Math.max(idx,0),all.length-1)];if(hit)hit.focus({preventScroll:true})}
function manPaint(){keepFocus(manBox,manPaint0)}
function manPaint0(){if(!manBox||!manBox.isConnected)return;manBox.innerHTML="";const S=manState;if(S.screen==="plan")return manPaintPlan();if(S.screen==="run")return manPaintRun();manPaintDone()}
function manPaintPlan(){
  const S=manState,cov=S.cov;if(!cov){manBox.append(el("div",{class:"hint-text",text:"Checking what you have…"}));return}
  const sys=cov.filter(c=>c.total>0);if(!sys.length){manBox.append(card("No games yet","Add a games folder for a console first (Settings → Consoles), then come back here."));return}
  if(!manualPick)manualPick=new Set(sys.map(c=>c.id));
  // the same look as Get box art: a card per console with how many manuals it has, ticked = in this download
  manBox.append(el("div",{class:"wz2-bar"},el("span",{class:"wz2-bart",text:`${sys.filter(c=>manualPick.has(c.id)).length} of ${sys.length} consoles`}),
    el("button",{type:"button",class:"btn small",onclick:()=>{manualPick=new Set(sys.map(c=>c.id));manPaint()}},"Select all"),
    el("button",{type:"button",class:"btn small ghost",onclick:()=>{manualPick=new Set();manPaint()}},"None")));
  const grid=el("div",{class:"wz2-grid"});
  sys.forEach(c=>{const on=manualPick.has(c.id),miss=c.total-c.have;
    grid.append(el("button",{type:"button",class:"wz2-card big"+(on?" on":""),role:"checkbox","aria-checked":String(on),"data-man":c.id,onclick:()=>{on?manualPick.delete(c.id):manualPick.add(c.id);manPaint()}},
      el("span",{class:"wz2-tick","aria-hidden":"true"}),el("span",{class:"wz2-name",text:c.name}),
      el("span",{class:"wz2-st"+(miss?"":" ok"),text:miss?`${c.have} of ${c.total} manuals · ${miss} missing`:`All ${c.total} manuals`}),
      c.low?el("span",{class:"wz2-st",text:`${c.low} small scan${c.low===1?"":"s"}`}):null))});
  manBox.append(grid);
  manBox.append(card("","",toggle("Only sure English manuals",cfg.ui.manualSure!==false,v=>{cfg.ui.manualSure=v;manPaint()},"man-sure"),el("div",{class:"hint-text",text:"Off takes the best match for every game, even unsure ones."}),
    toggle("Fetch manuals for new games",cfg.ui.autoManuals!==false,v=>{cfg.ui.autoManuals=v},"gen-am"),el("div",{class:"hint-text",text:"In the background, after new games turn up. Only sure English manuals."})));
  const where=el("details",{class:"adv"},el("summary",{},"Where manuals are kept"));where.append(artLibCard());manBox.append(where);
  const chosen=sys.filter(c=>manualPick.has(c.id)),missing=chosen.reduce((a,c)=>a+c.total-c.have,0);
  manBox.append(startBar(chosen.length?el("span",{},el("b",{text:`${chosen.length} console${chosen.length>1?"s":""}`}),` · ${missing?`up to ${missing} missing manual${missing===1?"":"s"} to find`:"every game already has one"}`):el("span",{text:"Tick at least one console to start."}),
    el("button",{type:"button",class:"btn primary art-start",disabled:!chosen.length||!missing?"":null,onclick:manStart},"Start download"),"Manuals you already have are kept. Searching sends your game titles to those sites."));
}
async function manStart(){
  if(coverBusy){Shelf.toast("Another download is running");return}const ids=[...manualPick];if(!ids.length)return;await persist();
  dlRuns++;Object.assign(manState,{screen:"run",prog:{},stage:"Starting…",rep:null,err:null,started:Date.now(),ids,phase:"search"});coverBusy=true;manPaint();
  try{
    const found=await api.findManuals(ids)||[];if(manState.screen!=="run")return;
    const min=window.BoxshelfManMin||7,pick=[],unsure=[],none=[];
    found.forEach(r=>{if(r.have)return;const c=(r.candidates||[])[0];if(!c){none.push(r);return}if(cfg.ui.manualSure!==false&&!(c.score>=min&&/^(eng|en|english|)$/i.test(c.language||""))){unsure.push(r);return}pick.push({r,c})});
    manState.phase="download";manState.stage=pick.length?`Downloading ${pick.length} manual${pick.length===1?"":"s"}…`:"Nothing to download";manPaint();
    const rep=pick.length?await api.downloadManuals(pick.map(({r,c})=>({systemId:r.systemId,file:r.file,id:c.id}))):[];
    const byFile=new Map((rep||[]).map(x=>[x.file,x]));
    manState.rep={found,systems:ids.map(id=>{const name=(manState.cov||[]).find(c=>c.id===id);const mine=found.filter(r=>r.systemId===id);
      return {id,name:name?name.name:id,had:mine.filter(r=>r.have).length,added:pick.filter(p=>p.r.systemId===id&&(byFile.get(p.r.file)||{}).pages).map(p=>[p.r.title,(byFile.get(p.r.file).pages>0?byFile.get(p.r.file).pages+" pages":"PDF")]),
        failed:pick.filter(p=>p.r.systemId===id&&!(byFile.get(p.r.file)||{}).pages).map(p=>p.r.title),unsure:unsure.filter(r=>r.systemId===id).map(r=>r.title),none:none.filter(r=>r.systemId===id).map(r=>r.title)}})};
  }catch(e){manState.err=e&&e.message||String(e)}
  coverBusy=false;manState.screen="done";manPaint();Shelf.toast(manState.err?"Manuals: "+manState.err:"Manual download finished");
}
function manPaintRun(){
  const S=manState,cov=S.cov||[];
  manBox.append(el("div",{class:"art-stage"},el("span",{class:"scanning"})," ",el("b",{text:S.phase==="download"?"Downloading manuals":"Looking for manuals"}),el("span",{class:"art-elapsed",text:" · "+fmtSecs((Date.now()-S.started)/1000)})));
  if(S.phase==="download")manBox.append(el("div",{class:"hint-text",text:S.stage}));
  S.ids.forEach(id=>{const c=cov.find(x=>x.id===id)||{name:id,total:0},p=S.prog[id],tot=p?p.sysTotal:c.total,dn=p?p.sysDone:0,pct=tot?Math.round(dn/tot*100):0;
    manBox.append(el("div",{class:"art-prog"+(p&&dn>=tot&&tot?" done":"")},el("div",{class:"art-prog-top"},el("b",{text:c.name}),el("span",{text:`${dn} / ${tot}`})),el("div",{class:"art-bar"},el("div",{class:"art-fill",style:`width:${S.phase==="download"?100:pct}%`})),el("div",{class:"art-now",text:p?(dn>=tot?"Searched":`Now: ${p.game||""}`):"Waiting"})))});
  if(S.phase!=="download")manBox.append(el("div",{class:"row"},el("button",{type:"button",class:"btn small",onclick:e=>{e.currentTarget.disabled=true;e.currentTarget.textContent="Stopping…";api.cancelManuals&&api.cancelManuals()}},"Cancel")));
  clearTimeout(manPaintRun.t);manPaintRun.t=setTimeout(()=>{if(manState.screen==="run")manPaint()},1000);
}
function manPaintDone(){
  const S=manState,r=S.rep;
  if(S.err)manBox.append(card("Manuals didn't finish",S.err));
  else if(r){const added=r.systems.reduce((a,x)=>a+x.added.length,0),miss=r.systems.reduce((a,x)=>a+x.none.length+x.unsure.length+x.failed.length,0);
    manBox.append(el("div",{class:"art-finish"},el("div",{class:"art-finish-icon",text:"✓"}),el("div",{},el("div",{class:"art-finish-title",text:"All done"}),el("div",{class:"hint-text",text:`${added} manual${added===1?"":"s"} added${miss?`, ${miss} game${miss===1?"":"s"} still without one`:""} · took ${fmtSecs((Date.now()-S.started)/1000)}. They show up when you close Settings.`}))));
    const lst=(row,arr,label,cls)=>{if(!arr.length)return;row.append(el("details",{class:"art-miss "+cls},el("summary",{text:label}),el("div",{class:"art-glist"},...arr.map(x=>Array.isArray(x)?el("div",{class:"art-gl"},el("span",{text:x[0]}),el("small",{text:x[1]})):el("div",{class:"art-gl"},el("span",{text:x}))))))};
    r.systems.forEach(x=>{const row=el("div",{class:"art-res"},el("div",{class:"art-res-top"},el("b",{text:x.name}),el("span",{text:(x.added.length?`${x.added.length} added`:"nothing new")+(x.had?` · ${x.had} already had one`:"")})));
      lst(row,x.added,`${x.added.length} manual${x.added.length===1?"":"s"} added`,"new");lst(row,x.unsure,`${x.unsure.length} unsure match${x.unsure.length===1?"":"es"} skipped`,"up");if(["sw","ps4","ps5"].includes(x.id))lst(row,x.none,`${x.none.length} with no printed manual (the guide is inside the game; Boxshelf makes a booklet instead)`,"");else lst(row,x.none,`${x.none.length} not found`,"");lst(row,x.failed,`${x.failed.length} failed to download`,"");manBox.append(row)});}
  manBox.append(el("div",{class:"row"},el("button",{type:"button",class:"btn primary",onclick:()=>{manState.screen="plan";manState.rep=null;manState.err=null;manState.cov=null;api.manualCoverage&&api.manualCoverage().then(l=>{manState.cov=l||[];manPaint()}).catch(()=>{manState.cov=[];manPaint()});manPaint()}},"Back to the manuals list")));
}
/* ---------- Settings → Box art: pick per console which pictures to fetch, then three clear screens (start, progress, finish) ---------- */
const ART_KINDS=[["front","Front"],["back","Back"],["spine","Spine"],["disc","Disc / cart"]];
let artState={screen:"plan",cov:null,prog:{},rep:null,err:null,started:0};
if(api.onCoversProgress)api.onCoversProgress(p=>{if(artState.screen!=="run")return;if(p.systemId){artState.prog[p.systemId]=p}else artState.stageMsg=p.stage;artPaint()});
let artBox=null;
function artPlan(){const pl=cfg.ui.artPlan;if(pl&&typeof pl==="object")return pl;const o={};(info.consoles||[]).forEach(c=>{o[c.id]=["front","back","spine","disc"]});return o}
function artSetPlan(id,kinds){cfg.ui.artPlan=Object.assign({},artPlan(),{[id]:kinds});artPaint()}
// the parts every ticked console gets (all consoles when none is ticked), in the usual order
// (nothing ticked: the parts last chosen here, remembered for this session, else all four)
let artPartsMem=null;
function artParts(sys,plan){const from=sys.filter(c=>(plan[c.id]||[]).length);if(!from.length)return artPartsMem?[...artPartsMem]:ART_KINDS.map(x=>x[0]);return ART_KINDS.map(x=>x[0]).filter(k=>from.every(c=>(plan[c.id]||[]).includes(k)))}
function artSection(b){
  head(b,"Library","Download missing art now","Pick the consoles and what to get. Pictures you already have are kept, and each console follows its Art Studio style.");
  artBox=el("div",{class:"art-box"});b.append(artBox);
  if(artState.screen!=="run"){artState.screen=artState.rep?"done":"plan"}
  if(!artState.cov||artState.screen==="plan"){artState.cov=null;api.artCoverage&&api.artCoverage().then(c=>{artState.cov=c;artPaint()}).catch(()=>{artState.cov=[];artPaint()})}
  artPaint();
}
function artPaint(){keepFocus(artBox,artPaint0)}
function artPaint0(){
  if(!artBox||!artBox.isConnected)return;artBox.innerHTML="";const S=artState;
  if(S.screen==="plan")return artPaintPlan();if(S.screen==="run")return artPaintRun();return artPaintDone();
}
function artPaintPlan(){
  const S=artState,plan=artPlan(),cov=S.cov;
  if(!cov){artBox.append(el("div",{class:"hint-text",text:"Checking what you have…"}));return}
  const sys=cov.filter(c=>c.total>0);
  if(!sys.length){artBox.append(card("No games yet","Add a games folder for a console first (Settings → Consoles), then come back here."));return}
  const hd=cfg.ui.artHD!==false,chosen=sys.filter(c=>(plan[c.id]||[]).length);
  // what to get, for every ticked console at once (a part is lit when every ticked console gets it); the per-console grid
  // below ("Choose per console") is still there for mixing
  const parts=artParts(sys,plan);
  const setPart=(k,on)=>{const next=ART_KINDS.map(x=>x[0]).filter(x=>x===k?on:parts.includes(x));artPartsMem=next;
    if(chosen.length){const pl=artPlan();chosen.forEach(c=>{const cur=(pl[c.id]||[]).filter(x=>x!==k);if(on)cur.push(k);pl[c.id]=cur.length?cur:[...next]});cfg.ui.artPlan=Object.assign({},pl)}artPaint()};
  // the consoles: a card each, ticked = it's in this download
  const top=el("div",{class:"wz2-bar"},el("span",{class:"wz2-bart",text:`${chosen.length} of ${sys.length} consoles`}),
    el("button",{type:"button",class:"btn small",onclick:()=>{const pl=artPlan(),use=parts.length?parts:ART_KINDS.map(x=>x[0]);sys.forEach(c=>{pl[c.id]=[...use]});cfg.ui.artPlan=Object.assign({},pl);artPaint()}},"Select all"),
    el("button",{type:"button",class:"btn small ghost",onclick:()=>{const pl=artPlan();sys.forEach(c=>{pl[c.id]=[]});cfg.ui.artPlan=Object.assign({},pl);artPaint()}},"None"));
  artBox.append(top);
  const grid=el("div",{class:"wz2-grid"});
  sys.forEach(c=>{const on=(plan[c.id]||[]).length>0,missFront=c.total-c.front,low=hd?ART_KINDS.reduce((a,[k])=>a+(c[k+"Low"]||0),0):0;
    const st=missFront?`${c.front} of ${c.total} covers · ${missFront} missing`:`All ${c.total} covers`,lack=ART_KINDS.slice(1).filter(([k])=>c[k]<c.total).length;
    grid.append(el("button",{type:"button",class:"wz2-card big"+(on?" on":""),role:"checkbox","aria-checked":String(on),"data-art":c.id,onclick:()=>artSetPlan(c.id,on?[]:(parts.length?[...parts]:ART_KINDS.map(x=>x[0])))},
      el("span",{class:"wz2-tick","aria-hidden":"true"}),el("span",{class:"wz2-name",text:c.name}),
      el("span",{class:"wz2-st"+(!missFront&&!lack?" ok":""),text:st}),
      el("span",{class:"wz2-st",text:(lack?"Some backs, spines or discs missing":missFront?"":"Cases complete")+(low?`${lack||missFront?" · ":""}${low} low-res`:"")})))});
  artBox.append(grid);
  // what to get
  const chips=el("div",{class:"wz2-chips"});
  [["front","Covers"],["back","Backs"],["spine","Spines"],["disc","Discs & carts"]].forEach(([k,l])=>{const on=parts.includes(k);
    chips.append(el("button",{type:"button",class:"wz2-chip"+(on?" on":""),role:"checkbox","aria-checked":String(on),onclick:()=>setPart(k,!on)},el("span",{class:"wz2-tick","aria-hidden":"true"}),l))});
  artBox.append(el("div",{class:"wz2-sub",text:"What to get"}),chips);
  artBox.append(card("","",toggle("Sharpest scans first",hd,v=>{cfg.ui.artHD=v;artPaint()},"art-hd"),el("div",{class:"hint-text",text:"Takes the sharpest version there is and swaps out low-res pictures you already have. A game with only small scans keeps the best one found, and gets upgraded when a sharper one turns up."}),
    toggle("Fetch box art for new games",cfg.ui.autoCovers!==false,v=>{cfg.ui.autoCovers=v},"gen-ac"),el("div",{class:"hint-text",text:"In the background, after new games turn up."})));
  // the old grid: consoles × parts with what you have, for mixing per console
  const more=el("details",{class:"adv"},el("summary",{},"Choose per console"));
  const tbl=el("div",{class:"art-table",role:"grid"});
  tbl.append(el("div",{class:"art-row art-headrow"},el("div",{class:"art-sys",text:"Console"}),...ART_KINDS.map(([k,l])=>el("button",{type:"button",class:"art-colhead",title:`Tick or untick ${l} for every console`,onclick:()=>{const all=sys.every(c=>(plan[c.id]||[]).includes(k));const pl=artPlan();sys.forEach(c=>{const cur=(pl[c.id]||[]).filter(x=>x!==k);if(!all)cur.push(k);pl[c.id]=cur});cfg.ui.artPlan=Object.assign({},pl);artPaint()}},l)),el("div",{class:"art-all",text:""})));
  sys.forEach(c=>{const kinds=plan[c.id]||[];
    const cells=ART_KINDS.map(([k,l])=>{const on=kinds.includes(k),have=c[k],low=c[k+"Low"];
      return el("button",{type:"button",class:"art-cell"+(on?" on":""),"aria-pressed":on?"true":"false","aria-label":`${c.name} ${l}`,onclick:()=>artSetPlan(c.id,on?kinds.filter(x=>x!==k):[...kinds,k])},
        el("span",{class:"art-check",text:on?"✓":""}),el("span",{class:"art-count",text:`${have}/${c.total}`}),low&&cfg.ui.artHD!==false?el("span",{class:"art-low",text:`${low} low-res`}):"")});
    const allOn=ART_KINDS.every(([k])=>kinds.includes(k));
    tbl.append(el("div",{class:"art-row"},el("div",{class:"art-sys"},el("b",{text:c.name}),el("span",{text:`${c.total} games`})),...cells,
      el("button",{type:"button",class:"btn small art-all",onclick:()=>artSetPlan(c.id,allOn?[]:ART_KINDS.map(x=>x[0]))},allOn?"None":"All")))});
  more.append(el("div",{class:"hint-text",text:"Each box is one console and one picture type, with how many you have. Tick exactly what to get."}),tbl);
  if(chosen.some(c=>ART_KINDS.some(([k])=>(plan[c.id]||[]).includes(k)!==parts.includes(k))))more.open=true; // a mix made here stays in view
  artBox.append(more);
  const where=el("details",{class:"adv"},el("summary",{},"Where box art is kept"));where.append(artLibCard());artBox.append(where);
  // what Start will do
  let missing=0,low=0;
  chosen.forEach(c=>(plan[c.id]||[]).forEach(k=>{missing+=c.total-c[k];if(hd)low+=c[k+"Low"]}));
  const sum=el("div",{class:"art-summary"});
  if(!chosen.length)sum.append(el("span",{text:"Tick at least one console to start."}));
  else sum.append(el("b",{text:`${chosen.length} console${chosen.length>1?"s":""}`}),` · up to ${missing} missing picture${missing===1?"":"s"} to find`+(low?` and ${low} low-res to upgrade`:"")+(missing+low===0?" — everything you picked is already here"+(hd?" in HD":""):""));
  const start=el("button",{type:"button",class:"btn primary art-start",disabled:!chosen.length?"":null,onclick:artStart},missing+low===0&&chosen.length?"Check again":"Start download");
  artBox.append(startBar(sum,start,"Pictures you already have are kept (Prefer HD scans swaps one only when the new picture is clearly sharper). Downloading sends your game titles to those sites. The first run also fetches the LaunchBox database (about 110 MB, once)."));
}
async function artStart(){
  if(coverBusy){Shelf.toast("Another download is running");return}
  const plan=artPlan(),ids=(artState.cov||[]).filter(c=>c.total&&(plan[c.id]||[]).length).map(c=>c.id);if(!ids.length)return;
  await persist();
  dlRuns++;Object.assign(artState,{screen:"run",prog:{},rep:null,err:null,started:Date.now(),ids,stageMsg:"Starting…"});coverBusy=true;artPaint();Shelf.play&&Shelf.play("open");
  try{const p={};ids.forEach(id=>{p[id]=plan[id]});artState.rep=await api.downloadCovers(ids,p,cfg.ui.artHD!==false)}catch(e){artState.err=e&&e.message||String(e)}
  coverBusy=false;artState.screen="done";artState.cov=null;artPaint();Shelf.toast(artState.err?"Box art: "+artState.err:artState.rep&&artState.rep.cancelled?"Box art download cancelled":"Box art download finished");
}
function artPaintRun(){
  const S=artState,cov=S.cov||[];
  artBox.append(el("div",{class:"art-stage"},el("span",{class:"scanning"})," ",el("b",{text:"Downloading box art"}),el("span",{class:"art-elapsed",text:" · "+fmtSecs((Date.now()-S.started)/1000)})));
  if(S.stageMsg&&!Object.keys(S.prog).length)artBox.append(el("div",{class:"hint-text",text:S.stageMsg}));
  S.ids.forEach(id=>{const c=cov.find(x=>x.id===id)||{name:id,total:0},p=S.prog[id],tot=p?p.total:c.total,dn=p?p.done:0,pct=tot?Math.round(dn/tot*100):0;
    const st=p?(dn>=tot&&tot?"Finished":p.game?`Now: ${p.game}`:p.stage):"Waiting";
    artBox.append(el("div",{class:"art-prog"+(p&&dn>=tot&&tot?" done":"")},el("div",{class:"art-prog-top"},el("b",{text:c.name}),el("span",{text:`${dn} / ${tot}`+(p&&p.counts?` · ${p.counts.got} new · ${p.counts.upgraded} upgraded`:"")})),
      el("div",{class:"art-bar"},el("div",{class:"art-fill",style:`width:${pct}%`})),el("div",{class:"art-now",text:st})))});
  artBox.append(el("div",{class:"row"},el("button",{type:"button",class:"btn small",onclick:e=>{e.currentTarget.disabled=true;e.currentTarget.textContent="Stopping after the games in progress…";api.cancelCovers&&api.cancelCovers()}},"Cancel")));
  clearTimeout(artPaintRun.t);artPaintRun.t=setTimeout(()=>{if(artState.screen==="run")artPaint()},1000); // the timer ticks
}
function fmtSecs(s){s=Math.round(s);return s<60?`${s}s`:`${Math.floor(s/60)}m ${String(s%60).padStart(2,"0")}s`}
function artPaintDone(){
  const S=artState,r=S.rep;
  if(S.err){artBox.append(card("Box art didn't finish",S.err));}
  else if(r){
    const tot=r.systems.reduce((a,x)=>({got:a.got+x.front+x.back+x.spine+x.disc,up:a.up+(x.upgraded||0),miss:a.miss+x.missed.length}),{got:0,up:0,miss:0});
    artBox.append(el("div",{class:"art-finish"+(r.cancelled?" cancelled":"")},el("div",{class:"art-finish-icon",text:r.cancelled?"■":"✓"}),el("div",{},el("div",{class:"art-finish-title",text:r.cancelled?"Download cancelled":"All done"}),
      el("div",{class:"hint-text",text:`${tot.got} new picture${tot.got===1?"":"s"}${r.hd?`, ${tot.up} upgraded to HD`:""}${tot.miss?`, ${tot.miss} game${tot.miss===1?"":"s"} still missing something`:""} · took ${fmtSecs((Date.now()-S.started)/1000)}. New art shows on the shelf when you close Settings.`}))));
    r.systems.forEach(x=>{const parts=[["front","fronts"],["back","backs"],["spine","spines"],["disc","discs"]].filter(([k])=>x[k]).map(([k,l])=>`${x[k]} ${l}`);
      const row=el("div",{class:"art-res"},el("div",{class:"art-res-top"},el("b",{text:x.name}),el("span",{text:(parts.length?parts.join(", "):"nothing new")+(x.upgraded?` · ${x.upgraded} upgraded`:"")+(x.had?` · ${x.had} already complete`:"")})));
      const lst=(arr,label,cls)=>{if(!arr||!arr.length)return;const by={};arr.forEach(([t,k])=>{(by[t]=by[t]||[]).push(k)});const names=Object.keys(by).sort((p,q)=>p.localeCompare(q));
        row.append(el("details",{class:"art-miss "+cls},el("summary",{text:label}),el("div",{class:"art-glist"},...names.map(t=>el("div",{class:"art-gl"},el("span",{text:t}),el("small",{text:by[t].join(", ")}))))))};
      lst(x.up,`${x.upgraded} upgraded to HD`,"up");lst(x.got,`${x.got?x.got.length:0} new picture${x.got&&x.got.length===1?"":"s"}`,"new");
      if(x.missed.length){const kinds=Object.entries(x.missedKinds||{}).map(([k,n])=>`${n} ${k}${n>1?"s":""}`).join(", ");const d=el("details",{class:"art-miss"},el("summary",{text:`${x.missed.length} not found${kinds?` (${kinds})`:""}`}),el("div",{text:x.missed.join(", ")}));row.append(d);
        // (1.80) not a dead end: the console's games in Art Studio, to choose another version or a picture of your own
        if(api.artStudio)row.append(el("div",{class:"row"},el("button",{type:"button",class:"btn small",onclick:()=>open({page:"artstudio",systemId:x.id})},"Fix in Art Studio")))}
      artBox.append(row)});
    if(r.launchboxError)artBox.append(el("div",{class:"miss",text:"The LaunchBox database couldn't be reached, so only libretro fronts were tried: "+r.launchboxError}));
  }
  artBox.append(el("div",{class:"row"},el("button",{type:"button",class:"btn primary",onclick:()=>{artState.screen="plan";artState.rep=null;artState.cov=null;api.artCoverage().then(c=>{artState.cov=c;artPaint()}).catch(()=>{artState.cov=[];artPaint()});artPaint()}},"Back to the art list")));
}
/* ---------- Settings → Magazines & guides: your folders, and a search of the Internet Archive ---------- */
const IA_CHIPS={magazine:["Game Informer","Nintendo Power","Electronic Gaming Monthly","GamePro","Official Nintendo Magazine","Official Xbox Magazine","PlayStation Magazine","Nintendo Life"],guide:["Prima official guide","BradyGames guide","Nintendo Power guide","Piggyback guide","Zelda guide","Mario guide","Pokemon guide","Final Fantasy guide"]};
// backlog: results of the last page not checked yet (they're checked before the next page is fetched, so none is lost);
// pending: results archive.org was too busy to check (kept and asked again a little later, never dropped as "not downloadable")
const iaState={kind:"magazine",text:"",page:0,next:1,total:0,rows:36,plain:false,items:[],backlog:[],pending:[],seen:new Set(),busy:false,err:"",series:"",jobs:new Map(),checking:0,skipped:0,gen:0,recheckT:0,rechecks:0,osk:false};
const IA_PAR=2; // downloads at once: a big one never holds up the rest
if(api.onArchiveProgress)api.onArchiveProgress(p=>{const j=iaState.jobs.get(p.id);if(j&&j.state==="run"){j.pct=p.total?Math.round(p.bytes/p.total*100):0;j.mb=p.unit==='pages'?`page ${p.bytes} of ${p.total}`:(p.bytes/1048576).toFixed(1)+' MB';iaPaintJob(p.id)}});
let iaBox=null;
// "Game Informer Issue 150", "GamePro_June_1995", "Xbox The Official Magazine Xmas 2015" → the magazine's name (its series
// sub-folder): cut at the issue / volume / number, a month or season, or a year
const IA_MONTH="jan(?:uary)?|feb(?:ruary)?|mar(?:ch)?|apr(?:il)?|may|june?|july?|aug(?:ust)?|sep(?:t(?:ember)?)?|oct(?:ober)?|nov(?:ember)?|dec(?:ember)?|xmas|christmas|spring|summer|autumn|fall|winter|holiday";
const iaSeries=t=>{const s0=String(t||"").replace(/[_.]+/g," ").replace(/\s+/g," ").trim();
  const m=new RegExp(`^(.*?)(?:\\s*[-–:,]?\\s*(?:issue|vol\\.?|volume|no\\.?|number|#)\\s*\\d|\\s*[-–:,(]?\\s*\\b(?:${IA_MONTH})\\b|\\s+\\d{1,4}\\b|\\s*\\(\\d{4})`,"i").exec(s0);
  const s=(m?m[1]:"").replace(/[\s\-–:,(]+$/,"").trim();return s.length>2&&s.length<60?s:""};
function readingSection(b){
  head(b,"Library","Magazines & guides","Your reading corner in the museum. Add your own files, or find scans on the Internet Archive.");
  const where=el("div",{class:"hint-text ia-where"});
  b.append(card("Your folders","PDF, CBZ / CBR, or a folder of page images per issue. Sub-folders become series (Magazines\\Nintendo Power\\Issue 12.pdf).",
    el("div",{class:"row-btns"},el("button",{type:"button",class:"btn small",onclick:()=>api.openLibraryFolder&&api.openLibraryFolder("magazine")},"Open Magazines folder"),el("button",{type:"button",class:"btn small",onclick:()=>api.openLibraryFolder&&api.openLibraryFolder("guide")},"Open Guides folder")),where));
  if(api.readingWhere)Promise.resolve(api.readingWhere()).then(w=>{if(w&&where.isConnected)where.textContent=`Magazines: ${w.magazines}  ·  Guides: ${w.guides}`}).catch(()=>{});
  iaBox=el("div",{class:"ia-box"});
  b.append(card("Find on the Internet Archive","Shows only scans the Internet Archive lets anyone download (borrow-only books are left out). They're still copyrighted works, so it's your call what to keep. Searching sends your words to archive.org.",iaBox));
  iaPaint();
}
const iaReset=(S,kind)=>{S.gen++;clearTimeout(S.recheckT);S.busy=false;S.checking=0;if(kind)S.kind=kind;S.items=[];S.backlog=[];S.pending=[];S.seen=new Set();S.total=0;S.page=0;S.next=1;S.plain=false;S.skipped=0;S.rechecks=0;S.err=""};
// the same finder inside the museum (X in the reading corner): mounted into its panel. back() closes its on-screen keyboard
// first (true when it did); osk() opens it (A on the search box with a controller)
window.BoxshelfIA={mount(el,kind){iaBox=el;iaState.osk=false;if(kind&&kind!==iaState.kind)iaReset(iaState,kind);iaPaint()},unmount(el){if(iaBox===el){iaBox=null;iaState.osk=false}},
  back(){if(!iaState.osk)return false;iaState.osk=false;iaPaint();const t=iaBox&&iaBox.querySelector(".ia-type");if(t)t.focus({preventScroll:true});return true},
  osk(){if(!iaBox)return false;iaState.osk=true;iaPaint();const k=iaBox.querySelector(".ia-key");if(k)k.focus({preventScroll:true});return true},
  typing:()=>!!iaState.osk,running:()=>[...iaState.jobs.values()].filter(j=>j.state==="run"||j.state==="queued").length};
function iaPaint(){keepFocus(iaBox,iaPaint0)}
// the on-screen keyboard: plain buttons, so the museum panel's D-pad / stick navigation and A reach every key
const IA_KEYS=[["1","2","3","4","5","6","7","8","9","0"],["q","w","e","r","t","y","u","i","o","p"],["a","s","d","f","g","h","j","k","l","'"],["z","x","c","v","b","n","m","-","#",":"]];
function iaOsk(inp){const S=iaState;
  const typed=v=>{S.text=v;inp.value=v};
  const k=(label,run,cls,aria)=>el("button",{type:"button",class:"ia-key"+(cls?" "+cls:""),"aria-label":aria||label,onclick:e=>{e.preventDefault();run()}},label);
  return el("div",{class:"ia-osk",role:"group","aria-label":"On-screen keyboard"},
    ...IA_KEYS.map(row=>el("div",{class:"ia-krow"},...row.map(c=>k(c,()=>typed((S.text||"")+c))))),
    el("div",{class:"ia-krow"},k("Space",()=>typed((S.text||"")+" "),"wide"),k("⌫",()=>typed(String(S.text||"").slice(0,-1)),"mid","Delete"),k("Clear",()=>typed(""),"mid"),k("Search",()=>{S.osk=false;iaSearch(1);const t=iaBox&&iaBox.querySelector(".ia-type");if(t)t.focus({preventScroll:true})},"go","Search now")))}
function iaPaint0(){
  if(!iaBox||!iaBox.isConnected)return;iaBox.innerHTML="";const S=iaState,inMuseum=!!iaBox.closest("#musFind");
  const seg=el("div",{class:"seg ia-kind"},...[["magazine","Magazines"],["guide","Strategy guides"]].map(([k,l])=>el("button",{type:"button",class:"seg-btn"+(S.kind===k?" on":""),"aria-pressed":S.kind===k?"true":"false",onclick:()=>{if(S.kind===k)return;iaReset(S,k);iaPaint()}},l)));
  const inp=el("input",{type:"search",class:"ia-input",placeholder:S.kind==="guide"?"A game or a publisher, e.g. Wind Waker guide":"A magazine and issue, e.g. Game Informer 2008",value:S.text,oninput:()=>{S.text=inp.value},onkeydown:e=>{if(e.key==="Enter"){e.preventDefault();e.stopPropagation();S.text=inp.value;S.osk=false;iaSearch(1)}}});
  const go=el("button",{type:"button",class:"btn small primary",onclick:()=>{S.text=inp.value;S.osk=false;iaSearch(1)}},S.busy?"Searching…":"Search");if(S.busy)go.disabled=true;
  // in the museum (a controller, no keyboard): a button that brings up the on-screen keyboard
  const type=inMuseum?el("button",{type:"button",class:"btn small ia-type","aria-label":"Type a search",onclick:()=>{S.osk=!S.osk;iaPaint();if(S.osk){const k=iaBox.querySelector(".ia-key");if(k)k.focus({preventScroll:true})}}},S.osk?"Hide keyboard":"⌨ Type"):null;
  const chips=el("div",{class:"ia-chips"},...IA_CHIPS[S.kind].map(c=>el("button",{type:"button",class:"chip",onclick:()=>{S.text=c;S.osk=false;iaSearch(1)}},c)));
  iaBox.append(seg,el("div",{class:"row ia-row"},inp,type,go));
  if(S.osk)iaBox.append(iaOsk(inp));else iaBox.append(chips);
  if(S.err)iaBox.append(el("div",{class:"miss",text:S.err}));
  const jobs=[...S.jobs.values()].filter(j=>j.state==="run"||j.state==="queued");
  if(jobs.length)iaBox.append(el("div",{class:"hint-text ia-jobs",text:`Downloading ${jobs.filter(j=>j.state==="run").length}${jobs.length>IA_PAR?`, ${jobs.length-IA_PAR} waiting`:""}. You can keep searching; Cancel stops one.`}));
  const pend=S.pending.length?el("div",{class:"hint-text ia-busy"},el("span",{class:"scanning"}),S.recheckT?` archive.org is busy: still checking ${S.pending.length} more…`:` archive.org was too busy to check ${S.pending.length}. `,S.recheckT?null:el("button",{type:"button",class:"btn small",onclick:()=>{S.rechecks=0;iaRecheck(S.gen)}},"Check again")):null;
  if(S.items.length){
    iaBox.append(el("div",{class:"hint-text",text:`${S.items.length} you can download${S.skipped?` (${S.skipped} borrow-only or without a usable file were left out)`:""} · most downloaded first${S.plain&&S.kind==="magazine"?" · not only magazines":""}`}));
    const grid=el("div",{class:"ia-grid"});
    S.items.forEach(it=>{const img=el("img",{class:"ia-thumb",alt:"",loading:"lazy",src:it.thumb});img.onerror=()=>{img.style.visibility="hidden"};
      const job=S.jobs.get(it.id),act=el("div",{class:"ia-act","data-id":it.id});
      grid.append(el("div",{class:"ia-card"},img,el("div",{class:"ia-meta"},el("div",{class:"ia-title",text:it.title}),el("div",{class:"ia-sub",text:[it.date&&it.date.slice(0,7),it.pages?it.pages+" pages":"",it.check&&it.check.size?(it.check.size/1048576).toFixed(0)+" MB":"",it.check&&it.check.type==="bundle"?it.check.files+" issues":"",it.check&&(it.check.type==="pages"||it.check.type==="images")?"page scans":""].filter(Boolean).join(" · ")}),act)));
      iaFillAct(act,it,job)});
    iaBox.append(grid);
    if(S.checking)iaBox.append(el("div",{class:"hint-text"},el("span",{class:"scanning"}),` Checking which of ${S.checking} more can be downloaded…`));
    if(pend)iaBox.append(pend);
    if(iaMore()&&!S.checking)iaBox.append(el("div",{class:"row"},el("button",{type:"button",class:"btn small ia-more",onclick:()=>iaSearch(0)},S.busy?"Loading…":"Show more")));
  }else if(S.checking)iaBox.append(el("div",{class:"hint-text"},el("span",{class:"scanning"}),` Found some — checking which of ${S.checking} can be downloaded…`));
  else if(pend)iaBox.append(pend);
  else if(!S.busy&&S.text&&!S.err&&S.page)iaBox.append(el("div",{class:"hint-text",text:"Nothing downloadable found. Try fewer words, or the magazine's name alone."}));
}
const iaMore=()=>{const S=iaState;return S.backlog.length>0||(S.next<=20&&(S.next-1)*S.rows<S.total)};
function iaFillAct(act,it,job){act.innerHTML="";
  const cancel=el("button",{type:"button",class:"btn small ghost","aria-label":`Cancel ${it.title}`,onclick:()=>iaCancel(it.id)},"Cancel");
  if(!job){if(it.check&&it.check.owned)act.append(el("span",{class:"badge ok",text:"In your folder"}));else act.append(el("button",{type:"button",class:"btn small","aria-label":`Download ${it.title}`,onclick:()=>iaQueue(it)},"Download"));act.append(el("button",{type:"button",class:"btn small ghost","aria-label":`View ${it.title}`,onclick:()=>openLink(it.page)},"View"))}
  else if(job.state==="queued")act.append(el("span",{class:"ia-state",text:"Waiting…"}),cancel);
  else if(job.state==="run")act.append(el("div",{class:"art-bar"},el("div",{class:"art-fill",style:`width:${job.pct||0}%`})),el("span",{class:"ia-state",text:job.stopping?"Stopping…":job.pct?`${job.pct}% · ${job.mb}`:"Starting…"}),api.archiveCancel&&!job.stopping?cancel:null);
  else if(job.state==="done")act.append(el("span",{class:"badge ok",text:job.existed?"Already in your folder":"Saved"}),el("span",{class:"ia-state",text:" "+job.name}));
  else if(job.state==="cancelled")act.append(el("span",{class:"ia-state",text:"Cancelled"}),el("button",{type:"button",class:"btn small","aria-label":`Download ${it.title}`,onclick:()=>{iaState.jobs.delete(it.id);iaQueue(it)}},"Download"));
  else act.append(el("span",{class:"miss",text:job.err||"Failed"}),el("button",{type:"button",class:"btn small","aria-label":`Try ${it.title} again`,onclick:()=>{iaState.jobs.delete(it.id);iaQueue(it)}},"Try again"));
}
function iaPaintJob(id){if(!iaBox)return;const act=iaBox.querySelector(`.ia-act[data-id="${CSS.escape(id)}"]`),it=iaState.items.find(x=>x.id===id);if(act&&it)keepFocus(act,()=>iaFillAct(act,it,iaState.jobs.get(id)))}
// page 1 starts over (also while one is running: a chip or Search with new words replaces it, its answers are dropped by
// gen); page 0 = "Show more": carries on with the backlog, then the next page
async function iaSearch(page){const S=iaState;if(S.busy&&page!==1)return;if(!String(S.text||"").trim()){S.err="Type what to look for first.";iaPaint();return}
  if(page===1)iaReset(S);const gen=S.gen;S.busy=true;S.err="";iaPaint();
  // checked a few at a time: results appear as soon as each batch is confirmed; nothing fetched is skipped
  try{const want=S.items.length+12;
    for(let pages=0;S.items.length<want;){
      if(!S.backlog.length){if(pages>=4||!(S.next===1||iaMore()))break;
        const r=await api.archiveSearch(S.text,S.kind,S.next,S.plain);if(gen!==S.gen)return;pages++;S.page=r.page;S.next=r.page+1;S.total=r.total;S.plain=!!r.plain;if(r.rows)S.rows=r.rows;
        const fresh=r.items.filter(x=>!S.seen.has(x.id));fresh.forEach(x=>S.seen.add(x.id));S.backlog.push(...fresh);if(!fresh.length&&!r.items.length)break;continue}
      const batch=S.backlog.splice(0,6);S.checking=S.backlog.length+batch.length;iaPaint();
      const ok=api.archiveCheck?await api.archiveCheck(batch.map(x=>x.id)):{};if(gen!==S.gen)return;
      iaSort(S,batch,ok)}}
  catch(e){if(gen!==S.gen)return;S.err="Search didn't work: "+iaMsg(e)+(/busy/i.test(iaMsg(e))?"":" Check your internet connection and try again.")}
  S.checking=0;S.busy=false;iaPaint();if(S.pending.length&&!S.recheckT)iaRecheck(gen,true)}
// a check's answers: downloadable → listed; busy / unreachable → asked again later; anything else → left out (counted)
function iaSort(S,list,ok){list.forEach(x=>{const c=ok&&ok[x.id];if(c&&c.ok){x.check=c;S.items.push(x)}else if(!c||c.retry){if(!S.pending.includes(x))S.pending.push(x)}else S.skipped++})}
// archive.org was busy: wait a little longer each round (5, 10, 15… s), up to 6 rounds, then offer "Check again"
function iaRecheck(gen,later){const S=iaState;clearTimeout(S.recheckT);if(gen!==S.gen||!S.pending.length){S.recheckT=0;return}
  if(S.rechecks>=6){S.recheckT=0;iaPaint();return}
  S.recheckT=setTimeout(async()=>{if(gen!==S.gen)return;S.rechecks++;const list=S.pending.splice(0,12);
    let ok={};try{ok=api.archiveCheck?await api.archiveCheck(list.map(x=>x.id)):{}}catch{}
    if(gen!==S.gen)return;iaSort(S,list,ok);S.recheckT=0;iaPaint();if(S.pending.length)iaRecheck(gen,true)},later?5000*(S.rechecks+1):0);iaPaint()}
const iaMsg=e=>String(e&&e.message||e).replace(/^Error invoking remote method '[^']+': (Error: )?/,"");
// downloads run IA_PAR at a time, the rest wait their turn; each can be cancelled
function iaQueue(it){const S=iaState;S.jobs.set(it.id,{state:"queued",it,kind:S.kind,series:S.kind==="magazine"?iaSeries(it.title):""});iaPaint();iaPump()}
function iaCancel(id){const j=iaState.jobs.get(id);if(!j)return;
  if(j.state==="queued"){iaState.jobs.delete(id);iaPaint();return}
  // asked again a few times when the backend didn't know it yet (Cancel pressed the moment it started)
  if(j.state==="run"&&api.archiveCancel){j.stopping=true;iaPaintJob(id);const ask=n=>Promise.resolve(api.archiveCancel(id)).then(r=>{if(r===false&&n<6&&j.state==="run"&&iaState.jobs.get(id)===j)setTimeout(()=>ask(n+1),300)}).catch(()=>{});ask(0)}}
let iaRunning=0;
function iaPump(){while(iaRunning<IA_PAR){const next=[...iaState.jobs.entries()].find(([,j])=>j.state==="queued");if(!next)break;iaRun(next[0],next[1])}}
async function iaRun(id,j){iaRunning++;j.state="run";iaPaint();
  try{const r=await api.archiveDownload(id,j.kind,j.it.title,j.series);j.state="done";j.existed=r.existed;try{dispatchEvent(new CustomEvent("bs-library-changed"))}catch{}j.name=r.count>1?`${r.name} (${r.count} files)`:r.name;Shelf.toast(r.existed?`Already have: ${j.it.title}`:`Saved to your ${j.kind==="guide"?"Guides":"Magazines"} folder: ${j.it.title}`)}
  catch(e){const m=iaMsg(e);if(/^Cancelled\.?$/.test(m)||j.stopping){j.state="cancelled"}else{j.state="err";j.err=m}}
  finally{iaRunning--;j.stopping=false;iaPaint();iaPump()}}
// (beta.7) the first run's video previews: one switch (on when box art is downloaded), the videos already found, and
// whether YouTube may fill in for games without one. Settings → Box art & manuals → Video previews has the rest.
let pvTouched=false,pvFound=null;
function pvCard(){const on=(cfg.ui.previews||"off")!=="off";
  const found=el("div",{class:"hint-text wz2-pvfound",text:on?(pvFound==null?"Looking for videos you already have…":pvFound?`Found videos for ${pvFound} of your games. Steam games get their trailers too.`:"No video files found yet: Steam games get their trailers, and other games YouTube's."):""});
  if(on&&pvFound==null&&api.previewCount){pvFound=-1;persist().then(()=>api.previewCount()).then(c=>{pvFound=c&&c.total||0;render()}).catch(()=>{pvFound=0;render()})}
  return card("","",toggle("Play video previews",on,v=>{pvTouched=true;cfg.ui.previews=v?(window.bsPlatform&&bsPlatform.os==="android"?"details":"shelf"):"off";changed();render()},"wz2-pv"),
    el("div",{class:"hint-text",text:"Rest on a game and its preview plays, on the shelf and in Details: videos you already have (ES-DE, EmuDeck, RetroBat, LaunchBox), Steam's trailers for Steam games, and YouTube trailers for the rest."}),
    on?found:null,
    on&&pvClipOk?toggle("Console trailers too (a short clip of each game's YouTube trailer, saved so it plays on the shelf; fetches a free 25 MB helper once)",cfg.ui.previewClips!==false,v=>{pvTouched=true;cfg.ui.previewClips=!!v;changed()},"wz2-pvclip"):null,
    on&&api.previewYt?toggle("YouTube trailers when a game has no video",cfg.ui.previewYoutube!==false,v=>{pvTouched=true;cfg.ui.previewYoutube=!!v;changed()},"wz2-pvyt"):null)}
// (beta.8) console trailers: offered only where clips can be made (Windows); the helper is fetched once setup is done
let pvClipOk=false;if(api.clipStatus)Promise.resolve(api.clipStatus()).then(s=>{pvClipOk=!!(s&&s.supported)}).catch(()=>{});
let pvClipAsked=false;
function pvClipStart(){if(pvClipAsked)return;if(pvClipOk&&(pvClipAsked=true)&&api.clipHelper&&(cfg.ui.previews||"off")!=="off"&&cfg.ui.previewClips!==false)api.clipHelper().catch(()=>{})}
async function doneSection(b){
  pvClipStart(); // (beta.8) the console trailer helper, in the background (Settings → Video previews shows how it went)
  head(b,"All set","Here's your library","You can change any of this later in Settings (press S, or View on the controller: Create on a PlayStation pad, − on a Switch pad).");
  const grid=el("div",{class:"summary-grid"},el("div",{class:"sum"},el("span",{class:"scanning"}),el("span",{text:" Scanning…"})));b.append(grid);
  // Windows: the art was chosen on the Box art page; here its first download shows its progress (covers:auto from
  // main.js autoCovers, which the scan below starts), then points to the Art Studio
  if(winWizard()&&cfg.ui.autoCovers===true){const line=el("div",{class:"wz2-prog"},el("span",{class:"scanning"}),el("span",{class:"wz2-pt",text:" Getting box art starts in a moment…"}));
    const box=el("section",{class:"card grp wz2-artdone"},line,el("div",{class:"hint-text",text:"You can open Boxshelf now; covers fill in as they arrive."}));b.append(box);watchArt(line,box)}
  // online look-ups are off until you say yes here (or later in Settings → Art & manuals); Android asked already
  if(!window.BS_WIZARD_EXTRA&&!winWizard())b.append(card("Box art & manuals from the internet","Looking art and manuals up sends your game titles (and GameCube/Wii disc IDs) to the libretro thumbnails, the LaunchBox Games Database, GameTDB, GamesDatabase.org and the Internet Archive. Nothing else about you or your PC is sent.",
    toggle("Fetch box art for new games automatically",cfg.ui.autoCovers===true,v=>{cfg.ui.autoCovers=v;persist()},"done-ac"),
    toggle("Fetch manuals automatically",cfg.ui.autoManuals===true,v=>{cfg.ui.autoManuals=v;persist()},"done-am")));
  if(!window.BS_WIZARD_EXTRA&&!winWizard())b.append(artLibCard("Where to keep box art & manuals"));
  if(winWizard()&&artChosen===false&&api.previewFind)b.append(pvCard()); // (beta.7) "Not now" to box art skipped the page that offers previews
  await persist();
  // (beta.7) Set up for me, once the picked consoles are saved: what was set up, what waits for its first run, what was left
  if(winWizard()&&api.emuSetupAuto&&cfg.emuSetup&&cfg.emuSetup.auto!==false&&!emuAutoRan){emuAutoRan=true;
    const line=el("div",{class:"wz2-prog"},el("span",{class:"scanning"}),el("span",{class:"wz2-pt",text:" Setting up your emulators…"}));b.append(el("section",{class:"card grp wz2-emudone"},line));
    try{const r=await api.emuSetupAuto();if(r&&r.emuSetup)cfg.emuSetup=r.emuSetup;const L=[];
      if(r&&r.done&&r.done.length)L.push(`Set up: ${r.done.join(", ")}.`);if(r&&r.later&&r.later.length)L.push(`Set up after you play once: ${r.later.join(", ")}.`);
      if(r&&r.left&&r.left.length)L.push(`Left as you had them: ${r.left.join(", ")} (Settings → the console → Set up for me).`);
      line.replaceChildren(el("span",{class:"wz2-pt",text:L.length?L.join(" "):"Your emulators were already set up."}))}
    catch(e){line.replaceChildren(el("span",{class:"wz2-pt",text:"Setting up the emulators didn't work this time. Settings → each console → Set up for me can try again."}))}}
  let shelves=[];try{shelves=await api.scan()}catch(e){}
  if(!grid.isConnected)return; // left this page while it was scanning
  grid.innerHTML="";
  if(!shelves.length)grid.append(el("div",{class:"sum"},el("b",{text:"0"}),el("span",{text:"Nothing found yet. Go back and add an emulator or a games folder."})));
  shelves.forEach(s=>grid.append(el("div",{class:"sum"},el("b",{text:String(s.games.length)}),el("span",{text:`${s.name} ${s.kind==="apps"?"apps":"games"}`}))));
}
async function finish(){pickInit();const hd=applyLook();cfg.setupDone=true;wizard=false;
  // (beta.10) "Sharpest art": saved first (the consoles you picked must exist for main), then each console's style through api.artStyle, before the shelf's rescan
  if(hd.length&&api.artStyle){clearTimeout(saveT);await persist();for(const id of hd){try{await api.artStyle({systemId:id,style:{preset:"hd",region:"usa",parts:{}}})}catch(e){console.warn("setup: art style",id,e&&e.message)}}}
  await close();if(wzPnOn&&wzPn)setTimeout(()=>{try{Setup.open({page:"addgames"})}catch{}},900)} // (beta.7: then the Playnite import, if ticked) // (1.82) pickInit: done from the sidebar without visiting Your consoles still ticks only the consoles found
// the first box art download, followed on the Done screen; finished after the wizard closed: one toast on the shelf
let artLine=null,artDoneBox=null,artToast=false,artHooked=false,artHeard=false;
function watchArt(line,box){artLine=line;artDoneBox=box;artToast=true;artHeard=false;
  // nothing heard in 20 s (no games yet, or every game already has its art): say so rather than wait forever
  setTimeout(()=>{if(!artHeard&&line.isConnected){const t=line.querySelector(".wz2-pt"),s=line.querySelector(".scanning");if(s)s.remove();if(t)t.textContent="Box art downloads in the background whenever games are added."}},20000);
  if(artHooked||!api.onCoversAuto)return;artHooked=true;
  api.onCoversAuto(i=>{i=i||{};artHeard=true;
    if(i.end){const live=artLine&&artLine.isConnected;
      if(live)artDone(!!i.failed);else if(artToast&&i.got)Shelf.toast(`Box art is ready for ${i.got} game${i.got===1?"":"s"}. Change any console's look in Settings → Box art & manuals → Art Studio.`);
      artToast=false;artLine=null;return}
    if(!artLine||!artLine.isConnected)return;const t=artLine.querySelector(".wz2-pt");if(!t)return;
    t.textContent=" "+(i.total?`Getting box art · ${i.system||"games"}: ${i.done} of ${i.total}`:(i.stage||"Getting box art…"))});
}
function artDone(failed){const artBox=artDoneBox;if(!artBox)return;artBox.innerHTML="";
  artBox.append(el("div",{class:"wz2-prog"},el("span",{class:"wz2-ok","aria-hidden":"true",text:failed?"!":"✓"}),el("span",{text:failed?" Couldn't reach the art databases just now. Boxshelf tries again next time it starts.":" Box art is ready."})),
    el("div",{class:"hint-text",text:"Want a different look for a console, or a single game? The Art Studio has every version Boxshelf found."}),
    el("div",{class:"d-actions"},el("button",{type:"button",class:"btn",onclick:async()=>{await finish();open({page:"artstudio"})}},"Open the Art Studio")))}

/* ---------- keyboard & controller ---------- */
// what the controller can land on: never anything inside a closed section (Chromium won't focus it, so the pad got stuck there)
// lists that scroll inside the page (recent manuals, result lists…) become stops for the D-pad: up / down scroll them, and
// past their end move on to the next control
function markScrollers(){$("suBody").querySelectorAll("div,dl,ul").forEach(e=>{const cs=getComputedStyle(e);const can=/(auto|scroll)/.test(cs.overflowY)&&e.scrollHeight>e.clientHeight+4;if(can&&!e.hasAttribute("tabindex")){e.setAttribute("tabindex","0");e.classList.add("su-scroller")}else if(!can&&e.classList.contains("su-scroller")){e.removeAttribute("tabindex");e.classList.remove("su-scroller")}})}
function focusables(){markScrollers();return [...$("setup").querySelectorAll("button:not([disabled]),input:not([disabled]),select:not([disabled]),summary,.su-scroller")].filter(e=>e.offsetParent!==null&&(e.tagName==="SUMMARY"||!e.closest("details:not([open])")))}
function moveFocus(d){const f=focusables();if(!f.length)return;const i=f.indexOf(document.activeElement);let n=i<0?0:Math.max(0,Math.min(f.length-1,i+d));
  // step past anything that refuses focus instead of stopping on it
  for(let k=0;k<f.length;k++){f[n].focus();if(document.activeElement===f[n])break;const nx=n+(d<0?-1:1);if(nx<0||nx>=f.length)break;n=nx}
  f[n].scrollIntoView({block:"nearest"})}
// Two panes, like Discord on a controller: the sidebar and the page. Up / down move within the pane you're in; right
// from the sidebar steps into the page, left from the start of a row in the page steps back to the sidebar.
const inNav=e=>!!(e&&e.closest&&e.closest("#suNav"));
function paneItems(nav){return focusables().filter(e=>inNav(e)===nav)}
function focusEl(e){if(!e)return;e.focus();e.scrollIntoView({block:"nearest"})}
function navActive(){return $("suNav").querySelector(".su-step.on")||paneItems(true)[0]}
function paneMove(a){
  const ae=document.activeElement,here=ae&&$("setup").contains(ae)&&ae!==document.body?ae:null;
  if(here&&here.classList.contains("su-scroller")&&(a==="up"||a==="down")){const d=a==="down"?1:-1,max=here.scrollHeight-here.clientHeight;if((d>0&&here.scrollTop<max-2)||(d<0&&here.scrollTop>2)){here.scrollBy({top:d*here.clientHeight*.6,behavior:"smooth"});return}}
  if(!here){focusEl(wizard?paneItems(false)[0]:navActive());return}
  const nav=inNav(here),L=paneItems(nav),i=L.indexOf(here);
  if(nav){
    if(a==="up"||a==="down"){focusEl(L[Math.max(0,Math.min(L.length-1,i+(a==="down"?1:-1)))]);return}
    if(a==="right"){focusEl(paneItems(false)[0]);return}
    return}
  // in the page: same-row neighbours for left / right, the next control above / below for up / down
  const r=here.getBoundingClientRect(),cy=r.top+r.height/2;
  const row=L.filter(e=>{if(e===here)return false;const q=e.getBoundingClientRect();return Math.abs(q.top+q.height/2-cy)<Math.max(r.height,q.height)*.6});
  if(a==="left"||a==="right"){
    const side=row.filter(e=>{const q=e.getBoundingClientRect();return a==="left"?q.right<=r.left+2:q.left>=r.right-2}).sort((x,y)=>{const X=x.getBoundingClientRect(),Y=y.getBoundingClientRect();return a==="left"?Y.right-X.right:X.left-Y.left});
    if(side.length){focusEl(side[0]);return}
    if(a==="left"&&!wizard){focusEl(navActive());return}
    focusEl(L[Math.max(0,Math.min(L.length-1,i+(a==="right"?1:-1)))]);return}
  // up / down: the nearest control in the next row in that direction (so a row of choices is one step)
  const dir=a==="down"?1:-1;let cands=L.filter(e=>{const q=e.getBoundingClientRect(),y=q.top+q.height/2;return dir>0?y>cy+4&&!row.includes(e):y<cy-4&&!row.includes(e)});
  // the page before its footer: the footer's buttons sit at the bottom of the screen, nearer than the page's controls
  // scrolled out of view below, so down took Done / Next before the rest of the page. In the page, they come last.
  const body=$("suBody");if(body&&body.contains(here)){const inPage=cands.filter(e=>body.contains(e));if(inPage.length)cands=inPage}
  if(!cands.length)return;
  const ny=cands.map(e=>{const q=e.getBoundingClientRect();return q.top+q.height/2}),targetY=dir>0?Math.min(...ny):Math.max(...ny);
  const line=cands.filter((e,k)=>Math.abs(ny[k]-targetY)<18);
  line.sort((x,y)=>Math.abs(x.getBoundingClientRect().left-r.left)-Math.abs(y.getBoundingClientRect().left-r.left));focusEl(line[0]);
}
function goBack(){if(wizard){if(step>0)stepBy(-1);else if(cfg&&cfg.setupDone)close();else Shelf.toast("Finish setup to reach your shelf")}else close()} // B on the first step of a re-run wizard leaves (a first run can't: nothing is set up yet; (beta.10) it says so now instead of doing nothing)
function key(e){
  if(opening)return;
  if(e.key==="Escape"&&acctOsk){e.preventDefault();acctOskClose();return} // (the Accounts page's on-screen keyboard first)
  if(e.key==="Escape"){if(e.target&&e.target.tagName==="INPUT"){e.target.blur();return}e.preventDefault();goBack()}
}
function pad(a){
  if(opening)return;
  const ae=document.activeElement;
  // a dropdown: left/right picks the previous/next choice (its native popup can't be driven by a controller)
  if(ae&&ae.tagName==="SELECT"&&(a==="left"||a==="right")){const n=ae.options.length;if(n){ae.selectedIndex=Math.max(0,Math.min(n-1,ae.selectedIndex+(a==="right"?1:-1)));ae.dispatchEvent(new Event("input",{bubbles:true}));ae.dispatchEvent(new Event("change",{bubbles:true}))}return}
  if(ae&&ae.type==="range"&&(a==="left"||a==="right")){a==="right"?ae.stepUp():ae.stepDown();ae.dispatchEvent(new Event("input"));ae.dispatchEvent(new Event("change"));return}
  if(a==="up"||a==="down"||a==="left"||a==="right"){paneMove(a);return}
  else if(a==="ok"){if(ae&&ae.tagName==="SELECT"){moveFocus(1);return}
    if(ae&&ae.tagName==="INPUT"&&ae.dataset.osk&&$("setup").contains(ae)){acctOskOpen(ae.dataset.osk);return} // (beta.9) a key box: the on-screen keyboard
    if(ae&&ae!==document.body&&$("setup").contains(ae))ae.click();else moveFocus(0)}
  else if(a==="back"){if(acctOsk){acctOskClose();return}goBack()}
}

function scroll(dy){if(opening)return;const ae=document.activeElement;let t=ae&&ae.classList&&ae.classList.contains("su-scroller")?ae:null;
  if(!t&&ae){for(let p=ae.parentElement;p&&p!==$("suBody");p=p.parentElement){if(p.classList.contains("su-scroller")){t=p;break}}}
  (t||$("suBody")).scrollTop+=dy}
// Settings (settings.js) shows one of these classic pages for the bigger tools: the box art and manual download planners,
// the Internet Archive finder, a console's page with its detection lists, or the wizard ("Run setup again"). It shares
// Settings' config object; closing the page calls o.back() to return to the same row in Settings.
function openLegacy(page,o){o=o||{};if(o.info)info=o.info;if(o.cfg)cfg=o.cfg;wizard=!!o.wizard;legacyBack=wizard?null:(o.back||null);
  steps=sectionList();step=0;const i=steps.findIndex(x=>x.id===page);if(i>=0)step=i;lastPage=null;
  $("setup").classList.add("on");$("setup").setAttribute("aria-hidden","false");render();
  setTimeout(()=>{const n=wizard?paneItems(false)[0]:navActive();if(n)try{n.focus({preventScroll:true})}catch{}},0)}
const newOn=()=>!!(window.Settings&&Settings.active&&Settings.active());
// while the full-screen Settings is up it gets the keys; it reads the controller itself (all buttons), so pad / scroll
// from app.js are ignored then
// (and the classic handlers only act while a classic page is really on screen: never on a stale config while Settings closes)
const oldOn=()=>$("setup").classList.contains("on");
function keyR(e){if(newOn())return Settings.key(e);if(!oldOn())return;return key(e)}
function padR(a){if(newOn()||!oldOn())return;return pad(a)}
function scrollR(dy){if(newOn()||!oldOn())return;return scroll(dy)}
window.Setup={open,close,key:keyR,pad:padR,scroll:scrollR,openLegacy,legacyOpen:()=>!!legacyBack,downloads:()=>dlRuns};
})();
