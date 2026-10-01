-- ZimFete Asset Financing: initial schema.
-- Money balances live in Fineract; these tables hold the asset workflow and reference Fineract ids.

CREATE TABLE asset_catalogue_item (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    code            VARCHAR(40)   NOT NULL,
    name            VARCHAR(150)  NOT NULL,
    category        VARCHAR(30)   NOT NULL,
    description     VARCHAR(1000),
    standard_cost   DECIMAL(19,2) NOT NULL,
    currency        VARCHAR(3)      NOT NULL,
    active          BOOLEAN       NOT NULL,
    created_at      DATETIME(6)   NOT NULL,
    updated_at      DATETIME(6)   NOT NULL,
    CONSTRAINT uk_catalogue_code UNIQUE (code)
);

CREATE TABLE supplier (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(150) NOT NULL,
    phone       VARCHAR(40),
    email       VARCHAR(150),
    address     VARCHAR(300),
    active      BOOLEAN      NOT NULL,
    created_at  DATETIME(6)  NOT NULL
);

CREATE TABLE supplier_quote (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    catalogue_item_id   BIGINT        NOT NULL,
    supplier_id         BIGINT        NOT NULL,
    price               DECIMAL(19,2) NOT NULL,
    currency            VARCHAR(3)      NOT NULL,
    quote_date          DATE          NOT NULL,
    valid_until         DATE          NOT NULL,
    reference           VARCHAR(80),
    created_by          VARCHAR(100)  NOT NULL,
    created_at          DATETIME(6)   NOT NULL,
    CONSTRAINT fk_quote_item FOREIGN KEY (catalogue_item_id) REFERENCES asset_catalogue_item (id),
    CONSTRAINT fk_quote_supplier FOREIGN KEY (supplier_id) REFERENCES supplier (id)
);

CREATE TABLE asset_application (
    id                              BIGINT AUTO_INCREMENT PRIMARY KEY,
    reference                       VARCHAR(20)   NOT NULL,
    fineract_client_id              BIGINT        NOT NULL,
    member_name                     VARCHAR(200)  NOT NULL,
    office_id                       BIGINT        NOT NULL,
    officer_staff_id                BIGINT,
    catalogue_item_id               BIGINT        NOT NULL,
    quote_id                        BIGINT,
    asset_cost                      DECIMAL(19,2) NOT NULL,
    currency                        VARCHAR(3)      NOT NULL,
    deposit_percent                 DECIMAL(5,2)  NOT NULL,
    deposit_target                  DECIMAL(19,2) NOT NULL,
    fineract_savings_account_id     BIGINT        NOT NULL,
    deposited_amount                DECIMAL(19,2) NOT NULL,
    avg_monthly_deposit             DECIMAL(19,2) NOT NULL,
    estimated_target_date           DATE,
    balance_synced_at               DATETIME(6),
    status                          VARCHAR(20)   NOT NULL,
    opened_on                       DATE          NOT NULL,
    qualified_at                    DATETIME(6),
    conversion_step                 VARCHAR(20)   NOT NULL,
    deposit_applied                 DECIMAL(19,2),
    deposit_withdrawal_tx_id        BIGINT,
    financed_amount                 DECIMAL(19,2),
    fineract_loan_id                BIGINT,
    cancel_reason                   VARCHAR(500),
    created_by                      VARCHAR(100)  NOT NULL,
    created_at                      DATETIME(6)   NOT NULL,
    updated_at                      DATETIME(6)   NOT NULL,
    version                         BIGINT        NOT NULL,
    CONSTRAINT uk_application_reference UNIQUE (reference),
    CONSTRAINT uk_application_savings UNIQUE (fineract_savings_account_id),
    CONSTRAINT fk_application_item FOREIGN KEY (catalogue_item_id) REFERENCES asset_catalogue_item (id),
    CONSTRAINT fk_application_quote FOREIGN KEY (quote_id) REFERENCES supplier_quote (id)
);
CREATE INDEX ix_application_status ON asset_application (status, office_id);
CREATE INDEX ix_application_client ON asset_application (fineract_client_id);
CREATE INDEX ix_application_loan ON asset_application (fineract_loan_id);

CREATE TABLE purchase_order (
    id                      BIGINT AUTO_INCREMENT PRIMARY KEY,
    application_id          BIGINT        NOT NULL,
    supplier_id             BIGINT        NOT NULL,
    quote_id                BIGINT,
    amount                  DECIMAL(19,2) NOT NULL,
    currency                VARCHAR(3)      NOT NULL,
    status                  VARCHAR(20)   NOT NULL,
    queue_override_reason   VARCHAR(500),
    notes                   VARCHAR(1000),
    created_by              VARCHAR(100)  NOT NULL,
    created_at              DATETIME(6)   NOT NULL,
    approved_by             VARCHAR(100),
    approved_at             DATETIME(6),
    cancelled_by            VARCHAR(100),
    cancel_reason           VARCHAR(500),
    completed_at            DATETIME(6),
    version                 BIGINT        NOT NULL,
    CONSTRAINT fk_po_application FOREIGN KEY (application_id) REFERENCES asset_application (id),
    CONSTRAINT fk_po_supplier FOREIGN KEY (supplier_id) REFERENCES supplier (id),
    CONSTRAINT fk_po_quote FOREIGN KEY (quote_id) REFERENCES supplier_quote (id)
);
CREATE INDEX ix_po_status ON purchase_order (status);

CREATE TABLE financed_asset (
    id                      BIGINT AUTO_INCREMENT PRIMARY KEY,
    application_id          BIGINT        NOT NULL,
    catalogue_item_id       BIGINT        NOT NULL,
    office_id               BIGINT        NOT NULL,
    fineract_client_id      BIGINT        NOT NULL,
    serial_number           VARCHAR(100),
    latitude                DECIMAL(9,6)  NOT NULL,
    longitude               DECIMAL(9,6)  NOT NULL,
    delivered_on            DATE          NOT NULL,
    delivered_by            VARCHAR(100)  NOT NULL,
    member_acknowledged     BOOLEAN       NOT NULL,
    notes                   VARCHAR(1000),
    ownership               VARCHAR(30)   NOT NULL,
    ownership_changed_at    DATETIME(6)   NOT NULL,
    ownership_note          VARCHAR(500),
    created_at              DATETIME(6)   NOT NULL,
    version                 BIGINT        NOT NULL,
    CONSTRAINT uk_asset_application UNIQUE (application_id),
    CONSTRAINT fk_asset_application FOREIGN KEY (application_id) REFERENCES asset_application (id),
    CONSTRAINT fk_asset_item FOREIGN KEY (catalogue_item_id) REFERENCES asset_catalogue_item (id)
);

CREATE TABLE asset_inspection (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    asset_id            BIGINT        NOT NULL,
    inspected_on        DATE          NOT NULL,
    asset_condition     VARCHAR(20)   NOT NULL,
    latitude            DECIMAL(9,6),
    longitude           DECIMAL(9,6),
    notes               VARCHAR(1000),
    inspected_by        VARCHAR(100)  NOT NULL,
    created_at          DATETIME(6)   NOT NULL,
    CONSTRAINT fk_inspection_asset FOREIGN KEY (asset_id) REFERENCES financed_asset (id)
);

CREATE TABLE asset_photo (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    asset_id        BIGINT        NOT NULL,
    kind            VARCHAR(20)   NOT NULL,
    stored_name     VARCHAR(200)  NOT NULL,
    original_name   VARCHAR(255),
    content_type    VARCHAR(100)  NOT NULL,
    size_bytes      BIGINT        NOT NULL,
    uploaded_by     VARCHAR(100)  NOT NULL,
    uploaded_at     DATETIME(6)   NOT NULL,
    CONSTRAINT fk_photo_asset FOREIGN KEY (asset_id) REFERENCES financed_asset (id)
);
