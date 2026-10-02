# Testing the ZimFete Asset Financing system

There are two ways to test, and it is worth doing both, in this order:

| | What you test | Needs | Time |
|---|---|---|---|
| **Part A: demo on your own computer** | All the screens and the full workflow, with a built-in pretend Fineract and sample data | Your laptop | about 15 minutes |
| **Part B: with your Fineract on the Oracle VM** | The real thing: accounts, fees, withdrawals and loans posted in your Fineract | Your Oracle VM with Fineract running | about 1 hour |

Everything in Part B was tested end to end against **Apache Fineract 1.15.0**. Older Fineract versions may differ: see *Which Fineract version?* at the end.

---

## Part A: Demo on your own computer

### A1. Install (once)

| Tool | Version | Where |
|---|---|---|
| Java (JDK) | 21 | https://adoptium.net (choose "Temurin 21 LTS") |
| Node.js | 22 LTS | https://nodejs.org |
| Git | any | https://git-scm.com |

Maven is **not** needed; the project includes its own (`mvnw`).

Check them in a terminal (Windows: PowerShell):

```bash
java -version     # must say 21
node -v           # v22.x
```

### A2. Get the code

```bash
git clone https://github.com/maxypunzo/zimfete-website.git
cd zimfete-website
git checkout claude/sacco-digitization-roadmap-6v7ckm
```

### A3. Start the backend in demo mode (terminal 1)

```bash
cd asset-financing
./mvnw spring-boot:run -Dspring-boot.run.profiles=demo
```

On Windows PowerShell use: `.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=demo"`

The first run downloads dependencies (a few minutes). It is ready when you see `Started AssetFinancingApplication` and `Demo data seeded`.

### A4. Start the staff web app (terminal 2)

```bash
cd staff-web
npm install
npm run dev
```

Open **http://localhost:5173**. An orange bar at the top says *Demo mode*.

### A5. Sign in and try it

| User | Password | Is |
|---|---|---|
| `officer` | `officer` | Asset finance officer, Marondera |
| `officer2` | `officer2` | Asset finance officer, Hwedza |
| `manager`, `manager2` | same as username | Managers at Head Office (see all locations) |
| `admin` | `admin` | Administrator (catalogue) |

Walk through one member:

1. **officer**: *New application*. Search "Chipo", pick the borehole, open it.
2. On the application, click **Demo: record deposit** twice until the member passes 50%. The status changes to *In queue*. (In real use, tellers post deposits in Mifos.)
3. **Raise purchase order**. Because other demo members qualified earlier, it asks for a reason to serve this member first.
4. Sign out. As **manager**, go to *Purchase orders* and **Approve**.
5. As **officer**, on the application, **Record delivery**. Fill in latitude/longitude, tick the member's confirmation, and optionally add a photo.
6. As **manager**, **Convert to loan**. The status changes to *Repaying*.
7. **Demo: repay loan in full**. The status changes to *Paid off*, and the asset register shows *Member owned*.
8. Look at *Dashboard* and *Queue & cash* as manager, then as officer2 (who only sees Hwedza). Try the app on your phone's browser too, if your phone is on the same Wi-Fi: run `npm run dev -- --host` and open the address it prints.

Stop with **Ctrl+C** in both terminals. Demo data resets every time the backend restarts.

---

## Part B: With your Fineract on the Oracle VM

The module runs **on the same VM as Fineract** and talks to it over the VM's own network. For testing you open the screens through an **SSH tunnel**, so no new ports are opened to the internet.

```
 Your laptop ──SSH tunnel──► Oracle VM
 browser                      ├─ Fineract (+ Mifos)        :8443 or :8080
 localhost:4173               ├─ Asset financing module    :8090
                              └─ Staff web app (preview)   :4173  ─► /api ─► :8090
```

### B1. Prepare the VM (once)

You need an **Ampere A1** VM with at least 4 GB of memory (Fineract alone needs about 2 GB). The 1 GB AMD "micro" shape is too small.

Ubuntu:

```bash
sudo apt update
sudo apt install -y openjdk-21-jdk-headless git python3 curl
# Node.js 22 (Ubuntu's own nodejs package is too old)
curl -fsSL https://raw.githubusercontent.com/nvm-sh/nvm/v0.40.3/install.sh | bash
source ~/.bashrc
nvm install 22
```

Oracle Linux: `sudo dnf install -y java-21-openjdk-devel git python3`, then nvm as above.

### B2. Get the code and build (on the VM)

```bash
git clone https://github.com/maxypunzo/zimfete-website.git
cd zimfete-website
git checkout claude/sacco-digitization-roadmap-6v7ckm

cd asset-financing
./mvnw -DskipTests package          # builds target/asset-financing-0.1.0-SNAPSHOT.jar
cd ../staff-web
npm ci && npm run build             # builds dist/
cd ..
```

### B3. Find your Fineract API address

Try these on the VM (default login `mifos` / `password`; use your own if you changed it):

```bash
# Fineract docker images usually use HTTPS on 8443 with a self-signed certificate:
curl -k -u mifos:password -H "Fineract-Platform-TenantId: default" \
     https://localhost:8443/fineract-provider/api/v1/offices
# or plain HTTP on 8080:
curl -u mifos:password -H "Fineract-Platform-TenantId: default" \
     http://localhost:8080/fineract-provider/api/v1/offices
```

The one that prints `[{"id":1,"name":"Head Office",...` is your **Fineract URL** (everything up to and including `/api/v1`).

> **If only the HTTPS (8443) address works:** the module must trust Fineract's self-signed certificate. Do this once:
>
> ```bash
> mkdir -p ~/zimfete-test && cd ~/zimfete-test
> openssl s_client -connect localhost:8443 </dev/null 2>/dev/null | openssl x509 > fineract.crt
> keytool -importcert -noprompt -alias fineract -file fineract.crt \
>         -keystore fineract-trust.jks -storepass changeit
> ```
>
> Then add `-Djavax.net.ssl.trustStore=$HOME/zimfete-test/fineract-trust.jks -Djavax.net.ssl.trustStorePassword=changeit` to the `java` command in B6.
>
> If the module's log then says `No subject alternative names matching ... localhost`, the certificate was made for a different name. The simplest fix is to also publish Fineract's plain HTTP port **to localhost only** (in docker-compose: `ports: ["127.0.0.1:8080:8080"]` and `FINERACT_SERVER_SSL_ENABLED=false`) and use the `http://localhost:8080/...` URL. Ask for help if you are unsure: this changes your Fineract setup.

### B4. Set up Fineract with the script (once)

The script creates everything the module needs in Fineract, and is safe to run again:

- the 8 ZimFete locations as offices
- the *Supplier payment* payment type
- the *Asset account opening fee* charge
- the *Asset Deposit Account* savings product
- the *Asset Finance Loan* loan product
- the *Asset finance pool* fund
- the *Asset Finance Officer* and *Asset Finance Manager* roles

`--test-data` also adds test staff users and five test members. Use it only on a test server.

```bash
python3 asset-financing/deploy/setup-fineract.py \
    --url https://localhost:8443/fineract-provider/api/v1 --insecure \
    --user mifos --password password --test-data
```

(`--insecure` is only needed for a self-signed HTTPS address; drop it for `http://`.)

It ends by printing lines like:

```
ASSET_DEPOSIT_PRODUCT_ID=1
ASSET_LOAN_PRODUCT_ID=1
SUPPLIER_PAYMENT_TYPE_ID=4
ASSET_LOAN_FUND_ID=1
ASSET_OPENING_FEE_CHARGE_ID=1
```

Keep them for the next step. Test users it creates (password `Zimfete@Test2026`): `af.officer` (Marondera), `af.officer2` (Hwedza), `af.manager` and `af.manager2` (Head Office). `mifos` itself has the *Super user* role, which the module treats as administrator.

The opening fee is a **"Specified due date"** charge, not "Savings Activation". This was tested on Fineract 1.15: with an activation charge, Fineract refuses to open an account that has no money in it yet. The module attaches the fee when it opens the account, and collects it from the member's deposits as soon as they cover it.

The loan product's example terms are 24 months at 1.5% a month. Change them in Mifos (*Products → Loan Products → Asset Finance Loan*) to ZimFete's real terms; the module copies the terms from there.

### B5. Create the settings file

```bash
mkdir -p ~/zimfete-test/photos
openssl rand -hex 24          # copy the output: it is your webhook token
nano ~/zimfete-test/zimfete-assets.env
```

Paste this, filling in your values:

```bash
# Test database: a file on the VM (no MariaDB setup needed for testing).
# Keep the quotes: the value contains ";" characters.
DB_URL="jdbc:h2:file:/home/ubuntu/zimfete-test/db;MODE=MariaDB;DATABASE_TO_LOWER=TRUE"

FINERACT_URL=https://localhost:8443/fineract-provider/api/v1
FINERACT_TENANT=default
FINERACT_USERNAME=mifos
FINERACT_PASSWORD=password

# From the setup script (B4):
ASSET_DEPOSIT_PRODUCT_ID=1
ASSET_LOAN_PRODUCT_ID=1
SUPPLIER_PAYMENT_TYPE_ID=4
ASSET_LOAN_FUND_ID=1
ASSET_OPENING_FEE_CHARGE_ID=1

FINERACT_WEBHOOK_TOKEN=paste-the-openssl-output-here
PHOTO_DIR=/home/ubuntu/zimfete-test/photos

# Only for testing over the SSH tunnel (http). Never on the real server.
COOKIE_SECURE=false
```

(Change `/home/ubuntu` if your user is not `ubuntu`; `echo $HOME` shows it.)

Then make the file readable only by you: `chmod 600 ~/zimfete-test/zimfete-assets.env`

### B6. Start the module (terminal 1 on the VM)

```bash
cd ~/zimfete-website/asset-financing
set -a; . ~/zimfete-test/zimfete-assets.env; set +a
java -jar target/asset-financing-0.1.0-SNAPSHOT.jar
```

(Add the two `-Djavax.net.ssl...` options before `-jar` if you did the certificate step in B3.)

It is ready at `Started AssetFinancingApplication`. In a second terminal, check that it can reach Fineract:

```bash
curl -u mifos:password http://localhost:8090/api/offices
# -> [{"id":1,"name":"Head Office"},{"id":2,"name":"Marondera"},...]
```

A `502` with "Fineract is unreachable" means the Fineract URL or the certificate (B3) is wrong. The module's terminal shows the exact error.

### B7. Connect Fineract's webhook (recommended)

With the webhook, deposits and repayments made in Mifos show up in the module within seconds. Without it, the module checks every hour, and officers can click **Check balance now**.

The module must be running (Fineract calls it once when the webhook is created):

```bash
python3 asset-financing/deploy/setup-fineract.py \
    --url https://localhost:8443/fineract-provider/api/v1 --insecure \
    --webhook-url http://localhost:8090 --webhook-token <your FINERACT_WEBHOOK_TOKEN>
```

**If Fineract runs in Docker,** `localhost` inside the container means the container itself. Use the VM's private IP instead (`hostname -I | awk '{print $1}'`, e.g. `http://10.0.0.12:8090`). Oracle's Ubuntu images also block that by default, so allow it once:

```bash
sudo iptables -I INPUT -p tcp -s 172.16.0.0/12 --dport 8090 -j ACCEPT
```

A Fineract message `url.invalid` means it could not reach that address.

### B8. Start the web app (terminal 2 on the VM)

```bash
cd ~/zimfete-website/staff-web
npm run preview -- --host 127.0.0.1
```

### B9. Open it from your laptop

On your laptop, open an SSH tunnel (Windows: PowerShell has `ssh` built in):

```bash
ssh -i path/to/your-oracle-key -L 4173:localhost:4173 ubuntu@<VM public IP>
```

Leave it open and browse to **http://localhost:4173** on your laptop. No ports are opened on the VM.

### B10. Test script

Use a test member such as **Farai Ncube** (created by `--test-data`). Keep Mifos open in another tab to check what the module posts there.

| # | Who (in the app) | Do | Expect in the app | Check in Mifos |
|---|---|---|---|---|
| 1 | `mifos` | *Catalogue*: **Add asset** "Borehole" at 4,000 USD. *Suppliers*: add one | Asset listed with deposit 2,000 | — |
| 2 | `af.officer` | *New application* for Farai Ncube, borehole | Status *Saving*, target US$2,000 | Farai has an active **Asset Deposit Account** with external id = the AF-reference, and the opening fee 10.00 outstanding |
| 3 | (Mifos teller) | In Mifos, **deposit 1,200** into that account | Within seconds (webhook), or after **Check balance now**: **US$1,190** (fee taken) | A *Pay Charge 10.00* transaction; fee paid |
| 4 | (Mifos teller) | Deposit 900 | Status *In queue* (2,090 ≥ 2,000) | — |
| 5 | `af.officer` | **Raise purchase order** | *Awaiting approval*; officer has no Approve button | — |
| 6 | `af.manager` | *Purchase orders* → **Approve** | Status *Being procured* | — |
| 7 | `af.officer` | **Record delivery** (on a phone: *Use my location*, take a photo) | Status *Delivered* | — |
| 8 | `af.manager` | **Convert to loan** | Status *Repaying*; deposit applied 2,090, loan 1,910 | Savings: withdrawal 2,090 (*Supplier payment*), balance 0. New **Asset Finance Loan** 1,910, active, external id = AF-reference, fund *Asset finance pool* |
| 9 | (Mifos teller) | On the loan, **Prepay Loan** / repay in full | Status *Paid off*; asset register: *Member owned* | Loan closed |
| 10 | `af.officer2` | Search for Farai; open *Applications* | Not found / not listed (Hwedza only) | — |
| 11 | `af.manager` | Raise an order and try to approve it yourself | "Another manager must approve it" | — |

Also try:

- a second member who reaches 50% later, to see the queue order and the reason prompt;
- *Change price* on an application;
- cancelling a purchase order (the member goes back into the queue).

### B11. If something goes wrong

| Symptom | Cause and fix |
|---|---|
| Sign-in says "Wrong username or password" with the right password | The Fineract user has no *Asset Finance Officer/Manager* (or *Super user*) role. Role names must match exactly. Or Fineract is unreachable: see the module's terminal |
| The app shows "Cannot reach the server" | The module (B6) or the preview (B8) is not running, or the SSH tunnel closed |
| Module won't start: `Migration ... failed`, `Unknown data type: "DATETIME"` | `DB_URL` in the settings file is not in quotes |
| `502 ... PKIX path building failed` | The module does not trust Fineract's certificate: do the certificate step in B3 |
| Webhook setup says `url.invalid` | The module isn't running, or Fineract can't reach the address you gave: see B7 |
| Balance doesn't update after a deposit in Mifos | No webhook (B7). Click **Check balance now**; the module also checks every hour |
| Opening fee not collected | `ASSET_OPENING_FEE_CHARGE_ID` not set, or deposits are still less than the fee |
| Signed in, then immediately signed out again | Using plain http without `COOKIE_SECURE=false` (testing only), or not through `localhost` |
| Port 8090 already in use | Something else uses it: add `PORT=8091` to the settings file and change the tunnel/preview accordingly |

The module logs everything to the terminal from B6. Copy the error lines when asking for help. To see every webhook Fineract sends, start it with `LOGGING_LEVEL_ZW_CO_ZIMFETE_ASSETFINANCE_WORKFLOW=DEBUG` in front of `java`.

### B12. Start over

Stop the module and the preview with **Ctrl+C**, then run `rm -rf ~/zimfete-test/db* ~/zimfete-test/photos/*`. The module's data is gone. The test records in Fineract stay; that is why you should test on a test Fineract.

### Which Fineract version?

Mifos shows it under *Admin → System* (or ask the API: `curl -k https://localhost:8443/fineract-provider/actuator/info`). This was tested on **1.15.0**. Versions before 1.9 are not expected to work: the loan lookup by external id and the webhook format are different.

### After testing

To run it permanently, see `asset-financing/README.md` → *Deploy*:

- MariaDB instead of the test file database
- a systemd service
- Nginx with HTTPS on its own address (`staff-web/deploy/nginx.conf`)
- `COOKIE_SECURE` back to `true`

For automated checks there are also `./mvnw test` (backend), `npm test` and `npm run e2e` (web app). `LIVE=1 npx playwright test e2e/live.spec.ts` replays this whole Part B script against a **test** Fineract.
