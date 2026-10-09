const fs=require('node:fs'),vm=require('node:vm'),assert=require('node:assert/strict');
for(const file of ['script.js','updates.js','admin/admin.js','api/updates.js'])new vm.Script(fs.readFileSync(file,'utf8'),{filename:file});
const html=fs.readFileSync('index.html','utf8');for(const id of ['membership','community','updates'])assert.equal((html.match(new RegExp('id="'+id+'"','g'))||[]).length,1);
assert(!html.includes('General &amp; SME membership'));assert(html.includes('$10 joining fee'));assert(html.includes('updates.js'));
const seeds=require('../content/updates.json');assert.equal(seeds.length,2);assert(seeds[1].body.includes('six months from the date the loan is issued'));
const handler=require('../api/updates.js');
let records=new Map(),rate=new Map();
global.fetch=async(url,options)=>{const [cmd,k,...args]=JSON.parse(options.body);let result;
if(cmd==='INCR'){result=(rate.get(k)||0)+1;rate.set(k,result);}
if(cmd==='EXPIRE')result=1;
if(cmd==='HSET'){records.set(args[0],args[1]);result=1;}
if(cmd==='HGETALL')result=Array.from(records.entries()).flat();
return {ok:true,json:async()=>({result})};};
async function call(method,body,admin=false,password=''){const res={headers:{},setHeader(k,v){this.headers[k]=v;},status(code){this.code=code;return this;},json(body){this.body=body;return this;}};await handler({method,body,query:admin?{admin:'1'}:{},headers:{authorization:password?'Bearer '+password:'','x-forwarded-for':'test'}},res);return res;}
(async()=>{
delete process.env.UPSTASH_REDIS_REST_URL;delete process.env.UPSTASH_REDIS_REST_TOKEN;
let r=await call('GET');assert.equal(r.code,200);assert.equal(r.body.length,2);
r=await call('POST',seeds[0]);assert.equal(r.code,503);
process.env.UPSTASH_REDIS_REST_URL='https://test.upstash.io';process.env.UPSTASH_REDIS_REST_TOKEN='test';process.env.UPDATES_ADMIN_PASSWORD='a-test-password-long-enough';
r=await call('POST',seeds[0],false,'wrong');assert.equal(r.code,401);assert.equal(records.size,0);
const password=process.env.UPDATES_ADMIN_PASSWORD;
const draft={...seeds[1],id:'new-program',status:'draft',title:'Draft test'};
r=await call('POST',draft,false,password);assert.equal(r.code,200);
r=await call('GET');assert.equal(r.body.length,2);
r=await call('GET',null,true,password);assert.equal(r.body.length,3);
r=await call('POST',{...draft,status:'published'},false,password);assert.equal(r.code,200);
r=await call('GET');assert.equal(r.body.length,3);
r=await call('POST',{...seeds[0],status:'archived'},false,password);assert.equal(r.code,200);
r=await call('GET');assert(!r.body.some(x=>x.id===seeds[0].id));
r=await call('POST',{...draft,poster:'javascript:alert(1)'},false,password);assert.equal(r.code,400);
r=await call('POST',{...draft,poster:'data:image/png;base64,aGVsbG8='},false,password);assert.equal(r.code,200);
r=await call('POST',{...draft,title:''},false,password);assert.equal(r.code,400);
r=await call('DELETE');assert.equal(r.code,405);
global.fetch=async()=>{throw Error('outage');};r=await call('POST',draft,false,password);assert.equal(r.code,503);
console.log('PASS: JavaScript syntax, membership integration, public seed data, authentication, draft visibility, publish, archive, poster validation, input validation and storage failure.');
})().catch(e=>{console.error(e);process.exitCode=1;});
