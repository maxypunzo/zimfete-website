#!/usr/bin/env python3
"""
Sets up Fineract for the ZimFete asset financing module, through the Fineract API.

Creates (only if missing, so it is safe to run again):
  - USD enabled as a currency
  - the 8 ZimFete locations as offices under Head Office
  - payment type "Supplier payment"
  - savings charge "Asset account opening fee" (charged when the member makes the first deposit)
  - savings product "Asset Deposit Account"
  - loan product "Asset Finance Loan"
  - fund "Asset finance pool"
  - roles "Asset Finance Officer" and "Asset Finance Manager"
Optionally (--test-data) also test users and test members.

At the end it prints the settings to paste into zimfete-assets.env.

Usage:
  python3 setup-fineract.py --url https://localhost:8443/fineract-provider/api/v1 --user mifos --password password --insecure
Only Python 3 is needed (no extra packages).
"""
import argparse
import base64
import datetime
import json
import ssl
import sys
import urllib.error
import urllib.request

OFFICES = ["Marondera", "Mackeche", "Hwedza", "Seke", "Murewa", "Mutoko", "Mudzi", "UMP"]
DATE_FORMAT = "dd MMMM yyyy"


class Fineract:
    def __init__(self, url, user, password, tenant, insecure):
        self.url = url.rstrip("/")
        token = base64.b64encode(f"{user}:{password}".encode()).decode()
        self.headers = {
            "Authorization": "Basic " + token,
            "Fineract-Platform-TenantId": tenant,
            "Content-Type": "application/json",
            "Accept": "application/json",
        }
        self.context = ssl._create_unverified_context() if insecure else None

    def call(self, method, path, body=None):
        data = json.dumps(body).encode() if body is not None else None
        request = urllib.request.Request(self.url + path, data=data, method=method, headers=self.headers)
        try:
            with urllib.request.urlopen(request, context=self.context, timeout=60) as response:
                text = response.read().decode()
                return json.loads(text) if text else None
        except urllib.error.HTTPError as e:
            detail = e.read().decode()
            try:
                parsed = json.loads(detail)
                errors = parsed.get("errors") or []
                detail = "; ".join(x.get("defaultUserMessage", "") for x in errors) or parsed.get(
                    "defaultUserMessage", detail)
            except ValueError:
                pass
            sys.exit(f"\nFineract refused {method} {path}: HTTP {e.code}: {detail}")
        except urllib.error.URLError as e:
            sys.exit(f"\nCannot reach Fineract at {self.url}: {e.reason}\n"
                     "Check --url. If Fineract uses a self-signed certificate, add --insecure.")

    def get(self, path):
        return self.call("GET", path)

    def post(self, path, body):
        return self.call("POST", path, body)

    def put(self, path, body):
        return self.call("PUT", path, body)


def today():
    return datetime.date.today().strftime("%d %B %Y")


def find(items, name, key="name"):
    for item in items:
        if str(item.get(key, "")).lower() == name.lower():
            return item
    return None


def step(message):
    print(f"  {message}")


def ensure_currency(f):
    current = [c["code"] for c in f.get("/currencies").get("selectedCurrencyOptions", [])]
    if "USD" in current:
        step("USD currency: already enabled")
        return
    f.put("/currencies", {"currencies": current + ["USD"]})
    step("USD currency: enabled")


def ensure_offices(f):
    offices = f.get("/offices")
    head = next(o for o in offices if o.get("parentId") is None)
    ids = {}
    for name in OFFICES:
        office = find(offices, name)
        if office:
            ids[name] = office["id"]
            continue
        result = f.post("/offices", {"name": name, "parentId": head["id"], "openingDate": "01 January 2020",
                                     "dateFormat": DATE_FORMAT, "locale": "en"})
        ids[name] = result["resourceId"]
    step("Offices: " + ", ".join(f"{n} ({i})" for n, i in ids.items()))
    return head["id"], ids


def ensure_payment_type(f):
    existing = find(f.get("/paymenttypes"), "Supplier payment")
    if existing:
        step(f"Payment type 'Supplier payment': exists (id {existing['id']})")
        return existing["id"]
    result = f.post("/paymenttypes", {"name": "Supplier payment", "description": "Paid directly to an asset supplier",
                                      "isCashPayment": False, "position": 10})
    step(f"Payment type 'Supplier payment': created (id {result['resourceId']})")
    return result["resourceId"]


def ensure_opening_fee(f, amount):
    existing = find(f.get("/charges"), "Asset account opening fee")
    if existing:
        step(f"Charge 'Asset account opening fee': exists (id {existing['id']})")
        return existing["id"]
    # "Specified due date" so the account can be opened with a zero balance; the module attaches it
    # to each new account and collects it from the member's deposits once they cover it.
    # (Tested on Fineract 1.15: a "Savings Activation" charge makes activation of an empty account fail.)
    result = f.post("/charges", {
        "name": "Asset account opening fee", "chargeAppliesTo": 2, "currencyCode": "USD",
        "chargeTimeType": 2, "chargeCalculationType": 1, "amount": amount, "active": True,
        "penalty": False, "locale": "en"})
    step(f"Charge 'Asset account opening fee' (USD {amount}): created (id {result['resourceId']})")
    return result["resourceId"]


def ensure_savings_product(f):
    existing = find(f.get("/savingsproducts"), "Asset Deposit Account")
    if existing:
        step(f"Savings product 'Asset Deposit Account': exists (id {existing['id']})")
        return existing["id"]
    result = f.post("/savingsproducts", {
        "name": "Asset Deposit Account", "shortName": "ADA",
        "description": "Member deposits toward 50% of an asset's cost",
        "currencyCode": "USD", "digitsAfterDecimal": 2, "inMultiplesOf": 0,
        "nominalAnnualInterestRate": 0, "interestCompoundingPeriodType": 1, "interestPostingPeriodType": 4,
        "interestCalculationType": 1, "interestCalculationDaysInYearType": 365,
        "allowOverdraft": False, "withdrawalFeeForTransfers": False, "enforceMinRequiredBalance": False,
        # 1 = no accounting. Switch to cash accounting in Mifos once the chart of accounts is ready.
        "accountingRule": 1, "locale": "en"})
    step(f"Savings product 'Asset Deposit Account': created (id {result['resourceId']})")
    return result["resourceId"]


def ensure_loan_product(f, fund_id):
    existing = find(f.get("/loanproducts"), "Asset Finance Loan")
    if existing:
        step(f"Loan product 'Asset Finance Loan': exists (id {existing['id']})")
        return existing["id"]
    result = f.post("/loanproducts", {
        "name": "Asset Finance Loan", "shortName": "AFL",
        "description": "Balance of an asset's cost after the member's 50% deposit",
        "fundId": fund_id, "currencyCode": "USD", "digitsAfterDecimal": 2, "inMultiplesOf": 0,
        "principal": 2000, "minPrincipal": 50, "maxPrincipal": 100000,
        "numberOfRepayments": 24, "minNumberOfRepayments": 1, "maxNumberOfRepayments": 60,
        "repaymentEvery": 1, "repaymentFrequencyType": 2,
        # Example terms: change them in Mifos to ZimFete's policy (this module copies them from the product).
        "interestRatePerPeriod": 1.5, "interestRateFrequencyType": 2,
        "amortizationType": 1, "interestType": 0, "interestCalculationPeriodType": 1,
        "transactionProcessingStrategyCode": "mifos-standard-strategy",
        "daysInYearType": 1, "daysInMonthType": 1, "isInterestRecalculationEnabled": False,
        "accountingRule": 1, "locale": "en", "dateFormat": DATE_FORMAT})
    step(f"Loan product 'Asset Finance Loan': created (id {result['resourceId']})")
    return result["resourceId"]


def ensure_fund(f):
    existing = find(f.get("/funds"), "Asset finance pool")
    if existing:
        step(f"Fund 'Asset finance pool': exists (id {existing['id']})")
        return existing["id"]
    result = f.post("/funds", {"name": "Asset finance pool"})
    step(f"Fund 'Asset finance pool': created (id {result['resourceId']})")
    return result["resourceId"]


def ensure_hook(f, module_url, token):
    display = "ZimFete asset financing"
    url = f"{module_url.rstrip('/')}/api/webhooks/fineract/{token}/"
    existing = find(f.get("/hooks"), display, key="displayName")
    if existing:
        step(f"Webhook '{display}': exists (id {existing['id']}); delete it in Mifos to re-create with a new URL")
        return
    # Fineract calls the URL once when the hook is created, so the module must already be running.
    f.post("/hooks", {
        "name": "Web", "displayName": display, "isActive": True,
        "config": {"Payload URL": url, "Content Type": "json"},
        "events": [{"actionName": a, "entityName": "SAVINGSACCOUNT"} for a in ("DEPOSIT", "WITHDRAWAL", "UNDOTRANSACTION")]
        + [{"actionName": "REPAYMENT", "entityName": "LOAN"}]})
    step(f"Webhook '{display}': created -> {module_url.rstrip('/')}/api/webhooks/fineract/<token>/")


def ensure_role(f, name, description):
    existing = find(f.get("/roles"), name)
    if existing:
        step(f"Role '{name}': exists (id {existing['id']})")
        return existing["id"]
    result = f.post("/roles", {"name": name, "description": description})
    step(f"Role '{name}': created (id {result['resourceId']})")
    return result["resourceId"]


def ensure_user(f, username, first, last, office_id, role_id, password):
    existing = find(f.get("/users"), username, key="username")
    if existing:
        step(f"User '{username}': exists")
        return
    f.post("/users", {"username": username, "firstname": first, "lastname": last,
                      "email": f"{username}@example.invalid", "officeId": office_id, "roles": [role_id],
                      "sendPasswordToEmail": False, "password": password, "repeatPassword": password,
                      "passwordNeverExpires": True})
    step(f"User '{username}' (password {password}): created")


def ensure_member(f, first, last, office_id):
    name = f"{first} {last}"
    page = f.get("/clients?limit=500")
    if find(page.get("pageItems", []), name, key="displayName"):
        return
    f.post("/clients", {"officeId": office_id, "legalFormId": 1, "firstname": first, "lastname": last,
                        "active": True, "activationDate": today(), "submittedOnDate": today(),
                        "dateFormat": DATE_FORMAT, "locale": "en"})


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--url", required=True, help="e.g. https://localhost:8443/fineract-provider/api/v1")
    parser.add_argument("--user", default="mifos")
    parser.add_argument("--password", default="password")
    parser.add_argument("--tenant", default="default")
    parser.add_argument("--insecure", action="store_true", help="accept a self-signed certificate")
    parser.add_argument("--opening-fee", type=float, default=10.0, help="asset account opening fee in USD")
    parser.add_argument("--webhook-url", help="address Fineract uses to reach the module, e.g. http://10.0.0.5:8090 "
                        "(the module must be running). Needs --webhook-token.")
    parser.add_argument("--webhook-token", help="the FINERACT_WEBHOOK_TOKEN value from zimfete-assets.env")
    parser.add_argument("--test-data", action="store_true",
                        help="also create test users (password Zimfete@Test2026) and a few test members")
    args = parser.parse_args()

    f = Fineract(args.url, args.user, args.password, args.tenant, args.insecure)
    print(f"Setting up Fineract at {args.url}")
    ensure_currency(f)
    head_id, offices = ensure_offices(f)
    payment_type = ensure_payment_type(f)
    fee = ensure_opening_fee(f, args.opening_fee)
    savings_product = ensure_savings_product(f)
    fund = ensure_fund(f)
    loan_product = ensure_loan_product(f, fund)
    officer_role = ensure_role(f, "Asset Finance Officer", "Asset finance officer at a location")
    manager_role = ensure_role(f, "Asset Finance Manager", "Approves asset purchase orders and conversions")

    if args.webhook_url:
        if not args.webhook_token:
            sys.exit("--webhook-url needs --webhook-token")
        ensure_hook(f, args.webhook_url, args.webhook_token)

    if args.test_data:
        ensure_user(f, "af.officer", "Test", "Officer", offices["Marondera"], officer_role, "Zimfete@Test2026")
        ensure_user(f, "af.officer2", "Test", "Officer Hwedza", offices["Hwedza"], officer_role, "Zimfete@Test2026")
        ensure_user(f, "af.manager", "Test", "Manager", head_id, manager_role, "Zimfete@Test2026")
        ensure_user(f, "af.manager2", "Second", "Manager", head_id, manager_role, "Zimfete@Test2026")
        for first, last, office in [("Tendai", "Moyo", "Marondera"), ("Chipo", "Mutasa", "Marondera"),
                                    ("Farai", "Ncube", "Marondera"), ("Rudo", "Chikwanha", "Hwedza"),
                                    ("Tafadzwa", "Zhou", "Mutoko")]:
            ensure_member(f, first, last, offices[office])
        step("Test members: Tendai Moyo, Chipo Mutasa, Farai Ncube (Marondera), Rudo Chikwanha (Hwedza), "
             "Tafadzwa Zhou (Mutoko)")

    print(f"""
Done. Put these in zimfete-assets.env:

ASSET_DEPOSIT_PRODUCT_ID={savings_product}
ASSET_LOAN_PRODUCT_ID={loan_product}
SUPPLIER_PAYMENT_TYPE_ID={payment_type}
ASSET_LOAN_FUND_ID={fund}
ASSET_OPENING_FEE_CHARGE_ID={fee}

Still to do in Mifos:
  - Give staff the roles "Asset Finance Officer" / "Asset Finance Manager".
  - Review the example loan terms on "Asset Finance Loan" (24 months, 1.5% a month).""")


if __name__ == "__main__":
    main()
