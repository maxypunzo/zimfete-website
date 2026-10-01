# ZimFete SACCO: Mifos X / Apache Fineract Fit-Gap Analysis

This document answers: **if ZimFete uses Mifos X, what does it already cover, and what must be built?**

Companion to [digitization-roadmap.md](digitization-roadmap.md).

> **Version note.** "Mifos X" today means the **Apache Fineract** back-end plus the **Mifos X web-app** (Angular) front-end. The older "community-app" (the Mifos X 18.x downloads many people practised on) is no longer maintained. Start production on a current Fineract release and the current web-app, not the practice install.

---

## 1. Summary

| Area | Mifos covers | Build / extend | How the gap is closed |
|---|---|---|---|
| Members, branches, staff, KYC | ~90% | Veteran verification fields and checks | Configuration + data tables |
| Fees (joining, subscription, account opening) | ~75% | Subscription arrears for members with no balance | Configuration + a small custom job/report |
| Savings (incl. interest) | ~95% | Little | Configuration |
| Soft loans | ~90% | Liquidity check before approval | Small custom service |
| Accounting / general ledger | ~90% | Fund-pool and profit-allocation reports, ZiG/USD conversion | SQL reports + procedures |
| Asset financing | ~40% (the money side only) | Asset catalogue, 50% progress, **waiting queue**, procurement, delivery, asset register | **Custom module** |
| Contract farming | ~15% (input credit as a loan, GL postings) | Almost everything | **Custom module** |
| Channels (EcoCash, SMS, WhatsApp, USSD) | ~30% | Payment and messaging connectors | **Custom integrations** |
| Field officer mobile app | ~50% | Offline reliability, GPS/photo visits, asset/farm screens | Customise the Mifos field app or build your own |
| Board dashboard ("the cycle") | ~30% | Cycle, liquidity, queue, farming ROI views | BI tool on a reporting copy of the database |

**In short:** Mifos covers the *money engine* (members, savings, loans, fees, accounting) very well. That is the hardest and riskiest part to build, and you get it free. What you must build are the parts unique to ZimFete: **the asset financing pipeline and queue, contract farming, local payment/SMS connectors, and the cycle dashboard.**

---

## 2. What Mifos covers out of the box

### 2.1 Organisation and members
- **Offices (branches)**: set up Head Office with Marondera, Mackeche, Hwedza, Seke, Murewa, Mutoko, Mudzi and UMP under it. Data and reports can be filtered by office.
- **Staff / loan officers**: each asset finance officer is a staff member assigned to an office, with their own clients and portfolio.
- **Clients**: full member record, ID documents (national ID as an identifier type), addresses, family members/next of kin, photo, document uploads.
- **Data tables** (custom fields, no coding needed): add *Veteran registration number, Veteran verified (Y/N), Verified by, Date* to clients; add *Asset type, Asset cost* to accounts, and so on.
- **Roles and permissions** per user, **maker-checker** (a second person approves) on any action you choose, and a full **audit trail**.

### 2.2 Fees (revenue streams 1–3)
- **Charges** can be attached to clients, savings accounts and loans: one-time fees, activation fees, specified-due-date fees, **monthly fees**, withdrawal fees, overdue penalties.
- **Joining fee**: a one-time client charge or account activation fee.
- **Account opening fee (asset finance)**: an activation charge on the asset deposit savings product.
- **Monthly subscription**: a monthly fee charge on a compulsory membership account, deducted automatically by the scheduler. *See gap 3.2 for members with no balance.*
- Fee income posts automatically to the GL income accounts you map.

### 2.3 Savings (revenue stream 5)
- Savings products with any interest rate. **10% per month is configured as a 120% nominal annual rate**, compounded and posted monthly (or simple, if that is the policy).
- Interest calculation and posting run automatically as scheduled jobs.
- Minimum balances, lock-in periods, withdrawal fees, account holds/blocks, dormancy rules.
- **Recurring deposit** and **fixed deposit** products if you want structured saving plans.
- Statements and transaction history.

### 2.4 Asset deposits (revenue stream 4): the money side
- Create a separate savings product, **"Asset Deposit Account"**: account opening fee, a lock-in so it cannot be withdrawn freely, and its own GL liability account, kept separate from ordinary savings.
- Data table on the account for *chosen asset, total cost, 50% target*.
- Each deposit is receipted and posted to the ledger.
- What Mifos **cannot** do here is the workflow: progress to target, the queue, procurement and delivery. See section 3.1.

### 2.5 Soft loans and asset finance loans (revenue stream 6)
- Loan products: flat or declining balance, repayment frequency, grace periods, min/max amounts and terms, fees, penalties.
- Workflow: **submit → approve → disburse**, with maker-checker and approval permissions by role.
- Guarantors (including holding a guarantor's savings), collateral records.
- Repayment schedules, automatic overdue penalties, rescheduling, write-offs, loan loss provisioning.
- Arrears and **Portfolio at Risk (PAR)** reporting, delinquency buckets, per officer and per branch.
- **Linked savings account / standing instructions**: repayments can be auto-deducted from a member's savings.
- **Asset finance loan** (the remaining 50%): a separate loan product, disbursed to the supplier (payment type "Supplier payment"), so the asset loan book is reported separately from soft loans.

### 2.6 Accounting
- Full double-entry **general ledger**, chart of accounts, product-to-GL mapping (cash or accrual).
- Every deposit, fee, disbursement, repayment and interest posting creates journal entries automatically.
- Manual journal entries (for farming income and expenses until that module exists), accounting closures, trial balance, balance sheet, income statement.
- **Funds**: loans can be tagged with the fund that financed them (e.g. "Member savings pool", "Asset deposit pool", "Retained profit"). This is the starting point for tracking the cycle.
- **Tellers and cashiers**: cash allocated to each officer or branch, with end-of-day settlement. This is useful for 8 locations handling cash.
- **Payment types**: Cash, EcoCash, Bank transfer, so reconciliation per channel is possible.

### 2.7 Reporting and integration
- Standard reports (client lists, portfolio, arrears, PAR, collections, GL reports).
- **Custom SQL reports** that can be added through the UI with no code. Many ZimFete reports (asset deposit progress, subscriptions in arrears) can start this way.
- **REST API** for everything, plus **webhooks (hooks)** on events. This is how custom modules plug in.
- SMS campaigns module (needs an SMS gateway connector, see 3.4).
- Open-source **Mifos field officer app** (Android) and **Mifos mobile banking app** (member self-service).

---

## 3. What must be built or extended

### 3.1 Asset Financing module (largest gap, custom build)
Mifos handles the money; ZimFete needs an application around it for:

| Feature | Notes |
|---|---|
| **Asset catalogue** | Asset types (borehole, tractor, solar, etc.), standard cost, suppliers, quotes with expiry dates, price updates |
| **Asset application** | Member picks an asset → system calculates the 50% target → opens the Asset Deposit Account in Mifos via API and charges the opening fee |
| **Progress tracker** | Deposited vs target, % complete, average monthly deposit, **estimated date to reach 50%**, SMS updates to the member |
| **Waiting queue** | Members who reached 50% are ranked by your queue rule (date reached, date opened, priority); status: *Saving → Qualified → In queue → Procuring → Delivered → Repaying → Paid off* |
| **Cash-needed forecast** | Total cash needed to serve the queue this month and next, compared with available cash (feeds the liquidity check in 3.3) |
| **Procurement** | Purchase order to the supplier, approval, supplier payment |
| **Delivery / installation** | Officer confirms with **photos, GPS location, serial number**, member signature |
| **Conversion** | One action: apply the deposit, create and disburse the asset finance loan for the balance in Mifos via API |
| **Asset register** | Every financed asset: location, serial number, condition visits, insurance, ownership (SACCO-owned until paid off), repossession/transfer history |

Mifos data tables and SQL reports can give a **stop-gap version** (fields for the asset and a progress/queue report) while the full module is built.

### 3.2 Membership subscriptions: small extension
Monthly fees in Mifos are deducted from a savings balance. A member with no balance needs a rule:
- Option A: allow the membership account to go overdrawn so arrears are visible (configuration only).
- Option B: a small custom job + report that tracks subscription arrears and flags or suspends members after N months unpaid.
**Decide the policy first**, then pick the option.

### 3.3 Liquidity check before loan approval: small custom service
Mifos does not know that asset deposit money must be available when members reach 50%. Build a service that, before a soft loan is approved, computes:

```
Available to lend = Cash and bank balances
                  - Cash needed for the asset queue (next 30–60 days)
                  - Savings withdrawal reserve (policy %)
```

It warns or blocks approval when a loan would push cash below the reserve. This can start as a daily report the approver must check, then become an automatic check via the API.

### 3.4 Payment and messaging connectors: custom integrations
| Integration | Why it is needed |
|---|---|
| **EcoCash (and bank) collections** | Members pay by EcoCash; payments must post automatically to the correct Mifos account, with daily reconciliation. Build a connector (or adapt Mifos Payment Hub) to EcoCash's merchant API. |
| **SMS gateway** | Receipts, due-date reminders, asset-progress updates. Mifos's SMS module needs an adapter for a local Zimbabwean bulk-SMS provider. |
| **WhatsApp / USSD** (later) | Balance and asset-progress enquiry for members without smartphones (USSD) or who prefer WhatsApp. Custom, via a USSD aggregator / WhatsApp Business API calling the Mifos API. |

### 3.5 Contract Farming module (custom build)
Mifos has no agriculture features. The only reusable pieces are: **input credit can be modelled as a Mifos loan** (inputs issued = disbursement in kind; harvest proceeds = repayment), and **all postings go to the Mifos GL**. Build:

| Feature | Notes |
|---|---|
| Farmer and plot registry | Linked to the Mifos client where the farmer is a member; plot GPS and size |
| Seasons and contracts | Crop, hectares, expected yield, agreed price, **offtaker (buyer)** |
| Input management | Stock of seed/fertiliser/chemicals, issuing to farmers at cost, a simple store/inventory |
| Field monitoring | Officer visits, crop stage, photos, problems, yield estimates |
| Harvest and sales | Quantities delivered, grading, sale to the offtaker, payment received |
| Settlement | Proceeds − input credit − fees = farmer payout and ZimFete profit, posted to the GL |
| Project P&L / ROI | Money invested (from loan profits) vs return, per season, crop and location |

### 3.6 Field officer app: extend or build
The open-source Mifos field officer app does client, loan and savings work, but:
- offline use in areas with poor signal needs testing and probably hardening;
- it has no screens for asset delivery, asset condition checks, or farm visits (GPS + photos).
**Option:** use the Mifos app for collections, and build one lightweight offline-first app (e.g. Flutter/React Native) for asset and farm field work that syncs to the custom modules.

### 3.7 Reporting and dashboards: extend
- **Cycle dashboard**: money in by stream → loan book → farming investment → asset portfolio → returns, plus liquidity, PAR, the queue and farming ROI.
- **Profit allocation report**: how profit from soft loans and farming was moved into asset financing.
- Regulator and AGM reports in the required format.
Recommended: an open-source BI tool (e.g. Metabase) on a **read-only reporting copy** of the Mifos and custom-module databases.

### 3.8 Currency (USD and ZiG): configure and verify
Mifos supports multiple currencies, but each product/account has **one** currency and there is no built-in FX conversion between accounts. Verify ZiG (ISO code **ZWG**) is in your Fineract currency list (add it if not), keep USD and ZWG products separate, and handle conversions with journal entries under a written procedure.

---

## 4. Recommended architecture

```
 ┌─────────────────────────────────────────────────────────────────────┐
 │  Mifos X web-app (staff)   Mifos field app   Custom field app (GPS) │
 │  Member SMS / WhatsApp / USSD          EcoCash & bank connectors    │
 └───────────────┬───────────────────────────────┬─────────────────────┘
                 │                               │
 ┌───────────────▼───────────────┐   ┌───────────▼──────────────────────┐
 │  APACHE FINERACT (unchanged)  │◄──┤  ZIMFETE EXTENSIONS (custom app)  │
 │  Clients · Savings · Loans    │API│  Asset Financing & queue          │
 │  Fees · GL · Tellers · Funds  │──►│  Contract Farming                 │
 │  Reports · Hooks · Audit      │hooks Liquidity check · Integrations │
 └───────────────┬───────────────┘   └───────────┬──────────────────────┘
                 └──────────► Reporting DB copy ◄┘──► Metabase dashboards
```

**Rule: do not modify (fork) Fineract's core code.** Build ZimFete-specific features as a separate application that talks to Fineract through its **REST API** and **webhooks**. That way you can upgrade Fineract for security fixes without redoing your custom work. Use Fineract's built-in configuration (products, charges, data tables, SQL reports, maker-checker) wherever it fits before writing any code.

---

## 5. Revised build order with Mifos

| Phase | What | Mostly configuration or build? |
|---|---|---|
| 0 | Rulebook, chart of accounts, data clean-up, install a current Fineract + web-app on a proper server with backups | Setup |
| 1 | Offices, staff, roles, clients + veteran data table, fee charges, savings and asset deposit products, GL mapping, tellers, data migration, parallel run | **Configuration** |
| 2 | Soft loan and asset finance loan products, approval workflow, PAR reports; SMS connector; liquidity report | Configuration + **small builds** |
| 3 | EcoCash connector; Asset Financing module (catalogue, progress, queue, procurement, delivery, conversion, register); field app for asset visits | **Build** |
| 4 | Contract Farming module (using input-credit loans in Mifos) | **Build** |
| 5 | Dashboards, member self-service (Mifos mobile app / USSD / WhatsApp), regulator reports | Build + configuration |

Because Phases 1–2 are mostly configuration, ZimFete can **go live on members, savings, fees and soft loans within a few months**. Custom development then concentrates on Phases 3–4.

---

## 6. Skills and running costs to plan for

- **Fineract administrator** (in-house or partner): product setup, users, reports, upgrades.
- **Developer(s)** for the extensions app and integrations (Fineract REST API knowledge).
- **Hosting**: a cloud or local server (Java + MySQL/MariaDB or PostgreSQL), HTTPS, **daily off-site backups**, monitoring. Fineract is free; hosting, support and development are not.
- **Support partner** option: several Mifos implementation partners offer hosting and support. That is worth comparing against running it yourselves.

---

## 7. Proof-of-concept checklist (using your practice experience)

Before committing, re-install a **current** Fineract + Mifos web-app and run one complete ZimFete member through it:

1. Create 2 offices (e.g. Marondera, Hwedza) and an officer user in each.
2. Create a member with the veteran data table; charge the joining fee.
3. Open a membership savings account with the monthly subscription fee; run the monthly job.
4. Open a savings account at 120% nominal annual (10%/month); post interest and check the amount.
5. Open an Asset Deposit Account for a $4,000 borehole (target $2,000) with an opening fee; make deposits.
6. Disburse a soft loan tagged with the fund "Asset deposit pool"; repay it; let one instalment go overdue and check the penalty.
7. When the deposit hits $2,000, disburse a $2,000 asset finance loan to the supplier.
8. Check the trial balance, balance sheet and income statement: is every amount where you expect it?

Anything that fails or feels awkward in this test goes onto the build list.
