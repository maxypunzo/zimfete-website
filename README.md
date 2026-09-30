# ZimFete SACCO — website

A static one-page site. No build step, no framework, no dependencies to install.

## Files

- `index.html` — all page content and structure
- `styles.css` — corporate design system (colors, type, layout)
- `script.js` — mobile menu toggle (vanilla JS)
- `favicon.svg` — browser tab icon
- `og-image.png` — the image shown when the site is shared on WhatsApp, Facebook, etc.
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
- **Social links** — Facebook, Instagram and Twitter/X currently point to `#` placeholders in the footer; add the real URLs once the pages exist.
- **Custom domain** — once deployed, add your domain (e.g. `zimfete.co.zw`) under Project Settings → Domains in Vercel.

## Making small edits later

Everything is in two files. Text and structure live in `index.html`; colors, spacing and fonts live in `styles.css` under `:root` at the top (change a value there and it updates everywhere it's used). No rebuild step — edit and redeploy.
