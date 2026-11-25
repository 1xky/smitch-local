// Controller behavior checks with a minimal DOM and a simulated Android bridge.
// These do not substitute for an Android WebView/device test.
const fs=require('node:fs'),vm=require('node:vm'),assert=require('node:assert/strict'),path=require('node:path');
const root=path.join(__dirname,'..'),html=fs.readFileSync(path.join(root,'web/index.html'),'utf8');
const all=[],elements={};
class Element {
  constructor(tag='div',attrs=''){
    this.tag=tag;this.children=[];this.style={};this.attributes={};this.className='';this.textContent='';this.disabled=false;
    this.value=(attrs.match(/\bvalue="([^"]*)"/)||[])[1]||'';this.hidden=/\bhidden\b/.test(attrs);
    this.classList={toggle:(name,enabled)=>{const classes=new Set(this.className.split(' ').filter(Boolean));enabled?classes.add(name):classes.delete(name);this.className=[...classes].join(' ');}};
    all.push(this);
  }
  appendChild(el){this.children.push(el);return el;}
  replaceChildren(){this.children=[];}
  setAttribute(name,value){this.attributes[name]=value;}
  scrollIntoView(){}
}
for(const match of html.matchAll(/<([a-z][a-z0-9]*)\b([^>]*\bid="([^"]+)"[^>]*)>/g))elements[match[3]]=new Element(match[1],match[2]);
let visibilityHandler;
const document={getElementById:id=>{assert.ok(elements[id],id);return elements[id];},createElement:tag=>new Element(tag),querySelectorAll:()=>all.filter(el=>['button','input'].includes(el.tag)),addEventListener:(event,fn)=>{if(event==='visibilitychange')visibilityHandler=fn;},hidden:false};
const requests=[];
let failNext=null,fixture={model:'RGB CCT Bulb',mac:'AABBCCDDEEFF',ip:'192.168.1.42',port:80,white:false,rgb:true,cct:true,power:0,brightness:65,temperature:4000,color:'#ff3300'};
const context={document,console,Promise,Error,Number,Math,JSON,window:null};context.window=context;
context.SmitchNative={request(id,payload){const q=JSON.parse(payload);requests.push(q);if(failNext){const error=failNext;failNext=null;context.nativeResult(id,{error});return;}let result={sent:true};if(q.op==='info')result={ip:'192.168.1.42',port:80};if(q.op==='connect')result={...fixture,ip:q.ip,port:q.port};if(q.op==='discover')result={devices:[fixture],limited:false};context.nativeResult(id,{result});}};
vm.createContext(context);vm.runInContext(fs.readFileSync(path.join(root,'web/app.js'),'utf8'),context);
const tick=async()=>{for(let i=0;i<12;i++)await Promise.resolve();};
(async()=>{
  await tick();assert.equal(elements.ip.value,'192.168.1.42');assert.equal(requests.length,1);assert.equal(elements.controller.hidden,true);
  elements.discover.onclick();await tick();assert.equal(elements.results.children.length,1);assert.match(elements.notice.textContent,/Found 1 bulb/);
  elements.results.children[0].onclick();await tick();assert.equal(requests.at(-1).op,'connect');assert.equal(elements.controller.hidden,false);assert.equal(elements['color-controls'].hidden,false);assert.equal(elements['white-controls'].hidden,false);assert.equal(elements['brightness-value'].textContent,'65%');
  elements.on.onclick();await tick();assert.equal(requests.at(-1).action,'power');assert.equal(requests.at(-1).value,true);assert.equal(elements['power-report'].textContent,'Last command: On');
  const count=requests.length;elements.brightness.value='55';elements.brightness.oninput.call(elements.brightness);assert.equal(requests.length,count);elements.brightness.onchange.call(elements.brightness);await tick();assert.equal(requests.at(-1).value,55);
  elements.hue.value='120';elements.saturation.value='100';elements.hue.oninput();assert.equal(elements.preview.style.background,'#00ff00');elements.hue.onchange();await tick();assert.equal(requests.at(-1).value,'#00ff00');
  elements.saturation.value='0';elements.saturation.onchange();await tick();assert.equal(requests.at(-1).value,'#ffffff');
  fixture={...fixture,model:'White Bulb',white:true,rgb:false,cct:true};elements.refresh.onclick();await tick();assert.equal(elements['color-controls'].hidden,true);assert.equal(elements['white-controls'].hidden,false);
  failNext='Could not reach the bulb.';elements.hotspot.onclick();await tick();assert.equal(elements.controller.hidden,true);assert.match(elements.notice.textContent,/Could not reach/);assert.equal(elements.notice.className,'error');
  elements.ssid.value='Home';elements.password.value='password123';elements['setup-form'].onsubmit({preventDefault(){}});assert.equal(elements.password.value,'');await tick();assert.equal(requests.at(-1).op,'setup');assert.equal(requests.at(-1).ssid,'Home');assert.match(elements.notice.textContent,/Setup commands sent/);
  elements.password.value='do-not-retain';document.hidden=true;visibilityHandler();assert.equal(elements.password.value,'');
  assert.equal(requests.some(q=>['login','cloud','signup'].includes(q.op)),false);
  console.log('PASS: UI bridge, discovery, controls, failures, capabilities and password clearing.');
})().catch(error=>{console.error(error);process.exitCode=1;});
