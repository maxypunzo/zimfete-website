# ZimFete SACCO — website

A static one-page site. No build step, no framework, no dependencies to install.

## Files

- `index.html` — all page content and structure
- `styles.css` — corporate design system (colors, type, layout)
- `script.js` — mobile menu toggle (vanilla JS)
- `favicon.svg` — browser tab icon
- `og-image.png` — the image shown when the site is shared on WhatsApp, Facebook, etc.
- `images/` — logo (`logo.png`, `logo-white.png` for the footer) and project photos
- `robots.txt` — allows search engines to index the site

## Deploy to Vercel

**Option A — Vercel CLI**
```
npm i -g vercel
cd zimfete-website
vercel
```
Follow the prompts (link or create a project, keep the defaults — no framework, no build command, output directory is the project root). Vercel will give you a live URL immediately, and a production URL after `vercel --prod`.

**Option B — Vercel dashboard (no CLI)**
1. Push this folder to a GitHub repo.
2. On vercel.com, click "Add New… → Project" and import that repo.
3. Framework preset: "Other". Leave build command and output directory blank.
4. Deploy.

## Before you go live — update these

- **Bank account number** in the footer of `index.html` — there are two different numbers across the documents you've shared (company profile vs. this site); confirm the correct one before publishing.
- **Custom domain** — once deployed, add your domain (e.g. `zimfete.co.zw`) under Project Settings → Domains in Vercel.

## Making small edits later

Everything is in two files. Text and structure live in `index.html`; colors, spacing and fonts live in `styles.css` under `:root` at the top (change a value there and it updates everywhere it's used). No rebuild step — edit and redeploy.

<!-- /////////////////////////////////////// -->

The company have people who deposit they money into the company account its a SACCO.   Then they offer software loans. The profit from these soft loans will then  Fund profitable agribusiness to invest into ( usually contract farming) Then profit from both will be used for Asset Financing (either borehole, tractor or any Asset). For Asset financing people are required to deposit half of the total cost before the project start.  If a user deposit is not yet meet the minimum required number,  they have to wait until they reach that amount for them to get the Asset.  While waiting for the user amount to meat the conditions his or her deposits will be used to fund soft loans.  That how the cycle works.  Now everything about this company is manual there is no Electronic system whatsoever except the excel sheet which they keep the loans and assets data.  They want to digitize their organization from system for loan management,  a system for Asset management  and a system for their agribusiness contract farming.  What is the proper road map to accomplish this.

In all these locations in Marondera, Mackeche, Hwedza, Seke Murewa, Mutoko Mudzi and UMP, we have asset finance officers. Revenue comes from 
joining fees for people from the veteran community to be a member of the sacco
subscription fees per month for members
Account opening (paid once for people who want asset financing - they open an account which they are required to deposit at least 50% of the total cost of the asset they want to aquire)
Account deposits (people deposit money into their account until it reaches the minimum required tresh hold to get an asset financing)
Savings (people who save their money to the sacco which they will get 10% interest per month)
Loan repayment(people who repay their asset finance loan)
## Weekly news and programs: staff editing

The public page shows the Seke meeting and iFLA piggery program from content/updates.json until the database is connected. Staff use /admin/ to add or edit news, meetings and programs, upload posters, and choose Draft, Published or Archived. Updates save in a database, without source edits or redeployment. Each save updates one announcement; concurrent edits of the same announcement use the last save.

### One-time Vercel setup
1. Create a persistent Upstash Redis database (directly or through the Vercel Marketplace).
2. In Vercel Project Settings > Environment Variables, set UPSTASH_REDIS_REST_URL and UPSTASH_REDIS_REST_TOKEN using the database credentials.
3. Set UPDATES_ADMIN_PASSWORD to a unique staff password of at least 16 characters. Keep all three values server-side, never in website files.
4. Deploy this version to Vercel, then open /admin/ over HTTPS and sign in. If the integration supplies different variable names, map the credentials to the names above.
5. Check a draft is absent publicly, publish it and check the website, then archive it and confirm it disappears. Uploaded posters must be PNG, JPEG or WebP and no larger than 250 KB.
6. Keep the database persistent and backed up. Rotate the password in Vercel and redeploy when staff access changes.

The server uses the documented Upstash REST command interface: https://upstash.com/docs/redis/features/restapi
The password stays in browser memory only while signed in. Refreshing or signing out clears it. The database credentials are never sent to the browser. Staff login requests are rate limited to 30 per minute per IP.

An API/database outage falls back to the original two announcements. This may show the original version of an edited or archived seeded notice until service recovers. Meeting dates in the past are labelled as past news; staff should archive outdated notices.
No membership identity data is collected by this website. Officers collect and verify it on the membership form.
The piggery first repayment is six months from loan issue; chemical costs and the instalment schedule still need confirmation before finance is accepted.

### Verification
Run node --test tests/updates.test.cjs from the project root. Tests mock storage; live Vercel credentials are not needed.
