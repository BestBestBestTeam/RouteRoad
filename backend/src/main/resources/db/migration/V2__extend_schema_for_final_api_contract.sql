LOCK TABLE office, service_order, planning_run, assignment IN ACCESS EXCLUSIVE MODE;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM office)
        OR EXISTS (SELECT 1 FROM service_order)
        OR EXISTS (SELECT 1 FROM planning_run)
        OR EXISTS (SELECT 1 FROM assignment) THEN
        RAISE EXCEPTION 'V2 requires empty office, service_order, planning_run and assignment tables; an explicit data migration is required otherwise';
    END IF;
    IF EXISTS (SELECT 1 FROM pg_constraint WHERE confrelid = 'assignment'::regclass) THEN
        RAISE EXCEPTION 'V2 found unexpected foreign keys referencing assignment; review dependencies before changing its primary key';
    END IF;
END
$$;

ALTER TABLE office
    ADD COLUMN address text NOT NULL,
    ADD CONSTRAINT office_address_check CHECK (length(btrim(address)) > 0);

ALTER TABLE service_order
    ADD COLUMN number text NOT NULL,
    ADD COLUMN address text NOT NULL,
    ADD COLUMN district text,
    ADD COLUMN product text NOT NULL,
    ADD CONSTRAINT service_order_number_key UNIQUE (number),
    ADD CONSTRAINT service_order_number_check CHECK (length(btrim(number)) > 0),
    ADD CONSTRAINT service_order_address_check CHECK (length(btrim(address)) > 0),
    ADD CONSTRAINT service_order_product_check CHECK (length(btrim(product)) > 0);

ALTER TABLE planning_run
    ADD COLUMN metrics jsonb NOT NULL,
    ADD CONSTRAINT planning_run_metrics_check CHECK (
        jsonb_typeof(metrics) = 'object'
        AND metrics ?& ARRAY['total_distance_m', 'violated_windows', 'unassigned_count', 'solve_time_ms']
        AND jsonb_typeof(metrics -> 'total_distance_m') = 'number'
        AND (metrics ->> 'total_distance_m')::numeric BETWEEN 0 AND 2147483647
        AND (metrics ->> 'total_distance_m')::numeric = trunc((metrics ->> 'total_distance_m')::numeric)
        AND jsonb_typeof(metrics -> 'violated_windows') = 'number'
        AND (metrics ->> 'violated_windows')::numeric BETWEEN 0 AND 2147483647
        AND (metrics ->> 'violated_windows')::numeric = trunc((metrics ->> 'violated_windows')::numeric)
        AND jsonb_typeof(metrics -> 'unassigned_count') = 'number'
        AND (metrics ->> 'unassigned_count')::numeric BETWEEN 0 AND 2147483647
        AND (metrics ->> 'unassigned_count')::numeric = trunc((metrics ->> 'unassigned_count')::numeric)
        AND jsonb_typeof(metrics -> 'solve_time_ms') = 'number'
        AND (metrics ->> 'solve_time_ms')::numeric BETWEEN 0 AND 2147483647
        AND (metrics ->> 'solve_time_ms')::numeric = trunc((metrics ->> 'solve_time_ms')::numeric)
    ),
    DROP CONSTRAINT planning_run_event_check,
    ADD CONSTRAINT planning_run_event_check CHECK (
        (run_type = 'ASSIGN' AND event_type IS NULL)
        OR (run_type = 'REASSIGN' AND event_type IS NOT NULL AND event_type IN (
            'CLIENT_CANCELLED', 'ORDER_REFUSED', 'COURIER_REFUSED',
            'COURIER_DELAYED', 'AUTO_REASSIGN_REQUEST', 'MANUAL_REASSIGN'
        ))
    );

ALTER TABLE assignment
    DROP CONSTRAINT assignment_pkey,
    DROP CONSTRAINT assignment_plan_order_key,
    DROP COLUMN id,
    ADD PRIMARY KEY (order_id, plan_version);

DROP INDEX assignment_order_version_idx;
CREATE INDEX assignment_plan_version_idx ON assignment (plan_version);

CREATE TABLE app_user (
    id uuid PRIMARY KEY,
    login text NOT NULL,
    password_hash text NOT NULL,
    name varchar(64) NOT NULL,
    role varchar(16) NOT NULL,
    courier_id uuid,
    CONSTRAINT app_user_login_key UNIQUE (login),
    CONSTRAINT app_user_courier_key UNIQUE (courier_id),
    CONSTRAINT app_user_courier_fk FOREIGN KEY (courier_id) REFERENCES courier (id),
    CONSTRAINT app_user_login_check CHECK (length(btrim(login)) > 0),
    CONSTRAINT app_user_password_hash_check CHECK (length(btrim(password_hash)) > 0),
    CONSTRAINT app_user_name_check CHECK (length(btrim(name)) BETWEEN 2 AND 64),
    CONSTRAINT app_user_role_check CHECK (role IN ('dispatcher', 'representative')),
    CONSTRAINT app_user_role_courier_check CHECK (
        (role = 'representative' AND courier_id IS NOT NULL)
        OR (role = 'dispatcher' AND courier_id IS NULL)
    )
);

CREATE TABLE business_event (
    id uuid PRIMARY KEY,
    type varchar(32) NOT NULL,
    service_date date NOT NULL,
    order_id uuid,
    courier_id uuid,
    actor_id uuid,
    occurred_at timestamptz NOT NULL,
    created_at timestamptz NOT NULL,
    reason text NOT NULL,
    payload jsonb NOT NULL,
    CONSTRAINT business_event_order_fk FOREIGN KEY (order_id) REFERENCES service_order (id),
    CONSTRAINT business_event_courier_fk FOREIGN KEY (courier_id) REFERENCES courier (id),
    CONSTRAINT business_event_actor_fk FOREIGN KEY (actor_id) REFERENCES app_user (id),
    CONSTRAINT business_event_type_check CHECK (type IN (
        'DATA_LOADED', 'PLAN_ACTIVATED', 'STATUS_CHANGED', 'ASSIGNMENT_CHANGED',
        'ORDER_RESCHEDULED', 'COURIER_DELAYED', 'ORDER_REFUSED', 'COURIER_REFUSED',
        'ALERT_CREATED', 'ALERT_ORDER_RESOLVED', 'ALERT_RESOLVED', 'SCENARIO_RESET'
    )),
    CONSTRAINT business_event_payload_check CHECK (jsonb_typeof(payload) = 'object')
);

CREATE INDEX business_event_date_idx ON business_event (service_date, created_at, id);
CREATE INDEX business_event_order_idx ON business_event (order_id, created_at) WHERE order_id IS NOT NULL;
CREATE INDEX business_event_type_idx ON business_event (service_date, type, created_at);
CREATE INDEX business_event_actor_idx ON business_event (actor_id, service_date) WHERE actor_id IS NOT NULL;
CREATE INDEX business_event_courier_idx ON business_event (courier_id, service_date, type) WHERE courier_id IS NOT NULL;

CREATE TABLE alert (
    id uuid PRIMARY KEY,
    source_event_id uuid NOT NULL,
    service_date date NOT NULL,
    courier_id uuid,
    kind varchar(16) NOT NULL,
    scope varchar(16) NOT NULL,
    severity varchar(16) NOT NULL,
    status varchar(16) NOT NULL DEFAULT 'open',
    reason_code text NOT NULL,
    reason_text text NOT NULL,
    created_at timestamptz NOT NULL,
    deadline_at timestamptz NOT NULL,
    resolved_at timestamptz,
    resolved_by uuid,
    resolution_reason text,
    CONSTRAINT alert_source_event_fk FOREIGN KEY (source_event_id) REFERENCES business_event (id),
    CONSTRAINT alert_courier_fk FOREIGN KEY (courier_id) REFERENCES courier (id),
    CONSTRAINT alert_resolved_by_fk FOREIGN KEY (resolved_by) REFERENCES app_user (id),
    CONSTRAINT alert_kind_check CHECK (kind IN ('delay', 'refusal', 'conflict')),
    CONSTRAINT alert_scope_check CHECK (scope IN ('order', 'courier_day')),
    CONSTRAINT alert_severity_check CHECK (severity IN ('warning', 'critical')),
    CONSTRAINT alert_status_check CHECK (status IN ('open', 'resolved')),
    CONSTRAINT alert_deadline_check CHECK (deadline_at >= created_at),
    CONSTRAINT alert_resolution_check CHECK (
        (status = 'open' AND resolved_at IS NULL AND resolved_by IS NULL AND resolution_reason IS NULL)
        OR (status = 'resolved' AND resolved_at IS NOT NULL)
    )
);

CREATE INDEX alert_date_status_idx ON alert (service_date, status, severity, created_at);
CREATE INDEX alert_pending_deadline_idx ON alert (deadline_at) WHERE status = 'open';
CREATE INDEX alert_source_event_idx ON alert (source_event_id);
CREATE INDEX alert_courier_idx ON alert (courier_id, service_date) WHERE courier_id IS NOT NULL;

CREATE TABLE alert_order (
    alert_id uuid NOT NULL,
    order_id uuid NOT NULL,
    status varchar(16) NOT NULL DEFAULT 'pending',
    resolved_at timestamptz,
    resolved_by uuid,
    resolution_reason text,
    PRIMARY KEY (alert_id, order_id),
    CONSTRAINT alert_order_alert_fk FOREIGN KEY (alert_id) REFERENCES alert (id),
    CONSTRAINT alert_order_order_fk FOREIGN KEY (order_id) REFERENCES service_order (id),
    CONSTRAINT alert_order_resolved_by_fk FOREIGN KEY (resolved_by) REFERENCES app_user (id),
    CONSTRAINT alert_order_status_check CHECK (status IN ('pending', 'resolved')),
    CONSTRAINT alert_order_resolution_check CHECK (
        (status = 'pending' AND resolved_at IS NULL AND resolved_by IS NULL AND resolution_reason IS NULL)
        OR (status = 'resolved' AND resolved_at IS NOT NULL)
    )
);

CREATE INDEX alert_order_order_idx ON alert_order (order_id);

CREATE TABLE api_command (
    id uuid PRIMARY KEY,
    scenario_id uuid NOT NULL,
    actor_id uuid,
    idempotency_key uuid NOT NULL,
    type text NOT NULL,
    request_hash text NOT NULL,
    request jsonb NOT NULL,
    status varchar(16) NOT NULL DEFAULT 'pending',
    result jsonb,
    error jsonb,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT api_command_actor_fk FOREIGN KEY (actor_id) REFERENCES app_user (id),
    CONSTRAINT api_command_actor_key UNIQUE NULLS NOT DISTINCT (actor_id, idempotency_key),
    CONSTRAINT api_command_type_check CHECK (length(btrim(type)) > 0),
    CONSTRAINT api_command_request_hash_check CHECK (length(btrim(request_hash)) > 0),
    CONSTRAINT api_command_request_check CHECK (jsonb_typeof(request) = 'object'),
    CONSTRAINT api_command_error_check CHECK (error IS NULL OR jsonb_typeof(error) = 'object'),
    CONSTRAINT api_command_status_check CHECK (status IN ('pending', 'running', 'succeeded', 'failed'))
);

CREATE INDEX api_command_scenario_status_idx ON api_command (scenario_id, status);

CREATE TABLE scenario_state (
    scenario_id uuid PRIMARY KEY,
    seed integer,
    config jsonb NOT NULL,
    service_date date NOT NULL,
    model_anchor_at timestamptz NOT NULL,
    real_anchor_at timestamptz NOT NULL,
    speed double precision NOT NULL,
    revision bigint NOT NULL,
    CONSTRAINT scenario_state_config_check CHECK (jsonb_typeof(config) = 'object'),
    CONSTRAINT scenario_state_speed_check CHECK (speed >= 0 AND speed < 'Infinity'::double precision),
    CONSTRAINT scenario_state_revision_check CHECK (revision >= 0)
);

CREATE UNIQUE INDEX scenario_state_singleton_key ON scenario_state ((true));
