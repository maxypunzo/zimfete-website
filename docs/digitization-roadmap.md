# ZimFete SACCO — Digitization Roadmap

**Goal:** move ZimFete from paper and Excel to one connected system covering members, savings, soft loans, asset financing and contract farming. Asset finance officers in Marondera, Mackeche, Hwedza, Seke, Murewa, Mutoko, Mudzi and UMP should all use it.

---

## 1. The most important design decision: one system, not three

The business is a **single cycle of money**:

```
 Member money in                          Uses of money                  Returns
 ───────────────                          ─────────────                  ───────
 Joining fees ───────┐
 Monthly subs ───────┤
 Account opening ────┼──►  SACCO FUND  ──►  Soft loans ──────────────► interest
 Asset deposits ─────┤    (one ledger)  ──►  Contract farming ────────► crop profit
 Savings ────────────┤                  ──►  Asset financing (50%) ──► loan repayments
 Loan repayments ────┘          ▲                                          │
                                └──────────────────────────────────────────┘
```

Every dollar a member deposits, whether savings or an asset deposit that is still below the 50% threshold, is lent out as a soft loan. Profit from loans goes into farming, and profit from both goes into assets. If you build three separate systems (loans, assets, farming), you get three separate sets of books. Nobody will be able to answer the questions that matter most:

- *How much member money is currently out on loan?*
- *If 5 members reach their 50% this month, do we have the cash to buy their assets?*
- *Which profit came from loans and which from farming, and where did it go?*

**So build one platform with one general ledger (the books) in the middle.** Loans, Assets and Agribusiness are three *modules* that sit on top of it. Every transaction in any module posts to the same ledger.

```
 ┌──────────────────────────────────────────────────────────────────┐
 │  Channels: Office web app · Field officer mobile app (offline)   │
 │            Member SMS/WhatsApp/USSD · EcoCash/bank feeds         │
 ├──────────────┬──────────────────┬───────────────────┬────────────┤
 │ Members &    │  Soft Loans      │  Asset Financing  │ Agribusiness│
 │ Savings      │  module          │  module           │ (contract   │
 │ module       │                  │                   │  farming)   │
 ├──────────────┴──────────────────┴───────────────────┴────────────┤
 │  CORE: General ledger · Fund pools · Fees · Receipts · Audit log │
 │        Users, roles & branches (8 locations) · Reports           │
 └──────────────────────────────────────────────────────────────────┘
```

---

## 2. Revenue streams → where each one lives in the system

| # | Revenue / money in | Account type in system | Module | Key rules to capture |
|---|---|---|---|---|
| 1 | Joining fee (veterans) | Fee income | Members | Veteran eligibility check, once per member |
| 2 | Monthly subscription | Fee income, billed monthly | Members | Auto-bill each month, arrears list, suspension rules |
| 3 | Account opening (asset finance) | Fee income | Asset Financing | Paid once when an asset account is opened |
| 4 | Asset account deposits | **Liability**: the member's money, held toward 50% | Asset Financing | Target = 50% of asset cost; progress %; lent out as soft loans while waiting |
| 5 | Savings (10%/month interest) | **Liability**: the member's money plus interest owed | Members & Savings | Interest calculation, withdrawal rules and notice periods |
| 6 | Loan repayments | Reduces loan receivable; interest = income | Soft Loans and Asset Financing | Schedule, arrears, penalties |

> Note: items 4 and 5 are **not revenue**. They are money owed back to members (or owed as an asset). The system must keep them separate from income, or the SACCO will look more profitable than it is.

---

## 3. Phased roadmap (about 15–18 months)

### Phase 0: Foundations (Months 0–2). No software yet.

Software can only automate rules that have been written down. This phase decides whether the project succeeds.

1. **Map current processes** at head office and at 2 or 3 field locations: how money is received, recorded, approved and paid out.
2. **Write the rulebook** (policies the system will enforce):
   - Fees: joining, subscription, account opening (amounts, due dates, penalties)
   - Savings: interest rate, how it is calculated (simple or compound), withdrawal rules
   - Soft loans: who qualifies, maximum amount (for example, a multiple of savings), interest, term, guarantors, penalties, approval limits per role
   - Asset financing: the 50% rule, **queue rules** (first to reach 50% is served first? Is there a limit per month?), repayment terms for the other 50%, ownership and repossession
   - Contract farming: contract terms, input costing, profit-sharing
3. **Chart of accounts**: agree the ledger accounts, including separate *fund pools* (member savings, asset deposits, loan book, farming investments, asset portfolio, retained profit).
4. **Clean the Excel data**: give every member a unique member number, confirm balances, reconcile loans against bank and EcoCash statements. Get every balance signed off. This becomes the opening balance in the new system.
5. **Regulatory and legal check**: confirm reporting obligations under the Co-operative Societies Act and with the Registrar, and whether deposit-taking triggers any RBZ requirements. Check data protection (Cyber and Data Protection Act).
6. **Infrastructure**: connectivity at each location, Android phones or tablets for the asset finance officers, mobile money and bank accounts for collections.
7. **Build-vs-buy decision** (see section 5).

**Deliverable:** signed-off rulebook, clean opening balances, chosen platform or vendor.

---

### Phase 1: Core and Members (Months 2–5)

*Goes live first because every other module depends on it.*

- Member registry: national ID, veteran verification, next of kin, photo, location/branch, assigned officer
- Fee engine: joining fee, monthly subscription billing and arrears, account opening fee
- Savings accounts: deposits, withdrawals, automatic interest posting, statements
- General ledger with fund pools; every transaction creates a receipt and a ledger entry
- Payment channels: cash with printed or SMS receipt, EcoCash, bank transfer, with daily reconciliation
- Users and roles for the 8 locations: officer, branch supervisor, accountant, manager, board. **Maker-checker**: the person who records a payment is not the person who approves it.
- Full audit log (who did what, when)
- **Migration** of the cleaned Excel data, then a **parallel run** (Excel and the system together for 1–2 months until the numbers match)

**Success measure:** every dollar received at any location shows up the same day in the system with a receipt sent to the member.

---

### Phase 2: Soft Loan Management (Months 4–7)

- Loan products (term, interest method, fees, penalties)
- Application → appraisal → approval workflow, with approval limits by role
- Eligibility checks built in (savings history, subscription up to date, existing loans)
- Disbursement (cash, EcoCash, bank) posted to the ledger
- Repayment schedules, automatic penalties, **SMS reminders** before due dates
- Arrears and Portfolio-at-Risk (PAR 30/60/90) reports per officer and location
- **Liquidity guardrail:** before approving a loan, the system checks how much of the asset-deposit pool must stay available for members about to reach 50%. This prevents lending out money you will need for assets next month.

**Success measure:** management can see the full loan book, arrears and available lending cash at any moment without opening Excel.

---

### Phase 3: Asset Financing (Months 6–10)

- **Asset catalogue**: boreholes, tractors and others, with standard costs and supplier quotes
- Asset account opening (with the account opening fee) for a chosen asset and its cost
- **Progress tracker**: deposited vs. 50% target, expected date to reach target, visible to the member by SMS
- **Waiting queue**: members who reached 50% are ranked by your queue rules; the system shows the cash needed to serve the queue
- Procurement: purchase order → supplier → delivery/installation, with photo and GPS proof captured by the field officer
- Conversion: when the asset is delivered, the deposit is applied and the remaining 50% becomes an **asset finance loan** with its own repayment schedule
- **Asset register**: every financed asset with location, serial number, condition, insurance and ownership status (SACCO-owned until paid off)
- Field officer mobile app that **works offline** in rural areas and syncs when signal returns: site visits, repayments collected, asset condition checks

**Success measure:** any member can be told exactly how close they are to their asset, and the board can see the full waiting list and the cash needed to serve it.

---

### Phase 4: Agribusiness and Contract Farming (Months 9–14)

- Farmer/grower registry (linked to members where applicable), plots with GPS and size
- **Contracts**: crop, season, hectares, expected yield, offtaker (buyer), agreed price
- **Input credit**: seed, fertiliser, chemicals and tillage issued to farmers, valued and recorded as an amount owed
- Field monitoring: officer visits, crop stage, photos, problems
- Harvest: quantities delivered, grading, sales to the offtaker
- **Settlement**: sale proceeds minus input costs = farmer payout and SACCO profit
- Project P&L per season/crop/location, showing return on the money invested from loan profits

**Success measure:** each farming project shows the money invested, the return, and the profit sent back into the fund.

---

### Phase 5: Integration, Self-Service and Insight (Months 12–18)

- **Member self-service**: balance, loan and asset-progress enquiry by USSD/WhatsApp/SMS, plus a member login linked from the website
- **"The Cycle" dashboard** for the board, showing money in → loans → farming → assets → returns, with liquidity, PAR, farming ROI and the asset queue
- Profit allocation reporting: how profit from loans and from farming was moved into asset financing
- Regulatory and AGM reports, year-end interest and dividend runs
- Automated backups, disaster recovery test, external security review

---

### Timeline at a glance

```
Month:          0    2    4    6    8   10   12   14   16   18
Phase 0 Found.  ████
Phase 1 Core         ██████
Phase 2 Loans             ██████
Phase 3 Assets                 ████████
Phase 4 Farming                          ██████████
Phase 5 Insight                                    ██████████
Training/support ─────────────────────────────────────────────►
```
Phases overlap on purpose: while one module is being built, the previous one is in parallel run and training.

---

## 4. Cross-cutting work (every phase)

- **Training**: hands-on training for every asset finance officer at their location, plus a simple printed guide. Pick a "champion" user per location.
- **Change management**: set a date when Excel stops being used for each module, and enforce it.
- **Security**: individual logins (no shared passwords), role-based access, 2-factor login for approvers, encrypted backups held off-site.
- **Controls**: maker-checker on payments and approvals, daily cash reconciliation per location, monthly reconciliation against bank and EcoCash statements.
- **Multi-currency**: USD and ZiG accounts, with exchange rates recorded per transaction.

---

## 5. Build vs. buy: recommendation

| Option | Pros | Cons |
|---|---|---|
| **A. Off-the-shelf SACCO/microfinance core** (e.g. Apache Fineract / Mifos X, open source; or a commercial SACCO system from a local or regional vendor) + **custom modules** for asset financing and contract farming | Loans, savings, ledger, interest and accounting are already built and tested; faster; cheaper | Asset queue and contract farming must be custom-built or configured; needs a partner who knows the platform |
| **B. Fully custom build** | Fits the cycle exactly | Slowest and riskiest; you are rebuilding accounting and interest logic that already exists; depends on one developer team |
| **C. Generic tools** (Excel/Google Sheets, generic accounting software) | Cheap, quick | Doesn't solve multi-location control, loans or the asset queue; the same problem in a new tool |

**Recommendation: Option A.** Use a proven core for members, savings, loans and the ledger (Phases 1–2). Build the **Asset Financing queue** and **Contract Farming** modules as custom applications that post into that same ledger (Phases 3–4). That way the accounting engine is one you didn't have to build yourselves, and the parts that make ZimFete unique are built to fit.

When picking a vendor/partner, require: offline-capable Android app for officers, EcoCash and bank integration, SMS, multi-branch, multi-currency, data export (so you are never locked in), local support in Zimbabwe, and a reference SACCO client you can visit.

---

## 6. Business risks the system should make visible

These come from the model as described. The system won't fix them, but it should show them early.

1. **Savings interest of 10% per month.** That is 120% a year simple, about 214% compounded. Soft loans and farming would have to earn more than that *after* defaults just to break even on savings. Confirm the rate (10% per **year** is far more common), and have the system model whether the cycle can afford it.
2. **Liquidity mismatch.** Asset deposits are lent out as soft loans. If many members reach 50% at once, or loans default, the cash may not be there to buy their assets. The liquidity guardrail (Phase 2) and the queue cash forecast (Phase 3) exist for this reason.
3. **Concentration in agriculture.** Drought affects soft-loan borrowers, farming projects and asset loan repayments all at the same time. Report exposure by location and by crop.
4. **Cash handling across 8 locations.** Moving collections to EcoCash/bank wherever possible, and issuing digital receipts, cuts fraud and errors.

---

## 7. Questions to answer before Phase 0 ends

- Is savings interest really 10% per **month**? Simple or compound?
- Are soft loans for members only? What is the maximum loan per member?
- What is the exact queue rule once members reach 50%?
- Who owns the asset until the second 50% is repaid, and what happens on default?
- In contract farming, does ZimFete farm directly, fund member-farmers, or both? Who are the offtakers?
- Which currencies (USD, ZiG) and payment channels (cash, EcoCash, bank) do members use, and in what proportions?
- How many members, active loans and assets are there today? This determines system size and cost.
- Who internally will own the system day to day (a system administrator / data officer)?

---

## 8. First 30 days: concrete next steps

1. Appoint an internal **project owner** and one champion per location.
2. Collect every Excel sheet, receipt book and ledger into one place.
3. Hold rulebook workshops (section 3, Phase 0, step 2) with management and the board.
4. Assign member numbers and start the data clean-up.
5. Shortlist 2 or 3 vendors/partners (section 5), ask for demos against **your** cycle: a member who saves, takes a soft loan, opens an asset account, reaches 50% and gets a borehole.
6. Agree budget and timeline with the board.
