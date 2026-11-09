// The TV side of the phone companion (main/companion.js): the phone's remote becomes one more controller in
// navigator.getGamepads(), so every screen (shelf, Details, Settings, museum, Inspect…) reads it exactly like a real pad,
// Confirm swap and prompt style included. Its typing goes into the text field that has focus, or to Settings' search
// as key presses. The phone's Play goes to the shelf's own launch path (Shelf.companionPlay in app.js).
// The Home menu is a separate window: main.js drives that one itself (homemenu.button).
(function(){
"use strict";
const api=window.boxshelf;if(!api||!api.onCompanionInput||!navigator.getGamepads)return;
// standard mapping: the face buttons by position (a = bottom, b = right, x = left, y = top), as on any pad
const IDX={a:0,b:1,x:2,y:3,lb:4,rb:5,view:8,start:9,up:12,down:13,left:14,right:15,home:16};
const MIN_MS=90,HOLD_MS=1300; // a tap lasts long enough for a frame to see it; a hold lets go if the phone stops saying "still down"
const buttons=[...Array(17)].map(()=>({pressed:false,touched:false,value:0}));
const pad={id:"Boxshelf phone remote",index:7,connected:true,mapping:"standard",timestamp:0,axes:[0,0,0,0],buttons,vibrationActuator:null};
let live=false;const held={};
const real=Navigator.prototype.getGamepads;
// listed only once the phone has been used; it carries the real pad's name when there is one, so the shelf's prompts
// and "controller connected" note don't change just because you picked up the phone
try{Object.defineProperty(navigator,"getGamepads",{configurable:true,value:function(){const L=Array.from(real.call(navigator)||[]);if(!live)return L;const r=L.find(p=>p&&p.connected&&p!==pad);pad.id=r?r.id:"Boxshelf phone remote";L.push(pad);return L}})}catch(e){return}
function set(i,on){buttons[i].pressed=on;buttons[i].touched=on;buttons[i].value=on?1:0;pad.timestamp=performance.now()}
function press(b,down){const i=IDX[b];if(i==null)return;live=true;const now=performance.now();
  if(down){let h=held[b];if(!h||!buttons[i].pressed){h=held[b]={at:now,seq:(h?h.seq:0)+1};set(i,true)}h.until=now+HOLD_MS;return}
  const h=held[b];if(!h||!buttons[i].pressed)return;const seq=h.seq,left=h.at+MIN_MS-now;
  const up=()=>{if(held[b]&&held[b].seq===seq){set(i,false);delete held[b]}};if(left>0)setTimeout(up,left);else up()}
setInterval(()=>{const now=performance.now();for(const b in held)if(held[b].until<now){set(IDX[b],false);delete held[b]}},250);
// typing: into the focused text field when there is one, otherwise as key presses for Settings (typing there searches)
const typable=el=>!!el&&!el.readOnly&&!el.disabled&&(el.tagName==="TEXTAREA"||(el.tagName==="INPUT"&&/^(text|search|email|url|tel|password|)$/i.test(el.type||"")));
function keyEv(k){const t=document.activeElement&&document.activeElement!==document.body?document.activeElement:document.body;
  for(const type of ["keydown","keyup"])t.dispatchEvent(new KeyboardEvent(type,{key:k,code:k.length===1?(/[a-z]/i.test(k)?"Key"+k.toUpperCase():/\d/.test(k)?"Digit"+k:k===" "?"Space":""):k,bubbles:true,cancelable:true}))}
function text(m){const el=document.activeElement;
  if(typable(el)){if(m.key==="Enter"){keyEv("Enter");return}
    const s=el.selectionStart==null?el.value.length:el.selectionStart,e=el.selectionEnd==null?s:el.selectionEnd;
    if(m.key==="Backspace"){if(s===e&&s>0)el.setRangeText("",s-1,s,"end");else if(s!==e)el.setRangeText("",s,e,"end");else return}
    else if(m.key==="Escape"){keyEv("Escape");return}
    else el.setRangeText(String(m.text||""),s,e,"end");
    el.dispatchEvent(new Event("input",{bubbles:true}));return}
  const S=window.Settings;
  if(S&&S.isOpen&&S.isOpen()){if(m.key)keyEv(m.key);else for(const ch of String(m.text||""))keyEv(ch);return}
  if(window.Shelf&&Shelf.toast)Shelf.toast("Open a search box on the TV to type into it (Settings search, the museum finder)")}
api.onCompanionInput(m=>{try{if(!m||typeof m!=="object")return;
  if(m.play){if(window.Shelf&&Shelf.companionPlay)Shelf.companionPlay(m.play);return}
  if(m.text!=null||m.key){text(m);return}
  if(m.b)press(String(m.b),!!m.d)}catch(e){console.warn("companion input",e)}});
})();
