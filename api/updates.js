const crypto=require('node:crypto');
const seeds=require('../content/updates.json');
const key='zimfete:updates';
async function redis(command){const r=await fetch(process.env.UPSTASH_REDIS_REST_URL,{method:'POST',headers:{Authorization:'Bearer '+process.env.UPSTASH_REDIS_REST_TOKEN,'Content-Type':'application/json'},body:JSON.stringify(command),signal:AbortSignal.timeout(10000)});const data=await r.json();if(!r.ok||data.error)throw Error('Storage unavailable');return data.result;}
function valid(x){if(!x||typeof x!=='object')return false;return /^[a-zA-Z0-9-]{1,80}$/.test(x.id)&&['news','program'].includes(x.type)&&['published','draft','archived'].includes(x.status)&&/^\d{4}-\d{2}-\d{2}$/.test(x.date)&&!isNaN(Date.parse(x.date))&&['title','summary','body','location','poster'].every(k=>typeof x[k]==='string')&&x.title.trim().length>0&&x.title.length<=160&&x.summary.length<=600&&x.body.length<=12000&&x.location.length<=200&&x.poster.length<=360000&&(!x.poster||/^data:image\/(png|jpeg|webp);base64,[A-Za-z0-9+/=]+$/.test(x.poster));}
module.exports=async function(req,res){
res.setHeader('Cache-Control','no-store');res.setHeader('X-Content-Type-Options','nosniff');
if(!['GET','POST'].includes(req.method)){res.setHeader('Allow','GET, POST');return res.status(405).json({error:'Method not allowed'});}
const configured=process.env.UPSTASH_REDIS_REST_URL&&process.env.UPSTASH_REDIS_REST_TOKEN;
const admin=req.method==='POST'||req.query.admin==='1';
if(admin&&!configured)return res.status(503).json({error:'Staff editing needs the one-time database setup. See the website README.'});
try{
if(admin){
if(!process.env.UPDATES_ADMIN_PASSWORD||process.env.UPDATES_ADMIN_PASSWORD.length<16)return res.status(503).json({error:'Staff editing needs an admin password of at least 16 characters.'});
const ip=crypto.createHash('sha256').update(String(req.headers['x-forwarded-for']||'unknown')).digest('hex');
const bucket='zimfete:login:'+ip+':'+Math.floor(Date.now()/60000);const attempts=await redis(['INCR',bucket]);if(attempts===1)await redis(['EXPIRE',bucket,120]);if(attempts>30)return res.status(429).json({error:'Too many requests. Wait a minute and try again.'});
const supplied=String(req.headers.authorization||'').replace(/^Bearer /,'');
const digest=s=>crypto.createHash('sha256').update(s).digest();
if(!crypto.timingSafeEqual(digest(supplied),digest(process.env.UPDATES_ADMIN_PASSWORD)))return res.status(401).json({error:'Incorrect staff password.'});
}
if(req.method==='POST'){
let x=req.body;if(typeof x==='string'){try{x=JSON.parse(x);}catch(e){return res.status(400).json({error:'Invalid announcement'});}}
if(!valid(x))return res.status(400).json({error:'Check the announcement fields and poster size.'});
const clean={};for(const field of ['id','type','title','date','location','status','summary','body','poster'])clean[field]=x[field];clean.updatedAt=new Date().toISOString();
await redis(['HSET',key,x.id,JSON.stringify(clean)]);return res.status(200).json({ok:true});
}
let entries=new Map(seeds.map(x=>[x.id,x]));if(configured){const values=await redis(['HGETALL',key]);for(let i=0;i<values.length;i+=2){const x=JSON.parse(values[i+1]);entries.set(x.id,x);}}
return res.status(200).json(Array.from(entries.values()).filter(x=>admin||x.status==='published'));
}catch(e){return res.status(503).json({error:'Updates are temporarily unavailable. Please try again.'});}
};
