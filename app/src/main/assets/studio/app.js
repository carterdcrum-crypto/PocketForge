(function(){
'use strict';
const config=JSON.parse(document.getElementById('app-config').textContent);
const spec=config.spec, project=config.projectId;
let active=spec.screens[0], query='', noticeTimer, deleted=null;
const $=id=>document.getElementById(id);
const el=(tag,text,cls)=>{const e=document.createElement(tag);if(text!=null)e.textContent=text;if(cls)e.className=cls;return e;};
const btn=(text,action,cls)=>{const b=el('button',text,cls);b.type='button';b.onclick=action;return b;};
const icon={list:'≡',checklist:'✓',ledger:'↗',calculator:'×',info:'i'};
const key=s=>'pocketforge.data.v1.'+project+'.'+s.id;
function read(s){try{const value=JSON.parse(localStorage.getItem(key(s))||'[]');if(!Array.isArray(value))throw Error();return value;}catch(e){notice('Saved entries could not be read. They have not been replaced.');return null;}}
function write(s,rows){try{localStorage.setItem(key(s),JSON.stringify(rows));return true;}catch(e){notice('This device could not save your change. Free some space and try again.');return false;}}
function notice(text){$('notice').textContent=text;clearTimeout(noticeTimer);noticeTimer=setTimeout(()=>{$('notice').textContent='';},6500);}
const amount=n=>Number(n).toLocaleString(undefined,{maximumFractionDigits:2,minimumFractionDigits:2});
function setTheme(){document.body.classList.toggle('light',!spec.dark);document.documentElement.style.setProperty('--accent',spec.accent);const hex=spec.accent.slice(1);const rgb=[0,2,4].map(i=>parseInt(hex.slice(i,i+2),16)/255).map(c=>c<=.04045?c/12.92:((c+.055)/1.055)**2.4);document.documentElement.style.setProperty('--ink',(.2126*rgb[0]+.7152*rgb[1]+.0722*rgb[2])>.179?'#101710':'#ffffff');}
function navigation(){const nav=$('navigation');nav.replaceChildren();spec.screens.forEach(s=>{const b=btn('',()=>{active=s;query='';render();window.scrollTo(0,0);});b.append(el('span',icon[s.type],'nav-icon'),el('span',s.title));if(s.id===active.id)b.setAttribute('aria-current','page');b.setAttribute('aria-label',s.title);nav.append(b);});}
function render(){
 navigation();const main=$('main');main.replaceChildren();const hero=el('section',null,'hero');hero.append(el('h1',active.title),el('p',active.description,'muted'));main.append(hero);
 if(active.type==='info'){main.append(el('article',active.content,'info'));}
 else if(active.type==='calculator'){calculator(main);}
 else{collection(main);}
 main.append(el('footer','Saved on this device. No account needed.'));
}
function collection(main){
 const rows=read(active);if(rows===null){main.append(el('p','Your saved entries need attention. Export this app or contact support before resetting any data.','error'));return;}
 if(active.type==='ledger'||active.type==='checklist'){
 const summary=el('section',null,'summary');const values=el('div');const label=active.type==='ledger'?'Total recorded':'Completed';let total='';
 if(active.type==='ledger'){const f=active.fields.find(f=>f.type==='number');total=active.unit+amount(rows.reduce((n,r)=>n+(Number(r.values[f.id])||0),0));}
 else total=rows.filter(r=>r.done).length+' / '+rows.length;
 values.append(el('p',label,'eyebrow'),el('p',total,'stat'));summary.append(values,el('span',rows.length===1?'1 entry':rows.length+' entries','muted'));main.append(summary);
 }
 const toolbar=el('div',null,'toolbar');const search=el('input');search.type='search';search.placeholder='Search entries';search.setAttribute('aria-label','Search entries');search.value=query;search.oninput=()=>{query=search.value;renderRows(list,rows);};toolbar.append(search,btn('+ Add',()=>edit(null),'primary'));main.append(toolbar);const list=el('section');list.setAttribute('aria-label','Entries');main.append(list);renderRows(list,rows);
}
function renderRows(list,rows){
 list.replaceChildren();const found=rows.filter(r=>Object.values(r.values).join(' ').toLowerCase().includes(query.toLowerCase()));
 if(!found.length){const empty=el('div',null,'empty');empty.append(el('div',icon[active.type],'empty-icon'),el('h2',query?'No matches yet':'Your space, ready to go.'),el('p',query?'Try a different word.':'Add your first entry. Everything here will come from you.'));if(!query)empty.append(btn('Add first entry',()=>edit(null),'primary'));list.append(empty);return;}
 found.slice().reverse().forEach(row=>{
 const card=el('article',null,'row'+(row.done?' done':''));const head=el('div',null,'row-head');const title=String(row.values[active.fields[0].id]||'Untitled');
 if(active.type==='checklist'){const check=btn(row.done?'✓':'○',()=>{const all=read(active);if(!all)return;const next=all.map(r=>r.id===row.id?{...r,done:!r.done}:r);if(write(active,next))render();},'check'+(row.done?' checked':''));check.setAttribute('aria-label',(row.done?'Mark incomplete: ':'Complete: ')+title);check.setAttribute('aria-pressed',String(!!row.done));head.append(check);}
 head.append(el('h2',title));card.append(head);
 active.fields.slice(1).forEach(f=>{const value=row.values[f.id];if(value!==''&&value!=null)card.append(el('p',f.type==='number'&&active.type==='ledger'?active.unit+amount(value):f.label+': '+value,f.type==='number'&&active.type==='ledger'?'amount':'details'));});
 const actions=el('div',null,'row-actions');actions.append(btn('Edit',()=>edit(row)),btn('Delete',()=>confirmDelete(row)));card.append(actions);list.append(card);
 });
}
function fieldInput(field,value){const input=el(field.type==='multiline'?'textarea':'input');input.id='field-'+field.id;input.name=field.id;if(field.type!=='multiline')input.type=field.type==='number'?'number':field.type==='date'?'date':'text';if(field.type==='number'){input.step='any';input.inputMode='decimal';}input.maxLength=4000;input.required=field.required;input.value=value==null?'':value;return input;}
function edit(row){
 const screen=active, dialog=$('editor');dialog.replaceChildren();const form=el('form');form.append(el('h2',row?'Edit entry':'New entry'));screen.fields.forEach(f=>{const label=el('label',f.label+(f.required?'':' (optional)'));label.htmlFor='field-'+f.id;form.append(label,fieldInput(f,row?row.values[f.id]:''));});const error=el('p','','error');error.setAttribute('role','alert');form.append(error);
 const actions=el('div',null,'form-actions');actions.append(btn('Cancel',()=>dialog.close(),'secondary'));const save=el('button','Save entry','primary');save.type='submit';actions.append(save);form.append(actions);
 form.onsubmit=e=>{e.preventDefault();const values={};for(const f of screen.fields){const value=form.elements.namedItem(f.id).value.trim();if(f.required&&!value){error.textContent='Please fill in '+f.label+'.';return;}if(f.type==='number'&&value&&!Number.isFinite(Number(value))){error.textContent='Enter a valid number for '+f.label+'.';return;}values[f.id]=value;}
 const rows=read(screen);if(!rows)return;const item={id:row?row.id:Date.now().toString(36)+Math.random().toString(36).slice(2),done:row?!!row.done:false,values:{...(row?row.values:{}),...values}};const next=row?rows.map(r=>r.id===row.id?item:r):[...rows,item];if(write(screen,next)){dialog.close();render();notice('Saved on this device.');}};
 dialog.append(form);dialog.showModal();
}
function confirmDelete(row){const dialog=$('editor'),screen=active;dialog.replaceChildren();dialog.append(el('h2','Delete this entry?'),el('p','You can undo this until you leave this screen.','muted'));const actions=el('div',null,'form-actions');actions.append(btn('Keep entry',()=>dialog.close(),'secondary'),btn('Delete entry',()=>{const rows=read(screen);if(rows&&write(screen,rows.filter(r=>r.id!==row.id))){deleted={screen,row,index:rows.findIndex(r=>r.id===row.id)};dialog.close();render();const n=$('notice');n.replaceChildren(el('span','Entry deleted. '),btn('Undo',undoDelete));clearTimeout(noticeTimer);}},'primary'));dialog.append(actions);dialog.showModal();}
function undoDelete(){if(!deleted)return;const {screen,row,index}=deleted;const rows=read(screen);if(!rows)return;rows.splice(index,0,row);if(write(screen,rows)){deleted=null;render();notice('Entry restored.');}}
function calculator(main){const form=el('form',null,'calc');const result=el('output','','result');result.setAttribute('aria-live','polite');const error=el('p','','error');error.setAttribute('role','alert');active.fields.forEach(f=>{const label=el('label',f.label);label.htmlFor='field-'+f.id;form.append(label,fieldInput(f,''));});const go=el('button','Calculate','primary');go.type='submit';go.style.marginTop='22px';form.append(go,result,error);form.onsubmit=e=>{e.preventDefault();error.textContent='';result.textContent='';const values=active.fields.map(f=>Number(form.elements.namedItem(f.id).value));if(values.some(n=>!Number.isFinite(n))){error.textContent='Please enter valid numbers.';return;}let n=values[0];if(active.operation==='sum')n=values.reduce((a,b)=>a+b,0);if(active.operation==='product')n=values.reduce((a,b)=>a*b,1);if(active.operation==='difference')n=values.slice(1).reduce((a,b)=>a-b,n);if(active.operation==='ratio'){if(values.slice(1).some(v=>v===0)){error.textContent='Choose a number other than zero for the divisor.';return;}n=values.slice(1).reduce((a,b)=>a/b,n);}if(!Number.isFinite(n)){error.textContent='The result is too large. Try smaller numbers.';return;}result.textContent=active.unit+amount(n);};main.append(form);}
setTheme();document.title=spec.name;$('app-name').textContent=spec.name;$('app-mark').textContent=spec.name.slice(0,1).toUpperCase();$('tagline').textContent=spec.tagline;render();
})();
