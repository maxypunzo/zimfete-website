# ZimFete Asset Financing module

Asset financing pipeline for ZimFete SACCO, built as a separate Spring Boot service that sits beside **Apache Fineract / Mifos X**.

Fineract holds the money: deposit accounts, loans and the general ledger. This module runs the asset workflow around it:

```
 SAVING ──► QUALIFIED ──► PROCUREMENT ──► DELIVERED ──► REPAYING ──► PAID_OFF
 (deposits   (reached 50%,   (purchase order   (officer confirms   (deposit applied +   (loan closed;
  toward 50%)  in the queue)    approved)         with GPS/photos)    loan disbursed)      ownership → member)
```

See `../docs/digitization-roadmap.md` and `../docs/mifos-gap-analysis.md` for the background.

## What it does

| Feature | Endpoint(s) |
|---|---|
| Asset catalogue, suppliers, supplier quotes | `/api/catalogue/items`, `/api/suppliers`, `/api/catalogue/items/{id}/quotes` |
| Open an application (opens the Asset Deposit Account in Fineract; the opening fee is charged by Fineract) | `POST /api/applications` |
| Progress to 50%: % complete, average monthly deposit, **estimated qualifying date** | `POST /api/applications/{id}/refresh` |
| **Waiting queue** ranked across all locations, with cumulative cash needed | `GET /api/queue` |
| **Cash forecast**: queue and members expected to qualify within N days, per location | `GET /api/queue/forecast?days=60` |
| Reprice when the supplier price changes (may move the member back to SAVING) | `POST /api/applications/{id}/reprice` |
| Purchase orders: **maker-checker**; serving out of queue order needs a written reason | `POST /api/applications/{id}/purchase-orders`, `/api/purchase-orders/{id}/approve` |
| Delivery confirmation with GPS, serial number, member acknowledgement, photos | `POST /api/applications/{id}/delivery`, `POST /api/assets/{id}/photos` |
| **Conversion**: deposit applied + loan for the balance, disbursed to the supplier | `POST /api/applications/{id}/convert` |
| Asset register, inspections, repossession; ownership transferred automatically when the loan closes | `/api/assets`, `/api/assets/{id}/inspections`, `/api/assets/{id}/repossess` |
| Fineract hooks: update progress the moment a deposit or repayment is posted (also collects the opening fee) | `POST /api/webhooks/fineract/{token}/` |

Interactive API documentation is at `/swagger-ui.html`. The staff screens are in [`../staff-web`](../staff-web).

### Rules built in

- **Deposit target** = 50% of the asset cost (`zimfete.policy.deposit-percent`). The price is locked when the application is opened (from a valid quote, or the catalogue's standard cost).
- **Queue order**: first to reach the target is served first (`queue-rule: FIRST_QUALIFIED`), or first to apply (`FIRST_OPENED`). A member whose balance drops below the target (withdrawal, price increase) leaves the queue.
- **Cash needed per asset = full asset cost.** The supplier is paid in full; the member's deposit is already in the pooled money, much of it lent out as soft loans.
- **Officers see only their own location**; Head Office users see all locations.
- **Maker-checker** on purchase orders: whoever raises an order cannot approve it.
- **Roles** (taken from Fineract): OFFICER (open applications, raise orders, record delivery/inspections), MANAGER (approve orders, convert, reprice, cancel, repossess), ADMIN (catalogue and suppliers). Each role includes the ones below it.

### How conversion posts money in Fineract

| Step | Fineract transaction | Accounting effect |
|---|---|---|
| 1 | Withdrawal from the Asset Deposit Account, payment type *Supplier payment* | Member deposit liability ↓, cash ↓ |
| 2–4 | Loan for (cost − deposit) created from the asset loan product, approved, disbursed with payment type *Supplier payment* | Loan portfolio ↑, cash ↓ |

Steps 1 + 4 together equal the full price paid to the supplier. Each step is saved as soon as it succeeds. If Fineract fails part-way, running **convert** again resumes at the failed step and checks Fineract first, so nothing is posted twice (covered by `interruptedConversionResumesWithoutDoublePosting`).

## Login

Staff log in with their **Mifos/Fineract username and password**. The module checks them with Fineract's `/authentication` endpoint and maps Fineract role names to module roles in `application.yml`:

```yaml
zimfete.security.role-mapping:
  "[Asset Finance Officer]": OFFICER
  "[Asset Finance Manager]": MANAGER
  "[Super user]": ADMIN
```

Create the two roles in Mifos (Admin → Users → Manage Roles) and give them to the right users.

There are two ways to authenticate:

- **Staff web app:** `POST /api/auth/login` once. This sets an HttpOnly, SameSite=Strict session cookie (30-minute idle timeout). Every change must also send the `X-XSRF-TOKEN` header, copied from the `XSRF-TOKEN` cookie (CSRF protection). `GET /api/me` returns the signed-in user; `POST /api/auth/logout` ends the session.
- **Scripts / Swagger UI:** HTTP Basic on every request.

The server never sends a Basic challenge, so browsers never show a password pop-up. **Serve it over HTTPS only**: the session cookie is `Secure` by default (`COOKIE_SECURE`).

## Demo mode

`--spring.profiles.active=demo` runs the module for training and trying things out, with no Fineract and no real money:

- a pretend Fineract held in memory, with ZimFete's 8 locations and 17 sample members;
- sample assets, suppliers and members at different stages;
- built-in users `officer`, `officer2`, `manager`, `manager2` and `admin` (password = username);
- extra endpoints that stand in for Mifos teller actions (`/api/demo/...`).

Everything is lost on restart. Demo mode refuses to start unless the built-in users are also enabled, so it cannot be switched on against real Fineract logins.

## Fineract setup (once)

Run `deploy/setup-fineract.py` (Python 3 only). It creates what the module needs and prints the IDs for the settings file. Step-by-step instructions are in [`../docs/testing-guide.md`](../docs/testing-guide.md), part B. The script creates:

| Item | Notes |
|---|---|
| USD currency, the 8 locations as offices | |
| Payment type *Supplier payment* | `SUPPLIER_PAYMENT_TYPE_ID` |
| Savings charge *Asset account opening fee*, type **Specified due date** | `ASSET_OPENING_FEE_CHARGE_ID`. The module attaches it to each new deposit account and collects it from deposits once they cover it. (A *Savings Activation* charge makes Fineract refuse to activate an empty account. Also, Fineract does not copy a product's charges to accounts opened through the API.) |
| Savings product *Asset Deposit Account* (0% interest, no overdraft, no lock-in) | `ASSET_DEPOSIT_PRODUCT_ID`. Don't add a lock-in period: it would block the withdrawal that applies the deposit at conversion |
| Loan product *Asset Finance Loan* (example: 24 months, 1.5% a month) | `ASSET_LOAN_PRODUCT_ID`. The module copies the terms from this product, so set the real terms in Mifos |
| Fund *Asset finance pool* | `ASSET_LOAN_FUND_ID` |
| Roles *Asset Finance Officer*, *Asset Finance Manager* | Give them to staff in Mifos |
| Web hook (with `--webhook-url`) | Calls `/api/webhooks/fineract/<FINERACT_WEBHOOK_TOKEN>/` on savings DEPOSIT, WITHDRAWAL, UNDOTRANSACTION and loan REPAYMENT. The token is in the path because Fineract's hook client drops query strings. The module must be running when the hook is created |

Products are created with accounting off. Switch them to cash accounting in Mifos once ZimFete's chart of accounts is ready.

All of this was verified against **Apache Fineract 1.15.0**, using the full lifecycle test `../staff-web/e2e/live.spec.ts`.

## Run locally

Requires Java 21 (Maven is included as `./mvnw`). Full instructions: [`../docs/testing-guide.md`](../docs/testing-guide.md).

```bash
./mvnw test                                 # 29 tests, Fineract replaced by a fake
./mvnw spring-boot:run -Dspring-boot.run.profiles=demo   # try it with a pretend Fineract
```

The `dev` profile uses an H2 file database in `./data` and built-in users (password = username): `officer` (Marondera, office 2), `officer2` (Hwedza, office 3), `manager`, `manager2`, `admin` (Head Office). **Never use it on a server.** Calls that reach Fineract need a running Fineract (`FINERACT_URL`).

## Deploy on the Oracle Cloud VM (next to Fineract)

1. On the MariaDB server Fineract uses:
   ```sql
   CREATE DATABASE zimfete_assets CHARACTER SET utf8mb4;
   CREATE USER 'zimfete_assets'@'localhost' IDENTIFIED BY '...';
   GRANT ALL ON zimfete_assets.* TO 'zimfete_assets'@'localhost';
   ```
   If MariaDB runs in Docker (the Fineract docker-compose), use the container's host/port in `DB_URL` instead of `localhost`.
2. Build with `./mvnw -DskipTests package`. Copy `target/asset-financing-*.jar` to `/opt/zimfete-assets/asset-financing.jar`.
3. Install Java 21 (`sudo apt install openjdk-21-jre-headless`). Create user `zimfete` and the folder `/opt/zimfete-assets/photos`.
4. Copy `deploy/zimfete-assets.env.example` to `/opt/zimfete-assets/zimfete-assets.env`, fill it in, and `chmod 600` it.
5. Install `deploy/zimfete-assets.service` into `/etc/systemd/system/`, then run `sudo systemctl enable --now zimfete-assets`.
6. Put it behind HTTPS with Nginx, on the same address as the staff web app: see `../staff-web/deploy/nginx.conf`. Open only 80/443 in the Oracle security list; keep 8090 and 3306 closed to the internet.
7. Back up the `zimfete_assets` database and the photos folder together with Fineract's database.

The schema is created and upgraded automatically by Flyway on start-up (`src/main/resources/db/migration`).

A `Dockerfile` is included if you prefer containers (it works on ARM/Ampere).

## Not built yet

- SMS/WhatsApp to members: `MemberNotifier` currently only logs; plug in a local SMS gateway.
- Offline mobile capture for officers in low-signal areas.
- Multi-currency (ZiG): v1 assumes the catalogue currency matches the deposit and loan products (USD).
- Refund/transfer of a cancelled member's deposit is done in Mifos per policy.
- Runs as **one instance**: the conversion lock is in memory.
