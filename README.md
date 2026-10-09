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