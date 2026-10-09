(async function(){
const list=document.getElementById('updates-list');if(!list)return;
let entries=[],filter='all';
function el(tag,text){const n=document.createElement(tag);if(text)n.textContent=text;return n;}
function render(){list.replaceChildren();const today=new Intl.DateTimeFormat('en-CA',{timeZone:'Africa/Harare',year:'numeric',month:'2-digit',day:'2-digit'}).format(new Date());
entries.filter(x=>x.status==='published'&&(filter==='all'||x.type===filter)).sort((a,b)=>b.date.localeCompare(a.date)).forEach(x=>{
const card=el('article');card.className='membership-card update-card';
if(x.poster&&/^data:image\/(png|jpeg|webp);base64,/.test(x.poster)){const img=el('img');img.src=x.poster;img.alt=x.title+' poster';img.className='update-poster';img.loading='lazy';card.append(img);}
card.append(el('p',x.type==='program'?'Available program':x.date<today?'Past news / meeting':'News / meeting'));
card.append(el('h3',x.title));const date=el('time',new Intl.DateTimeFormat('en-GB',{dateStyle:'long',timeZone:'UTC'}).format(new Date(x.date+'T12:00:00Z')));date.dateTime=x.date;card.append(date);
if(x.location)card.append(el('p',x.location));card.append(el('p',x.summary));
const details=el('details');details.append(el('summary','Read details'));x.body.split(/\n\s*\n/).forEach(p=>details.append(el('p',p)));
const link=el('a','Enquire with ZimFete');link.href='#contact';details.append(link);card.append(details);list.append(card);
});document.getElementById('updates-status').textContent=list.children.length?'':'No announcements in this category.';
}
document.querySelectorAll('[data-filter]').forEach(b=>b.addEventListener('click',()=>{filter=b.dataset.filter;document.querySelectorAll('[data-filter]').forEach(x=>x.setAttribute('aria-pressed',String(x===b)));render();}));
try{const r=await fetch('/api/updates');if(!r.ok)throw Error();entries=await r.json();}catch(e){try{const r=await fetch('content/updates.json');if(!r.ok)throw Error();entries=await r.json();document.getElementById('updates-status').textContent='';}catch(e){document.getElementById('updates-status').textContent='Announcements are temporarily unavailable. Please contact ZimFete.';return;}}render();
})();