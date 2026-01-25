create table if not exists triage_findings (
    id uuid primary key,
    repository varchar(512) not null,
    commit_sha varchar(128),
    run_id varchar(128),
    cwe_id varchar(32),
    rule_id varchar(256),
    fingerprint varchar(512),
    file_path varchar(1024),
    start_line integer,
    confidence_percent integer,
    classification varchar(64),
    status varchar(32),
    claimed_by varchar(256),
    claimed_at timestamp with time zone,
    decided_by varchar(256),
    decided_at timestamp with time zone,
    pr_url varchar(1024),
    pr_branch varchar(256),
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null
);

create table if not exists triage_audit_events (
    id uuid primary key,
    repository varchar(512) not null,
    finding_id uuid,
    event_type varchar(64) not null,
    actor varchar(256),
    details text,
    created_at timestamp with time zone not null
);

create index if not exists idx_triage_findings_repo on triage_findings (repository);
create index if not exists idx_triage_findings_status on triage_findings (status);
create index if not exists idx_triage_audit_repo on triage_audit_events (repository);
