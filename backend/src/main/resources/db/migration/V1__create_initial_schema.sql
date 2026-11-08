
CREATE TABLE office (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    name text NOT NULL,
    lat double precision NOT NULL,
    lon double precision NOT NULL,
    CONSTRAINT office_name_check CHECK (length(btrim(name)) > 0),
    CONSTRAINT office_coordinates_check CHECK (lat BETWEEN -90 AND 90 AND lon BETWEEN -180 AND 180)
);

CREATE TABLE courier (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    name text NOT NULL,
    office_id uuid NOT NULL,
    shift_start timestamptz NOT NULL,
    shift_end timestamptz NOT NULL,
    position_lat double precision,
    position_lon double precision,
    position_at timestamptz,
    is_available boolean NOT NULL,
    CONSTRAINT courier_office_fk FOREIGN KEY (office_id) REFERENCES office (id),
    CONSTRAINT courier_name_check CHECK (length(btrim(name)) > 0),
    CONSTRAINT courier_shift_check CHECK (shift_start < shift_end),
    CONSTRAINT courier_position_check CHECK (
        (position_lat IS NULL AND position_lon IS NULL AND position_at IS NULL)
        OR (position_lat IS NOT NULL AND position_lon IS NOT NULL AND position_at IS NOT NULL)
    ),
    CONSTRAINT courier_position_lat_check CHECK (position_lat BETWEEN -90 AND 90),
    CONSTRAINT courier_position_lon_check CHECK (position_lon BETWEEN -180 AND 180)
);

CREATE INDEX courier_office_idx ON courier (office_id);

CREATE TABLE service_order (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    client_name text NOT NULL,
    lat double precision NOT NULL,
    lon double precision NOT NULL,
    window_from timestamptz NOT NULL,
    window_to timestamptz NOT NULL,
    service_duration_s integer NOT NULL,
    status varchar(32) NOT NULL DEFAULT 'NEW',
    service_date date NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT service_order_client_name_check CHECK (length(btrim(client_name)) > 0),
    CONSTRAINT service_order_coordinates_check CHECK (lat BETWEEN -90 AND 90 AND lon BETWEEN -180 AND 180),
    CONSTRAINT service_order_window_check CHECK (window_from <= window_to),
    CONSTRAINT service_order_duration_check CHECK (service_duration_s > 0),
    CONSTRAINT service_order_status_check CHECK (status IN (
        'NEW', 'ASSIGNED', 'UNASSIGNED', 'EN_ROUTE', 'ARRIVED',
        'IN_PROGRESS', 'COMPLETED', 'RESCHEDULED', 'CANCELLED'
    )),
    CONSTRAINT service_order_version_check CHECK (version >= 0)
);

CREATE INDEX service_order_date_status_idx ON service_order (service_date, status);

CREATE SEQUENCE planning_run_plan_version_seq AS bigint START WITH 1 INCREMENT BY 1 NO CYCLE;

CREATE TABLE planning_run (
    plan_version bigint PRIMARY KEY DEFAULT nextval('planning_run_plan_version_seq'::regclass),
    request_id uuid NOT NULL,
    run_type varchar(16) NOT NULL,
    event_type varchar(32),
    algorithm varchar(16) NOT NULL,
    plan_date date NOT NULL,
    model_now timestamptz NOT NULL,
    created_at timestamptz NOT NULL,
    CONSTRAINT planning_run_request_id_key UNIQUE (request_id),
    CONSTRAINT planning_run_version_check CHECK (plan_version > 0),
    CONSTRAINT planning_run_type_check CHECK (run_type IN ('ASSIGN', 'REASSIGN')),
    CONSTRAINT planning_run_event_check CHECK (
        (run_type = 'ASSIGN' AND event_type IS NULL)
        OR (run_type = 'REASSIGN' AND event_type IS NOT NULL AND event_type IN (
            'CLIENT_CANCELLED', 'COURIER_REFUSED', 'COURIER_DELAYED',
            'AUTO_REASSIGN_REQUEST', 'MANUAL_REASSIGN'
        ))
    ),
    CONSTRAINT planning_run_algorithm_check CHECK (algorithm IN ('greedy', 'ortools'))
);

ALTER SEQUENCE planning_run_plan_version_seq OWNED BY planning_run.plan_version;

CREATE TABLE route (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    courier_id uuid NOT NULL,
    plan_date date NOT NULL,
    plan_version bigint NOT NULL,
    is_active boolean NOT NULL DEFAULT false,
    algorithm varchar(16) NOT NULL,
    total_distance_m integer NOT NULL,
    violated_windows integer NOT NULL,
    created_at timestamptz NOT NULL,
    CONSTRAINT route_courier_fk FOREIGN KEY (courier_id) REFERENCES courier (id),
    CONSTRAINT route_planning_run_fk FOREIGN KEY (plan_version) REFERENCES planning_run (plan_version),
    CONSTRAINT route_courier_date_version_key UNIQUE (courier_id, plan_date, plan_version),
    -- Supports the assignment FK without coupling its run to the route's run.
    CONSTRAINT route_id_courier_key UNIQUE (id, courier_id),
    CONSTRAINT route_algorithm_check CHECK (algorithm IN ('greedy', 'ortools')),
    CONSTRAINT route_distance_check CHECK (total_distance_m >= 0),
    CONSTRAINT route_violated_windows_check CHECK (violated_windows >= 0)
);

CREATE UNIQUE INDEX route_active_courier_date_key ON route (courier_id, plan_date) WHERE is_active;
CREATE INDEX route_plan_version_idx ON route (plan_version);

CREATE TABLE route_stop (
    route_id uuid NOT NULL,
    sequence integer NOT NULL,
    type varchar(16) NOT NULL,
    order_id uuid,
    office_id uuid,
    arrival_eta timestamptz NOT NULL,
    departure_eta timestamptz NOT NULL,
    distance_from_prev_m integer NOT NULL,
    window_violation_s integer NOT NULL,
    PRIMARY KEY (route_id, sequence),
    CONSTRAINT route_stop_route_fk FOREIGN KEY (route_id) REFERENCES route (id),
    CONSTRAINT route_stop_order_fk FOREIGN KEY (order_id) REFERENCES service_order (id),
    CONSTRAINT route_stop_office_fk FOREIGN KEY (office_id) REFERENCES office (id),
    CONSTRAINT route_stop_sequence_check CHECK (sequence >= 1),
    CONSTRAINT route_stop_type_check CHECK (type IN ('OFFICE', 'ORDER')),
    CONSTRAINT route_stop_target_check CHECK (
        (type = 'ORDER' AND order_id IS NOT NULL AND office_id IS NULL)
        OR (type = 'OFFICE' AND office_id IS NOT NULL AND order_id IS NULL)
    ),
    CONSTRAINT route_stop_eta_check CHECK (departure_eta >= arrival_eta),
    CONSTRAINT route_stop_distance_check CHECK (distance_from_prev_m >= 0),
    CONSTRAINT route_stop_window_violation_check CHECK (window_violation_s >= 0)
);

CREATE INDEX route_stop_order_idx ON route_stop (order_id) WHERE order_id IS NOT NULL;
CREATE INDEX route_stop_office_idx ON route_stop (office_id) WHERE office_id IS NOT NULL;

CREATE TABLE assignment (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id uuid NOT NULL,
    courier_id uuid,
    route_id uuid,
    plan_version bigint NOT NULL,
    source_order_version bigint NOT NULL,
    reason_code varchar(32) NOT NULL,
    reason_text text NOT NULL,
    decided_by varchar(16) NOT NULL,
    created_at timestamptz NOT NULL,
    CONSTRAINT assignment_order_fk FOREIGN KEY (order_id) REFERENCES service_order (id),
    CONSTRAINT assignment_courier_fk FOREIGN KEY (courier_id) REFERENCES courier (id),
    CONSTRAINT assignment_route_courier_fk FOREIGN KEY (route_id, courier_id) REFERENCES route (id, courier_id),
    CONSTRAINT assignment_planning_run_fk FOREIGN KEY (plan_version) REFERENCES planning_run (plan_version),
    CONSTRAINT assignment_plan_order_key UNIQUE (plan_version, order_id),
    CONSTRAINT assignment_source_version_check CHECK (source_order_version >= 0),
    CONSTRAINT assignment_reason_check CHECK (reason_code IN (
        'NEAREST', 'FREE_WINDOW', 'OPTIMIZED', 'REASSIGNED_AFTER_REFUSAL',
        'MANUAL_DISPATCHER', 'AUTO_AFTER_TIMEOUT', 'NO_CAPACITY',
        'WINDOW_INFEASIBLE', 'DEFER_NEXT_DAY'
    )),
    CONSTRAINT assignment_decided_by_check CHECK (decided_by IN ('SYSTEM', 'DISPATCHER')),
    CONSTRAINT assignment_target_check CHECK (
        (courier_id IS NULL AND route_id IS NULL)
        OR (courier_id IS NOT NULL AND route_id IS NOT NULL)
    )
);

CREATE INDEX assignment_order_version_idx ON assignment (order_id, plan_version DESC);
CREATE INDEX assignment_courier_idx ON assignment (courier_id) WHERE courier_id IS NOT NULL;
CREATE INDEX assignment_route_idx ON assignment (route_id) WHERE route_id IS NOT NULL;
